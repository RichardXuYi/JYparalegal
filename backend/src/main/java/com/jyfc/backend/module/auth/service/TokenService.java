package com.jyfc.backend.module.auth.service;

import com.jyfc.backend.core.security.jwt.JwtService;
import com.jyfc.backend.module.auth.dto.AuthDeviceDTO;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.entity.UserRefreshTokenEntity;
import com.jyfc.backend.module.auth.repository.UserRefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 跨端 Token 认证的核心服务：签发 access/refresh、刷新轮换、吊销。
 *
 * <p>refresh token 明文仅在响应中下发一次，服务端仅保存其 SHA-256 哈希用于校验与吊销。</p>
 */
@Service
public class TokenService {

    /** 登录/刷新成功后返回给客户端的令牌对。 */
    public record TokenPair(String accessToken, String refreshToken, long expiresIn, Long deviceId) {}

    private final JwtService jwtService;
    private final UserRefreshTokenRepository refreshTokenRepository;

    public TokenService(JwtService jwtService, UserRefreshTokenRepository refreshTokenRepository) {
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /** 为用户签发一对新令牌，并持久化 refresh token 哈希。 */
    @Transactional
    public TokenPair issueForUser(UserEntity user, String deviceName, String ipAddress, String userAgent) {
        if (user == null) throw new IllegalArgumentException("User must not be null");
        Long userId = user.getId();
        if (userId == null) throw new IllegalArgumentException("User ID must not be null");
        return issue(userId, user.getUsername(), deviceName, ipAddress, userAgent);
    }

    @Transactional
    public TokenPair issue(Long userId, String username, String deviceName, String ipAddress, String userAgent) {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (username == null) throw new IllegalArgumentException("username must not be null");
        String access = jwtService.generateAccessToken(username, List.of("ROLE_USER"));
        String refresh = jwtService.generateRefreshToken();

        UserRefreshTokenEntity record = new UserRefreshTokenEntity();
        record.setUserId(userId);
        record.setUsername(username);
        record.setTokenHash(sha256(refresh));
        record.setRevoked(false);
        record.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTtlSeconds()));
        record.setDeviceName(deviceName);
        record.setIpAddress(ipAddress);
        record.setUserAgent(userAgent);
        refreshTokenRepository.save(record);

        return new TokenPair(access, refresh, jwtService.getAccessTtlSeconds(), record.getId());
    }

    /**
     * 用 refresh token 轮换出新的令牌对：校验有效性 → 吊销旧记录 → 签发新对。
     *
     * @return 新令牌对；若 refresh token 无效/已吊销/过期则返回 {@link Optional#empty()}
     */
    @Transactional
    public Optional<TokenPair> rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        Optional<UserRefreshTokenEntity> recordOpt = refreshTokenRepository.findByTokenHash(sha256(refreshToken));
        if (recordOpt.isEmpty()) {
            return Optional.empty();
        }
        UserRefreshTokenEntity record = recordOpt.get();
        if (Boolean.TRUE.equals(record.getRevoked()) || record.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        // 轮换：吊销旧的，签发新对（继承设备元数据）
        record.setRevoked(true);
        refreshTokenRepository.save(record);
        return Optional.of(issue(record.getUserId(), record.getUsername(),
                record.getDeviceName(), record.getIpAddress(), record.getUserAgent()));
    }

    /** 吊销指定 refresh token（登出）。找不到则静默忽略。 */
    @Transactional
    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(sha256(refreshToken)).ifPresent(record -> {
            record.setRevoked(true);
            refreshTokenRepository.save(record);
        });
    }

    /**
     * 吊销某用户全部仍然有效的 refresh token：改密后各端无法再刷新，只能重新登录。
     *
     * @return 被吊销的令牌数
     */
    @Transactional
    public int revokeAllForUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        return refreshTokenRepository.revokeAllByUserId(userId);
    }

    /**
     * 查询某用户所有活跃设备（未吊销的 refresh token 记录）。
     */
    @Transactional(readOnly = true)
    public List<AuthDeviceDTO> listActiveDevices(Long userId) {
        if (userId == null) return List.of();
        return refreshTokenRepository.findByUserIdAndRevokedFalse(userId).stream()
                .map(r -> new AuthDeviceDTO(
                        r.getId(),
                        r.getDeviceName(),
                        r.getIpAddress(),
                        r.getUserAgent(),
                        r.getCreatedAt(),
                        r.getExpiresAt()))
                .toList();
    }

    /**
     * 按设备记录 ID 吊销指定用户的 refresh token（主动断连）。
     *
     * @return true 表示成功吊销；false 表示记录不存在或不属于该用户
     */
    @Transactional
    public boolean revokeDevice(Long deviceId, Long userId) {
        if (deviceId == null || userId == null) return false;
        return refreshTokenRepository.revokeByIdAndUserId(deviceId, userId) > 0;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
