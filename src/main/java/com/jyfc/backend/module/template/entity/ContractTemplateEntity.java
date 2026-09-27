package com.jyfc.backend.module.template.entity;

import com.jyfc.backend.core.tenant.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 合同模板（V140）。body 内含 {{varName}} 占位符，variables 为 JSON 变量名清单（渲染时校验未填项）。
 * 租户（D12）：应用层强制 —— 写入经 TenantEntityListener 注入 tenant_id，读取经仓库 tenant-scoped 查询。
 */
@Entity
@Table(name = "contract_template")
@EntityListeners(TenantEntityListener.class)
public class ContractTemplateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 50)
    private String category;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String body;

    @Column(columnDefinition = "JSON")
    private String variables;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_by")
    private Long createdBy;

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
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getVariables() { return variables; }
    public void setVariables(String variables) { this.variables = variables; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
