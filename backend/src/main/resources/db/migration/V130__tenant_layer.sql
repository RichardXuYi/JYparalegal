-- V130 租户层（D12/D13）。法律域号段起点；V120-V129 预留设备管理，勿占。
-- 基线零编辑：本文件只新增表/列/回填，不改 V001-V113。
CREATE TABLE IF NOT EXISTS tenants (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(128) NOT NULL,
  tenant_type   VARCHAR(32)  NOT NULL DEFAULT 'ENTERPRISE',
  status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  owner_user_id BIGINT NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_tenants_owner FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE companies ADD COLUMN tenant_id BIGINT NULL AFTER id;
ALTER TABLE companies ADD KEY idx_companies_tenant (tenant_id);
ALTER TABLE companies ADD CONSTRAINT fk_companies_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE SET NULL;

ALTER TABLE users ADD COLUMN tenant_id BIGINT NULL AFTER id;
ALTER TABLE users ADD KEY idx_users_tenant (tenant_id);
ALTER TABLE users ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE SET NULL;

-- 回填①：每 company → 一 tenant（初期 1:1）
INSERT INTO tenants (name, tenant_type, status, owner_user_id)
SELECT c.name, 'ENTERPRISE', 'ACTIVE', c.owner_user_id FROM companies c;

UPDATE companies c
  JOIN tenants t ON t.owner_user_id = c.owner_user_id AND t.tenant_type = 'ENTERPRISE' AND t.name = c.name
   SET c.tenant_id = t.id;

-- 回填②：company 成员挂公司 tenant
UPDATE users u JOIN companies c ON u.company_id = c.id SET u.tenant_id = c.tenant_id;

-- 回填③：无 company 的用户 → 个人 tenant
INSERT INTO tenants (name, tenant_type, status, owner_user_id)
SELECT CONCAT('personal-', u.id), 'INDIVIDUAL', 'ACTIVE', u.id FROM users u WHERE u.company_id IS NULL;

UPDATE users u
  JOIN tenants t ON t.owner_user_id = u.id AND t.tenant_type = 'INDIVIDUAL'
   SET u.tenant_id = t.id
 WHERE u.company_id IS NULL;
