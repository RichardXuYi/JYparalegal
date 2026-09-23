package com.jyfc.backend.module.userconfig.service;

import com.jyfc.backend.module.userconfig.dto.ConfigEntryDto;
import com.jyfc.backend.module.userconfig.dto.ConfigPutRequest;
import com.jyfc.backend.module.userconfig.entity.UserConfigEntryEntity;
import com.jyfc.backend.module.userconfig.repository.UserConfigEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 用户配置 KV 云同步服务。
 *
 * <p>值为 JSON 文本整存整取，服务端不解析语义；按 (userId, namespace, configKey) 覆盖写，
 * 每次覆盖 version 自增。namespace 为开放集合（profile / preferences / points / usage …），
 * 新增配置类型无需结构变更。内容指纹为 contentJson UTF-8 字节的 SHA-256。</p>
 */
@Service
public class UserConfigService {

    private static final Logger log = LoggerFactory.getLogger(UserConfigService.class);

    /** 单条配置 JSON 上限 256KB，防止滥用为文件存储（文件走 bundle 通道）。 */
    private static final int MAX_CONTENT_BYTES = 256 * 1024;

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9-]{1,64}$");
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    private final UserConfigEntryRepository repository;

    public UserConfigService(UserConfigEntryRepository repository) {
        this.repository = repository;
    }

    /** 抛出该异常表示请求非法（400）。 */
    public static class ConfigSyncBadRequestException extends RuntimeException {
        public ConfigSyncBadRequestException(String message) {
            super(message);
        }
    }

    public List<ConfigEntryDto> list(Long userId, String namespace) {
        if (userId == null) return List.of();
        List<UserConfigEntryEntity> entities;
        if (namespace == null || namespace.isBlank()) {
            entities = repository.findByUserId(userId);
        } else {
            validateNamespace(namespace);
            entities = repository.findByUserIdAndNamespace(userId, namespace);
        }
        return entities.stream().map(this::toDto).toList();
    }

    public Optional<ConfigEntryDto> get(Long userId, String namespace, String key) {
        if (userId == null) return Optional.empty();
        validateNamespace(namespace);
        validateKey(key);
        return repository.findByUserIdAndNamespaceAndConfigKey(userId, namespace, key)
                .map(this::toDto);
    }

    @Transactional
    public ConfigEntryDto put(Long userId, String namespace, String key, ConfigPutRequest request) {
        if (userId == null) throw new ConfigSyncBadRequestException("userId must not be null");
        validateNamespace(namespace);
        validateKey(key);
        if (request == null || request.contentJson() == null || request.contentJson().isBlank()) {
            throw new ConfigSyncBadRequestException("配置内容为空");
        }
        byte[] bytes = request.contentJson().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONTENT_BYTES) {
            throw new ConfigSyncBadRequestException("配置内容超出大小限制");
        }
        String computedHash = sha256Hex(bytes);
        if (request.hash() != null && !request.hash().isBlank()
                && !computedHash.equalsIgnoreCase(request.hash())) {
            throw new ConfigSyncBadRequestException("内容指纹校验失败");
        }

        UserConfigEntryEntity entity = repository
                .findByUserIdAndNamespaceAndConfigKey(userId, namespace, key)
                .orElseGet(UserConfigEntryEntity::new);
        boolean isNew = entity.getId() == null;
        entity.setUserId(userId);
        entity.setNamespace(namespace);
        entity.setConfigKey(key);
        entity.setContentJson(request.contentJson());
        entity.setContentHash(computedHash);
        entity.setVersion(isNew ? 1L : entity.getVersion() + 1);
        entity.setClientType(truncate(request.clientType(), 32));
        UserConfigEntryEntity saved = repository.save(entity);
        log.info("Synced config put: userId={}, namespace={}, key={}, version={}",
                userId, namespace, key, saved.getVersion());
        return toDto(saved);
    }

    @Transactional
    public boolean delete(Long userId, String namespace, String key) {
        if (userId == null) return false;
        validateNamespace(namespace);
        validateKey(key);
        Optional<UserConfigEntryEntity> entityOpt =
                repository.findByUserIdAndNamespaceAndConfigKey(userId, namespace, key);
        if (entityOpt.isEmpty()) {
            return false;
        }
        repository.deleteByUserIdAndNamespaceAndConfigKey(userId, namespace, key);
        return true;
    }

    // ==================== helpers ====================

    private ConfigEntryDto toDto(UserConfigEntryEntity e) {
        return new ConfigEntryDto(
                e.getNamespace(),
                e.getConfigKey(),
                e.getContentJson(),
                e.getContentHash(),
                e.getVersion() == null ? 1L : e.getVersion(),
                e.getClientType(),
                e.getUpdatedAt() == null ? null : e.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private void validateNamespace(String namespace) {
        if (namespace == null || !NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new ConfigSyncBadRequestException("非法的配置 namespace");
        }
    }

    private void validateKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new ConfigSyncBadRequestException("非法的配置 key");
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
