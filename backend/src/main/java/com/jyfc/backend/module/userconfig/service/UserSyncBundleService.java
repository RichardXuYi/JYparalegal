package com.jyfc.backend.module.userconfig.service;

import com.jyfc.backend.module.userconfig.dto.BundleDetailDto;
import com.jyfc.backend.module.userconfig.dto.BundleFilePayload;
import com.jyfc.backend.module.userconfig.dto.BundlePushRequest;
import com.jyfc.backend.module.userconfig.dto.BundleSummaryDto;
import com.jyfc.backend.module.userconfig.entity.UserSyncBundleEntity;
import com.jyfc.backend.module.userconfig.repository.UserSyncBundleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 用户文件型同步包（bundle）云同步服务。
 *
 * <p>存储策略与 skill 同步一致：元数据入库（{@link UserSyncBundleEntity}），文件内容按
 * {@code <uploadRoot>/user-bundles/<userId>/<kind>/<slug>/} 落到服务器磁盘。内容指纹
 * {@code contentHash} 与 Studio 端算法一致（见 config-sync-service.ts / skill-sync-service.ts）：
 * 对每个文件取原始字节的 SHA-256，再对按路径排序后的 {@code path + "\n" + fileHash + "\n"}
 * 序列取 SHA-256。</p>
 */
@Service
public class UserSyncBundleService {

    private static final Logger log = LoggerFactory.getLogger(UserSyncBundleService.class);

    /** 单文件上限 2MB，单 bundle 总量上限 20MB；超限拒绝，避免滥用磁盘。 */
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;
    private static final int MAX_FILE_COUNT = 500;

    /** 允许的 bundle 类型（开放集合按需扩展，服务端白名单防目录滥用）。 */
    private static final Set<String> ALLOWED_KINDS = Set.of("agent-profile");

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    private final UserSyncBundleRepository repository;
    private final Path storageRoot;

    public UserSyncBundleService(
            UserSyncBundleRepository repository,
            @Value("${file.upload.path:./uploads}") String uploadPath) {
        this.repository = repository;
        this.storageRoot = Paths.get(uploadPath, "user-bundles").toAbsolutePath().normalize();
    }

    /** 抛出该异常表示请求非法（400）。 */
    public static class BundleSyncBadRequestException extends RuntimeException {
        public BundleSyncBadRequestException(String message) {
            super(message);
        }
    }

    public List<BundleSummaryDto> listSummaries(Long userId, String kind) {
        if (userId == null) return List.of();
        List<UserSyncBundleEntity> entities;
        if (kind == null || kind.isBlank()) {
            entities = repository.findByUserId(userId);
        } else {
            validateKind(kind);
            entities = repository.findByUserIdAndKind(userId, kind);
        }
        return entities.stream().map(this::toSummary).toList();
    }

    public Optional<BundleDetailDto> getDetail(Long userId, String kind, String slug) {
        if (userId == null) return Optional.empty();
        validateKind(kind);
        validateSlug(slug);
        Optional<UserSyncBundleEntity> entityOpt = repository.findByUserIdAndKindAndSlug(userId, kind, slug);
        if (entityOpt.isEmpty()) {
            return Optional.empty();
        }
        UserSyncBundleEntity entity = entityOpt.get();
        Path dir = resolveBundleDir(userId, kind, slug);
        List<BundleFilePayload> files = readBundleFiles(dir);
        return Optional.of(new BundleDetailDto(
                entity.getKind(),
                entity.getSlug(),
                entity.getContentHash(),
                entity.getClientType(),
                entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                files));
    }

