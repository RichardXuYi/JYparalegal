package com.jyfc.backend.core.cp;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 数据平面（DP）侧的 CP token 验签器（D16 / docs/08 §3.1、§5）——**auth 反转**的关键：
 * 既有 DP 用 {@code JWT_SECRET} 对称自签；接入 CP 后，DP 改用 **CP 公钥（JWKS）离线验签**。
 *
 * <p>只持有公钥 → 客户即便拿到 DP 机器也**造不出假票**（威胁①已解）。
 * 私钥永不出 CP。
 */
@Component
public class CpTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(CpTokenVerifier.class);

    private final CpProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private volatile Map<String, RSAPublicKey> cache = Map.of();
    private volatile long cacheAt = 0L;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    public CpTokenVerifier(CpProperties props) {
        this.props = props;
    }

    /**
     * 验签 CP passport JWT（校验签名 + iss + aud=dp:instanceId + exp）。
     *
     * @return 验签通过则返回 DecodedJWT；否则 empty（不抛异常，由调用方回退到 HS256/Session）
     */
    public Optional<DecodedJWT> verify(String token) {
        if (!props.isEnabled() || token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            DecodedJWT undecoded = JWT.decode(token);
            String kid = undecoded.getKeyId();
            RSAPublicKey pub = resolveKey(kid);
            if (pub == null) {
                log.warn("CP verify: 无匹配公钥 (kid={})，JWKS 可能未就绪", kid);
                return Optional.empty();
            }
            JWTVerifier verifier = JWT.require(Algorithm.RSA256(pub, null))
                    .withIssuer(props.getIssuer())
                    .withAudience("dp:" + props.getInstanceId())
                    .build();
            return Optional.of(verifier.verify(token));
        } catch (Exception e) {
            // 签名不符/过期/iss-aud 不符 —— 视为无效，不抛（放行给后续认证路径）。
            // 必须留痕：这里的完全静默曾让 CP 用 HS256 签发、本类按 RS256 验签的不匹配
            // 潜伏数月——票一直验不过，表现为"CP 联运好像没生效"而不是报错。
            log.warn("CP passport 验签未通过，回退到其它认证方式: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private RSAPublicKey resolveKey(String kid) throws Exception {
        Map<String, RSAPublicKey> keys = keys();
        if (keys.isEmpty()) {
            return null;
        }
        if (kid != null && keys.containsKey(kid)) {
            return keys.get(kid);
        }
        return keys.values().iterator().next();
    }

    /** 拉取并缓存 CP JWKS 公钥（在线走此；离线部署在安装时内置公钥，docs/08 §3.1）。 */
    private Map<String, RSAPublicKey> keys() throws Exception {
        long now = System.currentTimeMillis();
        Map<String, RSAPublicKey> local = cache;
        if (!local.isEmpty() && now - cacheAt < CACHE_TTL_MS) {
            return local;
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + "/cp/.well-known/jwks.json"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            return local;   // 拉取失败沿用旧缓存
        }
        JsonNode root = mapper.readTree(res.body());
        Map<String, RSAPublicKey> parsed = new HashMap<>();
        for (JsonNode k : root.path("keys")) {
            String kidv = k.path("kid").asText("default");
            String n = k.path("n").asText(null);
            String e = k.path("e").asText(null);
            if (n == null || e == null) {
                continue;
            }
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
            RSAPublicKey pub = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, exponent));
            parsed.put(kidv, pub);
        }
        if (!parsed.isEmpty()) {
            cache = parsed;
            cacheAt = now;
            log.info("CP JWKS 已加载：{} 个公钥 ({})", parsed.size(), parsed.keySet());
        }
        return parsed;
    }
}