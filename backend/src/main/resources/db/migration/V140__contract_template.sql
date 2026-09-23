-- V140 合同模板表。模板正文含 {{varName}} 占位符，variables 为 JSON 变量名清单；租户隔离（tenant_id）。
CREATE TABLE contract_template (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id  BIGINT NOT NULL,
  title      VARCHAR(200) NOT NULL,
  category   VARCHAR(50),
  body       MEDIUMTEXT,
  variables  JSON,
  status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_by BIGINT,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  KEY idx_tpl_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
