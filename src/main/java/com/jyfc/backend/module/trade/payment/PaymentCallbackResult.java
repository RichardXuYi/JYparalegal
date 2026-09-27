package com.jyfc.backend.module.trade.payment;

/**
 * 支付回调解析结果。
 *
 * 各渠道回调格式不同（微信 XML、支付宝表单参数），由对应 {@link PaymentGateway}
 * 解析并验签后归一为该结构，交由上层统一处理支付成功。
 */
public class PaymentCallbackResult {

    /** 是否解析并验签成功。 */
    private final boolean verified;

    /** 商户侧支付流水号（out_trade_no）。 */
    private final String transactionNo;

    /** 渠道侧交易号（trade_no / transaction_id）。 */
    private final String channelTradeNo;

    private PaymentCallbackResult(boolean verified, String transactionNo, String channelTradeNo) {
        this.verified = verified;
        this.transactionNo = transactionNo;
        this.channelTradeNo = channelTradeNo;
    }

    public static PaymentCallbackResult ok(String transactionNo, String channelTradeNo) {
        return new PaymentCallbackResult(true, transactionNo, channelTradeNo);
    }

    public static PaymentCallbackResult fail() {
        return new PaymentCallbackResult(false, null, null);
    }

    public boolean isVerified() { return verified; }
    public String getTransactionNo() { return transactionNo; }
    public String getChannelTradeNo() { return channelTradeNo; }
}
