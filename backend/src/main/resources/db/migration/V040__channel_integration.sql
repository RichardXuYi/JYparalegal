-- ============================================================
-- V040__channel_integration.sql
-- 功能组【渠道集成（钉钉/飞书/企微）】版本号区间：V040–V049
-- 表：user_dingtalk_binding / dingtalk_approval_mapping /
--     user_feishu_binding / wecom_approval_mapping /
--     wecom_department_mapping
--
-- 重构说明：
--   - contract_id 改为可空（原 NOT NULL 但合同模块不存在，属幻影引用）
--   - 添加 user_dingtalk_binding / user_feishu_binding -> users 外键约束
--   - 所有时间戳列添加 DEFAULT CURRENT_TIMESTAMP
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 钉钉用户绑定
CREATE TABLE IF NOT EXISTS `user_dingtalk_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `dingtalk_unionid` varchar(100) NOT NULL,
  `dingtalk_userid` varchar(100),
  `dingtalk_mobile` varchar(20),
  `access_token` varchar(500),
  `refresh_token` varchar(500),
  `token_expires_at` datetime,
  `is_active` tinyint(1) DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_dingtalk_binding_user_id` (`user_id`),
  UNIQUE KEY `uk_user_dingtalk_binding_unionid` (`dingtalk_unionid`),
  CONSTRAINT `fk_dingtalk_binding_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钉钉用户绑定表';

-- 钉钉审批映射
CREATE TABLE IF NOT EXISTS `dingtalk_approval_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `contract_id` bigint COMMENT '业务关联 ID（合同模块未实现，暂为可空）',
  `dingtalk_approval_id` varchar(100) NOT NULL,
  `dingtalk_process_code` varchar(100),
  `status` varchar(50),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dingtalk_approval_mapping_approval_id` (`dingtalk_approval_id`),
  KEY `idx_dingtalk_approval_mapping_contract_id` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钉钉审批映射表';

-- 飞书用户绑定
CREATE TABLE IF NOT EXISTS `user_feishu_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `feishu_union_id` varchar(100) NOT NULL,
  `feishu_open_id` varchar(100),
  `feishu_user_id` varchar(100),
  `feishu_email` varchar(200),
  `feishu_mobile` varchar(20),
  `user_access_token` varchar(500),
  `refresh_token` varchar(500),
  `token_expires_at` datetime,
  `is_active` tinyint(1) DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_feishu_binding_user_id` (`user_id`),
  UNIQUE KEY `uk_user_feishu_binding_union_id` (`feishu_union_id`),
  CONSTRAINT `fk_feishu_binding_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='飞书用户绑定表';

-- 企微审批映射
CREATE TABLE IF NOT EXISTS `wecom_approval_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `contract_id` bigint COMMENT '业务关联 ID（合同模块未实现，暂为可空）',
  `wecom_sp_no` varchar(100) NOT NULL,
  `template_id` varchar(100),
  `status` varchar(50),
  `applicant_userid` varchar(100),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_wecom_approval_mapping_contract_id` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企微审批映射表';

-- 企微部门映射
CREATE TABLE IF NOT EXISTS `wecom_department_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wecom_dept_id` bigint NOT NULL,
  `wecom_dept_name` varchar(100),
  `parent_id` bigint,
  `order_num` int,
  `synced_at` datetime,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wecom_department_mapping_dept_id` (`wecom_dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企微部门映射表';

SET FOREIGN_KEY_CHECKS = 1;
