package com.jyfc.backend.module.marketing.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.marketing.entity.Activity;
import com.jyfc.backend.module.marketing.repository.ActivityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/activities")
public class ActivityPublicController {

    private final ActivityRepository activityRepository;

    @Autowired
    public ActivityPublicController(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listActivities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        Pageable pageable = PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Order.desc("sortOrder"), Sort.Order.desc("startTime")));
        // 只查询状态为1（已发布）的活动
        Page<Activity> pageResult = activityRepository.findByStatus(1, pageable);
        
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "items", pageResult.getContent(),
            "total", pageResult.getTotalElements(),
            "page", page,
            "pageSize", pageSize
        )));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Activity>> getActivity(@PathVariable Long id) {
        return activityRepository.findById(id)
                        .map(activity -> ResponseEntity.ok(ApiResponse.success(activity)))
                        .orElse(ResponseEntity.status(404).body(ApiResponse.error(404, "Activity not found")));
    }
}
