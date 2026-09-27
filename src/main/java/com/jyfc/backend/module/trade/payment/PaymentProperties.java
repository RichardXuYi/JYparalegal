package com.jyfc.backend.module.trade.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付相关配置（前缀 {@code payment}）。
 *
 * {@code provider} 决定当前生效的支付通道；默认 {@code simulated}（模拟通道，可端到端跑通，
 * 不涉及真实资金与密钥）。真实渠道 {@code alipay}/{@code wechat} 的密钥占位放在对应 Map 中，
 * 仅在切换到真实通道时才需要填充，且不应提交真实密钥。
 */
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    /** 生效的支付通道：simulated / alipay / wechat。 */
    private String provider = "simulated";

    /** 支付宝渠道配置占位（appId/privateKey/publicKey/gatewayUrl 等）。 */
    private Map<String, String> alipay = new HashMap<>();

    /** 微信支付渠道配置占位（appId/mchId/apiKey/certPath 等）。 */
    private Map<String, String> wechat = new HashMap<>();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Map<String, String> getAlipay() { return alipay; }
    public void setAlipay(Map<String, String> alipay) { this.alipay = alipay; }

    public Map<String, String> getWechat() { return wechat; }
    public void setWechat(Map<String, String> wechat) { this.wechat = wechat; }
}
