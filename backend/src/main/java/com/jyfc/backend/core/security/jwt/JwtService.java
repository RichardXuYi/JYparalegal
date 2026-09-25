package com.jyfc.backend.core.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT 签发与校验服务。
 *
 * <p>access token 携带用户名（subject）与角色列表（roles claim）；
 * refresh token 为不透明随机串（不含业务信息），由服务端 {@code user_refresh_tokens} 表管理吊销。</p>
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private final JwtProperties properties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.algorithm = Algorithm.HMAC256(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.verifier = JWT.require(algorithm)
                .withIssuer(properties.getIssuer())
                .build();
    }

    /**
     * 签发 access token。
     *
     * @param username 用户名（作为 subject）
     * @param roles    角色列表，例如 {@code ["ROLE_USER"]}
     */
    public String generateAccessToken(String username, List<String> roles) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(properties.getIssuer())
                .withSubject(username)
                .withClaim(CLAIM_TYPE, TYPE_ACCESS)
                .withArrayClaim(CLAIM_ROLES, roles == null
                        ? new String[0]
                        : roles.toArray(new String[0]))
                .withIssuedAt(now)
                .withExpiresAt(now.plusSeconds(properties.getAccessTtlSeconds()))
                .sign(algorithm);
    }

    /**
     * 生成一个不透明的 refresh token（随机串）。真正的过期/吊销由数据库记录管理。
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    public long getAccessTtlSeconds() {
        return properties.getAccessTtlSeconds();
    }

    public long getRefreshTtlSeconds() {
        return properties.getRefreshTtlSeconds();
    }

    /**
     * 校验并解析 access token。校验失败返回 {@link Optional#empty()}。
     */
    public Optional<DecodedJWT> verifyAccessToken(String token) {
        try {
            DecodedJWT decoded = verifier.verify(token);
            if (!TYPE_ACCESS.equals(decoded.getClaim(CLAIM_TYPE).asString())) {
                return Optional.empty();
            }
            return Optional.of(decoded);
        } catch (JWTVerificationException e) {
            return Optional.empty();
        }
    }

    public String getUsername(DecodedJWT decoded) {
        return decoded.getSubject();
    }

    public List<String> getRoles(DecodedJWT decoded) {
        List<String> roles = decoded.getClaim(CLAIM_ROLES).asList(String.class);
        return roles != null ? roles : Collections.emptyList();
    }
}
