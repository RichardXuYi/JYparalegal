package com.jyfc.backend.module.sign.controller;

import com.jyfc.backend.core.cp.CpProperties;
import com.jyfc.backend.core.cp.CpSigningClient;
import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.account.service.SignQuotaService;
import com.jyfc.backend.module.sign.entity.SignInviteEntity;
import com.jyfc.backend.module.sign.entity.SignPartyEntity;
import com.jyfc.backend.module.sign.entity.SignTaskEntity;
import com.jyfc.backend.module.sign.repository.SignTaskRepository;
import com.jyfc.backend.module.sign.service.SignDraftService;
import com.jyfc.backend.module.sign.service.SignFlowService;
import com.jyfc.backend.module.signdoc.repository.SignDocRepository;
import com.jyfc.backend.module.signdoc.service.SignDocService;
import com.jyfc.backend.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 签署域 REST（S1'；契约见 docs/04-API重基线-V3.md §2）。
 * 租户应用层强制（D12）；actor 取当前登录用户；/api/** 默认 authenticated。
 *
 * <p><b>D16 追加</b>：送签（SUBMIT）的配额强制点按 {@code jy.cp.mode} 分流——
 * {@code required}（默认）经 CP 在线裁决并且 <b>CP 不可达即送签失败</b>（fail-closed）；
 * {@code local} 由 DP {@code tenant_quota} 本地强制（honor-system）；{@code off} 仅测试。</p>
 */
@RestController
@RequestMapping("/api/sign")
@PreAuthorize("hasAuthority('ROLE_USER')")
public class SignTaskController {

    private final SignTaskRepository taskRepository;
    private final SignFlowService flow;
    private final SignDraftService draft;
    private final UserContextUtil userContextUtil;
    private final CpProperties cpProperties;
    private final CpSigningClient cpSigningClient;
    private final SignQuotaService signQuotaService;
    private final SignDocRepository signDocRepository;
    private final SignDocService signDocService;

    public SignTaskController(SignTaskRepository taskRepository,
                              SignFlowService flow, SignDraftService draft, UserContextUtil userContextUtil,
                              CpProperties cpProperties, CpSigningClient cpSigningClient,
                              SignQuotaService signQuotaService, SignDocRepository signDocRepository,
                              SignDocService signDocService) {
        this.taskRepository = taskRepository;
        this.flow = flow;
        this.draft = draft;
        this.userContextUtil = userContextUtil;
        this.cpProperties = cpProperties;
        this.cpSigningClient = cpSigningClient;
        this.signQuotaService = signQuotaService;
        this.signDocRepository = signDocRepository;
        this.signDocService = signDocService;
    }

    private void requireTenant() {
        Long t = JyTenantContext.get();
        if (t == null || JyTenantContext.ROOT_TENANT_ID.equals(t)) {
            throw new com.jyfc.backend.core.exception.BusinessException("租户上下文缺失：当前用户未绑定 tenant，无法操作法律域");
        }
    }

