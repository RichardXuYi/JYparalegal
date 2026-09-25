package com.jyfc.backend.module.knowledge.repository;

import com.jyfc.backend.module.knowledge.entity.KnowledgeEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntry, Long> {

    long countByIsActive(boolean isActive);

    Optional<KnowledgeEntry> findBySourceTypeAndSourceId(String sourceType, Long sourceId);

    Page<KnowledgeEntry> findByCategoryId(Long categoryId, Pageable pageable);

    Page<KnowledgeEntry> findByTitleContaining(String keyword, Pageable pageable);

    Page<KnowledgeEntry> findByCategoryIdAndTitleContaining(Long categoryId, String keyword, Pageable pageable);

    @Query(value = """
        SELECT k.*
        FROM knowledge_entries k
        WHERE k.is_active = 1
          AND (k.tenant_id IS NULL OR k.tenant_id = :tenantId)
          AND (
                LOWER(k.title)   LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(CAST(k.content AS CHAR)) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(COALESCE(k.tags, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY k.quality_score DESC, k.usage_count DESC
        """,
        countQuery = """
        SELECT count(*)
        FROM knowledge_entries k
        WHERE k.is_active = 1
          AND (k.tenant_id IS NULL OR k.tenant_id = :tenantId)
          AND (
                LOWER(k.title)   LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(CAST(k.content AS CHAR)) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(COALESCE(k.tags, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        """,
        nativeQuery = true)
    Page<KnowledgeEntry> searchByKeywordPaged(@Param("keyword") String keyword,
                                              @Param("tenantId") Long tenantId,
                                              Pageable pageable);

    @Query(value = """
        SELECT
          id AS id,
          title AS title,
          content AS content,
          source_type AS sourceType,
          source_id AS sourceId,
          MATCH(title, content) AGAINST (:q IN NATURAL LANGUAGE MODE) AS score
        FROM knowledge_entries
        WHERE is_active = 1
          AND MATCH(title, content) AGAINST (:q IN NATURAL LANGUAGE MODE)
        ORDER BY score DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<KnowledgeSearchProjection> searchFullText(@Param("q") String query, @Param("limit") int limit);

    /**
     * 关键字搜索（title + tags 用 LIKE，content 用 CAST 转字符串后再 LIKE）。
     *
     * <p>知识库 content 是 @Lob LONGTEXT (CLOB)，Hibernate 不允许对 CLOB 直接调用 LOWER/LOCATE。
     * 所以走 nativeQuery + CAST，让 MySQL 自己处理。
     * 分页由 Service 层手动完成。
     *
     * @param keyword 用户输入的查询关键字（调用方负责转义 LIKE 通配符）
     */
    @Query(value = """
        SELECT k.*
        FROM knowledge_entries k
        WHERE k.is_active = 1
          AND (
                LOWER(k.title)   LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(CAST(k.content AS CHAR)) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(COALESCE(k.tags, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY k.quality_score DESC, k.usage_count DESC
        """,
        nativeQuery = true)
    List<KnowledgeEntry> searchByKeyword(@Param("keyword") String keyword);

    interface KnowledgeSearchProjection {
        Long getId();
        String getTitle();
        String getContent();
        String getSourceType();
        Long getSourceId();
        Double getScore();
    }

    /**
     * Agent kb 工具检索（租户作用域，V131 要求"落地前检索必须 Java 层过滤"）：
     * 平台行（tenant_id IS NULL，全租户可见）∪ 本租户私有行，绝不含他租户行。
     *
     * @param keyword 已转义 LIKE 通配符的关键字
     */
    @Query(value = """
        SELECT k.*
        FROM knowledge_entries k
        WHERE k.is_active = 1
          AND (k.tenant_id IS NULL OR k.tenant_id = :tenantId)
          AND (
                LOWER(k.title)   LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(CAST(k.content AS CHAR)) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(COALESCE(k.tags, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY k.quality_score DESC, k.usage_count DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<KnowledgeEntry> searchScopedForAgent(@Param("keyword") String keyword,
                                             @Param("tenantId") Long tenantId,
                                             @Param("limit") int limit);
}
