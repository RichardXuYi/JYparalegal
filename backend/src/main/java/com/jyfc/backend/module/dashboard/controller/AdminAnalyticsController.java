package com.jyfc.backend.module.dashboard.controller;

import com.jyfc.backend.module.product.entity.ProductCategory;
import com.jyfc.backend.module.trade.repository.OrderRepository;
import com.jyfc.backend.module.product.repository.ProductCategoryRepository;
import com.jyfc.backend.module.product.repository.ProductRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;

    public AdminAnalyticsController(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/sales")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE','ROLE_SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSalesStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime end
    ) {
        // Simplified stats
        if (start == null) start = LocalDateTime.now().minusDays(30);
        if (end == null) end = LocalDateTime.now();

        long totalOrders = orderRepository.countByCreatedAtBetween(start, end);
        // long totalSales = orderRepository.sumTotalAmountByCreatedAtBetween(start, end); // 需要添加到仓库接口
        
        return ResponseEntity.ok(Map.of(
            "totalOrders", totalOrders,
            "period", start.toLocalDate() + " to " + end.toLocalDate()
        ));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE','ROLE_SUPER_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getCategoryStats() {
        List<ProductCategory> categories = categoryRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (ProductCategory cat : categories) {
            Long catId = cat.getId();
            if (catId == null) continue;
            long count = productRepository.countByCategoryId(catId);
            if (count > 0) {
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("name", cat.getName() != null ? cat.getName() : "");
                item.put("value", count);
                result.add(item);
            }
        }
        return ResponseEntity.ok(result);
    }
}