    @PostMapping("/tasks")
    public ApiResponse<SignTaskEntity> create(@RequestBody Map<String, Object> body) {
        requireTenant();
        Long userId = userContextUtil.getCurrentUserId();
        SignTaskEntity t = new SignTaskEntity();
        t.setTitle(String.valueOf(body.getOrDefault("title", "未命名任务")));
        t.setTaskNo("T" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        t.setStatus("DRAFT");
        t.setCreatedBy(userId);
        // 公司归属由服务端按管理员授权强制写入，不接受客户端指定（无默认）
        t.setCompanyId(userContextUtil.getAuthorizedCompanyId());
        if (body.get("expireAt") != null) t.setExpireAt(java.time.LocalDateTime.parse(String.valueOf(body.get("expireAt"))));
        if (body.get("finalizeMode") != null) {
            String mode = String.valueOf(body.get("finalizeMode"));
            if (!"AUTO".equals(mode) && !"MANUAL".equals(mode)) {
                throw new com.jyfc.backend.core.exception.BusinessException("finalizeMode 仅支持 AUTO / MANUAL: " + mode);
            }
            t.setFinalizeMode(mode);
        }
        return ApiResponse.success(taskRepository.save(t));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<SignTaskEntity>> list(@RequestParam(required = false) String view) {
        requireTenant();
        return ApiResponse.success(flow.listByView(view, userContextUtil.getCurrentUserId()));
    }

    @GetMapping("/tasks/inbox/counts")
    public ApiResponse<Map<String, Object>> counts() {
        requireTenant();
        return ApiResponse.success(flow.counts(userContextUtil.getCurrentUserId()));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<SignTaskEntity> get(@PathVariable Long id) {
        requireTenant();
        return ApiResponse.success(flow.taskForActor(id, userContextUtil.getCurrentUserId()));
    }

    @PostMapping("/tasks/{id}/transition")
    public ApiResponse<SignTaskEntity> transition(@PathVariable Long id, @RequestBody Map<String, String> body,
                                                  HttpServletRequest request) {
        requireTenant();
        Long userId = userContextUtil.getCurrentUserId();
        String trigger = body.getOrDefault("trigger", "");
        // 授权先行（含状态机边预检）：非法转移不得白耗外部配额
        SignTaskEntity authorized = flow.authorizeTransition(id, trigger, userId);
        if ("SUBMIT".equals(trigger)) chargeForSigning(authorized, request);
        return ApiResponse.success(flow.transition(id, trigger, userId, body.get("reason")));
    }

    @PatchMapping("/tasks/{id}")
    public ApiResponse<SignTaskEntity> patchDraft(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireTenant();
        return ApiResponse.success(draft.patchDraft(id, body, userContextUtil.getCurrentUserId()));
    }

    @PutMapping("/tasks/{id}/parties")
    public ApiResponse<List<SignPartyEntity>> replaceParties(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireTenant();
        return ApiResponse.success(draft.replaceParties(id, partyRows(body), userContextUtil.getCurrentUserId()));
    }

    @PostMapping("/tasks/{id}/submit")
    public ApiResponse<SignTaskEntity> submit(@PathVariable Long id, HttpServletRequest request) {
        requireTenant();
        Long userId = userContextUtil.getCurrentUserId();
        if ("SIGNING".equals(draft.peekSubmitTarget(id, userId))) {
            chargeForSigning(flow.taskForActor(id, userId), request);
        }
        return ApiResponse.success(draft.submit(id, userId));
    }

    @PostMapping("/tasks/{id}/finish-fill")
    public ApiResponse<SignTaskEntity> finishFill(@PathVariable Long id, HttpServletRequest request) {
        requireTenant();
        Long userId = userContextUtil.getCurrentUserId();
        if ("SIGNING".equals(draft.peekAfterFill(id, userId))) {
            chargeForSigning(flow.taskForActor(id, userId), request);
        }
        return ApiResponse.success(draft.finishFill(id, userId));
    }

    @PostMapping("/tasks/{id}/confirm-final")
    public ApiResponse<SignTaskEntity> confirmFinal(@PathVariable Long id, HttpServletRequest request) {
        requireTenant();
        Long userId = userContextUtil.getCurrentUserId();
        chargeForSigning(flow.taskForActor(id, userId), request);
        return ApiResponse.success(draft.confirmFinal(id, userId));
    }

    /** 进入可签署态之前扣配额并记下 e签宝流程号。同一任务只建一次流程。 */
    private void chargeForSigning(SignTaskEntity task, HttpServletRequest request) {
        if (task.getProviderFlowId() != null && !task.getProviderFlowId().isBlank()) {
            return;
        }
        if (cpProperties.isEnabled()) {
            java.util.Map<String, Object> doc = signDocService.primaryDocForUpload(task.getId());
            String fileName = doc == null ? null : String.valueOf(doc.get("fileName"));
            String fileBase64 = doc == null ? null : String.valueOf(doc.get("contentBase64"));
            java.util.List<java.util.Map<String, Object>> signers = flow.signersForProvider(task.getId());
            java.util.Map<String, Object> issued = cpSigningClient.execute(task.getTaskNo(), task.getTitle(),
                    primaryDocSha256(task.getId()), fileName, fileBase64, signers, bearerToken(request));
            Object flowId = issued.get("providerTaskId");
            if (flowId != null) {
                task.setProvider(String.valueOf(issued.getOrDefault("provider", "esign-saas-v3")));
                task.setProviderFlowId(String.valueOf(flowId));
                Object mode = issued.get("esignMode");
                if (mode != null) task.setProviderMode(String.valueOf(mode));
                taskRepository.save(task);
            }
        } else if (cpProperties.isLocalQuota()) {
            signQuotaService.assertCanSign(JyTenantContext.get());
        }
    }

    private static List<Map<String, Object>> partyRows(Map<String, Object> body) {
        Object raw = body.get("parties");
        if (!(raw instanceof List<?> list)) {
            throw new com.jyfc.backend.core.exception.BusinessException("parties 必须是数组");
        }
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new com.jyfc.backend.core.exception.BusinessException("参与方格式不正确");
            }
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> row.put(String.valueOf(k), v));
            rows.add(row);
        }
        return rows;
    }

