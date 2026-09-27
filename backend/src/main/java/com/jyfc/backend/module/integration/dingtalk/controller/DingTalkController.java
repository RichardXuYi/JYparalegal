package com.jyfc.backend.module.integration.dingtalk.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.integration.dingtalk.config.DingTalkProperties;
import com.jyfc.backend.module.integration.dingtalk.entity.UserDingTalkBinding;
import com.jyfc.backend.module.integration.dingtalk.repository.UserDingTalkBindingRepository;
import com.jyfc.backend.module.integration.dingtalk.service.DingTalkApprovalService;
import com.jyfc.backend.module.integration.dingtalk.service.DingTalkAuthService;
import com.jyfc.backend.module.integration.dingtalk.service.DingTalkMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 钉钉集成 REST 控制器。
 * <p>
 * 提供钉钉 OAuth 登录、消息推送、审批流对接等 API。
 */
@Tag(name = "钉钉集成", description = "钉钉OAuth登录、消息推送、审批流对接API")
@RestController
@RequestMapping("/api/integration/dingtalk")
public class DingTalkController {

    private static final Logger log = LoggerFactory.getLogger(DingTalkController.class);

    private final DingTalkProperties properties;
    private final DingTalkAuthService authService;
    private final DingTalkMessageService messageService;
    private final DingTalkApprovalService approvalService;
    private final UserDingTalkBindingRepository bindingRepository;

    @Autowired
    public DingTalkController(DingTalkProperties properties,
                              DingTalkAuthService authService,
                              DingTalkMessageService messageService,
                              DingTalkApprovalService approvalService,
                              UserDingTalkBindingRepository bindingRepository) {
        this.properties = properties;
        this.authService = authService;
        this.messageService = messageService;
        this.approvalService = approvalService;
        this.bindingRepository = bindingRepository;
    }

    // ==================== OAuth 登录 ====================

    /**
     * 生成钉钉扫码登录 URL。
     *
     * @param state 防 CSRF 状态参数（可选）
     * @return 扫码登录 URL
     */
    @GetMapping("/qrcode")
    public ResponseEntity<ApiResponse<Map<String, String>>> getQrCodeUrl(
            @RequestParam(required = false) String state) {
        checkEnabled();
        String url = authService.getLoginQrCodeUrl(null, state);
        return ResponseEntity.ok(ApiResponse.success(Map.of("qrCodeUrl", url)));
    }

