package com.jyfc.backend.module.sign.service;

import com.jyfc.backend.core.cp.CpProperties;
import com.jyfc.backend.core.cp.CpSigningClient;
import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.audit.entity.AuditLogEntity;
import com.jyfc.backend.module.audit.entity.DataAccessLogEntity;
import com.jyfc.backend.module.audit.repository.AuditLogRepository;
import com.jyfc.backend.module.audit.repository.DataAccessLogRepository;
import com.jyfc.backend.module.auth.entity.CompanyEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.CompanyRepository;
import com.jyfc.backend.module.auth.repository.UserRepository;
import com.jyfc.backend.module.notification.entity.NotificationEntity;
import com.jyfc.backend.module.notification.service.NotificationService;
import com.jyfc.backend.module.sign.entity.CertificateRecordEntity;
import com.jyfc.backend.module.sign.entity.SignCcEntity;
import com.jyfc.backend.module.sign.entity.SignInviteEntity;
import com.jyfc.backend.module.sign.entity.SignPartyEntity;
import com.jyfc.backend.module.sign.entity.SignTaskEntity;
import com.jyfc.backend.module.sign.repository.CertificateRecordRepository;
import com.jyfc.backend.module.sign.repository.SignCcRepository;
import com.jyfc.backend.module.sign.repository.SignInviteRepository;
import com.jyfc.backend.module.sign.repository.SignPartyRepository;
import com.jyfc.backend.module.sign.repository.SignTaskRepository;
import com.jyfc.backend.module.signdoc.repository.SignDocRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 签署流程服务（S1'）。
 * 隔离模型（V3.0.2 修正）：**管理视图按租户隔离**（created-by / tenant 列表 / KPI）；
 * **参与视图按链接授权跨租户**（sign_party.user_id / sign_invite.target_user_id 指向谁，谁可见可签）——
 * 这是跨企业签署的本质（B 企业用户签署 A 企业发起的任务）。授权在服务边界强制，状态机按 id 推进。
 */
@Service
public class SignFlowService {

    private static final List<String> PENDING_STATUSES = List.of("PENDING_FILL", "PENDING_FINALIZE", "PENDING_SIGN");

    private final SignTaskRepository taskRepository;
    private final SignPartyRepository partyRepository;
    private final SignInviteRepository inviteRepository;
    private final AuditLogRepository auditLogRepository;
    private final DataAccessLogRepository dataAccessLogRepository;
    private final UserRepository userRepository;
    private final CertificateRecordRepository certificateRecordRepository;
    private final SignDocRepository signDocRepository;
    private final NotificationService notificationService;
    private final SignTaskStateMachine stateMachine;
    private final CpProperties cpProperties;
    private final CpSigningClient cpSigningClient;
    private final SignCcRepository signCcRepository;
    private final CompanyRepository companyRepository;

    public SignFlowService(SignTaskRepository taskRepository, SignPartyRepository partyRepository,
                           SignInviteRepository inviteRepository, AuditLogRepository auditLogRepository,
                           DataAccessLogRepository dataAccessLogRepository, UserRepository userRepository,
                           CertificateRecordRepository certificateRecordRepository,
                           SignDocRepository signDocRepository,
                           NotificationService notificationService,
                           SignTaskStateMachine stateMachine,
                           CpProperties cpProperties,
                           CpSigningClient cpSigningClient,
                           SignCcRepository signCcRepository,
                           CompanyRepository companyRepository) {
        this.taskRepository = taskRepository;
        this.partyRepository = partyRepository;
        this.inviteRepository = inviteRepository;
        this.auditLogRepository = auditLogRepository;
        this.dataAccessLogRepository = dataAccessLogRepository;
        this.userRepository = userRepository;
        this.certificateRecordRepository = certificateRecordRepository;
        this.signDocRepository = signDocRepository;
        this.notificationService = notificationService;
        this.stateMachine = stateMachine;
        this.cpProperties = cpProperties;
        this.cpSigningClient = cpSigningClient;
        this.signCcRepository = signCcRepository;
        this.companyRepository = companyRepository;
    }

