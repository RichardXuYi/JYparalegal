-- ============================================================
-- V020__knowledge.sql
-- 功能组【知识库（RAG 记忆）】版本号区间：V020–V029
-- 表：knowledge_categories / knowledge_entries
--
-- 重构说明：
--   - 补列 knowledge_entries.quality_score（实体已有，原迁移缺失）
--   - 补列 knowledge_entries.version（实体已有，原迁移缺失）
--   - 添加外键约束（entries->categories, categories 自引用 parent_id）
--   - 所有时间戳列添加 DEFAULT CURRENT_TIMESTAMP
-- 说明：向量数据本体存于 Qdrant，MySQL 仅保存条目元数据与 embedding_id 关联。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 知识分类
CREATE TABLE IF NOT EXISTS `knowledge_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL,
  `parent_id` bigint,
  `module` varchar(50) NOT NULL,
  `sort_order` int DEFAULT 0,
  `is_active` tinyint(1) DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_categories_code` (`code`),
  KEY `idx_knowledge_categories_parent_id` (`parent_id`),
  CONSTRAINT `fk_knowledge_categories_parent_id` FOREIGN KEY (`parent_id`) REFERENCES `knowledge_categories` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识分类表';

-- 知识条目
CREATE TABLE IF NOT EXISTS `knowledge_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint NOT NULL,
  `title` varchar(500) NOT NULL,
  `content` LONGTEXT NOT NULL,
  `content_hash` varchar(64),
  `source_type` varchar(50) NOT NULL,
  `source_id` bigint,
  `tags` json,
  `metadata` json,
  `risk_level` varchar(20),
  `quality_score` decimal(3,2) DEFAULT 0.00 COMMENT '质量评分（0.00-1.00）',
  `is_active` tinyint(1) DEFAULT 1,
  `is_standard` tinyint(1) DEFAULT 0,
  `embedding_id` varchar(255),
  `version` int DEFAULT 1 COMMENT '内容版本号',
  `usage_count` int DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_entries_category_id` (`category_id`),
  CONSTRAINT `fk_knowledge_entries_category_id` FOREIGN KEY (`category_id`) REFERENCES `knowledge_categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识条目表';

SET FOREIGN_KEY_CHECKS = 1;
