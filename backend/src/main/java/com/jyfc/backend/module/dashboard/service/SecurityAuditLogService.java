package com.jyfc.backend.module.dashboard.service;

import com.jyfc.backend.module.dashboard.entity.SecurityAuditLog;
import com.jyfc.backend.module.dashboard.repository.SecurityAuditLogRepository;
import com.jyfc.backend.core.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 安全审计日志服务
 */
@Service
public class SecurityAuditLogService {
    
    private final SecurityAuditLogRepository auditLogRepository;
    
    @Autowired
    public SecurityAuditLogService(SecurityAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }
    
    /**
     * 记录安全事件
     */
    public void logSecurityEvent(
        SecurityConstants.AuditEventType eventType,
        String username,
        String ipAddress,
        String userAgent,
        String deviceFingerprint,
        String details,
        String result,
        String errorMessage,
        String riskLevel
    ) {
        SecurityAuditLog log = new SecurityAuditLog();
        log.setEventType(eventType != null ? eventType.name() : "UNKNOWN");
        // 防御性处理: 某些事件(如缺少凭据的登录失败)username 可能为 null
        log.setUsername(username != null && !username.isBlank() ? username : "anonymous");
        log.setIpAddress(ipAddress != null ? ipAddress : "unknown");
        log.setUserAgent(userAgent);
        log.setDeviceFingerprint(deviceFingerprint);
        log.setDetails(details);
        log.setResult(result);
        log.setErrorMessage(errorMessage);
        log.setRiskLevel(riskLevel);

        // 自动判断是否需要调查的事件
        if (isHighRiskEvent(eventType, riskLevel)) {
            log.setRequiresInvestigation(true);
        }

        try {
            auditLogRepository.save(log);
        } catch (Exception e) {
            // 审计日志写入失败不应影响主业务流
            logger.warn("Failed to persist security audit log (event={}, user={}): {}",
                eventType, username, e.getMessage());
        }
        logger.info("Security event logged: {} by user: {} from IP: {}", eventType, username, ipAddress);
    }
    
    /**
     * 便捷方法：记录登录失败
     */
    public void logLoginFailure(String username, String ipAddress, String userAgent, String deviceFingerprint, String reason) {
        logSecurityEvent(
            SecurityConstants.AuditEventType.LOGIN_FAILURE,
            username,
            ipAddress,
            userAgent,
            deviceFingerprint,
            reason,
            "failure",
            reason,
            "medium"
        );
    }
    
    /**
     * 便捷方法：记录登录成功
     */
    public void logLoginSuccess(String username, String ipAddress, String userAgent, String deviceFingerprint) {
        logSecurityEvent(
            SecurityConstants.AuditEventType.LOGIN_SUCCESS,
            username,
            ipAddress,
            userAgent,
            deviceFingerprint,
            "User logged in successfully",
            "success",
            null,
            "low"
        );
    }
    
    /**
     * 便捷方法：记录注册
     */
    public void logRegistration(String username, String ipAddress, String userAgent, String deviceFingerprint, boolean success, String error) {
        String riskLevel = "low";
        String result = success ? "success" : "failure";
        
        logSecurityEvent(
            SecurityConstants.AuditEventType.REGISTER_ATTEMPT,
            username,
            ipAddress,
            userAgent,
            deviceFingerprint,
            "User registration attempt",
            result,
            error,
            riskLevel
        );
    }
    
    /**
     * 便捷方法：记录可疑活动
     */
    public void logSuspiciousActivity(String username, String ipAddress, String userAgent, String deviceFingerprint, String details) {
        logSecurityEvent(
            SecurityConstants.AuditEventType.SUSPICIOUS_ACTIVITY,
            username,
            ipAddress,
            userAgent,
            deviceFingerprint,
            details,
            "suspicious",
            null,
            "high"
        );
    }
    
    /**
     * 获取特定用户的审计日志
     */
    public List<SecurityAuditLog> getUserAuditLogs(String username) {
        if (username == null) return List.of();
        return auditLogRepository.findByUsernameOrderByCreatedAtDesc(username);
    }
    
    /**
     * 获取特定IP的审计日志
     */
    public List<SecurityAuditLog> getIpAuditLogs(String ipAddress) {
        if (ipAddress == null) return List.of();
        return auditLogRepository.findByIpAddressOrderByCreatedAtDesc(ipAddress);
    }
    
    /**
     * 获取需要调查的日志
     */
    public List<SecurityAuditLog> getLogsRequiringInvestigation() {
        return auditLogRepository.findLogsRequiringInvestigation();
    }
    
    /**
     * 获取失败的登录尝试
     */
    public List<SecurityAuditLog> getFailedLoginAttempts(String username, LocalDateTime startTime, LocalDateTime endTime) {
        if (username == null || startTime == null || endTime == null) return List.of();
        return auditLogRepository.findFailedLoginAttempts(username, startTime, endTime);
    }
    
    /**
     * 获取高风险事件
     */
    public List<SecurityAuditLog> getHighRiskEvents() {
        return auditLogRepository.findHighRiskEvents();
    }
    
    /**
     * 清理旧的审计日志（保留90天）
     */
    @Transactional
    public long cleanupOldLogs() {
        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
        long deleted = auditLogRepository.deleteByCreatedAtBefore(ninetyDaysAgo);
        logger.info("Audit logs cleanup: removed {} logs older than {}", deleted, ninetyDaysAgo);
        return deleted;
    }
    
    /**
     * 判断是否为高风险事件
     */
    private boolean isHighRiskEvent(SecurityConstants.AuditEventType eventType, String riskLevel) {
        if ("high".equals(riskLevel) || "critical".equals(riskLevel)) {
            return true;
        }
        if (eventType == null) return false;
        
        // 特定事件类型也认为是高风险
        return eventType == SecurityConstants.AuditEventType.SUSPICIOUS_ACTIVITY ||
               eventType == SecurityConstants.AuditEventType.UNAUTHORIZED_ACCESS ||
               eventType == SecurityConstants.AuditEventType.ACCOUNT_LOCKED;
    }
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditLogService.class);
}