    /** 送签绑定的文档指纹：取任务下第一份签署文档的 sha256（无文档时为空）。 */
    private String primaryDocSha256(Long taskId) {
        return signDocRepository.findAllByTaskIdOrderByIdAsc(taskId).stream()
                .findFirst().map(com.jyfc.backend.module.signdoc.entity.SignDocEntity::getSha256)
                .orElse(null);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
    }

    @GetMapping("/tasks/{id}/parties")
    public ApiResponse<List<SignPartyEntity>> parties(@PathVariable Long id) {
        requireTenant();
        return ApiResponse.success(flow.parties(id, userContextUtil.getCurrentUserId()));
    }

    @PostMapping("/tasks/{id}/parties")
    public ApiResponse<Map<String, Object>> addParty(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireTenant();
        Long userId = userContextUtil.getCurrentUserId();
        Long targetUser = body.get("userId") != null ? Long.valueOf(String.valueOf(body.get("userId"))) : null;
        Integer order = body.get("signOrder") != null ? Integer.valueOf(String.valueOf(body.get("signOrder"))) : 1;
        return ApiResponse.success(flow.addParty(id, targetUser,
                body.get("externalName") != null ? String.valueOf(body.get("externalName")) : null,
                body.get("externalPhone") != null ? String.valueOf(body.get("externalPhone")) : null,
                body.get("partyRole") != null ? String.valueOf(body.get("partyRole")) : "SIGNER",
                order, userId));
    }

    @GetMapping("/invites")
    public ApiResponse<List<SignInviteEntity>> myInvites() {
        requireTenant();
        return ApiResponse.success(flow.myInvites(userContextUtil.getCurrentUserId()));
    }

    @PostMapping("/invites/{id}/accept")
    public ApiResponse<SignInviteEntity> acceptInvite(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, String> body) {
        requireTenant();
        String token = body != null ? body.get("token") : null;
        return ApiResponse.success(flow.acceptInvite(id, userContextUtil.getCurrentUserId(), token));
    }

    @PostMapping("/tasks/{id}/parties/{pid}/sign")
    public ApiResponse<java.util.Map<String, Object>> sign(@PathVariable Long id, @PathVariable Long pid,
                                                           HttpServletRequest request) {
        requireTenant();
        return ApiResponse.success(flow.openSign(id, pid, userContextUtil.getCurrentUserId(), bearerToken(request)));
    }

    @PostMapping("/tasks/{id}/void")
    public ApiResponse<SignTaskEntity> startVoid(@PathVariable Long id,
                                                 @RequestBody(required = false) java.util.Map<String, String> body) {
        requireTenant();
        String reason = body != null ? body.get("reason") : null;
        return ApiResponse.success(flow.startVoid(id, userContextUtil.getCurrentUserId(), reason));
    }

    @PostMapping("/tasks/{id}/parties/{pid}/reject")
    public ApiResponse<SignTaskEntity> reject(@PathVariable Long id, @PathVariable Long pid,
                                              @RequestBody(required = false) Map<String, String> body) {
        requireTenant();
        return ApiResponse.success(flow.reject(id, pid, userContextUtil.getCurrentUserId(),
                body != null ? body.get("reason") : null));
    }

    @PostMapping("/tasks/{id}/extend")
    public ApiResponse<SignTaskEntity> extend(@PathVariable Long id, @RequestBody Map<String, String> body) {
        requireTenant();
        return ApiResponse.success(flow.extend(id, body.get("expireAt"), userContextUtil.getCurrentUserId()));
    }

    @PostMapping("/tasks/{id}/revoke")
    public ApiResponse<SignTaskEntity> revoke(@PathVariable Long id) {
        requireTenant();
        return ApiResponse.success(flow.revoke(id, userContextUtil.getCurrentUserId()));
    }

    @GetMapping("/tasks/{id}/download")
    public ApiResponse<Map<String, Object>> download(@PathVariable Long id) {
        requireTenant();
        return ApiResponse.success(flow.download(id, userContextUtil.getCurrentUserId()));
    }

    @PostMapping("/tasks/{id}/certificate")
    public ApiResponse<Map<String, Object>> certificate(@PathVariable Long id) {
        requireTenant();
        return ApiResponse.success(flow.certificate(id, userContextUtil.getCurrentUserId()));
    }
}
