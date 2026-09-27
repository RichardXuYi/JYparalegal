package com.jyfc.backend.module.sign.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.jyfc.backend.core.tenant.TenantEntityListener;

import java.time.LocalDateTime;

/**
 * 签署任务（V132）。状态机唯一权威：旧版 prd/10 §5.2 转移表（12 态含 REVOKED）。
 * 状态转移只允许经 SignTaskStateMachine.transfer()。
 * 租户（D12）：应用层强制 —— 写入经 TenantEntityListener 注入 tenant_id，读取经仓库 tenant-scoped 查询。
 */
@Entity
@Table(name = "sign_task")
@EntityListeners(TenantEntityListener.class)
public class SignTaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(name = "task_no", nullable = false, length = 64)
    private String taskNo;

    @Column(nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "sign_mode", nullable = false, length = 32)
    private String signMode = "PARALLEL";

    @Column(name = "finalize_mode", nullable = false, length = 32)
    private String finalizeMode = "AUTO";

    @Column(nullable = false, length = 32)
    private String source = "WEB";

    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    @Column(name = "contract_due_at")
    private LocalDateTime contractDueAt;

    @Column(name = "contract_due_end_at")
    private LocalDateTime contractDueEndAt;

    @Column(name = "approval_flow_id")
    private Long approvalFlowId;

    @Column(name = "quota_snapshot")
    private Integer quotaSnapshot;

    @Column(length = 32)
    private String provider;

    @Column(name = "provider_flow_id", length = 128)
    private String providerFlowId;

    @Column(name = "provider_mode", length = 16)
    private String providerMode;

    @Version
    @Column(nullable = false)
    private Integer version = 0;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

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
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSignMode() { return signMode; }
    public void setSignMode(String signMode) { this.signMode = signMode; }
    public String getFinalizeMode() { return finalizeMode; }
    public void setFinalizeMode(String finalizeMode) { this.finalizeMode = finalizeMode; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public LocalDateTime getContractDueAt() { return contractDueAt; }
    public void setContractDueAt(LocalDateTime contractDueAt) { this.contractDueAt = contractDueAt; }
    public LocalDateTime getContractDueEndAt() { return contractDueEndAt; }
    public void setContractDueEndAt(LocalDateTime contractDueEndAt) { this.contractDueEndAt = contractDueEndAt; }
    public Long getApprovalFlowId() { return approvalFlowId; }
    public void setApprovalFlowId(Long approvalFlowId) { this.approvalFlowId = approvalFlowId; }
    public Integer getQuotaSnapshot() { return quotaSnapshot; }
    public void setQuotaSnapshot(Integer quotaSnapshot) { this.quotaSnapshot = quotaSnapshot; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderFlowId() { return providerFlowId; }
    public void setProviderFlowId(String providerFlowId) { this.providerFlowId = providerFlowId; }
    public String getProviderMode() { return providerMode; }
    public void setProviderMode(String providerMode) { this.providerMode = providerMode; }
    public Integer getVersion() { return version; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
