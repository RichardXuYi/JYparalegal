package com.jyfc.backend.module.agent.controller;

import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.agent.entity.AgentRunEntity;
import com.jyfc.backend.module.agent.entity.AgentToolCallEntity;
import com.jyfc.backend.module.agent.repository.AgentRunRepository;
import com.jyfc.backend.module.agent.repository.AgentToolCallRepository;
import com.jyfc.backend.module.knowledge.service.KnowledgeService;
import com.jyfc.backend.module.review.service.ReviewPipeline;
import com.jyfc.backend.module.sign.entity.SignTaskEntity;
import com.jyfc.backend.module.sign.repository.SignTaskRepository;
import com.jyfc.backend.module.sign.service.SignFlowService;
import com.jyfc.backend.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 业务工具端点（M2'，docs/04 §3）。由 studio-web 的 OpenClaw 经 MCP/HTTP 调用，
 * 携带用户 Bearer token（租户/用户上下文）+ X-Actor-Type: AGENT + X-Actor-Session。
 * 每次调用写 agent_run + agent_tool_call（G-2 归因）；create_sign_task 强制 HITL（prd11 §4.4）。
 */
@RestController
@RequestMapping("/internal/tools")
@PreAuthorize("hasAuthority('ROLE_USER')")
public class InternalToolController {

    private final SignFlowService flow;
    private final SignTaskRepository taskRepository;
    private final ReviewPipeline reviewPipeline;
    private final AgentRunRepository runRepository;
    private final AgentToolCallRepository toolCallRepository;
    private final KnowledgeService knowledgeService;
    private final UserContextUtil userContextUtil;

    public InternalToolController(SignFlowService flow, SignTaskRepository taskRepository, ReviewPipeline reviewPipeline,
                                  AgentRunRepository runRepository, AgentToolCallRepository toolCallRepository,
                                  KnowledgeService knowledgeService,
                                  UserContextUtil userContextUtil) {
        this.flow = flow;
        this.taskRepository = taskRepository;
        this.reviewPipeline = reviewPipeline;
        this.runRepository = runRepository;
        this.toolCallRepository = toolCallRepository;
        this.knowledgeService = knowledgeService;
        this.userContextUtil = userContextUtil;
    }

    private AgentRunEntity beginRun(HttpServletRequest req, Long userId) {
        AgentRunEntity r = new AgentRunEntity();
        Long t = JyTenantContext.get();
        r.setTenantId(t != null ? t : 0L);
        r.setUserId(userId);
        String sess = req.getHeader("X-Actor-Session");
        r.setSessionRef(sess != null ? sess : "local-" + UUID.randomUUID().toString().substring(0, 8));
        r.setProvider("jy-internal");
        r.setModel("rule-engine（模型通道待 API Key）");
        r.setStatus("RUNNING");
        return runRepository.save(r);
    }

    private void endRun(AgentRunEntity run, String tool, String argsJson, Object result,
                        boolean hitlRequired, boolean hitlConfirmed, Long hitlActor, Long taskId) {
        run.setStatus("COMPLETED");
        run.setEndedAt(LocalDateTime.now());
        runRepository.save(run);
        AgentToolCallEntity c = new AgentToolCallEntity();
        c.setRunId(run.getId());
        c.setToolName(tool);
        c.setArgsJson(jsonQuote(argsJson));
        String rs = String.valueOf(result);
        c.setResultJson(jsonQuote(rs.length() > 900 ? rs.substring(0, 900) : rs));
        c.setStatus("SUCCESS");
        c.setHitlRequired(hitlRequired);
        c.setHitlConfirmed(hitlConfirmed);
        c.setHitlActorId(hitlActor);
        c.setTaskId(taskId);
        toolCallRepository.save(c);
    }

