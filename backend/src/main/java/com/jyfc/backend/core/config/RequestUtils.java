package com.jyfc.backend.core.config;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * 请求工具类 - 提取请求相关信息
 */
public class RequestUtils {

    /**
     * 可信代理 IP 白名单。
     * 只有来自这些 IP 的请求才信任其 X-Forwarded-For 等代理头。
     * 包含 loopback / RFC1918 本地网络常见反向代理地址。
     * 生产环境应根据实际代理出口 IP 配置；可通过静态方法 addTrustedProxy() 扩展。
     */
    private static final Set<String> TRUSTED_PROXY_IPS = new java.util.concurrent.CopyOnWriteArraySet<>(
        Set.of(
            "127.0.0.1", "::1", "0:0:0:0:0:0:0:1",  // loopback
            "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"  // RFC1918
        )
    );

    /**
     * 添加可信代理 IP（CIDR 记法或单 IP）。
     * 入口点如启动配置、环境变量读取等可调用此方法注册生产代理地址。
     */
    public static void addTrustedProxy(String cidrOrIp) {
        if (cidrOrIp != null && !cidrOrIp.isBlank()) {
            TRUSTED_PROXY_IPS.add(cidrOrIp.trim());
        }
    }

    /**
     * 获取客户端真实IP地址
     *
     * 仅当请求来自可信代理时才读取 X-Forwarded-For 等代理头。
     * 否则直接返回 RemoteAddr，防止客户端伪造 IP。
     */
    public static String getClientIpAddress() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }

        String remoteAddr = request.getRemoteAddr();

        // 仅当上游是可信代理时才信任代理头
        if (isTrustedProxy(remoteAddr)) {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // 多个IP以逗号分隔，取第一个（最左侧为原始客户端）
                return ip.split(",")[0].trim();
            }

            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            ip = request.getHeader("Proxy-Client-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            ip = request.getHeader("WL-Proxy-Client-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            ip = request.getHeader("HTTP_CLIENT_IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
        }

        return remoteAddr;
    }

    /**
     * 检查 IP 是否在可信代理白名单内（支持 CIDR 记法）。
     */
    private static boolean isTrustedProxy(String ip) {
        if (ip == null) {
            return false;
        }
        for (String trusted : TRUSTED_PROXY_IPS) {
            if (matchesCidrOrExact(ip, trusted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 简单 CIDR v4 匹配 / 精确匹配。
     */
    private static boolean matchesCidrOrExact(String ip, String cidrOrIp) {
        if (ip.equals(cidrOrIp)) {
            return true;
        }
        int slash = cidrOrIp.indexOf('/');
        if (slash < 0) {
            return false;
        }
        try {
            String prefix = cidrOrIp.substring(0, slash);
            int bits = Integer.parseInt(cidrOrIp.substring(slash + 1));
            return ipInCidr(ip, prefix, bits);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean ipInCidr(String ip, String prefix, int bits) {
        long ipLong = ipToLong(ip);
        long prefixLong = ipToLong(prefix);
        long mask = bits == 0 ? 0 : (0xFFFFFFFFL << (32 - bits)) & 0xFFFFFFFFL;
        return (ipLong & mask) == (prefixLong & mask);
    }

    private static long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return 0;
        }
        return (Long.parseLong(parts[0]) << 24)
            | (Long.parseLong(parts[1]) << 16)
            | (Long.parseLong(parts[2]) << 8)
            | Long.parseLong(parts[3]);
    }
    
    /**
     * 获取User-Agent
     */
    public static String getUserAgent() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        return request.getHeader("User-Agent");
    }
    
    /**
     * 获取Accept-Language
     */
    public static String getAcceptLanguage() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        return request.getHeader("Accept-Language");
    }
    
    /**
     * 获取Accept-Encoding
     */
    public static String getAcceptEncoding() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        return request.getHeader("Accept-Encoding");
    }
    
    /**
     * 获取所有请求头
     */
    public static java.util.Map<String, String> getRequestHeaders() {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        HttpServletRequest request = getRequest();
        if (request != null) {
            java.util.Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headers.put(name, request.getHeader(name));
            }
        }
        return headers;
    }
    
    /**
     * 获取当前HttpServletRequest
     */
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest();
    }
}
