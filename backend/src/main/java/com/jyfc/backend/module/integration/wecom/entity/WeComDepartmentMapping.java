package com.jyfc.backend.module.integration.wecom.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 企业微信部门映射实体
 */
@Entity
@Table(name = "wecom_department_mapping")
public class WeComDepartmentMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wecom_dept_id", nullable = false, unique = true)
    private Long wecomDeptId;

    @Column(name = "wecom_dept_name", length = 100)
    private String wecomDeptName;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "order_num")
    private Integer orderNum = 0;

    @CreationTimestamp
    @Column(name = "synced_at", updatable = false)
    private LocalDateTime syncedAt;

    // ======== Getters & Setters ========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWecomDeptId() { return wecomDeptId; }
    public void setWecomDeptId(Long wecomDeptId) { this.wecomDeptId = wecomDeptId; }

    public String getWecomDeptName() { return wecomDeptName; }
    public void setWecomDeptName(String wecomDeptName) { this.wecomDeptName = wecomDeptName; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public Integer getOrderNum() { return orderNum; }
    public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }

    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }
}
