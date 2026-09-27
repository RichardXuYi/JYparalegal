package com.jyfc.backend.module.integration.dingtalk.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户钉钉绑定表。
 * 记录 JYFC 用户与钉钉用户的绑定关系及 token 信息。
 */
@Entity
@Table(name = "user_dingtalk_binding")
public class UserDingTalkBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "dingtalk_unionid", nullable = false, unique = true, length = 100)
    private String dingtalkUnionId;

    @Column(name = "dingtalk_userid", length = 100)
    private String dingtalkUserId;

    @Column(name = "dingtalk_mobile", length = 20)
    private String dingtalkMobile;

    @Column(name = "access_token", length = 500)
    private String accessToken;

    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ---- Getters & Setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getDingtalkUnionId() { return dingtalkUnionId; }
    public void setDingtalkUnionId(String dingtalkUnionId) { this.dingtalkUnionId = dingtalkUnionId; }

    public String getDingtalkUserId() { return dingtalkUserId; }
    public void setDingtalkUserId(String dingtalkUserId) { this.dingtalkUserId = dingtalkUserId; }

    public String getDingtalkMobile() { return dingtalkMobile; }
    public void setDingtalkMobile(String dingtalkMobile) { this.dingtalkMobile = dingtalkMobile; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(LocalDateTime tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
