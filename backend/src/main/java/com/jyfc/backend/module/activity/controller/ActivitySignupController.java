package com.jyfc.backend.module.activity.controller;

import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.module.activity.entity.ActivitySignup;
import com.jyfc.backend.module.activity.service.ActivitySignupService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 活动报名（用户端）
 * <p>路径前缀 /api/app/activities/{id}/...</p>
 */
@RestController
@RequestMapping("/api/app/activities/{id}")
public class ActivitySignupController {

    private final ActivitySignupService signupService;
    private final UserContextUtil userContextUtil;

    public ActivitySignupController(ActivitySignupService signupService, UserContextUtil userContextUtil) {
        this.signupService = signupService;
        this.userContextUtil = userContextUtil;
    }

    /**
     * 报名
     */
    @PostMapping("/signup")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Map<String, Object>> signup(@PathVariable("id") Long activityId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        Long userId = userContextUtil.getCurrentUserId();
        Integer maxParticipants = null;
        if (body != null && body.get("maxParticipants") != null) {
            try {
                maxParticipants = Integer.valueOf(String.valueOf(body.get("maxParticipants")));
            } catch (NumberFormatException ignored) {
            }
        }
        ActivitySignup signup = signupService.signup(activityId, userId, maxParticipants);
        Map<String, Object> result = toMap(signup);
        return ApiResponse.success("报名成功", result);
    }

    /**
     * 取消报名
     */
    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Void> cancel(@PathVariable("id") Long activityId) {
        Long userId = userContextUtil.getCurrentUserId();
        signupService.cancel(activityId, userId);
        return ApiResponse.success("已取消报名", null);
    }

    /**
     * 签到
     */
    @PostMapping("/signin")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Map<String, Object>> signin(@PathVariable("id") Long activityId) {
        Long userId = userContextUtil.getCurrentUserId();
        ActivitySignup signup = signupService.checkin(activityId, userId);
        return ApiResponse.success("签到成功", toMap(signup));
    }

    /**
     * 分享
     */
    @PostMapping("/share")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Map<String, Object>> share(@PathVariable("id") Long activityId) {
        return ApiResponse.success(signupService.share(activityId));
    }

    /**
     * 查询我的报名状态
     */
    @GetMapping("/signup")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Map<String, Object>> mySignup(@PathVariable("id") Long activityId) {
        Long userId = userContextUtil.getCurrentUserId();
        Optional<ActivitySignup> opt = signupService.findMine(activityId, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("signedUp", opt.isPresent());
        result.put("signup", opt.map(this::toMap).orElse(null));
        return ApiResponse.success(result);
    }

    /**
     * 管理员/活动方查询报名列表
     */
    @GetMapping("/signups")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EMPLOYEE')")
    public ApiResponse<List<ActivitySignup>> listSignups(@PathVariable("id") Long activityId) {
        return ApiResponse.success(signupService.listByActivity(activityId));
    }

    private Map<String, Object> toMap(ActivitySignup s) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.getId());
        map.put("activityId", s.getActivityId());
        map.put("userId", s.getUserId());
        map.put("signupTime", s.getSignupTime());
        map.put("status", s.getStatus());
        map.put("signinTime", s.getSigninTime());
        return map;
    }
}