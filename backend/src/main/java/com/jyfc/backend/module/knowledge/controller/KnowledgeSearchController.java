package com.jyfc.backend.module.knowledge.controller;

import com.jyfc.backend.module.knowledge.dto.KnowledgeSearchResponse;
import com.jyfc.backend.module.knowledge.service.KnowledgeService;
import com.jyfc.backend.shared.dto.ApiResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库检索接口（面向登录用户）。
 *
 * <p>路径：{@code GET /api/knowledge/search}
 * <p>权限：任意已登录用户（{@code /api/**} 默认 {@code authenticated()}）
 *
 * <p>对齐前端 {@code official-web/src/api/services/legal.ts} 中的
 * {@code searchKnowledge(query, page, size)} 调用契约。
 */
@RestController
@RequestMapping("/api/knowledge")
@Validated
public class KnowledgeSearchController {

    /** 关键字最小长度（前端契约 2） */
    private static final int MIN_QUERY_LENGTH = 2;

    private final KnowledgeService knowledgeService;

    public KnowledgeSearchController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * 关键字分页搜索知识库。
     *
     * @param q     搜索关键字，trim 后长度 ≥ 2
     * @param page  页码（0-based，默认 0）
     * @param size  每页条数（默认 10，服务端最大 100）
     * @return {@link KnowledgeSearchResponse}，按 {@code ApiResponse.data} 包装
     */
    @GetMapping("/search")
    public ApiResponse<KnowledgeSearchResponse> search(
            @RequestParam("q")
            @NotBlank(message = "搜索关键字不能为空")
            @Size(min = MIN_QUERY_LENGTH, max = 100, message = "搜索关键字长度必须在 2 到 100 之间")
            String q,

            @RequestParam(value = "page", defaultValue = "0")
            @Min(value = 0, message = "page 必须 ≥ 0")
            int page,

            @RequestParam(value = "size", defaultValue = "10")
            @Min(value = 1, message = "size 必须 ≥ 1")
            int size
    ) {
        KnowledgeSearchResponse data = knowledgeService.searchKnowledge(q, page, size);
        return ApiResponse.success(data);
    }
}
