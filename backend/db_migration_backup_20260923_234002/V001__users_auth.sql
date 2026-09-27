-- ============================================================
-- V001__users_auth.sql
-- 功能组【账号与安全】版本号区间：V001–V009
-- 表：users / admins / user_refresh_tokens / security_audit_logs
--
-- 全局版本号规划（每 10 个版本号一个功能组，上限 V999）：
--   V001–V009  账号与安全
--   V010–V019  组织架构
--   V020–V029  知识库（RAG 记忆）
--   V030–V039  技能库（Skill 同步）
--   V040–V049  渠道集成（钉钉/飞书/企微）
--   V050–V059  商品
--   V060–V069  交易（购物车/订单/支付/退款）
--   V070–V079  营销（活动/优惠券/首页配置/导入任务）
--   V080–V089  内容（资讯/站点内容）
--   V090–V099  互动与通知（咨询/收藏/消息/通知）
--   V100–V109  客户端版本发布
--   V110–V119  用户配置云同步 + Gateway 路由
--   V120–V129  设备管理与登录安全增强
--   V130–V899  预留（未来功能大类扩展）
--   V900–V999  预留（全局重构与紧急修复）
--
-- 重构说明（整体重写版）：
--   - 内联 user_refresh_tokens 设备管理字段（原 V120 ALTER 合并到此）
--   - 补建 security_audit_logs 查询索引（原 V001 缺失，实体 @Index 声明）
--   - 所有时间戳列添加 DEFAULT CURRENT_TIMESTAMP
--   - 添加 user_refresh_tokens -> users 外键约束
--
-- 幂等约定：所有 CREATE TABLE 携带 IF NOT EXISTS；
-- 种子数据使用 INSERT IGNORE / ON DUPLICATE KEY，可安全重放。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 平台用户（数智员工的"雇主"账号）
CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(255) NOT NULL,
  `email` varchar(255),
  `phone` varchar(255),
  `password_hash` varchar(255),
  `avatar` varchar(255),
  `status` int DEFAULT 1,
  `user_type` varchar(20) DEFAULT 'PERSONAL',
  `company_id` bigint,
  `department_id` bigint,
  `position_id` bigint,
  `email_verified` tinyint(1) DEFAULT 0,
  `last_login_ip` varchar(255),
  `last_login_time` datetime,
  `failed_login_attempts` int DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_phone` (`phone`),
  KEY `idx_users_company_id` (`company_id`),
  KEY `idx_users_department_id` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台用户表';

-- 后台管理员
CREATE TABLE IF NOT EXISTS `admins` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(255) NOT NULL,
  `email` varchar(255),
  `password_hash` varchar(255),
  `avatar` varchar(255),
  `role` varchar(255) NOT NULL,
  `status` int DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台管理员表';

-- 跨端 JWT 刷新令牌（存储哈希，支持吊销与设备管理）
CREATE TABLE IF NOT EXISTS `user_refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `username` varchar(255) NOT NULL,
  `token_hash` varchar(128) NOT NULL,
  `revoked` tinyint(1) NOT NULL DEFAULT 0,
  `expires_at` datetime NOT NULL,
  `device_name` varchar(255) COMMENT '客户端自动检测的设备名称',
  `ip_address` varchar(255) COMMENT '登录时的客户端 IP 地址',
  `user_agent` varchar(500) COMMENT '登录时的客户端 User-Agent',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_refresh_tokens_token_hash` (`token_hash`),
  KEY `idx_user_refresh_tokens_user_id` (`user_id`),
  CONSTRAINT `fk_refresh_tokens_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JWT 刷新令牌表';

-- 安全审计日志
CREATE TABLE IF NOT EXISTS `security_audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_type` varchar(255) NOT NULL,
  `username` varchar(255),
  `ip_address` varchar(255),
  `user_agent` TEXT,
  `device_fingerprint` varchar(255),
  `details` TEXT,
  `result` varchar(255),
  `error_message` TEXT,
  `risk_level` varchar(255),
  `requires_investigation` tinyint(1) DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_security_audit_logs_username` (`username`),
  KEY `idx_security_audit_logs_ip_address` (`ip_address`),
  KEY `idx_security_audit_logs_event_type` (`event_type`),
  KEY `idx_security_audit_logs_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全审计日志表';

SET FOREIGN_KEY_CHECKS = 1;
