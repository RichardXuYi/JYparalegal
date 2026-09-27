package com.jyfc.backend.module.version.controller;

import com.jyfc.backend.module.version.dto.VersionCheckResponse;
import com.jyfc.backend.module.version.dto.VersionInfoDto;
import com.jyfc.backend.module.version.service.VersionService;
import com.jyfc.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 公开版本检查接口（无需登录）
 */
@RestController
@RequestMapping("/api/public/version")
@RequiredArgsConstructor
@Tag(name = "版本管理-公开", description = "客户端版本检查（无需登录）")
public class PublicVersionController {

    private final VersionService versionService;

    /**
     * 检查版本更新
     */
    @GetMapping("/check")
    @Operation(summary = "检查版本更新")
    public ResponseEntity<ApiResponse<VersionCheckResponse>> checkVersion(
            @RequestParam String clientType,
            @RequestParam String currentVersion) {
        VersionCheckResponse response = versionService.checkVersion(clientType, currentVersion);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 获取某端最新版本详情
     */
    @GetMapping("/info")
    @Operation(summary = "获取最新版本详情")
    public ResponseEntity<ApiResponse<VersionInfoDto>> getVersionInfo(
            @RequestParam String clientType) {
        VersionInfoDto response = versionService.getVersionInfo(clientType);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
