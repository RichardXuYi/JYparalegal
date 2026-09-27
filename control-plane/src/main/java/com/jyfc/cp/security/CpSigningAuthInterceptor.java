package com.jyfc.cp.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.jyfc.cp.config.CpRsaKeyProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 校验 {@code /cp/v1/signing/**} 的 Bearer passport JWT（RS256，由
 * {@link com.jyfc.cp.controller.CpAuthController} 用 {@link CpRsaKeyProvider} 私钥签发）。
 * <p>
 * 此前这些端点零鉴权：任何能访问 8281 的人都可创建/撤销/延期/下载真实 e签宝签署
 * 流程（法律效力 + 计费）。此拦截器强制校验签名、issuer 与有效期。
 * <p>
 * 鉴权失败返回 HTTP 401 + 业务码 {@code 2001}，与 {@code CpAuthController} 的
 * 认证失败码一致——DP 的 {@code CpSigningClient} 正是依据 body {@code code==2001}
 * 触发"用服务账号重试一次"，从而兼容 DP 转发终端用户令牌（在 CP 侧验不过）的场景。
 * 升级 HS256→RS256 期间无需停机：DP 缓存的旧令牌在此验不过 → 回 2001 → DP 自动重登。
 */
@Component
public class CpSigningAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CpSigningAuthInterceptor.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNAUTHORIZED_BODY = "{\"code\":2001,\"msg\":\"unauthorized\",\"data\":null}";

    private final JWTVerifier verifier;

    public CpSigningAuthInterceptor(CpRsaKeyProvider keyProvider,
                                    @Value("${cp.jwt.issuer:https://cp.jyparalegal.local}") String issuer) {
        this.verifier = JWT.require(Algorithm.RSA256(keyProvider.publicKey(), null))
                .withIssuer(issuer)
                .build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return reject(response, "missing bearer token");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            verifier.verify(token);
            return true;
        } catch (RuntimeException e) {
            return reject(response, "invalid token: " + e.getMessage());
        }
    }

    private boolean reject(HttpServletResponse response, String reason) throws java.io.IOException {
        log.warn("CP 签署端点鉴权失败: {}", reason);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(UNAUTHORIZED_BODY.getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
