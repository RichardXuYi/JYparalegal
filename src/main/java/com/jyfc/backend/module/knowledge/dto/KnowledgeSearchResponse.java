package com.jyfc.backend.module.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识库搜索接口（{@code GET /api/knowledge/search}）的响应数据。
 *
 * <p>前端 {@code searchKnowledge()} 通过 {@code request<T>} 解包 {@code data} 字段后
 * 期望得到这个对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchResponse {
    private List<KnowledgeSearchResult> results;
    private long total;
    private int page;
    private int size;
    private String query;
}
