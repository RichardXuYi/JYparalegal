package com.jyfc.backend.module.marketing.controller;

import com.jyfc.backend.module.marketing.dto.ActivityDTO;
import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.marketing.entity.Activity;
import com.jyfc.backend.module.marketing.entity.Coupon;
import com.jyfc.backend.module.marketing.entity.Discount;
import com.jyfc.backend.module.marketing.repository.ActivityRepository;
import com.jyfc.backend.module.marketing.repository.CouponRepository;
import com.jyfc.backend.module.marketing.repository.DiscountRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/activities")
public class AdminActivityController {
    private final ActivityRepository activityRepository;
    private final CouponRepository couponRepository;
    private final DiscountRepository discountRepository;

    public AdminActivityController(ActivityRepository activityRepository,
                                   CouponRepository couponRepository,
                                   DiscountRepository discountRepository) {
        this.activityRepository = activityRepository;
        this.couponRepository = couponRepository;
        this.discountRepository = discountRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<Page<Activity>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) Integer status) {
        int pageNum = Math.max(0, page - 1);
        int pageSize = Math.max(1, Math.min(size, 100));
        var sort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("sortOrder"),
                org.springframework.data.domain.Sort.Order.desc("createdAt"));
        var pageable = PageRequest.of(pageNum, pageSize, sort);
        Page<Activity> p = status == null ? activityRepository.findAll(pageable) : activityRepository.findByStatus(status, pageable);
        return ApiResponse.success(p);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Activity> create(@Valid @RequestBody ActivityDTO dto) {
        Activity a = new Activity();
        a.setName(dto.getName());
        a.setDescription(dto.getDescription());
        a.setCoverImage(dto.getCoverImage());
        a.setStartTime(dto.getStartTime());
        a.setEndTime(dto.getEndTime());
        a.setStatus(1); // 默认启用
        if (dto.getSortOrder() != null) a.setSortOrder(dto.getSortOrder());

        if (dto.getCouponIds() != null && !dto.getCouponIds().isEmpty()) {
            List<Coupon> coupons = couponRepository.findAllById(new ArrayList<>(dto.getCouponIds()));
            a.setCoupons(new HashSet<>(coupons));
        }
        if (dto.getDiscountIds() != null && !dto.getDiscountIds().isEmpty()) {
            List<Discount> discounts = discountRepository.findAllById(new ArrayList<>(dto.getDiscountIds()));
            a.setDiscounts(new HashSet<>(discounts));
        }

        return ApiResponse.success(activityRepository.save(a));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Activity> update(@PathVariable Long id, @Valid @RequestBody ActivityDTO dto) {
        return activityRepository.findById(id).map(a -> {
            a.setName(dto.getName());
            a.setDescription(dto.getDescription());
            a.setCoverImage(dto.getCoverImage());
            a.setStartTime(dto.getStartTime());
            a.setEndTime(dto.getEndTime());
            if (dto.getSortOrder() != null) a.setSortOrder(dto.getSortOrder());

            if (dto.getCouponIds() != null) {
                List<Coupon> coupons = couponRepository.findAllById(new ArrayList<>(dto.getCouponIds()));
                a.setCoupons(new HashSet<>(coupons));
            }
            if (dto.getDiscountIds() != null) {
                List<Discount> discounts = discountRepository.findAllById(new ArrayList<>(dto.getDiscountIds()));
                a.setDiscounts(new HashSet<>(discounts));
            }

            return ApiResponse.success(activityRepository.save(a));
        }).orElse(ApiResponse.error(404, "Activity not found"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Activity> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer status = body.get("status") instanceof Integer
                ? (Integer) body.get("status")
                : Integer.valueOf(String.valueOf(body.get("status")));
        if (status != 0 && status != 1) {
            return ApiResponse.error(400, "Status must be 0 (disabled) or 1 (enabled)");
        }
        return activityRepository.findById(id).map(a -> {
            a.setStatus(status);
            return ApiResponse.success(activityRepository.save(a));
        }).orElse(ApiResponse.error(404, "Activity not found"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return activityRepository.findById(id).map(a -> {
            activityRepository.delete(a);
            return ApiResponse.<Void>success("Activity deleted", null);
        }).orElse(ApiResponse.<Void>error(404, "Activity not found"));
    }
}
