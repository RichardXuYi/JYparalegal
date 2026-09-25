package com.jyfc.backend.module.dashboard.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 安全审计日志实体
 * 记录所有安全相关的事件
 */
@Entity
@Table(name = "security_audit_logs", indexes = {
    @Index(name = "idx_username", columnList = "username"),
    @Index(name = "idx_ip_address", columnList = "ip_address"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class SecurityAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 事件类型
     */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * 用户名
     */
    @Column
    private String username;

    /**
     * IP地址
     */
    @Column(name = "ip_address")
    private String ipAddress;

    /**
     * User-Agent
     */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    /**
     * 设备指纹
     */
    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    /**
     * 事件详情
     */
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * 结果(success/failure)
     */
    @Column
    private String result;

    /**
     * 错误消息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 风险等级(low/medium/high/critical)
     */
    @Column(name = "risk_level")
    private String riskLevel;

    /**
     * 是否需要进一步调查
     */
    @Column(name = "requires_investigation")
    private Boolean requiresInvestigation = false;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ==================== Getter 与 Setter 方法 ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public Boolean getRequiresInvestigation() { return requiresInvestigation; }
    public void setRequiresInvestigation(Boolean requiresInvestigation) { this.requiresInvestigation = requiresInvestigation; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "SecurityAuditLog{" +
                "id=" + id +
                ", eventType='" + eventType + '\'' +
                ", username='" + username + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", result='" + result + '\'' +
                ", riskLevel='" + riskLevel + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
