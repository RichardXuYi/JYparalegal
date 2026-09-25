package com.jyfc.backend.core.security;

import java.util.regex.Pattern;

/**
 * 验证工具类
 */
public class ValidationUtils {
    
    /**
     * 验证邮箱格式
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }
    
    /**
     * 验证手机号格式
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        return phone.matches("^1[3-9]\\d{9}$");
    }
    
    /**
     * 验证用户名格式和合法性
     */
    public static ValidationResult validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return new ValidationResult(false, "用户名不能为空");
        }
        
        String trimmed = username.trim();
        
        // 检查长度
        if (trimmed.length() < SecurityConstants.USERNAME_MIN_LENGTH) {
            return new ValidationResult(false, "用户名至少3个字符");
        }
        
        if (trimmed.length() > SecurityConstants.USERNAME_MAX_LENGTH) {
            return new ValidationResult(false, "用户名最多50个字符");
        }
        
        // 检查禁用列表（大小写不敏感）
        if (SecurityConstants.FORBIDDEN_USERNAMES.contains(trimmed.toLowerCase())) {
            return new ValidationResult(false, SecurityConstants.ResponseMessages.FORBIDDEN_USERNAME);
        }
        
        // 检查禁用模式
        for (Pattern pattern : SecurityConstants.FORBIDDEN_USERNAME_PATTERNS) {
            if (pattern.matcher(trimmed).matches()) {
                return new ValidationResult(false, "用户名格式不符合要求");
            }
        }
        
        // 检查用户名格式
        if (!SecurityConstants.USERNAME_PATTERN.matcher(trimmed).matches()) {
            return new ValidationResult(false, SecurityConstants.ResponseMessages.INVALID_USERNAME_FORMAT);
        }
        
        return new ValidationResult(true, "");
    }
    
    /**
     * 验证密码强度
     */
    public static ValidationResult validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return new ValidationResult(false, "密码不能为空");
        }
        
        if (password.length() < SecurityConstants.PASSWORD_MIN_LENGTH) {
            return new ValidationResult(false, "密码至少6位");
        }
        
        if (password.length() > SecurityConstants.PASSWORD_MAX_LENGTH) {
            return new ValidationResult(false, "密码最多100位");
        }
        
        // 检查弱密码
        if (SecurityConstants.WEAK_PASSWORDS.contains(password.toLowerCase())) {
            return new ValidationResult(false, SecurityConstants.ResponseMessages.WEAK_PASSWORD);
        }
        
        return new ValidationResult(true, "");
    }
    
    /**
     * 检查是否为强密码
     */
    public static boolean isStrongPassword(String password) {
        if (password == null) {
            return false;
        }
        return SecurityConstants.STRONG_PASSWORD_PATTERN.matcher(password).matches();
    }
    
    /**
     * 从User-Agent检测爬虫/自动化工具
     */
    public static boolean isSuspiciousUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return true; // 没有User-Agent被认为是可疑的
        }
        
        String lowerUA = userAgent.toLowerCase();
        for (String suspicious : SecurityConstants.SUSPICIOUS_USER_AGENTS) {
            if (lowerUA.contains(suspicious)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查IP是否为代理/VPN
     * 简单实现，实际应使用第三方API服务
     */
    public static boolean isProbablyProxy(String ip) {
        // 私网IP不是代理
        if (isPrivateIP(ip)) {
            return false;
        }
        
        // 这里应该调用实际的IP检测服务
        // 例如：IPQualityScore, MaxMind GeoIP2等
        return false;
    }
    
    /**
     * 检查是否为私网IP
     */
    public static boolean isPrivateIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        return ip.startsWith("127.") ||           // 本地
               ip.startsWith("192.168.") ||       // 私网
               ip.startsWith("10.") ||            // 私网
               ip.startsWith("172.") ||           // 私网
               ip.equals("::1") ||                // IPv6本地
               ip.startsWith("fc00:") ||          // IPv6私网
               ip.startsWith("fd00:");             // IPv6私网
    }
    
    /**
     * 验证验证码格式
     */
    public static boolean isValidVerificationCode(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        return code.matches("^\\d{6}$");
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        
        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
