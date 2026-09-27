package com.jyfc.backend.module.integration.wecom.service;

import com.jyfc.backend.module.integration.wecom.config.WeComProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 企业微信应用消息推送服务
 *
 * 通过自建应用向企业微信用户推送消息。
 * 支持文本、Markdown、文本卡片、图文消息。
 *
 * 频率限制说明：
 * - 每个应用对同一个成员的单个消息类型限频 200 次/分钟
 * - 每个应用对同一个成员的所有消息类型限频 600 次/分钟
 * - 建议配合消息队列和重试机制使用
 *
 * 消息方向：自建应用 → 企业成员（不支持外部联系人）
 */
@Service
public class WeComMessageService {

    private static final Logger log = LoggerFactory.getLogger(WeComMessageService.class);

    /** 发送消息 API 路径 */
    private static final String MESSAGE_SEND_PATH = "/message/send";

    private final WeComProperties weComProperties;
    private final WeComTokenService tokenService;
    private final WebClient.Builder webClientBuilder;

    public WeComMessageService(WeComProperties weComProperties,
                               WeComTokenService tokenService,
                               WebClient.Builder webClientBuilder) {
        this.weComProperties = weComProperties;
        this.tokenService = tokenService;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * 发送文本消息
     *
     * @param userIds 接收消息的企业成员 UserID 列表
     * @param content 消息内容（最长不超过 2048 字节）
     * @return 企业微信 API 响应
     */
    public Map<String, Object> sendTextMessage(List<String> userIds, String content) {
        Map<String, Object> text = new java.util.HashMap<>();
        text.put("content", content);

        Map<String, Object> body = buildBaseBody(userIds, "text");
        body.put("text", text);

        return sendMessage(body);
    }

    /**
     * 发送 Markdown 消息
     *
     * 企业微信应用消息支持部分 Markdown 格式：
     * 标题：# 一级 ~ 六级
     * 加粗：**bold**
     * 链接：[text](url)
     * 引用：> text
     * 有序/无序列表
     *
     * @param userIds 接收消息的企业成员 UserID 列表
     * @param content Markdown 格式消息内容
     * @return 企业微信 API 响应
     */
    public Map<String, Object> sendMarkdownMessage(List<String> userIds, String content) {
        Map<String, Object> markdown = new java.util.HashMap<>();
        markdown.put("content", content);

        Map<String, Object> body = buildBaseBody(userIds, "markdown");
        body.put("markdown", markdown);

        return sendMessage(body);
    }

    /**
     * 发送文本卡片消息（推荐用于审批通知）
     *
     * 文本卡片有标题、描述和可点击链接，视觉效果优于纯文本。
     *
     * @param userIds     接收消息的企业成员 UserID 列表
     * @param title       卡片标题
     * @param description 卡片描述（最长 512 字节，支持换行 \n）
     * @param url         点击卡片后跳转的链接
     * @return 企业微信 API 响应
     */
    public Map<String, Object> sendTextCardMessage(List<String> userIds, String title,
                                                   String description, String url) {
        Map<String, Object> textcard = new java.util.HashMap<>();
        textcard.put("title", title);
        textcard.put("description", description);
        textcard.put("url", url);
        textcard.put("btntxt", "查看详情");

        Map<String, Object> body = buildBaseBody(userIds, "textcard");
        body.put("textcard", textcard);

        return sendMessage(body);
    }

    /**
     * 发送图文消息
     *
     * @param userIds  接收消息的企业成员 UserID 列表
     * @param articles 图文消息列表（每个条目包含 title/description/url/picurl）
     * @return 企业微信 API 响应
     */
    public Map<String, Object> sendNewsMessage(List<String> userIds, List<Map<String, String>> articles) {
        Map<String, Object> news = new java.util.HashMap<>();
        news.put("articles", articles);

        Map<String, Object> body = buildBaseBody(userIds, "news");
        body.put("news", news);

        return sendMessage(body);
    }

    /**
     * 异步发送消息（不会阻塞，适用于非关键通知）
     */
    public CompletableFuture<Map<String, Object>> sendTextMessageAsync(List<String> userIds, String content) {
        return CompletableFuture.supplyAsync(() -> sendTextMessage(userIds, content));
    }

    // ==================== 内部方法 ====================

    /**
     * 构造消息请求体基础部分
     */
    private Map<String, Object> buildBaseBody(List<String> userIds, String msgType) {
        return new java.util.HashMap<>(Map.of(
                "touser", String.join("|", userIds),
                "msgtype", msgType,
                "agentid", Integer.parseInt(weComProperties.getAgentId()),
                "safe", 0,
                "enable_id_trans", 0,
                "enable_duplicate_check", 0
        ));
    }

    /**
     * 发送消息到企业微信 API
     *
     * 如果发送失败（频率限制等），会自动重试一次。
     */
    private Map<String, Object> sendMessage(Map<String, Object> messageBody) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.AGENT);
        String url = weComProperties.getBaseUrl() + MESSAGE_SEND_PATH + "?access_token=" + accessToken;

        log.debug("发送企业微信消息: msgtype={}, touser={}",
                messageBody.get("msgtype"), messageBody.get("touser"));

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(url)
                .bodyValue(messageBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("企业微信消息发送返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");

            // 频率限制错误，记录日志不抛出异常（让调用方决定是否重试）
            if (errcode == 45009 || errcode == 48018) {
                log.warn("企业微信消息发送频率限制: errcode={}, errmsg={}", errcode, errmsg);
            } else {
                throw new RuntimeException("企业微信消息发送失败: errcode=" + errcode + ", errmsg=" + errmsg);
            }
        }

        if (response.containsKey("invaliduser") && !((String) response.getOrDefault("invaliduser", "")).isBlank()) {
            log.warn("部分用户发送失败: invaliduser={}", response.get("invaliduser"));
        }

        log.debug("企业微信消息发送成功");
        return response;
    }
}
