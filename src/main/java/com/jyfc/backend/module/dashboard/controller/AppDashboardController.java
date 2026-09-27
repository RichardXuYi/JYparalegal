package com.jyfc.backend.module.dashboard.controller;

import com.jyfc.backend.module.auth.repository.UserRepository;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户端工作台统计接口
 */
@RestController
@RequestMapping("/api/app/dashboard")
public class AppDashboardController {

    @SuppressWarnings("unused")
    private final UserRepository userRepository;

    public AppDashboardController(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    /**
     * 获取工作台统计数据
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // 待办任务数
        stats.put("todoCount", 0);

        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
