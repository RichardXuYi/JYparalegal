package com.jyfc.backend.module.integration.dingtalk.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 钉钉审批映射表。
 * 记录 JYFC 合同与钉钉审批实例的关联关系。
 */
@Entity
@Table(name = "dingtalk_approval_mapping")
public class DingTalkApprovalMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "dingtalk_approval_id", nullable = false, unique = true, length = 100)
    private String dingtalkApprovalId;

    @Column(name = "dingtalk_process_code", length = 100)
    private String dingtalkProcessCode;

    @Column(name = "status", length = 50)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ---- Getters & Setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getContractId() { return contractId; }
    public void setContractId(Long contractId) { this.contractId = contractId; }

    public String getDingtalkApprovalId() { return dingtalkApprovalId; }
    public void setDingtalkApprovalId(String dingtalkApprovalId) { this.dingtalkApprovalId = dingtalkApprovalId; }

    public String getDingtalkProcessCode() { return dingtalkProcessCode; }
    public void setDingtalkProcessCode(String dingtalkProcessCode) { this.dingtalkProcessCode = dingtalkProcessCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
