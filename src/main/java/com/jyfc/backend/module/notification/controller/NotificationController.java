package com.jyfc.backend.module.notification.controller;

import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.module.notification.service.NotificationService;
import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.shared.dto.PageResponse;
import com.jyfc.backend.module.notification.dto.NotificationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通知中心 Controller（MVP）
 * <p>
 * 所有接口需要登录，统一从 SecurityContext 解析 userId；
 * 避免前端把 userId 传过来造成越权。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_ADMIN')")
@Tag(name = "通知中心", description = "站内统一通知")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserContextUtil userContextUtil;

    /**
     * 分页获取当前用户的通知
     */
    @GetMapping
    @Operation(summary = "通知列表（分页）")
    public ResponseEntity<ApiResponse<PageResponse<NotificationDto>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Long userId = userContextUtil.getCurrentUserId();
        PageResponse<NotificationDto> data = notificationService.getUserNotifications(userId, page, size, unreadOnly);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 获取当前用户未读数量
     */
    @GetMapping("/unread-count")
    @Operation(summary = "未读数量")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        Long userId = userContextUtil.getCurrentUserId();
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    /**
     * 标记单条已读
     */
    @PostMapping("/{id}/read")
    @Operation(summary = "标记单条已读")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> markRead(@PathVariable("id") Long id) {
        Long userId = userContextUtil.getCurrentUserId();
        boolean ok = notificationService.markAsRead(userId, id);
        if (!ok) {
            return ResponseEntity.ok(ApiResponse.success(Map.of("ok", false)));
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true)));
    }

    /**
     * 全部标记为已读
     */
    @PostMapping("/read-all")
    @Operation(summary = "全部标记为已读")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead() {
        Long userId = userContextUtil.getCurrentUserId();
        int updated = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", updated)));
    }
}
