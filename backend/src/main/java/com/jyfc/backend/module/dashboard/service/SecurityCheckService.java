package com.jyfc.backend.module.dashboard.service;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.security.SecurityConstants;
import com.jyfc.backend.core.security.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.jyfc.backend.module.auth.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 安全检查服务
 * 提供登录注册的安全防护机制
 */
@Service
public class SecurityCheckService {
    
    private static final Logger log = LoggerFactory.getLogger(SecurityCheckService.class);
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Value("${security.login.max-attempts-per-ip:10}")
    private int maxLoginAttemptsPerIp;

    @Value("${security.login.rate-limit-window:15}")
    private int rateLimitWindowMinutes;

    @Autowired
    public SecurityCheckService(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate,
                                @Autowired(required = false) org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate,
                                UserService userService) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    
    private boolean isRedisAvailable() {
        return stringRedisTemplate != null && redisTemplate != null;
    }
    
    // ==================== 登录防护 ====================
    
    /**
     * 检查账户是否被锁定
     */
    public boolean isAccountLocked(String username) {
        if (username == null) return false;
        if (!isRedisAvailable()) throw new BusinessException("安全服务不可用，请稍后重试");
        String lockKey = "lock:account:" + username;
        String locked = stringRedisTemplate.opsForValue().get(lockKey);
        return "true".equals(locked);
    }
    
    /**
     * 锁定账户
     */
    public void lockAccount(String username) {
        if (username == null) return;
        if (!isRedisAvailable()) throw new BusinessException("安全服务不可用，请稍后重试");
        String lockKey = "lock:account:" + username;
        stringRedisTemplate.opsForValue().set(
            lockKey,
            "true",
            SecurityConstants.LOCK_TIME_DURATION,
            TimeUnit.MINUTES
        );
        log.warn("Account locked: {}", username);
    }
    
    /**
     * 解锁账户
     */
    public void unlockAccount(String username) {
        if (username == null) return;
        if (!isRedisAvailable()) throw new BusinessException("安全服务不可用，请稍后重试");
        String lockKey = "lock:account:" + username;
        stringRedisTemplate.delete(lockKey);
        log.info("Account unlocked: {}", username);
    }
    
    /**
     * 记录登录失败尝试
     */
    public void recordLoginFailure(String identifier) {
        if (identifier == null) return;
        if (!isRedisAvailable()) throw new BusinessException("安全服务不可用，请稍后重试");
        String attemptKey = "login:failures:" + identifier;
        
        // Atomic increment
        Long failureCount = stringRedisTemplate.opsForValue().increment(attemptKey);
        
        if (failureCount != null && failureCount == 1) {
             stringRedisTemplate.expire(attemptKey, rateLimitWindowMinutes, TimeUnit.MINUTES);
        }
        
        // 如果超过最大尝试次数，锁定账户
        if (failureCount != null && failureCount >= SecurityConstants.MAX_LOGIN_ATTEMPTS) {
            lockAccount(identifier);
        }
    }
    
    /**
     * 清除登录失败记录
     */
    public void clearLoginFailures(String identifier) {
        if (identifier == null) return;
        if (!isRedisAvailable()) throw new BusinessException("安全服务不可用，请稍后重试");
        String attemptKey = "login:failures:" + identifier;
        stringRedisTemplate.delete(attemptKey);
    }
    
    /**
     * 获取登录失败次数
     */
    public int getLoginFailureCount(String identifier) {
        if (identifier == null) return 0;
        if (!isRedisAvailable()) throw new BusinessException("安全服务不可用，请稍后重试");
        String attemptKey = "login:failures:" + identifier;
        String countStr = stringRedisTemplate.opsForValue().get(attemptKey);
        return countStr != null ? Integer.parseInt(countStr) : 0;
    }
    
