package com.jyfc.backend.module.integration.feishu.security;

import com.jyfc.backend.module.integration.feishu.config.FeishuProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书事件签名验证与加密解密。
 * <p>
 * 功能：
 * 1. URL challenge 验证（首次配置事件回调 URL 时，飞书会发送 GET 请求验证）
 * 2. Event signature 验证（每次事件推送时，验证 HMAC-SHA256 签名）
 * 3. AES-256-CBC 解密事件 payload（当配置了 encrypt_key 时）
 * 4. 防重放攻击（缓存最近 5 分钟的 timestamp+nonce）
 */
@Component
public class FeishuSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(FeishuSignatureVerifier.class);

    /** 防重放缓存（timestamp:nonce -> true），5 分钟过期 */
    private final ConcurrentHashMap<String, Long> replayCache = new ConcurrentHashMap<>();

    /** 防重放窗口（毫秒），默认 5 分钟 */
    private static final long REPLAY_WINDOW_MS = 5 * 60 * 1000;

    private final FeishuProperties properties;

    public FeishuSignatureVerifier(FeishuProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("飞书签名验证器初始化完成");
    }

    /**
     * 处理 URL challenge 验证请求。
     * <p>
     * 飞书在配置事件回调 URL 时，会发送如下格式的请求：
     * <pre>
     * {
     *   "challenge": "xxx",
     *   "token": "***",
     *   "type": "url_verification"
     * }
     * </pre>
     * 需要返回 {"challenge": "xxx"}
     *
     * @param body 请求体
     * @return 包含 challenge 的响应 Map
     */
    public Map<String, Object> handleUrlChallenge(Map<String, Object> body) {
        // 验证 token
        String receivedToken = (String) body.get("token");
        if (receivedToken == null || !receivedToken.equals(properties.getVerificationToken())) {
            log.warn("URL challenge token 验证失败");
            throw new SecurityException("Invalid verification token");
        }

        // 检查 type
        String type = (String) body.get("type");
        if (!"url_verification".equals(type)) {
            throw new SecurityException("Invalid type, expected url_verification");
        }

        String challenge = (String) body.get("challenge");
        if (challenge == null || challenge.isBlank()) {
            throw new SecurityException("Missing challenge field");
        }

        log.info("飞书 URL challenge 验证通过");
        return Map.of("challenge", challenge);
    }

    /**
     * 验证飞书事件推送的签名。
     * <p>
     * 签名算法：HMAC-SHA256(timestamp + nonce + body, verification_token)
     * 签名在请求头中传递：X-Lark-Signature
     *
     * @param timestamp  X-Lark-Request-Timestamp 头
     * @param nonce      X-Lark-Request-Nonce 头
     * @param body       原始请求体字符串
     * @param signature  X-Lark-Signature 头
     * @return true 如果签名有效
     */
    public boolean verifySignature(String timestamp, String nonce, String body, String signature) {
        if (signature == null || timestamp == null || nonce == null || body == null) {
            log.warn("飞书签名验证参数缺失");
            return false;
        }

        // 防重放：检查 timestamp + nonce 是否已处理过
        String replayKey = timestamp + ":" + nonce;
        if (replayCache.containsKey(replayKey)) {
            log.warn("飞书事件重复推送，已拦截: {}", replayKey);
            return false;
        }

        // 计算签名
        String data = timestamp + nonce + body;
        String expectedSignature = hmacSha256(data, properties.getVerificationToken());

        boolean valid = expectedSignature.equalsIgnoreCase(signature);
        if (valid) {
            // 缓存防重放标记，1 小时后自动过期
            replayCache.put(replayKey, System.currentTimeMillis() + REPLAY_WINDOW_MS);
            // 清理过期的缓存条目
            cleanReplayCache();
        } else {
            log.warn("飞书事件签名验证失败: expected={}, received={}", expectedSignature, signature);
        }

        return valid;
    }

    /**
     * 解密飞书事件推送的加密 payload。
     * <p>
     * 当在飞书开放平台配置了 encrypt_key 后，
     * 所有事件推送会被加密并包装在 {"encrypt": "..."} 中。
     * 使用 AES-256-CBC 解密，密钥为 encrypt_key 的 Base64 解码。
     *
     * @param encryptBase64 请求体中 encrypt 字段的值
     * @return 解密后的明文 JSON 字符串
     */
    public String decryptPayload(String encryptBase64) {
        String encryptKey = properties.getEncryptKey();
        if (encryptKey == null || encryptKey.isBlank()) {
            log.warn("飞书 encrypt_key 未配置，无法解密事件 payload");
            return null;
        }

        try {
            // 1. Base64 解码密钥（密钥是 32 字节的 Base64 编码）
            byte[] keyBytes = Base64.getDecoder().decode(encryptKey);

            // 2. Base64 解码密文（encrypt 字段的值也是 Base64 编码）
            byte[] encryptedData = Base64.getDecoder().decode(encryptBase64);

            // 3. AES-256-CBC 解密，PKCS5Padding
            // 飞书使用前 16 字节作为 IV
            byte[] iv = new byte[16];
            byte[] cipherData = new byte[encryptedData.length - 16];
            System.arraycopy(encryptedData, 0, iv, 0, 16);
            System.arraycopy(encryptedData, 16, cipherData, 0, cipherData.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(cipherData);

            // 4. 去除 PKCS7 填充并转字符串
            String plainText = new String(decrypted, StandardCharsets.UTF_8);
            log.debug("飞书事件 payload 解密成功");
            return plainText;

        } catch (Exception e) {
            log.error("飞书事件 payload 解密失败", e);
            throw new SecurityException("Feishu event decryption failed: " + e.getMessage());
        }
    }

    // ===== 内部方法 =====

    /**
     * 计算 HMAC-SHA256。
     */
    private String hmacSha256(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new SecurityException("HMAC-SHA256 计算失败", e);
        }
    }

    /**
     * 字节数组转小写十六进制字符串。
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * 清理过期的防重放缓存条目。
     */
    private void cleanReplayCache() {
        long now = System.currentTimeMillis();
        replayCache.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
