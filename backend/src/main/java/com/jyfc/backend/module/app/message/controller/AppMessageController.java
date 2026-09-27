package com.jyfc.backend.module.app.message.controller;

import com.jyfc.backend.module.app.message.entity.UserMessageEntity;
import com.jyfc.backend.module.app.message.service.AppMessageService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * App 端用户消息中心 API
 * 路径：/api/app/messages
 *
 * P1-5 修复：原缺失端点，前端 messages.tsx 调用 500
 */
@RestController
@RequestMapping("/api/app/messages")
public class AppMessageController {

    private final AppMessageService messageService;

    @Autowired
    public AppMessageController(AppMessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = messageService.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        Page<UserMessageEntity> p = messageService.listMessages(userId, type, page, size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", p.getContent().stream().map(this::toDto).toList());
        result.put("total", p.getTotalElements());
        result.put("page", p.getNumber());
        result.put("pageSize", p.getSize());
        return ApiResponse.success(result);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount() {
        Long userId = messageService.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.success(Map.of("count", 0));
        }
        return ApiResponse.success(Map.of("count", messageService.getUnreadCount(userId)));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Boolean> markRead(@PathVariable Long id) {
        Long userId = messageService.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(messageService.markRead(userId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        Long userId = messageService.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(messageService.delete(userId, id));
    }

    private Map<String, Object> toDto(UserMessageEntity m) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", m.getId());
        d.put("type", m.getType());
        d.put("title", m.getTitle());
        d.put("content", m.getContent());
        d.put("isRead", Boolean.TRUE.equals(m.getIsRead()));
        d.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
        return d;
    }
}
