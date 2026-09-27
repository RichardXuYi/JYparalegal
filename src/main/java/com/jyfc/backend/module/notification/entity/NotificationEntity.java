package com.jyfc.backend.module.notification.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 统一通知中心实体
 * <p>
 * 范围（MVP）：仅用于业务系统站内通知，不与钉钉/飞书/企微集成。
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        @Index(name = "idx_notifications_user_read", columnList = "user_id, is_read"),
        @Index(name = "idx_notifications_created_at", columnList = "created_at")
    }
)
public class NotificationEntity {

    /**
     * 通知类型枚举（与前端 type 字段对齐）
     */
    public enum Type {
        SYSTEM,              // 系统通知
        SIGN                 // 签署域通知（邀请/待接收/待签等）
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收通知的用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 通知类型（枚举字符串） */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private Type type;

    /** 通知标题 */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /** 通知内容 */
    @Column(name = "content", length = 1000)
    private String content;

    /** 跳转链接（前端路由） */
    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /** 是否已读 */
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public NotificationEntity() {}

    public NotificationEntity(Long userId, Type type, String title, String content, String linkUrl) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.linkUrl = linkUrl;
        this.isRead = false;
    }

    // Getters / Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }

    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
