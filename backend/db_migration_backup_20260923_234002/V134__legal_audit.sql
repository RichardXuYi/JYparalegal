-- V134 审计三表（关闭 G-1）。append-only，永不软删。
CREATE TABLE IF NOT EXISTS audit_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT NOT NULL,
  company_id  BIGINT NULL,
  actor_type  VARCHAR(16) NOT NULL,
  actor_id    BIGINT NULL,
  action      VARCHAR(64) NOT NULL,
  entity_type VARCHAR(64) NOT NULL,
  entity_id   BIGINT NULL,
  before_json JSON NULL,
  after_json  JSON NULL,
  ip          VARCHAR(64) NULL,
  trace_id    VARCHAR(64) NULL,
  result      VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_al_tenant (tenant_id),
  KEY idx_al_entity (entity_type, entity_id),
  KEY idx_al_actor (actor_type, actor_id),
  CONSTRAINT fk_al_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS task_event (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id    BIGINT NOT NULL,
  task_id      BIGINT NOT NULL,
  from_status  VARCHAR(32) NULL,
  to_status    VARCHAR(32) NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  actor_type   VARCHAR(16) NOT NULL,
  actor_id     BIGINT NULL,
  reason       VARCHAR(256) NULL,
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_te_task (task_id),
  KEY idx_te_tenant (tenant_id),
  CONSTRAINT fk_te_task   FOREIGN KEY (task_id)  REFERENCES sign_task(id) ON DELETE RESTRICT,
  CONSTRAINT fk_te_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS data_access_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT NOT NULL,
  actor_type  VARCHAR(16) NOT NULL,
  actor_id    BIGINT NULL,
  entity_type VARCHAR(64) NOT NULL,
  entity_id   BIGINT NULL,
  access_type VARCHAR(32) NOT NULL,
  field_mask  JSON NULL,
  ip          VARCHAR(64) NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_dal_entity (entity_type, entity_id),
  KEY idx_dal_tenant (tenant_id),
  CONSTRAINT fk_dal_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
