package com.jyfc.backend.module.activity.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 活动报名实体
 */
@Entity
@Table(name = "activity_signups", uniqueConstraints = {
        @UniqueConstraint(name = "uk_activity_user", columnNames = { "activity_id", "user_id" })
})
public class ActivitySignup {

    /** 已报名 */
    public static final int STATUS_SIGNED_UP = 0;
    /** 已签到 */
    public static final int STATUS_CHECKED_IN = 1;
    /** 已取消 */
    public static final int STATUS_CANCELLED = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp
    @Column(name = "signup_time", updatable = false, nullable = false)
    private LocalDateTime signupTime;

    @Column(nullable = false)
    private Integer status = STATUS_SIGNED_UP;

    @Column(name = "signin_time")
    private LocalDateTime signinTime;

    @Column(length = 500)
    private String remark;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getSignupTime() { return signupTime; }
    public void setSignupTime(LocalDateTime signupTime) { this.signupTime = signupTime; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getSigninTime() { return signinTime; }
    public void setSigninTime(LocalDateTime signinTime) { this.signinTime = signinTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}