package com.jyfc.backend.module.integration.feishu.service;

import com.jyfc.backend.module.integration.feishu.config.FeishuProperties;
import com.jyfc.backend.module.integration.feishu.exception.FeishuApiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书 Token 管理服务。
 * <p>
 * 管理 tenant_access_token 的缓存与自动刷新，
 * 以及 OAuth 流程中的 user_access_token 换取。
 */
@Service
public class FeishuTokenService {

    private static final Logger log = LoggerFactory.getLogger(FeishuTokenService.class);

    /** tenant_access_token 缓存键 */
    private static final String TENANT_TOKEN_KEY = "tenant_access_token";

    /** Token 缓存（key -> {token, expiresAt}） */
    private final ConcurrentHashMap<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();

    private final FeishuProperties properties;
    private final WebClient webClient;

    public FeishuTokenService(FeishuProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("飞书集成未启用，Token 服务跳过初始化");
            return;
        }
        log.info("飞书 Token 服务初始化完成");
    }

    /**
     * 获取 tenant_access_token。
     * 优先使用缓存，过期或不存在时自动请求新 token。
     */
    public String getTenantAccessToken() {
        if (!properties.isEnabled()) {
            throw new FeishuApiException(-1, "飞书集成未启用");
        }

        // 检查缓存
        TokenEntry cached = tokenCache.get(TENANT_TOKEN_KEY);
        if (cached != null && !cached.isExpired()) {
            return cached.token;
        }

        // 请求新 token
        return refreshTenantAccessToken();
    }

    /**
     * 刷新 tenant_access_token。
     * 调用飞书 POST /open-apis/auth/v3/tenant_access_token/internal
     */
    private synchronized String refreshTenantAccessToken() {
        // 双重检查
        TokenEntry cached = tokenCache.get(TENANT_TOKEN_KEY);
        if (cached != null && !cached.isExpired()) {
            return cached.token;
        }

        log.info("正在刷新飞书 tenant_access_token...");

        Map<String, Object> body = new HashMap<>();
        body.put("app_id", properties.getAppId());
        body.put("app_secret", properties.getAppSecret());

        Map<String, Object> response = webClient.post()
                .uri("/auth/v3/tenant_access_token/internal")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10))
                .block();

        if (response == null) {
            throw new FeishuApiException(-1, "飞书 token 请求返回为空");
        }

        // 检查 errcode
        Integer errCode = (Integer) response.get("code");
        if (errCode != null && errCode != 0) {
            String errMsg = (String) response.get("msg");
            log.error("飞书 token 请求失败: code={}, msg={}", errCode, errMsg);
            throw new FeishuApiException(errCode, errMsg != null ? errMsg : "Unknown error");
        }

        String token = (String) response.get("tenant_access_token");
        Integer expireIn = (Integer) response.get("expire");

        if (token == null || expireIn == null) {
            throw new FeishuApiException(-1, "飞书 token 响应字段缺失");
        }

        // 缓存 token，提前 5 分钟过期
        long expiresAt = Instant.now().getEpochSecond() + expireIn - 300;
        tokenCache.put(TENANT_TOKEN_KEY, new TokenEntry(token, expiresAt));

        log.info("飞书 tenant_access_token 刷新成功，有效期 {} 秒", expireIn);
        return token;
    }

    /**
     * 用 OAuth code 换取 user_access_token。
     * 调用飞书 POST /open-apis/authen/v1/oidc/access_token
     */
    public Map<String, Object> getUserAccessToken(String code) {
        String tenantToken = getTenantAccessToken();

        Map<String, Object> body = new HashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("code", code);

        Map<String, Object> response = webClient.post()
                .uri("/authen/v1/oidc/access_token")
                .header("Authorization", "Bearer " + tenantToken)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10))
                .block();

        if (response == null) {
            throw new FeishuApiException(-1, "飞书 OAuth token 请求返回为空");
        }

        Integer errCode = (Integer) response.get("code");
        if (errCode != null && errCode != 0) {
            String errMsg = (String) response.get("msg");
            log.error("飞书 OAuth token 换取失败: code={}, msg={}", errCode, errMsg);
            throw new FeishuApiException(errCode, errMsg != null ? errMsg : "Unknown error");
        }

        return response;
    }

    /**
     * 刷新 user_access_token。
     * 调用飞书 POST /open-apis/authen/v1/oidc/refresh_access_token
     */
    public Map<String, Object> refreshUserToken(String refreshToken) {
        String tenantToken = getTenantAccessToken();

        Map<String, Object> body = new HashMap<>();
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", refreshToken);

        Map<String, Object> response = webClient.post()
                .uri("/authen/v1/oidc/refresh_access_token")
                .header("Authorization", "Bearer " + tenantToken)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10))
                .block();

        if (response == null) {
            throw new FeishuApiException(-1, "飞书用户 token 刷新请求返回为空");
        }

        Integer errCode = (Integer) response.get("code");
        if (errCode != null && errCode != 0) {
            String errMsg = (String) response.get("msg");
            log.error("飞书用户 token 刷新失败: code={}, msg={}", errCode, errMsg);
            throw new FeishuApiException(errCode, errMsg != null ? errMsg : "Unknown error");
        }

        return response;
    }

    /**
     * 定时任务：每 90 分钟刷新一次 tenant_access_token。
     * 防止缓存过期后首次请求被拦截。
     */
    @Scheduled(fixedRate = 90 * 60 * 1000) // 90 分钟
    public void scheduledRefresh() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            refreshTenantAccessToken();
        } catch (Exception e) {
            log.error("定时刷新飞书 token 失败", e);
        }
    }

    /**
     * Token 缓存条目。
     */
    private static class TokenEntry {
        final String token;
        final long expiresAt; // Unix timestamp (秒)

        TokenEntry(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return Instant.now().getEpochSecond() >= expiresAt;
        }
    }
}
