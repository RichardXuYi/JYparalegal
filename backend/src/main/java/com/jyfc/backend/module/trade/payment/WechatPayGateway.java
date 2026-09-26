package com.jyfc.backend.module.trade.payment;

import com.jyfc.backend.module.trade.entity.PaymentTransaction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 微信支付网关（预留实现）。
 *
 * 仅在 {@code payment.provider=wechat} 时生效。当前为占位骨架：预下单与验签逻辑
 * 需接入微信支付 SDK 并使用 {@link PaymentProperties#getWechat()} 中的商户密钥完成。
 * 未接入前切换到该通道会抛出未实现异常，避免误用。
 */
@Component
public class WechatPayGateway implements PaymentGateway {

    public static final String PROVIDER = "wechat";

    @SuppressWarnings("unused")
    private final PaymentProperties properties;

    public WechatPayGateway(PaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public PrepayResult createPrepay(PaymentTransaction txn) {
        // TODO: 接入微信支付统一下单 API，使用 properties.getWechat() 中的 appId/mchId/apiKey/证书，
        //       返回真实 prepayId 与前端调起支付所需的签名参数。
        throw new UnsupportedOperationException("微信支付通道尚未接入，请配置 payment.wechat.* 并实现 createPrepay");
    }

    @Override
    public PaymentCallbackResult parseCallback(String rawBody, Map<String, String> params) {
        // TODO: 解析微信回调 XML，使用商户密钥验签；验签通过后返回 out_trade_no 与 transaction_id。
        String txnNo = extractXmlValue(rawBody, "out_trade_no");
        if (txnNo == null) {
            return PaymentCallbackResult.fail();
        }
        // 未实现验签，暂不认为可信，返回失败以避免在真实通道下误判支付成功。
        return PaymentCallbackResult.fail();
    }

    private String extractXmlValue(String xml, String tag) {
        if (xml == null) return null;
        String startTag = "<" + tag + ">";
        String endTag = "</" + tag + ">";
        int start = xml.indexOf(startTag);
        if (start < 0) return null;
        start += startTag.length();
        int end = xml.indexOf(endTag, start);
        if (end < 0) return null;
        return xml.substring(start, end);
    }
}
