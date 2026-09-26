package com.jyfc.backend.module.integration.feishu.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyfc.backend.module.integration.feishu.config.FeishuProperties;
import com.jyfc.backend.module.integration.feishu.exception.FeishuApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 飞书审批流对接服务。
 * <p>
 * 用于创建审批实例、查询审批状态、解析审批事件回调。
 * 合同审批流程：创建合同 -> 提交飞书审批 -> 监听审批结果 -> 更新合同状态。
 */
@Service
public class FeishuApprovalService {

    private static final Logger log = LoggerFactory.getLogger(FeishuApprovalService.class);

    private final FeishuProperties properties;
    private final FeishuTokenService tokenService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public FeishuApprovalService(FeishuProperties properties,
                                  FeishuTokenService tokenService,
                                  WebClient.Builder webClientBuilder,
                                  ObjectMapper objectMapper) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 创建审批实例。
     * <p>
     * 调用飞书 POST /open-apis/approval/v4/instances
     *
     * @param approvalCode 审批定义 code（需先在飞书后台创建审批流程）
     * @param formData     表单数据，key 为飞书审批表单控件的 id
     * @param userId       发起人 user_id（需获取过飞书 user_id）
     * @return 飞书 API 响应，包含 instance_code
     */
    public Map<String, Object> createApproval(String approvalCode, Map<String, Object> formData, String userId) {
        String token = tokenService.getTenantAccessToken();

        // 构造表单 JSON
        String formJson;
        try {
            formJson = objectMapper.writeValueAsString(formData);
        } catch (JsonProcessingException e) {
            throw new FeishuApiException(-1, "审批表单 JSON 序列化失败: " + e.getMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("approval_code", approvalCode);
        body.put("user_id", userId);
        body.put("form", formJson);
        body.put("uuid", java.util.UUID.randomUUID().toString());

        Map<String, Object> response = webClient.post()
                .uri("/approval/v4/instances")
                .header("Authorization", "Bearer " + token)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(15))
                .block();

        return checkResponse(response, "创建飞书审批实例");
    }

    /**
     * 查询审批实例状态。
     * <p>
     * 调用飞书 GET /open-apis/approval/v4/instances/{instance_id}
     *
     * @param instanceCode 审批实例 code（创建时返回的 instance_code）
     * @return 审批实例状态信息
     */
    public Map<String, Object> getApprovalStatus(String instanceCode) {
        String token = tokenService.getTenantAccessToken();

        Map<String, Object> response = webClient.get()
                .uri("/approval/v4/instances/" + instanceCode)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10))
                .block();

        return checkResponse(response, "查询飞书审批状态");
    }

    /**
     * 订阅审批事件（占位方法）。
     * <p>
     * 目前飞书审批事件的订阅配置需在飞书开发者后台完成：
     * 事件订阅 -> 审批事件（approval_instance）-> 配置回调地址
     * 回调地址格式：https://your-domain/api/integration/feishu/callback
     * <p>
     * 此方法为占位，实际回调由 FeishuController 中的 callback 端点处理。
     *
     * @param callbackUrl 回调地址
     */
    public void registerApprovalEvent(String callbackUrl) {
        log.info("飞书审批事件回调注册：请在飞书开放平台配置回调地址 -> {}", callbackUrl);
        log.info("当前系统回调地址：{}/api/integration/feishu/callback", properties.getBaseUrl());
    }

    /**
     * 解析飞书推送的审批事件。
     * <p>
     * 事件类型包括：
     * - approval_instance: 审批实例状态变更（通过/拒绝/转交/撤回）
     * - approval_instance_cc: 审批抄送
     *
     * @param eventPayload 事件 payload（已解密）
     * @return 解析后的事件信息
     */
    public Map<String, Object> parseApprovalEvent(Map<String, Object> eventPayload) {
        if (eventPayload == null) {
            throw new FeishuApiException(-1, "审批事件 payload 为空");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("raw", eventPayload);

        // 新飞书事件格式，事件信息在 header 和 event 字段
        Object headerObj = eventPayload.get("header");
        if (headerObj instanceof Map<?, ?> header) {
            result.put("eventId", header.get("event_id"));
            result.put("eventType", header.get("event_type"));
            result.put("tenantKey", header.get("tenant_key"));
            result.put("appId", header.get("app_id"));
        }

        Object eventObj = eventPayload.get("event");
        if (eventObj instanceof Map<?, ?> event) {
            result.put("instanceCode", event.get("instance_code"));
            result.put("approvalCode", event.get("approval_code"));
            result.put("status", event.get("status"));
            result.put("userId", event.get("user_id"));
            result.put("timestamp", event.get("update_time"));
        }

        log.info("解析飞书审批事件: type={}, status={}",
                result.get("eventType"), result.get("status"));

        return result;
    }

    /**
     * 解析审批事件（旧格式兼容）。
     *
     * @param payloadJson JSON 字符串形式的 payload
     * @return 解析后的事件信息
     */
    public Map<String, Object> parseApprovalEvent(String payloadJson) {
        try {
            Map<String, Object> payload = objectMapper.readValue(payloadJson,
                    new TypeReference<Map<String, Object>>() {});
            return parseApprovalEvent(payload);
        } catch (JsonProcessingException e) {
            throw new FeishuApiException(-1, "审批事件 JSON 解析失败: " + e.getMessage());
        }
    }

    // ===== 内部工具方法 =====

    @SuppressWarnings("unchecked")
    private Map<String, Object> checkResponse(Map<String, Object> response, String action) {
        if (response == null) {
            throw new FeishuApiException(-1, action + "：响应为空");
        }
        Number code = (Number) response.get("code");
        if (code != null && code.longValue() != 0) {
            String msg = (String) response.get("msg");
            log.error("{} 失败: code={}, msg={}", action, code, msg);
            throw new FeishuApiException(code.intValue(), msg != null ? msg : "Unknown error");
        }
        return (Map<String, Object>) response.get("data");
    }
}
