package com.jyfc.backend.module.sign.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.account.service.SignQuotaService;
import com.jyfc.backend.module.auth.repository.UserRepository;
import com.jyfc.backend.module.notification.entity.NotificationEntity;
import com.jyfc.backend.module.notification.service.NotificationService;
import com.jyfc.backend.module.sign.entity.SignInviteEntity;
import com.jyfc.backend.module.sign.entity.SignPartyEntity;
import com.jyfc.backend.module.sign.entity.SignTaskEntity;
import com.jyfc.backend.module.sign.repository.SignInviteRepository;
import com.jyfc.backend.module.sign.repository.SignPartyRepository;
import com.jyfc.backend.module.sign.repository.SignTaskRepository;
import com.jyfc.backend.module.signdoc.repository.SignDocRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 创建任务两段式（docs/11 P0）。草稿可反复保存；确认提交按参与方式进入填写、定稿或签署。
 * 签署要求只落 JSON，阅读秒数、人脸、AI 手绘留到签署执行时。
 */
@Service
public class SignDraftService {

    private static final Set<String> SIGNATURE_MODES = Set.of("UNLIMITED", "STANDARD", "HANDWRITE", "AI_HANDWRITE");
    private static final Set<String> WILL_MODES = Set.of("PASSWORD", "SMS", "FACE");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SignTaskRepository taskRepository;
    private final SignPartyRepository partyRepository;
    private final SignInviteRepository inviteRepository;
    private final SignDocRepository signDocRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SignTaskStateMachine stateMachine;
    private final SignQuotaService signQuotaService;

    public SignDraftService(SignTaskRepository taskRepository, SignPartyRepository partyRepository,
                            SignInviteRepository inviteRepository, SignDocRepository signDocRepository,
                            UserRepository userRepository, NotificationService notificationService,
                            SignTaskStateMachine stateMachine, SignQuotaService signQuotaService) {
        this.taskRepository = taskRepository;
        this.partyRepository = partyRepository;
        this.inviteRepository = inviteRepository;
        this.signDocRepository = signDocRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.stateMachine = stateMachine;
        this.signQuotaService = signQuotaService;
    }

    private Long tenant() {
        Long t = JyTenantContext.get();
        if (t == null || t == 0L) throw new BusinessException("租户上下文缺失");
        return t;
    }

    private SignTaskEntity draftOf(Long id, Long actorId) {
        SignTaskEntity t = taskRepository.findByIdAndTenantId(id, tenant())
                .orElseThrow(() -> new BusinessException("任务不存在或跨租户: " + id));
        if (!Objects.equals(t.getCreatedBy(), actorId)) throw new BusinessException("仅任务发起方可编辑草稿");
        if (!"DRAFT".equals(t.getStatus())) throw new BusinessException("仅草稿可修改");
        return t;
    }

    @Transactional
    public SignTaskEntity patchDraft(Long id, Map<String, Object> body, Long actorId) {
        SignTaskEntity t = draftOf(id, actorId);
        if (body.containsKey("title")) {
            String title = str(body.get("title"));
            if (title == null || title.isBlank()) throw new BusinessException("请填写任务主题");
            if (title.length() > 50) throw new BusinessException("任务主题不能超过 50 字");
            t.setTitle(title);
        }
        if (body.containsKey("expireAt")) t.setExpireAt(parseTime(body.get("expireAt"), "签署截止时间"));
        if (body.containsKey("contractDueAt")) t.setContractDueAt(parseTime(body.get("contractDueAt"), "合同到期日"));
        if (body.containsKey("contractDueEndAt")) t.setContractDueEndAt(parseTime(body.get("contractDueEndAt"), "合同到期结束日"));
        if (t.getContractDueAt() != null && t.getContractDueEndAt() != null
                && t.getContractDueEndAt().isBefore(t.getContractDueAt())) {
            throw new BusinessException("合同到期结束日不能早于开始日");
        }
        if (body.containsKey("signMode")) {
            String mode = str(body.get("signMode"));
            if (!"PARALLEL".equals(mode) && !"SEQUENTIAL".equals(mode)) {
                throw new BusinessException("signMode 仅支持 PARALLEL / SEQUENTIAL");
            }
            t.setSignMode(mode);
        }
        if (body.containsKey("finalizeMode")) {
            String mode = str(body.get("finalizeMode"));
            if (!"AUTO".equals(mode) && !"MANUAL".equals(mode)) {
                throw new BusinessException("finalizeMode 仅支持 AUTO / MANUAL");
            }
            t.setFinalizeMode(mode);
        }
        if (body.containsKey("approvalFlowId")) t.setApprovalFlowId(longOrNull(body.get("approvalFlowId")));
        int remaining = remainingQuota();
        t.setQuotaSnapshot(remaining);
        return taskRepository.save(t);
    }

