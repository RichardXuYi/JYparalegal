package com.jyfc.backend.module.integration.dingtalk.repository;

import com.jyfc.backend.module.integration.dingtalk.entity.UserDingTalkBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserDingTalkBindingRepository extends JpaRepository<UserDingTalkBinding, Long> {
    Optional<UserDingTalkBinding> findByUserId(Long userId);
    Optional<UserDingTalkBinding> findByDingtalkUnionId(String dingtalkUnionId);
    boolean existsByUserId(Long userId);
    boolean existsByDingtalkUnionId(String dingtalkUnionId);
}
