-- ============================================================
-- V030__user_skills.sql
-- 功能组【技能库（Skill 同步）】版本号区间：V030–V039
-- 表：user_skills
--
-- 重构说明：
--   - 添加 user_skills -> users 外键约束
--   - 所有时间戳列添加 DEFAULT CURRENT_TIMESTAMP
-- 说明：skill 文件内容落在服务器磁盘（见 UserSkillService），
-- 此表仅存元数据与内容指纹，供跨端同步比对。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `user_skills` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `slug` varchar(255) NOT NULL,
  `name` varchar(255),
  `description` varchar(1000),
  `version` varchar(64),
  `content_hash` varchar(128) NOT NULL,
  `storage_path` varchar(512) NOT NULL,
  `size_bytes` bigint NOT NULL DEFAULT 0,
  `file_count` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_skills_user_slug` (`user_id`, `slug`),
  KEY `idx_user_skills_user_id` (`user_id`),
  CONSTRAINT `fk_user_skills_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户技能表';

SET FOREIGN_KEY_CHECKS = 1;
