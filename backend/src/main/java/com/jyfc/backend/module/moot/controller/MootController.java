package com.jyfc.backend.module.moot.controller;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.moot.entity.*;
import com.jyfc.backend.shared.dto.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模拟法庭（M4' 功能骨架）。庭审阶段状态机含反向跳转（prd13 §5：新证据/补充调查回退）；
 * 评分 dimension+rationale 可解释（prd13 §6）；私有视野隔离 = 查询按 role 过滤（S 后续细化）。
 * 真实多角色语音依赖 TRTC 凭据（M3'），本切片以留痕+状态机+评分骨架验收。
 */
@RestController
@RequestMapping("/api/moot")
@PreAuthorize("hasAuthority('ROLE_USER')")
public class MootController {

    private static final Map<String, List<String>> PHASE_EDGES = Map.of(
            "PREP", List.of("OPENING"),
            "OPENING", List.of("COURT_INVESTIGATION"),
            "COURT_INVESTIGATION", List.of("COURT_DEBATE"),
            "COURT_DEBATE", List.of("COURT_INVESTIGATION", "FINAL_STATEMENT"), // 反向：新证据回退调查
            "FINAL_STATEMENT", List.of("JUDGMENT"),
            "JUDGMENT", List.of("CLOSED")
    );

    @PersistenceContext
    private EntityManager em;

    private final UserContextUtil userContextUtil;

    public MootController(UserContextUtil userContextUtil) {
        this.userContextUtil = userContextUtil;
    }

    private Long tenant() {
        Long t = JyTenantContext.get();
        if (t == null || t == 0L) throw new BusinessException("租户上下文缺失");
        return t;
    }

    /** 写操作授权：仅案件发起人或在该案持有角色者可推进庭审/录笔录/打分/配角色。 */
    private void requireCaseWrite(MootCaseEntity c, Long caseId) {
        Long actor = userContextUtil.getCurrentUserId();
        if (Objects.equals(c.getCreatedBy(), actor)) return;
        Long held = em.createQuery(
                "SELECT COUNT(r) FROM MootRoleEntity r WHERE r.caseId = :id AND r.userId = :u", Long.class)
                .setParameter("id", caseId).setParameter("u", actor).getSingleResult();
        if (held == null || held == 0) throw new BusinessException("仅案件发起人或案件角色可操作该案件");
    }

    @PostMapping("/cases")
    @Transactional
    public ApiResponse<MootCaseEntity> create(@RequestBody Map<String, Object> body) {
        MootCaseEntity c = new MootCaseEntity();
        c.setTenantId(tenant());
        c.setTitle(String.valueOf(body.getOrDefault("title", "moot-case")));
        c.setCaseType(String.valueOf(body.getOrDefault("caseType", "CIVIL")));
        c.setCreatedBy(userContextUtil.getCurrentUserId());
        em.persist(c);
        return ApiResponse.success(c);
    }

    @GetMapping("/cases")
    public ApiResponse<List<MootCaseEntity>> list() {
        return ApiResponse.success(em.createQuery(
                "SELECT c FROM MootCaseEntity c WHERE c.tenantId = :t ORDER BY c.id DESC", MootCaseEntity.class)
                .setParameter("t", tenant()).getResultList());
    }

    @GetMapping("/cases/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        MootCaseEntity c = em.find(MootCaseEntity.class, id);
        if (c == null || !c.getTenantId().equals(tenant())) throw new BusinessException("案件不存在或跨租户");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("case", c);
        out.put("roles", em.createQuery("SELECT r FROM MootRoleEntity r WHERE r.caseId = :id", MootRoleEntity.class).setParameter("id", id).getResultList());
        out.put("phases", em.createQuery("SELECT p FROM MootPhaseEventEntity p WHERE p.caseId = :id ORDER BY p.id", MootPhaseEventEntity.class).setParameter("id", id).getResultList());
        out.put("minutes", em.createQuery("SELECT m FROM MootMinuteEntity m WHERE m.caseId = :id ORDER BY m.id", MootMinuteEntity.class).setParameter("id", id).getResultList());
        out.put("scores", em.createQuery("SELECT s FROM MootScoreEntity s WHERE s.caseId = :id ORDER BY s.id", MootScoreEntity.class).setParameter("id", id).getResultList());
        return ApiResponse.success(out);
    }

