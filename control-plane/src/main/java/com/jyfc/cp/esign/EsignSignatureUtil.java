package com.jyfc.cp.esign;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * e签宝 SaaS API V3 签名工具。
 * <p>
 * 签名算法：HMAC-SHA256，待签名字符串 = METHOD\nAccept\nContent-MD5\nContent-Type\n\nURL
 * 回调验签：HMAC-SHA256(timestamp + query + body, appSecret) → hex
 */
public final class EsignSignatureUtil {

    private EsignSignatureUtil() {}

    /**
     * 计算 Content-MD5（MD5 → Base64）。
     */
    public static String contentMd5(String body) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(body.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeBase64String(digest);
        } catch (Exception e) {
            throw new IllegalStateException("MD5 computation failed", e);
        }
    }

    /**
     * 计算文件字节的 Content-MD5（MD5 → Base64）——上传 e签宝文件时必需。
     */
    public static String contentMd5(byte[] body) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return Base64.encodeBase64String(md5.digest(body));
        } catch (Exception e) {
            throw new IllegalStateException("MD5 computation failed", e);
        }
    }

    /**
     * 构造待签名字符串并计算 HMAC-SHA256 签名（Base64）。
     *
     * @param method      HTTP method (GET/POST/PUT/DELETE)
     * @param accept      Accept header value
     * @param contentMd5  Content-MD5 value (empty for GET/DELETE)
     * @param contentType Content-Type header value
     * @param url         API path (e.g. /v3/sign-flow/create-by-file)
     * @param secret      App Secret
     */
    public static String sign(String method, String accept, String contentMd5,
                              String contentType, String url, String secret) {
        // 待签名字符串: METHOD\nAccept\nContent-MD5\nContent-Type\n\nURL
        String message = method + "\n"
                + accept + "\n"
                + contentMd5 + "\n"
                + contentType + "\n"
                + "\n"   // Date is empty
                + url;
        return hmacSha256Base64(message, secret);
    }

    /**
     * e签宝回调验签：HMAC-SHA256(timestamp + query + body, key) → hex string。
     */
    public static boolean verifyCallback(String timestamp, String query, String body,
                                         String key, String signature) {
        String data = timestamp + query + body;
        String computed = hmacSha256Hex(data, key);
        return computed.equalsIgnoreCase(signature);
    }

    // ---- internal ----

    private static String hmacSha256Base64(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeBase64String(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    private static String hmacSha256Hex(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 hex failed", e);
        }
    }
}
