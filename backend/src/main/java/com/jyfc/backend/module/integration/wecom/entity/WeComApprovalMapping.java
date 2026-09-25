package com.jyfc.backend.module.integration.wecom.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 企业微信审批映射实体
 */
@Entity
@Table(name = "wecom_approval_mapping")
public class WeComApprovalMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "wecom_sp_no", nullable = false, length = 100)
    private String wecomSpNo;

    @Column(name = "template_id", length = 100)
    private String templateId;

    @Column(length = 50)
    private String status;

    @Column(name = "applicant_userid", length = 100)
    private String applicantUserid;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ======== Getters & Setters ========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getContractId() { return contractId; }
    public void setContractId(Long contractId) { this.contractId = contractId; }

    public String getWecomSpNo() { return wecomSpNo; }
    public void setWecomSpNo(String wecomSpNo) { this.wecomSpNo = wecomSpNo; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getApplicantUserid() { return applicantUserid; }
    public void setApplicantUserid(String applicantUserid) { this.applicantUserid = applicantUserid; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
