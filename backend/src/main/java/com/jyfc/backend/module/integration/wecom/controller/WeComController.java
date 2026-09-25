package com.jyfc.backend.module.integration.wecom.controller;

import com.jyfc.backend.module.integration.wecom.config.WeComProperties;
import com.jyfc.backend.module.integration.wecom.service.*;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 企业微信集成 REST 控制器
 *
 * 提供企业微信相关 API 端点，包括：
 * - OAuth 登录授权
 * - 通讯录同步
 * - 消息推送
 * - 审批流程
 * - JS-SDK 配置
 * - 事件回调
 */
@Tag(name = "企业微信集成", description = "企业微信OAuth登录、通讯录同步、消息推送、审批流程API")
@RestController
@RequestMapping("/api/integration/wecom")
public class WeComController {

    private static final Logger log = LoggerFactory.getLogger(WeComController.class);

    private final WeComProperties weComProperties;
    private final WeComAuthService weComAuthService;
    private final WeComContactService weComContactService;
    private final WeComMessageService weComMessageService;
    private final WeComApprovalService weComApprovalService;
    private final WeComJsSdkService weComJsSdkService;

    public WeComController(WeComProperties weComProperties,
                           WeComAuthService weComAuthService,
                           WeComContactService weComContactService,
                           WeComMessageService weComMessageService,
                           WeComApprovalService weComApprovalService,
                           WeComJsSdkService weComJsSdkService) {
        this.weComProperties = weComProperties;
        this.weComAuthService = weComAuthService;
        this.weComContactService = weComContactService;
        this.weComMessageService = weComMessageService;
        this.weComApprovalService = weComApprovalService;
        this.weComJsSdkService = weComJsSdkService;
    }

    // ==================== OAuth 登录 ====================

    /**
     * 生成企业微信登录 URL
     *
     * 前端跳转到该 URL 后进入企业微信 OAuth 授权页。
     *
     * @param state  携带的状态参数（防 CSRF）
     * @param scope  授权范围（base/userinfo）
     * @return 登录 URL
     */
    @GetMapping("/login-url")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLoginUrl(
            @RequestParam(defaultValue = "") String state,
            @RequestParam(defaultValue = "base") String scope) {

        if (!weComProperties.isEnabled()) {
            return ResponseEntity.ok(ApiResponse.error(400, "企业微信集成未启用"));
        }

        String loginUrl = weComAuthService.getAuthorizeUrl(null, state, scope);
        return ResponseEntity.ok(ApiResponse.success(Map.of("loginUrl", loginUrl)));
    }

