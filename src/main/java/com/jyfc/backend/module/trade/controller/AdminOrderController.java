package com.jyfc.backend.module.trade.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.UserRepository;
import com.jyfc.backend.module.trade.dto.OrderResponseDTO;
import com.jyfc.backend.module.trade.entity.OrderEntity;
import com.jyfc.backend.module.trade.entity.OrderItem;
import com.jyfc.backend.module.trade.repository.OrderRepository;
import com.jyfc.backend.module.trade.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "管理端订单", description = "管理员订单查看、审核、管理API")
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private static final Map<Integer, Set<Integer>> VALID_TRANSITIONS = Map.of(
            0, Set.of(1, 4),  // 待支付 → 待发货/已取消
            1, Set.of(2),      // 待发货 → 已发货
            2, Set.of(3),      // 已发货 → 已完成
            3, Set.of(),       // 已完成 终态
            4, Set.of()        // 已取消 终态
    );

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public AdminOrderController(OrderService orderService, OrderRepository orderRepository,
                                UserRepository userRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<List<OrderResponseDTO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long userId) {

        Page<OrderEntity> p = orderService.listOrders(page, size, status, userId);
        List<OrderResponseDTO> dtos = p.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ApiResponse.success(dtos);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> getDetail(@PathVariable long id) {
        return orderRepository.findById(id).map(order -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("order", toDto(order));
                    Long orderId = order.getId();
                    result.put("items", orderId != null ? orderService.getOrderItems(orderId) : List.of());
                    return ApiResponse.success(result);
                })
                .orElse(ApiResponse.error(404, "Order not found"));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<OrderResponseDTO> updateStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Object statusObj = body.get("status");
        if (statusObj == null) {
            return ApiResponse.error(400, "status is required");
        }
        Integer status = statusObj instanceof Integer
                ? (Integer) statusObj
                : Integer.valueOf(String.valueOf(statusObj));
        return orderRepository.findById(id).map(o -> {
            Set<Integer> allowed = VALID_TRANSITIONS.getOrDefault(o.getStatus(), Set.of());
            if (!allowed.contains(status)) {
                return ApiResponse.<OrderResponseDTO>error(400,
                        "Invalid status transition: " + o.getStatus() + " → " + status);
            }
            o.setStatus(status);
            if (status == 4) {
                orderService.restoreStockForOrder(o.getId());
            }
            OrderEntity saved = orderRepository.save(o);
            return ApiResponse.success("Order status updated successfully", toDto(saved));
        }).orElse(ApiResponse.error(404, "Order not found"));
    }

    /** Convert OrderEntity to OrderResponseDTO, enriching with username, avatar & item summary. */
    private OrderResponseDTO toDto(OrderEntity entity) {
        String username = null;
        String avatar = null;
        if (entity.getUserId() != null) {
            UserEntity user = userRepository.findById(entity.getUserId()).orElse(null);
            if (user != null) {
                username = user.getUsername();
                avatar = user.getAvatar();
            }
        }
        OrderResponseDTO dto = new OrderResponseDTO(entity, username, avatar);
        Long entityId = entity.getId();
        List<OrderItem> items = entityId != null ? orderService.getOrderItems(entityId) : List.of();
        dto.setItemCount(items.size());
        if (!items.isEmpty()) {
            dto.setProductName(items.get(0).getProductName());
        }
        return dto;
    }
}
