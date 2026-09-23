-- V139 租户配额表：账号中心（套餐 / 签署份数配额 / AI token 配额）。
-- 幂等约定：CREATE TABLE 携带 IF NOT EXISTS（同 V001 约定）。
CREATE TABLE IF NOT EXISTS tenant_quota (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id      BIGINT NOT NULL,
  plan           VARCHAR(30) NOT NULL DEFAULT 'PRO',
  sign_quota     INT NOT NULL DEFAULT 11000,
  ai_quota_tokens BIGINT NOT NULL DEFAULT 0,
  ai_used_tokens BIGINT NOT NULL DEFAULT 0,
  updated_at     DATETIME(6),
  UNIQUE KEY uk_quota_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
