package com.jyfc.backend.module.auth.service;

import com.jyfc.backend.core.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证服务
 * 处理邮箱验证码的发送、验证和有效期管理
 */
@Service
public class EmailVerificationService {
    
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public EmailVerificationService(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    private boolean isRedisAvailable() {
        return redisTemplate != null;
    }
    
    /**
     * 生成验证码
     */
    public String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < SecurityConstants.EMAIL_VERIFICATION_CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
    
    /**
     * 发送验证码到邮箱
     * 实际需要集成邮件服务
     */
    public void sendVerificationCode(String email) {
        if (email == null || email.isBlank()) {
            log.warn("Cannot send verification code: email is null or blank");
            return;
        }
        if (!isRedisAvailable()) {
            log.warn("Redis not available, cannot send verification code to: {}", email);
            return;
        }
        // 检查是否频繁请求
        String requestCountKey = "email:verify:requests:" + email;
        Object count = redisTemplate.opsForValue().get(requestCountKey);
        
        if (count != null) {
            long requestCount = ((Number) count).longValue();
            if (requestCount >= SecurityConstants.MAX_VERIFICATION_CODE_REQUESTS) {
                throw new IllegalStateException("验证码请求过于频繁，请24小时后重试");
            }
        }
        
        // 检查发送间隔
        String lastSendKey = "email:verify:lastsend:" + email;
        Object lastSend = redisTemplate.opsForValue().get(lastSendKey);
        if (lastSend != null) {
            throw new IllegalStateException(
                "请等待" + SecurityConstants.VERIFICATION_CODE_SEND_INTERVAL + "秒后再请求"
            );
        }
        
        // 生成验证码
        String code = generateVerificationCode();
        String codeKey = "email:verify:code:" + email;
        
        // 保存验证码
        redisTemplate.opsForValue().set(
            codeKey,
            (Object) code,
            SecurityConstants.EMAIL_VERIFICATION_EXPIRY,
            TimeUnit.MINUTES
        );
        
        // 记录最后发送时间
        redisTemplate.opsForValue().set(
            lastSendKey,
            (Object) "1",
            SecurityConstants.VERIFICATION_CODE_SEND_INTERVAL,
            TimeUnit.SECONDS
        );
        
        // 增加请求计数（原子操作，避免竞态）
        Long newCount = redisTemplate.opsForValue().increment(requestCountKey);
        if (newCount != null && newCount == 1L) {
            redisTemplate.expire(requestCountKey, 24, TimeUnit.HOURS);
        }
        
        // TODO: 实际发送邮件
        // 这里需要集成真实的邮件服务（如JavaMail、SendGrid等）
        sendEmailMessage(email, code);
        
        log.info("Email verification code sent to: {}", email);
    }
    
    /**
     * 验证码
     */
    public boolean verifyCode(String email, String code) {
        if (email == null || code == null) return false;
        if (!isRedisAvailable()) return false;
        String codeKey = "email:verify:code:" + email;
        Object storedCode = redisTemplate.opsForValue().get(codeKey);
        
        if (storedCode == null) {
            log.warn("Verification code not found or expired for: {}", email);
            return false;
        }
        
        boolean isValid = storedCode.toString().equals(code);
        
        if (isValid) {
            // 验证成功，删除验证码
            redisTemplate.delete(codeKey);
            log.info("Email verified successfully: {}", email);
        } else {
            log.warn("Invalid verification code for: {}", email);
        }
        
        return isValid;
    }
    
    /**
     * 检查邮箱是否已验证
     */
    public boolean isEmailVerified(String email) {
        if (email == null) return false;
        if (!isRedisAvailable()) return false;
        String verifiedKey = "email:verified:" + email;
        Boolean verified = (Boolean) redisTemplate.opsForValue().get(verifiedKey);
        return verified != null && verified;
    }
    
    /**
     * 标记邮箱为已验证
     */
    public void markEmailAsVerified(String email) {
        if (email == null) return;
        if (!isRedisAvailable()) return;
        String verifiedKey = "email:verified:" + email;
        redisTemplate.opsForValue().set(verifiedKey, (Object) Boolean.TRUE, 365, TimeUnit.DAYS);
        log.info("Email marked as verified: {}", email);
    }
    
    /**
     * 清除邮箱验证状态
     */
    public void clearEmailVerification(String email) {
        if (email == null) return;
        if (!isRedisAvailable()) return;
        String verifiedKey = "email:verified:" + email;
        String codeKey = "email:verify:code:" + email;
        redisTemplate.delete(List.of(verifiedKey, codeKey));
    }
    
    /**
     * 实际发送邮件的方法
     * TODO: 集成真实邮件服务
     */
    private void sendEmailMessage(String email, String code) {
        
        
        // TODO: 实际调用邮件API
        // mailService.sendEmail(email, "邮箱验证码", body);
        
        log.info("Email verification code sent to {}", email);
    }
    
    /**
     * 获取剩余过期时间（秒）
     */
    public Long getCodeExpiryTime(String email) {
        if (email == null) return null;
        if (!isRedisAvailable()) return null;
        String codeKey = "email:verify:code:" + email;
        Long ttl = redisTemplate.getExpire(codeKey, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : null;
    }
}
