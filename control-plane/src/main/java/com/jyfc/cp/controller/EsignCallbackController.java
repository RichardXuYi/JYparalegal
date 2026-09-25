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

        // 验签
        if (timestamp != null && signature != null) {
            String query = playIdentity != null ? "playIdentity=" + playIdentity : "";
            boolean valid = EsignSignatureUtil.verifyCallback(timestamp, query, rawBody, appSecret, signature);
            if (!valid) {
                log.warn("e签宝回调验签失败！");
                return Map.of("code", 403, "msg", "signature invalid");
            }
        }

        // 解析回调体，映射为 DP 期望的事件格式
        try {
            JsonNode body = mapper.readTree(rawBody);
            String action = mapAction(body.path("action").asText(""));
            String flowId = body.path("flowId").asText(null);
            String accountId = body.path("accountId").asText(null);
            String eventKey = body.path("timestamp").asText(String.valueOf(System.currentTimeMillis()));

            if (flowId == null || action == null) {
                log.warn("e签宝回调缺少 flowId 或 action");
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
     */
    private String mapAction(String esignAction) {
        return switch (esignAction) {
            case "SIGN_FLOW_UPDATE" -> {
                // SIGN_FLOW_UPDATE 需要根据 signResult 细分
                yield "SIGNER_SIGNED"; // 简化：默认当作签署完成
            }
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
