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
