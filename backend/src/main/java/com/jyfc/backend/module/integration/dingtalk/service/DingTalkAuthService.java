package com.jyfc.backend.module.integration.dingtalk.service;

import com.jyfc.backend.module.integration.dingtalk.config.DingTalkProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 钉钉 OAuth 认证服务。
 * <p>
 * 功能：
 * <ul>
 *   <li>获取并缓存企业 access_token（2小时过期，提前5分钟刷新）</li>
 *   <li>OAuth 授权码换取用户 token</li>
 *   <li>获取钉钉用户信息</li>
 *   <li>构造扫码登录 URL</li>
 * </ul>
 *
 * 安全提示：钉钉 API 要求 appsecret / accessSecret / sns_token 作为 URL 查询参数传递（不支持 Header 方式）。
 * 这些敏感凭证可能被代理/网关日志记录，请确保 HTTP 客户端日志不记录完整 URL。
 * 切勿在应用日志中输出 secret 或包含 secret 的完整 URL。
 */
@Service
public class DingTalkAuthService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkAuthService.class);

    private final DingTalkProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /** 企业级 access_token 缓存 */
    private final AtomicReference<String> cachedCorpToken = new AtomicReference<>();
    private volatile LocalDateTime corpTokenExpiresAt;

    public DingTalkAuthService(DingTalkProperties properties,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 获取企业级 access_token（带缓存）。
     * <p>
     * 缓存策略：token 有效期 2 小时，提前 5 分钟刷新。
     *
     * @return access_token
     */
    public String getAccessToken() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("钉钉集成未启用，请配置 integration.dingtalk.enabled=true");
        }

        // 检查缓存是否有效
        if (cachedCorpToken.get() != null && corpTokenExpiresAt != null
                && LocalDateTime.now().isBefore(corpTokenExpiresAt)) {
            return cachedCorpToken.get();
        }

        synchronized (this) {
            // 双重检查
            if (cachedCorpToken.get() != null && corpTokenExpiresAt != null
                    && LocalDateTime.now().isBefore(corpTokenExpiresAt)) {
                return cachedCorpToken.get();
            }

            log.info("正在获取钉钉企业 access_token...");
            try {
                String response = webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/gettoken")
                                .queryParam("appkey", properties.getAppKey())
                                .queryParam("appsecret", properties.getAppSecret())
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode json = objectMapper.readTree(response);
                int errcode = json.path("errcode").asInt(-1);
                if (errcode != 0) {
                    String errmsg = json.path("errmsg").asText("unknown error");
                    log.error("获取企业 access_token 失败: errcode={}, errmsg={}", errcode, errmsg);
                    throw new RuntimeException("获取企业 access_token 失败: " + errmsg);
                }

                String token = json.path("access_token").asText();
                int expiresIn = json.path("expires_in").asInt(7200);

                // 缓存 token，提前 5 分钟过期
                cachedCorpToken.set(token);
                corpTokenExpiresAt = LocalDateTime.now().plusSeconds(expiresIn - 300);

                log.info("成功获取企业 access_token，有效期 {} 秒", expiresIn);
                return token;
            } catch (Exception e) {
                log.error("获取企业 access_token 异常", e);
                throw new RuntimeException("获取企业 access_token 失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * OAuth 授权码换取用户级 access_token。
     *
     * @param code OAuth 授权码
     * @return 包含 access_token、refresh_token 等信息的 Map
     */
    public Map<String, Object> getUserAccessToken(String code) {
        getAccessToken();
        log.info("正在用授权码换取用户 token...");

        try {
            String response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sns/gettoken_bycode")
                            .queryParam("accessKey", properties.getAppKey())
                            .queryParam("accessSecret", properties.getAppSecret())
                            .build())
                    .bodyValue(Map.of("tmp_auth_code", code != null ? code : ""))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode json = objectMapper.readTree(response);
            int errcode = json.path("errcode").asInt(-1);
            if (errcode != 0) {
                String errmsg = json.path("errmsg").asText("unknown error");
                log.error("换取用户 token 失败: errcode={}, errmsg={}", errcode, errmsg);
                throw new RuntimeException("换取用户 token 失败: " + errmsg);
            }

            JsonNode snsToken = json.path("sns_token");
            return Map.of(
                    "accessToken", snsToken.path("access_token").asText(),
                    "refreshToken", snsToken.path("refresh_token").asText(),
                    "expiresIn", snsToken.path("expires_in").asInt(),
                    "openid", snsToken.path("openid").asText(),
                    "persistentCode", snsToken.path("persistent_code").asText()
            );
        } catch (Exception e) {
            log.error("换取用户 token 异常", e);
            throw new RuntimeException("换取用户 token 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据用户 access_token 获取钉钉用户信息。
     *
     * @param accessToken 用户级 access_token
     * @return 包含用户信息的 Map（nick、unionId、openId、dingId 等）
     */
    public Map<String, Object> getUserInfo(String accessToken) {
        log.info("正在获取钉钉用户信息...");

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sns/getuserinfo")
                            .queryParam("sns_token", accessToken)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode json = objectMapper.readTree(response);
            int errcode = json.path("errcode").asInt(-1);
            if (errcode != 0) {
                String errmsg = json.path("errmsg").asText("unknown error");
                log.error("获取用户信息失败: errcode={}, errmsg={}", errcode, errmsg);
                throw new RuntimeException("获取用户信息失败: " + errmsg);
            }

            JsonNode userInfo = json.path("user_info");
            return Map.of(
                    "nick", userInfo.path("nick").asText(),
                    "unionId", userInfo.path("unionid").asText(),
                    "openId", userInfo.path("openid").asText(),
                    "dingId", userInfo.path("dingId").asText("")
            );
        } catch (Exception e) {
            log.error("获取用户信息异常", e);
            throw new RuntimeException("获取用户信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 一站式：根据授权码获取用户详情。
     * <p>
     * 流程：code → user_access_token → user_info
     *
     * @param code OAuth 授权码
     * @return 包含用户信息和 token 的 Map
     */
    public Map<String, Object> getUserDetailByCode(String code) {
        log.info("正在通过授权码获取用户详情...");
        Map<String, Object> tokenResult = getUserAccessToken(code);
        String accessToken = (String) tokenResult.get("accessToken");
        Map<String, Object> userInfo = getUserInfo(accessToken);

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", tokenResult.get("refreshToken"),
                "expiresIn", tokenResult.get("expiresIn"),
                "nick", userInfo.get("nick"),
                "unionId", userInfo.get("unionId"),
                "openId", userInfo.get("openId"),
                "dingId", userInfo.get("dingId")
        );
    }

    /**
     * 构造钉钉扫码登录 URL。
     *
     * @param redirectUri 回调地址（为空则使用配置中的默认值）
     * @param state       防 CSRF 状态参数
     * @return 扫码登录 URL
     */
    public String getLoginQrCodeUrl(String redirectUri, String state) {
        String uri = (redirectUri != null && !redirectUri.isEmpty())
                ? redirectUri
                : properties.getRedirectUri();

        return "https://login.dingtalk.com/oauth2/auth?" +
                "response_type=code" +
                "&client_id=" + properties.getAppKey() +
                "&scope=openid" +
                "&state=" + (state != null ? state : "jyfc") +
                "&redirect_uri=" + uri;
    }
}
