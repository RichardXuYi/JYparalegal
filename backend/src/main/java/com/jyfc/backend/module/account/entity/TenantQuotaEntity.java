package com.jyfc.backend.module.account.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import com.jyfc.backend.core.tenant.TenantEntityListener;

import java.time.LocalDateTime;

/**
 * 租户配额（V139 tenant_quota）。账号中心切片：套餐 plan、签署份数配额 sign_quota（PRO 默认 11000）、
 * AI token 配额/已用。租户（D12）：应用层强制 —— 写入经 TenantEntityListener 注入 tenant_id，
 * 读取经仓库 tenant-scoped 查询；uk_quota_tenant 保证每租户一行。
 */
@Entity
@Table(name = "tenant_quota")
@EntityListeners(TenantEntityListener.class)
public class TenantQuotaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 30)
    private String plan = "PRO";

    @Column(name = "sign_quota", nullable = false)
    private Integer signQuota = 11000;

    @Column(name = "ai_quota_tokens", nullable = false)
    private Long aiQuotaTokens = 0L;

    @Column(name = "ai_used_tokens", nullable = false)
    private Long aiUsedTokens = 0L;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public Integer getSignQuota() { return signQuota; }
    public void setSignQuota(Integer signQuota) { this.signQuota = signQuota; }
    public Long getAiQuotaTokens() { return aiQuotaTokens; }
    public void setAiQuotaTokens(Long aiQuotaTokens) { this.aiQuotaTokens = aiQuotaTokens; }
    public Long getAiUsedTokens() { return aiUsedTokens; }
    public void setAiUsedTokens(Long aiUsedTokens) { this.aiUsedTokens = aiUsedTokens; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
