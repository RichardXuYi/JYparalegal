package com.jyfc.backend.module.trade.service;

import com.jyfc.backend.module.trade.entity.OrderEntity;
import com.jyfc.backend.module.trade.entity.OrderItem;
import com.jyfc.backend.module.trade.entity.PaymentTransaction;
import com.jyfc.backend.module.trade.entity.Refund;
import com.jyfc.backend.module.product.repository.SkuRepository;
import com.jyfc.backend.module.trade.repository.OrderItemRepository;
import com.jyfc.backend.module.trade.repository.OrderRepository;
import com.jyfc.backend.module.trade.repository.PaymentTransactionRepository;
import com.jyfc.backend.module.trade.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 订单退款服务（独立于 OrderService，避免互相改动冲突）。
 * <p>订单状态机：1-待发货 / 2-待收货 / 3-已完成 可发起退款；</p>
 * <p>退款中：订单 status=5；退款完成：订单 status=6。</p>
 */
@Service
public class OrderRefundService {

    /** 可发起退款的订单状态 */
    private static final List<Integer> REFUNDABLE_STATUSES = Arrays.asList(1, 2, 3);
    /** 退款中订单状态 */
    public static final int ORDER_STATUS_REFUNDING = 5;
    /** 已退款订单状态 */
    public static final int ORDER_STATUS_REFUNDED = 6;

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SkuRepository skuRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public OrderRefundService(RefundRepository refundRepository, OrderRepository orderRepository,
                              OrderItemRepository orderItemRepository, SkuRepository skuRepository,
                              PaymentTransactionRepository paymentTransactionRepository) {
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.skuRepository = skuRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    /**
     * 申请退款（用户端）
     */
    @Transactional
    public Refund applyRefund(Long orderId, Long userId, BigDecimal amount, String reason) {
        if (orderId == null) throw new IllegalArgumentException("orderId must not be null");
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("订单不存在"));
        if (!userId.equals(order.getUserId())) {
            throw new IllegalStateException("无权操作此订单");
        }
        Integer status = order.getStatus();
        if (status == null || !REFUNDABLE_STATUSES.contains(status)) {
            throw new IllegalStateException("当前订单状态不允许退款");
        }
        // 已存在进行中的退款单（0/1）则拒绝重复申请
        long activeRefunds = refundRepository.countByOrderIdAndStatusIn(orderId,
                Arrays.asList(Refund.STATUS_PENDING, Refund.STATUS_REFUNDING));
        if (activeRefunds > 0) {
            throw new IllegalStateException("已有进行中的退款申请");
        }

        BigDecimal refundAmount = amount;
        if (refundAmount == null) {
            refundAmount = order.getPayAmount();
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("退款金额必须大于 0");
        }
        if (order.getPayAmount() != null && refundAmount.compareTo(order.getPayAmount()) > 0) {
            throw new IllegalArgumentException("退款金额不能超过实付金额");
        }

        Refund refund = new Refund();
        refund.setRefundNo(generateRefundNo());
        refund.setOrderId(orderId);
        refund.setOrderNo(order.getOrderNo());
        refund.setUserId(userId);
        refund.setAmount(refundAmount);
        refund.setReason(reason);
        refund.setStatus(Refund.STATUS_PENDING);
        return refundRepository.save(refund);
    }

    /**
     * 管理员审核通过 -> 退款中（更新订单状态为 5）
     */
    @Transactional
    public Refund approveRefund(Long refundId, String adminRemark) {
        if (refundId == null) throw new IllegalArgumentException("refundId must not be null");
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalStateException("退款单不存在"));
        if (refund.getStatus() != Refund.STATUS_PENDING) {
            throw new IllegalStateException("当前退款单状态不可审核");
        }
        refund.setStatus(Refund.STATUS_REFUNDING);
        refund.setAdminRemark(adminRemark);
        refund.setProcessedAt(LocalDateTime.now());
        refund = refundRepository.save(refund);