    private Long tenant() {
        Long t = JyTenantContext.get();
        if (t == null || t == 0L) throw new BusinessException("租户上下文缺失");
        return t;
    }

    /** 管理域读取：仅本租户。 */
    private SignTaskEntity task(Long id) {
        return taskRepository.findByIdAndTenantId(id, tenant())
                .orElseThrow(() -> new BusinessException("任务不存在或跨租户: " + id));
    }

    /** 参与域读取：本租户 或 与 actor 有 party/invite 链接。 */
    public SignTaskEntity taskForActor(Long id, Long actor) {
        Optional<SignTaskEntity> own = taskRepository.findByIdAndTenantId(id, tenant());
        if (own.isPresent()) return own.get();
        SignTaskEntity t = taskRepository.findById(id).orElseThrow(() -> new BusinessException("任务不存在: " + id));
        if (!linkedToActor(t.getId(), actor)) throw new BusinessException("无权访问该任务: " + id);
        return t;
    }

    private boolean linkedToActor(Long taskId, Long actor) {
        boolean byParty = partyRepository.findByTaskIdOrderByIdAsc(taskId).stream()
                .anyMatch(p -> actor.equals(p.getUserId()));
        boolean byInvite = inviteRepository.findByTaskId(taskId).stream()
                .anyMatch(i -> actor.equals(i.getTargetUserId()));
        return byParty || byInvite;
    }

    private void audit(String action, String entityType, Long entityId, Long actorId) {
        AuditLogEntity a = new AuditLogEntity();
        a.setActorType("USER");
        a.setActorId(actorId);
        a.setAction(action);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setResult("SUCCESS");
        auditLogRepository.save(a);
    }

    /**
     * 添加参与方 + 创建邀请。仅任务发起方可操作；内部邀请的目标用户必须存在且同租户。
     * 返回体含明文邀请令牌（仅此一次）：外部参与方凭令牌 accept 认领，内部参与方无需令牌。
     */
    @Transactional
    public Map<String, Object> addParty(Long taskId, Long userId, String externalName, String externalPhone,
                                        String role, Integer order, Long actorId) {
        SignTaskEntity t = task(taskId);
        if (!Objects.equals(t.getCreatedBy(), actorId)) throw new BusinessException("仅任务发起方可添加参与方");
        if (userId != null) {
            // 目标用户必须真实存在；跨租户邀请允许（跨企业签署为产品本质），
            // 但无人在接受邀请（partyStatus → PENDING_SIGN）前可签署。
            userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("目标用户不存在: " + userId));
        }
        SignPartyEntity p = new SignPartyEntity();
        p.setTaskId(t.getId());
        p.setPartyRole(role != null ? role : "SIGNER");
        p.setPartyType(userId != null ? "ORG" : "PERSON");
        p.setPartyStatus("PENDING_FILL");
        p.setSignOrder(order != null ? order : 1);
        p.setUserId(userId);
        p.setExternalName(externalName);
        p.setExternalPhone(externalPhone);
        p.setCreatedBy(actorId);
        partyRepository.save(p);

        String rawToken = UUID.randomUUID().toString();
        SignInviteEntity inv = new SignInviteEntity();
        inv.setTaskId(t.getId());
        inv.setPartyId(p.getId());
        inv.setInviteStatus("WAIT");
        inv.setTargetUserId(userId);
        inv.setTokenHash(sha256(rawToken));
        inv.setExpireAt(LocalDateTime.now().plusDays(7));
        inv.setCreatedBy(actorId);
        inviteRepository.save(inv);
        p.setSignInviteId(inv.getId());
        partyRepository.save(p);

