package com.jyfc.backend.module.trade.controller;

import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.module.trade.entity.Refund;
import com.jyfc.backend.module.trade.service.OrderRefundService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 订单退款（用户端 + 管理端）
 */
@RestController
@RequestMapping("/api/app/orders")
public class OrderRefundController {

    private final OrderRefundService refundService;
    private final UserContextUtil userContextUtil;

    public OrderRefundController(OrderRefundService refundService, UserContextUtil userContextUtil) {
        this.refundService = refundService;
        this.userContextUtil = userContextUtil;
    }

    /**
     * 申请退款（用户）
     * POST /api/app/orders/{id}/refund
     */
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Refund> applyRefund(@PathVariable("id") Long orderId,
                                           @RequestBody(required = false) Map<String, Object> body) {
        Long userId = userContextUtil.getCurrentUserId();
        BigDecimal amount = null;
        String reason = null;
        if (body != null) {
            Object amountObj = body.get("amount");
            if (amountObj != null) {
                try { amount = new BigDecimal(String.valueOf(amountObj)); } catch (NumberFormatException ignored) {}
            }
            Object reasonObj = body.get("reason");
            if (reasonObj != null) {
                reason = String.valueOf(reasonObj);
            }
        }
        Refund refund = refundService.applyRefund(orderId, userId, amount, reason);
        return ApiResponse.success("退款申请已提交", refund);
    }

    /**
     * 订单的退款记录（用户 / 管理员）
     */
    @GetMapping("/{id}/refunds")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<List<Refund>> listRefunds(@PathVariable("id") Long orderId) {
        Long userId = userContextUtil.getCurrentUserId();
        boolean isAdmin = userContextUtil.isAdmin();
        List<Refund> refunds = refundService.listByOrder(orderId);
        if (!isAdmin) {
            refunds.removeIf(r -> !userId.equals(r.getUserId()));
        }
        return ApiResponse.success(refunds);
    }

    /**
     * 我的退款记录
     */
    @GetMapping("/refunds/mine")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<List<Refund>> myRefunds() {
        Long userId = userContextUtil.getCurrentUserId();
        return ApiResponse.success(refundService.listByUser(userId));
    }

    /**
     * 管理员审核：通过 -> 退款中
     */
    @PostMapping("/refunds/{refundId}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EMPLOYEE')")
    public ApiResponse<Refund> approve(@PathVariable Long refundId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        String remark = body != null && body.get("remark") != null ? String.valueOf(body.get("remark")) : null;
        return ApiResponse.success("已通过", refundService.approveRefund(refundId, remark));
    }

    /**
     * 管理员标记完成
     */
    @PostMapping("/refunds/{refundId}/complete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EMPLOYEE')")
    public ApiResponse<Refund> complete(@PathVariable Long refundId) {
        return ApiResponse.success("退款已完成", refundService.completeRefund(refundId));
    }

    /**
     * 管理员拒绝
     */
    @PostMapping("/refunds/{refundId}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EMPLOYEE')")
    public ApiResponse<Refund> reject(@PathVariable Long refundId,
                                      @RequestBody(required = false) Map<String, Object> body) {
        String remark = body != null && body.get("remark") != null ? String.valueOf(body.get("remark")) : null;
        return ApiResponse.success("已拒绝", refundService.rejectRefund(refundId, remark));
    }
}