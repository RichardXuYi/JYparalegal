package com.jyfc.backend.module.auth.controller;

import com.jyfc.backend.module.auth.dto.CouponDTO;
import com.jyfc.backend.module.auth.dto.UserCouponDTO;
import com.jyfc.backend.module.marketing.entity.Coupon;
import com.jyfc.backend.module.auth.entity.UserCoupon;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.marketing.repository.CouponRepository;
import com.jyfc.backend.module.auth.repository.UserCouponRepository;
import com.jyfc.backend.module.auth.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/app/coupons")
public class UserCouponController {

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final UserService userService;

    @Autowired
    public UserCouponController(UserCouponRepository userCouponRepository,
                                CouponRepository couponRepository,
                                UserService userService) {
        this.userCouponRepository = userCouponRepository;
        this.couponRepository = couponRepository;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<Map<String, Object>> listMyCoupons(
            Authentication authentication,
            @RequestParam(required = false) Integer status) {
        
        UserEntity user = getCurrentUser(authentication);
        if (user == null || user.getId() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        List<UserCoupon> coupons;
        if (status != null) {
            coupons = userCouponRepository.findByUserIdAndStatus(user.getId(), status);
        } else {
            coupons = userCouponRepository.findByUserId(user.getId());
        }

        List<UserCouponDTO> dtos = coupons.stream().map(this::convertToDTO).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("data", dtos));
    }

    @PostMapping("/claim/{couponId}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<Map<String, Object>> claimCoupon(
            Authentication authentication,
            @PathVariable Long couponId) {
        
        UserEntity user = getCurrentUser(authentication);
        if (user == null || user.getId() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        if (couponId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "优惠券ID不能为空"));
        }
        Optional<Coupon> couponOpt = couponRepository.findById(couponId);
        if (couponOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "优惠券不存在"));
        }
        Coupon coupon = couponOpt.get();

        // 验证逻辑
        if (coupon.getStatus() != 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "优惠券已失效"));
        }
        if (coupon.getEndTime() != null && coupon.getEndTime().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "优惠券已过期"));
        }

        // 检查是否已领取
        // List<UserCoupon> existing = userCouponRepository.findByUserIdAndStatus(user.getId(), 0);
        // if (existing.stream().anyMatch(c -> c.getCoupon().getId().equals(couponId))) {
        //    return ResponseEntity.badRequest().body(Map.of("error", "您已领取过该优惠券"));
        // }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUser(user);
        userCoupon.setCoupon(coupon);
        userCoupon.setStatus(0); // Unused
        
        UserCoupon saved = userCouponRepository.save(userCoupon);

        return ResponseEntity.ok(Map.of("message", "领取成功", "data", convertToDTO(saved)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<Map<String, Object>> getCouponStats(Authentication authentication) {
        UserEntity user = getCurrentUser(authentication);
        if (user == null || user.getId() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        List<UserCoupon> all = userCouponRepository.findByUserId(user.getId());
        long unused = all.stream().filter(c -> c.getStatus() == 0).count();
        long used = all.stream().filter(c -> c.getStatus() == 1).count();
        long expired = all.stream().filter(c -> c.getStatus() == 2).count();

        return ResponseEntity.ok(Map.of(
            "unused", unused,
            "used", used,
            "expired", expired,
            "total", all.size()
        ));
    }

    private UserCouponDTO convertToDTO(UserCoupon entity) {
        UserCouponDTO dto = new UserCouponDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getUser() != null && entity.getUser().getId() != null) {
            dto.setUserId(entity.getUser().getId());
        }
        
        if (entity.getCoupon() != null) {
            CouponDTO couponDTO = new CouponDTO();
            BeanUtils.copyProperties(entity.getCoupon(), couponDTO);
            dto.setCoupon(couponDTO);
        }
        return dto;
    }

    private UserEntity getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userService.findByUsername(authentication.getName()).orElse(null);
    }
}
