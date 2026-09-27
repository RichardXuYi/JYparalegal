package com.jyfc.backend.module.integration.feishu.service;

import com.jyfc.backend.module.integration.feishu.config.FeishuProperties;
import com.jyfc.backend.module.integration.feishu.dto.FeishuUserInfo;
import com.jyfc.backend.module.integration.feishu.exception.FeishuApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 飞书 OAuth 登录服务。
 * <p>
 * 处理飞书扫码/应用内登录流程，包括构造登录 URL、
 * 用 code 换取用户信息、用 token 获取用户详情。
 */
@Service
public class FeishuAuthService {

    private static final Logger log = LoggerFactory.getLogger(FeishuAuthService.class);

    private final FeishuProperties properties;
    private final FeishuTokenService tokenService;
    private final WebClient webClient;

    public FeishuAuthService(FeishuProperties properties,
                              FeishuTokenService tokenService,
                              WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    /**
     * 构造飞书 OAuth 登录 URL。
     * 用户跳转到此 URL 后会看到飞书扫码/登录页面，登录后回调到 redirectUri。
     *
     * @param state 自定义 state 参数（防 CSRF）
     * @return 完整的登录 URL
     */
    public String getLoginUrl(String redirectUri, String state) {
        String actualRedirectUri = (redirectUri != null && !redirectUri.isBlank())
                ? redirectUri
                : properties.getRedirectUri();

        try {
            String encodedRedirectUri = URLEncoder.encode(actualRedirectUri, StandardCharsets.UTF_8);
            String url = properties.getBaseUrl()
                    + "/authen/v1/index?app_id=" + properties.getAppId()
                    + "&redirect_uri=" + encodedRedirectUri
                    + "&state=" + (state != null ? state : "");
            return url;
        } catch (Exception e) {
            throw new FeishuApiException(-1, "构造飞书登录 URL 失败: " + e.getMessage());
        }
    }

    /**
     * 用 OAuth code 换取飞书用户信息。
     * 流程：code -> user_access_token -> 调用用户信息接口
     *
     * @param code OAuth 授权码
     * @return 飞书用户信息
     */
    public FeishuUserInfo getUserInfoByCode(String code) {
        // 1. code 换 user_access_token
        Map<String, Object> tokenResponse = tokenService.getUserAccessToken(code);
        String userAccessToken = (String) tokenResponse.get("access_token");
        if (userAccessToken == null) {
            throw new FeishuApiException(-1, "飞书 OAuth 响应中缺少 access_token");
        }

        // 2. 用 token 获取用户信息
        return getUserInfoByToken(userAccessToken);
    }

    /**
     * 使用 user_access_token 获取飞书用户详情。
     * 调用 GET /open-apis/authen/v1/user_info
     *
     * @param userAccessToken 飞书用户 access_token
     * @return 飞书用户信息
     */
    public FeishuUserInfo getUserInfoByToken(String userAccessToken) {
        Map<String, Object> response = webClient.get()
                .uri("/authen/v1/user_info")
                .header("Authorization", "Bearer " + userAccessToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10))
                .block();

        if (response == null) {
            throw new FeishuApiException(-1, "飞书用户信息请求返回为空");
        }

        Integer errCode = (Integer) response.get("code");
        if (errCode != null && errCode != 0) {
            String errMsg = (String) response.get("msg");
            log.error("飞书用户信息获取失败: code={}, msg={}", errCode, errMsg);
            throw new FeishuApiException(errCode, errMsg != null ? errMsg : "Unknown error");
        }

        FeishuUserInfo userInfo = new FeishuUserInfo();
        userInfo.setUnionId((String) response.get("union_id"));
        userInfo.setOpenId((String) response.get("open_id"));
        userInfo.setUserId((String) response.get("user_id"));
        userInfo.setName((String) response.get("name"));
        userInfo.setAvatar((String) response.get("avatar_url"));
        userInfo.setMobile((String) response.get("mobile"));
        userInfo.setEmail((String) response.get("email"));
        userInfo.setTenantKey((String) response.get("tenant_key"));

        return userInfo;
    }
}
