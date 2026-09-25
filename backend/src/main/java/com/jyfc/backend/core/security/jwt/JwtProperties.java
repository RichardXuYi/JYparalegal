package com.jyfc.backend.core.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项，绑定 application.yml 中的 {@code jwt.*}。
 *
 * <p>用于跨端（尤其是 Studio 桌面端）的 Token 认证：签发 access / refresh token。
 * web / app 端仍可继续使用 Session Cookie，二者共存。</p>
 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * 签名密钥。默认留空——不再内置弱默认值（BE-001）。
     * 生产环境必须通过 {@code JWT_SECRET} 环境变量注入长度 >= 32 的随机密钥，
     * 否则 {@code ProductionConfigValidator} 会在 prod profile 启动时 fail-fast。
     * 开发环境的密钥由 {@code application-dev.yml} 提供。
     */
    private String secret = "";

    /** 令牌签发者标识。 */
    private String issuer = "jy-financial";

    /** access token 有效期（秒），默认 2 小时。 */
    private long accessTtlSeconds = 7200;

    /** refresh token 有效期（秒），默认 30 天。 */
    private long refreshTtlSeconds = 2592000;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public long getAccessTtlSeconds() { return accessTtlSeconds; }
    public void setAccessTtlSeconds(long accessTtlSeconds) { this.accessTtlSeconds = accessTtlSeconds; }

    public long getRefreshTtlSeconds() { return refreshTtlSeconds; }
    public void setRefreshTtlSeconds(long refreshTtlSeconds) { this.refreshTtlSeconds = refreshTtlSeconds; }
}