    @Transactional
    public BundleSummaryDto upsert(Long userId, String kind, String slug, BundlePushRequest request) {
        if (userId == null) throw new BundleSyncBadRequestException("userId must not be null");
        validateKind(kind);
        validateSlug(slug);
        if (request == null || request.files() == null || request.files().isEmpty()) {
            throw new BundleSyncBadRequestException("bundle 文件为空");
        }
        List<BundleFilePayload> files = request.files();
        if (files.size() > MAX_FILE_COUNT) {
            throw new BundleSyncBadRequestException("bundle 文件数量超出限制");
        }

        // 解码并做安全/大小校验
        long totalBytes = 0;
        List<byte[]> decoded = new ArrayList<>(files.size());
        for (BundleFilePayload file : files) {
            String relPath = file.path();
            if (relPath == null || relPath.isBlank()) {
                throw new BundleSyncBadRequestException("文件路径为空");
            }
            assertSafeRelativePath(relPath);
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(file.contentBase64() == null ? "" : file.contentBase64());
            } catch (IllegalArgumentException e) {
                throw new BundleSyncBadRequestException("文件内容 Base64 非法: " + relPath);
            }
            if (bytes.length > MAX_FILE_BYTES) {
                throw new BundleSyncBadRequestException("单个文件超出大小限制: " + relPath);
            }
            totalBytes += bytes.length;
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw new BundleSyncBadRequestException("bundle 总大小超出限制");
            }
            decoded.add(bytes);
        }

        // 校验内容指纹，防止半包/损坏
        String computedHash = computeHash(files, decoded);
        if (request.hash() != null && !request.hash().isBlank()
                && !computedHash.equalsIgnoreCase(request.hash())) {
            throw new BundleSyncBadRequestException("内容指纹校验失败");
        }

        Path dir = resolveBundleDir(userId, kind, slug);
        writeBundleFiles(dir, files, decoded);

        UserSyncBundleEntity entity = repository.findByUserIdAndKindAndSlug(userId, kind, slug)
                .orElseGet(UserSyncBundleEntity::new);
        entity.setUserId(userId);
        entity.setKind(kind);
        entity.setSlug(slug);
        entity.setContentHash(computedHash);
        entity.setStoragePath(storageRoot.relativize(dir).toString().replace('\\', '/'));
        entity.setSizeBytes(totalBytes);
        entity.setFileCount(files.size());
        entity.setClientType(truncate(request.clientType(), 32));
        UserSyncBundleEntity saved = repository.save(entity);
        log.info("Synced bundle push: userId={}, kind={}, slug={}, files={}, bytes={}",
                userId, kind, slug, files.size(), totalBytes);
        return toSummary(saved);
    }

    @Transactional
    public boolean delete(Long userId, String kind, String slug) {
        if (userId == null) return false;
        validateKind(kind);
        validateSlug(slug);
        Optional<UserSyncBundleEntity> entityOpt = repository.findByUserIdAndKindAndSlug(userId, kind, slug);
        if (entityOpt.isEmpty()) {
            return false;
        }
        repository.deleteByUserIdAndKindAndSlug(userId, kind, slug);
        deleteDirRecursively(resolveBundleDir(userId, kind, slug));
        return true;
    }

    // ==================== helpers ====================

    private BundleSummaryDto toSummary(UserSyncBundleEntity e) {
        return new BundleSummaryDto(
                e.getKind(),
                e.getSlug(),
                e.getContentHash(),
                e.getSizeBytes() == null ? 0 : e.getSizeBytes(),
                e.getFileCount() == null ? 0 : e.getFileCount(),
                e.getClientType(),
                e.getUpdatedAt() == null ? null : e.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private void validateKind(String kind) {
        if (kind == null || !ALLOWED_KINDS.contains(kind)) {
            throw new BundleSyncBadRequestException("非法的 bundle 类型");
        }
    }

    private void validateSlug(String slug) {
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new BundleSyncBadRequestException("非法的 bundle 标识");
        }
    }

    private void assertSafeRelativePath(String relPath) {
        String normalized = relPath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..") || normalized.contains(":")) {
            throw new BundleSyncBadRequestException("非法的文件路径: " + relPath);
        }
    }

    private Path resolveBundleDir(Long userId, String kind, String slug) {
        Path dir = storageRoot.resolve(String.valueOf(userId)).resolve(kind).resolve(slug).normalize();
        if (!dir.startsWith(storageRoot)) {
            throw new BundleSyncBadRequestException("非法的存储路径");
        }
        return dir;
    }

    private List<BundleFilePayload> readBundleFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<BundleFilePayload> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            List<Path> paths = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> dir.relativize(p).toString().replace('\\', '/')))
                    .toList();
            for (Path p : paths) {
                byte[] bytes = Files.readAllBytes(p);
                String rel = dir.relativize(p).toString().replace('\\', '/');
                result.add(new BundleFilePayload(rel, Base64.getEncoder().encodeToString(bytes)));
            }
        } catch (IOException e) {
            log.warn("Failed to read bundle files at {}: {}", dir, e.getMessage());
        }
        return result;
    }

    private void writeBundleFiles(Path dir, List<BundleFilePayload> files, List<byte[]> decoded) {
        try {
            // 全量覆盖：先清空旧目录再写入，避免残留已删除的文件
            deleteDirRecursively(dir);
            Files.createDirectories(dir);
            for (int i = 0; i < files.size(); i++) {
                String rel = files.get(i).path().replace('\\', '/');
                Path target = dir.resolve(rel).normalize();
                if (!target.startsWith(dir)) {
                    throw new BundleSyncBadRequestException("非法的文件路径: " + rel);
                }
                Files.createDirectories(target.getParent());
                Files.write(target, decoded.get(i));
            }
        } catch (IOException e) {
            throw new RuntimeException("写入 bundle 文件失败: " + e.getMessage(), e);
        }
    }

    private void deleteDirRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best-effort cleanup
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to delete bundle dir {}: {}", dir, e.getMessage());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 计算 bundle 内容指纹。必须与 Studio 端算法保持一致（同 skill 同步）。
     */
    static String computeHash(List<BundleFilePayload> files, List<byte[]> decoded) {
        List<String> lines = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            String path = files.get(i).path().replace('\\', '/');
            lines.add(path + "\u0000" + sha256Hex(decoded.get(i)));
        }
        lines.sort(Comparator.naturalOrder());
        MessageDigest digest = newSha256();
        for (String line : lines) {
            String[] parts = line.split("\u0000", 2);
            digest.update(parts[0].getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(parts[1].getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        }
        return toHex(digest.digest());
    }

    static String sha256Hex(byte[] bytes) {
        return toHex(newSha256().digest(bytes));
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
