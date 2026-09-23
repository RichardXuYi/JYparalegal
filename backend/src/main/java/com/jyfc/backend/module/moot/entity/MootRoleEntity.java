package com.jyfc.backend.module.moot.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 庭审角色（V137）。私有视野隔离在查询层按 role 过滤（prd13 §5）。 */
@Entity
@Table(name = "moot_role")
public class MootRoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "role_type", nullable = false, length = 32)
    private String roleType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "agent_flag", nullable = false)
    private Boolean agentFlag = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Boolean getAgentFlag() { return agentFlag; }
    public void setAgentFlag(Boolean agentFlag) { this.agentFlag = agentFlag; }
}
