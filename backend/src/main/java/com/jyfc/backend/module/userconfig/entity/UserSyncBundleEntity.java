package com.jyfc.backend.module.userconfig.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 用户文件型同步包（bundle）元数据。
 *
 * <p>bundle 的实际文件内容存放在服务器磁盘（见 {@code UserSyncBundleService}），本表
 * 只保存元数据与内容指纹（{@code contentHash}），用于跨端上传/下载时的差异比对。
 * {@code kind} 区分包类型（当前为 agent-profile：SOUL.md / IDENTITY.md / MEMORY.md
 * 等 workspace 文件），{@code slug} 为包内标识（如 agentId）。
 * ({@code userId} + {@code kind} + {@code slug}) 唯一。</p>
 */
@Entity
@Table(
    name = "user_sync_bundles",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_bundles_user_kind_slug", columnNames = {"user_id", "kind", "slug"})
    },
    indexes = {
        @Index(name = "idx_user_bundles_user_kind", columnList = "user_id, kind")
    }
)
public class UserSyncBundleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "kind", nullable = false, length = 64)
    private String kind;

    @Column(name = "slug", nullable = false)
    private String slug;

    /** bundle 内容指纹：对全部文件（路径 + 各文件 SHA-256）归一化后的 SHA-256 十六进制值。 */
    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    /** 磁盘存储目录（相对上传根目录），形如 {@code user-bundles/<userId>/<kind>/<slug>}。 */
    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes = 0L;

    @Column(name = "file_count", nullable = false)
    private Integer fileCount = 0;

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
        if (sizeBytes == null) {
            sizeBytes = 0L;
        }
        if (fileCount == null) {
            fileCount = 0;
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
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }
    public String getClientType() { return clientType; }
    public void setClientType(String clientType) { this.clientType = clientType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
