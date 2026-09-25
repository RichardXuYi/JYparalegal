package com.jyfc.backend.module.skill.controller;

import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.service.UserService;
import com.jyfc.backend.module.skill.dto.SkillDetailDto;
import com.jyfc.backend.module.skill.dto.SkillPushRequest;
import com.jyfc.backend.module.skill.dto.SkillSummaryDto;
import com.jyfc.backend.module.skill.service.UserSkillService;
import com.jyfc.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用户 skill 跨端同步接口。
 *
 * <p>Studio 主进程持 JWT 访问令牌调用（web/app 端也可复用）。所有接口都要求登录用户
 * （{@code ROLE_USER}），且只能操作自己名下的 skill —— userId 来自认证上下文，不接受
 * 客户端传入。</p>
 */
@Tag(name = "用户技能同步", description = "跨端同步用户 skill 文件")
@RestController
@RequestMapping("/api/user/skills")
public class UserSkillController {

    private static final Logger log = LoggerFactory.getLogger(UserSkillController.class);

    private final UserSkillService userSkillService;
    private final UserService userService;

    public UserSkillController(UserSkillService userSkillService, UserService userService) {
        this.userSkillService = userSkillService;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<Map<String, List<SkillSummaryDto>>>> list(Authentication authentication) {
        Long userId = requireUserId(authentication);
        List<SkillSummaryDto> skills = userSkillService.listSummaries(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("skills", skills)));
    }

    @GetMapping("/{slug}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<SkillDetailDto>> get(
            Authentication authentication,
            @PathVariable String slug) {
        Long userId = requireUserId(authentication);
        Optional<SkillDetailDto> detail = userSkillService.getDetail(userId, slug);
        return detail
                .map(d -> ResponseEntity.ok(ApiResponse.success(d)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "skill 不存在")));
    }

    @PutMapping("/{slug}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<SkillSummaryDto>> upsert(
            Authentication authentication,
            @PathVariable String slug,
            @RequestBody SkillPushRequest request) {
        Long userId = requireUserId(authentication);
        SkillSummaryDto summary = userSkillService.upsert(userId, slug, request);
        return ResponseEntity.ok(ApiResponse.success("已同步", summary));
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<Void>> delete(
            Authentication authentication,
            @PathVariable String slug) {
        Long userId = requireUserId(authentication);
        boolean removed = userSkillService.delete(userId, slug);
        if (!removed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "skill 不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @ExceptionHandler(UserSkillService.SkillSyncBadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(UserSkillService.SkillSyncBadRequestException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new UserSkillService.SkillSyncBadRequestException("未登录");
        }
        String username = authentication.getName();
        Optional<UserEntity> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("Skill sync for unknown user: {}", username);
            throw new UserSkillService.SkillSyncBadRequestException("用户不存在");
        }
        return userOpt.get().getId();
    }
}
