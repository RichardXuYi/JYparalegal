package com.jyfc.backend.module.auth.service;

import com.jyfc.backend.module.auth.entity.AdminEntity;
import com.jyfc.backend.module.auth.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final Integer ACTIVE_STATUS = 1;

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public Optional<AdminEntity> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            log.warn("Attempt to find admin with null or empty username");
            return Optional.empty();
        }
        return adminRepository.findByUsername(username);
    }

    public Optional<AdminEntity> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            log.warn("Attempt to find admin with null or empty email");
            return Optional.empty();
        }
        return adminRepository.findByEmail(email);
    }

    public Optional<AdminEntity> findById(Long id) {
        if (id == null) {
            log.warn("Attempt to find admin with null id");
            return Optional.empty();
        }
        return adminRepository.findById(id);
    }

    public boolean validatePassword(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public AdminEntity createAdmin(String username, String email, String rawPassword, String role) {
        if (username == null || email == null || rawPassword == null) {
            throw new IllegalArgumentException("username, email, and password must not be null");
        }
        log.debug("Creating admin with username: {}, email: {}, role: {}", username, email, role);

        AdminEntity admin = new AdminEntity();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setStatus(ACTIVE_STATUS);
        admin.setRole(role != null ? role : "ADMIN");

        AdminEntity saved = adminRepository.save(admin);
        log.info("Created admin with ID: {}", saved.getId());
        return saved;
    }

    public AdminEntity updateAdmin(AdminEntity admin) {
        if (admin == null) {
            throw new IllegalArgumentException("Admin cannot be null");
        }
        log.debug("Updating admin: {}", admin.getUsername());
        return adminRepository.save(admin);
    }

    public AdminEntity updatePassword(AdminEntity admin, String newRawPassword) {
        if (admin == null) {
            throw new IllegalArgumentException("Admin cannot be null");
        }
        if (newRawPassword == null || newRawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        admin.setPassword(passwordEncoder.encode(newRawPassword));
        return adminRepository.save(admin);
    }
}
