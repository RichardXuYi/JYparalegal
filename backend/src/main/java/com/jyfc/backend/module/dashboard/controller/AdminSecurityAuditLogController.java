package com.jyfc.backend.module.dashboard.controller;

import com.jyfc.backend.module.dashboard.service.SecurityAuditLogService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/security/audit-logs")
public class AdminSecurityAuditLogController {
    private final SecurityAuditLogService securityAuditLogService;

    public AdminSecurityAuditLogController(SecurityAuditLogService securityAuditLogService) {
        this.securityAuditLogService = securityAuditLogService;
    }

    @PostMapping("/cleanup")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE','ROLE_SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> cleanupOldLogs() {
        long deleted = securityAuditLogService.cleanupOldLogs();
        return ApiResponse.success(Map.of("deleted", deleted));
    }
}

