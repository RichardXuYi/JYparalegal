package com.jyfc.backend.module.skill.repository;

import com.jyfc.backend.module.skill.entity.UserSkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSkillRepository extends JpaRepository<UserSkillEntity, Long> {

    List<UserSkillEntity> findByUserId(Long userId);

    Optional<UserSkillEntity> findByUserIdAndSlug(Long userId, String slug);

    void deleteByUserIdAndSlug(Long userId, String slug);
}
