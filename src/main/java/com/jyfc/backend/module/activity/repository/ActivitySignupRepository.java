package com.jyfc.backend.module.activity.repository;

import com.jyfc.backend.module.activity.entity.ActivitySignup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivitySignupRepository extends JpaRepository<ActivitySignup, Long> {

    Optional<ActivitySignup> findByActivityIdAndUserId(Long activityId, Long userId);

    List<ActivitySignup> findByActivityId(Long activityId);

    List<ActivitySignup> findByUserId(Long userId);

    long countByActivityIdAndStatusNot(Long activityId, Integer excludedStatus);

    long countByActivityId(Long activityId);

    boolean existsByActivityIdAndUserIdAndStatusNot(Long activityId, Long userId, Integer excludedStatus);
}