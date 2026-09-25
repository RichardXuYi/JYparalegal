package com.jyfc.backend.module.auth.dto;

import java.time.LocalDateTime;

/**
 * 设备信息 DTO，用于向前端返回已登录设备的摘要。
 */
public record AuthDeviceDTO(
        Long id,
        String deviceName,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {}
