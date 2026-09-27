package com.jyfc.backend.module.signdoc.controller;

import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.module.signdoc.entity.SignDocEntity;
import com.jyfc.backend.module.signdoc.repository.SignDocRepository;
import com.jyfc.backend.module.signdoc.service.SignDocService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 签署文档 REST（文档上传/业务文档切片，挂 /api/sign 与 SignTaskController 同域，子路径不冲突）。
 * POST /tasks/{id}/docs 上传附件；POST /tasks/from-file 从文件直建 DRAFT 任务；
 * GET /tasks/{id}/bizdocs 业务文档聚合视图：docs=sign_doc 行，contractDocs=合同文档（V133 无 contract_doc 表/无 task_id 列，回空），
 * approvals=审批（本切片回空）。授权在 SignDocService：仅同租户（D12）。
 */
@RestController
@RequestMapping("/api/sign")
public class SignDocController {

    private final SignDocService signDocService;
    private final SignDocRepository signDocRepository;
    private final UserContextUtil userContextUtil;

    public SignDocController(SignDocService signDocService, SignDocRepository signDocRepository,
                             UserContextUtil userContextUtil) {
        this.signDocService = signDocService;
        this.signDocRepository = signDocRepository;
        this.userContextUtil = userContextUtil;
    }

    @GetMapping("/tasks/{id}/docs")
    public ApiResponse<List<Map<String, Object>>> docs(@PathVariable Long id) {
        return ApiResponse.success(signDocService.listUploads(id));
    }

    @PostMapping("/tasks/{id}/docs")
    public ApiResponse<SignDocEntity> upload(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long actor = userContextUtil.getCurrentUserId();
        return ApiResponse.success(signDocService.storeUpload(id, actor,
                body.get("fileName"), body.get("contentBase64")));
    }

    @PostMapping("/tasks/from-file")
    public ApiResponse<Map<String, Object>> fromFile(@RequestBody Map<String, Object> body) {
        Long actor = userContextUtil.getCurrentUserId();
        String title = body.get("title") != null ? String.valueOf(body.get("title")) : null;
        // 公司归属由服务端按管理员授权强制写入，不接受客户端指定（无默认）
        Long companyId = userContextUtil.getAuthorizedCompanyId();
        Long taskId = signDocService.createTaskFromFile(title, companyId,
                body.get("fileName") != null ? String.valueOf(body.get("fileName")) : null,
                body.get("contentBase64") != null ? String.valueOf(body.get("contentBase64")) : null, actor);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", taskId);
        return ApiResponse.success(out);
    }

    @GetMapping("/tasks/{id}/bizdocs")
    public ApiResponse<Map<String, Object>> bizdocs(@PathVariable Long id) {
        // 授权：加载任务须同租户（service 内 findByIdAndTenantId），否则抛"任务不存在或跨租户"
        signDocService.requireTaskAccessible(id);
        List<SignDocEntity> docs = signDocRepository.findAllByTaskIdOrderByIdAsc(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("docs", docs);
        // V133 实为 contract_template/counterparty，无 contract_doc 表亦无 task_id 列 → 回空（后续切片补）
        out.put("contractDocs", List.of());
        out.put("approvals", List.of());
        return ApiResponse.success(out);
    }
}
