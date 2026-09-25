package com.jyfc.backend.module.integration.feishu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 飞书集成配置属性。
 * <p>
 * 在 application.yml 中配置：
 * <pre>
 * integration:
 *   feishu:
 *     enabled: false
 *     app-id: ${FEISHU_APP_ID:}
 *     app-secret: ${FEISHU_APP_SECRET:}
 *     verification-token: ${FEISHU_VERIFICATION_TOKEN:}
 *     encrypt-key: ${FEISHU_ENCRYPT_KEY:}
 *     base-url: https://open.feishu.cn/open-apis
 *     redirect-uri: ${FEISHU_REDIRECT_URI:http://localhost:3103/api/integration/feishu/callback}
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "integration.feishu")
public class FeishuProperties {

    /** 是否启用飞书集成 */
    private boolean enabled = false;

    /** 飞书应用 App ID */
    private String appId;

    /** 飞书应用 App Secret */
    private String appSecret;

    /** 事件订阅验证 token */
    private String verificationToken;

    /** 事件加密 key（可选） */
    private String encryptKey;

    /** 飞书开放平台 API 基础地址 */
    private String baseUrl = "https://open.feishu.cn/open-apis";

    /** OAuth 回调地址 */
    private String redirectUri;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public String getEncryptKey() { return encryptKey; }
    public void setEncryptKey(String encryptKey) { this.encryptKey = encryptKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
}
