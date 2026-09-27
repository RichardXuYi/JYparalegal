package com.jyfc.backend.module.template.controller;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.template.entity.ContractTemplateEntity;
import com.jyfc.backend.module.template.service.ContractTemplateService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 合同模板 REST 入口（V140）。list/create/get/render 均按当前租户隔离；
 * render 用入参 variables 替换正文 {{varName}}，回渲染文本 + 未填变量清单。
 */
@RestController
@RequestMapping("/api/templates")
public class ContractTemplateController {

    private final ContractTemplateService service;
    private final UserContextUtil userContextUtil;

    public ContractTemplateController(ContractTemplateService service, UserContextUtil userContextUtil) {
        this.service = service;
        this.userContextUtil = userContextUtil;
    }

    private Long tenant() {
        Long t = JyTenantContext.get();
        if (t == null || t == 0L) throw new BusinessException("租户上下文缺失");
        return t;
    }

    @GetMapping("")
    public ApiResponse<List<ContractTemplateEntity>> list() {
        return ApiResponse.success(service.list(tenant()));
    }

    @PostMapping("")
    @Transactional
    @SuppressWarnings("unchecked")
    public ApiResponse<ContractTemplateEntity> create(@RequestBody Map<String, Object> body) {
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        String category = body.get("category") == null ? null : String.valueOf(body.get("category"));
        String text = body.get("body") == null ? null : String.valueOf(body.get("body"));
        List<String> variables = body.get("variables") instanceof List<?> l ? (List<String>) l : null;
        ContractTemplateEntity e = service.create(tenant(), userContextUtil.getCurrentUserId(),
                title, category, text, variables);
        return ApiResponse.success(e);
    }

    @GetMapping("/{id}")
    public ApiResponse<ContractTemplateEntity> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(tenant(), id));
    }

    @PostMapping("/{id}/render")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> render(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> variables = body.get("variables") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        return ApiResponse.success(service.render(tenant(), id, variables));
    }
}
