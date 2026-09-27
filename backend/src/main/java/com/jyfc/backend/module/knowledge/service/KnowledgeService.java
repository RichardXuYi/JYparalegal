package com.jyfc.backend.module.knowledge.service;

import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.knowledge.dto.KnowledgeSearchResponse;
import com.jyfc.backend.module.knowledge.dto.KnowledgeSearchResult;
import com.jyfc.backend.module.knowledge.entity.KnowledgeCategory;
import com.jyfc.backend.module.knowledge.entity.KnowledgeEntry;
import com.jyfc.backend.module.knowledge.repository.KnowledgeCategoryRepository;
import com.jyfc.backend.module.knowledge.repository.KnowledgeEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class KnowledgeService {

    private static final int SNIPPET_RADIUS = 100;

    private final KnowledgeEntryRepository repository;
    private final KnowledgeCategoryRepository categoryRepository;

    public KnowledgeService(KnowledgeEntryRepository repository,
                             KnowledgeCategoryRepository categoryRepository) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
    }

    public Page<KnowledgeEntry> list(Long categoryId, String keyword, Pageable pageable) {
        if (categoryId != null && keyword != null && !keyword.isBlank()) {
            return repository.findByCategoryIdAndTitleContaining(categoryId, keyword, pageable);
        } else if (categoryId != null) {
            return repository.findByCategoryId(categoryId, pageable);
        } else if (keyword != null && !keyword.isBlank()) {
            return repository.findByTitleContaining(keyword, pageable);
        }
        return repository.findAll(pageable);
    }

    public KnowledgeEntry getById(Long id) {
        if (id == null) return null;
        return repository.findById(id).orElse(null);
    }

    public KnowledgeEntry create(KnowledgeEntry entry) {
        if (entry == null) throw new IllegalArgumentException("KnowledgeEntry must not be null");
        return repository.save(entry);
    }

    public KnowledgeEntry update(Long id, KnowledgeEntry entry) {
        if (id == null) return null;
        KnowledgeEntry existing = repository.findById(id).orElse(null);
        if (existing == null) return null;
        existing.setTitle(entry.getTitle());
        existing.setContent(entry.getContent());
        existing.setCategoryId(entry.getCategoryId());
        existing.setTags(entry.getTags());
        existing.setIsActive(entry.getIsActive());
        return repository.save(existing);
    }

    public void delete(Long id) {
        if (id == null) return;
        repository.deleteById(id);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", repository.count());
        stats.put("active", repository.countByIsActive(true));
        stats.put("inactive", repository.countByIsActive(false));
        return stats;
    }

    // ============================================================
    // /api/knowledge/search 实现
    // ============================================================

    /**
     * 知识库全文搜索。
     *
     * <p>使用 JPA LIKE 搜索。
     *
     * @param keyword 关键字（已 trim，不为空，长度 >= 2）
     * @param page    0-based 页码（裁剪到 [0, +inf)）
     * @param size    每页条数（裁剪到 [1, 100]）
     */
    public KnowledgeSearchResponse searchKnowledge(String keyword, int page, int size) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        // 使用数据库分页（租户作用域：平台行 tenant_id IS NULL ∪ 本租户私有行，
        // 绝不含他租户行；tenantId 为 null 时只回平台行）。
        String likeKeyword = escapeLikeWildcards(safeKeyword);
        Page<KnowledgeEntry> matchedPage = repository.searchByKeywordPaged(likeKeyword,
                JyTenantContext.get(), PageRequest.of(safePage, safeSize));

        List<KnowledgeEntry> entries = matchedPage.getContent();

        // 批量加载分类名称，避免 N+1 查询
        Set<Long> categoryIds = new HashSet<>();
        for (KnowledgeEntry e : entries) {
            if (e.getCategoryId() != null) {
                categoryIds.add(e.getCategoryId());
            }
        }
        Map<Long, String> categoryNameMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            for (KnowledgeCategory c : categoryRepository.findAllById(categoryIds)) {
                categoryNameMap.put(c.getId(), c.getName());
            }
        }

        List<KnowledgeSearchResult> items = new ArrayList<>();
        for (KnowledgeEntry e : entries) {
            String categoryName = e.getCategoryId() != null ? categoryNameMap.get(e.getCategoryId()) : null;
            items.add(toResult(e, 0.0, categoryName));
        }
        return KnowledgeSearchResponse.builder()
                .results(items)
                .total(matchedPage.getTotalElements())
                .page(safePage)
                .size(safeSize)
                .query(safeKeyword)
                .build();
    }

    private KnowledgeSearchResult toResult(KnowledgeEntry e, double relevanceScore, String categoryName) {
        return KnowledgeSearchResult.builder()
                .id(e.getId())
                .title(e.getTitle())
                .content(buildSnippet(e.getContent()))
                .category(categoryName)
                .source(buildSource(e.getSourceType(), e.getSourceId()))
                .relevanceScore(relevanceScore)
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    /**
     * Agent `kb_search` 工具用：租户作用域检索（平台行全租户可见；私有行仅本租户）。
     *
     * @param keyword  关键字（空白即空结果，不编造）
     * @param tenantId 当前租户；null 时只回平台行
     * @param limit    上限（裁剪到 [1, 20]）
     */
    public List<Map<String, Object>> searchForAgent(String keyword, Long tenantId, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String likeKeyword = escapeLikeWildcards(keyword.trim());
        int safeLimit = Math.min(Math.max(1, limit), 20);
        List<Map<String, Object>> out = new ArrayList<>();
        for (KnowledgeEntry e : repository.searchScopedForAgent(likeKeyword, tenantId, safeLimit)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.getId());
            m.put("title", e.getTitle());
            m.put("snippet", buildSnippet(e.getContent()));
            m.put("source", buildSource(e.getSourceType(), e.getSourceId()));
            out.add(m);
        }
        return out;
    }

    private String buildSnippet(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        if (trimmed.length() <= SNIPPET_RADIUS * 2 + 6) {
            return trimmed;
        }
        return trimmed.substring(0, SNIPPET_RADIUS * 2) + "...";
    }

    private String buildSource(String sourceType, Long sourceId) {
        if (sourceType == null && sourceId == null) return null;
        if (sourceId == null) return sourceType;
        if (sourceType == null) return String.valueOf(sourceId);
        return sourceType + "#" + sourceId;
    }

    private String escapeLikeWildcards(String input) {
        if (input == null) return "";
        // JPA LIKE 里的通配符需要手动转义
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
