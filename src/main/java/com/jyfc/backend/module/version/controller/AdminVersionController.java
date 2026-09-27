package com.jyfc.backend.module.version.controller;

import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.module.version.dto.VersionInfoDto;
import com.jyfc.backend.module.version.dto.VersionPublishRequest;
import com.jyfc.backend.module.version.service.VersionService;
import com.jyfc.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端版本管理接口（需要管理员权限）
 */
@RestController
@RequestMapping("/api/admin/versions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
@Tag(name = "版本管理-管理端", description = "版本发布与管理（需管理员权限）")
public class AdminVersionController {

    private final VersionService versionService;
    private final UserContextUtil userContextUtil;

    /**
     * 获取所有端版本信息
     */
    @GetMapping
    @Operation(summary = "获取所有端版本信息")
    public ResponseEntity<ApiResponse<List<VersionInfoDto>>> getAllVersions() {
        List<VersionInfoDto> versions = versionService.getAllVersions();
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    /**
     * 获取某端版本信息
     */
    @GetMapping("/{clientType}")
    @Operation(summary = "获取某端版本信息")
    public ResponseEntity<ApiResponse<VersionInfoDto>> getVersion(
            @PathVariable String clientType) {
        VersionInfoDto version = versionService.getVersionInfo(clientType);
        return ResponseEntity.ok(ApiResponse.success(version));
    }

    /**
     * 更新某端版本信息
     */
    @PutMapping("/{clientType}")
    @Operation(summary = "更新某端版本信息")
    public ResponseEntity<ApiResponse<VersionInfoDto>> updateVersion(
            @PathVariable String clientType,
            @Valid @RequestBody VersionPublishRequest request) {
        request.setClientType(clientType);
        VersionInfoDto version = versionService.updateVersion(clientType, request);
        return ResponseEntity.ok(ApiResponse.success(version));
    }

    /**
     * 发布新版本
     */
    @PostMapping("/publish")
    @Operation(summary = "发布新版本")
    public ResponseEntity<ApiResponse<VersionInfoDto>> publishVersion(
            @Valid @RequestBody VersionPublishRequest request) {
        Long userId = userContextUtil.getCurrentUserId();
        VersionInfoDto version = versionService.publishVersion(request, userId);
        return ResponseEntity.ok(ApiResponse.success(version));
    }
}
