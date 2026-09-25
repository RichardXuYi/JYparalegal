package com.jyfc.backend.module.integration.dingtalk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 钉钉集成配置属性。
 * <p>
 * 在 application.yml 中配置：
 * <pre>
 * integration:
 *   dingtalk:
 *     enabled: false
 *     corp-id: ${DINGTALK_CORP_ID:}
 *     agent-id: ${DINGTALK_AGENT_ID:}
 *     app-key: ${DINGTALK_APP_KEY:}
 *     app-secret: ${DINGTALK_APP_SECRET:}
 *     redirect-uri: ${DINGTALK_REDIRECT_URI:http://localhost:3103/api/integration/dingtalk/callback}
 *     base-url: https://oapi.dingtalk.com
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "integration.dingtalk")
public class DingTalkProperties {

    /** 是否启用钉钉集成 */
    private boolean enabled = false;

    /** 企业 CorpId */
    private String corpId;

    /** 微应用 AgentId */
    private String agentId;

    /** 应用 AppKey */
    private String appKey;

    /** 应用 AppSecret */
    private String appSecret;

    /** OAuth 回调地址 */
    private String redirectUri;

    /** 钉钉开放平台 API 基础地址 */
    private String baseUrl = "https://oapi.dingtalk.com";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getCorpId() { return corpId; }
    public void setCorpId(String corpId) { this.corpId = corpId; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
