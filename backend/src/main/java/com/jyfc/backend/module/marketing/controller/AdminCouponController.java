package com.jyfc.backend.module.marketing.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.auth.dto.CouponDTO;
import com.jyfc.backend.module.marketing.entity.Coupon;
import com.jyfc.backend.module.marketing.repository.CouponRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/coupons")
public class AdminCouponController {
    private final CouponRepository couponRepository;

    public AdminCouponController(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<Page<Coupon>> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size,
                                          @RequestParam(required = false) Integer status) {
        int pageNum = Math.max(0, page - 1);
        int pageSize = Math.max(1, Math.min(size, 100));
        var pageable = PageRequest.of(pageNum, pageSize);
        Page<Coupon> p = status == null ? couponRepository.findAll(pageable) : couponRepository.findByStatus(status, pageable);
        return ApiResponse.success(p);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Coupon> create(@Valid @RequestBody CouponDTO dto) {
        if (couponRepository.existsByCode(dto.getCode())) {
            return ApiResponse.error(400, "Coupon code already exists");
        }
        Coupon c = new Coupon();
        c.setCode(dto.getCode());
        c.setName(dto.getName());
        c.setType(dto.getType());
        c.setValue(dto.getValue());
        c.setMinSpend(dto.getMinSpend());
        c.setStartTime(dto.getStartTime());
        c.setEndTime(dto.getEndTime());
        c.setStatus(1); // 默认启用
        return ApiResponse.success(couponRepository.save(c));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Coupon> update(@PathVariable Long id, @Valid @RequestBody CouponDTO dto) {
        return couponRepository.findById(id).map(c -> {
            c.setName(dto.getName());
            c.setType(dto.getType());
            c.setValue(dto.getValue());
            c.setMinSpend(dto.getMinSpend());
            c.setStartTime(dto.getStartTime());
            c.setEndTime(dto.getEndTime());
            return ApiResponse.success("Coupon updated successfully", couponRepository.save(c));
        }).orElse(ApiResponse.error(404, "Coupon not found"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return couponRepository.findById(id).map(c -> {
            couponRepository.delete(c);
            return ApiResponse.success("Coupon deleted successfully", (Void)null);
        }).orElse(ApiResponse.error(404, "Coupon not found"));
    }
}