    @Transactional
    public List<SignPartyEntity> replaceParties(Long id, List<Map<String, Object>> rows, Long actorId) {
        SignTaskEntity t = draftOf(id, actorId);
        if (rows == null) throw new BusinessException("parties 必须是数组");
        if (rows.size() > 50) throw new BusinessException("收件人不能超过 50 人");
        inviteRepository.deleteByTaskId(t.getId());
        partyRepository.deleteByTaskId(t.getId());
        List<SignPartyEntity> saved = new ArrayList<>();
        int order = 1;
        for (Map<String, Object> row : rows) {
            saved.add(partyRepository.save(buildParty(t, row, order++, actorId)));
        }
        return saved;
    }

    /** 提交后的目标态。不写库，供调用方在进入签署前做配额裁决。 */
    public String peekSubmitTarget(Long id, Long actorId) {
        SignTaskEntity t = draftOf(id, actorId);
        validateReady(t);
        return targetOf(partyRepository.findByTaskIdOrderByIdAsc(t.getId()), t.getFinalizeMode());
    }

    @Transactional
    public SignTaskEntity submit(Long id, Long actorId) {
        SignTaskEntity t = draftOf(id, actorId);
        validateReady(t);
        List<SignPartyEntity> parties = partyRepository.findByTaskIdOrderByIdAsc(t.getId());
        String target = targetOf(parties, t.getFinalizeMode());
        applyPartyStatusFor(parties, target);
        issueInvites(t, parties, actorId);
        stateMachine.transfer(t.getId(), "SUBMIT", "USER", actorId, "create submit");
        String trigger = switch (target) {
            case "FILLING" -> "OPEN_FILL";
            case "FINALIZING" -> "OPEN_FINALIZE";
            default -> "START";
        };
        return stateMachine.transfer(t.getId(), trigger, "USER", actorId, "enter " + target);
    }

    /** 完成填写后的目标态：手动定稿停在定稿，否则进入签署。 */
    public String peekAfterFill(Long id, Long actorId) {
        SignTaskEntity t = taskRepository.findByIdAndTenantId(id, tenant())
                .orElseThrow(() -> new BusinessException("任务不存在或跨租户: " + id));
        if (!"FILLING".equals(t.getStatus())) throw new BusinessException("当前不在填写阶段");
        assertFillActor(t, actorId);
        return "MANUAL".equals(t.getFinalizeMode()) ? "FINALIZING" : "SIGNING";
    }

    @Transactional
    public SignTaskEntity finishFill(Long id, Long actorId) {
        String target = peekAfterFill(id, actorId);
        List<SignPartyEntity> parties = partyRepository.findByTaskIdOrderByIdAsc(id);
        if ("SIGNING".equals(target)) {
            markReadyToSign(parties);
        } else {
            for (SignPartyEntity p : parties) {
                p.setPartyStatus("PENDING_FINALIZE");
                partyRepository.save(p);
            }
        }
        String trigger = "SIGNING".equals(target) ? "FINISH_FILL_TO_SIGN" : "FINISH_FILL_TO_FINAL";
        return stateMachine.transfer(id, trigger, "USER", actorId, "fill done");
    }

    @Transactional
    public SignTaskEntity confirmFinal(Long id, Long actorId) {
        SignTaskEntity t = taskRepository.findByIdAndTenantId(id, tenant())
                .orElseThrow(() -> new BusinessException("任务不存在或跨租户: " + id));
        if (!Objects.equals(t.getCreatedBy(), actorId)) throw new BusinessException("仅任务发起方可确认定稿");
        if (!"FINALIZING".equals(t.getStatus())) throw new BusinessException("当前不在定稿阶段");
        markReadyToSign(partyRepository.findByTaskIdOrderByIdAsc(id));
        return stateMachine.transfer(id, "CONFIRM_FINAL", "USER", actorId, "finalized");
    }

