-- ============================================================
-- V110__user_config_sync.sql (本档合并原 V110 + V113 + V144)
-- 功能组【用户配置同步 / 版本对齐】版本号区间：V110-V119
-- 内容：用户配置同步 + 全端版本号对齐(原 V113=1.4.59, V144=1.5.0)
-- ============================================================

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

-- ===== 合并自 V113__bump_version_1_4_59 =====

-- ============================================================
-- V113__bump_version_1_4_59.sql
-- 功能组【用户配置同步 / 版本对齐】版本号区间：V110–V119
-- 将全端版本号统一升级至 1.4.59
-- ============================================================

SET NAMES utf8mb4;

-- 更新各端当前版本号
UPDATE app_version
SET current_version = '1.4.59',
    release_notes   = '版本升级至 1.4.59',
    updated_at      = NOW()
WHERE client_type IN ('WEB', 'ADMIN', 'APP', 'STUDIO', 'BACKEND');

-- 新增版本发布历史记录
INSERT IGNORE INTO release_history (client_type, version, release_notes, published_at)
VALUES
  ('WEB',     '1.4.59', '全端版本号统一升级至 1.4.59', NOW()),
  ('ADMIN',   '1.4.59', '全端版本号统一升级至 1.4.59', NOW()),
  ('APP',     '1.4.59', '全端版本号统一升级至 1.4.59', NOW()),
  ('STUDIO',  '1.4.59', '全端版本号统一升级至 1.4.59', NOW()),
  ('BACKEND', '1.4.59', '全端版本号统一升级至 1.4.59', NOW());

-- ===== 合并自 V144__bump_version_1_5_0 =====

-- ============================================================
-- V144__bump_version_1_5_0.sql
-- 功能组【用户配置同步 / 版本对齐】
-- 将全端版本号统一升级至 1.5.0
--
-- 号段说明：版本对齐本属 V110–V119 区间（见 V113），但既有库已应用到 V143，
-- Flyway 默认 out-of-order=false 会拒绝插入低于已应用最高版本的迁移，
-- 故本迁移追加为 V144（D13 法律域号段之外的版本对齐追加）。
--
-- 版本要点：接入控制平面（CP，D16 / docs/08）——身份与套餐权鉴上移 CP，
-- 签章改经 CP chokepoint（FASC 商户凭证只在 CP），本地 JWT/license 降级为 gating。
-- ============================================================

SET NAMES utf8mb4;

-- 更新各端当前版本号
UPDATE app_version
SET current_version = '1.5.0',
    release_notes   = '版本升级至 1.5.0：接入控制平面 CP（身份/套餐权鉴上移，签章 chokepoint，D16）',
    updated_at      = NOW()
WHERE client_type IN ('WEB', 'ADMIN', 'APP', 'STUDIO', 'BACKEND');

-- 新增版本发布历史记录
INSERT IGNORE INTO release_history (client_type, version, release_notes, published_at)
VALUES
  ('WEB',     '1.5.0', '前端登录改由 CP 签发 RS256 passport；套餐/用量 gating 与升级引导（D16）', NOW()),
  ('ADMIN',   '1.5.0', '全端版本号统一升级至 1.5.0', NOW()),
  ('APP',     '1.5.0', '全端版本号统一升级至 1.5.0', NOW()),
  ('STUDIO',  '1.5.0', 'SSO 桥登录目标改 CP；Agent 走 CP 网关时用量在 CP 计量（D16）', NOW()),
  ('BACKEND', '1.5.0', 'auth 由签发改为验证 CP token；送签经 CP chokepoint 扣配额（D16）', NOW());
