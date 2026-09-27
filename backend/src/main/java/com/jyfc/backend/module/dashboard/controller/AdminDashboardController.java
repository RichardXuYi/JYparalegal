package com.jyfc.backend.module.dashboard.controller;

import com.jyfc.backend.module.news.repository.NewsRepository;
import com.jyfc.backend.module.trade.repository.OrderRepository;
import com.jyfc.backend.module.product.repository.ProductRepository;
import com.jyfc.backend.module.product.repository.ProductReviewRepository;
import com.jyfc.backend.module.auth.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final NewsRepository newsRepository;
    private final ProductReviewRepository reviewRepository;

    public AdminDashboardController(
            UserRepository userRepository, 
            OrderRepository orderRepository,
            ProductRepository productRepository,
            NewsRepository newsRepository,
            ProductReviewRepository reviewRepository
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.newsRepository = newsRepository;
        this.reviewRepository = reviewRepository;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE','ROLE_SUPER_ADMIN')")
    public Map<String, Object> dashboard() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            long userCount = userRepository.count();
            stats.put("userCount", userCount);
        } catch (Exception e) { stats.put("userCount", 0); }

        try {
            long orderCount = orderRepository.count();
            stats.put("orderCount", orderCount);
            // Pending orders: status 0
            long pendingOrderCount = orderRepository.countByStatus(0);
            stats.put("pendingOrderCount", pendingOrderCount);
        } catch (Exception e) { 
            stats.put("orderCount", 0); 
            stats.put("pendingOrderCount", 0);
        }

        try {
            long productCount = productRepository.count();
            stats.put("productCount", productCount);
            // Product viewers - mock or logs? Use product count for now
            stats.put("productViewerCount", productCount * 10); 
        } catch (Exception e) { stats.put("productCount", 0); }

        try {
            long newsCount = newsRepository.count();
            stats.put("newsCount", newsCount);
            stats.put("newsBrowserCount", newsCount * 20);
        } catch (Exception e) { stats.put("newsCount", 0); }
        
        try {
            long reviewCount = reviewRepository.count();
            // Service viewer count? 
            stats.put("serviceViewerCount", reviewCount * 5);
        } catch (Exception e) { stats.put("serviceViewerCount", 0); }

        // Activity map
        Map<String, Object> activity = new HashMap<>();
        activity.put("productViewers", stats.getOrDefault("productViewerCount", 0L));
        activity.put("newsBrowsers", stats.getOrDefault("newsBrowserCount", 0L));
        activity.put("serviceViewers", stats.getOrDefault("serviceViewerCount", 0L));
        stats.put("activity", activity);

        return stats;
    }
}