    @PostMapping("/cases/{id}/roles")
    @Transactional
    public ApiResponse<MootRoleEntity> addRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MootCaseEntity c = em.find(MootCaseEntity.class, id);
        if (c == null || !c.getTenantId().equals(tenant())) throw new BusinessException("案件不存在或跨租户");
        requireCaseWrite(c, id);
        MootRoleEntity r = new MootRoleEntity();
        r.setTenantId(tenant());
        r.setCaseId(id);
        r.setRoleType(String.valueOf(body.getOrDefault("roleType", "PLAINTIFF")));
        r.setUserId(body.get("userId") != null ? Long.valueOf(String.valueOf(body.get("userId"))) : null);
        r.setAgentFlag(Boolean.parseBoolean(String.valueOf(body.getOrDefault("agentFlag", "false"))));
        em.persist(r);
        return ApiResponse.success(r);
    }

    @PostMapping("/cases/{id}/phase")
    @Transactional
    public ApiResponse<MootCaseEntity> phase(@PathVariable Long id, @RequestBody Map<String, String> body) {
        MootCaseEntity c = em.find(MootCaseEntity.class, id);
        if (c == null || !c.getTenantId().equals(tenant())) throw new BusinessException("案件不存在或跨租户");
        requireCaseWrite(c, id);
        String to = body.getOrDefault("to", "");
        List<String> allowed = PHASE_EDGES.getOrDefault(c.getPhase(), List.of());
        if (!allowed.contains(to)) throw new BusinessException("ILLEGAL_PHASE: " + c.getPhase() + " -> " + to);
        MootPhaseEventEntity e = new MootPhaseEventEntity();
        e.setTenantId(tenant());
        e.setCaseId(id);
        e.setFromPhase(c.getPhase());
        e.setToPhase(to);
        e.setTriggerType(body.getOrDefault("trigger", "USER"));
        e.setReason(body.get("reason"));
        em.persist(e);
        c.setPhase(to);
        return ApiResponse.success(c);
    }

    @PostMapping("/cases/{id}/minutes")
    @Transactional
    public ApiResponse<MootMinuteEntity> minute(@PathVariable Long id, @RequestBody Map<String, String> body) {
        MootCaseEntity c = em.find(MootCaseEntity.class, id);
        if (c == null || !c.getTenantId().equals(tenant())) throw new BusinessException("案件不存在或跨租户");
        requireCaseWrite(c, id);
        MootMinuteEntity m = new MootMinuteEntity();
        m.setTenantId(tenant());
        m.setCaseId(id);
        m.setSpeaker(body.getOrDefault("speaker", "unknown"));
        m.setContent(body.getOrDefault("content", ""));
        m.setSpokenAt(LocalDateTime.now());
        em.persist(m);
        return ApiResponse.success(m);
    }

    @PostMapping("/cases/{id}/scores")
    @Transactional
    public ApiResponse<MootScoreEntity> score(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MootCaseEntity c = em.find(MootCaseEntity.class, id);
        if (c == null || !c.getTenantId().equals(tenant())) throw new BusinessException("案件不存在或跨租户");
        requireCaseWrite(c, id);
        MootScoreEntity s = new MootScoreEntity();
        s.setTenantId(tenant());
        s.setCaseId(id);
        s.setRoleId(body.get("roleId") != null ? Long.valueOf(String.valueOf(body.get("roleId"))) : null);
        s.setDimension(String.valueOf(body.getOrDefault("dimension", "overall")));
        s.setScore(Integer.parseInt(String.valueOf(body.getOrDefault("score", "0"))));
        s.setRationale(String.valueOf(body.getOrDefault("rationale", "no-rationale")));
        em.persist(s);
        return ApiResponse.success(s);
    }
}
