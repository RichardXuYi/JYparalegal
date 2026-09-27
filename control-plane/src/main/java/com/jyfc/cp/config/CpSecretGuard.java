package com.jyfc.cp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CP 密钥启动校验。CP 直接持有 e签宝凭证并签发服务账号 passport，任何弱/缺失密钥
 * 都等于把"签章必经你方云"的强制点敞开。非 {@code dev} profile 下，密钥缺失、过短
 * 或等于历史弱默认值时 fail-fast，防止：
 * <ul>
 *   <li>用已知 {@code cp-secret} / {@code dev-callback-token} 冒充 DP 或伪造回调转发；</li>
 *   <li>audience 未与 DP 的 {@code jy.cp.instance-id} 对齐——这不会报错，只会让 DP
 *       验签永远失败并静默回退（历史上 HS256/RS256 不匹配就是这么潜伏的），故启动即查。</li>
 * </ul>
 * passport 签名私钥的可用性由 {@link CpRsaKeyProvider} 在构造期保证（非 dev 下无法
 * 落盘即抛）。本地开发用 {@code --spring.profiles.active=dev} 跳过强度校验（会打印告警）。
 */
@Component
public class CpSecretGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CpSecretGuard.class);

    private static final List<String> WEAK_VALUES = List.of(
            "cp-secret",
            "dev-callback-token");

    private final Environment environment;

    @Value("${cp.auth.service-password:}")
    private String servicePassword;

    @Value("${cp.dp.callback-token:}")
    private String callbackToken;

    @Value("${cp.esign.app-id:}")
    private String esignAppId;

    @Value("${cp.esign.app-secret:}")
    private String esignAppSecret;

    @Value("${cp.jwt.audience:}")
    private String audience;

    public CpSecretGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean isDev = List.of(environment.getActiveProfiles()).contains("dev");
        if (isDev) {
            log.warn("CP 以 dev profile 启动：跳过密钥强度校验（严禁用于生产）");
            return;
        }

        requirePresent(servicePassword, "CP_SERVICE_PASSWORD");
        requirePresent(callbackToken, "CP_ESIGN_CALLBACK_TOKEN");
        requirePresent(esignAppId, "ESIGN_APP_ID");
        requirePresent(esignAppSecret, "ESIGN_APP_SECRET");
        if (isBlank(audience) || !audience.trim().startsWith("dp:")) {
            throw new IllegalStateException("CP 启动失败：CP_JWT_AUDIENCE 应为 dp:<DP 的 jy.cp.instance-id>，"
                    + "当前为 \"" + audience + "\"。留空或不匹配不会报错，只会让 DP 验签静默失败。");
        }

        log.info("CP 密钥校验通过（非 dev profile）");
    }

    private void requirePresent(String value, String envName) {
        if (isBlank(value)) {
            throw new IllegalStateException(
                    "CP 启动失败：" + envName + " 未配置。请通过环境变量注入（或用 dev profile 本地运行）。");
        }
        if (WEAK_VALUES.contains(value.trim())) {
            throw new IllegalStateException(
                    "CP 启动失败：" + envName + " 使用了历史弱默认值，必须改为强随机值。");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
