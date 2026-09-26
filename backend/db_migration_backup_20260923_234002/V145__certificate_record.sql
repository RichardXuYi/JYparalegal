-- V145 出证记录（append-only，永不软删；D14 审计表约定）。
-- 出证是一次性事件：首次出证落库，重复调用返回同一记录（certNo/issuedAt 稳定），
-- 修正"每次现生成 certificateNo/issuedAt、不落库、无证据链"（审阅 #18）。
CREATE TABLE IF NOT EXISTS certificate_record (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id        BIGINT NOT NULL,
  task_id          BIGINT NOT NULL,
  cert_no          VARCHAR(64)  NOT NULL,
  authority        VARCHAR(128) NOT NULL,
  provider         VARCHAR(32)  NOT NULL,
  provider_task_id VARCHAR(128) NULL,
  provider_cert_no VARCHAR(128) NULL,
  doc_sha256       CHAR(64)     NULL,
  issued_by        BIGINT       NOT NULL,
  issued_at        DATETIME(3)  NOT NULL,
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_cert_task (task_id),
  KEY idx_cert_tenant (tenant_id),
  CONSTRAINT fk_cert_task   FOREIGN KEY (task_id)   REFERENCES sign_task(id),
  CONSTRAINT fk_cert_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
