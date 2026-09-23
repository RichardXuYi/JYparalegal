package com.jyfc.backend.module.moot.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 庭审笔录条目（V137）。 */
@Entity
@Table(name = "moot_minute")
public class MootMinuteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(nullable = false, length = 64)
    private String speaker;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "spoken_at", nullable = false)
    private LocalDateTime spokenAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getSpeaker() { return speaker; }
    public void setSpeaker(String speaker) { this.speaker = speaker; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getSpokenAt() { return spokenAt; }
    public void setSpokenAt(LocalDateTime spokenAt) { this.spokenAt = spokenAt; }
}
