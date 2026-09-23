package com.jyfc.backend.core.cp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyfc.backend.core.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DP → CP 的**签章 chokepoint 客户端**（D16 / docs/08 §4.A）。
 *
 * <p>"送签"必须经 CP：验 passport、查套餐、在线扣配额，再调 e签宝建流程。
 * DP 的本地计数只是预提示——**权威强制在 CP**，故 DP 被 patch 也绕不过（威胁②）。
 */
@Component
public class CpSigningClient {

    private static final Logger log = LoggerFactory.getLogger(CpSigningClient.class);

    private final CpProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private volatile String serviceToken;
    private volatile long serviceTokenExpiresAtMs;
    private volatile long serviceTokenIssuedAtMs;

    public CpSigningClient(CpProperties props) {
        this.props = props;
    }

    /**
     * 请求 CP 执行一次签署（送签 chokepoint）。
     *
     * @param forwardToken 终端用户持有的 CP passport（优先转发，使 CP 按正确租户计费）；可为 null → 用服务账号
     * @return CP 返回的 data（grantId/providerTaskId/signUrl/quota/plan）
     * @throws BusinessException CP 拒绝（如配额用尽 SIGN_QUOTA_EXCEEDED）或 CP 不可达
     */
    public Map<String, Object> execute(String taskRef, String title, String docSha256, String forwardToken) {
        if (!props.isEnabled()) {
            throw new IllegalStateException("CP 未启用（jy.cp.mode≠required）：local/off 模式不应调用 CP chokepoint");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskRef", taskRef);
        body.put("title", title);
        body.put("docSha256", docSha256);

        String token = (forwardToken != null && !forwardToken.isBlank()) ? forwardToken : serviceToken();
        JsonNode env = post("/cp/v1/signing/execute", body, token);

        // 转发 token 失效 → 用服务账号重试一次
        if (env.path("code").asInt(-1) == 2001 && forwardToken != null && !forwardToken.isBlank()) {
            serviceToken = null;
            env = post("/cp/v1/signing/execute", body, serviceToken());
        }
        int code = env.path("code").asInt(-1);
        if (code != 0) {
            // 3xxx（如 3001 配额用尽）→ 业务错误，向上暴露为签署受阻
            throw new BusinessException("CP 签署被拒[" + code + "]: " + env.path("msg").asText());
        }
        JsonNode data = env.path("data");
        Map<String, Object> out = new LinkedHashMap<>();
        data.fields().forEachRemaining(e -> out.put(e.getKey(), mapper.convertValue(e.getValue(), Object.class)));
        log.info("CP chokepoint 放行：taskRef={} grant={} provider={}", taskRef,
                out.get("grantId"), out.get("provider"));
        return out;
    }

    public Map<String, Object> signUrl(String flowId, String account, String forwardToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("flowId", flowId);
        body.put("account", account);
        return call("/cp/v1/signing/sign-url", body, forwardToken);
    }

    public void revokeFlow(String flowId, String reason, String forwardToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("flowId", flowId);
        body.put("reason", reason);
        call("/cp/v1/signing/revoke", body, forwardToken);
    }

    public void extendFlow(String flowId, long expireAtEpochMs, String forwardToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("flowId", flowId);
        body.put("expireAtEpochMs", expireAtEpochMs);
        call("/cp/v1/signing/extend", body, forwardToken);
    }

    public Map<String, Object> downloadFlow(String flowId, String forwardToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("flowId", flowId);
        return call("/cp/v1/signing/download", body, forwardToken);
    }

    public void rescindFlow(String flowId, String reason, String forwardToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("flowId", flowId);
        body.put("reason", reason);
        call("/cp/v1/signing/rescind", body, forwardToken);
    }

    private Map<String, Object> call(String path, Map<String, Object> body, String forwardToken) {
        if (!props.isEnabled()) {
            throw new IllegalStateException("CP 未启用");
        }
        String token = (forwardToken != null && !forwardToken.isBlank()) ? forwardToken : serviceToken();
        JsonNode env = post(path, body, token);
        if (env.path("code").asInt(-1) == 2001 && forwardToken != null && !forwardToken.isBlank()) {
            serviceToken = null;
            env = post(path, body, serviceToken());
        }
        int code = env.path("code").asInt(-1);
        if (code != 0) {
            throw new BusinessException("CP 签署被拒[" + code + "]: " + env.path("msg").asText());
        }
        JsonNode data = env.path("data");
        Map<String, Object> out = new LinkedHashMap<>();
        if (data != null && data.isObject()) {
            data.fields().forEachRemaining(e -> out.put(e.getKey(), mapper.convertValue(e.getValue(), Object.class)));
        }
        return out;
    }

    /** 用服务账号登录 CP 换 passport（缓存至过期前 30s）。 */
    private synchronized String serviceToken() {
        long now = System.currentTimeMillis();
        if (serviceToken != null && now < serviceTokenExpiresAtMs) {
            return serviceToken;
        }
        if (props.getServiceUsername() == null || props.getServicePassword() == null) {
            throw new BusinessException("CP 服务账号未配置（jy.cp.service-username/password）");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", props.getServiceUsername());
        body.put("password", props.getServicePassword());
        JsonNode env = post("/cp/v1/auth/login", body, null);
        if (env.path("code").asInt(-1) != 0) {
            throw new BusinessException("CP 服务账号登录失败: " + env.path("msg").asText());
        }
        String token = env.path("data").path("accessToken").asText(null);
        long expiresIn = env.path("data").path("expiresIn").asLong(900);
        if (token == null) {
            throw new BusinessException("CP 登录未返回 accessToken");
        }
        serviceToken = token;
        serviceTokenIssuedAtMs = now;
        serviceTokenExpiresAtMs = now + Math.max(60, expiresIn - 30) * 1000L;
        return token;
    }

    private JsonNode post(String path, Map<String, Object> body, String bearer) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (bearer != null) {
                b.header("Authorization", "Bearer " + bearer);
            }
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return mapper.readTree(res.body());
        } catch (Exception e) {
            throw new BusinessException("CP 不可达（" + path + "）: " + e.getMessage());
        }
    }
}