    private void validateReady(SignTaskEntity t) {
        if (t.getTitle() == null || t.getTitle().isBlank() || "未命名任务".equals(t.getTitle())) {
            throw new BusinessException("请填写任务主题");
        }
        if (t.getTitle().length() > 50) throw new BusinessException("任务主题不能超过 50 字");
        if (t.getExpireAt() == null) throw new BusinessException("请填写签署截止时间");
        if (!t.getExpireAt().isAfter(LocalDateTime.now())) throw new BusinessException("签署截止时间必须晚于现在");
        List<SignPartyEntity> parties = partyRepository.findByTaskIdOrderByIdAsc(t.getId());
        if (parties.isEmpty()) throw new BusinessException("请至少添加一名收件人");
        boolean anyDuty = parties.stream().anyMatch(p -> Boolean.TRUE.equals(p.getCanFill()) || Boolean.TRUE.equals(p.getCanSign()));
        if (!anyDuty) throw new BusinessException("收件人须勾选填写或签署");
        if (signDocRepository.findAllByTaskIdOrderByIdAsc(t.getId()).isEmpty()) {
            throw new BusinessException("请至少上传一份文件");
        }
    }

    private static String targetOf(List<SignPartyEntity> parties, String finalizeMode) {
        boolean anyFill = parties.stream().anyMatch(p -> Boolean.TRUE.equals(p.getCanFill()));
        if (anyFill) return "FILLING";
        if ("MANUAL".equals(finalizeMode)) return "FINALIZING";
        return "SIGNING";
    }

    private void applyPartyStatusFor(List<SignPartyEntity> parties, String target) {
        for (SignPartyEntity p : parties) {
            if ("SIGNING".equals(target) && Boolean.TRUE.equals(p.getCanSign())) {
                p.setPartyStatus("PENDING_SIGN");
            } else if ("FINALIZING".equals(target)) {
                p.setPartyStatus("PENDING_FINALIZE");
            } else if (Boolean.TRUE.equals(p.getCanFill())) {
                p.setPartyStatus("PENDING_FILL");
            } else {
                p.setPartyStatus("PENDING_SIGN");
            }
            if (Boolean.TRUE.equals(p.getCanSign())) p.setPartyRole("SIGNER");
            else p.setPartyRole("FILLER");
            partyRepository.save(p);
        }
    }

    private void markReadyToSign(List<SignPartyEntity> parties) {
        for (SignPartyEntity p : parties) {
            if (Boolean.TRUE.equals(p.getCanSign())) p.setPartyStatus("PENDING_SIGN");
            else p.setPartyStatus("SIGNED");
            partyRepository.save(p);
        }
    }

    private void assertFillActor(SignTaskEntity t, Long actorId) {
        if (Objects.equals(t.getCreatedBy(), actorId)) return;
        boolean filler = partyRepository.findByTaskIdOrderByIdAsc(t.getId()).stream()
                .anyMatch(p -> Boolean.TRUE.equals(p.getCanFill()) && Objects.equals(p.getUserId(), actorId));
        if (!filler) throw new BusinessException("仅发起方或填写人可完成填写");
    }

    private void issueInvites(SignTaskEntity t, List<SignPartyEntity> parties, Long actorId) {
        for (SignPartyEntity p : parties) {
            String rawToken = UUID.randomUUID().toString();
            SignInviteEntity inv = new SignInviteEntity();
            inv.setTaskId(t.getId());
            inv.setPartyId(p.getId());
            inv.setInviteStatus("WAIT");
            inv.setTargetUserId(p.getUserId());
            inv.setTokenHash(sha256(rawToken));
            inv.setExpireAt(t.getExpireAt());
            inv.setCreatedBy(actorId);
            inviteRepository.save(inv);
            p.setSignInviteId(inv.getId());
            partyRepository.save(p);
            if (p.getUserId() != null) {
                notificationService.createNotification(p.getUserId(), NotificationEntity.Type.SIGN,
                        "待你接收的签署邀请",
                        "任务《" + t.getTitle() + "》邀请你参与",
                        "/signing?view=PENDING_RECEIVE");
            }
        }
    }

