package com.jyfc.backend.core.security.jwt;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jyfc.backend.core.cp.CpClaims;
import com.jyfc.backend.core.cp.CpProperties;
import com.jyfc.backend.core.cp.CpTokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 从 {@code Authorization: Bearer <token>} 头解析并校验 access token。
 *
 * <p>校验通过则写入 {@link SecurityContextHolder}；否则放行给后续过滤器
 * （从而仍可回退到 Session Cookie 认证）。因此 Cookie 与 Token 双模共存。</p>
 *
 * <p><b>D16 追加</b>：HS256 自签校验失败后，若 CP 处于 {@code required} 模式（{@code jy.cp.mode=required}），
 * 再尝试用 **CP 公钥验签 RS256 passport JWT**（auth 反转，docs/08 §5）。两条路径互不影响；
 * local/off 模式下行为与既有完全一致。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CpTokenVerifier cpTokenVerifier;
    private final CpProperties cpProperties;

    public JwtAuthenticationFilter(JwtService jwtService, CpTokenVerifier cpTokenVerifier,
                                   CpProperties cpProperties) {
        this.jwtService = jwtService;
        this.cpTokenVerifier = cpTokenVerifier;
        this.cpProperties = cpProperties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<DecodedJWT> decodedOpt = jwtService.verifyAccessToken(token);
            if (decodedOpt.isPresent()) {
                DecodedJWT decoded = decodedOpt.get();
                String username = jwtService.getUsername(decoded);
                List<SimpleGrantedAuthority> authorities = jwtService.getRoles(decoded).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                UsernamePasswordAuthenticationToken auth =
                        UsernamePasswordAuthenticationToken.authenticated(username, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else if (cpProperties.isEnabled()) {
                cpTokenVerifier.verify(token).ifPresent(decoded -> {
                    String username = decoded.getClaim(CpClaims.CLAIM_USERNAME).asString();
                    if (username == null || username.isBlank()) {
                        username = decoded.getSubject();
                    }
                    Long tenantId = decoded.getClaim(CpClaims.CLAIM_TENANT_ID).asLong();
                    UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(
                            username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    // tenant 以 CP claim 为权威（D12/D16），供 TenantContextFilter 使用
                    if (tenantId != null) {
                        request.setAttribute(CpClaims.REQ_ATTR_TENANT_ID, tenantId);
                    }
                    request.setAttribute(CpClaims.REQ_ATTR_CP_USER, username);
                });
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length()).trim();
        }
        return null;
    }
}
