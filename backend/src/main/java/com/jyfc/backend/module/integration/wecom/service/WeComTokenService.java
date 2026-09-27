package com.jyfc.backend.module.integration.wecom.service;

import com.jyfc.backend.module.integration.wecom.config.WeComProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信 Token 管理服务
 *
 * 管理 access_token 和 jsapi_ticket 的获取与缓存。
 * access_token 区分三种 Secret 类型：agent（应用）、contact（通讯录）、approval（审批）。
 * 所有 token 缓存 7200 秒（企业微信默认有效期），提前 300 秒刷新防止边界过期。
 */
@Service
public class WeComTokenService {

    private static final Logger log = LoggerFactory.getLogger(WeComTokenService.class);

    /** 默认 access_token 过期时间（秒） */
    private static final long TOKEN_EXPIRE_SECONDS = 7200;

    /** 提前刷新余量（秒） */
    private static final long TOKEN_REFRESH_MARGIN = 300;

    /** Redis key 前缀 */
    private static final String REDIS_KEY_PREFIX = "wecom:token:";

    /** Redis jsapi_ticket key */
    private static final String REDIS_JSAPI_TICKET_KEY = "wecom:jsapi_ticket";

    private final WeComProperties weComProperties;
    private final WebClient.Builder webClientBuilder;
    private final StringRedisTemplate redisTemplate;

    public WeComTokenService(WeComProperties weComProperties,
                             WebClient.Builder webClientBuilder,
                             StringRedisTemplate redisTemplate) {
        this.weComProperties = weComProperties;
        this.webClientBuilder = webClientBuilder;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Secret 类型枚举
     */
    public enum SecretType {
        /** 自建应用的 Secret */
        AGENT,
        /** 通讯录同步的 Secret */
        CONTACT,
        /** 审批的 Secret */
        APPROVAL
    }

    /**
     * 获取 access_token（带缓存）
     *
     * @param secretType Secret 类型
     * @return access_token 字符串
     */
    public String getAccessToken(SecretType secretType) {
        if (!weComProperties.isEnabled()) {
            throw new IllegalStateException("企业微信集成未启用，请配置 integration.wecom.enabled=true");
        }

        String corpId = weComProperties.getCorpId();
        String secret = resolveSecret(secretType);
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("企业微信 " + secretType + " Secret 未配置");
        }

        String redisKey = REDIS_KEY_PREFIX + secretType.name().toLowerCase();

        // 尝试从缓存获取
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        // 同步获取，防止并发重复请求 token
        synchronized (this) {
            // double-check 缓存
            cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null && !cached.isBlank()) {
                return cached;
            }
            // 调用企业微信 API 获取
            return fetchAccessToken(corpId, secret, redisKey);
        }
    }

    /**
     * 刷新指定类型的 access_token（强制刷新）
     */
    public String refreshAccessToken(SecretType secretType) {
        String redisKey = REDIS_KEY_PREFIX + secretType.name().toLowerCase();
        redisTemplate.delete(redisKey);
        return getAccessToken(secretType);
    }

    /**
     * 获取 jsapi_ticket（带缓存）
     */
    public String getJsApiTicket() {
        if (!weComProperties.isEnabled()) {
            throw new IllegalStateException("企业微信集成未启用");
        }

        // 尝试从缓存获取
        String cached = redisTemplate.opsForValue().get(REDIS_JSAPI_TICKET_KEY);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        // 获取 access_token（用应用 Secret）
        String accessToken = getAccessToken(SecretType.AGENT);

        // 调用 API 获取 jsapi_ticket
        return fetchJsApiTicket(accessToken);
    }

    /**
     * 生成 JS-SDK 签名
     *
     * @param url 当前页面完整 URL（包括 ? 和 # 之后的部分）
     * @return 签名参数 nonceStr / timestamp / signature
     */
    public Map<String, Object> generateSignature(String url) {
        String jsapiTicket = getJsApiTicket();
        String nonceStr = generateNonceStr();
        long timestamp = System.currentTimeMillis() / 1000;

        // 按企业微信文档：jsapi_ticket & noncestr & timestamp & url 拼接后 SHA1
        String rawSignature = "jsapi_ticket=" + jsapiTicket
                + "&noncestr=" + nonceStr
                + "&timestamp=" + timestamp
                + "&url=" + url;

        String signature = sha1Hex(rawSignature);

        log.debug("JS-SDK signature generated for url: {}", url);

        return Map.of(
                "nonceStr", nonceStr,
                "timestamp", timestamp,
                "signature", signature
        );
    }

    // ==================== 内部方法 ====================

    /**
     * 根据 SecretType 解析对应的 Secret 值
     */
    private String resolveSecret(SecretType secretType) {
        return switch (secretType) {
            case AGENT -> weComProperties.getCorpSecret();
            case CONTACT -> weComProperties.getContactSecret();
            case APPROVAL -> weComProperties.getApprovalSecret();
        };
    }

    /**
     * 调用企业微信 API 获取 access_token
     */
    private String fetchAccessToken(String corpId, String secret, String redisKey) {
        String url = weComProperties.getBaseUrl() + "/gettoken?corpid=" + corpId + "&corpsecret=" + secret;

        Map<String, Object> response = webClientBuilder.build()
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("企业微信 access_token 请求返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("企业微信 access_token 获取失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        String accessToken = (String) response.get("access_token");
        Integer expiresIn = (Integer) response.getOrDefault("expires_in", (int) TOKEN_EXPIRE_SECONDS);

        // 缓存 token（提前 300 秒过期以留有余量）
        long ttl = Math.max(expiresIn - TOKEN_REFRESH_MARGIN, 60);
        redisTemplate.opsForValue().set(redisKey, accessToken, ttl, TimeUnit.SECONDS);

        log.info("企业微信 access_token 获取成功 ({}), 缓存 {} 秒", redisKey, ttl);
        return accessToken;
    }

    /**
     * 调用企业微信 API 获取 jsapi_ticket
     */
    private String fetchJsApiTicket(String accessToken) {
        String url = weComProperties.getBaseUrl() + "/get_jsapi_ticket?access_token=" + accessToken;

        Map<String, Object> response = webClientBuilder.build()
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("企业微信 jsapi_ticket 请求返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("企业微信 jsapi_ticket 获取失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        String ticket = (String) response.get("ticket");
        Integer expiresIn = (Integer) response.getOrDefault("expires_in", (int) TOKEN_EXPIRE_SECONDS);

        long ttl = Math.max(expiresIn - TOKEN_REFRESH_MARGIN, 60);
        redisTemplate.opsForValue().set(REDIS_JSAPI_TICKET_KEY, ticket, ttl, TimeUnit.SECONDS);

        log.info("企业微信 jsapi_ticket 获取成功, 缓存 {} 秒", ttl);
        return ticket;
    }

    /**
     * 生成随机字符串
     */
    private String generateNonceStr() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA1 十六进制编码
     */
    private String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA1 签名失败", e);
        }
    }
}
