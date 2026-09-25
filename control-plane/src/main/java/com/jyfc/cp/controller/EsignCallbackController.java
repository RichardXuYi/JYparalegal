package com.jyfc.cp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyfc.cp.esign.EsignSignatureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接收 e签宝的签署事件回调，验签后转发到 DP。
 * <p>
 * e签宝回调 → CP (/api/esign/callback) → DP (/api/sign/callbacks/esign)
 */
@RestController
@RequestMapping("/api/esign")
public class EsignCallbackController {

    private static final Logger log = LoggerFactory.getLogger(EsignCallbackController.class);

    /** 回调时间戳新鲜度窗口（±5 分钟），超出即视为重放拒绝。 */
    private static final long CALLBACK_TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${cp.esign.app-secret}")
    private String appSecret;

    @Value("${cp.dp.base-url:http://localhost:8181}")
    private String dpBaseUrl;

    @Value("${cp.dp.callback-token:}")
    private String dpCallbackToken;

    /**
     * e签宝回调入口。
     * <p>
     * e签宝推送 POST /api/esign/callback?playIdentity=xxx
     * Header: X-Tsign-Open-Ca-Timestamp, X-Tsign-Open-Signature
     */
    @PostMapping("/callback")
    public Map<String, Object> callback(
            @RequestParam(value = "playIdentity", required = false) String playIdentity,
            @RequestHeader(value = "X-Tsign-Open-Ca-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Tsign-Open-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        log.info("收到 e签宝回调: playIdentity={} timestamp={}", playIdentity, timestamp);

        // 验签（fail-closed）：缺少任一签名头一律拒绝。此前的 `if (timestamp != null
        // && signature != null)` 意味着不带签名头的请求直接跳过验签，任何人 POST 一条
        // {"action":"SIGN_FLOW_UPDATE","flowId":"..."} 即可伪造"已签署"法律状态。
        if (timestamp == null || timestamp.isBlank() || signature == null || signature.isBlank()) {
            log.warn("e签宝回调缺少签名头，拒绝处理");
            return Map.of("code", 403, "msg", "missing signature");
        }

        // 时间戳新鲜度校验，防重放（±5 分钟）。verifyCallback 已把 timestamp 绑进
        // HMAC，此处再限制窗口，避免旧回调被无限期重放。
        long tsMillis;
        try {
            long parsed = Long.parseLong(timestamp.trim());
            // 兼容秒/毫秒两种单位：小于 1e11 视为秒（毫秒时间戳当前约 1.7e12）。
            tsMillis = parsed < 100_000_000_000L ? parsed * 1000L : parsed;
        } catch (NumberFormatException e) {
            log.warn("e签宝回调时间戳非法: {}", timestamp);
            return Map.of("code", 403, "msg", "invalid timestamp");
        }
        if (Math.abs(System.currentTimeMillis() - tsMillis) > CALLBACK_TIMESTAMP_TOLERANCE_MS) {
            log.warn("e签宝回调时间戳超出容忍窗口: ts={}", timestamp);
            return Map.of("code", 403, "msg", "timestamp expired");
        }

        String query = playIdentity != null ? "playIdentity=" + playIdentity : "";
        boolean valid = EsignSignatureUtil.verifyCallback(timestamp, query, rawBody, appSecret, signature);
        if (!valid) {
            log.warn("e签宝回调验签失败！");
            return Map.of("code", 403, "msg", "signature invalid");
        }

        // 解析回调体，映射为 DP 期望的事件格式
        try {
            JsonNode body = mapper.readTree(rawBody);
            String action = mapAction(body.path("action").asText(""), body);
            String flowId = body.path("flowId").asText(null);
            String accountId = body.path("accountId").asText(null);
            String eventKey = body.path("timestamp").asText(String.valueOf(System.currentTimeMillis()));

            if (flowId == null || action == null || "IGNORE".equals(action)) {
                log.info("e签宝回调不推进状态，忽略: action={} flowId={}", action, flowId);
                return Map.of("code", 0, "msg", "ignored");
            }

            // 转发到 DP
            forwardToDp(eventKey, flowId, action, accountId, body);

            return Map.of("code", 0, "msg", "success");
        } catch (Exception e) {
            log.error("处理 e签宝回调异常", e);
            return Map.of("code", 500, "msg", e.getMessage());
        }
    }

    /**
     * 将 e签宝的 action 映射为 DP EsignCallbackService 期望的 action。
     * <p>
     * {@code SIGN_FLOW_UPDATE} 是伞形事件（查看/转换/签署完成等都会触发），必须按
     * {@code signResult} 细分：仅 {@code signResult==2}（签署完成，见 e签宝 SDK
     * {@code CallbackCheckDemo} 示例 body）才映射为 {@code SIGNER_SIGNED}，其余子事件
     * 一律 IGNORE，避免把"没人签"的合同误判为已签署（此前无条件当签署完成）。
     */
    private String mapAction(String esignAction, JsonNode body) {
        return switch (esignAction) {
            case "SIGN_FLOW_UPDATE" -> body.path("signResult").asInt(-1) == 2 ? "SIGNER_SIGNED" : "IGNORE";
            case "SIGN_FLOW_COMPLETE" -> "FLOW_COMPLETE";
            case "SIGN_FLOW_EXPIRE" -> "EXPIRE";
            case "SIGN_FLOW_REVOKE" -> "REVOKE";
            case "SIGN_FLOW_REJECT" -> "REJECT";
            case "SIGN_FLOW_FINISH_VOID", "SIGN_FLOW_RESCISSION" -> "VOID_FINISH";
            default -> "IGNORE";
        };
    }

    /**
     * 转发事件到 DP 的 /api/sign/callbacks/esign。
     */
    private void forwardToDp(String eventKey, String flowId, String action,
                             String account, JsonNode originalBody) {
        if (dpCallbackToken == null || dpCallbackToken.isBlank()) {
            log.warn("DP 回调令牌未配置，跳过转发");
            return;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventKey", eventKey);
            payload.put("flowId", flowId);
            payload.put("action", action);
            payload.put("account", account);
            payload.put("reason", originalBody.path("resultDescription").asText(null));

            String json = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dpBaseUrl + "/api/sign/callbacks/esign"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Esign-Callback-Token", dpCallbackToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("转发回调到 DP: flowId={} action={} status={}", flowId, action, resp.statusCode());
        } catch (Exception e) {
            log.error("转发回调到 DP 失败: flowId={}", flowId, e);
        }
    }
}
