package com.jyfc.backend.module.review.service;

import com.jyfc.backend.module.review.llm.LlmReviewChannel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 审查流水线（M2'，五段式之"规则通道"落地；模型通道待 API Key，降级策略见 prd11 §5.2）。
 * 规则库 = 代码内 YAML 等价结构（S 后续外置 resources/rules/*.yml）；每条带法律依据与改写建议。
 * 红线：无依据不编造（prd11 §8.1 弃权）。
 */
@Service
public class ReviewPipeline {

    /** LLM 模型通道（api-key 为空时整体弃权，规则通道独立可用）。 */
    private final LlmReviewChannel llm;

    public ReviewPipeline(LlmReviewChannel llm) {
        this.llm = llm;
    }

    private record Rule(String id, String riskType, String level, Pattern pattern, String basis, String suggestion) { }

    private static final List<Rule> RULES = List.of(
            new Rule("R001", "违约金过高", "HIGH",
                    Pattern.compile("违约金[^。；\\n]{0,40}(3[1-9]|[4-9]\\d|\\d{3})\\s*%"),
                    "《民法典》第 585 条", "建议将违约金比例降至不超过损失的 30%"),
            new Rule("R002", "缺少争议解决/管辖条款", "MEDIUM",
                    Pattern.compile("^(?!.*(管辖|争议解决|仲裁)).*$", Pattern.DOTALL),
                    "《民事诉讼法》第 35 条", "建议补充管辖法院或仲裁条款"),
            new Rule("R003", "缺少履行期限", "MEDIUM",
                    Pattern.compile("^(?!.*(履行期限|交付时间|交货日期)).*$", Pattern.DOTALL),
                    "《民法典》第 511 条", "建议明确履行期限，避免约定不明")
    );

    /** 审查入口：text 为合同全文（M1 由调用方提供；PDF 解析 PDFBox 于 S 后续接）。 */
    public Map<String, Object> review(String text) {
        List<Map<String, Object>> findings = new ArrayList<>();
        for (Rule r : RULES) {
            boolean hit;
            if (r.id().equals("R001")) {
                hit = r.pattern().matcher(text).find();
            } else {
                hit = r.pattern().matcher(text).matches(); // 全文缺失型规则
            }
            if (hit) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("rule_id", r.id());
                f.put("risk_type", r.riskType());
                f.put("risk_level", r.level());
                f.put("original_text", excerpt(text, r));
                f.put("suggested_text", r.suggestion());
                f.put("source", "rule");
                f.put("legal_basis", r.basis());
                findings.add(f);
            }
        }
        // LLM 通道补充（合并顺序：规则发现在前，规则对 legal_basis 保持权威；LLM 异常/无 Key → 空，弃权不编造）
        boolean llmUsed = false;
        if (llm.isAvailable()) {
            for (Map<String, Object> lf : llm.review(text)) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("rule_id", "LLM-" + (findings.size() + 1));
                f.put("risk_type", lf.get("risk"));
                f.put("risk_level", lf.get("level"));
                f.put("original_text", lf.get("clause"));
                f.put("suggested_text", null); // LLM 不给改写建议（避免编造），留人工处理
                f.put("source", "LLM");
                f.put("legal_basis", lf.get("basis")); // 仅供参考，权威性低于规则通道
                findings.add(f);
            }
            llmUsed = true;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("findings", findings);
        out.put("summary", "共发现 " + findings.stream().filter(f -> "HIGH".equals(f.get("risk_level"))).count()
                + " 处高风险、" + findings.stream().filter(f -> "MEDIUM".equals(f.get("risk_level"))).count() + " 处中风险");
        out.put("model_channel", llmUsed
                ? "llm-merged（LLM 发现合并于规则之后，legal_basis 以规则通道为权威）"
                : "unavailable-no-api-key（降级：仅规则通道，prd11 §5.2）");
        return out;
    }

    private String excerpt(String text, Rule r) {
        if (r.id().equals("R001")) {
            var m = r.pattern().matcher(text);
            return m.find() ? m.group() : "";
        }
        return "(全文缺失该要素)";
    }

    /** 要素抽取：LLM 通道可用时优先（弃权返回 null 则降级正则规则版，不编造）。 */
    public Map<String, Object> extract(String text) {
        Map<String, Object> llmOut = llm.extract(text);
        if (llmOut != null) return llmOut; // LLM 通道结果（含 elements/confidence/source=llm）
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("party_a", grab(text, "甲方[：:]\\s*([^\\n，,。]{2,40})"));
        e.put("party_b", grab(text, "乙方[：:]\\s*([^\\n，,。]{2,40})"));
        e.put("amount", grab(text, "(人民币|￥|¥)?\\s*(\\d+(?:\\.\\d+)?)\\s*(元|万元)"));
        e.put("sign_date", grab(text, "(\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}日?)"));
        Map<String, Object> conf = new LinkedHashMap<>();
        e.forEach((k, v) -> conf.put(k, v == null ? 0 : 0.9));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("elements", e);
        out.put("confidence", conf);
        return out;
    }

    private String grab(String text, String regex) {
        var m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(m.groupCount() > 1 ? m.groupCount() : 1).trim() : null;
    }

    /** 文书起草（M2' 模板版）：返回带免责声明的草稿（prd11 §7.2 强制文案）。 */
    public Map<String, Object> draft(String type, Map<String, Object> params) {
        String title = switch (type == null ? "letter" : type) {
            case "opinion" -> "法律意见书";
            case "complaint" -> "民事起诉状";
            default -> "律师函";
        };
        StringBuilder sb = new StringBuilder(title).append("\n\n");
        sb.append("致：").append(params.getOrDefault("to", "〔收件人〕")).append("\n");
        sb.append("事由：").append(params.getOrDefault("subject", "〔事由〕")).append("\n\n");
        // LLM 通道可用时优先起草正文；弃权（null）则降级模板占位（免责声明强制保留）
        String llmBody = llm.draft("起草一份《" + title + "》。收件人：" + params.getOrDefault("to", "〔收件人〕")
                + "；事由：" + params.getOrDefault("subject", "〔事由〕") + "。");
        sb.append(llmBody != null ? llmBody : "〔正文：由规则模板生成；模型通道接入后由 LLM 起草并保留人工编辑〕").append("\n\n");
        sb.append("本文书由 AI 辅助生成，不构成律师法律意见。使用前请咨询执业律师。\n");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", title);
        out.put("content", sb.toString());
        out.put("source", llmBody != null ? "llm" : "template");
        out.put("format", "text/plain（M2' 占位；DOCX 导出后续）");
        return out;
    }
}