    private SignPartyEntity buildParty(SignTaskEntity t, Map<String, Object> row, int order, Long actorId) {
        String type = str(row.get("partyType"));
        if (!"ORG".equals(type) && !"PERSON".equals(type)) throw new BusinessException("主体仅支持个人或企业");
        String name = str(row.get("externalName"));
        if (name == null || name.isBlank()) throw new BusinessException("请填写收件人名称");
        if (name.length() > 64) throw new BusinessException("收件人名称不能超过 64 字");
        boolean canFill = bool(row.get("canFill"));
        boolean canSign = bool(row.get("canSign"));
        if (!canFill && !canSign) throw new BusinessException("收件人须勾选填写或签署");
        Long memberId = longOrNull(row.get("memberUserId"));
        if (memberId != null) {
            userRepository.findById(memberId).orElseThrow(() -> new BusinessException("指定成员不存在: " + memberId));
        }
        String phone = str(row.get("externalPhone"));
        String email = str(row.get("externalEmail"));
        if (phone != null && phone.length() > 32) throw new BusinessException("手机号过长");
        if (email != null && email.length() > 128) throw new BusinessException("邮箱过长");

        SignPartyEntity p = new SignPartyEntity();
        p.setTaskId(t.getId());
        p.setPartyType(type);
        p.setPartyRole(canSign ? "SIGNER" : "FILLER");
        p.setPartyStatus("PENDING_FILL");
        p.setSignOrder(order);
        p.setExternalName(name);
        p.setExternalPhone(phone);
        p.setExternalEmail(email);
        p.setMemberUserId(memberId);
        p.setUserId(memberId);
        p.setCanFill(canFill);
        p.setCanSign(canSign);
        p.setIdentityCheck(bool(row.get("identityCheck")));
        p.setSignRequirementJson(requirementJson(row.get("signRequirement")));
        p.setCreatedBy(actorId);
        return p;
    }

    private String requirementJson(Object raw) {
        if (raw == null) return null;
        if (!(raw instanceof Map<?, ?> map)) throw new BusinessException("签署要求格式不正确");
        String mode = str(map.get("signatureMode"));
        if (mode == null) mode = "UNLIMITED";
        if (!SIGNATURE_MODES.contains(mode)) throw new BusinessException("签名方式不正确");
        List<String> wills = new ArrayList<>();
        Object willRaw = map.get("wills");
        if (willRaw instanceof List<?> list) {
            for (Object w : list) {
                String code = str(w);
                if (code == null) continue;
                if (!WILL_MODES.contains(code)) throw new BusinessException("签署意愿方式不正确");
                if (!wills.contains(code)) wills.add(code);
            }
        }
        boolean readToEnd = bool(map.get("readToEnd"));
        Integer readSeconds = intOrNull(map.get("readSeconds"));
        if (readSeconds != null && (readSeconds < 1 || readSeconds > 3600)) {
            throw new BusinessException("阅读秒数须在 1 到 3600 之间");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("signatureMode", mode);
        out.put("wills", wills);
        out.put("readToEnd", readToEnd);
        out.put("readSeconds", readSeconds);
        out.put("requireAttachment", bool(map.get("requireAttachment")));
        try {
            String json = JSON.writeValueAsString(out);
            if (json.length() > 4000) throw new BusinessException("签署要求过长");
            return json;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("签署要求无法保存");
        }
    }

    private int remainingQuota() {
        var snap = signQuotaService.snapshot(tenant());
        Object remaining = snap.get("signRemaining");
        if (remaining instanceof Number n) return n.intValue();
        return 0;
    }

    private static LocalDateTime parseTime(Object raw, String label) {
        return SignTimeUtil.parse(raw == null ? null : String.valueOf(raw), label);
    }

    private static String str(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static boolean bool(Object raw) {
        if (raw instanceof Boolean b) return b;
        return raw != null && "true".equalsIgnoreCase(String.valueOf(raw));
    }

    private static Long longOrNull(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        try {
            return Long.valueOf(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new BusinessException("数字格式不正确");
        }
    }

    private static Integer intOrNull(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank() || "null".equals(String.valueOf(raw))) return null;
        try {
            return Integer.valueOf(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new BusinessException("数字格式不正确");
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