        audit("SIGN_PARTY_ADD", "sign_task", t.getId(), actorId);
        // 站内通知（B9）：受邀人已注册时即时可见于「待接收」；外部无账号方由发起人转发邀请链接
        // （明文令牌仅经 addParty 响应一次性返回；邮件通道列为后续）
        if (userId != null) {
            notificationService.createNotification(userId, NotificationEntity.Type.SIGN,
                    "待你接收的签署邀请",
                    "任务《" + t.getTitle() + "》邀请你作为 " + p.getPartyRole() + " 参与签署",
                    "/signing?view=PENDING_RECEIVE");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("party", p);
        out.put("inviteId", inv.getId());
        out.put("inviteToken", rawToken);
        return out;
    }

    /**
     * 接受邀请。内部邀请（targetUserId 非空）校验本人；外部邀请必须出示一次性令牌（仅比哈希）。
     * 外部方接受即认领该参与方（绑定 user_id），后续 sign/reject 按本人校验。
     */
    @Transactional
    public SignInviteEntity acceptInvite(Long inviteId, Long actorId, String rawToken) {
        SignInviteEntity inv = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new BusinessException("邀请不存在: " + inviteId));
        if (!"WAIT".equals(inv.getInviteStatus())) throw new BusinessException("邀请已处理: " + inv.getInviteStatus());
        if (!inv.getExpireAt().isAfter(LocalDateTime.now())) throw new BusinessException("邀请已过期");
        if (inv.getTargetUserId() != null) {
            if (!inv.getTargetUserId().equals(actorId)) throw new BusinessException("邀请目标不符");
        } else {
            if (rawToken == null || rawToken.isBlank() || !sha256(rawToken).equals(inv.getTokenHash())) {
                throw new BusinessException("邀请令牌无效");
            }
            inv.setTargetUserId(actorId);
        }
        inv.setInviteStatus("ACCEPTED");
        inviteRepository.save(inv);

        SignPartyEntity p = partyRepository.findById(inv.getPartyId())
                .orElseThrow(() -> new BusinessException("参与方不存在"));
        if (p.getUserId() == null) p.setUserId(actorId);
        p.setPartyStatus("PENDING_SIGN");
        partyRepository.save(p);

