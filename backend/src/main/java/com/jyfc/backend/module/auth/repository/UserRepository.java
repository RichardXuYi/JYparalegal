package com.jyfc.backend.module.auth.repository;

import com.jyfc.backend.module.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByPhone(String phone);
    long countByCreatedAtBefore(LocalDateTime dateTime);

    /**
     * 查询归属于某企业的成员（基于 users.company_id 反查）。
     * 用于 /api/organization/members 端点。
     */
    List<UserEntity> findByCompanyId(Long companyId);

    /**
     * 统计归属于某企业的成员数。
     */
    long countByCompanyId(Long companyId);
}