    /**
     * 检查IP是否超过登录速率限制
     */
    public boolean exceedsLoginRateLimit(String ipAddress) {
        if (ipAddress == null) return false;
        if (!isRedisAvailable()) throw new BusinessException("安全服务不可用，请稍后重试");
        String rateLimitKey = "ratelimit:login:" + ipAddress;
        Long count = stringRedisTemplate.opsForValue().increment(rateLimitKey);
        
        if (count != null && count == 1) {
            stringRedisTemplate.expire(rateLimitKey, rateLimitWindowMinutes, TimeUnit.MINUTES);
        }

        return count != null && count > maxLoginAttemptsPerIp;
    }
    
    /**
     * 检查IP是否结合用户检测到多账号登录可疑行为
     */
    public boolean detectMultiAccountLoginFromIP(String ipAddress, String username) {
        if (ipAddress == null || username == null) return false;
        if (!isRedisAvailable()) throw new BusinessException("安全服务不可用，请稍后重试");
        String key = "multi:login:ip:" + ipAddress;
        
        // Use Redis Set structure
        stringRedisTemplate.opsForSet().add(key, username);
        stringRedisTemplate.expire(key, 1, TimeUnit.HOURS);
        
        Long size = stringRedisTemplate.opsForSet().size(key);
        
        // 如果同一IP在短时间内登录超过3个不同账户，认为是可疑的
        return size != null && size > SecurityConstants.MAX_CONCURRENT_SESSIONS;
    }
    
    // ==================== 注册防护 ====================
    
    /**
     * 检查IP是否超过注册速率限制
     */
    public boolean exceedsRegistrationRateLimit(String ipAddress) {
        if (ipAddress == null) return false;
        if (!isRedisAvailable()) throw new BusinessException("安全服务不可用，请稍后重试");
        String rateLimitKey = "ratelimit:register:ip:" + ipAddress;
        Long count = stringRedisTemplate.opsForValue().increment(rateLimitKey);
        
        if (count != null && count == 1) {
            stringRedisTemplate.expire(rateLimitKey, SecurityConstants.REGISTRATION_WINDOW, TimeUnit.HOURS);
        }
        
        return count != null && count > SecurityConstants.MAX_REGISTRATIONS_PER_IP;
    }
    
    /**
     * 检查设备是否超过注册限制
     */
    public boolean exceedsDeviceRegistrationLimit(String deviceFingerprint) {
        if (deviceFingerprint == null) return false;
        if (!isRedisAvailable()) throw new BusinessException("安全服务不可用，请稍后重试");
        String rateLimitKey = "ratelimit:register:device:" + deviceFingerprint;
        Long count = stringRedisTemplate.opsForValue().increment(rateLimitKey);
        
        if (count != null && count == 1) {
            stringRedisTemplate.expire(rateLimitKey, SecurityConstants.REGISTRATION_WINDOW, TimeUnit.HOURS);
        }
        
        return count != null && count > SecurityConstants.MAX_REGISTRATIONS_PER_DEVICE;
    }
    
    /**
     * 检查是否可疑的注册行为
     */
    public boolean isSuspiciousRegistration(String ipAddress, String userAgent, String deviceFingerprint) {
        StringBuilder reasons = new StringBuilder();
        
        // 1.检查User-Agent
        if (ValidationUtils.isSuspiciousUserAgent(userAgent)) {
            reasons.append("Suspicious User-Agent; ");
        }
        
        // 2.检查IP是否为代理
        if (!ValidationUtils.isPrivateIP(ipAddress) && ValidationUtils.isProbablyProxy(ipAddress)) {
            reasons.append("Proxy/VPN detected; ");
        }
        
        if (reasons.length() > 0) {
            log.warn("Suspicious registration detected: {} | IP: {} | Device: {}", reasons, ipAddress, deviceFingerprint);
            return true;
        }
        
        return false;
    }
    
    // ==================== 撞库防护 ====================
    
