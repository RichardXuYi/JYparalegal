package com.jyfc.backend.module.mall.controller;

import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.module.mall.entity.CartItem;
import com.jyfc.backend.module.mall.service.CartService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商城购物车（用户端）
 */
@RestController
@RequestMapping("/api/app/cart")
public class CartController {

    private final CartService cartService;
    private final UserContextUtil userContextUtil;

    public CartController(CartService cartService, UserContextUtil userContextUtil) {
        this.cartService = cartService;
        this.userContextUtil = userContextUtil;
    }

    /**
     * 加购 POST /api/app/cart
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<CartItem> add(@RequestBody Map<String, Object> body) {
        Long userId = userContextUtil.getCurrentUserId();
        if (body == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        Long productId = parseLong(body.get("productId"));
        if (productId == null) {
            return ApiResponse.error(400, "productId 不能为空");
        }
        Long skuId = parseLong(body.get("skuId"));
        Integer quantity = parseInt(body.get("quantity"));
        if (quantity == null) {
            quantity = 1;
        }
        CartItem item = cartService.addToCart(userId, productId, skuId, quantity);
        return ApiResponse.success("已加入购物车", item);
    }

    /**
     * 购物车列表 GET /api/app/cart
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Map<String, Object>> list() {
        Long userId = userContextUtil.getCurrentUserId();
        List<CartItem> items = cartService.list(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("total", items.size());
        result.put("items", items);
        return ApiResponse.success(result);
    }

    /**
     * 更新数量 PUT /api/app/cart/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<CartItem> update(@PathVariable("id") Long cartId,
                                        @RequestBody Map<String, Object> body) {
        Long userId = userContextUtil.getCurrentUserId();
        if (body == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        Integer quantity = parseInt(body.get("quantity"));
        Boolean selected = body.get("selected") != null ? Boolean.valueOf(String.valueOf(body.get("selected"))) : null;

        CartItem updated;
        if (quantity != null) {
            updated = cartService.updateQuantity(userId, cartId, quantity);
        } else {
            updated = cartService.list(userId).stream().filter(c -> c.getId().equals(cartId)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("购物车项不存在"));
        }
        if (selected != null) {
            updated = cartService.toggleSelected(userId, cartId, selected);
        }
        return ApiResponse.success("已更新", updated);
    }

    /**
     * 取消加购（单条） DELETE /api/app/cart/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Void> remove(@PathVariable("id") Long cartId) {
        Long userId = userContextUtil.getCurrentUserId();
        cartService.remove(userId, cartId);
        return ApiResponse.success("已删除", null);
    }

    /**
     * 清空购物车 DELETE /api/app/cart
     */
    @DeleteMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Map<String, Object>> clear() {
        Long userId = userContextUtil.getCurrentUserId();
        long count = cartService.clear(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("removed", count);
        return ApiResponse.success("购物车已清空", result);
    }

    private Long parseLong(Object v) {
        if (v == null) return null;
        try { return Long.valueOf(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseInt(Object v) {
        if (v == null) return null;
        try { return Integer.valueOf(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }
}