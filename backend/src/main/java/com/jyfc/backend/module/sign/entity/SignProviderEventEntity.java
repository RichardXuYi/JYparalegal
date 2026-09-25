package com.jyfc.backend.module.sign.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** e签宝回调幂等。同一 event_key 只推进一次状态机。 */
@Entity
@Table(name = "sign_provider_event")
public class SignProviderEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, length = 191)
    private String eventKey;

    @Column(name = "flow_id", length = 128)
    private String flowId;

    @Column(length = 64)
    private String action;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
