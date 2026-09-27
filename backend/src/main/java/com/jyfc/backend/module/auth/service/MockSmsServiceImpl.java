package com.jyfc.backend.module.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Mock 短信服务实现
 * 将验证码打印到控制台日志, 不实际发送短信
 * 生产环境替换为真实短信服务商实现
 */
@Service
public class MockSmsServiceImpl implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(MockSmsServiceImpl.class);

    @Value("${sms.mock.log-code:false}")
    private boolean logCode;

    @Override
    public boolean sendVerificationCode(String phone, String code) {
        log.info("===== [Mock SMS] =====");
        log.info("手机号: {}", phone);
        if (logCode) {
            log.info("验证码: {}", code);
        }
        log.info("======================");
        return true;
    }
}
