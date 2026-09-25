package com.jyfc.backend.module.integration.feishu.repository;

import com.jyfc.backend.module.integration.feishu.entity.UserFeishuBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserFeishuBindingRepository extends JpaRepository<UserFeishuBinding, Long> {
    Optional<UserFeishuBinding> findByUserId(Long userId);
    Optional<UserFeishuBinding> findByFeishuUnionId(String feishuUnionId);
    boolean existsByUserId(Long userId);
    boolean existsByFeishuUnionId(String feishuUnionId);
}
