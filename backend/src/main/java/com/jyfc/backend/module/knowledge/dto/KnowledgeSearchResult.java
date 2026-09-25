package com.jyfc.backend.module.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库全文搜索结果 DTO。
 *
 * <p>字段对齐前端 {@code KnowledgeSearchResult} 接口契约：
 * <ul>
 *   <li>{@code id}            - 知识条目主键</li>
 *   <li>{@code title}         - 标题</li>
 *   <li>{@code content}       - 摘要片段（围绕关键词前后各 100 字）</li>
 *   <li>{@code category}      - 分类名称（来自 knowledge_categories.name）</li>
 *   <li>{@code source}        - 来源（sourceType / sourceId 组合）</li>
 *   <li>{@code relevanceScore}- 相关度 0~1（最高 1.0，归一化分数）</li>
 *   <li>{@code updatedAt}     - 更新时间</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchResult {
    private Long id;
    private String title;
    private String content;
    private String category;
    private String source;
    private Double relevanceScore;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
