package com.jyfc.backend.module.product.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.shared.dto.PageResponse;
import com.jyfc.backend.module.marketing.entity.Activity;
import com.jyfc.backend.module.marketing.entity.Coupon;
import com.jyfc.backend.module.product.entity.Product;
import com.jyfc.backend.module.product.entity.Sku;
import com.jyfc.backend.module.marketing.repository.ActivityRepository;
import com.jyfc.backend.module.product.repository.ProductRepository;
import com.jyfc.backend.module.product.repository.SkuRepository;
import com.jyfc.backend.module.product.dto.PromotionDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/products")
public class ProductPublicController {

    private final ProductRepository productRepository;
    private final ActivityRepository activityRepository;
    private final SkuRepository skuRepository;

    @Autowired
    public ProductPublicController(ProductRepository productRepository, ActivityRepository activityRepository,
                                  SkuRepository skuRepository) {
        this.productRepository = productRepository;
        this.activityRepository = activityRepository;
        this.skuRepository = skuRepository;
    }

    @GetMapping
    public ApiResponse<PageResponse<Product>> listProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String productType) {

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());

        Page<Product> pageResult;
        if (search != null && !search.isBlank()) {
            pageResult = productRepository.findByNameContaining(search, pageable);
        } else if (category != null) {
            pageResult = productRepository.findByCategoryId(category, pageable);
        } else if (productType != null && !productType.isBlank()) {
            pageResult = productRepository.findByProductType(productType, pageable);
        } else {
            pageResult = productRepository.findByStatus(1, pageable); // Only published products for public
        }

        List<Product> products = pageResult.getContent();

        enrichProductsWithPromotions(products);

        PageResponse<Product> pr = new PageResponse<>(products, pageResult.getTotalElements(), page, pageSize);
        return ApiResponse.success(pr);
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> getProduct(@PathVariable Long id) {
        if (id == null) return ApiResponse.error(400, "id is required");
        return productRepository.findById(id)
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .map(product -> {
                    enrichProductsWithPromotions(List.of(product));
                    return ApiResponse.success(product);
                }).orElse(ApiResponse.error(404, "Product not found"));
    }

    /**
     * 公开 SKU 列表：供前端商品详情页选择真实规格。
     * 无 SKU 时返回空数组（前端回退为按商品价格单规格购买）。
     */
    @GetMapping("/{id}/skus")
    public ApiResponse<List<Sku>> getProductSkus(@PathVariable Long id) {
        if (id == null) return ApiResponse.error(400, "id is required");
        Product product = productRepository.findById(id)
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .orElse(null);
        if (product == null) {
            return ApiResponse.error(404, "Product not found");
        }
        return ApiResponse.success(skuRepository.findByProductId(id));
    }

    private void enrichProductsWithPromotions(List<Product> products) {
        if (products.isEmpty()) return;

        List<Activity> activeActivities = activityRepository.findAllActiveWithDetails();
        LocalDateTime now = LocalDateTime.now();

        List<Activity> validActivities = activeActivities.stream()
                .filter(a -> (a.getStartTime() == null || a.getStartTime().isBefore(now))
                        && (a.getEndTime() == null || a.getEndTime().isAfter(now)))
                .collect(Collectors.toList());

        Map<Long, List<PromotionDTO>> productPromoMap = new HashMap<>();

        for (Activity activity : validActivities) {
            Set<Product> productsSet = activity.getProducts();
            if (productsSet == null) continue;
            for (Product p : productsSet) {
                PromotionDTO promo = new PromotionDTO();
                promo.setActivityId(activity.getId() != null ? activity.getId() : 0L);
                promo.setActivityName(activity.getName() != null ? activity.getName() : "");
                Set<Coupon> coupons = activity.getCoupons();
                promo.setCoupons(new ArrayList<>(coupons != null ? coupons : Collections.emptySet()));

                Long productId = p.getId();
                if (productId == null) continue;
                productPromoMap.computeIfAbsent(productId, k -> new ArrayList<>()).add(promo);
            }
        }

        for (Product product : products) {
            Long productId = product.getId();
            product.setPromotions(productId != null ? productPromoMap.getOrDefault(productId, new ArrayList<>()) : new ArrayList<>());
        }
    }
}