    /**
     * 企业微信登录（code 换登录态）
     *
     * 企业微信 OAuth 授权回调后，前端通过 code 获取用户信息并完成登录/绑定。
     *
     * @param body 请求体，包含 code
     * @return 登录结果
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "code 不能为空"));
        }

        try {
            Map<String, Object> userInfo = weComAuthService.getUserInfoByCode(code);
            log.info("企业微信登录成功: userid={}", userInfo.get("UserId"));
            return ResponseEntity.ok(ApiResponse.success("登录成功", userInfo));
        } catch (Exception e) {
            log.error("企业微信登录失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "登录失败: " + e.getMessage()));
        }
    }

    /**
     * 获取当前绑定的企业微信用户信息
     *
     * @param userId 系统用户 ID
     * @return 绑定信息
     */
    @GetMapping("/userinfo")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserInfo(
            @RequestParam(required = false) Long userId) {

        if (userId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "userId 不能为空"));
        }

        try {
            Map<String, Object> userDetail = weComAuthService.getUserDetail(null);
            return ResponseEntity.ok(ApiResponse.success(userDetail));
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "获取用户信息失败: " + e.getMessage()));
        }
    }

    /**
     * 绑定企业微信账号到系统用户
     *
     * @param body 请求体，包含 code 和 systemUserId
     * @return 绑定结果
     */
    @PostMapping("/bind")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bindAccount(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        Long systemUserId = body.get("systemUserId") != null
                ? Long.valueOf(body.get("systemUserId").toString()) : null;

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "code 不能为空"));
        }
        if (systemUserId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "systemUserId 不能为空"));
        }

        try {
            Map<String, Object> userInfo = weComAuthService.getUserInfoByCode(code);
            log.info("企业微信账号绑定成功: userId={}, wecomUserid={}", systemUserId, userInfo.get("UserId"));
            return ResponseEntity.ok(ApiResponse.success("绑定成功", userInfo));
        } catch (Exception e) {
            log.error("企业微信账号绑定失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "绑定失败: " + e.getMessage()));
        }
    }

    // ==================== 通讯录同步 ====================

    /**
     * 同步企业微信通讯录
     *
     * @return 同步结果
     */
    @PostMapping("/contact/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncContact() {
        if (!weComProperties.isEnabled()) {
            return ResponseEntity.ok(ApiResponse.error(400, "企业微信集成未启用"));
        }

        try {
            List<Map<String, Object>> departments = weComContactService.syncDepartmentList();
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "departments", departments,
                    "departmentCount", departments.size()
            )));
        } catch (Exception e) {
            log.error("通讯录同步失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "同步失败: " + e.getMessage()));
        }
    }

    // ==================== 消息推送 ====================

    /**
     * 发送企业微信消息
     *
     * @param body 请求体，包含 userIds、type、content 等
     * @return 发送结果
     */
    @PostMapping("/message/send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendMessage(@RequestBody Map<String, Object> body) {
        if (!weComProperties.isEnabled()) {
            return ResponseEntity.ok(ApiResponse.error(400, "企业微信集成未启用"));
        }

        @SuppressWarnings("unchecked")
        List<String> userIds = (List<String>) body.get("userIds");
        String type = (String) body.getOrDefault("type", "text");
        String content = (String) body.get("content");

        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "userIds 不能为空"));
        }
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "content 不能为空"));
        }

        try {
            Map<String, Object> result = switch (type) {
                case "markdown" -> weComMessageService.sendMarkdownMessage(userIds, content);
                case "textcard" -> {
                    String title = (String) body.getOrDefault("title", "通知");
                    String url = (String) body.getOrDefault("url", "");
                    yield weComMessageService.sendTextCardMessage(userIds, title, content, url);
                }
                default -> weComMessageService.sendTextMessage(userIds, content);
            };
            return ResponseEntity.ok(ApiResponse.success("发送成功", result));
        } catch (Exception e) {
            log.error("消息发送失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "发送失败: " + e.getMessage()));
        }
    }

    // ==================== 审批流程 ====================

    /**
     * 创建企业微信审批
     *
     * @param body 请求体，包含 contractId、templateId、applicant、formData、summary
     * @return 创建结果
     */
    @PostMapping("/approval/create")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createApproval(@RequestBody Map<String, Object> body) {
        if (!weComProperties.isEnabled()) {
            return ResponseEntity.ok(ApiResponse.error(400, "企业微信集成未启用"));
        }

        Long contractId = body.get("contractId") != null
                ? Long.valueOf(body.get("contractId").toString()) : null;
        String templateId = (String) body.get("templateId");
        String applicant = (String) body.get("applicant");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> formData = (List<Map<String, Object>>) body.get("formData");
        String summary = (String) body.getOrDefault("summary", "");

        if (templateId == null || templateId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "templateId 不能为空"));
        }
        if (applicant == null || applicant.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "applicant 不能为空"));
        }

        try {
            Map<String, Object> result;
            if (contractId != null) {
                result = weComApprovalService.createApprovalWithMapping(contractId, templateId, applicant, formData, summary);
            } else {
                result = weComApprovalService.createApproval(templateId, applicant, formData, summary);
            }
            return ResponseEntity.ok(ApiResponse.success("审批发起成功", result));
        } catch (Exception e) {
            log.error("审批创建失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "审批失败: " + e.getMessage()));
        }
    }

    // ==================== JS-SDK 配置 ====================

    /**
     * 获取 JS-SDK 配置
     *
     * 前端调用此接口获取 wx.config 所需参数。
     *
     * @param url 当前页面完整 URL
     * @return JS-SDK 配置参数
     */
    @GetMapping("/js-sdk-config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getJsSdkConfig(
            @RequestParam String url) {

        if (!weComProperties.isEnabled()) {
            return ResponseEntity.ok(ApiResponse.error(400, "企业微信集成未启用"));
        }

        try {
            Map<String, Object> config = weComJsSdkService.getJsSdkConfig(url);
            return ResponseEntity.ok(ApiResponse.success(config));
        } catch (Exception e) {
            log.error("JS-SDK 配置获取失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "配置获取失败: " + e.getMessage()));
        }
    }

    // ==================== 事件回调 ====================

    /**
     * 企业微信事件回调（GET 用于验证 URL）
     *
     * 企业微信管理后台配置回调 URL 时，会用 GET 请求验证 URL 有效性。
     *
     * @param msgSignature 消息签名
     * @param timestamp    时间戳
     * @param nonce        随机数
     * @param echostr      加密字符串
     * @return 解密后的 echostr
     */
    @GetMapping("/callback")
    public ResponseEntity<String> callbackVerify(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr) {

        log.info("企业微信回调 URL 验证: msg_signature={}, timestamp={}", msgSignature, timestamp);

        // TODO: 实现消息加解密（需要配置 EncodingAESKey）
        // 此处先返回 echostr 让验证通过
        return ResponseEntity.ok(echostr);
    }

    /**
     * 企业微信事件回调（POST 接收事件）
     *
     * 接收审批变更、成员变更等事件推送。
     *
     * @param body 加密的消息体（XML 格式）
     * @return 处理结果
     */
    @PostMapping(value = "/callback", consumes = "application/xml")
    public ResponseEntity<String> handleCallback(@RequestBody String body) {
        log.info("企业微信事件回调接收: body={}", body);

        // TODO: 解析消息体，处理审批回调事件
        // 1. 解密消息
        // 2. 解析 XML 获取事件类型
        // 3. 如果是 approval_change 事件，更新审批状态

        return ResponseEntity.ok("");
    }

    /**
     * 企业微信事件回调（POST JSON 格式）
     */
    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<String>> handleCallbackJson(@RequestBody Map<String, Object> body) {
        log.info("企业微信 JSON 回调接收: {}", body);
        return ResponseEntity.ok(ApiResponse.success("ok"));
    }
}
