package com.jyfc.backend.module.integration.feishu.service;

import com.jyfc.backend.module.integration.feishu.config.FeishuProperties;
import com.jyfc.backend.module.integration.feishu.exception.FeishuApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书消息服务。
 * <p>
 * 支持发送文本、富文本、消息卡片、批量消息、图片/文件上传。
 * 用于合同审批通知、到期提醒等场景。
 */
@Service
public class FeishuMessageService {

    private static final Logger log = LoggerFactory.getLogger(FeishuMessageService.class);

    @SuppressWarnings("unused")
    private final FeishuProperties properties;
    private final FeishuTokenService tokenService;
    private final WebClient webClient;

    public FeishuMessageService(FeishuProperties properties,
                                 FeishuTokenService tokenService,
                                 WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    /**
     * 发送消息到指定用户或群聊。
     * <p>
     * receive_id_type 支持：open_id、union_id、user_id、chat_id
     *
     * @param receiveId     接收者 ID
     * @param receiveIdType 接收者 ID 类型（open_id / union_id / user_id / chat_id）
     * @param msgType       消息类型（text / post / image / interactive）
     * @param content       消息内容 JSON 字符串
     * @return 飞书 API 响应
     */
    public Map<String, Object> sendMessage(String receiveId, String receiveIdType, String msgType, String content) {
        String token = tokenService.getTenantAccessToken();

        Map<String, Object> body = new HashMap<>();
        body.put("receive_id", receiveId);
        body.put("msg_type", msgType);
        body.put("content", content);

        Map<String, Object> response = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/im/v1/messages")
                        .queryParam("receive_id_type", receiveIdType)
                        .build())
                .header("Authorization", "Bearer " + token)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10))
                .block();

        return checkResponse(response, "发送飞书消息");
    }

    /**
     * 发送消息卡片（交互式消息）。
     * 卡片内容为飞书消息卡片 JSON 格式。
     *
     * @param receiveId 接收者 ID
     * @param cardJson  卡片 JSON 字符串
     * @param receiveIdType 接收者 ID 类型（默认 open_id）
     * @return 飞书 API 响应
     */
    public Map<String, Object> sendCard(String receiveId, String cardJson) {
        return sendMessage(receiveId, "open_id", "interactive", cardJson);
    }

    /**
     * 发送消息卡片到指定聊天（群聊）。
     *
     * @param chatId   群聊 ID
     * @param cardJson 卡片 JSON 字符串
     * @return 飞书 API 响应
     */
    public Map<String, Object> sendCardToChat(String chatId, String cardJson) {
        return sendMessage(chatId, "chat_id", "interactive", cardJson);
    }

    /**
     * 批量发送消息（逐个发送到多个用户）。
     * 飞书批量消息 API 有限制，此处用循环逐个发送。
     *
     * @param userIds 用户 ID 列表（open_id）
     * @param content 文本消息内容
     * @return 发送结果统计
     */
    public Map<String, Object> sendBatchMessage(List<String> userIds, String content) {
        String jsonContent = "{\"text\":\"" + escapeJson(content) + "\"}";

        int success = 0;
        int failed = 0;
        for (String userId : userIds) {
            try {
                sendMessage(userId, "open_id", "text", jsonContent);
                success++;
            } catch (Exception e) {
                log.warn("批量消息发送失败 userId={}: {}", userId, e.getMessage());
                failed++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", userIds.size());
        result.put("success", success);
        result.put("failed", failed);
        return result;
    }

    /**
     * 发送文本消息（便捷方法）。
     *
     * @param receiveId     接收者 ID
     * @param receiveIdType 接收者 ID 类型
     * @param text          文本内容
     * @return 飞书 API 响应
     */
    public Map<String, Object> sendText(String receiveId, String receiveIdType, String text) {
        String jsonContent = "{\"text\":\"" + escapeJson(text) + "\"}";
        return sendMessage(receiveId, receiveIdType, "text", jsonContent);
    }

    /**
     * 上传图片素材。
     * <p>
     * 图片可用于消息卡片或富文本中。
     *
     * @param imageBytes 图片字节数据
     * @param imageName  图片文件名
     * @param imageType  图片类型（message / avatar）
     * @return 飞书 API 响应，包含 image_key
     */
    public Map<String, Object> uploadImage(byte[] imageBytes, String imageName, String imageType) {
        String token = tokenService.getTenantAccessToken();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("image_type", imageType != null ? imageType : "message");
        builder.part("image", new ByteArrayResource(imageBytes))
                .filename(imageName != null ? imageName : "image.png");

        Map<String, Object> response = webClient.post()
                .uri("/im/v1/images")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(30))
                .block();

        return checkResponse(response, "上传飞书图片");
    }

    /**
     * 上传文件素材。
     *
     * @param fileBytes 文件字节数据
     * @param fileName  文件名
     * @param fileType  文件类型（opus / stream）
     * @return 飞书 API 响应，包含 file_key
     */
    public Map<String, Object> uploadFile(byte[] fileBytes, String fileName, String fileType) {
        String token = tokenService.getTenantAccessToken();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file_type", fileType != null ? fileType : "stream");
        builder.part("file_name", fileName != null ? fileName : "file");
        builder.part("file", new ByteArrayResource(fileBytes))
                .filename(fileName != null ? fileName : "file.bin");

        Map<String, Object> response = webClient.post()
                .uri("/im/v1/files")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(60))
                .block();

        return checkResponse(response, "上传飞书文件");
    }

    // ===== 内部工具方法 =====

    /**
     * 检查飞书 API 响应是否包含错误码。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> checkResponse(Map<String, Object> response, String action) {
        if (response == null) {
            throw new FeishuApiException(-1, action + "：响应为空");
        }

        Number code = (Number) response.get("code");
        if (code != null && code.longValue() != 0) {
            String msg = (String) response.get("msg");
            log.error("{} 失败: code={}, msg={}", action, code, msg);
            throw new FeishuApiException(code.intValue(), msg != null ? msg : "Unknown error");
        }

        return (Map<String, Object>) response.get("data");
    }

    /**
     * 转义 JSON 字符串中的特殊字符。
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
