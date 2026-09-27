package com.jyfc.backend.module.notification.dto;

import com.jyfc.backend.module.notification.entity.NotificationEntity;

import java.time.LocalDateTime;

/**
 * 通知视图 DTO（直接暴露给前端，与 ApiResponse.data 配合使用）
 */
public class NotificationDto {
    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String linkUrl;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public NotificationDto() {}

    public static NotificationDto from(NotificationEntity e) {
        NotificationDto d = new NotificationDto();
        d.id = e.getId();
        d.userId = e.getUserId();
        d.type = e.getType() == null ? null : e.getType().name();
        d.title = e.getTitle();
        d.content = e.getContent();
        d.linkUrl = e.getLinkUrl();
        d.isRead = e.getIsRead();
        d.createdAt = e.getCreatedAt();
        return d;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
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
