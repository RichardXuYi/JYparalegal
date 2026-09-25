package com.jyfc.backend.module.trade.payment;

import com.jyfc.backend.module.trade.entity.PaymentTransaction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付宝网关（预留实现）。
 *
 * 仅在 {@code payment.provider=alipay} 时生效。当前为占位骨架：预下单与验签逻辑
 * 需接入支付宝 SDK 并使用 {@link PaymentProperties#getAlipay()} 中的应用密钥完成。
 * 未接入前切换到该通道会抛出未实现异常，避免误用。
 */
@Component
public class AlipayGateway implements PaymentGateway {

    public static final String PROVIDER = "alipay";

    @SuppressWarnings("unused")
    private final PaymentProperties properties;

    public AlipayGateway(PaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public PrepayResult createPrepay(PaymentTransaction txn) {
        // TODO: 接入支付宝下单 API，使用 properties.getAlipay() 中的 appId/privateKey/publicKey/gatewayUrl，
        //       返回真实的 orderString 供前端调起支付。
        throw new UnsupportedOperationException("支付宝通道尚未接入，请配置 payment.alipay.* 并实现 createPrepay");
    }

    @Override
    public PaymentCallbackResult parseCallback(String rawBody, Map<String, String> params) {
        if (params == null) {
            return PaymentCallbackResult.fail();
        }
        // TODO: 使用支付宝公钥验签 params；验签通过后返回 out_trade_no 与 trade_no。
        // 未实现验签，暂不认为可信，返回失败以避免在真实通道下误判支付成功。
        return PaymentCallbackResult.fail();
    }
}
