package com.jyfc.backend.core.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductionConfigValidator implements ApplicationRunner {

    /** JWT 密钥最小字节长度（HMAC-SHA256 建议 >= 256 bit）。 */
    private static final int MIN_JWT_SECRET_LENGTH = 32;

    /** 仓库中出现过的弱/占位密钥，生产环境一律拒绝。 */
    private static final List<String> WEAK_JWT_SECRETS = List.of(
            "change-me-in-production-please-use-a-long-random-secret",
            "change-me-in-prod-please-use-a-long-random-secret-key-value",
            "dev-only-jwt-secret-not-for-production-32b+"
    );

    private final Environment environment;

    public ProductionConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles == null || activeProfiles.length == 0) {
            throw new IllegalStateException("No Spring profile explicitly set. "
                    + "Set SPRING_PROFILES_ACTIVE to 'dev', 'prod', etc. "
                    + "Running without an explicit profile is not allowed.");
        }

        boolean isProd = List.of(activeProfiles).contains("prod");
        if (!isProd) {
            return;
        }

        String dbUrl = requireNonBlank(environment.getProperty("spring.datasource.url"), "DB_URL");
        String dbUser = requireNonBlank(environment.getProperty("spring.datasource.username"), "DB_USER");
        String dbPassword = environment.getProperty("spring.datasource.password");
        String redisPassword = environment.getProperty("spring.data.redis.password");

        if ("root".equalsIgnoreCase(dbUser)) {
            throw new IllegalStateException("Production DB user must not be root");
        }

        // 系统跨端认证使用 JWT（studio 等走 Bearer token），生产环境的 JWT_SECRET
        // 由下方 validateJwtSecret 强制校验；session 凭据（DB/Redis 密码）同样强校验。
        if (dbPassword == null || dbPassword.trim().isEmpty() || "jy_password".equals(dbPassword)) {
            throw new IllegalStateException("Production DB_PASSWORD must be set to a strong value via environment variable");
        }
        if (redisPassword == null || redisPassword.trim().isEmpty()) {
            throw new IllegalStateException("Production REDIS_PASSWORD must be set via environment variable");
        }

        validateJwtSecret(environment.getProperty("jwt.secret"));

        validateDbTls(dbUrl);
    }

    /**
     * BE-001: 生产环境强制校验 JWT 签名密钥。
     * 密钥缺失、过短或等于任一仓库弱默认值时 fail-fast，防止 token 被伪造。
     */
    private void validateJwtSecret(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Production JWT_SECRET must be set via environment variable");
        }
        String secret = jwtSecret.trim();
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Production JWT_SECRET must be at least " + MIN_JWT_SECRET_LENGTH + " bytes long");
        }
        if (WEAK_JWT_SECRETS.contains(secret) || secret.contains("change-me")) {
            throw new IllegalStateException(
                    "Production JWT_SECRET must not use a built-in/placeholder default value");
        }
    }

    private void validateDbTls(String dbUrl) {
        if (dbUrl.startsWith("jdbc:mysql:")) {
            if (dbUrl.contains("useSSL=false")) {
                throw new IllegalStateException("MySQL production DB_URL must not set useSSL=false");
            }
            if (!containsQueryFlag(dbUrl, "useSSL", "true")) {
                throw new IllegalStateException("MySQL production DB_URL must include useSSL=true");
            }
            if (!containsQueryFlag(dbUrl, "requireSSL", "true")) {
                throw new IllegalStateException("MySQL production DB_URL must include requireSSL=true");
            }
            return;
        }

        if (dbUrl.startsWith("jdbc:postgresql:")) {
            String lower = dbUrl.toLowerCase();
            if (lower.contains("sslmode=disable")) {
                throw new IllegalStateException("PostgreSQL production DB_URL must not set sslmode=disable");
            }
            if (!(lower.contains("ssl=true") || lower.contains("sslmode=require") || lower.contains("sslmode=verify-full"))) {
                throw new IllegalStateException("PostgreSQL production DB_URL must enable SSL (ssl=true or sslmode=require/verify-full)");
            }
        }
    }

    private boolean containsQueryFlag(String url, String key, String value) {
        String needle = key + "=" + value;
        int q = url.indexOf('?');
        if (q < 0) return false;
        String query = url.substring(q + 1);
        String[] parts = query.split("&");
        for (String part : parts) {
            if (needle.equals(part)) return true;
        }
        return false;
    }

    private String requireNonBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing required production setting: " + name);
        }
        return value.trim();
    }
}
