-- ============================================================
-- V090__interaction.sql
-- 功能组【互动与通知】版本号区间：V090–V099
-- 表：consultations / favorites / user_messages / notifications
--
-- 重构说明：
--   - 补列 favorites.title / favorites.image（实体已有，原迁移缺失）
--   - 添加外键约束（consultations/favorites/messages/notifications -> users）
--   - 补建外键列索引
--   - 所有时间戳列添加 DEFAULT CURRENT_TIMESTAMP
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 用户咨询预约
CREATE TABLE IF NOT EXISTS `consultations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `subject` varchar(100) NOT NULL,
  `description` TEXT,
  `status` varchar(20) DEFAULT 'REQUESTED',
  `scheduled_at` datetime,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_consultations_user_id` (`user_id`),
  CONSTRAINT `fk_consultations_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户咨询预约表';

-- 用户收藏
CREATE TABLE IF NOT EXISTS `favorites` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` varchar(255) NOT NULL,
  `target_id` bigint NOT NULL,
  `title` varchar(255) COMMENT '冗余标题，便于列表展示',
  `image` varchar(255) COMMENT '冗余图片，便于列表展示',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_favorites_user_id` (`user_id`),
  CONSTRAINT `fk_favorites_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表';

-- 应用端站内消息
CREATE TABLE IF NOT EXISTS `user_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` varchar(32) NOT NULL,
  `title` varchar(200),
  `content` TEXT,
  `is_read` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_messages_user_id` (`user_id`),
  CONSTRAINT `fk_user_messages_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用端站内消息表';

-- 统一通知中心
CREATE TABLE IF NOT EXISTS `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` varchar(255) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content` varchar(1000),
  `link_url` varchar(500),
  `is_read` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_user_id` (`user_id`),
  KEY `idx_notifications_user_read` (`user_id`, `is_read`),
  KEY `idx_notifications_created_at` (`created_at`),
  CONSTRAINT `fk_notifications_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一通知中心表';

SET FOREIGN_KEY_CHECKS = 1;