        SignTaskEntity t = taskRepository.findById(inv.getTaskId()).orElseThrow();
        if ("CREATED".equals(t.getStatus())) {
            stateMachine.transfer(t.getId(), "START", "USER", actorId, "invite accepted");
        }
        audit("SIGN_INVITE_ACCEPT", "sign_invite", inv.getId(), actorId);
        return inv;
    }

    /**
     * 打开 e签宝签署页。不把参与方标成已签署；完成只来自回调。
     */
    public Map<String, Object> openSign(Long taskId, Long partyId, Long actorId, String forwardToken) {
        SignTaskEntity t = taskForActor(taskId, actorId);
        SignPartyEntity p = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException("参与方不存在: " + partyId));
        if (!p.getTaskId().equals(t.getId())) throw new BusinessException("参与方不属于该任务");
        if (!Objects.equals(p.getUserId(), actorId)) throw new BusinessException("只能签署本人的参与方");
        if (!"SIGNING".equals(t.getStatus())) throw new BusinessException("任务不在签署中");
        if (!"PENDING_SIGN".equals(p.getPartyStatus())) throw new BusinessException("参与方状态不可签署: " + p.getPartyStatus());
        if (!Boolean.TRUE.equals(p.getCanSign()) && !"SIGNER".equals(p.getPartyRole())) {
            throw new BusinessException("该参与方不需要签署");
        }
        if ("SEQUENTIAL".equals(t.getSignMode())) {
            int mine = p.getSignOrder() == null ? 1 : p.getSignOrder();
            boolean blocked = partyRepository.findByTaskIdOrderByIdAsc(t.getId()).stream()
                    .filter(x -> Boolean.TRUE.equals(x.getCanSign()) || "SIGNER".equals(x.getPartyRole()))
                    .anyMatch(x -> (x.getSignOrder() == null ? 1 : x.getSignOrder()) < mine
                            && !"SIGNED".equals(x.getPartyStatus()));
            if (blocked) throw new BusinessException("尚未轮到您签署");
        }
        if (!cpProperties.isEnabled()) {
            throw new BusinessException("当前环境未连接控制平面，不能发起具有法律效力的签署");
        }
        if (t.getProviderFlowId() == null || t.getProviderFlowId().isBlank()) {
            throw new BusinessException("签署流程尚未在 e签宝创建");
        }
        String account = signerAccount(p);
        Map<String, Object> url = cpSigningClient.signUrl(t.getProviderFlowId(), account, forwardToken);
        url.put("providerFlowId", t.getProviderFlowId());
        audit("SIGN_OPEN_URL:" + url.getOrDefault("provider", "esign-saas-v3"), "sign_party", p.getId(), actorId);
        return url;
    }

    public List<SignTaskEntity> listByView(String view, Long userId) {
        String v = view == null ? "CREATED_BY_ME" : view;
        switch (v) {
            case "CREATED_BY_ME":
                return taskRepository.findAllByTenantIdOrderByIdDesc(tenant()).stream()
                        .filter(x -> x.getCreatedBy().equals(userId)).collect(Collectors.toList());
            case "RECEIVED":
                return tasksByIds(partyRepository.findByUserId(userId).stream()
                        .map(SignPartyEntity::getTaskId).collect(Collectors.toSet()));
            case "PENDING_ME":
                return tasksByIds(partyRepository.findByUserIdAndPartyStatusIn(userId, PENDING_STATUSES).stream()
                        .map(SignPartyEntity::getTaskId).collect(Collectors.toSet()));
            case "PENDING_OTHERS":
                return taskRepository.findAllByTenantIdOrderByIdDesc(tenant()).stream()
                        .filter(x -> "SIGNING".equals(x.getStatus()))
                        .filter(x -> partyRepository.findByTaskIdOrderByIdAsc(x.getId()).stream()
                                .anyMatch(p -> PENDING_STATUSES.contains(p.getPartyStatus())
                                        && (p.getUserId() == null || !p.getUserId().equals(userId))))
                        .collect(Collectors.toList());
            case "COMPLETED":
                return taskRepository.findAllByTenantIdOrderByIdDesc(tenant()).stream()
                        .filter(x -> "COMPLETED".equals(x.getStatus())).collect(Collectors.toList());
            case "PENDING_RECEIVE":
                return tasksByIds(inviteRepository.findByTargetUserIdAndInviteStatus(userId, "WAIT").stream()
                        .map(SignInviteEntity::getTaskId).collect(Collectors.toSet()));
            case "CC_TO_ME":
                return tasksByIds(signCcRepository.findByUserId(userId).stream()
                        .map(SignCcEntity::getTaskId).collect(Collectors.toSet()));
            case "EXPIRING_SOON":
                LocalDateTime soon = LocalDateTime.now().plusDays(3);
                return taskRepository.findAllByTenantIdOrderByIdDesc(tenant()).stream()
                        .filter(x -> "SIGNING".equals(x.getStatus()))
                        .filter(x -> x.getExpireAt() != null && x.getExpireAt().isAfter(LocalDateTime.now()) && x.getExpireAt().isBefore(soon))
                        .collect(Collectors.toList());
            case "ALL_SIGNING":
                return taskRepository.findAllByTenantIdOrderByIdDesc(tenant()).stream()
                        .filter(x -> !"DRAFT".equals(x.getStatus()))
                        .collect(Collectors.toList());
            case "PROCESS_CENTER": {
                Set<Long> ids = new LinkedHashSet<>();
                ids.addAll(partyRepository.findByUserIdAndPartyStatusIn(userId, PENDING_STATUSES).stream()
                        .map(SignPartyEntity::getTaskId).collect(Collectors.toSet()));
                ids.addAll(inviteRepository.findByTargetUserIdAndInviteStatus(userId, "WAIT").stream()
                        .map(SignInviteEntity::getTaskId).collect(Collectors.toSet()));
                LocalDateTime horizon = LocalDateTime.now().plusDays(3);
                taskRepository.findAllByTenantIdOrderByIdDesc(tenant()).stream()
                        .filter(x -> "SIGNING".equals(x.getStatus()))
                        .filter(x -> x.getExpireAt() != null && x.getExpireAt().isAfter(LocalDateTime.now()) && x.getExpireAt().isBefore(horizon))
                        .forEach(x -> ids.add(x.getId()));
                return tasksByIds(ids);
            }
            case "BATCH_SENT":
            case "QR_SIGNED":
                return List.of();
            default:
                throw new BusinessException("INVALID_VIEW: " + v);
        }
    }

    private List<SignTaskEntity> tasksByIds(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return taskRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(SignTaskEntity::getId).reversed())
                .collect(Collectors.toList());
    }

    public Map<String, Object> counts(Long userId) {
        List<SignTaskEntity> mine = taskRepository.findAllByTenantIdOrderByIdDesc(tenant());
        List<SignTaskEntity> signing = mine.stream().filter(x -> "SIGNING".equals(x.getStatus())).collect(Collectors.toList());
        Set<Long> myPendingTasks = partyRepository.findByUserIdAndPartyStatusIn(userId, List.of("PENDING_SIGN")).stream()
                .map(SignPartyEntity::getTaskId).collect(Collectors.toSet());
        long pendingMine = myPendingTasks.size();
        long pendingOthers = signing.stream().filter(x ->
                partyRepository.findByTaskIdOrderByIdAsc(x.getId()).stream()
                        .anyMatch(p -> "PENDING_SIGN".equals(p.getPartyStatus())
                                && (p.getUserId() == null || !p.getUserId().equals(userId)))).count();
        long expiring = signing.stream()
                .filter(x -> x.getExpireAt() != null && x.getExpireAt().isAfter(LocalDateTime.now())
                        && x.getExpireAt().isBefore(LocalDateTime.now().plusDays(3))).count();
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("pendingMine", pendingMine);
        kpi.put("pendingOthers", pendingOthers);
        kpi.put("expiringSoon", expiring);
        kpi.put("signing", (long) signing.size());
        Map<String, Object> menu = new LinkedHashMap<>();
        menu.put("pendingReceive", (long) inviteRepository.findByTargetUserIdAndInviteStatus(userId, "WAIT").size());
        menu.put("pendingMe", pendingMine);
        menu.put("allSigning", mine.stream().filter(x -> !"DRAFT".equals(x.getStatus())).count());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kpi", kpi);
        out.put("menu", menu);
        return out;
    }

    /** 拒签（S2'）：必签方拒签 → REJECTED → 自动收敛 TERMINATED（prd10 §5.2/§7.3）。 */
    @Transactional
    public SignTaskEntity reject(Long taskId, Long partyId, Long actorId, String reason) {
        // P0/A1：拒签理由为法律证据，服务端硬性强制非空（不只依赖前端拦截）
        if (reason == null || reason.isBlank()) throw new BusinessException("拒签理由不能为空");
        SignTaskEntity t = taskForActor(taskId, actorId);
        SignPartyEntity p = partyRepository.findById(partyId).orElseThrow();
        if (!p.getTaskId().equals(t.getId())) throw new BusinessException("参与方不属于该任务");
        // 严格本人：userId 为空的未认领外部方不可被任何人代拒
        if (!Objects.equals(p.getUserId(), actorId)) throw new BusinessException("只能拒签本人的参与方");
        if (!PENDING_STATUSES.contains(p.getPartyStatus())) throw new BusinessException("参与方状态不可拒签: " + p.getPartyStatus());
        p.setPartyStatus("REJECTED");
        partyRepository.save(p);
        audit("SIGN_PARTY_REJECT", "sign_party", p.getId(), actorId);
        SignTaskEntity r = stateMachine.transfer(t.getId(), "REJECT", "USER", actorId, reason);
        return stateMachine.transfer(r.getId(), "AUTO_TERMINATE", "SYSTEM", actorId, "required signer rejected");
    }

    /** 延期（S2'）：EXPIRED → SIGNING + 新截止（prd10 §7.4）；仅发起方可延期。截止时间安全解析，非法格式 → 400。 */
    @Transactional
    public SignTaskEntity extend(Long taskId, String newExpireRaw, Long actorId) {
        SignTaskEntity t = task(taskId);
        if (!Objects.equals(t.getCreatedBy(), actorId)) throw new BusinessException("仅任务发起方可延期");
        LocalDateTime newExpire = SignTimeUtil.parseDeadline(newExpireRaw);
        if (newExpire == null) throw new BusinessException("请提供新的截止时间");
        if (newExpire.isBefore(LocalDateTime.now())) throw new BusinessException("截止日期不能早于现在");
        if (t.getProviderFlowId() != null && !t.getProviderFlowId().isBlank() && cpProperties.isEnabled()) {
            long epoch = newExpire.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            cpSigningClient.extendFlow(t.getProviderFlowId(), epoch, null);
        }
        t.setExpireAt(newExpire);
        taskRepository.save(t);
        return stateMachine.transfer(t.getId(), "EXTEND", "USER", actorId, "extend deadline");
    }

    /** 已完成任务申请解约。厂商受理后进入作废中，完成靠回调。 */
    @Transactional
    public SignTaskEntity startVoid(Long taskId, Long actorId, String reason) {
        SignTaskEntity t = task(taskId);
        if (!Objects.equals(t.getCreatedBy(), actorId)) throw new BusinessException("仅任务发起方可申请解约");
        if (!"COMPLETED".equals(t.getStatus())) throw new BusinessException("只有已完成的任务可以申请解约");
        if (t.getProviderFlowId() != null && !t.getProviderFlowId().isBlank() && cpProperties.isEnabled()) {
            cpSigningClient.rescindFlow(t.getProviderFlowId(), reason, null);
        }
        return stateMachine.transfer(t.getId(), "START_VOID", "USER", actorId, reason == null ? "申请解约" : reason);
    }

    private String signerAccount(SignPartyEntity p) {
        if (p.getExternalPhone() != null && !p.getExternalPhone().isBlank()) return p.getExternalPhone();
        if (p.getExternalEmail() != null && !p.getExternalEmail().isBlank()) return p.getExternalEmail();
        if (p.getUserId() != null) {
            return userRepository.findById(p.getUserId())
                    .map(u -> u.getPhone() != null && !u.getPhone().isBlank() ? u.getPhone() : u.getEmail())
                    .orElse(null);
        }
        return null;
    }

    /** 撤回：已提交且尚未完成签署（CREATED/FILLING/FINALIZING/SIGNING）→ REVOKED。 */
    @Transactional
    public SignTaskEntity revoke(Long taskId, Long actorId) {
        SignTaskEntity t = task(taskId);
        if (!t.getCreatedBy().equals(actorId)) throw new BusinessException("仅发起方可撤回");
        if (t.getProviderFlowId() != null && !t.getProviderFlowId().isBlank() && cpProperties.isEnabled()
                && "SIGNING".equals(t.getStatus())) {
            cpSigningClient.revokeFlow(t.getProviderFlowId(), "发起方撤回", null);
        }
        return stateMachine.transfer(t.getId(), "REVOKE", "USER", actorId, "initiator revoked");
    }

    /**
     * 可经 /transition 驱动的用户触发器。REVOKE/EXTEND/REJECT 走各自专用端点（含参与方状态联动），
     * AUTO_TERMINATE 为系统触发器，均不允许由本接口驱动。
     */
    private static final Set<String> USER_TRANSITION_TRIGGERS = Set.of("SUBMIT", "START", "COMPLETE", "TERMINATE");

    /**
     * /transition 的授权闸门：仅任务发起方可推进本租户任务；触发器须在白名单内，且状态机边必须合法。
     * 返回已授权任务，供调用方（如 CP 送签 chokepoint）在同一授权结论下继续动作——
     * 边预检在此完成，确保非法转移不会白耗外部配额。
     */
    public SignTaskEntity authorizeTransition(Long taskId, String trigger, Long actorId) {
        if (!USER_TRANSITION_TRIGGERS.contains(trigger)) {
            throw new BusinessException("该触发器不可由此接口驱动，请使用专用端点: " + trigger);
        }
        SignTaskEntity t = task(taskId);
        if (!Objects.equals(t.getCreatedBy(), actorId)) throw new BusinessException("仅任务发起方可推进该任务");
        stateMachine.assertTransferable(t.getStatus(), trigger);
        return t;
    }

    /** 管理域状态推进：授权后经状态机转移（transfer 内部按 id 加载以支持跨企业参与方场景）。 */
    @Transactional
    public SignTaskEntity transition(Long taskId, String trigger, Long actorId, String reason) {
        SignTaskEntity t = authorizeTransition(taskId, trigger, actorId);
        // 完成闸门：至少一名 SIGNER 且全部已签——空签署人集合不得判为"完成"
        if ("COMPLETE".equals(trigger) && !allSignersSigned(t.getId())) {
            throw new BusinessException("尚未全部签署完成，不可置为 COMPLETED");
        }
        return stateMachine.transfer(t.getId(), trigger, "USER", actorId, reason);
    }

    /** 必签方是否全部已签（至少一名 SIGNER；空集合恒为 false）。 */
    private boolean allSignersSigned(Long taskId) {
        List<SignPartyEntity> signers = partyRepository.findByTaskIdOrderByIdAsc(taskId).stream()
                .filter(x -> "SIGNER".equals(x.getPartyRole()))
                .collect(Collectors.toList());
        return !signers.isEmpty() && signers.stream().allMatch(x -> "SIGNED".equals(x.getPartyStatus()));
    }

    public List<SignPartyEntity> parties(Long taskId, Long actor) {
        taskForActor(taskId, actor);
        return partyRepository.findByTaskIdOrderByIdAsc(taskId);
    }

    /**
     * 送签用的签署人描述（供 CP 转 e签宝 create-by-file.signers）。
     * 企业章路线：内部成员参与方（partyType=ORG 且绑定成员）解析其所属企业的 e签宝机构号 →
     * 发 {signerType:ORG, orgId}（企业盖章）；无机构号则退回个人签署（account=手机号/邮箱）。
     */
    public List<Map<String, Object>> signersForProvider(Long taskId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SignPartyEntity p : partyRepository.findByTaskIdOrderByIdAsc(taskId)) {
            boolean signing = Boolean.TRUE.equals(p.getCanSign()) || "SIGNER".equals(p.getPartyRole());
            if (!signing) continue;
            String orgId = "ORG".equals(p.getPartyType()) && p.getMemberUserId() != null
                    ? resolveEsignOrgId(p.getMemberUserId()) : null;
            Map<String, Object> m = new LinkedHashMap<>();
            if (orgId != null) {
                m.put("signerType", "ORG");
                m.put("orgId", orgId);
            } else {
                m.put("signerType", "PERSON");
                m.put("account", p.getExternalPhone());
                m.put("phone", p.getExternalPhone());
                m.put("email", p.getExternalEmail());
                // e签宝建流程要求个人签署方提供姓名（账号在 e签宝侧未注册时必需）
                m.put("name", p.getExternalName());
            }
            m.put("signOrder", p.getSignOrder() == null ? 1 : p.getSignOrder());
            out.add(m);
        }
        return out;
    }

    /** 用户 → 其所属企业 → e签宝机构号；任一缺失返回 null（退回个人签署）。 */
    private String resolveEsignOrgId(Long memberUserId) {
        return userRepository.findById(memberUserId)
                .map(UserEntity::getCompanyId)
                .flatMap(companyRepository::findById)
                .map(CompanyEntity::getEsignOrgId)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
    }

    public List<SignInviteEntity> myInvites(Long userId) {
        return inviteRepository.findByTargetUserIdAndInviteStatus(userId, "WAIT");
    }

    @Transactional
    public Map<String, Object> download(Long taskId, Long actorId) {
        SignTaskEntity t = taskForActor(taskId, actorId);
        if (!"COMPLETED".equals(t.getStatus()) && !"VOIDED".equals(t.getStatus())) {
            throw new BusinessException("任务未完成，不可下载: " + t.getStatus());
        }
        DataAccessLogEntity d = new DataAccessLogEntity();
        d.setActorType("USER");
        d.setActorId(actorId);
        d.setEntityType("sign_task");
        d.setEntityId(t.getId());
        d.setAccessType("DOWNLOAD");
        dataAccessLogRepository.save(d);
        Map<String, Object> out = esignRecord(t);
        if (t.getProviderFlowId() != null && cpProperties.isEnabled()) {
            try {
                out.putAll(cpSigningClient.downloadFlow(t.getProviderFlowId(), null));
            } catch (BusinessException e) {
                out.put("message", e.getMessage());
            }
        }
        certificateRecordRepository.findByTaskId(t.getId()).ifPresent(rec -> {
            out.put("certNo", rec.getCertNo());
            out.put("issuedAt", rec.getIssuedAt().toString());
            out.put("authority", rec.getAuthority());
        });
        return out;
    }

    /**
     * 出证（M1 出证线）：仅 COMPLETED 可出证；写 CERT_ISSUE 留痕。
     * 出证为一次性事件：首次落库 certificate_record（一任务一证），重复调用返回同一记录，certNo/issuedAt 稳定。
     */
    @Transactional
    public Map<String, Object> certificate(Long taskId, Long actorId) {
        SignTaskEntity t = taskForActor(taskId, actorId);
        if (!"COMPLETED".equals(t.getStatus())) throw new BusinessException("任务未完成，不可出证: " + t.getStatus());
        DataAccessLogEntity d = new DataAccessLogEntity();
        d.setActorType("USER");
        d.setActorId(actorId);
        d.setEntityType("sign_task");
        d.setEntityId(t.getId());
        d.setAccessType("CERT_ISSUE");
        dataAccessLogRepository.save(d);

        Map<String, Object> providerCert = esignRecord(t);
        CertificateRecordEntity rec = certificateRecordRepository.findByTaskId(t.getId()).orElseGet(() -> {
            CertificateRecordEntity n = new CertificateRecordEntity();
            n.setTenantId(t.getTenantId());
            n.setTaskId(t.getId());
            n.setCertNo("CERT-" + t.getTaskNo());
            n.setAuthority("e签宝");
            n.setProvider("esign-saas-v3");
            n.setProviderTaskId(t.getProviderFlowId());
            n.setProviderCertNo(null);
            n.setDocSha256(primaryDocSha256(t.getId()));
            n.setIssuedBy(actorId);
            n.setIssuedAt(java.time.LocalDateTime.now());
            return certificateRecordRepository.save(n);
        });

        Map<String, Object> cert = new java.util.LinkedHashMap<>(providerCert);
        cert.put("certNo", rec.getCertNo());
        cert.put("issuedAt", rec.getIssuedAt().toString());
        cert.put("authority", rec.getAuthority());
        cert.put("certRecordId", rec.getId());
        return cert;
    }

    /** 出证元数据。文件地址只来自 e签宝下载接口，这里不造第二家厂商的证明。 */
    private static Map<String, Object> esignRecord(SignTaskEntity task) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", "esign-saas-v3");
        m.put("providerTaskId", task.getProviderFlowId());
        m.put("authority", "e签宝");
        return m;
    }

    /** 出证/送签绑定的文档指纹：任务下第一份签署文档的 sha256（无文档时为空）。 */
    private String primaryDocSha256(Long taskId) {
        return signDocRepository.findAllByTaskIdOrderByIdAsc(taskId).stream()
                .findFirst().map(com.jyfc.backend.module.signdoc.entity.SignDocEntity::getSha256)
                .orElse(null);
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
