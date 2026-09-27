package com.jyfc.backend.module.trade.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.trade.dto.OrderResponseDTO;
import com.jyfc.backend.module.trade.entity.OrderEntity;
import com.jyfc.backend.module.trade.entity.OrderItem;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.product.repository.SkuRepository;
import com.jyfc.backend.module.trade.repository.OrderRepository;
import com.jyfc.backend.module.trade.repository.OrderItemRepository;
import com.jyfc.backend.module.auth.repository.UserRepository;
import com.jyfc.backend.module.trade.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "订单管理", description = "用户订单创建、查询、管理API")
@RestController
@RequestMapping("/api/app/orders")
public class OrderPublicController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SkuRepository skuRepository;
    private final UserRepository userRepository;

    @Autowired
    public OrderPublicController(OrderService orderService, OrderRepository orderRepository,
                                  OrderItemRepository orderItemRepository,
                                  SkuRepository skuRepository,
                                  UserRepository userRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.skuRepository = skuRepository;
        this.userRepository = userRepository;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Unauthorized");
        }
        String username = auth.getName();
        Long userId = userRepository.findByUsername(username)
                .map(UserEntity::getId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        if (userId == null) {
            throw new RuntimeException("User ID is null");
        }
        return userId;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<List<OrderResponseDTO>> listMyOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        Long userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<OrderEntity> pageResult = orderRepository.findByUserId(userId, pageable);

        List<OrderResponseDTO> dtos = pageResult.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ApiResponse.success(dtos);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @Transactional
    public ApiResponse<OrderResponseDTO> createOrder(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();

        OrderEntity order = orderService.createOrder(userId, body);

        return ApiResponse.success("Order created successfully", toDto(order));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<OrderResponseDTO> getOrder(@PathVariable Long id) {
        Long userId = getCurrentUserId();

        return orderRepository.findById(id)
                .filter(order -> userId.equals(order.getUserId()))
                .map(order -> ApiResponse.success(toDto(order)))
                .orElse(ApiResponse.error(404, "Order not found"));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @Transactional
    public ApiResponse<OrderEntity> cancelOrder(@PathVariable Long id) {
        Long userId = getCurrentUserId();

        return orderRepository.findById(id)
                .filter(order -> userId.equals(order.getUserId()))
                .map(order -> {
                    if (order.getStatus() != 0) {
                        return ApiResponse.<OrderEntity>error(400, "Only pending payment orders can be cancelled");
                    }
                    // F1-4: 取消订单前恢复 SKU 库存（按订单实际 Item 数量逐条还原）
                    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
                    for (OrderItem it : items) {
                        if (it.getSkuId() != null && it.getQuantity() != null && it.getQuantity() > 0) {
                            skuRepository.restoreStockAtomic(it.getSkuId(), it.getQuantity());
                        }
                    }
                    order.setStatus(4); // 已取消
                    orderRepository.save(order);
                    return ApiResponse.success("Order cancelled successfully", order);
                })
                .orElse(ApiResponse.error(404, "Order not found"));
    }

    /**
     * 将 OrderEntity 转换为扁平的 OrderResponseDTO，并补充商品名称/件数摘要，
     * 供 app/web 端订单列表、详情与支付页展示（与管理端保持一致的响应结构）。
     */
    private OrderResponseDTO toDto(OrderEntity entity) {
        OrderResponseDTO dto = new OrderResponseDTO(entity, null, null);
        List<OrderItem> items = orderService.getOrderItems(entity.getId());
        dto.setItemCount(items.size());
        if (!items.isEmpty()) {
            dto.setProductName(items.get(0).getProductName());
        }
        return dto;
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @Transactional
    public ApiResponse<OrderEntity> confirmOrder(@PathVariable Long id) {
        Long userId = getCurrentUserId();

        return orderRepository.findById(id)
                .filter(order -> userId.equals(order.getUserId()))
                .map(order -> {
                    if (order.getStatus() != 2) {
                        return ApiResponse.<OrderEntity>error(400, "Only shipped orders can be confirmed");
                    }
                    order.setStatus(3); // 已完成
                    orderRepository.save(order);
                    return ApiResponse.success("Order confirmed successfully", order);
                })
                .orElse(ApiResponse.error(404, "Order not found"));
    }
}