        // 订单状态置为"退款中"
        orderRepository.findById(refund.getOrderId()).ifPresent(order -> {
            order.setStatus(ORDER_STATUS_REFUNDING);
            orderRepository.save(order);
        });
        return refund;
    }

    /**
     * 完成退款（管理员/系统回调）
     */
    @Transactional
    public Refund completeRefund(Long refundId) {
        if (refundId == null) throw new IllegalArgumentException("refundId must not be null");
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalStateException("退款单不存在"));
        if (refund.getStatus() != Refund.STATUS_REFUNDING) {
            throw new IllegalStateException("退款单当前状态不可完成");
        }
        refund.setStatus(Refund.STATUS_SUCCESS);
        refund.setProcessedAt(LocalDateTime.now());
        refund = refundRepository.save(refund);

        // 订单状态置为"已退款"，并恢复 SKU 库存
        orderRepository.findById(refund.getOrderId()).ifPresent(order -> {
            order.setStatus(ORDER_STATUS_REFUNDED);
            orderRepository.save(order);
            // F1-4: 退款成功后按订单明细恢复库存
            Long orderId = order.getId();
            List<OrderItem> items = orderId != null ? orderItemRepository.findByOrderId(orderId) : List.of();
            for (OrderItem it : items) {
                if (it.getSkuId() != null && it.getQuantity() != null && it.getQuantity() > 0) {
                    skuRepository.restoreStockAtomic(it.getSkuId(), it.getQuantity());
                }
            }
        });

        // F2: 同步把订单对应的支付流水置为 REFUNDED，保持订单/流水状态一致
        List<PaymentTransaction> txns = paymentTransactionRepository.findByOrderId(refund.getOrderId());
        for (PaymentTransaction txn : txns) {
            if (!"REFUNDED".equals(txn.getStatus())) {
                txn.setStatus("REFUNDED");
                paymentTransactionRepository.save(txn);
            }
        }
        return refund;
    }

    /**
     * 拒绝退款
     * <p>F2: 拒绝时若订单处于"退款中"（5），按 PaymentTransaction 推断原订单状态并恢复。</p>
     * <p>推断规则：有 SUCCESS 流水 → 视为已支付，恢复为 1（待发货）；否则恢复为 0（待付款）。</p>
     */
    @Transactional
    public Refund rejectRefund(Long refundId, String adminRemark) {
        if (refundId == null) throw new IllegalArgumentException("refundId must not be null");
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalStateException("退款单不存在"));
        if (refund.getStatus() != Refund.STATUS_PENDING) {
            throw new IllegalStateException("退款单当前状态不可拒绝");
        }
        refund.setStatus(Refund.STATUS_REJECTED);
        refund.setAdminRemark(adminRemark);
        refund.setProcessedAt(LocalDateTime.now());
        refund = refundRepository.save(refund);

        // F2: 仅在订单被错误置为 REFUNDING(5) 时，按 PaymentTransaction 推断原状态并还原
        orderRepository.findById(refund.getOrderId()).ifPresent(order -> {
            if (order.getStatus() != null && order.getStatus() == ORDER_STATUS_REFUNDING) {
                Long oid = order.getId();
                List<PaymentTransaction> txns = oid != null ? paymentTransactionRepository.findByOrderId(oid) : List.of();
                boolean hasSuccess = false;
                for (PaymentTransaction t : txns) {
                    if ("SUCCESS".equals(t.getStatus())) {
                        hasSuccess = true;
                        break;
                    }
                }
                int original = hasSuccess ? 1 : 0;
                order.setStatus(original);
                orderRepository.save(order);
            }
        });
        return refund;
    }

    public List<Refund> listByOrder(Long orderId) {
        if (orderId == null) return List.of();
        return refundRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    public List<Refund> listByUser(Long userId) {
        if (userId == null) return List.of();
        return refundRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Refund> listByStatus(Integer status) {
        if (status == null) return List.of();
        return refundRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    private String generateRefundNo() {
        // 使用时间戳 + 8 位 UUID 截断，降低碰撞概率（16^8 ≈ 43 亿空间）
        return "RF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}