package com.jyfc.backend.module.integration.wecom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 企业微信集成配置属性
 *
 * 对应 application.yml 中的 integration.wecom.*
 * 所有密钥需要管理员在企业微信管理后台获取后填充
 */
@Configuration
@ConfigurationProperties(prefix = "integration.wecom")
public class WeComProperties {

    /** 是否启用企业微信集成 */
    private boolean enabled = false;

    /** 企业 CorpID（管理后台 → 我的企业 → 企业信息） */
    private String corpId;

    /** 自建应用的 Secret（管理后台 → 应用管理 → 自建应用 → 查看Secret） */
    private String corpSecret;

    /** 自建应用的 AgentId（管理后台 → 应用管理 → 自建应用） */
    private String agentId;

    /** 通讯录同步 Secret（管理后台 → 管理工具 → 通讯录同步 → 查看Secret） */
    private String contactSecret;

    /** 审批 Secret（管理后台 → 应用管理 → 审批 → API 文档 → Secret） */
    private String approvalSecret;

    /** OAuth 授权回调地址（需要配置为可信域名） */
    private String redirectUri;

    /** 企业微信 API 基础 URL */
    private String baseUrl = "https://qyapi.weixin.qq.com/cgi-bin";

    /** OAuth 授权基础 URL */
    private String authBaseUrl = "https://open.weixin.qq.com/connect/oauth2";

    // ======== Getters & Setters ========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCorpId() {
        return corpId;
    }

    public void setCorpId(String corpId) {
        this.corpId = corpId;
    }

    public String getCorpSecret() {
        return corpSecret;
    }

    public void setCorpSecret(String corpSecret) {
        this.corpSecret = corpSecret;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getContactSecret() {
        return contactSecret;
    }

    public void setContactSecret(String contactSecret) {
        this.contactSecret = contactSecret;
    }

    public String getApprovalSecret() {
        return approvalSecret;
    }

    public void setApprovalSecret(String approvalSecret) {
        this.approvalSecret = approvalSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthBaseUrl() {
        return authBaseUrl;
    }

    public void setAuthBaseUrl(String authBaseUrl) {
        this.authBaseUrl = authBaseUrl;
    }
}
