package com.jyfc.backend.module.sign.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import com.jyfc.backend.core.tenant.TenantEntityListener;

import java.time.LocalDateTime;

/** 签署邀请（V132）。invite_status 权威 = WAIT/ACCEPTED/EXPIRED/REJECTED（非 PENDING）。 */
@Entity
@Table(name = "sign_invite")
@EntityListeners(TenantEntityListener.class)
public class SignInviteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "invite_status", nullable = false, length = 32)
    private String inviteStatus = "WAIT";

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "target_company_id")
    private Long targetCompanyId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public String getInviteStatus() { return inviteStatus; }
    public void setInviteStatus(String inviteStatus) { this.inviteStatus = inviteStatus; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public Long getTargetCompanyId() { return targetCompanyId; }
    public void setTargetCompanyId(Long targetCompanyId) { this.targetCompanyId = targetCompanyId; }
    /** 仅哈希落库，禁止随实体序列化外泄。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
