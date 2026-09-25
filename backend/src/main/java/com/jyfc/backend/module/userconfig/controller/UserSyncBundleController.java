package com.jyfc.backend.module.userconfig.controller;

import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.service.UserService;
import com.jyfc.backend.module.userconfig.dto.BundleDetailDto;
import com.jyfc.backend.module.userconfig.dto.BundlePushRequest;
import com.jyfc.backend.module.userconfig.dto.BundleSummaryDto;
import com.jyfc.backend.module.userconfig.service.UserSyncBundleService;
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
 * 用户文件型同步包（bundle）跨端同步接口。
 *
 * <p>与 skill 同步同协议（文件清单 Base64 + 内容指纹），当前 kind 支持
 * agent-profile（SOUL.md / IDENTITY.md / MEMORY.md 等 agent workspace 文件）。
 * 所有接口都要求登录用户（{@code ROLE_USER}），且只能操作自己名下的 bundle。</p>
 */
@Tag(name = "用户同步包", description = "跨端同步用户文件型配置包（agent-profile 等）")
@RestController
@RequestMapping("/api/user/bundles")
public class UserSyncBundleController {

    private static final Logger log = LoggerFactory.getLogger(UserSyncBundleController.class);

    private final UserSyncBundleService bundleService;
    private final UserService userService;

    public UserSyncBundleController(UserSyncBundleService bundleService, UserService userService) {
        this.bundleService = bundleService;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<Map<String, List<BundleSummaryDto>>>> list(
            Authentication authentication,
            @RequestParam(required = false) String kind) {
        Long userId = requireUserId(authentication);
        List<BundleSummaryDto> bundles = bundleService.listSummaries(userId, kind);
        return ResponseEntity.ok(ApiResponse.success(Map.of("bundles", bundles)));
    }

    @GetMapping("/{kind}/{slug}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<BundleDetailDto>> get(
            Authentication authentication,
            @PathVariable String kind,
            @PathVariable String slug) {
        Long userId = requireUserId(authentication);
        Optional<BundleDetailDto> detail = bundleService.getDetail(userId, kind, slug);
        return detail
                .map(d -> ResponseEntity.ok(ApiResponse.success(d)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "bundle 不存在")));
    }

    @PutMapping("/{kind}/{slug}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<BundleSummaryDto>> upsert(
            Authentication authentication,
            @PathVariable String kind,
            @PathVariable String slug,
            @RequestBody BundlePushRequest request) {
        Long userId = requireUserId(authentication);
        BundleSummaryDto summary = bundleService.upsert(userId, kind, slug, request);
        return ResponseEntity.ok(ApiResponse.success("已同步", summary));
    }

    @DeleteMapping("/{kind}/{slug}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<Void>> delete(
            Authentication authentication,
            @PathVariable String kind,
            @PathVariable String slug) {
        Long userId = requireUserId(authentication);
        boolean removed = bundleService.delete(userId, kind, slug);
        if (!removed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "bundle 不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @ExceptionHandler(UserSyncBundleService.BundleSyncBadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(UserSyncBundleService.BundleSyncBadRequestException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new UserSyncBundleService.BundleSyncBadRequestException("未登录");
        }
        String username = authentication.getName();
        Optional<UserEntity> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("Bundle sync for unknown user: {}", username);
            throw new UserSyncBundleService.BundleSyncBadRequestException("用户不存在");
        }
        return userOpt.get().getId();
    }
}
