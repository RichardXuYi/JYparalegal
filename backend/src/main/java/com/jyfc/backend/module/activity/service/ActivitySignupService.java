package com.jyfc.backend.module.activity.service;

import com.jyfc.backend.module.activity.entity.ActivitySignup;
import com.jyfc.backend.module.activity.repository.ActivitySignupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ActivitySignupService {

    private final ActivitySignupRepository signupRepository;

    public ActivitySignupService(ActivitySignupRepository signupRepository) {
        this.signupRepository = signupRepository;
    }

    /**
     * 报名
     */
    @Transactional
    public ActivitySignup signup(Long activityId, Long userId, Integer maxParticipants) {
        if (activityId == null || userId == null) {
            throw new IllegalArgumentException("activityId and userId must not be null");
        }
        Optional<ActivitySignup> existing = signupRepository.findByActivityIdAndUserId(activityId, userId);
        if (existing.isPresent()) {
            ActivitySignup s = existing.get();
            if (s.getStatus() != null && s.getStatus() == ActivitySignup.STATUS_CANCELLED) {
                // 重新报名
                s.setStatus(ActivitySignup.STATUS_SIGNED_UP);
                s.setSignupTime(LocalDateTime.now());
                s.setSigninTime(null);
                return signupRepository.save(s);
            }
            throw new IllegalStateException("您已报名该活动");
        }

        // 校验人数上限
        if (maxParticipants != null && maxParticipants > 0) {
            long activeCount = signupRepository.countByActivityIdAndStatusNot(activityId, ActivitySignup.STATUS_CANCELLED);
            if (activeCount >= maxParticipants) {
                throw new IllegalStateException("活动报名人数已满");
            }
        }

        ActivitySignup signup = new ActivitySignup();
        signup.setActivityId(activityId);
        signup.setUserId(userId);
        signup.setStatus(ActivitySignup.STATUS_SIGNED_UP);
        signup.setSignupTime(LocalDateTime.now());
        return signupRepository.save(signup);
    }

    /**
     * 取消报名
     */
    @Transactional
    public void cancel(Long activityId, Long userId) {
        if (activityId == null || userId == null) {
            throw new IllegalArgumentException("activityId and userId must not be null");
        }
        ActivitySignup signup = signupRepository.findByActivityIdAndUserId(activityId, userId)
                .orElseThrow(() -> new IllegalStateException("未找到报名记录"));
        if (signup.getStatus() != null && signup.getStatus() == ActivitySignup.STATUS_CANCELLED) {
            throw new IllegalStateException("报名已取消");
        }
        if (signup.getStatus() != null && signup.getStatus() == ActivitySignup.STATUS_CHECKED_IN) {
            throw new IllegalStateException("已签到，无法取消");
        }
        signup.setStatus(ActivitySignup.STATUS_CANCELLED);
        signupRepository.save(signup);
    }

    /**
     * 签到
     */
    @Transactional
    public ActivitySignup checkin(Long activityId, Long userId) {
        if (activityId == null || userId == null) {
            throw new IllegalArgumentException("activityId and userId must not be null");
        }
        ActivitySignup signup = signupRepository.findByActivityIdAndUserId(activityId, userId)
                .orElseThrow(() -> new IllegalStateException("请先报名"));
        if (signup.getStatus() != null && signup.getStatus() == ActivitySignup.STATUS_CANCELLED) {
            throw new IllegalStateException("报名已取消，无法签到");
        }
        signup.setStatus(ActivitySignup.STATUS_CHECKED_IN);
        signup.setSigninTime(LocalDateTime.now());
        return signupRepository.save(signup);
    }

    /**
     * 分享
     */
    public Map<String, Object> share(Long activityId) {
        if (activityId == null) return Map.of();
        long total = signupRepository.countByActivityId(activityId);
        long checked = signupRepository.countByActivityIdAndStatusNot(activityId, ActivitySignup.STATUS_CANCELLED);
        Map<String, Object> result = new HashMap<>();
        result.put("activityId", activityId);
        result.put("signupCount", total);
        result.put("activeCount", checked);
        result.put("success", true);
        return result;
    }

    public Optional<ActivitySignup> findMine(Long activityId, Long userId) {
        if (activityId == null || userId == null) return Optional.empty();
        return signupRepository.findByActivityIdAndUserId(activityId, userId);
    }

    public List<ActivitySignup> listByActivity(Long activityId) {
        if (activityId == null) return List.of();
        return signupRepository.findByActivityId(activityId);
    }

    public List<ActivitySignup> listByUser(Long userId) {
        if (userId == null) return List.of();
        return signupRepository.findByUserId(userId);
    }
}