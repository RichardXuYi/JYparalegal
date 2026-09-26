package com.jyfc.backend.module.integration.feishu.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyfc.backend.module.integration.feishu.config.FeishuProperties;
import com.jyfc.backend.module.integration.feishu.dto.FeishuUserInfo;
import com.jyfc.backend.module.integration.feishu.entity.UserFeishuBinding;
import com.jyfc.backend.module.integration.feishu.exception.FeishuApiException;
import com.jyfc.backend.module.integration.feishu.repository.UserFeishuBindingRepository;
import com.jyfc.backend.module.integration.feishu.security.FeishuSignatureVerifier;
import com.jyfc.backend.module.integration.feishu.service.FeishuApprovalService;
import com.jyfc.backend.module.integration.feishu.service.FeishuAuthService;
import com.jyfc.backend.module.integration.feishu.service.FeishuMessageService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 飞书集成 REST 控制器。
 * <p>
 * 提供飞书 OAuth 登录、消息发送、审批创建、事件回调等接口。
 */
@Tag(name = "飞书集成", description = "飞书OAuth登录、消息发送、审批创建、事件回调API")
@RestController
@RequestMapping("/api/integration/feishu")
public class FeishuController {

    private static final Logger log = LoggerFactory.getLogger(FeishuController.class);

    private final FeishuProperties properties;
    private final FeishuAuthService authService;
    private final FeishuMessageService messageService;
    private final FeishuApprovalService approvalService;
    private final FeishuSignatureVerifier signatureVerifier;
    private final UserFeishuBindingRepository bindingRepository;
    private final ObjectMapper objectMapper;

    public FeishuController(FeishuProperties properties,
                             FeishuAuthService authService,
                             FeishuMessageService messageService,
                             FeishuApprovalService approvalService,
                             FeishuSignatureVerifier signatureVerifier,
                             UserFeishuBindingRepository bindingRepository,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.authService = authService;
        this.messageService = messageService;
        this.approvalService = approvalService;
        this.signatureVerifier = signatureVerifier;
        this.bindingRepository = bindingRepository;
        this.objectMapper = objectMapper;
    }

    // ==================== OAuth 登录 ====================

    /**
     * 获取飞书登录 URL。
     * 前端跳转到此 URL，用户扫码/登录后回调到 redirect_uri。
     */
    @GetMapping("/login-url")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLoginUrl(
            @RequestParam(required = false) String redirectUri,
            @RequestParam(required = false) String state) {

        if (!properties.isEnabled()) {
            return ResponseEntity.ok(ApiResponse.error(503, "飞书集成未启用"));
        }

        String url = authService.getLoginUrl(redirectUri, state);
        return ResponseEntity.ok(ApiResponse.success(Map.of("loginUrl", url)));
    }

    /**
     * OAuth 登录：用 code 换取用户信息并返回绑定状态。
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> body) {
        if (!properties.isEnabled()) {
            return ResponseEntity.ok(ApiResponse.error(503, "飞书集成未启用"));
        }

        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "code 不能为空"));
        }

        try {
            FeishuUserInfo userInfo = authService.getUserInfoByCode(code);

            boolean alreadyBound = bindingRepository.existsByFeishuUnionId(userInfo.getUnionId());

            Map<String, Object> result = new HashMap<>();
            result.put("unionId", userInfo.getUnionId());
            result.put("openId", userInfo.getOpenId());
            result.put("name", userInfo.getName());
            result.put("avatar", userInfo.getAvatar());
            result.put("alreadyBound", alreadyBound);

            return ResponseEntity.ok(ApiResponse.success("飞书登录成功", result));
        } catch (FeishuApiException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 获取当前绑定信息（根据飞书 union_id）。
     */
    @GetMapping("/userinfo")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserInfo(
            @RequestParam String unionId) {

        if (!properties.isEnabled()) {
            return ResponseEntity.ok(ApiResponse.error(503, "飞书集成未启用"));
        }

        var bindingOpt = bindingRepository.findByFeishuUnionId(unionId);
        if (bindingOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(404, "未找到飞书绑定信息"));
        }