    /**
     * OAuth 授权码换登录态，绑定到 JYFC 用户。
     * <p>
     * 流程：前端扫码后获取 code → 调用此接口 → 获取钉钉用户信息 → 绑定到当前登录的 JYFC 用户
     *
     * @param body 包含 code 的请求体
     * @return 绑定结果
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> body) {
        checkEnabled();

        String code = body.get("code");
        if (code == null || code.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "缺少授权码 code"));
        }

        try {
            // 通过 code 获取钉钉用户详情
            Map<String, Object> userDetail = authService.getUserDetailByCode(code);
            String unionId = (String) userDetail.get("unionId");

            // 检查是否已绑定
            bindingRepository.findByDingtalkUnionId(unionId).ifPresent(binding -> {
                // 更新 token 信息
                binding.setAccessToken((String) userDetail.get("accessToken"));
                binding.setRefreshToken((String) userDetail.get("refreshToken"));
                binding.setTokenExpiresAt(LocalDateTime.now().plusSeconds(
                        ((Number) userDetail.get("expiresIn")).longValue()));
                bindingRepository.save(binding);
            });

            return ResponseEntity.ok(ApiResponse.success("钉钉用户信息获取成功", Map.of(
                    "unionId", unionId,
                    "nick", userDetail.get("nick"),
                    "openId", userDetail.get("openId"),
                    "dingId", userDetail.get("dingId")
            )));
        } catch (Exception e) {
            log.error("钉钉登录失败", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "钉钉登录失败: " + e.getMessage()));
        }
    }

    /**
     * 获取当前绑定用户的钉钉信息。
     *
     * @return 钉钉绑定信息
     */
    @GetMapping("/userinfo")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserInfo() {
        checkEnabled();

        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(401, "未登录"));
        }

        return bindingRepository.findByUserId(userId)
                .map(binding -> {
                    Map<String, Object> data = new java.util.HashMap<>();
                    data.put("userId", binding.getUserId());
                    data.put("dingtalkUnionId", binding.getDingtalkUnionId());
                    data.put("dingtalkUserId", binding.getDingtalkUserId() != null ? binding.getDingtalkUserId() : "");
                    data.put("dingtalkMobile", binding.getDingtalkMobile() != null ? binding.getDingtalkMobile() : "");
                    data.put("isActive", binding.getIsActive());
                    data.put("boundAt", binding.getCreatedAt().toString());
                    return ResponseEntity.ok(ApiResponse.success(data));
                })
                .orElseGet(() -> {
                    Map<String, Object> data = new java.util.HashMap<>();
                    data.put("bound", false);
                    return ResponseEntity.ok(ApiResponse.success(data));
                });
    }

    /**
     * 绑定钉钉账号到 JYFC 用户。
     * <p>
     * 前端扫码获取 code 后，调用此接口完成绑定。
     *
     * @param body 包含 code 的请求体
     * @return 绑定结果
     */
    @PostMapping("/bind")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bind(@RequestBody Map<String, String> body) {
        checkEnabled();

        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(401, "未登录"));
        }

        String code = body.get("code");
        if (code == null || code.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "缺少授权码 code"));
        }

        try {
            // 检查是否已绑定
            if (bindingRepository.existsByUserId(userId)) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "该用户已绑定钉钉账号"));
            }

            // 通过 code 获取钉钉用户详情
            Map<String, Object> userDetail = authService.getUserDetailByCode(code);
            String unionId = (String) userDetail.get("unionId");

            // 检查该钉钉账号是否已绑定其他用户
            if (bindingRepository.existsByDingtalkUnionId(unionId)) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "该钉钉账号已绑定其他用户"));
            }

            // 创建绑定关系
            UserDingTalkBinding binding = new UserDingTalkBinding();
            binding.setUserId(userId);
            binding.setDingtalkUnionId(unionId);
            binding.setDingtalkMobile((String) userDetail.get("mobile"));
            binding.setAccessToken((String) userDetail.get("accessToken"));
            binding.setRefreshToken((String) userDetail.get("refreshToken"));
            binding.setTokenExpiresAt(LocalDateTime.now().plusSeconds(
                    ((Number) userDetail.get("expiresIn")).longValue()));
            binding.setIsActive(true);
            bindingRepository.save(binding);

            log.info("用户 {} 绑定钉钉账号成功，unionId: {}", userId, unionId);
            return ResponseEntity.ok(ApiResponse.success("绑定成功", Map.of(
                    "userId", userId,
                    "dingtalkUnionId", unionId,
                    "dingtalkNick", userDetail.get("nick")
            )));
        } catch (Exception e) {
            log.error("绑定钉钉账号失败", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "绑定失败: " + e.getMessage()));
        }
    }

    // ==================== 消息推送 ====================

    /**
     * 发送工作通知。
     *
     * @param body 包含 userId、title、content、url 的请求体
     * @return 发送结果
     */
    @PostMapping("/message/send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendMessage(@RequestBody Map<String, String> body) {
        checkEnabled();

        String userId = body.get("userId");
        String title = body.get("title");
        String content = body.get("content");
        String url = body.get("url");

        if (userId == null || title == null || content == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "缺少必要参数: userId, title, content"));
        }

        Map<String, Object> result = messageService.sendWorkMessage(userId, title, content, url);
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(ApiResponse.success("消息发送成功", result));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "消息发送失败: " + result.get("errmsg")));
        }
    }

    // ==================== 审批流 ====================

    /**
     * 创建钉钉审批实例。
     *
     * @param body 包含 contractId、userId、processCode、formData、title 的请求体
     * @return 审批实例信息
     */
    @PostMapping("/approval/create")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createApproval(@RequestBody Map<String, Object> body) {
        checkEnabled();

        Number contractIdNum = (Number) body.get("contractId");
        String userId = (String) body.get("userId");
        String processCode = (String) body.get("processCode");
        String title = (String) body.get("title");

        if (contractIdNum == null || userId == null || processCode == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "缺少必要参数: contractId, userId, processCode"));
        }

        Long contractId = contractIdNum.longValue();

        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) body.getOrDefault("formData", Map.of());

        try {
            Map<String, Object> result = approvalService.createApprovalProcess(
                    contractId, userId, processCode, formData, title);
            return ResponseEntity.ok(ApiResponse.success("审批创建成功", result));
        } catch (Exception e) {
            log.error("创建审批失败", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "创建审批失败: " + e.getMessage()));
        }
    }

    /**
     * 接收钉钉审批状态回调。
     * <p>
     * 钉钉在审批状态变更时会调用此接口。
     *
     * @param callbackData 回调数据
     * @return 处理结果
     */
    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleCallback(@RequestBody Map<String, Object> callbackData) {
        log.info("收到钉钉审批回调: {}", callbackData);

        try {
            Map<String, Object> result = approvalService.handleApprovalCallback(callbackData);
            return ResponseEntity.ok(ApiResponse.success("回调处理成功", result));
        } catch (Exception e) {
            log.error("处理审批回调失败", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "回调处理失败: " + e.getMessage()));
        }
    }

    /**
     * 查询审批状态。
     *
     * @param approvalId 钉钉审批实例 ID
     * @return 审批状态信息
     */
    @GetMapping("/approval/status/{approvalId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getApprovalStatus(@PathVariable String approvalId) {
        checkEnabled();

        try {
            Map<String, Object> result = approvalService.getApprovalStatus(approvalId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("查询审批状态失败", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(500, "查询失败: " + e.getMessage()));
        }
    }

    // ==================== 辅助方法 ====================

    private void checkEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("钉钉集成未启用，请配置 integration.dingtalk.enabled=true");
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        // 从 principal 中获取用户 ID（具体实现取决于项目的认证机制）
        try {
            Object principal = auth.getPrincipal();
            if (principal instanceof com.jyfc.backend.module.auth.entity.UserEntity user) {
                return user.getId();
            }
            // 如果是 UserDetails 实现，尝试通过反射获取
            return null;
        } catch (Exception e) {
            log.warn("获取当前用户ID失败", e);
            return null;
        }
    }
}
