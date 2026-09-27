package com.jyfc.backend.module.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码管理服务
 * 负责验证码的生成, 存储, 校验和防刷控制
 */
@Service
public class SmsVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SmsVerificationService.class);
    private static final int CODE_LENGTH = 6;
    private static final long CODE_EXPIRE_MINUTES = 5; // 5分钟有效期
    private static final long SEND_INTERVAL_SECONDS = 60; // 60秒发送间隔
    // BE-007: 验证码校验失败上限与锁定窗口，防止爆破
    private static final int MAX_VERIFY_FAILURES = 5;
    private static final long VERIFY_FAIL_WINDOW_MINUTES = 10;
    // BE-007/BE-009: 每 IP 发送与校验的小时配额，防止刷量/枚举
    private static final int MAX_SEND_PER_IP_HOUR = 10;
    private static final int MAX_VERIFY_PER_IP_HOUR = 30;

    private final SmsService smsService;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public SmsVerificationService(SmsService smsService,
                                  @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.smsService = smsService;
        this.redisTemplate = redisTemplate;
    }
    
    private boolean isRedisAvailable() {
        return redisTemplate != null;
    }

    /**
     * 生成并发送验证码
     * @param phone 手机号
     * @return 发送结果消息
     * @throws IllegalStateException 如果发送过于频繁
     */
    public String generateAndSendCode(String phone) {
        if (phone == null || phone.isBlank()) {
            log.warn("Cannot send verification code: phone is null or blank");
            return "手机号不能为空";
        }
        if (!isRedisAvailable()) {
            log.warn("Redis not available, cannot send verification code to: {}", phone);
            return "验证码服务暂不可用";
        }
        String limitKey = "sms:limit:" + phone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            Long expire = redisTemplate.getExpire(limitKey, TimeUnit.SECONDS);
            throw new IllegalStateException("发送过于频繁, 请" + (expire != null ? expire : 60) + "秒后重试");
        }

        String code = generateCode();
        // In a real scenario, we would send the SMS here
        // boolean sent = smsService.sendVerificationCode(phone, code);
        // For now, assume success or use a mock
        boolean sent = true; 
        try {
             sent = smsService.sendVerificationCode(phone, code);
        } catch (Exception e) {
             log.error("Failed to send SMS to {}", phone, e);
             sent = false;
        }
        
        if (!sent) {
            throw new RuntimeException("验证码发送失败, 请稍后重试");
        }

        // Store code with expiration
        String codeKey = "sms:code:" + phone;
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        
        // Set rate limit
        redisTemplate.opsForValue().set(limitKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        // BE-002: 不得将验证码明文写入日志（防泄露）。需本地调试时，
        // 由 MockSmsServiceImpl 的 sms.mock.log-code 开关受控输出。
        log.info("Verification code generated and sent for phone: {}", phone);
        return "验证码已发送";
    }

    /**
     * 校验验证码
     * @param phone 手机号
     * @param code 用户输入的验证码
     * @return 是否验证成功
     */
    public boolean verifyCode(String phone, String code) {
        if (phone == null || code == null) return false;
        if (!isRedisAvailable()) return false;
        String codeKey = "sms:code:" + phone;
        String failKey = "sms:fail:" + phone;
        String storedCode = redisTemplate.opsForValue().get(codeKey);
        
        if (storedCode == null) {
            log.debug("No verification code found or expired for phone: {}", phone);
            return false;
        }

        if (storedCode.equals(code)) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(failKey);
            // Optional: clear limit key if we want to allow immediate re-send after successful verification?
            // Usually we keep the limit to prevent spam even after success.log.debug("Verification code matched for phone: {}", phone);
            return true;
        }

        // BE-007: 记录校验失败次数，达到上限则作废当前验证码，迫使重新获取，防止逐位爆破。
        Long failures = redisTemplate.opsForValue().increment(failKey);
        if (failures != null && failures == 1L) {
            redisTemplate.expire(failKey, VERIFY_FAIL_WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        if (failures != null && failures >= MAX_VERIFY_FAILURES) {
            redisTemplate.delete(codeKey);
            log.warn("Verification code invalidated after {} failed attempts for phone: {}", failures, phone);
        }
        log.debug("Verification code mismatch for phone: {}", phone);
        return false;
    }

    /**
     * BE-007/BE-009: 校验并递增某 IP 在滑动小时窗口内的验证码发送次数。
     * @return true 表示已超过发送配额（应拒绝）。
     */
    public boolean exceedsSendIpLimit(String ip) {
        return exceedsIpLimit("sms:ip:send:" + ip, ip, MAX_SEND_PER_IP_HOUR);
    }

    /**
     * BE-007: 校验并递增某 IP 在滑动小时窗口内的验证码校验次数（防枚举）。
     * @return true 表示已超过校验配额（应拒绝）。
     */
    public boolean exceedsVerifyIpLimit(String ip) {
        return exceedsIpLimit("sms:ip:verify:" + ip, ip, MAX_VERIFY_PER_IP_HOUR);
    }

    private boolean exceedsIpLimit(String key, String ip, int maxPerHour) {
        if (!isRedisAvailable() || ip == null || ip.isEmpty()) return false;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 1, TimeUnit.HOURS);
        }
        return count != null && count > maxPerHour;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
