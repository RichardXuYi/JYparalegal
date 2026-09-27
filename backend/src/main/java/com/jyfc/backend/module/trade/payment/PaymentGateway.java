package com.jyfc.backend.module.trade.payment;

import com.jyfc.backend.module.trade.entity.PaymentTransaction;

import java.util.Map;

/**
 * 可插拔支付网关抽象。
 *
 * 每个渠道（模拟/微信/支付宝）实现一个 {@link PaymentGateway}，通过 {@link #provider()}
 * 标识自己。运行时由 {@link PaymentGatewayResolver} 依据 {@code payment.provider} 配置与
 * 前端选择的支付方式路由到具体实现，从而做到「仅改配置即可切换真实/模拟通道」。
 */
public interface PaymentGateway {

    /** 网关标识：simulated / wechat / alipay。 */
    String provider();

    /**
     * 预下单：根据支付流水生成前端发起支付所需参数。
     */
    PrepayResult createPrepay(PaymentTransaction txn);

    /**
     * 解析并验签渠道回调。
     *
     * @param rawBody 原始请求体（微信为 XML）
     * @param params  表单/查询参数（支付宝为键值对）
     */
    PaymentCallbackResult parseCallback(String rawBody, Map<String, String> params);
}
