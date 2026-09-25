package com.jyfc.backend.module.skill.service;

import com.jyfc.backend.module.skill.dto.SkillDetailDto;
import com.jyfc.backend.module.skill.dto.SkillFilePayload;
import com.jyfc.backend.module.skill.dto.SkillPushRequest;
import com.jyfc.backend.module.skill.dto.SkillSummaryDto;
import com.jyfc.backend.module.skill.entity.UserSkillEntity;
import com.jyfc.backend.module.skill.repository.UserSkillRepository;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 用户 skill 同步服务。
 *
 * <p>存储策略：元数据入库（{@link UserSkillEntity}），skill 文件内容按
 * {@code <uploadRoot>/user-skills/<userId>/<slug>/} 落到服务器磁盘。内容指纹
 * {@code contentHash} 与 Studio 端算法一致（见 skill-sync-service.ts）：对每个文件取
 * 原始字节的 SHA-256，再对按路径排序后的 {@code path + "\n" + fileHash + "\n"} 序列取
 * SHA-256。</p>
 */
@Service
public class UserSkillService {

    private static final Logger log = LoggerFactory.getLogger(UserSkillService.class);

    /** 单文件上限 2MB，单 skill 总量上限 20MB；超限拒绝，避免滥用磁盘。 */
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;
    private static final int MAX_FILE_COUNT = 500;

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    private final UserSkillRepository repository;
    private final Path storageRoot;

    public UserSkillService(
            UserSkillRepository repository,
            @Value("${file.upload.path:./uploads}") String uploadPath) {
        this.repository = repository;
        this.storageRoot = Paths.get(uploadPath, "user-skills").toAbsolutePath().normalize();
    }

    /** 抛出该异常表示请求非法（400）。 */
    public static class SkillSyncBadRequestException extends RuntimeException {
        public SkillSyncBadRequestException(String message) {
            super(message);
        }
    }

    public List<SkillSummaryDto> listSummaries(Long userId) {
        if (userId == null) return List.of();
        return repository.findByUserId(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    public Optional<SkillDetailDto> getDetail(Long userId, String slug) {
        if (userId == null) return Optional.empty();
        validateSlug(slug);
        Optional<UserSkillEntity> entityOpt = repository.findByUserIdAndSlug(userId, slug);
        if (entityOpt.isEmpty()) {
            return Optional.empty();
        }
        UserSkillEntity entity = entityOpt.get();
        Path dir = resolveSkillDir(userId, slug);
        List<SkillFilePayload> files = readSkillFiles(dir);
        return Optional.of(new SkillDetailDto(
                entity.getSlug(),
                entity.getName(),
                entity.getDescription(),
                entity.getVersion(),
                entity.getContentHash(),
                files));
    }

    @Transactional
    public SkillSummaryDto upsert(Long userId, String slug, SkillPushRequest request) {
        if (userId == null) throw new SkillSyncBadRequestException("userId must not be null");
        validateSlug(slug);
        if (request == null || request.files() == null || request.files().isEmpty()) {
            throw new SkillSyncBadRequestException("skill 文件为空");
        }
        List<SkillFilePayload> files = request.files();
        if (files.size() > MAX_FILE_COUNT) {
            throw new SkillSyncBadRequestException("skill 文件数量超出限制");
        }

        // 解码并做安全/大小校验
        long totalBytes = 0;
        List<byte[]> decoded = new ArrayList<>(files.size());
        for (SkillFilePayload file : files) {
            String relPath = file.path();
            if (relPath == null || relPath.isBlank()) {
                throw new SkillSyncBadRequestException("文件路径为空");
            }
            assertSafeRelativePath(relPath);
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(file.contentBase64() == null ? "" : file.contentBase64());
            } catch (IllegalArgumentException e) {
                throw new SkillSyncBadRequestException("文件内容 Base64 非法: " + relPath);
            }
            if (bytes.length > MAX_FILE_BYTES) {
                throw new SkillSyncBadRequestException("单个文件超出大小限制: " + relPath);
            }
            totalBytes += bytes.length;
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw new SkillSyncBadRequestException("skill 总大小超出限制");
            }
            decoded.add(bytes);
        }

        // 校验内容指纹，防止半包/损坏
        String computedHash = computeHash(files, decoded);
        if (request.hash() != null && !request.hash().isBlank()
                && !computedHash.equalsIgnoreCase(request.hash())) {
            throw new SkillSyncBadRequestException("内容指纹校验失败");
        }

        Path dir = resolveSkillDir(userId, slug);
        writeSkillFiles(dir, files, decoded);

        UserSkillEntity entity = repository.findByUserIdAndSlug(userId, slug)
                .orElseGet(UserSkillEntity::new);
        entity.setUserId(userId);
        entity.setSlug(slug);
        entity.setName(request.name());
        entity.setDescription(truncate(request.description(), 1000));
        entity.setVersion(truncate(request.version(), 64));
        entity.setContentHash(computedHash);
        entity.setStoragePath(storageRoot.relativize(dir).toString().replace('\\', '/'));
        entity.setSizeBytes(totalBytes);
        entity.setFileCount(files.size());
        UserSkillEntity saved = repository.save(entity);
        log.info("Synced skill push: userId={}, slug={}, files={}, bytes={}", userId, slug, files.size(), totalBytes);
        return toSummary(saved);
    }

