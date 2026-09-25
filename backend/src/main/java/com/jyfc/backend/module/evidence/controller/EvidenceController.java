package com.jyfc.backend.module.evidence.controller;

import com.jyfc.backend.core.cp.CpProperties;
import com.jyfc.backend.core.cp.CpSigningClient;
import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.sign.entity.SignTaskEntity;
import com.jyfc.backend.module.sign.repository.SignTaskRepository;
import com.jyfc.backend.module.sign.service.SignFlowService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存证（Evidence）：不自建存证库。存证权威源是 e签宝签署流程的<b>蚂蚁链上链记录</b>，
 * 绑定在 e签宝 signFlowId 上，故可存证清单 = 本租户已完成且已产生的签署任务；
 * 详情/核验经控制平面 CP 转发到 e签宝 /v3/antchain-file-info(/verify)。
 * <p>
 * 未接通 CP/e签宝 时返回 available=false + 原因，不编造存证。
 */
@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final SignTaskRepository taskRepository;
    private final SignFlowService flow;
    private final CpSigningClient cpSigningClient;
    private final CpProperties cpProperties;
    private final UserContextUtil userContextUtil;

    public EvidenceController(SignTaskRepository taskRepository, SignFlowService flow,
                              CpSigningClient cpSigningClient, CpProperties cpProperties,
                              UserContextUtil userContextUtil) {
        this.taskRepository = taskRepository;
        this.flow = flow;
        this.cpSigningClient = cpSigningClient;
        this.cpProperties = cpProperties;
        this.userContextUtil = userContextUtil;
    }

    private Long tenant() {
        Long t = JyTenantContext.get();
        if (t == null || t == 0L) throw new BusinessException("租户上下文缺失");
        return t;
    }

    /** 可存证清单：本租户已完成签署任务（含是否已生成 e签宝存证流程）。 */
    @GetMapping("")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SignTaskEntity task : taskRepository.findAllByTenantIdOrderByIdDesc(tenant())) {
            if (!"COMPLETED".equals(task.getStatus())) continue;
            String flowId = task.getProviderFlowId();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", task.getId());
            m.put("taskNo", task.getTaskNo());
            m.put("title", task.getTitle());
            m.put("hasEvidence", flowId != null && !flowId.isBlank());
            m.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
            out.add(m);
        }
        return ApiResponse.success(out);
    }

    /** 某签署任务的蚂蚁链存证信息。 */
    @GetMapping("/{taskId}/antchain")
    public ApiResponse<Map<String, Object>> antchain(@PathVariable Long taskId) {
        SignTaskEntity task = flow.taskForActor(taskId, userContextUtil.getCurrentUserId());
        return ApiResponse.success(antchainInfo(task));
    }

    /** 核验存证：先取存证信息，再调 e签宝核验。 */
    @PostMapping("/{taskId}/antchain/verify")
    public ApiResponse<Map<String, Object>> verify(@PathVariable Long taskId) {
        SignTaskEntity task = flow.taskForActor(taskId, userContextUtil.getCurrentUserId());
        String flowId = task.getProviderFlowId();
        if (flowId == null || flowId.isBlank()) {
            return ApiResponse.success(unavailable("该任务未产生 e签宝存证流程（未送签或未接通控制平面）"));
        }
        if (!cpProperties.isEnabled()) {
            return ApiResponse.success(unavailable("当前环境未连接控制平面，无法核验 e签宝存证"));
        }
        try {
            Map<String, Object> info = cpSigningClient.antchainInfo(flowId, null);
            String fileHash = asText(info.get("fileHash"));
            String antTxHash = asText(info.get("antTxHash"));
            if (fileHash == null || antTxHash == null) {
                return ApiResponse.success(unavailable("存证信息不完整（缺少 fileHash / antTxHash）"));
            }
            Map<String, Object> result = new LinkedHashMap<>(cpSigningClient.antchainVerify(fileHash, antTxHash, null));
            result.put("available", true);
            return ApiResponse.success(result);
        } catch (BusinessException e) {
            return ApiResponse.success(unavailable(e.getMessage()));
        }
    }

    private Map<String, Object> antchainInfo(SignTaskEntity task) {
        String flowId = task.getProviderFlowId();
        if (flowId == null || flowId.isBlank()) {
            return unavailable("该任务未产生 e签宝存证流程（未送签或未接通控制平面）");
        }
        if (!cpProperties.isEnabled()) {
            return unavailable("当前环境未连接控制平面，无法获取 e签宝存证");
        }
        try {
            Map<String, Object> info = new LinkedHashMap<>(cpSigningClient.antchainInfo(flowId, null));
            info.put("available", true);
            return info;
        } catch (BusinessException e) {
            return unavailable(e.getMessage());
        }
    }

    private static Map<String, Object> unavailable(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", false);
        m.put("message", message);
        return m;
    }

    private static String asText(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return s.isBlank() ? null : s;
    }
}
