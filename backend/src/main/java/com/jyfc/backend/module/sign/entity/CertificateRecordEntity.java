package com.jyfc.backend.module.sign.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 出证记录（V145，append-only）。一个任务一条（uk_cert_task）：首次出证落库，
 * 重复出证返回同一记录，certNo/issuedAt 不再每次现生成。
 */
@Entity
@Table(name = "certificate_record")
public class CertificateRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "cert_no", nullable = false, length = 64)
    private String certNo;

    @Column(nullable = false, length = 128)
    private String authority;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_task_id", length = 128)
    private String providerTaskId;

    @Column(name = "provider_cert_no", length = 128)
    private String providerCertNo;

    @Column(name = "doc_sha256", length = 64)
    private String docSha256;

    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getCertNo() { return certNo; }
    public void setCertNo(String certNo) { this.certNo = certNo; }
    public String getAuthority() { return authority; }
    public void setAuthority(String authority) { this.authority = authority; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderTaskId() { return providerTaskId; }
    public void setProviderTaskId(String providerTaskId) { this.providerTaskId = providerTaskId; }
    public String getProviderCertNo() { return providerCertNo; }
    public void setProviderCertNo(String providerCertNo) { this.providerCertNo = providerCertNo; }
    public String getDocSha256() { return docSha256; }
    public void setDocSha256(String docSha256) { this.docSha256 = docSha256; }
    public Long getIssuedBy() { return issuedBy; }
    public void setIssuedBy(Long issuedBy) { this.issuedBy = issuedBy; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
