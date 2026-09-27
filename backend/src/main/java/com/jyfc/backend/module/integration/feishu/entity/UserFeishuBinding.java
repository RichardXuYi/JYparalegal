package com.jyfc.backend.module.integration.feishu.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户飞书绑定表。
 * 记录 JYFC 用户与飞书用户的绑定关系及 token 信息。
 */
@Entity
@Table(name = "user_feishu_binding")
public class UserFeishuBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "feishu_union_id", nullable = false, unique = true, length = 100)
    private String feishuUnionId;

    @Column(name = "feishu_open_id", length = 100)
    private String feishuOpenId;

    @Column(name = "feishu_user_id", length = 100)
    private String feishuUserId;

    @Column(name = "feishu_email", length = 200)
    private String feishuEmail;

    @Column(name = "feishu_mobile", length = 20)
    private String feishuMobile;

    @Column(name = "user_access_token", length = 500)
    private String userAccessToken;

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

    public String getFeishuUnionId() { return feishuUnionId; }
    public void setFeishuUnionId(String feishuUnionId) { this.feishuUnionId = feishuUnionId; }

    public String getFeishuOpenId() { return feishuOpenId; }
    public void setFeishuOpenId(String feishuOpenId) { this.feishuOpenId = feishuOpenId; }

    public String getFeishuUserId() { return feishuUserId; }
    public void setFeishuUserId(String feishuUserId) { this.feishuUserId = feishuUserId; }

    public String getFeishuEmail() { return feishuEmail; }
    public void setFeishuEmail(String feishuEmail) { this.feishuEmail = feishuEmail; }

    public String getFeishuMobile() { return feishuMobile; }
    public void setFeishuMobile(String feishuMobile) { this.feishuMobile = feishuMobile; }

    public String getUserAccessToken() { return userAccessToken; }
    public void setUserAccessToken(String userAccessToken) { this.userAccessToken = userAccessToken; }

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
