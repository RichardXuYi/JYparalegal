package com.jyfc.backend.module.signdoc.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import com.jyfc.backend.core.tenant.TenantEntityListener;

import java.time.LocalDateTime;

/**
 * 签署文档（V132 sign_doc + V143 落盘列）。映射既有表全列：
 * doc_file_id / evidence_status / sha256（V132），file_path / source（V143，上传落盘路径与来源）。
 * 租户（D12）：应用层强制 —— 写入经 TenantEntityListener 注入 tenant_id，读取经仓库 tenant-scoped 查询。
 */
@Entity
@Table(name = "sign_doc")
@EntityListeners(TenantEntityListener.class)
public class SignDocEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "doc_file_id", nullable = false)
    private Long docFileId;

    @Column(name = "evidence_status", nullable = false, length = 32)
    private String evidenceStatus = "NONE";

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(length = 30)
    private String source = "UPLOAD";

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getDocFileId() { return docFileId; }
    public void setDocFileId(Long docFileId) { this.docFileId = docFileId; }
    public String getEvidenceStatus() { return evidenceStatus; }
    public void setEvidenceStatus(String evidenceStatus) { this.evidenceStatus = evidenceStatus; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
