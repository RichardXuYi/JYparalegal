package com.jyfc.backend.module.auth.dto;

import java.time.LocalDateTime;

public class UserCouponDTO {
    private Long id;
    private Long userId;
    private CouponDTO coupon;
    private Integer status;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public CouponDTO getCoupon() { return coupon; }
    public void setCoupon(CouponDTO coupon) { this.coupon = coupon; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
