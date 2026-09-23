package com.jyfc.backend.module.userconfig.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 用户配置 KV 条目。
 *
 * <p>按 {@code namespace}（profile / preferences / points / usage …）+ {@code configKey}
 * 组织，值为 JSON 文本（{@code contentJson}），并带内容指纹 {@code contentHash} 与
 * 单调递增 {@code version}，供跨端同步做差异比对。({@code userId} + {@code namespace}
 * + {@code configKey}) 唯一。</p>
 */
@Entity
@Table(
    name = "user_config_entries",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_config_user_ns_key", columnNames = {"user_id", "namespace", "config_key"})
    },
    indexes = {
        @Index(name = "idx_user_config_user_ns", columnList = "user_id, namespace")
    }
)
public class UserConfigEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "namespace", nullable = false, length = 64)
    private String namespace;

    @Column(name = "config_key", nullable = false, length = 128)
    private String configKey;

    /** 配置值（JSON 文本，客户端语义自解释）。 */
    @Column(name = "content_json", nullable = false, columnDefinition = "mediumtext")
    private String contentJson;

    /** 内容指纹：contentJson 原始字节的 SHA-256 十六进制值。 */
    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    /** 每次覆盖写自增，供客户端做乐观比对。 */
    @Column(name = "version", nullable = false)
    private Long version = 1L;

    /** 最后写入方（desktop / web），仅展示用。 */
    @Column(name = "client_type", length = 32)
    private String clientType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (version == null) {
            version = 1L;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getClientType() { return clientType; }
    public void setClientType(String clientType) { this.clientType = clientType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
