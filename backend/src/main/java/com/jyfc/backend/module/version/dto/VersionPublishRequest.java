package com.jyfc.backend.module.version.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 发布新版本请求 DTO
 */
public class VersionPublishRequest {

    @NotBlank(message = "客户端类型不能为空")
    private String clientType;

    @NotBlank(message = "版本号不能为空")
    private String currentVersion;

    @NotBlank(message = "最低兼容版本不能为空")
    private String minVersion;

    private String downloadUrl;
    private String releaseNotes;
    private Boolean forceUpdate = false;

    public VersionPublishRequest() {}

    // Getters / Setters

    public String getClientType() { return clientType; }
    public void setClientType(String clientType) { this.clientType = clientType; }

    public String getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }

    public String getMinVersion() { return minVersion; }
    public void setMinVersion(String minVersion) { this.minVersion = minVersion; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getReleaseNotes() { return releaseNotes; }
    public void setReleaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; }

    public Boolean getForceUpdate() { return forceUpdate; }
    public void setForceUpdate(Boolean forceUpdate) { this.forceUpdate = forceUpdate; }
}
