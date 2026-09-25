-- ============================================================
-- V070__marketing.sql
-- 功能组【营销】版本号区间：V070–V079
-- 表：activities / activity_signups / coupons / user_coupons /
--     discounts / homepage_configs / import_jobs
--
-- 重构说明：
--   - import_jobs 列名 camelCase -> snake_case 全面对齐
--   - 添加外键约束（signups->activities/users, user_coupons->users/coupons）
--   - 移除旧版 user_coupons 条件加列兼容逻辑（全新库不需要）
--   - 所有时间戳列添加 DEFAULT CURRENT_TIMESTAMP
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 活动
CREATE TABLE IF NOT EXISTS `activities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `description` TEXT,
  `cover_image` varchar(255),
  `start_time` datetime,
  `end_time` datetime,
  `status` int NOT NULL DEFAULT 1,
  `sort_order` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动表';

-- 活动报名（同一活动+用户唯一）
CREATE TABLE IF NOT EXISTS `activity_signups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `activity_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `signup_time` datetime NOT NULL,
  `status` int NOT NULL,
  `signin_time` datetime,
  `remark` varchar(500),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_signups_activity_user` (`activity_id`, `user_id`),
  KEY `idx_activity_signups_user_id` (`user_id`),
  CONSTRAINT `fk_activity_signups_activity_id` FOREIGN KEY (`activity_id`) REFERENCES `activities` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_activity_signups_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动报名表';

-- 优惠券
CREATE TABLE IF NOT EXISTS `coupons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `type` int NOT NULL,
  `value` decimal(15,2) NOT NULL,
  `min_spend` decimal(15,2),
  `start_time` datetime,
  `end_time` datetime,
  `status` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupons_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券表';

-- 用户持券
CREATE TABLE IF NOT EXISTS `user_coupons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `coupon_id` bigint NOT NULL,
  `status` int NOT NULL DEFAULT 0,
  `used_at` datetime,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_coupons_user_id` (`user_id`),
  KEY `idx_user_coupons_coupon_id` (`coupon_id`),
  CONSTRAINT `fk_user_coupons_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_coupons_coupon_id` FOREIGN KEY (`coupon_id`) REFERENCES `coupons` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户持券表';

-- 折扣
CREATE TABLE IF NOT EXISTS `discounts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `type` int NOT NULL,
  `value` decimal(15,2) NOT NULL,
  `start_time` datetime,
  `end_time` datetime,
  `status` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='折扣表';

-- 首页配置
CREATE TABLE IF NOT EXISTS `homepage_configs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` varchar(255) NOT NULL,
  `title` varchar(255) NOT NULL,
  `ref_id` bigint,
  `config_json` TEXT,
  `sort_order` int,
  `status` int NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页配置表';

-- 后台批量导入任务（列名全面使用 snake_case）
CREATE TABLE IF NOT EXISTS `import_jobs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `module` varchar(50) NOT NULL,
  `status` varchar(50) NOT NULL,
  `file_name` varchar(255),
  `file_path` varchar(255),
  `operator_id` bigint,
  `total_count` int,
  `success_count` int,
  `failure_count` int,
  `error_summary` varchar(1000),
  `content_type` varchar(50),
  `file_size` bigint,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `started_at` datetime,
  `finished_at` datetime,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批量导入任务表';

SET FOREIGN_KEY_CHECKS = 1;
