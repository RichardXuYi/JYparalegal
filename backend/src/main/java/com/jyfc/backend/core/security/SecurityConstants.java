package com.jyfc.backend.core.security;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 安全相关常数定义
 */
public class SecurityConstants {
    
    // ==================== 禁用的用户名 ====================
    public static final Set<String> FORBIDDEN_USERNAMES = Set.of(
        // 系统管理用户
        "admin", "administrator", "root", "superadmin", "super_admin",
        "sysadmin", "sys_admin", "system", "support", "help", "guest",
        // 通用敏感词
        "null", "undefined", "test", "testing", "demo", "sample",
        // 数据库
        "database", "db", "sql", "mysql", "postgres", "oracle",
        // 开发相关
        "api", "backend", "frontend", "dev", "development", "staging",
        "production", "prod", "qa", "test_user", "testuser",
        // 常见默认用户
        "user", "operator", "manager", "moderator", "editor", "viewer",
        // 仅包含空格或特殊字符
        " ", "　"
    );
    
    // ==================== 用户名验证规则 ====================
    /**
     * 用户名长度限制
     */
    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 50;
    
    /**
     * 用户名允许的字符模式：字母(含中文)、数字、下划线、横线
     */
    public static final Pattern USERNAME_PATTERN = Pattern.compile(
        "^[\\u4e00-\\u9fa5a-zA-Z0-9_-]{" + USERNAME_MIN_LENGTH + "," + USERNAME_MAX_LENGTH + "}$"
    );
    
    /**
     * 禁止的用户名模式
     */
    public static final Set<Pattern> FORBIDDEN_USERNAME_PATTERNS = Set.of(
        // 数字序列
        Pattern.compile("^\\d{10,}$"),
        // 重复字符
        Pattern.compile("^(.)\\1{4,}$"),
        // 特殊字符过多
        Pattern.compile("^[!@#$%^&*()\\-_=+\\[\\]{}|;:',.<>?/]{3,}$")
    );
    
    // ==================== 密码验证规则 ====================
    public static final int PASSWORD_MIN_LENGTH = 6;
    public static final int PASSWORD_MAX_LENGTH = 100;
    
    /**
     * 强密码模式（可选）：至少8位，包含大小写字母、数字和特殊符号
     */
    public static final Pattern STRONG_PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );
    
    /**
     * 常见弱密码列表
     */
    public static final Set<String> WEAK_PASSWORDS = Set.of(
        "123456", "123456789", "password", "12345678", "111111",
        "1234567", "123123", "1234567890", "000000", "666666",
        "888888", "999999", "qwerty", "abc123", "iloveyou"
    );
    
    // ==================== 登录安全 ====================
    /**
     * 最大登录失败次数
     */
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    
    /**
     * 登录失败后的锁定时间（分钟）
     */
    public static final int LOCK_TIME_DURATION = 15;
    
    /**
     * 最大同时在线会话数
     */
    public static final int MAX_CONCURRENT_SESSIONS = 3;
    
    /**
     * 同一IP短时间内的最大登录尝试
     */
    public static final int MAX_LOGIN_ATTEMPTS_PER_IP = 10;
    
    /**
     * 短时间段定义（分钟）
     */
    public static final int RATE_LIMIT_WINDOW = 15;
    
    // ==================== 注册安全 ====================
    /**
     * 同一IP短时间内的最大注册数
     */
    public static final int MAX_REGISTRATIONS_PER_IP = 3;
    
    /**
     * 注册时间窗口（小时）
     */
    public static final int REGISTRATION_WINDOW = 24;
    
    /**
     * 同一设备短时间内的最大注册数
     */
    public static final int MAX_REGISTRATIONS_PER_DEVICE = 2;
    
    /**
     * 需要邮箱验证的账户
     */
    public static final boolean REQUIRE_EMAIL_VERIFICATION = false;
    
    /**
     * 邮箱验证码有效期（分钟）
     */
    public static final int EMAIL_VERIFICATION_EXPIRY = 30;
    
    /**
     * 邮箱验证码长度
     */
    public static final int EMAIL_VERIFICATION_CODE_LENGTH = 6;
    
    // ==================== 验证码通用 ====================
    /**
     * 验证码发送间隔（秒）
     */
    public static final int VERIFICATION_CODE_SEND_INTERVAL = 60;
    
    /**
     * 最大验证码请求次数
     */
    public static final int MAX_VERIFICATION_CODE_REQUESTS = 5;
    
    // ==================== 虚拟机/代理检测 ====================
    /**
     * 可疑的User-Agent特征
     */
    public static final Set<String> SUSPICIOUS_USER_AGENTS = Set.of(
        "bot", "crawler", "spider", "scraper", "curl", "wget",
        "python", "java", "selenium", "puppeteer", "phantomjs",
        "headless", "automated"
    );
    
    /**
     * 已知VPN/代理服务提供商列表（需要定期更新）
     */
    public static final Set<String> KNOWN_PROXY_PROVIDERS = Set.of(
        "vpn", "proxy", "tor", "anonymizer", "hideip"
    );
    
    // ==================== 数据库泄露防护 ====================
    /**
     * 密码加密算法
     */
    public static final String PASSWORD_ENCODER_ALGORITHM = "bcrypt";
    
    /**
     * Bcrypt强度
     */
    public static final int BCRYPT_STRENGTH = 12;
    
    /**
     * 启用密码加盐
     */
    public static final boolean ENABLE_PASSWORD_SALT = true;
    
    // ==================== 审计日志 ====================
    /**
     * 审计事件类型
     */
    public enum AuditEventType {
        LOGIN_ATTEMPT,           // 登录尝试
        LOGIN_SUCCESS,           // 登录成功
        LOGIN_FAILURE,           // 登录失败
        LOGOUT,                  // 登出
        REGISTER_ATTEMPT,        // 注册尝试
        REGISTER_SUCCESS,        // 注册成功
        REGISTER_FAILURE,        // 注册失败
        EMAIL_VERIFICATION_SENT, // 邮箱验证码发送
        EMAIL_VERIFIED,          // 邮箱已验证
        PASSWORD_CHANGE,         // 密码修改
        PASSWORD_RESET,          // 密码重置
        ACCOUNT_LOCKED,          // 账户锁定
        ACCOUNT_UNLOCKED,        // 账户解锁
        SUSPICIOUS_ACTIVITY,     // 可疑活动
        DEVICE_REGISTERED,       // 设备注册
        UNAUTHORIZED_ACCESS,     // 未授权访问
        RATE_LIMIT_EXCEEDED      // 超出速率限制
    }
    
    // ==================== 响应消息 ====================
    public static class ResponseMessages {
        public static final String FORBIDDEN_USERNAME = "用户名包含禁用词汇";
        public static final String INVALID_USERNAME_FORMAT = "用户名格式错误，仅允许字母、数字、下划线和横线";
        public static final String USERNAME_TOO_SHORT = "用户名至少3个字符";
        public static final String USERNAME_TOO_LONG = "用户名最多50个字符";
        public static final String WEAK_PASSWORD = "密码过于简单，请使用更复杂的密码";
        public static final String ACCOUNT_LOCKED = "登录失败过多，账户已被锁定，请在15分钟后重试";
        public static final String RATE_LIMIT_EXCEEDED = "请求过于频繁，请稍后再试";
        public static final String SUSPICIOUS_ACTIVITY = "检测到可疑活动，请验证您的身份";
        public static final String EMAIL_VERIFICATION_REQUIRED = "邮箱验证码不正确或已过期";
        public static final String VERIFY_EMAIL_FIRST = "请先验证您的邮箱";
    }
}
