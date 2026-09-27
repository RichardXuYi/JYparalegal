package com.jyfc.backend.core.config;

import com.jyfc.backend.module.auth.entity.AdminEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.AdminRepository;
import com.jyfc.backend.module.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(0) // Run before UserBootstrapper
public class AdminBootstrapper implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapper.class);

    private final Environment environment;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapper(Environment environment, AdminRepository adminRepository, UserRepository userRepository) {
        this.environment = environment;
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public void run(ApplicationArguments args) {
        String enabled = environment.getProperty("ADMIN_BOOTSTRAP_ENABLED", "false");
        if (!"true".equalsIgnoreCase(enabled)) {
            log.info("AdminBootstrapper disabled (ADMIN_BOOTSTRAP_ENABLED != true)");
            return;
        }

        String username = environment.getProperty("ADMIN_BOOTSTRAP_USERNAME");
        String password = environment.getProperty("ADMIN_BOOTSTRAP_PASSWORD");
        String email = environment.getProperty("ADMIN_BOOTSTRAP_EMAIL");
        boolean force = "true".equalsIgnoreCase(environment.getProperty("ADMIN_BOOTSTRAP_FORCE", "false"));

        if (username == null || username.trim().isEmpty()) {
            log.warn("ADMIN_BOOTSTRAP_ENABLED=true but ADMIN_BOOTSTRAP_USERNAME is not set");
            return;
        }
        if (password == null || password.trim().isEmpty()) {
            log.warn("ADMIN_BOOTSTRAP_ENABLED=true but ADMIN_BOOTSTRAP_PASSWORD is not set; skipping admin bootstrap");
            return;
        }

        // 检查 username 是否已存在于 users 表（不允许 admin 和 user 用户名重复）
        Optional<UserEntity> existingUser = userRepository.findByUsername(username.trim());
        if (existingUser.isPresent()) {
            log.error("Cannot create admin '{}': username already exists in users table. Admin and user usernames must be unique.", username);
            return;
        }

        if (email == null || email.trim().isEmpty()) {
            email = username.trim() + "@jyfc.admin";
        }

        // If admin already exists, only update when force=true (DEV/TEST only).
        Optional<AdminEntity> adminOpt = adminRepository.findByUsername(username.trim());
        if (adminOpt.isPresent()) {
            AdminEntity existing = adminOpt.get();
            if (force) {
                log.warn("Admin '{}' exists; FORCE reset enabled - updating password/email (DEV ONLY)", username);
                existing.setPassword(passwordEncoder.encode(password));
                if (email != null && !email.trim().isEmpty()) {
                    existing.setEmail(email.trim());
                }
                adminRepository.save(existing);
                log.info("Admin '{}' password force-reset (DEV ONLY)", username);
            } else {
                log.info("Admin '{}' already exists, skipping (FORCE disabled)", username);
            }
            return;
        }

        AdminEntity admin = new AdminEntity();
        admin.setUsername(username.trim());
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole("SUPER_ADMIN");
        admin.setStatus(1);
        adminRepository.save(admin);
        log.info("Admin '{}' created successfully", username);
    }
}