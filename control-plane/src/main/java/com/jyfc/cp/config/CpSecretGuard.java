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
 *   <li>用已知默认 {@code cp-dev-jwt-secret...} 伪造 passport 绕过签署端点鉴权；</li>
 *   <li>用已知 {@code cp-secret} / {@code dev-callback-token} 冒充 DP 或伪造回调转发。</li>
 * </ul>
 * 本地开发用 {@code --spring.profiles.active=dev} 跳过（会打印告警）。
 */
@Component
public class CpSecretGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CpSecretGuard.class);

    private static final int MIN_JWT_SECRET_BYTES = 32;
    private static final List<String> WEAK_VALUES = List.of(
            "cp-secret",
            "dev-callback-token",
            "cp-dev-jwt-secret-must-be-at-least-32-chars!!");

    private final Environment environment;

    @Value("${cp.jwt.secret:}")
    private String jwtSecret;

    @Value("${cp.auth.service-password:}")
    private String servicePassword;

    @Value("${cp.dp.callback-token:}")
    private String callbackToken;

    @Value("${cp.esign.app-id:}")
    private String esignAppId;

    @Value("${cp.esign.app-secret:}")
    private String esignAppSecret;

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

        requireStrong(jwtSecret, "CP_JWT_SECRET", MIN_JWT_SECRET_BYTES);
        requirePresent(servicePassword, "CP_SERVICE_PASSWORD");
        requirePresent(callbackToken, "CP_ESIGN_CALLBACK_TOKEN");
        requirePresent(esignAppId, "ESIGN_APP_ID");
        requirePresent(esignAppSecret, "ESIGN_APP_SECRET");

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

    private void requireStrong(String value, String envName, int minBytes) {
        requirePresent(value, envName);
        if (value.trim().getBytes(StandardCharsets.UTF_8).length < minBytes) {
            throw new IllegalStateException(
                    "CP 启动失败：" + envName + " 至少需要 " + minBytes + " 字节。");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
