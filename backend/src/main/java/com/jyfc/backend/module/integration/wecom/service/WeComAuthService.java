package com.jyfc.backend.module.integration.wecom.service;

import com.jyfc.backend.module.integration.wecom.config.WeComProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 企业微信 OAuth 授权登录服务
 *
 * 支持静默授权（base）和手动授权（userinfo）两种 scope。
 * 授权流程：
 *   1. 前端跳转 authorizeUrl → 企业微信授权页
 *   2. 用户确认 → 回调 redirectUri 带上 code
 *   3. 后端用 code 换取 UserID
 *   4. 可选：用 user_ticket 获取用户详细信息
 */
@Service
public class WeComAuthService {

    private static final Logger log = LoggerFactory.getLogger(WeComAuthService.class);

    private final WeComProperties weComProperties;
    private final WeComTokenService tokenService;
    private final WebClient.Builder webClientBuilder;

    public WeComAuthService(WeComProperties weComProperties,
                            WeComTokenService tokenService,
                            WebClient.Builder webClientBuilder) {
        this.weComProperties = weComProperties;
        this.tokenService = tokenService;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * 构造授权 URL
     *
     * @param redirectUri OAuth 回调地址（需与可信域名一致）
     * @param state       携带的状态参数（用于防 CSRF，建议传 sessionId 或随机字符串）
     * @param scope       授权范围：base（静默）/ userinfo（手动）
     * @return 完整的授权 URL
     */
    public String getAuthorizeUrl(String redirectUri, String state, String scope) {
        if (redirectUri == null || redirectUri.isBlank()) {
            redirectUri = weComProperties.getRedirectUri();
        }
        if (scope == null || scope.isBlank()) {
            scope = "base";
        }
        if (!"base".equals(scope) && !"userinfo".equals(scope)) {
            throw new IllegalArgumentException("scope 必须为 base 或 userinfo");
        }

        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String url = weComProperties.getAuthBaseUrl() + "/authorize"
                + "?appid=" + weComProperties.getCorpId()
                + "&redirect_uri=" + encodedRedirect
                + "&response_type=code"
                + "&scope=snsapi_" + scope
                + "&state=" + (state != null ? state : "")
                + "#wechat_redirect";

        log.debug("企业微信授权 URL 生成: scope={}", scope);
        return url;
    }

    /**
     * 通过 code 换取 UserID
     *
     * 注意：企业微信的 userid 是企业内唯一标识，不同于微信 OpenID。
     *
     * @param code 授权回调时携带的 code
     * @return 包含 UserID 和 UserTicket 的 Map
     */
    public Map<String, Object> getUserInfoByCode(String code) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.AGENT);
        String url = weComProperties.getBaseUrl() + "/user/getuserinfo"
                + "?access_token=" + accessToken
                + "&code=" + code;

        Map<String, Object> response = webClientBuilder.build()
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("企业微信 getUserInfoByCode 请求返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("企业微信 code 换 UserID 失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        log.info("企业微信 OAuth 登录成功, UserID={}", response.get("UserId"));
        return response;
    }

    /**
     * UserID 转 OpenID
     *
     * 部分企业微信 API 需要使用 OpenID 而非 UserID。
     *
     * @param userId 企业微信 UserID
     * @return 包含 OpenID 的 Map
     */
    public Map<String, Object> convertUserIdToOpenId(String userId) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.AGENT);
        String url = weComProperties.getBaseUrl() + "/user/convert_to_openid?access_token=" + accessToken;

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(url)
                .bodyValue(Map.of("userid", userId != null ? userId : ""))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("企业微信 convertUserIdToOpenId 请求返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("UserID 转 OpenID 失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        log.debug("UserID {} 转 OpenID 成功", userId);
        return response;
    }

    /**
     * 获取用户详细信息（需 user_ticket）
     *
     * user_ticket 在 getUserInfoByCode 中返回（仅 scope=userinfo 时）。
     * 该方法返回姓名、头像、手机、邮箱等详细信息。
     *
     * @param userTicket getUserInfoByCode 返回的 user_ticket
     * @return 用户详细信息
     */
    public Map<String, Object> getUserDetail(String userTicket) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.AGENT);
        String url = weComProperties.getBaseUrl() + "/user/getuserdetail?access_token=" + accessToken;

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(url)
                .bodyValue(Map.of("user_ticket", userTicket != null ? userTicket : ""))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("企业微信 getUserDetail 请求返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("获取用户详细信息失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        log.info("企业微信获取用户详细信息成功, UserID={}", response.get("userid"));
        return response;
    }
}
