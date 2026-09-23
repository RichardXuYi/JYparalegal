package com.jyfc.backend.module.version.service;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.module.version.dto.VersionCheckResponse;
import com.jyfc.backend.module.version.dto.VersionInfoDto;
import com.jyfc.backend.module.version.dto.VersionPublishRequest;
import com.jyfc.backend.module.version.entity.AppVersionEntity;
import com.jyfc.backend.module.version.entity.ReleaseHistoryEntity;
import com.jyfc.backend.module.version.repository.AppVersionRepository;
import com.jyfc.backend.module.version.repository.ReleaseHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 版本管理服务
 */
@Service
public class VersionService {
    private static final Logger log = LoggerFactory.getLogger(VersionService.class);

    private final AppVersionRepository appVersionRepository;
    private final ReleaseHistoryRepository releaseHistoryRepository;

    public VersionService(AppVersionRepository appVersionRepository,
                          ReleaseHistoryRepository releaseHistoryRepository) {
        this.appVersionRepository = appVersionRepository;
        this.releaseHistoryRepository = releaseHistoryRepository;
    }

    /**
     * 检查版本更新
     */
    @Transactional(readOnly = true)
    public VersionCheckResponse checkVersion(String clientType, String currentVersion) {
        AppVersionEntity.ClientType type;
        try {
            type = AppVersionEntity.ClientType.valueOf(clientType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("无效的客户端类型: " + clientType);
        }

        AppVersionEntity appVersion = appVersionRepository.findByClientType(type)
                .orElseThrow(() -> new BusinessException("版本配置不存在: " + clientType));

        String latestVersion = appVersion.getCurrentVersion();
        int cmp = compareSemver(currentVersion, latestVersion);

        String updateType;
        if (cmp >= 0) {
            updateType = "UP_TO_DATE";
        } else if (Boolean.TRUE.equals(appVersion.getForceUpdate()) 
                || compareSemver(currentVersion, appVersion.getMinVersion()) < 0) {
            updateType = "REQUIRED";
        } else {
            updateType = "RECOMMENDED";
        }

        VersionCheckResponse response = new VersionCheckResponse();
        response.setUpdateAvailable(!"UP_TO_DATE".equals(updateType));
        response.setCurrentVersion(currentVersion);
        response.setLatestVersion(latestVersion);
        response.setMinVersion(appVersion.getMinVersion());
        response.setForceUpdate(Boolean.TRUE.equals(appVersion.getForceUpdate()));
        response.setUpdateType(updateType);
        response.setReleaseNotes(appVersion.getReleaseNotes());
        response.setDownloadUrl(appVersion.getDownloadUrl());
        response.setPublishedAt(appVersion.getUpdatedAt());

        log.debug("版本检查: clientType={}, current={}, latest={}, type={}", 
                clientType, currentVersion, latestVersion, updateType);

        return response;
    }

    /**
     * 获取某端最新版本详情
     */
    @Transactional(readOnly = true)
    public VersionInfoDto getVersionInfo(String clientType) {
        AppVersionEntity.ClientType type;
        try {
            type = AppVersionEntity.ClientType.valueOf(clientType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("无效的客户端类型: " + clientType);
        }

        AppVersionEntity entity = appVersionRepository.findByClientType(type)
                .orElseThrow(() -> new BusinessException("版本配置不存在: " + clientType));

        return toDto(entity);
    }

    /**
     * 获取所有端版本信息
     */
    @Transactional(readOnly = true)
    public List<VersionInfoDto> getAllVersions() {
        return appVersionRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 更新某端版本信息
     */
    @Transactional
    public VersionInfoDto updateVersion(String clientType, VersionPublishRequest request) {
        AppVersionEntity.ClientType type;
        try {
            type = AppVersionEntity.ClientType.valueOf(clientType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("无效的客户端类型: " + clientType);
        }

        AppVersionEntity entity = appVersionRepository.findByClientType(type)
                .orElseGet(() -> {
                    AppVersionEntity newEntity = new AppVersionEntity();
                    newEntity.setClientType(type);
                    return newEntity;
                });

        entity.setCurrentVersion(request.getCurrentVersion());
        entity.setMinVersion(request.getMinVersion());
        entity.setDownloadUrl(request.getDownloadUrl());
        entity.setReleaseNotes(request.getReleaseNotes());
        entity.setForceUpdate(Boolean.TRUE.equals(request.getForceUpdate()));

        AppVersionEntity saved = appVersionRepository.save(entity);
        log.info("版本信息已更新: clientType={}, version={}", clientType, request.getCurrentVersion());

        return toDto(saved);
    }

    /**
     * 发布新版本（更新 app_version + 写入 release_history）
     */
    @Transactional
    public VersionInfoDto publishVersion(VersionPublishRequest request, Long publishedBy) {
        // 更新当前版本
        VersionInfoDto updated = updateVersion(request.getClientType(), request);

        // 写入发布历史
        ReleaseHistoryEntity history = new ReleaseHistoryEntity();
        history.setClientType(request.getClientType().toUpperCase());
        history.setVersion(request.getCurrentVersion());
        history.setReleaseNotes(request.getReleaseNotes());
        history.setDownloadUrl(request.getDownloadUrl());
        history.setForceUpdate(Boolean.TRUE.equals(request.getForceUpdate()));
        history.setPublishedBy(publishedBy);
        history.setPublishedAt(LocalDateTime.now());

        releaseHistoryRepository.save(history);
        log.info("版本已发布: clientType={}, version={}, publishedBy={}", 
                request.getClientType(), request.getCurrentVersion(), publishedBy);

        return updated;
    }

    /**
     * 语义化版本比较
     * @return 负数表示 v1 < v2，0 表示相等，正数表示 v1 > v2
     */
    private int compareSemver(String v1, String v2) {
        if (v1 == null || v2 == null) return 0;
        
        int[] parts1 = Arrays.stream(v1.split("\\."))
                .mapToInt(s -> {
                    try { return Integer.parseInt(s); } 
                    catch (NumberFormatException e) { return 0; }
                }).toArray();
        int[] parts2 = Arrays.stream(v2.split("\\."))
                .mapToInt(s -> {
                    try { return Integer.parseInt(s); } 
                    catch (NumberFormatException e) { return 0; }
                }).toArray();

        int maxLen = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLen; i++) {
            int p1 = i < parts1.length ? parts1[i] : 0;
            int p2 = i < parts2.length ? parts2[i] : 0;
            if (p1 != p2) return Integer.compare(p1, p2);
        }
        return 0;
    }

    private VersionInfoDto toDto(AppVersionEntity entity) {
        VersionInfoDto dto = new VersionInfoDto();
        dto.setId(entity.getId());
        dto.setClientType(entity.getClientType().name());
        dto.setCurrentVersion(entity.getCurrentVersion());
        dto.setMinVersion(entity.getMinVersion());
        dto.setDownloadUrl(entity.getDownloadUrl());
        dto.setReleaseNotes(entity.getReleaseNotes());
        dto.setForceUpdate(entity.getForceUpdate());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
