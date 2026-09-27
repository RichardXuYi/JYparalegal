package com.jyfc.cp.esign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * e签宝 SaaS API V3 HTTP 客户端。
 * <p>
 * 封装签名鉴权和 HTTP 调用，所有方法返回解析后的 JsonNode。
 */
@Component
public class EsignClient {

    private static final Logger log = LoggerFactory.getLogger(EsignClient.class);
    private static final String ACCEPT = "*/*";
    private static final String CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final String AUTH_MODE = "Signature";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${cp.esign.host:https://smlopenapi.esign.cn}")
    private String host;

    @Value("${cp.esign.app-id}")
    private String appId;

    @Value("${cp.esign.app-secret}")
    private String appSecret;

    /**
     * POST 请求 e签宝 API。
     */
    public JsonNode post(String apiPath, Object body) {
        try {
            String jsonBody = (body != null) ? mapper.writeValueAsString(body) : "{}";
            String contentMd5 = EsignSignatureUtil.contentMd5(jsonBody);
            String signature = EsignSignatureUtil.sign("POST", ACCEPT, contentMd5, CONTENT_TYPE, apiPath, appSecret);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(host + apiPath))
                    .timeout(Duration.ofSeconds(30))
                    .header("X-Tsign-Open-App-Id", appId)
                    .header("X-Tsign-Open-Ca-Timestamp", String.valueOf(System.currentTimeMillis()))
                    .header("Accept", ACCEPT)
                    .header("Content-MD5", contentMd5)
                    .header("Content-Type", CONTENT_TYPE)
                    .header("X-Tsign-Open-Auth-Mode", AUTH_MODE)
                    .header("X-Tsign-Open-Ca-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("e签宝 POST {} → {}", apiPath, response.statusCode());
            return mapper.readTree(response.body());
        } catch (Exception e) {
            throw new EsignApiException("e签宝 API 调用失败 [" + apiPath + "]: " + e.getMessage(), e);
        }
    }

    /**
     * GET 请求 e签宝 API。
     */
    public JsonNode get(String apiPath) {
        try {
            String signature = EsignSignatureUtil.sign("GET", ACCEPT, "", CONTENT_TYPE, apiPath, appSecret);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(host + apiPath))
                    .timeout(Duration.ofSeconds(30))
                    .header("X-Tsign-Open-App-Id", appId)
                    .header("X-Tsign-Open-Ca-Timestamp", String.valueOf(System.currentTimeMillis()))
                    .header("Accept", ACCEPT)
                    .header("Content-Type", CONTENT_TYPE)
                    .header("X-Tsign-Open-Auth-Mode", AUTH_MODE)
                    .header("X-Tsign-Open-Ca-Signature", signature)
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("e签宝 GET {} → {}", apiPath, response.statusCode());
            return mapper.readTree(response.body());
        } catch (Exception e) {
            throw new EsignApiException("e签宝 API 调用失败 [" + apiPath + "]: " + e.getMessage(), e);
        }
    }

    /**
     * PUT 原始字节到 e签宝返回的上传地址。
     * <p>该地址由 file-upload-url 下发，<b>不走 e签宝签名鉴权</b>，仅需 Content-MD5 与 Content-Type。
     * 上传成功通常返回 {"errCode":"0"}（可能空体）。
     */
    public JsonNode putRaw(String url, byte[] bytes, String contentMd5, String contentType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-MD5", contentMd5)
                    .header("Content-Type", contentType)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("e签宝 上传 PUT → {}", response.statusCode());
            String bodyStr = response.body();
            if (bodyStr == null || bodyStr.isBlank()) {
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("errCode", response.statusCode() / 100 == 2 ? "0" : String.valueOf(response.statusCode()));
                return mapper.valueToTree(ok);
            }
            return mapper.readTree(bodyStr);
        } catch (Exception e) {
            throw new EsignApiException("e签宝文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查 e签宝响应是否成功（code == 0）。
     */
    public static boolean isSuccess(JsonNode node) {
        return node != null && node.path("code").asInt(-1) == 0;
    }

    /**
     * 提取 data 节点。
     */
    public static JsonNode data(JsonNode node) {
        return node != null ? node.path("data") : null;
    }
}
