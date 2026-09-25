package com.jyfc.backend.module.skill.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 用户 skill 同步元数据。
 *
 * <p>skill 的实际文件内容存放在服务器磁盘（见 {@code UserSkillService}），本表只保存
 * 每个用户每个 skill 的元数据与内容指纹（{@code contentHash}），用于跨端双向同步时的
 * 差异比对与冲突判定。({@code userId} + {@code slug}) 唯一。</p>
 */
@Entity
@Table(
    name = "user_skills",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_skills_user_slug", columnNames = {"user_id", "slug"})
    },
    indexes = {
        @Index(name = "idx_user_skills_user_id", columnList = "user_id")
    }
)
public class UserSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "name")
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "version", length = 64)
    private String version;

    /** skill 内容指纹：对全部文件（路径 + 各文件 SHA-256）归一化后的 SHA-256 十六进制值。 */
    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    /** 磁盘存储目录（相对上传根目录），形如 {@code user-skills/<userId>/<slug>}。 */
    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes = 0L;

    @Column(name = "file_count", nullable = false)
    private Integer fileCount = 0;

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
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
