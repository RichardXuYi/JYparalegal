package com.jyfc.backend.module.agent.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Agent 工具调用归因（V135）。HITL 状态留痕。 */
@Entity
@Table(name = "agent_tool_call")
public class AgentToolCallEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(name = "args_json", columnDefinition = "JSON")
    private String argsJson;

    @Column(name = "result_json", columnDefinition = "JSON")
    private String resultJson;

    @Column(nullable = false, length = 32)
    private String status = "SUCCESS";

    @Column(name = "hitl_required", nullable = false)
    private Boolean hitlRequired = false;

    @Column(name = "hitl_confirmed", nullable = false)
    private Boolean hitlConfirmed = false;

    @Column(name = "hitl_actor_id")
    private Long hitlActorId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getArgsJson() { return argsJson; }
    public void setArgsJson(String argsJson) { this.argsJson = argsJson; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getHitlRequired() { return hitlRequired; }
    public void setHitlRequired(Boolean hitlRequired) { this.hitlRequired = hitlRequired; }
    public Boolean getHitlConfirmed() { return hitlConfirmed; }
    public void setHitlConfirmed(Boolean hitlConfirmed) { this.hitlConfirmed = hitlConfirmed; }
    public Long getHitlActorId() { return hitlActorId; }
    public void setHitlActorId(Long hitlActorId) { this.hitlActorId = hitlActorId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
