package com.jyfc.cp.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
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
 */
@RestController
@RequestMapping("/cp/v1/auth")
public class CpAuthController {

    private static final Logger log = LoggerFactory.getLogger(CpAuthController.class);

    @Value("${cp.auth.service-username:cp-service}")
    private String serviceUsername;

    @Value("${cp.auth.service-password:cp-secret}")
    private String servicePassword;

    @Value("${cp.jwt.secret:cp-dev-jwt-secret-must-be-at-least-32-chars!!}")
    private String jwtSecret;

    @Value("${cp.jwt.issuer:https://cp.jyparalegal.local}")
    private String issuer;

    @Value("${cp.jwt.expires-in:900}")
    private long expiresInSec;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (!serviceUsername.equals(username) || !servicePassword.equals(password)) {
            return error(2001, "用户名或密码错误");
        }

        Instant now = Instant.now();
        String token = JWT.create()
                .withIssuer(issuer)
                .withSubject(username)
                .withClaim("tenant_id", 1L)
                .withIssuedAt(now)
                .withExpiresAt(now.plusSeconds(expiresInSec))
                .sign(Algorithm.HMAC256(jwtSecret));

        log.info("CP 服务账号登录成功: user={}", username);

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
