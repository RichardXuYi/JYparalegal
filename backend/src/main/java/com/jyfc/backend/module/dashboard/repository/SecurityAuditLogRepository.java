package com.jyfc.backend.module.dashboard.repository;

import com.jyfc.backend.module.dashboard.entity.SecurityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 安全审计日志存储库
 */
@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {
    
    /**
     * 查找特定用户的审计日志
     */
    List<SecurityAuditLog> findByUsernameOrderByCreatedAtDesc(String username);
    
    /**
     * 查找特定IP的审计日志
     */
    List<SecurityAuditLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress);
    
    /**
     * 查找特定事件类型的审计日志
     */
    List<SecurityAuditLog> findByEventTypeOrderByCreatedAtDesc(String eventType);
    
    /**
     * 查找需要调查的日志
     */
    @Query("SELECT a FROM SecurityAuditLog a WHERE a.requiresInvestigation = true ORDER BY a.createdAt DESC")
    List<SecurityAuditLog> findLogsRequiringInvestigation();
    
    /**
     * 查找指定时间范围内的失败登录
     */
    @Query("SELECT a FROM SecurityAuditLog a WHERE a.eventType = 'LOGIN_FAILURE' " +
           "AND a.username = :username AND a.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY a.createdAt DESC")
    List<SecurityAuditLog> findFailedLoginAttempts(
        @Param("username") String username,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 查找指定IP在某时间范围内的所有活动
     */
    @Query("SELECT a FROM SecurityAuditLog a WHERE a.ipAddress = :ipAddress " +
           "AND a.createdAt BETWEEN :startTime AND :endTime ORDER BY a.createdAt DESC")
    List<SecurityAuditLog> findActivitiesByIpInTimeRange(
        @Param("ipAddress") String ipAddress,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 查找高风险事件
     */
    @Query("SELECT a FROM SecurityAuditLog a WHERE a.riskLevel IN ('high', 'critical') " +
           "ORDER BY a.createdAt DESC")
    List<SecurityAuditLog> findHighRiskEvents();
    
    /**
     * 统计特定事件的数量
     */
    long countByEventTypeAndCreatedAtAfter(String eventType, LocalDateTime after);

    long deleteByCreatedAtBefore(LocalDateTime before);
}
