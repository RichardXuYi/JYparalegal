-- ============================================================
-- V110__user_config_sync.sql
-- 功能组【用户配置云同步 + Gateway 舰队路由】版本号区间：V110–V119
-- 表：user_config_entries / user_sync_bundles /
--     gateway_nodes / user_gateway_assignments
--
-- 重构说明：
--   - 添加外键约束（config_entries/bundles/gateway_assignments -> users）
--   - 补建外键列索引
--   - 所有时间戳列添加 DEFAULT CURRENT_TIMESTAMP
-- 说明：
--   user_config_entries  -- 用户配置 KV（profile / preferences / points / usage …
--                            预留 namespace，新增配置类型零结构变更）。
--   user_sync_bundles    -- 文件型同步包（agent-profile：SOUL/IDENTITY/MEMORY 等
--                            workspace 文件），文件内容落服务器磁盘
--                            （见 UserSyncBundleService），此表仅存元数据与内容指纹。
--   gateway_nodes / user_gateway_assignments -- studio-web 多节点舰队的路由表
--                            （Phase 2 预留：节点注册/心跳/容量 与 用户->节点粘性分配）。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `user_config_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `namespace` varchar(64) NOT NULL,
  `config_key` varchar(128) NOT NULL,
  `content_json` mediumtext NOT NULL,
  `content_hash` varchar(128) NOT NULL,
  `version` bigint NOT NULL DEFAULT 1,
  `client_type` varchar(32),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_config_user_ns_key` (`user_id`, `namespace`, `config_key`),
  KEY `idx_user_config_user_ns` (`user_id`, `namespace`),
  CONSTRAINT `fk_user_config_entries_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户配置 KV 表';

CREATE TABLE IF NOT EXISTS `user_sync_bundles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `kind` varchar(64) NOT NULL,
  `slug` varchar(255) NOT NULL,
  `content_hash` varchar(128) NOT NULL,
  `storage_path` varchar(512) NOT NULL,
  `size_bytes` bigint NOT NULL DEFAULT 0,
  `file_count` int NOT NULL DEFAULT 0,
  `client_type` varchar(32),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_bundles_user_kind_slug` (`user_id`, `kind`, `slug`),
  KEY `idx_user_bundles_user_kind` (`user_id`, `kind`),
  CONSTRAINT `fk_user_sync_bundles_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件型同步包表';

-- Phase 2 舰队路由表（结构预留，本期不写业务逻辑）
CREATE TABLE IF NOT EXISTS `gateway_nodes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `node_key` varchar(128) NOT NULL,
  `host` varchar(255) NOT NULL,
  `port` int NOT NULL,
  `capacity` int NOT NULL DEFAULT 300,
  `active_users` int NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL DEFAULT 'online',
  `last_heartbeat_at` datetime,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gateway_nodes_node_key` (`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Gateway 节点表';

CREATE TABLE IF NOT EXISTS `user_gateway_assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `node_key` varchar(128) NOT NULL,
  `assigned_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_active_at` datetime,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_gateway_user` (`user_id`),
  KEY `idx_user_gateway_node` (`node_key`),
  CONSTRAINT `fk_user_gateway_assignments_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户 Gateway 分配表';

SET FOREIGN_KEY_CHECKS = 1;
