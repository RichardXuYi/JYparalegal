package com.jyfc.backend.module.evidence.controller;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.evidence.entity.EvidenceItemEntity;
import com.jyfc.backend.module.evidence.service.EvidenceService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 证据条目 REST 入口（V141）。list 支持 bizType/bizId 可选过滤；create 在带 content 时固化 sha256；
 * status 变更仅允许 UNVERIFIED/VERIFIED/REJECTED。所有端点按当前租户隔离。
 */
@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final EvidenceService service;
    private final UserContextUtil userContextUtil;

    public EvidenceController(EvidenceService service, UserContextUtil userContextUtil) {
        this.service = service;
        this.userContextUtil = userContextUtil;
    }

    private Long tenant() {
        Long t = JyTenantContext.get();
        if (t == null || t == 0L) throw new BusinessException("租户上下文缺失");
        return t;
    }

    @GetMapping("")
    public ApiResponse<List<EvidenceItemEntity>> list(@RequestParam(required = false) String bizType,
                                                      @RequestParam(required = false) Long bizId) {
        return ApiResponse.success(service.list(tenant(), bizType, bizId));
    }

    @PostMapping("")
    @Transactional
    public ApiResponse<EvidenceItemEntity> create(@RequestBody Map<String, Object> body) {
        String bizType = body.get("bizType") == null ? null : String.valueOf(body.get("bizType"));
        Long bizId = body.get("bizId") == null ? null : Long.valueOf(String.valueOf(body.get("bizId")));
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        String evidenceType = body.get("evidenceType") == null ? null : String.valueOf(body.get("evidenceType"));
        String source = body.get("source") == null ? null : String.valueOf(body.get("source"));
        String filePath = body.get("filePath") == null ? null : String.valueOf(body.get("filePath"));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
        EvidenceItemEntity e = service.create(tenant(), userContextUtil.getCurrentUserId(),
                bizType, bizId, name, evidenceType, source, filePath, note, content);
        return ApiResponse.success(e);
    }

    @PostMapping("/{id}/status")
    @Transactional
    public ApiResponse<EvidenceItemEntity> setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.success(service.setStatus(tenant(), id, body.getOrDefault("status", "")));
    }
}
