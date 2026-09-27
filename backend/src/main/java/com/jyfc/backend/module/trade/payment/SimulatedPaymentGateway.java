package com.jyfc.backend.module.trade.payment;

import com.jyfc.backend.module.trade.entity.PaymentTransaction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 模拟支付通道。
 *
 * 用于开发/演示环境端到端跑通「下单 → 发起支付 → 确认支付 → 订单已支付」全流程，
 * 不涉及真实资金与三方密钥。前端拿到 {@link PrepayResult#getConfirmPath()} 后，
 * 调用该路径即可模拟支付成功回调。
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    public static final String PROVIDER = "simulated";

    /** 供前端「确认支付」调用的相对路径。 */
    public static final String CONFIRM_PATH = "/api/app/pay/simulate/confirm";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public PrepayResult createPrepay(PaymentTransaction txn) {
        PrepayResult result = new PrepayResult();
        result.setProvider(PROVIDER);
        result.setPaymentMethod(txn.getPaymentMethod());
        result.setTransactionNo(txn.getTransactionNo());
        result.setAmount(txn.getAmount());
        result.setConfirmPath(CONFIRM_PATH);
        result.getPayParams().put("simulated", true);
        result.getPayParams().put("transactionNo", txn.getTransactionNo());
        result.getPayParams().put("confirmPath", CONFIRM_PATH);
        return result;
    }

    @Override
    public PaymentCallbackResult parseCallback(String rawBody, Map<String, String> params) {
        // 模拟通道无真实异步回调；支付成功通过 /simulate/confirm 主动确认。
        String txnNo = params != null ? params.get("out_trade_no") : null;
        if (txnNo == null || txnNo.isBlank()) {
            return PaymentCallbackResult.fail();
        }
        return PaymentCallbackResult.ok(txnNo, params.get("trade_no"));
    }
}
