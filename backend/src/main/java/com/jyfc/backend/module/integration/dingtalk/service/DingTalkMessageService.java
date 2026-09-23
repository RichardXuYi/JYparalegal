package com.jyfc.backend.module.integration.dingtalk.service;

import com.jyfc.backend.module.integration.dingtalk.config.DingTalkProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉消息推送服务。
 * <p>
 * 支持的消息类型：
 * <ul>
 *   <li>工作通知（企业内部应用消息）- 合同审批、到期提醒等</li>
 *   <li>群消息（群会话消息）</li>
 * </ul>
 * 消息格式：text / markdown / action_card
 */
@Service
public class DingTalkMessageService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkMessageService.class);

    private final DingTalkProperties properties;
    private final DingTalkAuthService authService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DingTalkMessageService(DingTalkProperties properties,
                                  DingTalkAuthService authService,
                                  ObjectMapper objectMapper) {
        this.properties = properties;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 发送工作通知（企业内部应用消息）。
     * <p>
     * 用于合同审批通知、到期提醒等场景。
     *
     * @param userId  钉钉 userId（企业内部用户 ID）
     * @param title   消息标题
     * @param content 消息内容
     * @param url     点击消息后跳转的链接（可选）
     * @return 发送结果
     */
    public Map<String, Object> sendWorkMessage(String userId, String title, String content, String url) {
        log.info("发送工作通知给用户: {}", userId);

        String accessToken = authService.getAccessToken();

        // 构建 action_card 消息体
        Map<String, Object> actionCard = new HashMap<>();
        actionCard.put("title", title);
        actionCard.put("markdown", content);
        if (url != null && !url.isEmpty()) {
            actionCard.put("single_title", "查看详情");
            actionCard.put("single_url", url);
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("msgtype", "action_card");
        msg.put("action_card", actionCard);

        Map<String, Object> body = new HashMap<>();
        body.put("agent_id", properties.getAgentId());
        body.put("userid_list", userId);
        body.put("msg", msg);

        return sendMessage("/topapi/message/corpconversation/asyncsend_v2", accessToken, body);
    }

    /**
     * 发送工作通知给多个用户。
     *
     * @param userIds 钉钉 userId 列表
     * @param title   消息标题
     * @param content 消息内容（Markdown 格式）
     * @param url     点击跳转链接（可选）
     * @return 发送结果
     */
    public Map<String, Object> sendWorkMessageToUsers(List<String> userIds, String title, String content, String url) {
        log.info("批量发送工作通知给 {} 个用户", userIds.size());

        String accessToken = authService.getAccessToken();

        Map<String, Object> actionCard = new HashMap<>();
        actionCard.put("title", title);
        actionCard.put("markdown", content);
        if (url != null && !url.isEmpty()) {
            actionCard.put("single_title", "查看详情");
            actionCard.put("single_url", url);
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("msgtype", "action_card");
        msg.put("action_card", actionCard);

        Map<String, Object> body = new HashMap<>();
        body.put("agent_id", properties.getAgentId());
        body.put("userid_list", String.join(",", userIds));
        body.put("msg", msg);

        return sendMessage("/topapi/message/corpconversation/asyncsend_v2", accessToken, body);
    }

    /**
     * 发送群消息。
     *
     * @param chatId  群会话 ID
     * @param content 消息内容（Markdown 格式）
     * @return 发送结果
     */
    public Map<String, Object> sendGroupMessage(String chatId, String content) {
        log.info("发送群消息到群: {}", chatId);

        String accessToken = authService.getAccessToken();

        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", "JYFC 通知");
        markdown.put("text", content);

        Map<String, Object> msg = new HashMap<>();
        msg.put("msgtype", "markdown");
        msg.put("markdown", markdown);

        Map<String, Object> body = new HashMap<>();
        body.put("chatbotId", chatId);
        body.put("msg", msg);

        return sendMessage("/v1.0/robot/groupMessages/send", accessToken, body);
    }

    /**
     * 发送文本消息到群。
     *
     * @param chatId 群会话 ID
     * @param text   文本内容
     * @return 发送结果
     */
    public Map<String, Object> sendGroupTextMessage(String chatId, String text) {
        log.info("发送文本消息到群: {}", chatId);

        String accessToken = authService.getAccessToken();

        Map<String, Object> textMsg = new HashMap<>();
        textMsg.put("content", text);

        Map<String, Object> msg = new HashMap<>();
        msg.put("msgtype", "text");
        msg.put("text", textMsg);

        Map<String, Object> body = new HashMap<>();
        body.put("chatbotId", chatId);
        body.put("msg", msg);

        return sendMessage("/v1.0/robot/groupMessages/send", accessToken, body);
    }

    /**
     * 通用消息发送方法。
     */
    private Map<String, Object> sendMessage(String path, String accessToken, Map<String, Object> body) {
        try {
            String response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("access_token", accessToken)
                            .build())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode json = objectMapper.readTree(response);
            int errcode = json.path("errcode").asInt(-1);
            if (errcode != 0) {
                String errmsg = json.path("errmsg").asText("unknown error");
                log.error("发送消息失败: errcode={}, errmsg={}", errcode, errmsg);
                return Map.of(
                        "success", false,
                        "errcode", errcode,
                        "errmsg", errmsg
                );
            }

            log.info("消息发送成功");
            return Map.of(
                    "success", true,
                    "errcode", 0,
                    "errmsg", "ok"
            );
        } catch (Exception e) {
            log.error("发送消息异常", e);
            return Map.of(
                    "success", false,
                    "errcode", -1,
                    "errmsg", e.getMessage()
            );
        }
    }
}
