package com.jyfc.backend.module.signdoc.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.jyfc.backend.core.tenant.TenantEntityListener;

import java.time.LocalDateTime;

/**
 * 文档物理文件（V132 doc_file）。sign_doc.doc_file_id 为 NOT NULL 外键，
 * 上传落盘切片须先建 doc_file 行再挂 sign_doc；storage_key 为数据目录内相对路径，sha256 供完整性校验。
 * 租户（D12）：应用层强制，写入经 TenantEntityListener 注入 tenant_id。
 */
@Entity
@Table(name = "doc_file")
@EntityListeners(TenantEntityListener.class)
public class DocFileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "storage_key", nullable = false, length = 256)
    private String storageKey;

    @Column(name = "file_name", nullable = false, length = 256)
    private String fileName;

    @Column(length = 64)
    private String mime;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes = 0L;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
