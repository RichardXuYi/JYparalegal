package com.jyfc.backend.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.jyfc.backend.core.security.jwt.JwtAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.HttpHeaders;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final Environment environment;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(Environment environment, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.environment = environment;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 仅在 dev / test profile 下开放 Swagger/Actuator，便于本地调试。
     * 生产环境必须登录或具备 ADMIN 角色才能访问。
     */
    private boolean isNonProdProfile() {
        String[] active = environment.getActiveProfiles();
        if (active == null || active.length == 0) {
            // 没有显式激活 profile 时，按 dev 行为对待（向后兼容）
            return true;
        }
        for (String p : active) {
            String lower = p.toLowerCase();
            if (lower.contains("dev") || lower.contains("test") || lower.contains("local")) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean exposeDocsAndHealth = isNonProdProfile();

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                    AntPathRequestMatcher.antMatcher("/api/auth/**"),
                    AntPathRequestMatcher.antMatcher("/api/public/**"),
                    AntPathRequestMatcher.antMatcher("/api/sign/callbacks/esign"),
                    // JWT Bearer 请求豁免 CSRF：Bearer token 不会被浏览器自动携带，
                    // 不存在跨站伪造风险（Studio 主进程 / Node host 均以 Bearer 调用）。
                    request -> {
                        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
                        return auth != null && auth.startsWith("Bearer ");
                    }
                )
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "img-src 'self' data: blob: https:; " +
                    "style-src 'self' https:; " +
                    "script-src 'self'; " +
                    "connect-src 'self' ws: wss:"
                ))
            )
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/error").permitAll()
                    // Auth endpoints (login / logout / register / send-code / me)
                    // are public. /api/auth/me is reachable so the admin
                    // frontend can probe a persisted session on startup; the
                    // controller itself returns 401 when the session is
                    // anonymous.
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/public/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/").permitAll()
                    .requestMatchers("/api/app/pay/wechat/callback", "/api/app/pay/alipay/notify").permitAll()
                    .requestMatchers("/api/sign/callbacks/esign").permitAll();

                // Swagger / OpenAPI：仅在非生产 profile 开放；生产环境需 ADMIN 角色
                if (exposeDocsAndHealth) {
                    auth.requestMatchers(
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/swagger-resources/**", "/webjars/**"
                    ).permitAll();
                    // Actuator：仅在非生产 profile 开放；生产环境需 ADMIN 角色
                    auth.requestMatchers("/actuator/**").hasAnyRole("SUPER_ADMIN", "ADMIN");
                } else {
                    auth.requestMatchers(
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/swagger-resources/**", "/webjars/**"
                    ).hasAnyRole("SUPER_ADMIN", "ADMIN");
                    auth.requestMatchers("/actuator/**").hasAnyRole("SUPER_ADMIN", "ADMIN");
                }

                auth
                    // STOMP destinations - require authentication
                    .requestMatchers("/topic/**", "/queue/**").authenticated()
                    .requestMatchers("/app/**").authenticated()
                    .requestMatchers("/api/admin/**").hasAnyRole("SUPER_ADMIN", "ADMIN", "EMPLOYEE")
                    // Require authentication for all other API endpoints
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().authenticated();
            })
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    log.warn("认证失败: {} - URI={}", authException.getMessage(), request.getRequestURI());
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\",\"data\":null}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    log.warn("权限不足: {} - URI={}", accessDeniedException.getMessage(), request.getRequestURI());
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.getWriter().write("{\"code\":403,\"msg\":\"权限不足\",\"data\":null}");
                })
            );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(SecurityConstants.BCRYPT_STRENGTH);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String allowedOrigins = environment.getProperty("app.cors.allowed-origins",
                "http://localhost:*,http://127.0.0.1:*,https://localhost:*,https://127.0.0.1:*");
        config.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
