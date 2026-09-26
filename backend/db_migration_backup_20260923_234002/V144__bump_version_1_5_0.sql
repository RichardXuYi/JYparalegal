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
