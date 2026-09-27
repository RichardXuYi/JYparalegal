package com.jyfc.backend.module.trade.controller;

import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.module.trade.entity.OrderEntity;
import com.jyfc.backend.module.trade.entity.PaymentTransaction;
import com.jyfc.backend.module.trade.payment.PaymentCallbackResult;
import com.jyfc.backend.module.trade.payment.PaymentGateway;
import com.jyfc.backend.module.trade.payment.PaymentGatewayResolver;
import com.jyfc.backend.module.trade.payment.PrepayResult;
import com.jyfc.backend.module.trade.repository.OrderRepository;
import com.jyfc.backend.module.trade.repository.PaymentTransactionRepository;
import com.jyfc.backend.module.trade.dto.PaymentRequest;
import com.jyfc.backend.module.trade.dto.SimulateConfirmRequest;
import com.jyfc.backend.module.trade.service.PaymentService;
import com.jyfc.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Tag(name = "订单支付", description = "订单支付、微信回调、支付宝通知")
@RestController
@RequestMapping("/api/app/pay")
public class OrderPaymentController {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentController.class);

    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserContextUtil userContextUtil;
    private final PaymentService paymentService;
    private final PaymentGatewayResolver gatewayResolver;

    public OrderPaymentController(OrderRepository orderRepository,
                                  PaymentTransactionRepository paymentTransactionRepository,
                                  UserContextUtil userContextUtil,
                                  PaymentService paymentService,
                                  PaymentGatewayResolver gatewayResolver) {
        this.orderRepository = orderRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.userContextUtil = userContextUtil;
        this.paymentService = paymentService;
        this.gatewayResolver = gatewayResolver;
    }

    /**
     * 发起支付
     * POST /api/app/pay
     */
    @PostMapping
    @Operation(summary = "发起支付", description = "根据订单ID和支付方式发起支付，委托支付网关生成预下单参数")
    public ApiResponse<Map<String, Object>> pay(@Valid @RequestBody PaymentRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }

        Long orderId = request.getOrderId();
        String paymentMethod = request.getPaymentMethod();

        if (orderId == null) {
            return ApiResponse.error(400, "订单ID不能为空");
        }

        OrderEntity order = orderRepository.findById(orderId)
                .orElse(null);
        if (order == null) {
            return ApiResponse.error(404, "订单不存在");
        }
        if (!userId.equals(order.getUserId())) {
            return ApiResponse.error(403, "无权操作此订单");
        }
        if (order.getStatus() != 0) {
            return ApiResponse.error(400, "订单状态不允许支付");
        }

        // Create payment transaction
        PaymentTransaction txn = new PaymentTransaction();
        txn.setOrderId(orderId);
        txn.setOrderNo(order.getOrderNo());
        txn.setTransactionNo(generateTransactionNo());
        txn.setPaymentMethod(paymentMethod);
        txn.setAmount(order.getPayAmount());
        txn.setStatus("PENDING");
        txn.setUserId(userId);
        txn = paymentTransactionRepository.save(txn);

        // 委托支付网关生成预下单参数（模拟/微信/支付宝由 payment.provider 配置决定）
        PaymentGateway gateway = gatewayResolver.resolveForPrepay(paymentMethod);
        PrepayResult prepay = gateway.createPrepay(txn);

        Map<String, Object> result = new HashMap<>();
        result.put("transactionId", txn.getId());
        result.put("transactionNo", txn.getTransactionNo());
        result.put("provider", prepay.getProvider());
        result.put("paymentMethod", prepay.getPaymentMethod());
        result.put("amount", prepay.getAmount());
        result.put("payParams", prepay.getPayParams());
        result.put("confirmPath", prepay.getConfirmPath());

        return ApiResponse.success(result);
    }

    /**
     * 模拟支付确认（仅模拟通道开放）
     * POST /api/app/pay/simulate/confirm
     */
    @PostMapping("/simulate/confirm")
    @Operation(summary = "模拟支付确认", description = "模拟通道下由前端主动确认支付成功，触发订单状态流转")
    public ApiResponse<Map<String, Object>> simulateConfirm(@Valid @RequestBody SimulateConfirmRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        if (!gatewayResolver.isSimulated()) {
            return ApiResponse.error(400, "当前支付通道不支持模拟确认");
        }

        String transactionNo = request.getTransactionNo();

        PaymentTransaction txn = paymentTransactionRepository.findByTransactionNo(transactionNo).orElse(null);
        if (txn == null) {
            return ApiResponse.error(404, "支付流水不存在");
        }
        if (!userId.equals(txn.getUserId())) {
            return ApiResponse.error(403, "无权操作此支付流水");
        }

        paymentService.processPaymentSuccess(transactionNo, txn.getPaymentMethod(), "SIMULATED_CONFIRM");

        OrderEntity order = orderRepository.findById(txn.getOrderId()).orElse(null);
        Map<String, Object> result = new HashMap<>();
        result.put("transactionNo", transactionNo);
        result.put("orderId", txn.getOrderId());
        result.put("orderStatus", order != null ? order.getStatus() : null);
        result.put("paid", true);
        return ApiResponse.success(result);
    }

    /**
     * 微信支付回调
     * POST /api/app/pay/wechat/callback
     */
    @PostMapping("/wechat/callback")
    @Operation(summary = "微信支付回调", description = "微信支付异步通知回调，解析验签委托支付网关")
    public Map<String, String> wechatCallback(@RequestBody(required = false) String xmlBody) {
        log.info("WeChat pay callback received");

        Map<String, String> response = new HashMap<>();
        response.put("return_code", "SUCCESS");
        response.put("return_msg", "OK");

        try {
            PaymentGateway gateway = gatewayResolver.resolveByProvider("wechat");
            if (gateway == null) {
                response.put("return_code", "FAIL");
                response.put("return_msg", "wechat gateway not configured");
                return response;
            }
            PaymentCallbackResult cb = gateway.parseCallback(xmlBody, new HashMap<>());
            if (cb.isVerified()) {
                paymentService.processPaymentSuccess(cb.getTransactionNo(), "wechat", xmlBody);
                if (cb.getChannelTradeNo() != null) {
                    recordChannelTradeNo(cb.getTransactionNo(), cb.getChannelTradeNo());
                }
            } else {
                response.put("return_code", "FAIL");
                response.put("return_msg", "verify failed");
            }
        } catch (Exception e) {
            log.error("Error processing wechat callback", e);
            response.put("return_code", "FAIL");
            response.put("return_msg", e.getMessage());
        }

        return response;
    }

    /**
     * 支付宝异步通知
     * POST /api/app/pay/alipay/notify
     */
    @PostMapping("/alipay/notify")
    @Operation(summary = "支付宝异步通知", description = "支付宝支付异步通知回调，解析验签委托支付网关")
    public String alipayNotify(@RequestParam(required = false) Map<String, String> params) {
        log.info("Alipay notify received");

        try {
            PaymentGateway gateway = gatewayResolver.resolveByProvider("alipay");
            if (gateway == null) {
                return "fail";
            }
            PaymentCallbackResult cb = gateway.parseCallback(null, params != null ? params : new HashMap<>());
            if (!cb.isVerified()) {
                return "fail";
            }
            paymentService.processPaymentSuccess(cb.getTransactionNo(), "alipay", params != null ? params.toString() : null);
            if (cb.getChannelTradeNo() != null) {
                recordChannelTradeNo(cb.getTransactionNo(), cb.getChannelTradeNo());
            }
            return "success";
        } catch (Exception e) {
            log.error("Error processing alipay notify", e);
            return "fail";
        }
    }

    /**
     * 查询支付状态
     * GET /api/app/pay/status/{orderId}
     */
    @GetMapping("/status/{orderId}")
    @Operation(summary = "查询支付状态", description = "根据订单ID查询支付状态")
    public ApiResponse<Map<String, Object>> getPaymentStatus(@PathVariable Long orderId) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !userId.equals(order.getUserId())) {
            return ApiResponse.error(404, "订单不存在");
        }

        var transactions = paymentTransactionRepository.findByOrderId(orderId);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("orderStatus", order.getStatus());
        result.put("payAmount", order.getPayAmount());
        result.put("transactions", transactions.stream().map(t -> {
            Map<String, Object> txnMap = new HashMap<>();
            txnMap.put("id", t.getId());
            txnMap.put("transactionNo", t.getTransactionNo());
            txnMap.put("paymentMethod", t.getPaymentMethod());
            txnMap.put("amount", t.getAmount());
            txnMap.put("status", t.getStatus());
            txnMap.put("paidAt", t.getPaidAt());
            return txnMap;
        }).toList());

        return ApiResponse.success(result);
    }

    private void recordChannelTradeNo(String transactionNo, String channelTradeNo) {
        paymentTransactionRepository.findByTransactionNo(transactionNo)
                .ifPresent(txn -> {
                    txn.setChannelTradeNo(channelTradeNo);
                    paymentTransactionRepository.save(txn);
                });
    }

    private Long getCurrentUserId() {
        try {
            return userContextUtil.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private String generateTransactionNo() {
        return "PAY" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