    @Transactional
    public boolean delete(Long userId, String slug) {
        if (userId == null) return false;
        validateSlug(slug);
        Optional<UserSkillEntity> entityOpt = repository.findByUserIdAndSlug(userId, slug);
        if (entityOpt.isEmpty()) {
            return false;
        }
        repository.deleteByUserIdAndSlug(userId, slug);
        deleteDirRecursively(resolveSkillDir(userId, slug));
        return true;
    }

    // ==================== helpers ====================

    private SkillSummaryDto toSummary(UserSkillEntity e) {
        return new SkillSummaryDto(
                e.getSlug(),
                e.getName(),
                e.getDescription(),
                e.getVersion(),
                e.getContentHash(),
                e.getSizeBytes() == null ? 0 : e.getSizeBytes(),
                e.getFileCount() == null ? 0 : e.getFileCount(),
                e.getUpdatedAt() == null ? null : e.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private void validateSlug(String slug) {
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new SkillSyncBadRequestException("非法的 skill 标识");
        }
    }

    private void assertSafeRelativePath(String relPath) {
        String normalized = relPath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..") || normalized.contains(":")) {
            throw new SkillSyncBadRequestException("非法的文件路径: " + relPath);
        }
    }

    private Path resolveSkillDir(Long userId, String slug) {
        Path dir = storageRoot.resolve(String.valueOf(userId)).resolve(slug).normalize();
        if (!dir.startsWith(storageRoot)) {
            throw new SkillSyncBadRequestException("非法的存储路径");
        }
        return dir;
    }

    private List<SkillFilePayload> readSkillFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<SkillFilePayload> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            List<Path> paths = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> dir.relativize(p).toString().replace('\\', '/')))
                    .toList();
            for (Path p : paths) {
                byte[] bytes = Files.readAllBytes(p);
                String rel = dir.relativize(p).toString().replace('\\', '/');
                result.add(new SkillFilePayload(rel, Base64.getEncoder().encodeToString(bytes)));
            }
        } catch (IOException e) {
            log.warn("Failed to read skill files at {}: {}", dir, e.getMessage());
        }
        return result;
    }

    private void writeSkillFiles(Path dir, List<SkillFilePayload> files, List<byte[]> decoded) {
        try {
            // 全量覆盖：先清空旧目录再写入，避免残留已删除的文件
            deleteDirRecursively(dir);
            Files.createDirectories(dir);
            for (int i = 0; i < files.size(); i++) {
                String rel = files.get(i).path().replace('\\', '/');
                Path target = dir.resolve(rel).normalize();
                if (!target.startsWith(dir)) {
                    throw new SkillSyncBadRequestException("非法的文件路径: " + rel);
                }
                Files.createDirectories(target.getParent());
                Files.write(target, decoded.get(i));
            }
        } catch (IOException e) {
            throw new RuntimeException("写入 skill 文件失败: " + e.getMessage(), e);
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
            log.warn("Failed to delete skill dir {}: {}", dir, e.getMessage());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 计算 skill 内容指纹。必须与 Studio 端算法保持一致。
     */
    static String computeHash(List<SkillFilePayload> files, List<byte[]> decoded) {
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