        UserFeishuBinding binding = bindingOpt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("userId", binding.getUserId());
        result.put("unionId", binding.getFeishuUnionId());
        result.put("openId", binding.getFeishuOpenId());
        result.put("email", binding.getFeishuEmail());
        result.put("mobile", binding.getFeishuMobile());
        result.put("isActive", binding.getIsActive());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 绑定本地账号与飞书身份。
     * 关联用户表中的 user_id 与飞书的 union_id。
     */
    @PostMapping("/bind")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bindAccount(@RequestBody Map<String, Object> body) {
        if (!properties.isEnabled()) {
            return ResponseEntity.ok(ApiResponse.error(503, "飞书集成未启用"));
        }

        Long userId = body.get("userId") instanceof Number ? ((Number) body.get("userId")).longValue() : null;
        String unionId = (String) body.get("unionId");
        String openId = (String) body.get("openId");
        String email = (String) body.get("email");
        String mobile = (String) body.get("mobile");

        if (userId == null || unionId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "userId 和 unionId 不能为空"));
        }

        if (bindingRepository.existsByUserId(userId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "该用户已绑定了飞书账号"));
        }
        if (bindingRepository.existsByFeishuUnionId(unionId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "该飞书账号已被其他用户绑定"));
        }

        UserFeishuBinding binding = new UserFeishuBinding();
        binding.setUserId(userId);
        binding.setFeishuUnionId(unionId);
        binding.setFeishuOpenId(openId);
        binding.setFeishuEmail(email);
        binding.setFeishuMobile(mobile);
        binding.setIsActive(true);
        bindingRepository.save(binding);

        log.info("用户 {} 绑定飞书账号成功: unionId={}", userId, unionId);
        return ResponseEntity.ok(ApiResponse.success("飞书账号绑定成功", Map.of("id", binding.getId())));
    }

    // ==================== 消息发送 ====================

    /**
     * 发送飞书消息。
     */
    @PostMapping("/message/send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendMessage(@RequestBody Map<String, Object> body) {
        if (!properties.isEnabled()) {
            return ResponseEntity.ok(ApiResponse.error(503, "飞书集成未启用"));
        }

        String receiveId = (String) body.get("receiveId");
        String receiveIdType = (String) body.getOrDefault("receiveIdType", "open_id");
        String msgType = (String) body.getOrDefault("msgType", "text");
        String content = (String) body.get("content");

        if (receiveId == null || content == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "receiveId 和 content 不能为空"));
        }

        try {
            Map<String, Object> result = messageService.sendMessage(receiveId, receiveIdType, msgType, content);
            return ResponseEntity.ok(ApiResponse.success("消息发送成功", result));
        } catch (FeishuApiException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ==================== 审批流 ====================

    /**
     * 创建飞书审批实例。
     */
    @PostMapping("/approval/create")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createApproval(@RequestBody Map<String, Object> body) {
        if (!properties.isEnabled()) {
            return ResponseEntity.ok(ApiResponse.error(503, "飞书集成未启用"));
        }

        String approvalCode = (String) body.get("approvalCode");
        String userId = (String) body.get("userId");
        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) body.get("formData");

        if (approvalCode == null || userId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "approvalCode 和 userId 不能为空"));
        }

        try {
            Map<String, Object> result = approvalService.createApproval(approvalCode, formData, userId);
            return ResponseEntity.ok(ApiResponse.success("审批创建成功", result));
        } catch (FeishuApiException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ==================== 事件回调 ====================

    /**
     * 飞书事件回调入口。
     * <p>
     * 处理以下事件：
     * - URL challenge 验证（首次配置事件回调时触发）
     * - 审批实例状态变更推送
     * - 消息接收等
     * <p>
     * <strong>注意：</strong>此接口接收原始字符串请求体以支持签名验证，
     * 内部通过 Jackson 反序列化为 Map 供业务逻辑使用。
     * 此接口应对外公开（无需认证），由飞书开放平台调用。
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Lark-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Lark-Request-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Lark-Signature", required = false) String signature) {

        if (!properties.isEnabled()) {
            return ResponseEntity.ok(Map.of("error", "飞书集成未启用"));
        }

        try {
            // 将 raw body JSON 解析为 Map
            Map<String, Object> body = objectMapper.readValue(rawBody,
                    new TypeReference<Map<String, Object>>() {});

            // 1. 检查是否是 URL challenge 验证
            if ("url_verification".equals(body.get("type"))) {
                log.info("收到飞书 URL challenge 验证请求");
                Map<String, Object> challengeResponse = signatureVerifier.handleUrlChallenge(body);
                return ResponseEntity.ok(challengeResponse);
            }

            // 2. 处理加密 payload（当配置了 encrypt_key 时）
            String encryptField = (String) body.get("encrypt");
            if (encryptField != null && !encryptField.isBlank()) {
                // 验证签名（使用原始 encrypt 字符串作为签名数据）
                if (signature != null && timestamp != null && nonce != null) {
                    boolean valid = signatureVerifier.verifySignature(timestamp, nonce, encryptField, signature);
                    if (!valid) {
                        log.warn("飞书加密事件签名验证失败，已拒绝");
                        return ResponseEntity.status(403).body(Map.of("error", "Invalid signature"));
                    }
                }

                // 解密
                String plainText = signatureVerifier.decryptPayload(encryptField);
                if (plainText == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Decryption failed"));
                }

                log.info("收到飞书加密事件，解密长度: {} 字符", plainText.length());
                // TODO: 解析解密后的事件内容并分发给对应处理器
                // Map<String, Object> eventData = objectMapper.readValue(plainText, new TypeReference<>() {});
                // handleFeishuEvent(eventData);

                return ResponseEntity.ok(Map.of());
            }

            // 3. 处理未加密的事件推送
            if (signature != null && timestamp != null && nonce != null) {
                boolean valid = signatureVerifier.verifySignature(timestamp, nonce, rawBody, signature);
                if (!valid) {
                    log.warn("飞书事件签名验证失败，已拒绝");
                    return ResponseEntity.status(403).body(Map.of("error", "Invalid signature"));
                }
            }

            // 4. 分发事件处理
            log.info("收到飞书事件推送: type={}", body.get("type"));

            // TODO: 根据 event_type 分发到具体业务处理器
            // 审批事件示例：
            // if ("approval_instance".equals(body.get("type"))) {
            //     approvalService.parseApprovalEvent(body);
            //     // 更新合同审批状态
            // }

            // 飞书要求回调必须返回 200
            return ResponseEntity.ok(Map.of());

        } catch (Exception e) {
            log.error("飞书事件回调处理异常", e);
            // 飞书要求回调返回 200（即使处理失败，否则会持续重试）
            return ResponseEntity.ok(Map.of("status", "accepted", "error", e.getMessage()));
        }
    }
}
