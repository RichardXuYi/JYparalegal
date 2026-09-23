package com.jyfc.backend.module.voice.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyfc.backend.core.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * 腾讯云 TRTC 真实语音适配器（M3' 生产通道）。
 * <p>
 * UserSig 按公开的 TLSSigAPIv2 算法本地生成：
 * ① content = {"TLS.ver":"2.0","TLS.identifier","TLS.sdkappid","TLS.time","TLS.expire"}（兼容字段 TLS.userid 一并携带）；
 * ② raw_sig = Base64(HMAC-SHA256(secret_key, content_json))；
 * ③ sig json 含 TLS.sig / TLS.identifier / TLS.userid / TLS.sdkappid / TLS.time / TLS.expire；
 * ④ zlib deflate 压缩后做 base64-url 变换（'+'→'*'，'/'→'-'，'='→'_'）。
 * <p>
 * startRoom 走 TRTC REST（https://console.tim.qq.com/v4/...），以管理员 UserSig 鉴权。
 * ⚠️ 生产切换需腾讯云凭据（jy.voice.trtc.sdk-app-id / secret-key），未配置时快速失败；
 * 缺省环境仍走 {@link StubVoiceAdapter}。
 */
@Component
@ConditionalOnProperty(name = "jy.voice.provider", havingValue = "trtc")
public class TrtcVoiceAdapter implements VoiceAdapter {

    private static final Logger log = LoggerFactory.getLogger(TrtcVoiceAdapter.class);

    /** TLSSigAPIv2 版本号。 */
    private static final String TLS_VER = "2.0";

    /** TRTC 服务端 REST 基地址（console.tim.qq.com 风格 v4 接口）。 */
    @Value("${jy.voice.trtc.rest-url:https://console.tim.qq.com}")
    private String restUrl;

    /** TRTC 应用 SDKAppID（生产凭据，缺省为空 = 未开通）。 */
    @Value("${jy.voice.trtc.sdk-app-id:0}")
    private long sdkAppId;

    /** TRTC 应用密钥（UserSig HMAC 签名用，生产凭据，缺省为空 = 未开通）。 */
    @Value("${jy.voice.trtc.secret-key:}")
    private String secretKey;

    /** 管理员账号 identifier（服务端 REST 调用身份）。 */
    @Value("${jy.voice.trtc.admin-identifier:admin}")
    private String adminIdentifier;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TrtcVoiceAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ==================== VoiceAdapter 契约实现 ====================

    @Override
    public Map<String, Object> startRoom(Long sessionId, String userId) {
        requireCredentials();
        String adminSig = genUserSig(adminIdentifier, 600);
        // 房间号 = 会话 id（留痕归因：一个语音会话对应一个 TRTC 房间）
        long roomId = sessionId;
        String url = restUrl + "/v4/trtc_room_ctrl/start"
                + "?sdkappid=" + sdkAppId
                + "&identifier=" + enc(adminIdentifier)
                + "&usersig=" + enc(adminSig)
                + "&random=" + new Random().nextInt(99999)
                + "&contenttype=json";
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("RoomId", roomId);
            body.put("RoomName", "jy-voice-session-" + sessionId);
            body.put("OwnerUserId", userId);
            String reqJson = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(reqJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                log.error("TRTC startRoom HTTP {}: {}", resp.statusCode(), resp.body());
                throw new BusinessException("TRTC 开房失败：HTTP " + resp.statusCode());
            }
            JsonNode json = objectMapper.readTree(resp.body());
            int errorCode = json.path("ErrorCode").asInt(0);
            String actionStatus = json.path("ActionStatus").asText("OK");
            if (errorCode != 0 || !"OK".equals(actionStatus)) {
                throw new BusinessException("TRTC 开房失败：" + json.path("ErrorInfo").asText("ErrorCode=" + errorCode));
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("provider", provider());
            out.put("sdkAppId", sdkAppId);
            out.put("roomId", roomId);
            out.put("userId", userId);
            // 客户端入房凭据：默认 24h 有效期
            out.put("userSig", genUserSig(userId, 86400));
            return out;
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("TRTC 开房调用被中断", e);
        } catch (Exception e) {
            throw new BusinessException("TRTC 开房调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String genUserSig(String userId, int expireSec) {
        requireCredentials();
        try {
            long time = System.currentTimeMillis() / 1000;
            int expire = expireSec <= 0 ? 86400 : expireSec;

            // ① 待签名 content（TLSSigAPIv2 公开算法字段序）
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("TLS.ver", TLS_VER);
            content.put("TLS.identifier", userId);
            content.put("TLS.sdkappid", sdkAppId);
            content.put("TLS.time", time);
            content.put("TLS.expire", expire);
            String contentJson = objectMapper.writeValueAsString(content);

            // ② HMAC-SHA256 → 标准 Base64
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawSig = mac.doFinal(contentJson.getBytes(StandardCharsets.UTF_8));
            String sigBase64 = Base64.getEncoder().encodeToString(rawSig);

            // ③ 签名结果 json（含兼容字段 TLS.userid）
            Map<String, Object> sig = new LinkedHashMap<>();
            sig.put("TLS.ver", TLS_VER);
            sig.put("TLS.sig", sigBase64);
            sig.put("TLS.identifier", userId);
            sig.put("TLS.userid", userId);
            sig.put("TLS.sdkappid", sdkAppId);
            sig.put("TLS.time", time);
            sig.put("TLS.expire", expire);
            byte[] sigJsonBytes = objectMapper.writeValueAsBytes(sig);

            // ④ zlib 压缩 + base64-url 变换
            return base64EncodeUrl(zlibCompress(sigJsonBytes));
        } catch (Exception e) {
            throw new BusinessException("TRTC UserSig 生成失败: " + e.getMessage(), e);
        }
    }

    /** 适配器标识（审计/归因用）。 */
    public String provider() {
        return "tencent-trtc";
    }

    // ==================== TLSSigAPIv2 工具 ====================

    /** zlib（deflate）压缩，TLSSigAPIv2 官方实现同款参数。 */
    private static byte[] zlibCompress(byte[] data) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        Deflater compressor = new Deflater();
        compressor.setInput(data);
        compressor.finish();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(bos, compressor)) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = compressor.deflate(buf)) > 0) {
                dos.write(buf, 0, n);
            }
        }
        compressor.end();
        return bos.toByteArray();
    }

    /** TLSSigAPIv2 base64-url 变换：'+'→'*'，'/'→'-'，'='→'_'（无填充语义保留）。 */
    private static String base64EncodeUrl(byte[] data) {
        return Base64.getUrlEncoder().encodeToString(data)
                .replace("+", "*")
                .replace("/", "-")
                .replace("=", "_");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** 凭据缺失时快速失败，避免以空密钥签出无效 UserSig。 */
    private void requireCredentials() {
        if (sdkAppId <= 0 || secretKey == null || secretKey.isBlank()) {
            throw new BusinessException("TRTC 凭据未配置（jy.voice.trtc.sdk-app-id / secret-key）——"
                    + "生产切换需腾讯云凭据，未到位前请使用 stub 通道");
        }
    }
}
