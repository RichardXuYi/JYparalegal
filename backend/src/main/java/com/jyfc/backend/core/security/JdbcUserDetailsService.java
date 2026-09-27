package com.jyfc.backend.core.security;

import com.jyfc.backend.module.auth.entity.AdminEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.AdminRepository;
import com.jyfc.backend.module.auth.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class JdbcUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    public JdbcUserDetailsService(UserRepository userRepository, AdminRepository adminRepository) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        // 1. 先查管理员表 — 如果同名账号同时存在于 users 和 admins 表，
        //    管理员身份优先，否则 admin-login 登录后鉴权拿到 ROLE_USER 导致 403
        Optional<AdminEntity> adminOpt = adminRepository.findByUsername(identifier);
        if (adminOpt.isEmpty()) {
            adminOpt = adminRepository.findByEmail(identifier);
        }

        if (adminOpt.isPresent()) {
            AdminEntity admin = adminOpt.get();
            boolean enabled = admin.getStatus() == null || admin.getStatus() == 1;
            String roleName = admin.getRole();
            if (roleName == null || roleName.isBlank()) {
                roleName = "ADMIN";
            }
            if (!roleName.startsWith("ROLE_")) {
                roleName = "ROLE_" + roleName;
            }
            return new User(
                    admin.getUsername(),
                    admin.getPassword() != null ? admin.getPassword() : "",
                    enabled,
                    true,
                    true,
                    !isLocked(admin.getStatus()),
                    List.of(new SimpleGrantedAuthority(roleName))
            );
        }

        // 2. 再查普通用户表 (支持 username / email / phone)
        Optional<UserEntity> userOpt = userRepository.findByUsername(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(identifier);
        }
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByPhone(identifier);
        }

        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            boolean enabled = user.getStatus() == null || user.getStatus() == 1;
            return new User(
                    user.getUsername(),
                    user.getPassword() != null ? user.getPassword() : "",
                    enabled,
                    true, // accountNonExpired
                    true, // credentialsNonExpired
                    !isLocked(user.getStatus()), // accountNonLocked
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
        }

        throw new UsernameNotFoundException("用户不存在: " + identifier);
    }

    private boolean isLocked(Integer status) {
        return status != null && status == 0;
    }
}
