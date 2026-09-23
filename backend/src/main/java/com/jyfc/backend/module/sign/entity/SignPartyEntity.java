package com.jyfc.backend.module.sign.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.jyfc.backend.core.tenant.TenantEntityListener;

import java.time.LocalDateTime;

/** 签署参与方（V132）。party_status 6 态权威 = 旧版 DB §2.1 / prd10 §5.3。 */
@Entity
@Table(name = "sign_party")
@EntityListeners(TenantEntityListener.class)
public class SignPartyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "party_role", nullable = false, length = 32)
    private String partyRole = "SIGNER";

    @Column(name = "party_type", nullable = false, length = 32)
    private String partyType = "ORG";

    @Column(name = "party_status", nullable = false, length = 32)
    private String partyStatus = "PENDING_FILL";

    @Column(name = "sign_order", nullable = false)
    private Integer signOrder = 1;

    @Column(name = "can_fill", nullable = false)
    private Boolean canFill = false;

    @Column(name = "can_sign", nullable = false)
    private Boolean canSign = true;

    @Column(name = "identity_check", nullable = false)
    private Boolean identityCheck = false;

    @Column(name = "member_user_id")
    private Long memberUserId;

    @Column(name = "sign_requirement_json", length = 4000)
    private String signRequirementJson;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "external_name", length = 64)
    private String externalName;

    @Column(name = "external_phone", length = 32)
    private String externalPhone;

    @Column(name = "external_email", length = 128)
    private String externalEmail;

    @Column(name = "sign_invite_id")
    private Long signInviteId;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getPartyRole() { return partyRole; }
    public void setPartyRole(String partyRole) { this.partyRole = partyRole; }
    public String getPartyType() { return partyType; }
    public void setPartyType(String partyType) { this.partyType = partyType; }
    public String getPartyStatus() { return partyStatus; }
    public void setPartyStatus(String partyStatus) { this.partyStatus = partyStatus; }
    public Integer getSignOrder() { return signOrder; }
    public void setSignOrder(Integer signOrder) { this.signOrder = signOrder; }
    public Boolean getCanFill() { return canFill; }
    public void setCanFill(Boolean canFill) { this.canFill = canFill; }
    public Boolean getCanSign() { return canSign; }
    public void setCanSign(Boolean canSign) { this.canSign = canSign; }
    public Boolean getIdentityCheck() { return identityCheck; }
    public void setIdentityCheck(Boolean identityCheck) { this.identityCheck = identityCheck; }
    public Long getMemberUserId() { return memberUserId; }
    public void setMemberUserId(Long memberUserId) { this.memberUserId = memberUserId; }
    public String getSignRequirementJson() { return signRequirementJson; }
    public void setSignRequirementJson(String signRequirementJson) { this.signRequirementJson = signRequirementJson; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getExternalName() { return externalName; }
    public void setExternalName(String externalName) { this.externalName = externalName; }
    public String getExternalPhone() { return externalPhone; }
    public void setExternalPhone(String externalPhone) { this.externalPhone = externalPhone; }
    public String getExternalEmail() { return externalEmail; }
    public void setExternalEmail(String externalEmail) { this.externalEmail = externalEmail; }
    public Long getSignInviteId() { return signInviteId; }
    public void setSignInviteId(Long signInviteId) { this.signInviteId = signInviteId; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
