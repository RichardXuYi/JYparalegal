package com.jyfc.backend.module.trade.service;

import com.jyfc.backend.module.trade.repository.OrderRepository;
import com.jyfc.backend.module.trade.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 支付相关的事务性业务逻辑。
 *
 * 之前该逻辑以 private @Transactional 方法形式内联在 OrderPaymentController 中，
 * 但 Spring 基于代理的 AOP 无法拦截 private 方法，@Transactional 会被静默忽略，
 * 导致订单状态与支付流水的更新不在同一事务中，存在部分失败时的数据不一致窗口。
 * 现将其提取为独立 Service 的 public 方法，由 Spring 代理保证事务边界生效。
 */
@Service("tradePaymentService")
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public PaymentService(OrderRepository orderRepository,
                          PaymentTransactionRepository paymentTransactionRepository) {
        this.orderRepository = orderRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    /**
     * F2 / BE-004: 处理支付成功（事务保证 txn + order 在同一事务中）。
     * 幂等双重保证：
     *   1) transaction_no 唯一约束（V191）阻止重复流水；
     *   2) 对流水行 SELECT ... FOR UPDATE 悲观锁，并发重复回调串行化，
     *      后到者阻塞至先提交后只能看到 status=SUCCESS 并直接返回。
     * 顺序：先保存订单状态，再保存 txn，避免“txn 已 SUCCESS 但 order 仍 0”的不一致窗口。
     */
    @Transactional
    public void processPaymentSuccess(String transactionNo, String channel, String notifyContent) {
        if (transactionNo == null) return;
        paymentTransactionRepository.findByTransactionNoForUpdate(transactionNo)
                .ifPresent(txn -> {
                    if ("SUCCESS".equals(txn.getStatus())) {
                        return; // Already processed
                    }

                    // 先处理订单（状态 0 → 1），再写 txn，确保同一事务
                    orderRepository.findById(txn.getOrderId())
                            .ifPresent(order -> {
                                if (order.getStatus() == 0) {
                                    order.setStatus(1); // 待发货
                                    order.setPaymentMethod(channel);
                                    orderRepository.save(order);
                                }
                            });

                    txn.setStatus("SUCCESS");
                    txn.setPaidAt(LocalDateTime.now());
                    txn.setNotifyContent(notifyContent);
                    paymentTransactionRepository.save(txn);
                });
    }
}