    /**
     * 记录登录尝试（用于撞库检测）
     */
    public void recordLoginAttempt(String ipAddress, String username, boolean success) {
        if (ipAddress == null) return;
        if (!isRedisAvailable()) throw new BusinessException("安全服务不可用，请稍后重试");
        String key = "bruteforce:attempt:" + ipAddress + ":" + username;

        if (!success) {
            Long attempts = redisTemplate.opsForValue().increment(key);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(key, 1, TimeUnit.HOURS);
            }
        } else {
            redisTemplate.delete(key);
        }
    }
    
    /**
     * 检测暴力破解攻击
     */
    public boolean detectBruteForceAttack(String ipAddress, String username) {
        if (ipAddress == null) return false;
        if (!isRedisAvailable()) throw new BusinessException("安全服务不可用，请稍后重试");
        String key = "bruteforce:attempt:" + ipAddress + ":" + username;
        Object attempts = redisTemplate.opsForValue().get(key);
        
        if (attempts != null) {
            Long attemptCount = 0L;
            if (attempts instanceof Number) {
                attemptCount = ((Number) attempts).longValue();
            }
            
            if (attemptCount > SecurityConstants.MAX_LOGIN_ATTEMPTS) {
                log.warn("Brute force attack detected: IP={}, Username={}, Attempts={}", ipAddress, username, attemptCount);
                return true;
            }
        }
        
        return false;
    }
    
    // ==================== 虚拟机/代理检测 ====================
    
    /**
     * 检测虚拟机/容器化环境
     * 实际应使用更完善的检测方法
     */
    public boolean detectVirtualEnvironment(Map<String, String> headers) {
        if (headers == null) return false;
        String userAgent = headers.getOrDefault("User-Agent", "");
        
        // 检查常见虚拟机标志
        String[] vmIndicators = {
            "virtualbox", "vmware", "kvm", "xen", "hyper-v",
            "parallels", "qemu", "vboxga", "docker",
            "wsl", "cygwin"
        };
        
        String lowerUA = userAgent.toLowerCase();
        for (String indicator : vmIndicators) {
            if (lowerUA.contains(indicator)) {
                return true;
            }
        }
        
        return false;
    }
    
    // ==================== 数据库泄露防护 ====================
    
    /**
     * 检查邮箱是否在已知泄露数据库中
     * 实际应集成haveibeenpwned或类似服务
     */
    public boolean isEmailInLeakedDatabase(String email) {
        // TODO: 集成第三方API (HaveIBeenPwned)
        return false;
    }
    
    /**
     * 检查密码是否在常见破解密码字典中
     */
    public boolean isPasswordInBreachDatabase(String password) {
        // TODO: 集成第三方密码检查服务
        return false;
    }
    
    // ==================== 通用工具方法 ====================
    
    /**
     * 生成设备指纹
     */
    public String generateDeviceFingerprint(Map<String, String> headers) {
        if (headers == null) return "unknown";
        StringBuilder fingerprint = new StringBuilder();
        fingerprint.append(headers.getOrDefault("User-Agent", ""));
        fingerprint.append("|");
        fingerprint.append(headers.getOrDefault("Accept-Language", ""));
        fingerprint.append("|");
        fingerprint.append(headers.getOrDefault("Accept-Encoding", ""));
        
        // 简单哈希（实际应使用更复杂的方法）
        return String.valueOf(fingerprint.toString().hashCode());
    }
    
    /**
     * 记录安全事件
     */
    public void logSecurityEvent(
        SecurityConstants.AuditEventType eventType,
        String username,
        String ipAddress,
        String details
    ) {
        log.info("SECURITY_EVENT: type={}, username={}, ip={}, details={}",
            eventType, username, ipAddress, details);
        
        // 保存到Redis审计日志（保留90天）
        if (isRedisAvailable()) {
            try {
                String logKey = "audit:" + System.currentTimeMillis() + ":" + username;
                Map<String, String> auditData = Map.of(
                    "eventType", eventType.name(),
                    "username", username != null ? username : "",
                    "ipAddress", ipAddress != null ? ipAddress : "",
                    "details", details != null ? details : "",
                    "timestamp", String.valueOf(System.currentTimeMillis())
                );
                redisTemplate.opsForHash().putAll(logKey, auditData);
                redisTemplate.expire(logKey, 90, java.util.concurrent.TimeUnit.DAYS);
            } catch (Exception e) {
                log.warn("保存审计日志到Redis失败: {}", e.getMessage());
            }
        }
    }
}
