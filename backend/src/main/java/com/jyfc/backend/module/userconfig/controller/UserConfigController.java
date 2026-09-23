package com.jyfc.backend.module.userconfig.controller;

import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.service.UserService;
import com.jyfc.backend.module.userconfig.dto.ConfigEntryDto;
import com.jyfc.backend.module.userconfig.dto.ConfigPutRequest;
import com.jyfc.backend.module.userconfig.service.UserConfigService;
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
 * 用户配置 KV 跨端同步接口。
 *
 * <p>Studio 主进程持 JWT 访问令牌调用（web/app 端也可复用）。所有接口都要求登录用户
 * （{@code ROLE_USER}），且只能操作自己名下的配置 —— userId 来自认证上下文，不接受
 * 客户端传入。namespace 开放（profile / preferences / points / usage …）。</p>
 */
@Tag(name = "用户配置同步", description = "跨端同步用户配置 KV")
@RestController
@RequestMapping("/api/user/config")
public class UserConfigController {

    private static final Logger log = LoggerFactory.getLogger(UserConfigController.class);

    private final UserConfigService userConfigService;
    private final UserService userService;

    public UserConfigController(UserConfigService userConfigService, UserService userService) {
        this.userConfigService = userConfigService;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<Map<String, List<ConfigEntryDto>>>> list(
            Authentication authentication,
            @RequestParam(required = false) String namespace) {
        Long userId = requireUserId(authentication);
        List<ConfigEntryDto> entries = userConfigService.list(userId, namespace);
        return ResponseEntity.ok(ApiResponse.success(Map.of("entries", entries)));
    }

    @GetMapping("/{namespace}/{key}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<ConfigEntryDto>> get(
            Authentication authentication,
            @PathVariable String namespace,
            @PathVariable String key) {
        Long userId = requireUserId(authentication);
        Optional<ConfigEntryDto> entry = userConfigService.get(userId, namespace, key);
        return entry
                .map(d -> ResponseEntity.ok(ApiResponse.success(d)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "配置不存在")));
    }

    @PutMapping("/{namespace}/{key}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<ConfigEntryDto>> put(
            Authentication authentication,
            @PathVariable String namespace,
            @PathVariable String key,
            @RequestBody ConfigPutRequest request) {
        Long userId = requireUserId(authentication);
        ConfigEntryDto saved = userConfigService.put(userId, namespace, key, request);
        return ResponseEntity.ok(ApiResponse.success("已同步", saved));
    }

    @DeleteMapping("/{namespace}/{key}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<ApiResponse<Void>> delete(
            Authentication authentication,
            @PathVariable String namespace,
            @PathVariable String key) {
        Long userId = requireUserId(authentication);
        boolean removed = userConfigService.delete(userId, namespace, key);
        if (!removed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "配置不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @ExceptionHandler(UserConfigService.ConfigSyncBadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(UserConfigService.ConfigSyncBadRequestException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new UserConfigService.ConfigSyncBadRequestException("未登录");
        }
        String username = authentication.getName();
        Optional<UserEntity> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("Config sync for unknown user: {}", username);
            throw new UserConfigService.ConfigSyncBadRequestException("用户不存在");
        }
        return userOpt.get().getId();
    }
}
