package com.jyfc.backend.module.auth.controller;

import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.service.TokenService;
import com.jyfc.backend.module.auth.service.UserService;
import com.jyfc.backend.core.security.SecurityConstants;
import com.jyfc.backend.core.security.ValidationUtils;
import com.jyfc.backend.module.dashboard.repository.ConsultationRepository;
import com.jyfc.backend.module.trade.repository.OrderRepository;
import com.jyfc.backend.module.dashboard.repository.FavoriteRepository;
import com.jyfc.backend.module.news.repository.NewsLikeRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

/**
 * 用户控制器
 * 提供用户资料管理、注册、开发测试等接口
 */
@RestController
@RequestMapping("/api/app")
public class UserController {
    
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    
    private final UserService userService;
    private final TokenService tokenService;
    private final ConsultationRepository consultationRepository;
    private final OrderRepository orderRepository;
    private final FavoriteRepository favoriteRepository;
    private final NewsLikeRepository newsLikeRepository;

    /** refresh token cookie 的 secure 属性，与 AuthController 保持一致。 */
    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;
    
    @Autowired
    public UserController(
            UserService userService,
            TokenService tokenService,
            ConsultationRepository consultationRepository,
            OrderRepository orderRepository,
            FavoriteRepository favoriteRepository,
            NewsLikeRepository newsLikeRepository) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.consultationRepository = consultationRepository;
        this.orderRepository = orderRepository;
        this.favoriteRepository = favoriteRepository;
        this.newsLikeRepository = newsLikeRepository;
    }
    
    // ==================== 用户资料管理 ====================
    
    /**
     * 获取当前用户信息
     * @param authentication 当前认证信息
     * @return 用户信息响应
     */
    @GetMapping("/user/profile")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            log.warn("Unauthorized access to profile endpoint");
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        
        String username = authentication.getName();
        log.debug("Getting profile for user: {}", username);
        
        Optional<UserEntity> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("User not found: {}", username);
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
        
        UserEntity user = userOpt.get();
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("avatar", user.getAvatar() != null ? user.getAvatar() : "");
        userInfo.put("phone", user.getPhone() != null ? user.getPhone() : "");
        userInfo.put("roles", List.of("USER"));
        userInfo.put("status", user.getStatus());
        userInfo.put("createdAt", user.getCreatedAt());
        userInfo.put("updatedAt", user.getUpdatedAt());
        
        return ResponseEntity.ok(userInfo);
    }
    
    /**
     * 更新用户资料
     * @param authentication 当前认证信息
     * @param body 更新内容（username, email, avatar）
     * @return 更新后的用户信息
     */
    @PutMapping("/user/profile")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<Map<String, Object>> updateProfile(
            Authentication authentication,
            @RequestBody Map<String, String> body) {
        
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        
        String username = authentication.getName();
        log.debug("Updating profile for user: {}", username);
        
        Optional<UserEntity> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
        
        UserEntity user = userOpt.get();
        
        // 更新用户名(如果提供)
        String newUsername = body.get("username");
        if (newUsername != null && !newUsername.equals(user.getUsername())) {
            // 检查新用户名是否已被使用
            if (userService.findByUsername(newUsername).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户名已被使用"));
            }
            user.setUsername(newUsername);
        }
        
        // 更新邮箱(如果提供)
        String newEmail = body.get("email");
        if (newEmail != null && !newEmail.equals(user.getEmail())) {
            // 检查新邮箱是否已被使用
            if (userService.findByEmail(newEmail).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "邮箱已被使用"));
            }
            user.setEmail(newEmail);
        }
        
        // 更新头像(如果提供)
        String avatar = body.get("avatar");
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        
        // 更新手机号(如果提供)
        String newPhone = body.get("phone");
        if (newPhone != null && !newPhone.equals(user.getPhone())) {
            if (!newPhone.isEmpty()) {
                if (userService.findByPhone(newPhone).isPresent()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "手机号已被使用"));
                }
            }
            user.setPhone(newPhone.isEmpty() ? null : newPhone);
        }
        
        // 保存更新
        UserEntity updatedUser = userService.updateUser(user);
        log.info("Profile updated for user: {}", username);
        
        // 返回更新后的用户信息
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", updatedUser.getId());
        userInfo.put("username", updatedUser.getUsername());
        userInfo.put("email", updatedUser.getEmail());
        userInfo.put("avatar", updatedUser.getAvatar() != null ? updatedUser.getAvatar() : "");
        userInfo.put("phone", updatedUser.getPhone() != null ? updatedUser.getPhone() : "");
        userInfo.put("roles", List.of("USER"));
        userInfo.put("status", updatedUser.getStatus());
        
        return ResponseEntity.ok(userInfo);
    }
    
    /**
     * 获取用户统计信息
     * @param authentication 当前认证信息
     * @return 用户统计信息
     */
    @GetMapping("/user/stats")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<Map<String, Object>> getUserStats(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            log.warn("Unauthorized access to stats endpoint");
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        
        String username = authentication.getName();
        log.debug("Getting stats for user: {}", username);
        
        Optional<UserEntity> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("User not found: {}", username);
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
        
        UserEntity user = userOpt.get();
        Long userId = user.getId();
        if (userId == null) {
            return ResponseEntity.status(500).body(Map.of("error", "用户数据异常"));
        }
        Map<String, Object> stats = new HashMap<>();
        stats.put("consultationCount", consultationRepository.countByUserId(userId));
        stats.put("orderCount", orderRepository.countByUserId(userId));
        stats.put("favoriteCount", favoriteRepository.countByUserId(userId));
        stats.put("likeCount", newsLikeRepository.countByUserId(userId));
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 修改密码
     * @param authentication 当前认证信息
     * @param body 包含旧密码和新密码的请求体
     * @return 修改结果
     */
    @PutMapping("/user/password")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<Map<String, Object>> changePassword(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody Map<String, String> body) {
        
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        
        String username = authentication.getName();
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        
        log.debug("Password change request for user: {}", username);
        
        if (oldPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "请提供旧密码和新密码"));
        }
        
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "新密码至少6位"));
        }
        
        Optional<UserEntity> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
        
        UserEntity user = userOpt.get();
        
        // 验证旧密码
        if (!userService.validatePassword(oldPassword, user.getPassword())) {
            log.warn("Password change failed: incorrect old password for user: {}", username);
            return ResponseEntity.badRequest().body(Map.of("error", "旧密码错误"));
        }
        
        // 更新密码
        userService.updatePassword(user, newPassword);

        // BE-11：改密必须让既有登录态失效，否则旧凭证在令牌有效期内继续可用。
        // 1) 吊销全部 refresh token —— 各端下次刷新即失败，只能重新登录；
        // 2) 断开当前会话；3) 清 refresh_token cookie。
        int revoked = tokenService.revokeAllForUser(user.getId());
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.addHeader("Set-Cookie", ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build()
                .toString());

        log.info("Password changed successfully for user: {} (revoked {} refresh token(s))", username, revoked);

        return ResponseEntity.ok(Map.of("message", "密码修改成功，已注销全部登录设备，请重新登录"));
    }
    
    // ==================== 用户注册 ====================
    
    /**
     * 用户注册
     * @param body 注册信息（username, email, password）
     * @return 注册结果
     */
    @PostMapping("/users/register")
    public ResponseEntity<Map<String, Object>> registerUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email = body.get("email");
        String password = body.get("password");
        String phone = body.get("phone");
        String userType = body.getOrDefault("userType", "PERSONAL"); // 默认个人用户
        
        // 校验用户类型
        if (userType == null || (!userType.equals("PERSONAL") && !userType.equals("ENTERPRISE"))) {
            userType = "PERSONAL"; // 默认个人用户
        }
        
        log.debug("Registration request for username: {}, email: {}, userType: {}", username, email, userType);
        
        // 验证必要参数
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名不能为空"));
        }
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "邮箱不能为空"));
        }
        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码不能为空"));
        }
        
        // 验证用户名长度
        if (username.trim().length() < 3 || username.trim().length() > 50) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名长度必须在3-50个字符之间"));
        }
        
        // 验证邮箱格式
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "邮箱格式不正确"));
        }
        
        // 验证密码强度
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码至少6位"));
        }
        if (password.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码长度不能超过100位"));
        }

        // 禁用用户名检查
        if (SecurityConstants.FORBIDDEN_USERNAMES.contains(username.toLowerCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", SecurityConstants.ResponseMessages.FORBIDDEN_USERNAME));
        }

        // 弱密码黑名单
        if (SecurityConstants.WEAK_PASSWORDS.contains(password)) {
            return ResponseEntity.badRequest().body(Map.of("error", SecurityConstants.ResponseMessages.WEAK_PASSWORD));
        }

        // 用户名格式校验
        ValidationUtils.ValidationResult usernameVal = ValidationUtils.validateUsername(username);
        if (!usernameVal.isValid()) {
            return ResponseEntity.badRequest().body(Map.of("error", usernameVal.getMessage()));
        }

        // 验证手机号格式(如果提供)
        if (phone != null && !phone.trim().isEmpty()) {
            if (!phone.trim().matches("^1[3-9]\\d{9}$")) {
                return ResponseEntity.badRequest().body(Map.of("error", "手机号格式不正确"));
            }
            if (userService.findByPhone(phone.trim()).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "手机号已被注册"));
            }
        }
        
        // 检查用户名是否已存在
        if (userService.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已被使用"));
        }
        
        // 检查邮箱是否已存在
        if (userService.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "邮箱已被注册"));
        }
        
        // 创建用户
        UserEntity user = userService.createUser(username, email, password, "USER");

        // 设置用户类型（个人/企业）
        user.setUserType(userType);

        // 设置手机号(如果提供)
        if (phone != null && !phone.trim().isEmpty()) {
            user.setPhone(phone.trim());
        }
        userService.updateUser(user);
        
        log.info("User registered successfully: {}", username);
        
        Long userId = user.getId();
        String userName = user.getUsername();
        String userEmail = user.getEmail();
        
        return ResponseEntity.ok(Map.of(
            "message", "用户注册成功",
            "user", Map.of(
                "id", userId != null ? userId : 0L,
                "username", userName != null ? userName : "",
                "email", userEmail != null ? userEmail : "",
                "userType", userType != null ? userType : "PERSONAL"
            )
        ));
    }
}
