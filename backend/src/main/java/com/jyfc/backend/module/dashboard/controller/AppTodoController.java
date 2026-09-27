package com.jyfc.backend.module.dashboard.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 用户端待办中心接口
 */
@RestController
@RequestMapping("/api/app/todo")
public class AppTodoController {

    /**
     * 获取当前用户待办任务列表
     * 注意: Workflow 功能已移除，暂时返回空列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyTodos(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // Workflow 功能已移除，返回空列表
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "items", Collections.emptyList(),
                "total", 0,
                "page", page,
                "size", size
        )));
    }
}
