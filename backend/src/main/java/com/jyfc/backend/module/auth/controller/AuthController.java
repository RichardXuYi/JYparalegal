package com.jyfc.backend.module.auth.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.auth.dto.AuthDeviceDTO;
import com.jyfc.backend.module.auth.dto.SendSmsCodeRequest;
import com.jyfc.backend.module.auth.dto.SmsLoginRequest;
import com.jyfc.backend.module.auth.entity.AdminEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.core.security.SecurityConstants;
import com.jyfc.backend.core.security.ValidationUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import com.jyfc.backend.module.auth.service.UserService;
import com.jyfc.backend.module.auth.service.AdminService;
import com.jyfc.backend.module.auth.service.TokenService;
import com.jyfc.backend.module.auth.service.RegistrationService;
import com.jyfc.backend.module.dashboard.service.SecurityCheckService;
import com.jyfc.backend.module.auth.service.EmailVerificationService;
import com.jyfc.backend.module.dashboard.service.SecurityAuditLogService;
import com.jyfc.backend.module.auth.service.SmsVerificationService;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "认证授权", description = "用户登录、注册、会话管理API")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final AdminService adminService;
    private final SecurityCheckService securityCheckService;
    private final EmailVerificationService emailVerificationService;
    private final SecurityAuditLogService auditLogService;
    private final AuthenticationManager authenticationManager;
    private final SmsVerificationService smsVerificationService;
    private final TokenService tokenService;
    private final RegistrationService registrationService;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Autowired
    public AuthController(
            UserService userService,
            AdminService adminService,
            SecurityCheckService securityCheckService,
            EmailVerificationService emailVerificationService,
            SecurityAuditLogService auditLogService,
            AuthenticationManager authenticationManager,
            SmsVerificationService smsVerificationService,
            TokenService tokenService,
            RegistrationService registrationService
    ) {
        this.userService = userService;
        this.adminService = adminService;
        this.securityCheckService = securityCheckService;
        this.emailVerificationService = emailVerificationService;
        this.auditLogService = auditLogService;
        this.authenticationManager = authenticationManager;
        this.smsVerificationService = smsVerificationService;
        this.tokenService = tokenService;
        this.registrationService = registrationService;
    }

    // ==================== User Login ====================

    /** 构建 refresh token 的 HttpOnly cookie */
    private ResponseCookie buildRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(7 * 24 * 60 * 60)
                .build();
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request,
            HttpServletResponse response) {
        return processLogin(body, false, request, response);
    }

    // Support form-urlencoded for legacy/simple clients if needed, mapped to same logic
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> loginForm(
            @RequestParam Map<String, String> params,
            HttpServletRequest request,
            HttpServletResponse response) {
        // Convert String map to Object map
        Map<String, Object> body = new HashMap<>(params);
        return processLogin(body, false, request, response);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> processLogin(Map<String, Object> body, boolean isAdmin, HttpServletRequest request, HttpServletResponse response) {
        String identifier = (String) body.get("identifier");
        if (identifier == null) identifier = (String) body.get("username");
        if (identifier == null) identifier = (String) body.get("email");
        if (identifier == null && !isAdmin) identifier = (String) body.get("phone");

        String password = (String) body.get("password");
        String ipAddress = getClientIp();
        String userAgent = request.getHeader("User-Agent");
        String deviceName = (String) body.get("deviceName");
        String deviceFingerprint = (String) body.get("deviceFingerprint");

        log.info("{} login attempt for identifier: {}", isAdmin ? "Admin" : "User", identifier);

        if (identifier == null || (password == null || password.trim().isEmpty())) {
            auditLogService.logLoginFailure(identifier, ipAddress, userAgent, deviceFingerprint, "Missing credentials");
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "用户名或密码不能为空"));
        }

        // Security Checks
        if (securityCheckService.exceedsLoginRateLimit(ipAddress)) {
            auditLogService.logSuspiciousActivity(identifier, ipAddress, userAgent, deviceFingerprint, "Login rate limit exceeded for IP");
            return ResponseEntity.status(429).body(ApiResponse.error(429, SecurityConstants.ResponseMessages.RATE_LIMIT_EXCEEDED));
        }

        if (securityCheckService.isAccountLocked(identifier)) {
            auditLogService.logLoginFailure(identifier, ipAddress, userAgent, deviceFingerprint, "Account is locked");
            return ResponseEntity.status(403).body(ApiResponse.error(403, SecurityConstants.ResponseMessages.ACCOUNT_LOCKED));
        }

        if (isAdmin) {
            return performAdminLogin(identifier, password, ipAddress, userAgent, deviceFingerprint);
        } else {
            return performUserLogin(identifier, password, ipAddress, userAgent, deviceFingerprint, deviceName, response);
        }
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> performUserLogin(String identifier, String password, String ip, String ua, String fingerprint, String deviceName, HttpServletResponse response) {
        Optional<UserEntity> userOpt = findUserByIdentifier(identifier);
        
        if (userOpt.isPresent() && securityCheckService.detectMultiAccountLoginFromIP(ip, identifier)) {
             auditLogService.logSuspiciousActivity(identifier, ip, ua, fingerprint, "Multiple account login detected from same IP");
        }

        if (userOpt.isEmpty()) {
            securityCheckService.recordLoginFailure(identifier);
            auditLogService.logLoginFailure(identifier, ip, ua, fingerprint, "User not found");
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "用户名或密码错误"));
        }

        UserEntity user = userOpt.get();
        if (!userService.validatePassword(password, user.getPassword())) {
            handleLoginFailure(identifier, ip, ua, fingerprint);
            // 与"用户不存在"分支返回完全一致的文案，避免通过响应差异枚举有效账号。
            // 剩余尝试次数仅记入服务端日志/审计，不回传给客户端。
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "用户名或密码错误"));
        }

        // Login Success
        securityCheckService.clearLoginFailures(identifier);
        user.setLastLoginIp(ip);
        user.setLastLoginTime(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        userService.updateUser(user);

        auditLogService.logLoginSuccess(identifier, ip, ua, fingerprint);

        // 使用 Spring Security 认证 (自动写入 HttpSession，由 Spring Session 持久化到 Redis)
        // 密码已在上方 validatePassword 中验证通过，直接构建已认证的 Authentication 对象
        // 避免 authenticationManager.authenticate() 再次通过 UserDetailsService 校验密码
        UsernamePasswordAuthenticationToken authToken = UsernamePasswordAuthenticationToken.authenticated(
                user.getUsername(), password, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // 强制创建/刷新 session，确保证书被持久化
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest httpRequest = attrs.getRequest();
        // 防 session fixation: 先废弃任何已有的 session，再创建新的
        HttpSession existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            try {
                existingSession.invalidate();
            } catch (IllegalStateException ignored) {
                // 已被并发请求失效，忽略
            }
        }
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

        // 跨端 Token：在保留 Session Cookie 的同时下发 access/refresh token（供 Studio 等桌面端使用）
        TokenService.TokenPair tokens = tokenService.issueForUser(user, deviceName, ip, ua);
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", user.getId() != null ? user.getId() : 0L);
        userInfo.put("username", user.getUsername() != null ? user.getUsername() : "");
        userInfo.put("email", user.getEmail() != null ? user.getEmail() : "");
        userInfo.put("role", "USER");
        userInfo.put("roles", List.of("USER"));
        result.put("user", userInfo);
        result.put("accessToken", tokens.accessToken());
        result.put("refreshToken", tokens.refreshToken());
        result.put("tokenType", "Bearer");
        result.put("expiresIn", tokens.expiresIn());
        result.put("deviceId", tokens.deviceId());

        // M-33: 将 refresh token 设置为 HttpOnly cookie，前端不再持久化
        response.addHeader("Set-Cookie", buildRefreshTokenCookie(tokens.refreshToken()).toString());

        return ResponseEntity.ok(ApiResponse.success("登录成功", result));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> performAdminLogin(String identifier, String password, String ip, String ua, String fingerprint) {
        Optional<AdminEntity> adminOpt = findAdminByIdentifier(identifier);

        if (adminOpt.isEmpty()) {
            securityCheckService.recordLoginFailure(identifier); // 复用简单检查
            return ResponseEntity.status(401).body(ApiResponse.error(401, "用户名或密码错误"));
        }

        AdminEntity admin = adminOpt.get();
        if (!adminService.validatePassword(password, admin.getPassword())) {
             securityCheckService.recordLoginFailure(identifier);
             return ResponseEntity.status(401).body(ApiResponse.error(401, "用户名或密码错误"));
        }

        securityCheckService.clearLoginFailures(identifier);

        String adminRole = admin.getRole();
        if (adminRole == null || adminRole.isBlank()) adminRole = "ADMIN";
        if (!adminRole.startsWith("ROLE_")) adminRole = "ROLE_" + adminRole;

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                admin.getUsername(), password, List.of(new SimpleGrantedAuthority(adminRole)));
        Authentication auth = authenticationManager.authenticate(authToken);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 强制创建/刷新 session，确保证书被持久化
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest httpRequest = attrs.getRequest();
        // 防 session fixation: 先废弃任何已有的 session，再创建新的
        HttpSession existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            try {
                existingSession.invalidate();
            } catch (IllegalStateException ignored) {
                // 已被并发请求失效，忽略
            }
        }
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        
        log.info("Admin logged in successfully: {}", admin.getUsername());

        Map<String, Object> adminInfo = new LinkedHashMap<>();
        adminInfo.put("id", admin.getId() != null ? admin.getId() : 0L);
        adminInfo.put("username", admin.getUsername() != null ? admin.getUsername() : "");
        adminInfo.put("email", admin.getEmail() != null ? admin.getEmail() : "");
        adminInfo.put("role", adminRole);
        adminInfo.put("roles", List.of(adminRole));

        return ResponseEntity.ok(ApiResponse.success("管理员登录成功", Map.of("user", adminInfo)));
    }

    private void handleLoginFailure(String identifier, String ip, String ua, String fingerprint) {
        securityCheckService.recordLoginFailure(identifier);
        securityCheckService.recordLoginAttempt(ip, identifier, false);
        auditLogService.logLoginFailure(identifier, ip, ua, fingerprint, "Invalid password");
    }

    // ==================== Admin Login ====================

    @PostMapping("/admin-login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminLogin(@RequestBody Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
        return processLogin(body, true, request, response);
    }
    
    @PostMapping(value = "/admin-login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminLoginForm(@RequestParam Map<String, String> params, HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> body = new HashMap<>(params);
        return processLogin(body, true, request, response);
    }

    // ==================== SMS Login & Verification ====================

    @PostMapping("/send-sms-code")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendSmsCode(@Valid @RequestBody SendSmsCodeRequest request) {
        String phone = request.getPhone();

        // BE-007/BE-009: 按 IP 限流，防止利用发送接口刷量/短信轰炸
        String sendIp = getClientIp();
        if (smsVerificationService.exceedsSendIpLimit(sendIp)) {
            return ResponseEntity.status(429).body(ApiResponse.error(429, SecurityConstants.ResponseMessages.RATE_LIMIT_EXCEEDED));
        }

        try {
            String message = smsVerificationService.generateAndSendCode(phone);
            return ResponseEntity.ok(ApiResponse.success(Map.of("message", message)));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to send SMS code", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "验证码发送失败, 请稍后重试"));
        }
    }

    @PostMapping("/login-sms")
    public ResponseEntity<ApiResponse<Map<String, Object>>> loginWithSms(@Valid @RequestBody SmsLoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        String phone = request.getPhone();
        String code = request.getCode();
        String ip = getClientIp();
        String ua = httpRequest.getHeader("User-Agent");

        // BE-007: 按 IP 限流，防止验证码枚举/爆破
        if (smsVerificationService.exceedsVerifyIpLimit(ip)) {
            return ResponseEntity.status(429).body(ApiResponse.error(429, SecurityConstants.ResponseMessages.RATE_LIMIT_EXCEEDED));
        }

        if (!smsVerificationService.verifyCode(phone, code)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "验证码错误或已过期"));
        }

        Optional<UserEntity> userOpt = userService.findByPhone(phone);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "该手机号尚未注册, 请先注册"));
        }

        UserEntity user = userOpt.get();
        // Update login info
        user.setLastLoginIp(ip);
        user.setLastLoginTime(LocalDateTime.now());
        userService.updateUser(user);

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                user.getUsername(), "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication auth = authenticationManager.authenticate(authToken);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 防 session fixation: 先废弃任何已有的 session，再创建新的
        ServletRequestAttributes smsAttrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest smsHttpRequest = smsAttrs.getRequest();
        HttpSession smsExistingSession = smsHttpRequest.getSession(false);
        if (smsExistingSession != null) {
            try {
                smsExistingSession.invalidate();
            } catch (IllegalStateException ignored) {
                // 已被并发请求失效，忽略
            }
        }
        HttpSession smsSession = smsHttpRequest.getSession(true);
        smsSession.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

        log.info("User logged in via SMS: {}", user.getUsername());

        // 跨端 Token：与密码登录对齐，下发 access/refresh token
        TokenService.TokenPair tokens = tokenService.issueForUser(user, null, ip, ua);

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", user.getId() != null ? user.getId() : 0L);
        userInfo.put("username", user.getUsername() != null ? user.getUsername() : "");
        userInfo.put("email", user.getEmail() != null ? user.getEmail() : "");
        userInfo.put("role", "USER");
        userInfo.put("roles", List.of("USER"));
        result.put("user", userInfo);
        result.put("accessToken", tokens.accessToken());
        result.put("refreshToken", tokens.refreshToken());
        result.put("tokenType", "Bearer");
        result.put("expiresIn", tokens.expiresIn());
        result.put("deviceId", tokens.deviceId());

        // M-33: 将 refresh token 设置为 HttpOnly cookie
        response.addHeader("Set-Cookie", buildRefreshTokenCookie(tokens.refreshToken()).toString());

        return ResponseEntity.ok(ApiResponse.success("登录成功", result));
    }

    // ==================== Email Verification ====================

    @PostMapping("/send-email-verification")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendEmailVerification(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String ip = getClientIp();
        String ua = body.get("userAgent");

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "邮箱不能为空"));
        }

        if (!ValidationUtils.isValidEmail(email)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "邮箱格式不正确"));
        }

        try {
            emailVerificationService.sendVerificationCode(email);
            auditLogService.logSecurityEvent(
                SecurityConstants.AuditEventType.EMAIL_VERIFICATION_SENT, "unknown", ip, ua, null,
                "Email verification code sent to: " + email, "success", null, "low"
            );
            return ResponseEntity.ok(ApiResponse.success(Map.of("message", "验证码已发送到邮箱，请在" + SecurityConstants.EMAIL_VERIFICATION_EXPIRY + "分钟内验证")));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to send email verification", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "发送失败，请稍后重试"));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyEmail(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        String ip = getClientIp();
        String ua = body.get("userAgent");

        if (email == null || code == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "邮箱和验证码不能为空"));
        }

        if (!ValidationUtils.isValidVerificationCode(code)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "验证码格式不正确"));
        }

        if (!emailVerificationService.verifyCode(email, code)) {
            auditLogService.logSecurityEvent(
                SecurityConstants.AuditEventType.SUSPICIOUS_ACTIVITY, "unknown", ip, ua, null,
                "Invalid email verification code for: " + email, "failure", "Invalid code", "medium"
            );
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "验证码错误或已过期"));
        }

        emailVerificationService.markEmailAsVerified(email);
        
        auditLogService.logSecurityEvent(
            SecurityConstants.AuditEventType.EMAIL_VERIFIED, "unknown", ip, ua, null,
            "Email verified: " + email, "success", null, "low"
        );
        
        return ResponseEntity.ok(ApiResponse.success("邮箱验证成功", Map.of("message", "邮箱验证成功")));
    }

    // ==================== Registration ====================

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String email = (String) body.get("email");
        String password = (String) body.get("password");
        String phone = (String) body.get("phone");
        String ua = (String) body.get("userAgent");
        String fingerprint = (String) body.get("deviceFingerprint");
        String smsCode = (String) body.get("smsCode");
        String ip = getClientIp();

        log.info("Registration request for username: {}, email: {}", username, email);

        // Basic Validation
        if (username == null || username.trim().isEmpty()) return badRequest(username, ip, ua, fingerprint, "Username is empty");
        if (password == null || password.isEmpty()) return badRequest(username, ip, ua, fingerprint, "Password is empty");
        if (phone == null || phone.trim().isEmpty()) return badRequest(username, ip, ua, fingerprint, "Phone is empty");

        ValidationUtils.ValidationResult usernameVal = ValidationUtils.validateUsername(username);
        if (!usernameVal.isValid()) return badRequest(username, ip, ua, fingerprint, usernameVal.getMessage());

        ValidationUtils.ValidationResult passwordVal = ValidationUtils.validatePassword(password);
        if (!passwordVal.isValid()) return badRequest(username, ip, ua, fingerprint, passwordVal.getMessage());

        // Email Validation (if provided)
        if (email != null && !email.trim().isEmpty()) {
            if (!ValidationUtils.isValidEmail(email)) return badRequest(username, ip, ua, fingerprint, "Invalid email format");
            if (!emailVerificationService.isEmailVerified(email)) return badRequest(username, ip, ua, fingerprint, "请先验证邮箱");
        }

        // Security Checks
        if (securityCheckService.exceedsRegistrationRateLimit(ip)) {
            auditLogService.logSuspiciousActivity(username, ip, ua, fingerprint, "Registration rate limit exceeded for IP");
            return ResponseEntity.status(429).body(ApiResponse.error(429, "注册过于频繁，请稍后再试"));
        }

        // Duplicate Checks
        if (userService.findByUsername(username).isPresent()) return badRequest(username, ip, ua, fingerprint, "用户名已被使用");
        if (email != null && !email.trim().isEmpty() && userService.findByEmail(email).isPresent()) return badRequest(username, ip, ua, fingerprint, "邮箱已被注册");
        
        // Phone Validation
        if (!ValidationUtils.isValidPhone(phone)) return badRequest(username, ip, ua, fingerprint, "手机号格式不正确");
        if (userService.findByPhone(phone.trim()).isPresent()) return badRequest(username, ip, ua, fingerprint, "手机号已被注册");
        
        // SMS Code Verification
        if (smsCode == null || smsCode.trim().isEmpty()) return badRequest(username, ip, ua, fingerprint, "请输入手机验证码");
        if (!smsVerificationService.verifyCode(phone, smsCode)) return badRequest(username, ip, ua, fingerprint, "手机验证码错误或已过期");

        try {
            UserEntity user = userService.createUser(username, email, password, "USER");
            user.setEmailVerified(true);
            user.setLastLoginIp(ip);
            if (phone != null) user.setPhone(phone.trim());
            
            userService.updateUser(user);
            
            if (email != null && !email.trim().isEmpty()) emailVerificationService.clearEmailVerification(email);
            
            auditLogService.logRegistration(username, ip, ua, fingerprint, true, null);
            log.info("User registered successfully: {}", username);
            
            Map<String, Object> regResult = new LinkedHashMap<>();
            regResult.put("id", user.getId() != null ? user.getId() : 0L);
            regResult.put("username", user.getUsername());
            regResult.put("email", user.getEmail());
            return ResponseEntity.ok(ApiResponse.success("注册成功！", regResult));
        } catch (Exception e) {
            log.error("Registration failed for user: {}", username, e);
            auditLogService.logRegistration(username, ip, ua, fingerprint, false, e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "注册失败，请稍后重试"));
        }
    }

    /**
     * 轻量注册（桌面端）：仅手机号 + 密码；企业另需公司名（信用代码选填）。
     * 不做短信/邮箱验证。注册后默认 FREE 套餐（未购买，法律域由后续门禁拦截）。
     * 注册成功不直接下发 token，前端用手机号+密码走 /login 自动登录。
     */
    @PostMapping("/register-simple")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerSimple(@RequestBody Map<String, Object> body) {
        String phone = body.get("phone") == null ? null : String.valueOf(body.get("phone")).trim();
        String password = (String) body.get("password");
        String userType = body.get("userType") == null ? "PERSONAL" : String.valueOf(body.get("userType"));
        String ip = getClientIp();

        if (phone == null || phone.isEmpty()) return badRequest(phone, ip, null, null, "手机号不能为空");
        if (!ValidationUtils.isValidPhone(phone)) return badRequest(phone, ip, null, null, "手机号格式不正确");
        if (password == null || password.length() < 6) return badRequest(phone, ip, null, null, "密码至少 6 位");
        if (userService.findByPhone(phone).isPresent() || userService.findByUsername(phone).isPresent()) {
            return badRequest(phone, ip, null, null, "手机号已被注册");
        }

        boolean enterprise = "ENTERPRISE".equalsIgnoreCase(userType);
        String companyName = body.get("companyName") == null ? null : String.valueOf(body.get("companyName")).trim();
        String unifiedCreditCode = body.get("unifiedCreditCode") == null ? null : String.valueOf(body.get("unifiedCreditCode")).trim();
        String legalPerson = body.get("legalPerson") == null ? null : String.valueOf(body.get("legalPerson")).trim();
        String contactEmail = body.get("contactEmail") == null ? null : String.valueOf(body.get("contactEmail")).trim();
        if (enterprise && (companyName == null || companyName.isEmpty())) {
            return badRequest(phone, ip, null, null, "请填写公司名称");
        }

        try {
            RegistrationService.Result r = registrationService.registerSimple(
                    phone, password, enterprise, companyName, unifiedCreditCode, legalPerson, contactEmail);
            auditLogService.logRegistration(phone, ip, null, null, true, null);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("id", r.userId());
            res.put("username", r.username());
            res.put("userType", r.userType());
            res.put("companyId", r.companyId());
            return ResponseEntity.ok(ApiResponse.success("注册成功", res));
        } catch (IllegalArgumentException e) {
            return badRequest(phone, ip, null, null, e.getMessage());
        } catch (Exception e) {
            log.error("Simple registration failed for phone: {}", phone, e);
            auditLogService.logRegistration(phone, ip, null, null, false, e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "注册失败，请稍后重试"));
        }
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> badRequest(String username, String ip, String ua, String fingerprint, String msg) {
        auditLogService.logRegistration(username, ip, ua, fingerprint, false, msg);
        return ResponseEntity.badRequest().body(ApiResponse.error(400, msg));
    }

    // ==================== Token Refresh ====================

    /**
     * 用 refresh token 轮换出新的 access/refresh token（跨端 Token 认证）。
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(
            @CookieValue(name = "refresh_token", required = false) String cookieRefreshToken,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletResponse response) {
        // M-33: 优先从 HttpOnly cookie 读取，fallback 到 body（过渡期兼容）
        String refreshToken = cookieRefreshToken;
        if (refreshToken == null && body != null) {
            refreshToken = body.get("refreshToken");
        }
        if (refreshToken == null) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "refresh token 缺失"));
        }
        return tokenService.rotate(refreshToken)
                .<ResponseEntity<ApiResponse<Map<String, Object>>>>map(tokens -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("accessToken", tokens.accessToken());
                    result.put("tokenType", "Bearer");
                    result.put("expiresIn", tokens.expiresIn());
                    result.put("deviceId", tokens.deviceId());
                    // M-33: 设置新的 refresh token cookie，不再在 body 中返回
                    response.addHeader("Set-Cookie", buildRefreshTokenCookie(tokens.refreshToken()).toString());
                    return ResponseEntity.ok(ApiResponse.success("刷新成功", result));
                })
                .orElseGet(() -> ResponseEntity.status(401).body(ApiResponse.error(401, "refresh token 无效或已过期")));
    }

    // ==================== Logout ====================

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logout(
            HttpServletRequest request,
            @CookieValue(name = "refresh_token", required = false) String cookieRefreshToken,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletResponse response) {
        // M-33: 从 cookie 或 body 读取 refresh token 并吊销
        String refreshToken = cookieRefreshToken;
        if (refreshToken == null && body != null) {
            refreshToken = body.get("refreshToken");
        }
        if (refreshToken != null) {
            tokenService.revoke(refreshToken);
        }
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        // M-33: 清除 refresh token cookie
        ResponseCookie clearCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", clearCookie.toString());
        log.info("User/Admin logged out successfully");
        return ResponseEntity.ok(ApiResponse.success("登出成功", Map.of("message", "登出成功")));
    }

    /**
     * 获取当前登录用户信息 (用于前端验证 session 有效性)
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录"));
        }

        String username = auth.getName();
        Optional<UserEntity> userOpt = userService.findByUsername(username);
        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            Map<String, Object> userMap = new LinkedHashMap<>();
            userMap.put("id", user.getId() != null ? user.getId() : 0L);
            userMap.put("username", user.getUsername() != null ? user.getUsername() : "");
            userMap.put("email", user.getEmail() != null ? user.getEmail() : "");
            userMap.put("phone", user.getPhone() != null ? user.getPhone() : "");
            userMap.put("role", "USER");
            userMap.put("roles", List.of("USER"));
            return ResponseEntity.ok(ApiResponse.success("已登录", Map.of("user", userMap)));
        }

        Optional<AdminEntity> adminOpt = adminService.findByUsername(username);
        if (adminOpt.isPresent()) {
            AdminEntity admin = adminOpt.get();
            Map<String, Object> adminMap = new LinkedHashMap<>();
            adminMap.put("id", admin.getId() != null ? admin.getId() : 0L);
            adminMap.put("username", admin.getUsername() != null ? admin.getUsername() : "");
            adminMap.put("email", admin.getEmail() != null ? admin.getEmail() : "");
            String role = admin.getRole() != null ? admin.getRole() : "ADMIN";
            adminMap.put("role", role);
            adminMap.put("roles", List.of(role));
            return ResponseEntity.ok(ApiResponse.success("已登录", Map.of("user", adminMap)));
        }

        return ResponseEntity.status(401).body(ApiResponse.error(401, "用户不存在"));
    }

    // ==================== Device Management ====================

    /**
     * 获取当前用户的已登录设备列表。
     */
    @GetMapping("/devices")
    public ResponseEntity<ApiResponse<List<AuthDeviceDTO>>> listDevices() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录"));
        }
        String username = auth.getName();
        Optional<UserEntity> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "用户不存在"));
        }
        List<AuthDeviceDTO> devices = tokenService.listActiveDevices(userOpt.get().getId());
        return ResponseEntity.ok(ApiResponse.success("获取设备列表成功", devices));
    }

    /**
     * 主动断开指定设备的连接（吊销其 refresh token）。
     */
    @DeleteMapping("/devices/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeDevice(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录"));
        }
        String username = auth.getName();
        Optional<UserEntity> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "用户不存在"));
        }
        boolean revoked = tokenService.revokeDevice(id, userOpt.get().getId());
        if (!revoked) {
            return ResponseEntity.status(404).body(ApiResponse.error(404, "设备不存在或已失效"));
        }
        log.info("User {} revoked device id={}", username, id);
        Map<String, Object> deviceResult = new LinkedHashMap<>();
        deviceResult.put("id", id != null ? id : 0L);
        return ResponseEntity.ok(ApiResponse.success("设备已断开", deviceResult));
    }

    // ==================== Helper Methods ====================

    private String getClientIp() {
        String ip = com.jyfc.backend.core.config.RequestUtils.getClientIpAddress();
        return ip != null ? ip : "127.0.0.1";
    }

    private Optional<UserEntity> findUserByIdentifier(String identifier) {
        Optional<UserEntity> userOpt = userService.findByUsername(identifier);
        if (userOpt.isPresent()) return userOpt;
        userOpt = userService.findByEmail(identifier);
        if (userOpt.isPresent()) return userOpt;
        return userService.findByPhone(identifier);
    }
    
    private Optional<AdminEntity> findAdminByIdentifier(String identifier) {
        Optional<AdminEntity> adminOpt = adminService.findByUsername(identifier);
        if (adminOpt.isPresent()) return adminOpt;
        return adminService.findByEmail(identifier);
    }
}