    /** MySQL JSON 列要求合法 JSON：统一包成 JSON 字符串字面量。 */
    private static String jsonQuote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(ch);
            }
        }
        return sb.append('"').toString();
    }

    @GetMapping("/tasks")
    public ApiResponse<Object> tasks(@RequestParam(required = false) String view, HttpServletRequest req) {
        Long uid = userContextUtil.getCurrentUserId();
        AgentRunEntity run = beginRun(req, uid);
        var data = flow.listByView(view, uid);
        endRun(run, "query_my_tasks", "view=" + view, data.size() + " tasks", false, false, null, null);
        return ApiResponse.success(data);
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<Object> task(@PathVariable Long id, HttpServletRequest req) {
        Long uid = userContextUtil.getCurrentUserId();
        AgentRunEntity run = beginRun(req, uid);
        var data = flow.taskForActor(id, uid);
        endRun(run, "open_task", "id=" + id, data.getStatus(), false, false, null, id);
        return ApiResponse.success(data);
    }

    @PostMapping("/tasks")
    public ApiResponse<Object> createTask(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long uid = userContextUtil.getCurrentUserId();
        AgentRunEntity run = beginRun(req, uid);
        String confirmed = req.getHeader("X-HITL-Confirmed");
        if (confirmed == null || confirmed.isBlank()) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("hitl_required", true);
            preview.put("preview", body);
            preview.put("note", "创建签署任务需人工二次确认（MCP Elicitation）；带 X-HITL-Confirmed 重放");
            endRun(run, "create_sign_task", String.valueOf(body), "awaiting-hitl", true, false, null, null);
            return ApiResponse.success(preview);
        }
        SignTaskEntity t = new SignTaskEntity();
        t.setTitle(String.valueOf(body.getOrDefault("title", "agent-task")));
        t.setTaskNo("A" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        t.setStatus("DRAFT");
        t.setCreatedBy(uid);
        t.setSource("AGENT");
        // 强制公司归属：非企业用户即便经 Agent 也不能创建签署任务
        t.setCompanyId(userContextUtil.getAuthorizedCompanyId());
        SignTaskEntity saved = taskRepository.save(t);
        endRun(run, "create_sign_task", String.valueOf(body), saved.getTaskNo(), true, true,
                Long.valueOf(confirmed), saved.getId());
        return ApiResponse.success(saved);
    }

    @PostMapping("/review")
    public ApiResponse<Object> review(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long uid = userContextUtil.getCurrentUserId();
        AgentRunEntity run = beginRun(req, uid);
        var data = reviewPipeline.review(String.valueOf(body.getOrDefault("text", "")));
        endRun(run, "review_contract", "len=" + String.valueOf(body.getOrDefault("text", "")).length(),
                data.get("summary"), false, false, null, null);
        return ApiResponse.success(data);
    }

    @PostMapping("/extract")
    public ApiResponse<Object> extract(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long uid = userContextUtil.getCurrentUserId();
        AgentRunEntity run = beginRun(req, uid);
        var data = reviewPipeline.extract(String.valueOf(body.getOrDefault("text", "")));
        endRun(run, "extract_elements", "len=" + String.valueOf(body.getOrDefault("text", "")).length(),
                "elements", false, false, null, null);
        return ApiResponse.success(data);
    }

    @PostMapping("/draft")
    @SuppressWarnings("unchecked")
    public ApiResponse<Object> draft(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long uid = userContextUtil.getCurrentUserId();
        AgentRunEntity run = beginRun(req, uid);
        Map<String, Object> params = body.get("params") instanceof Map ? (Map<String, Object>) body.get("params") : Map.of();
        var data = reviewPipeline.draft(String.valueOf(body.getOrDefault("type", "letter")), params);
        endRun(run, "draft_document", String.valueOf(body.get("type")), data.get("title"), false, false, null, null);
        return ApiResponse.success(data);
    }

    @GetMapping("/kb")
    public ApiResponse<Object> kb(@RequestParam String q, HttpServletRequest req) {
        Long uid = userContextUtil.getCurrentUserId();
        AgentRunEntity run = beginRun(req, uid);
        // 真实检索（租户作用域：平台行全租户可见 ∪ 本租户私有行；V131 要求 Java 层过滤）
        List<Map<String, Object>> hits = knowledgeService.searchForAgent(q, JyTenantContext.get(), 5);
        Map<String, Object> data = new LinkedHashMap<>();
        boolean found = !hits.isEmpty();
        data.put("found", found);
        data.put("results", hits);
        if (!found) {
            data.put("answer", "查不到相关法规（弃权不编造，prd11 §8.1）");
        }
        endRun(run, "kb_search", "q=" + q, found ? "found:" + hits.size() : "not-found", false, false, null, null);
        return ApiResponse.success(data);
    }
}
