package com.jyfc.backend.core.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.util.List;

/**
 * 根据请求路径自动切换 Session Cookie 名称:
 * /api/admin/** → ADMIN_SESSIONID
 * 其他路径      → JSESSIONID
 *
 * 实现同一浏览器中 admin 和 user 的 session 隔离。
 */
public class PathAwareCookieSerializer implements CookieSerializer {

    private static final String ADMIN_COOKIE_NAME = "ADMIN_SESSIONID";
    private static final String USER_COOKIE_NAME = "JSESSIONID";
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";

    private final DefaultCookieSerializer adminSerializer;
    private final DefaultCookieSerializer userSerializer;

    public PathAwareCookieSerializer(Environment environment) {
        boolean useSecure = isProductionProfile(environment);
        this.adminSerializer = createSerializer(ADMIN_COOKIE_NAME, useSecure);
        this.userSerializer = createSerializer(USER_COOKIE_NAME, useSecure);
    }

    private DefaultCookieSerializer createSerializer(String cookieName, boolean useSecure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookieName);
        serializer.setCookiePath("/api");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(useSecure);
        serializer.setSameSite("Lax");
        return serializer;
    }

    private static boolean isProductionProfile(Environment environment) {
        String[] active = environment.getActiveProfiles();
        if (active == null || active.length == 0) {
            return false;
        }
        for (String p : active) {
            if ("prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    private DefaultCookieSerializer selectSerializer(HttpServletRequest request) {
        if (request.getRequestURI().startsWith(ADMIN_PATH_PREFIX)) {
            return adminSerializer;
        }
        return userSerializer;
    }

    @Override
    public List<String> readCookieValues(HttpServletRequest request) {
        return selectSerializer(request).readCookieValues(request);
    }

    @Override
    public void writeCookieValue(CookieSerializer.CookieValue cookieValue) {
        selectSerializer(cookieValue.getRequest()).writeCookieValue(cookieValue);
    }
}
