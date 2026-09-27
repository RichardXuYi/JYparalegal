package com.jyfc.backend.module.auth.service;

import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.UserRepository;
import com.jyfc.backend.module.tenant.service.TenantProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Integer ACTIVE_STATUS = 1;
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantProvisioningService tenantProvisioningService;
    
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       TenantProvisioningService tenantProvisioningService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantProvisioningService = tenantProvisioningService;
    }
    
    /**
     * 根据用户名查找用户
     * @param username 用户名
     * @return 用户实体（如果存在）
     */
    public Optional<UserEntity> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            log.warn("Attempt to find user with null or empty username");
            return Optional.empty();
        }
        log.debug("Finding user by username: {}", username);
        Optional<UserEntity> user = userRepository.findByUsername(username);
        user.ifPresentOrElse(
            u -> log.debug("Found user: {}", u.getUsername()),
            () -> log.debug("No user found with username: {}", username)
        );
        return user;
    }
    
    /**
     * 根据邮箱查找用户
     * @param email 邮箱地址
     * @return 用户实体（如果存在）
     */
    public Optional<UserEntity> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            log.warn("Attempt to find user with null or empty email");
            return Optional.empty();
        }
        log.debug("Finding user by email: {}", email);
        Optional<UserEntity> user = userRepository.findByEmail(email);
        user.ifPresentOrElse(
            u -> log.debug("Found user: {}", u.getEmail()),
            () -> log.debug("No user found with email: {}", email)
        );
        return user;
    }
    
    /**
     * 根据手机号查找用户
     */
    public Optional<UserEntity> findByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            log.warn("Attempt to find user with null or empty phone");
            return Optional.empty();
        }
        log.debug("Finding user by phone: {}", phone);
        return userRepository.findByPhone(phone);
    }
    
    public boolean validatePassword(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            log.warn("Attempt to validate password with null parameters");
            return false;
        }
        log.debug("validatePassword: rawPassword length={}, encodedPassword length={}", rawPassword.length(), encodedPassword.length());
        boolean isValid = passwordEncoder.matches(rawPassword, encodedPassword);
        log.info("Password validation result: {}", isValid);
        return isValid;
    }
    
    /**
     * 创建普通用户
     * @param username 用户名
     * @param email 邮箱
     * @param rawPassword 原始密码（未加密）
     * @param role 用户角色
     * @return 创建的用户实体
     */
    public UserEntity createUser(String username, String email, String rawPassword, String role) {
        if (username == null || email == null || rawPassword == null) {
            throw new IllegalArgumentException("username, email, and password must not be null");
        }
        log.debug("Creating user with username: {}, email: {}, role: {}", username, email, role);
        
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(ACTIVE_STATUS);
        
        // Role is implied as USER
        
        UserEntity savedUser = userRepository.save(user);
        log.debug("Created user with ID: {}", savedUser.getId());

        // 运行时租户供给：新用户默认绑定个人租户，否则法律域 requireTenant 会一律拒绝
        tenantProvisioningService.provisionPersonalUser(savedUser);

        return savedUser;
    }
    
    /**
     * 根据ID查找用户
     * @param id 用户ID
     * @return 用户实体（如果存在）
     */
    public Optional<UserEntity> findById(Long id) {
        if (id == null) {
            log.warn("Attempt to find user with null id");
            return Optional.empty();
        }
        return userRepository.findById(id);
    }
    
    /**
     * 更新用户信息
     */
    public UserEntity updateUser(UserEntity user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        log.debug("Updating user: {}", user.getUsername());
        return userRepository.save(user);
    }
    
    /**
     * 更新用户密码
     */
    public UserEntity updatePassword(UserEntity user, String newRawPassword) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (newRawPassword == null || newRawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        log.debug("Updating password for user: {}", user.getUsername());
        user.setPassword(passwordEncoder.encode(newRawPassword));
        return userRepository.save(user);
    }

    /**
     * 查询归属于某企业的成员列表（基于 users.company_id 反查）。
     * 用于 /api/organization/members 端点。
     *
     * @param companyId 企业 ID
     * @return 归属于该企业的用户列表（不含公司 owner，owner 由调用方单独处理）
     */
    public List<UserEntity> findByCompanyId(Long companyId) {
        if (companyId == null) {
            log.warn("Attempt to find users with null companyId");
            return List.of();
        }
        log.debug("Finding users by companyId: {}", companyId);
        return userRepository.findByCompanyId(companyId);
    }

    /**
     * 统计归属于某企业的成员数。
     */
    public long countByCompanyId(Long companyId) {
        if (companyId == null) {
            log.warn("Attempt to count users with null companyId");
            return 0L;
        }
        return userRepository.countByCompanyId(companyId);
    }
}