-- ============================================================
-- V010__organization.sql
-- 功能组【组织架构】版本号区间：V010–V019
-- 表：companies / departments / positions
--
-- 重构说明：
--   - 添加外键约束（companies->users, departments->companies, positions->companies/departments）
--   - 补建外键列索引
--   - 所有时间戳列添加 DEFAULT CURRENT_TIMESTAMP
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 公司
CREATE TABLE IF NOT EXISTS `companies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `unified_credit_code` varchar(18),
  `legal_person` varchar(50),
  `contact_phone` varchar(20),
  `contact_email` varchar(100),
  `address` varchar(255),
  `logo` varchar(255),
  `business_license` varchar(255),
  `status` int DEFAULT 1,
  `owner_user_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_companies_owner_user_id` (`owner_user_id`),
  CONSTRAINT `fk_companies_owner_user_id` FOREIGN KEY (`owner_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公司表';

-- 部门
CREATE TABLE IF NOT EXISTS `departments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `parent_id` bigint,
  `name` varchar(50) NOT NULL,
  `code` varchar(20),
  `leader_user_id` bigint,
  `sort_order` int DEFAULT 0,
  `status` int DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_departments_company_id` (`company_id`),
  KEY `idx_departments_parent_id` (`parent_id`),
  CONSTRAINT `fk_departments_company_id` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_departments_parent_id` FOREIGN KEY (`parent_id`) REFERENCES `departments` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- 岗位
CREATE TABLE IF NOT EXISTS `positions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  `name` varchar(50) NOT NULL,
  `code` varchar(20),
  `level` int,
  `description` varchar(200),
  `status` int DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_positions_company_id` (`company_id`),
  KEY `idx_positions_department_id` (`department_id`),
  CONSTRAINT `fk_positions_company_id` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_positions_department_id` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位表';

SET FOREIGN_KEY_CHECKS = 1;
