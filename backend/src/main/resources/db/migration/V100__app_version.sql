-- ============================================================
-- V100__app_version.sql
-- 功能组【客户端版本发布】版本号区间：V100–V109
-- 表：app_version / release_history
--
-- 重构说明：
--   - 种子数据版本号直接对齐 v1.4.58（原 V121/V122 的版本升级
--     逻辑已内联，消除了引用错误列名的 BUG）
--   - release_history 内联全部历史版本记录
--   - 所有时间戳列已有 DEFAULT CURRENT_TIMESTAMP（保持不变）
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS app_version (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  client_type     VARCHAR(32)  NOT NULL COMMENT '客户端类型: WEB/ADMIN/APP/STUDIO/BACKEND',
  current_version VARCHAR(32)  NOT NULL COMMENT '当前最新版本号',
  min_version     VARCHAR(32)  NOT NULL COMMENT '最低兼容版本号',
  download_url    VARCHAR(512) NULL     COMMENT '安装包下载地址',
  release_notes   TEXT         NULL     COMMENT '更新说明(Markdown)',
  force_update    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否强制更新',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_version_client_type (client_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='各端最新版本信息';

CREATE TABLE IF NOT EXISTS release_history (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  client_type     VARCHAR(32)  NOT NULL,
  version         VARCHAR(32)  NOT NULL,
  release_notes   TEXT         NULL,
  download_url    VARCHAR(512) NULL,
  force_update    TINYINT(1)   NOT NULL DEFAULT 0,
  published_by    BIGINT       NULL     COMMENT '发布人 user_id',
  published_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_release_history_client_version (client_type, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='版本发布历史';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 种子数据：各端默认版本记录（对齐 v1.4.58）
-- ============================================================

INSERT INTO app_version (client_type, current_version, min_version, release_notes) VALUES
  ('WEB',     '1.4.58', '1.0.0', '初始版本'),
  ('ADMIN',   '1.4.58', '1.0.0', '初始版本'),
  ('APP',     '1.4.58', '1.0.0', '初始版本'),
  ('STUDIO',  '1.4.58', '1.0.0', '初始版本'),
  ('BACKEND', '1.4.58', '1.0.0', '初始版本')
ON DUPLICATE KEY UPDATE
  current_version = VALUES(current_version),
  min_version     = VALUES(min_version);

-- 版本发布历史记录
INSERT IGNORE INTO release_history (client_type, version, release_notes) VALUES
  ('WEB',     '1.4.01', '初始版本'),
  ('ADMIN',   '1.4.01', '初始版本'),
  ('APP',     '1.4.01', '初始版本'),
  ('STUDIO',  '1.4.01', '初始版本'),
  ('BACKEND', '1.4.01', '初始版本'),
  ('WEB',     '1.4.56', '版本升级至 1.4.56'),
  ('ADMIN',   '1.4.56', '版本升级至 1.4.56'),
  ('APP',     '1.4.56', '版本升级至 1.4.56'),
  ('STUDIO',  '1.4.56', '版本升级至 1.4.56'),
  ('BACKEND', '1.4.56', '版本升级至 1.4.56'),
  ('WEB',     '1.4.58', '版本升级至 1.4.58'),
  ('ADMIN',   '1.4.58', '版本升级至 1.4.58'),
  ('APP',     '1.4.58', '版本升级至 1.4.58'),
  ('STUDIO',  '1.4.58', '版本升级至 1.4.58'),
  ('BACKEND', '1.4.58', '版本升级至 1.4.58');
