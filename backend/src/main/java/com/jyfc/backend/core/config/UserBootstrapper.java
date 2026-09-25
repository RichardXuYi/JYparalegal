package com.jyfc.backend.core.config;

import com.jyfc.backend.module.auth.entity.AdminEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.AdminRepository;
import com.jyfc.backend.module.auth.repository.UserRepository;
import com.jyfc.backend.module.tenant.service.TenantProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(1)
public class UserBootstrapper implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(UserBootstrapper.class);
    private final Environment environment;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantProvisioningService tenantProvisioningService;

    public UserBootstrapper(Environment environment, UserRepository userRepository, AdminRepository adminRepository, PasswordEncoder passwordEncoder, TenantProvisioningService tenantProvisioningService) {
        this.environment = environment;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantProvisioningService = tenantProvisioningService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String enabled = environment.getProperty("USER_BOOTSTRAP_ENABLED", "false");
        if (!"true".equalsIgnoreCase(enabled)) {
            return;
        }

        String username = environment.getProperty("USER_BOOTSTRAP_USERNAME");
        String password = environment.getProperty("USER_BOOTSTRAP_PASSWORD");
        String email = environment.getProperty("USER_BOOTSTRAP_EMAIL");
        String phone = environment.getProperty("USER_BOOTSTRAP_PHONE");
        String force = environment.getProperty("USER_BOOTSTRAP_FORCE", "false");

        if (username == null || username.trim().isEmpty()) {
            log.warn("USER_BOOTSTRAP_ENABLED=true but USER_BOOTSTRAP_USERNAME is not set");
            return;
        }
        
        if (password == null || password.trim().isEmpty()) {
            log.warn("USER_BOOTSTRAP_ENABLED=true but USER_BOOTSTRAP_PASSWORD is not set");
            return;
        }

        // 检查 username 是否已存在于 admins 表（不允许 admin 和 user 用户名重复）
        Optional<AdminEntity> existingAdmin = adminRepository.findByUsername(username.trim());
        if (existingAdmin.isPresent()) {
            log.error("Cannot create user '{}': username already exists in admins table. Admin and user usernames must be unique.", username);
            return;
        }
        
        if (email == null || email.trim().isEmpty()) {
            email = username.trim() + "@example.com";
        }

        Optional<UserEntity> userOpt = userRepository.findByUsername(username.trim());
        if (userOpt.isPresent()) {
            UserEntity existing = userOpt.get();
            ensureDefaults(existing);
            tenantProvisioningService.provisionPersonalUser(existing);
            // 只有当显式配置了 FORCE=true 时才覆盖现有数据
            if ("true".equalsIgnoreCase(force)) {
                log.info("User '{}' exists; FORCE reset enabled - updating password (DEV ONLY)", username);
                existing.setPassword(passwordEncoder.encode(password));
                existing.setEmailVerified(true);
                if (phone != null) existing.setPhone(phone);
                userRepository.save(existing);
                log.info("User '{}' password force-reset (DEV ONLY)", username);
            }
            return;
        }

        if (userRepository.findByEmail(email.trim()).isPresent()) {
             return;
        }

        UserEntity user = new UserEntity();
        user.setUsername(username.trim());
        user.setEmail(email.trim());
        if (phone != null) user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(1); // Active
        user.setEmailVerified(true); // Assume bootstrapped users are verified
        ensureDefaults(user);
        UserEntity saved = userRepository.save(user);
        tenantProvisioningService.provisionPersonalUser(saved);
    }

    private void ensureDefaults(UserEntity user) {
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
    }
}
