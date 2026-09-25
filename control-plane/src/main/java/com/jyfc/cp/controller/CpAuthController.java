package com.jyfc.cp.controller;

import com.auth0.jwt.JWT;
import com.jyfc.cp.config.CpRsaKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CP 认证接口（/cp/v1/auth）。
 * <p>
 * DP 的 CpSigningClient.serviceToken() 调用 POST /cp/v1/auth/login 获取 passport JWT。
 * <p>
 * 令牌用 <b>RS256</b> 签发（私钥永不出 CP，见 {@link CpRsaKeyProvider}），DP 通过
 * {@code /cp/.well-known/jwks.json} 拿公钥离线验签。此前 CP 用 HS256 对称签、DP 按
 * RS256 验，两边永远对不上，验签静默失败并被回退掩盖。
 */
@RestController
@RequestMapping("/cp/v1/auth")
public class CpAuthController {

    private static final Logger log = LoggerFactory.getLogger(CpAuthController.class);

    /** 令牌用途声明。DP 侧只接受 {@code user} 身份票；服务账号票不得当用户身份用。 */
    public static final String CLAIM_TOKEN_USE = "token_use";
    public static final String TOKEN_USE_SERVICE = "service";

    @Value("${cp.auth.service-username:cp-service}")
    private String serviceUsername;

    @Value("${cp.auth.service-password:cp-secret}")
    private String servicePassword;

    @Value("${cp.jwt.issuer:https://cp.jyparalegal.local}")
    private String issuer;

    /**
     * 受众必须与 DP 的 {@code jy.cp.instance-id} 拼出的 {@code dp:<instanceId>} 一致，
     * 否则 DP 的 withAudience 校验直接失败。CP/DP 两侧成对配置，见部署文档。
     */
    @Value("${cp.jwt.audience:dp:dp-dev-01}")
    private String audience;

    /** 服务账号票所代表的租户。仅服务账号场景下为 1；终端用户身份接入后按用户归属签发。 */
    @Value("${cp.jwt.passport-tenant-id:1}")
    private Long passportTenantId;

    @Value("${cp.jwt.expires-in:900}")
    private long expiresInSec;

    private final CpRsaKeyProvider keyProvider;

    public CpAuthController(CpRsaKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (!serviceUsername.equals(username) || !servicePassword.equals(password)) {
            return error(2001, "用户名或密码错误");
        }

        Instant now = Instant.now();
        String token = JWT.create()
                .withKeyId(keyProvider.kid())
                .withIssuer(issuer)
                .withAudience(audience)
                .withSubject(username)
                .withClaim(CLAIM_TOKEN_USE, TOKEN_USE_SERVICE)
                .withClaim("tenant_id", passportTenantId)
                .withIssuedAt(now)
                .withExpiresAt(now.plusSeconds(expiresInSec))
                .sign(keyProvider.signAlgorithm());

        log.info("CP 服务账号登录成功: user={} audience={}", username, audience);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", token);
        data.put("expiresIn", expiresInSec);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("msg", "success");
        result.put("data", data);
        return result;
    }

    private Map<String, Object> error(int code, String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", code);
        r.put("msg", msg);
        r.put("data", null);
        return r;
    }
}
