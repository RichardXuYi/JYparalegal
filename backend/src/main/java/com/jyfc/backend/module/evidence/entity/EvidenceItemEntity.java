package com.jyfc.backend.module.evidence.entity;

import com.jyfc.backend.core.tenant.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 证据条目（V141）。按 biz_type+biz_id 挂接业务对象；sha256 固化内容指纹（创建时由 content 计算）；
 * 状态机 UNVERIFIED/VERIFIED/REJECTED。租户（D12）：应用层强制 —— 写入经 TenantEntityListener 注入
 * tenant_id，读取经仓库 tenant-scoped 查询。
 */
@Entity
@Table(name = "evidence_item")
@EntityListeners(TenantEntityListener.class)
public class EvidenceItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "biz_type", nullable = false, length = 20)
    private String bizType;

    @Column(name = "biz_id")
    private Long bizId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "evidence_type", length = 30)
    private String evidenceType;

    @Column(length = 50)
    private String source;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(length = 64)
    private String sha256;

    @Column(nullable = false, length = 20)
    private String status = "UNVERIFIED";

    @Column(length = 500)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
