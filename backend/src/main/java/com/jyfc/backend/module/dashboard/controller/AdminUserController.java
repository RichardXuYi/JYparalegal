package com.jyfc.backend.module.dashboard.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.auth.entity.AdminEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.UserRepository;
import com.jyfc.backend.module.auth.service.AdminService;
import com.jyfc.backend.module.dashboard.dto.ChangePasswordRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final UserRepository userRepository;
    private final AdminService adminService;

    public AdminUserController(UserRepository userRepository, AdminService adminService) {
        this.userRepository = userRepository;
        this.adminService = adminService;
    }
    
    @GetMapping("/users")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        int maxSize = Math.max(1, Math.min(pageSize, 100));
        
        Page<UserEntity> userPage = userRepository.findAll(PageRequest.of(Math.max(0, page - 1), maxSize));
        
        return ApiResponse.success(Map.of(
                "items", userPage.getContent(),
                "total", userPage.getTotalElements(),
                "page", page,
                "pageSize", maxSize
        ));
    }
    
    @GetMapping("/users/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> getUserById(@PathVariable long id) {
        return userRepository.findById(id).map(user -> {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("email", user.getEmail());
            userInfo.put("phone", user.getPhone());
            userInfo.put("avatar", user.getAvatar());
            userInfo.put("status", user.getStatus());
            userInfo.put("userType", user.getUserType());
            userInfo.put("companyId", user.getCompanyId());
            userInfo.put("createdAt", user.getCreatedAt());
            userInfo.put("updatedAt", user.getUpdatedAt());
            return ApiResponse.success(userInfo);
        }).orElse(ApiResponse.error(404, "用户不存在"));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String email = (String) body.get("email");
        String password = (String) body.get("password");
        String phone = (String) body.get("phone");

        if (username == null || username.isBlank()) {
            return ApiResponse.error(400, "用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            return ApiResponse.error(400, "密码至少6位");
        }

        // Check duplicates
        if (userRepository.findByUsername(username).isPresent()) {
            return ApiResponse.error(400, "用户名已存在");
        }
        if (email != null && !email.isBlank() && userRepository.findByEmail(email).isPresent()) {
            return ApiResponse.error(400, "邮箱已被注册");
        }
        if (phone != null && !phone.isBlank() && userRepository.findByPhone(phone).isPresent()) {
            return ApiResponse.error(400, "手机号已被注册");
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setStatus(1);
        user.setUserType(body.get("userType") != null ? (String) body.get("userType") : "PERSONAL");

        // Encode password
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        user.setPassword(encoder.encode(password));

        if (body.get("companyId") != null) {
            user.setCompanyId(Long.valueOf(String.valueOf(body.get("companyId"))));
        }

        UserEntity saved = userRepository.save(user);

        Map<String, Object> data = new HashMap<>();
        data.put("id", saved.getId());
        data.put("username", saved.getUsername());
        data.put("email", saved.getEmail());
        data.put("status", saved.getStatus());

        return ApiResponse.success("用户创建成功", data);
    }

    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> updateUserStatus(
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        return userRepository.findById(id).map(user -> {
                    // Expect status as Integer now (0/1)
                    Object statusObj = body.get("status");
                    if (statusObj instanceof Integer) {
                        user.setStatus((Integer) statusObj);
                    } else if (statusObj instanceof String) {
                        try {
                            user.setStatus(Integer.parseInt((String) statusObj));
                        } catch (NumberFormatException e) {}
                    }
                    
                    userRepository.save(user);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "状态更新成功");
                    return ApiResponse.success("状态更新成功", result);
                }).orElse(ApiResponse.error(404, "用户不存在"));
    }

    /**
     * 编辑用户资料（邮箱/手机号/头像/用户类型）。
     * 使 admin 端编辑落库，替代仅前端本地态的假操作。
     */
    @PutMapping("/users/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> updateUser(
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        return userRepository.findById(id).map(user -> {
            if (body.containsKey("email")) {
                String email = (String) body.get("email");
                if (email != null && !email.isBlank()) {
                    var existing = userRepository.findByEmail(email);
                    if (existing.isPresent() && !existing.get().getId().equals(id)) {
                        return ApiResponse.<Map<String, Object>>error(400, "邮箱已被其他用户使用");
                    }
                }
                user.setEmail(email);
            }
            if (body.containsKey("phone")) {
                String phone = (String) body.get("phone");
                if (phone != null && !phone.isBlank()) {
                    var existing = userRepository.findByPhone(phone);
                    if (existing.isPresent() && !existing.get().getId().equals(id)) {
                        return ApiResponse.<Map<String, Object>>error(400, "手机号已被其他用户使用");
                    }
                }
                user.setPhone(phone);
            }
            if (body.containsKey("avatar")) {
                user.setAvatar((String) body.get("avatar"));
            }
            if (body.containsKey("userType") && body.get("userType") != null) {
                user.setUserType((String) body.get("userType"));
            }
            UserEntity saved = userRepository.save(user);
            Map<String, Object> data = new HashMap<>();
            data.put("id", saved.getId());
            data.put("username", saved.getUsername());
            data.put("email", saved.getEmail());
            data.put("phone", saved.getPhone());
            data.put("avatar", saved.getAvatar());
            data.put("userType", saved.getUserType());
            data.put("status", saved.getStatus());
            return ApiResponse.success("用户更新成功", data);
        }).orElse(ApiResponse.error(404, "用户不存在"));
    }
    
    // Role update for users is removed as they are just USERs.
    @PostMapping("/users/employee")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> createEmployeeUser(@RequestBody Map<String, Object> userData) {
        try {
            String username = (String) userData.get("username");
            String password = (String) userData.get("password");
            String email = (String) userData.get("email");
            
            AdminEntity savedAdmin = adminService.createAdmin(username, email, password, "EMPLOYEE");
            
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> data = new HashMap<>();
            data.put("id", savedAdmin.getId());
            result.put("data", data);
            result.put("message", "员工账号创建成功");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(400, "员工账号创建失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/profile")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> getAdminProfile(
            org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ApiResponse.error(401, "未登录");
        }

        String username = authentication.getName();
        Optional<AdminEntity> adminOpt = adminService.findByUsername(username);
        if (adminOpt.isEmpty()) {
            return ApiResponse.error(404, "管理员不存在");
        }

        AdminEntity admin = adminOpt.get();
        Map<String, Object> adminInfo = new HashMap<>();
        adminInfo.put("id", admin.getId());
        adminInfo.put("username", admin.getUsername());
        adminInfo.put("email", admin.getEmail());
        adminInfo.put("avatar", admin.getAvatar() != null ? admin.getAvatar() : "");
        adminInfo.put("roles", List.of(admin.getRole() != null ? admin.getRole() : "ADMIN"));
        adminInfo.put("status", admin.getStatus());
        adminInfo.put("createdAt", admin.getCreatedAt());
        adminInfo.put("updatedAt", admin.getUpdatedAt());

        return ApiResponse.success(adminInfo);
    }

    /**
     * 当前管理员修改自己的登录密码。
     *
     * <p>前端契约（{@code Settings.tsx}）：</p>
     * <ul>
     *   <li>Method: {@code PUT}</li>
     *   <li>Path: {@code /api/admin/profile/password}</li>
     *   <li>Body: {@code { "current": "<old>", "next": "<new>" }}</li>
     * </ul>
     *
     * <p>校验：current 必须匹配 BCrypt 存储；next 长度 ≥ 8；current ≠ next。</p>
     *
     * @param authentication Spring Security 当前认证对象（用于取当前管理员用户名）
     * @param body            请求体（字段名严格使用 {@code current} / {@code next}）
     * @return 成功返 {@code { success: true }}；失败返 400 + 错误信息
     */
    @PutMapping("/profile/password")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EMPLOYEE')")
    public ApiResponse<Map<String, Object>> changeAdminPassword(
            Authentication authentication,
            @RequestBody ChangePasswordRequest body) {

        if (authentication == null || authentication.getName() == null) {
            return ApiResponse.error(401, "未登录");
        }

        if (body == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }

        String current = body.getCurrent();
        String next = body.getNext();

        if (current == null || current.isEmpty()) {
            return ApiResponse.error(400, "请提供当前密码");
        }
        if (next == null || next.isEmpty()) {
            return ApiResponse.error(400, "请提供新密码");
        }
        if (next.length() < 8) {
            return ApiResponse.error(400, "新密码至少 8 位");
        }
        if (current.equals(next)) {
            return ApiResponse.error(400, "新密码不能与当前密码相同");
        }

        String username = authentication.getName();
        log.debug("Admin password change request for user: {}", username);

        Optional<AdminEntity> adminOpt = adminService.findByUsername(username);
        if (adminOpt.isEmpty()) {
            log.warn("Admin password change failed: admin not found: {}", username);
            return ApiResponse.error(404, "管理员不存在");
        }

        AdminEntity admin = adminOpt.get();

        // 校验当前密码
        if (!adminService.validatePassword(current, admin.getPassword())) {
            log.warn("Admin password change failed: incorrect current password for user: {}", username);
            return ApiResponse.error(400, "当前密码错误");
        }

        // 持久化新密码（BCrypt 编码由 AdminService.updatePassword 内部完成）
        adminService.updatePassword(admin, next);

        log.info("Admin password changed successfully for user: {}", username);

        Map<String, Object> data = new HashMap<>();
        data.put("success", true);
        data.put("message", "密码修改成功，请重新登录");
        return ApiResponse.success("密码修改成功", data);
    }
}
