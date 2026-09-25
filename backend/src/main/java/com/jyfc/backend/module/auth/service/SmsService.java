package com.jyfc.backend.module.auth.service;

/**
 * 短信发送服务接口
 * 当前使用 Mock 实现,后续可替换为阿里云/腾讯云等真实短信服务
 */
public interface SmsService {

    /**
     * 发送验证码短信
     * @param phone 手机号
     * @param code 验证码
     * @return 是否发送成功
     */
    boolean sendVerificationCode(String phone, String code);
}
