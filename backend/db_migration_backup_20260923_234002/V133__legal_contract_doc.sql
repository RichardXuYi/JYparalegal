-- V133 相对方表（doc_file 已在 V132 建）。
-- contract_template 统一由 V140 创建（entity/服务只认 V140 的 title/body/variables 形状），
-- 此处不再建同名表，否则空库回放到 V140 会因表已存在而失败（MySQL 1050）。
CREATE TABLE IF NOT EXISTS counterparty (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id     BIGINT NOT NULL,
  company_id    BIGINT NULL,
  name          VARCHAR(128) NOT NULL,
  uscc          VARCHAR(32)  NULL,
  contact_name  VARCHAR(64)  NULL,
  contact_phone VARCHAR(32)  NULL,
  risk_level    VARCHAR(32)  NULL,
  deleted_at    DATETIME(3) NULL,
  created_by    BIGINT NOT NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_cp_tenant (tenant_id),
  KEY idx_cp_uscc (uscc),
  CONSTRAINT fk_cp_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
