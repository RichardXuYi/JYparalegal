package com.jyfc.backend.core.security;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.module.auth.entity.AdminEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.AdminRepository;
import com.jyfc.backend.module.auth.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 用户上下文工具类，同时支持普通用户和管理员
 */
@Component
public class UserContextUtil {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    public UserContextUtil(UserRepository userRepository, AdminRepository adminRepository) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
    }

    /**
     * 获取当前登录用户ID（支持普通用户和管理员）
     */
    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("未登录");
        }
        String username = auth.getName();

        // 先查普通用户
        Optional<UserEntity> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            return userOpt.get().getId();
        }

        // 再查管理员
        Optional<AdminEntity> adminOpt = adminRepository.findByUsername(username);
        if (adminOpt.isPresent()) {
            return adminOpt.get().getId();
        }

        throw new UsernameNotFoundException("用户不存在: " + username);
    }

    /**
     * 获取当前登录的普通用户实体（非管理员）
     */
    public UserEntity getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("未登录");
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
    }

    /**
     * 获取当前登录用户实体（支持普通用户和管理员，返回 Object）
     */
    public Object getCurrentUserEntity() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("未登录");
        }
        String username = auth.getName();

        Optional<UserEntity> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            return userOpt.get();
        }

        Optional<AdminEntity> adminOpt = adminRepository.findByUsername(username);
        if (adminOpt.isPresent()) {
            return adminOpt.get();
        }

        throw new UsernameNotFoundException("用户不存在: " + username);
    }

    /**
     * 获取当前用户所属公司ID
     */
    public Long getCurrentCompanyId() {
        UserEntity user = getCurrentUser();
        if (user.getCompanyId() == null) {
            throw new RuntimeException("当前用户未关联企业");
        }
        return user.getCompanyId();
    }

    /**
     * 强制公司归属（签署等法律域功能专用）：取管理员授权给当前用户的公司 users.company_id
     * 为唯一权威来源，无默认兜底。未授权任何公司（或当前主体不是企业用户，如管理员）
     * → 抛 BusinessException，提示「你不是企业用户，请联系管理员注册，当前仅可使用 Agent」。
     */
    public Long getAuthorizedCompanyId() {
        Long cid = null;
        try {
            UserEntity user = getCurrentUser();
            cid = user.getCompanyId();
        } catch (RuntimeException ignored) {
            // 非企业用户主体（管理员）或上下文异常：视为无授权公司
        }
        if (cid == null) {
            throw new BusinessException("你不是企业用户，请联系管理员注册后使用签署等功能（当前仅可使用 Agent）");
        }
        return cid;
    }

    /**
     * 判断当前用户是否为管理员（AdminEntity）
     */
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        String username = auth.getName();
        return adminRepository.findByUsername(username).isPresent();
    }
}
