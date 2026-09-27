package com.jyfc.backend.module.trade.payment;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付网关预下单结果。
 *
 * 由具体 {@link PaymentGateway} 实现填充，供前端发起支付使用。
 * 不同渠道的差异化参数放入 {@link #payParams}（如微信 prepayId、支付宝 orderString）。
 */
public class PrepayResult {

    /** 产生该预下单的网关标识（simulated/wechat/alipay）。 */
    private String provider;

    /** 本次支付对应的支付方式（前端选择，用于展示）。 */
    private String paymentMethod;

    /** 支付流水号。 */
    private String transactionNo;

    /** 应付金额。 */
    private BigDecimal amount;

    /** 渠道差异化参数。 */
    private Map<String, Object> payParams = new HashMap<>();

    /**
     * 模拟通道下，供前端「确认支付」调用的相对路径；真实渠道为 null。
     */
    private String confirmPath;

    public PrepayResult() {}

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getTransactionNo() { return transactionNo; }
    public void setTransactionNo(String transactionNo) { this.transactionNo = transactionNo; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Map<String, Object> getPayParams() { return payParams; }
    public void setPayParams(Map<String, Object> payParams) { this.payParams = payParams; }

    public String getConfirmPath() { return confirmPath; }
    public void setConfirmPath(String confirmPath) { this.confirmPath = confirmPath; }
}
