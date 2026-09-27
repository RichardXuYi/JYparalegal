package com.jyfc.backend.module.trade.payment;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 支付网关路由器。
 *
 * 依据 {@code payment.provider} 配置选择生效通道：
 * <ul>
 *   <li>simulated：所有支付走模拟通道（记录前端所选支付方式仅用于展示）。</li>
 *   <li>alipay / wechat：按前端所选支付方式路由到对应真实网关，缺省回退到当前生效通道。</li>
 * </ul>
 */
@Component
public class PaymentGatewayResolver {

    private final PaymentProperties properties;
    private final Map<String, PaymentGateway> gatewaysByProvider;

    public PaymentGatewayResolver(PaymentProperties properties, List<PaymentGateway> gateways) {
        this.properties = properties;
        this.gatewaysByProvider = gateways.stream()
                .collect(Collectors.toMap(PaymentGateway::provider, Function.identity()));
    }

    /** 当前生效通道标识。 */
    public String activeProvider() {
        return properties.getProvider();
    }

    /** 是否为模拟通道。 */
    public boolean isSimulated() {
        return SimulatedPaymentGateway.PROVIDER.equalsIgnoreCase(activeProvider());
    }

    /**
     * 为预下单选择网关。模拟通道下恒用模拟网关；否则按支付方式路由，缺省回退到生效通道。
     */
    public PaymentGateway resolveForPrepay(String paymentMethod) {
        if (isSimulated()) {
            return required(SimulatedPaymentGateway.PROVIDER);
        }
        PaymentGateway byMethod = paymentMethod != null ? gatewaysByProvider.get(paymentMethod.toLowerCase()) : null;
        return byMethod != null ? byMethod : required(activeProvider());
    }

    /** 按渠道标识获取网关（用于回调），不存在返回 null。 */
    public PaymentGateway resolveByProvider(String provider) {
        return provider != null ? gatewaysByProvider.get(provider.toLowerCase()) : null;
    }

    private PaymentGateway required(String provider) {
        PaymentGateway g = gatewaysByProvider.get(provider);
        if (g == null) {
            throw new IllegalStateException("未找到支付网关: " + provider);
        }
        return g;
    }
}
