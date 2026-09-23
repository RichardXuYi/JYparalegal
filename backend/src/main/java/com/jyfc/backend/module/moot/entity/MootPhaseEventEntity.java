package com.jyfc.backend.module.moot.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 庭审阶段事件（V137，append-only）。含反向跳转（新证据/补充调查）。 */
@Entity
@Table(name = "moot_phase_event")
public class MootPhaseEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "from_phase", length = 32)
    private String fromPhase;

    @Column(name = "to_phase", nullable = false, length = 32)
    private String toPhase;

    @Column(name = "trigger_type", nullable = false, length = 32)
    private String triggerType;

    @Column(length = 256)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getFromPhase() { return fromPhase; }
    public void setFromPhase(String fromPhase) { this.fromPhase = fromPhase; }
    public String getToPhase() { return toPhase; }
    public void setToPhase(String toPhase) { this.toPhase = toPhase; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
