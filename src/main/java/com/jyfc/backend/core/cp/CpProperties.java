package com.jyfc.backend.core.cp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 控制平面（CP）集成配置（D16 / docs/08）。
 *
 * <p>三态模式（{@code jy.cp.mode}）：
 * <ul>
 *   <li>{@code required}（默认，生产姿态）：送签必须经 CP chokepoint 在线裁决，CP 不可达即送签失败（fail-closed）；
 *       同时接受 CP 签发的 RS256 passport JWT（身份以 CP 为权威）。</li>
 *   <li>{@code local}（单机/开发）：不调 CP；送签配额由 DP 本地 {@code tenant_quota} 强制（honor-system：
 *       拦误用、不拦改代码，见 docs/08 §1 威胁②）。</li>
 *   <li>{@code off}：不计量、不调 CP。<b>仅测试</b>，不得用于交付。</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "jy.cp")
public class CpProperties {

    public static final String MODE_REQUIRED = "required";
    public static final String MODE_LOCAL = "local";
    public static final String MODE_OFF = "off";

    /** 集成模式：required / local / off。默认 required（fail-closed）。 */
    private String mode = MODE_REQUIRED;

    /** CP 基址。 */
    private String baseUrl = "http://localhost:8281";

    /** 本 DP 实例 id；用于校验 token 的 aud=dp:<instanceId>（防跨实例重放）。 */
    private String instanceId = "dp-dev-01";

    /** 期望的签发者，与 CP 的 cp.issuer 一致。 */
    private String issuer = "https://cp.jyparalegal.local";

    /** 服务账号（DP 代表租户向 CP 发起 chokepoint 调用时的兜底身份；优先转发终端用户 passport）。 */
    private String serviceUsername;
    private String servicePassword;

    /** CP 联运启用（required 模式）：送签走 CP chokepoint，且接受 CP passport。 */
    public boolean isEnabled() { return MODE_REQUIRED.equals(mode); }

    /** 本地配额兜底（local 模式）。 */
    public boolean isLocalQuota() { return MODE_LOCAL.equals(mode); }

    /** 完全关闭（off 模式，仅测试）。 */
    public boolean isOff() { return MODE_OFF.equals(mode); }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getServiceUsername() { return serviceUsername; }
    public void setServiceUsername(String serviceUsername) { this.serviceUsername = serviceUsername; }

    public String getServicePassword() { return servicePassword; }
    public void setServicePassword(String servicePassword) { this.servicePassword = servicePassword; }
}