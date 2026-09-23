package com.jyfc.backend.module.auth.dto;

import java.time.LocalDateTime;

/**
 * 组织成员 DTO
 * 用于 GET /api/organization/members 端点的返回结构。
 *
 * <p>字段说明：
 * <ul>
 *   <li>userId - 用户 ID</li>
 *   <li>username - 用户名（login name）</li>
 *   <li>realName - 真实姓名（当前数据模型未存储该字段，回退到 username；保留字段便于未来扩展）</li>
 *   <li>role - 角色：OWNER（企业所有者）/ MEMBER（被分配的企业成员）</li>
 *   <li>positionName - 关联职位名称（可能为 null，未分配职位时）</li>
 *   <li>departmentName - 关联部门名称（可能为 null）</li>
 *   <li>joinedAt - 加入时间：owner 为企业创建时间，member 为用户创建时间或最近一次分配时间</li>
 * </ul>
 * </p>
 */
public class OrganizationMemberDTO {
    private Long userId;
    private String username;
    private String realName;
    private String role;
    private String positionName;
    private String departmentName;
    private LocalDateTime joinedAt;

    public OrganizationMemberDTO() {}

    public OrganizationMemberDTO(Long userId, String username, String realName, String role,
                                 String positionName, String departmentName, LocalDateTime joinedAt) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.role = role;
        this.positionName = positionName;
        this.departmentName = departmentName;
        this.joinedAt = joinedAt;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPositionName() { return positionName; }
    public void setPositionName(String positionName) { this.positionName = positionName; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}