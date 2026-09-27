package com.jyfc.backend.module.review.llm;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 审查通道（M2' 五段式之"模型通道"，DashScope / OpenAI 兼容协议）。
 * <p>
 * 配置：jy.llm.api-key（为空 = 通道不可用，见 {@link #isAvailable()}）、
 * jy.llm.base-url（默认 DashScope compatible-mode）、jy.llm.model（默认 qwen3-max）。
 * <p>
 * 红线（prd11 §8.1 弃权）：任何异常/超时/解析失败一律返回空结果，绝不编造条款或依据；
 * 法律依据（legal_basis）以规则通道为权威，LLM 发现仅作补充合并。
 */
@Component
public class LlmReviewChannel {

    private static final Logger log = LoggerFactory.getLogger(LlmReviewChannel.class);

    /** OpenAI 兼容模式基地址（DashScope 默认）。 */
    @Value("${jy.llm.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    /** API Key：为空字符串时通道整体弃权（@ConditionalOnProperty 无法判空串，故运行时检查）。 */
    @Value("${jy.llm.api-key:}")
    private String apiKey;

    /** 模型名。 */
    @Value("${jy.llm.model:qwen3-max}")
    private String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LlmReviewChannel(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 通道是否可用：仅当 api-key 非空。调用方（ReviewPipeline）据此决定合并或降级。 */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 合同风险审查：要求模型输出 JSON 数组 [{clause, risk, level, basis}]。
     *
     * @param text 合同全文
     * @return findings 列表；不可用或任何异常 → 空列表（弃权，不编造）
     */
    public List<Map<String, Object>> review(String text) {
        if (!isAvailable()) return Collections.emptyList();
        String system = "你是资深合同审查律师。审查合同文本中的风险条款，只输出 JSON 数组（不要 markdown 代码块、不要多余文字），"
                + "每个元素形如 {\"clause\":\"风险条款原文摘录\",\"risk\":\"风险描述\",\"level\":\"HIGH|MEDIUM|LOW\",\"basis\":\"法律依据（法条编号）\"}。"
                + "红线：没有把握的不要输出；找不到风险就输出空数组 []。禁止编造条款或法律依据。";
        try {
            String content = chat(system, text);
            return parseFindings(content);
        } catch (Exception e) {
            log.warn("LLM 审查通道异常，弃权返回空结果: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 要素抽取：要求模型输出 JSON 对象 {party_a, party_b, amount, sign_date}。
     *
     * @return 要素 Map（缺失键为 null）；不可用或异常 → null（调用方降级到规则版）
     */
    public Map<String, Object> extract(String text) {
        if (!isAvailable()) return null;
        String system = "你是合同要素抽取引擎。从合同文本抽取要素，只输出 JSON 对象："
                + "{\"party_a\":\"甲方名称\",\"party_b\":\"乙方名称\",\"amount\":\"合同金额\",\"sign_date\":\"签订日期\"}。"
                + "文本中不存在的要素输出 null。禁止编造。";
        try {
            String content = chat(system, text);
            JsonNode json = readJsonObject(content);
            if (json == null) return null;
            Map<String, Object> e = new LinkedHashMap<>();
            for (String key : List.of("party_a", "party_b", "amount", "sign_date")) {
                JsonNode v = json.path(key);
                e.put(key, v.isMissingNode() || v.isNull() ? null : v.asText());
            }
            Map<String, Object> conf = new LinkedHashMap<>();
            e.forEach((k, v) -> conf.put(k, v == null ? 0 : 0.7)); // LLM 通道置信度低于正则直采
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("elements", e);
            out.put("confidence", conf);
            out.put("source", "llm");
            return out;
        } catch (Exception e) {
            log.warn("LLM 抽取通道异常，弃权返回 null: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 文书起草：按意图生成草稿正文（保留 prd11 §7.2 强制免责声明由调用方拼接）。
     *
     * @param intent 起草意图描述（文书类型 + 收件人 + 事由等）
     * @return 草稿正文；不可用或异常 → null（调用方降级到模板版）
     */
    public String draft(String intent) {
        if (!isAvailable()) return null;
        String system = "你是执业律师助理。按用户意图起草法律文书正文（纯文本，不含免责声明）。"
                + "只使用用户提供的信息，缺失信息用〔待补充〕占位。禁止编造事实与法条。";
        try {
            String content = chat(system, intent);
            return (content == null || content.isBlank()) ? null : content.trim();
        } catch (Exception e) {
            log.warn("LLM 起草通道异常，弃权返回 null: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 内部实现 ====================

    /** 调用 /chat/completions（OpenAI 兼容），返回 choices[0].message.content。 */
    private String chat(String systemPrompt, String userContent) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)));
        body.put("temperature", 0.2);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            log.error("LLM 接口 HTTP {}: {}", resp.statusCode(), truncate(resp.body()));
            throw new IllegalStateException("LLM HTTP " + resp.statusCode());
        }
        JsonNode json = objectMapper.readTree(resp.body());
        JsonNode content = json.path("choices").path(0).path("message").path("content");
        return content.isMissingNode() || content.isNull() ? null : content.asText();
    }

    /** 解析模型输出的 findings JSON 数组；容忍 markdown 代码块包裹。非法结构 → 空列表（弃权）。 */
    private List<Map<String, Object>> parseFindings(String content) throws Exception {
        JsonNode arr = readJsonArray(content);
        if (arr == null || !arr.isArray()) return Collections.emptyList();
        List<Map<String, Object>> findings = new ArrayList<>();
        for (JsonNode n : arr) {
            String clause = n.path("clause").asText(null);
            String risk = n.path("risk").asText(null);
            if (clause == null || risk == null) continue; // 结构不完整直接丢弃，不猜测
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("clause", clause);
            f.put("risk", risk);
            f.put("level", n.path("level").asText("MEDIUM"));
            f.put("basis", n.path("basis").asText(null));
            findings.add(f);
        }
        return findings;
    }

    /** 剥掉可能的 ```json ... ``` 包裹后按数组解析；失败返回 null。 */
    private JsonNode readJsonArray(String content) {
        try {
            return objectMapper.readTree(stripFence(content));
        } catch (Exception e) {
            return null;
        }
    }

    /** 剥掉可能的 ```json ... ``` 包裹后按对象解析；失败返回 null。 */
    private JsonNode readJsonObject(String content) {
        try {
            JsonNode json = objectMapper.readTree(stripFence(content));
            return json.isObject() ? json : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripFence(String content) {
        if (content == null) return "";
        String s = content.trim();
        if (s.startsWith("```")) {
            int firstBreak = s.indexOf('\n');
            if (firstBreak > 0) s = s.substring(firstBreak + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    private static String truncate(String s) {
        return s == null ? "" : (s.length() > 500 ? s.substring(0, 500) + "..." : s);
    }
}
