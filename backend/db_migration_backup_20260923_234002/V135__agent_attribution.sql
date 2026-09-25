-- V135 Agent 归因四表（关闭 G-2 前置）。M1 只建表，M2' 接线（OpenClaw 回写）。
CREATE TABLE IF NOT EXISTS prompt_version (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  agent_key    VARCHAR(64) NOT NULL,
  version      INT NOT NULL DEFAULT 1,
  content_hash CHAR(64) NOT NULL,
  body         TEXT NOT NULL,
  status       VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  created_by   BIGINT NULL,
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_pv_key_ver (agent_key, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_run (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id         BIGINT NOT NULL,
  company_id        BIGINT NULL,
  user_id           BIGINT NOT NULL,
  session_ref       VARCHAR(64) NOT NULL,
  status            VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
  provider          VARCHAR(64) NOT NULL,
  model             VARCHAR(64) NOT NULL,
  prompt_version_id BIGINT NULL,
  input_tokens      INT NOT NULL DEFAULT 0,
  output_tokens     INT NOT NULL DEFAULT 0,
  cost_micros       BIGINT NOT NULL DEFAULT 0,
  latency_ms        INT NULL,
  fallback_reason   VARCHAR(128) NULL,
  started_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ended_at          DATETIME(3) NULL,
  KEY idx_ar_tenant (tenant_id),
  KEY idx_ar_user (user_id),
  KEY idx_ar_session (session_ref),
  CONSTRAINT fk_ar_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_ar_user   FOREIGN KEY (user_id)   REFERENCES users(id),
  CONSTRAINT fk_ar_pv     FOREIGN KEY (prompt_version_id) REFERENCES prompt_version(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_tool_call (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id         BIGINT NOT NULL,
  tool_name      VARCHAR(64) NOT NULL,
  args_json      JSON NULL,
  result_json    JSON NULL,
  status         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  hitl_required  TINYINT(1) NOT NULL DEFAULT 0,
  hitl_confirmed TINYINT(1) NOT NULL DEFAULT 0,
  hitl_actor_id  BIGINT NULL,
  task_id        BIGINT NULL,
  latency_ms     INT NULL,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_atc_run (run_id),
  KEY idx_atc_task (task_id),
  CONSTRAINT fk_atc_run  FOREIGN KEY (run_id)  REFERENCES agent_run(id) ON DELETE CASCADE,
  CONSTRAINT fk_atc_task FOREIGN KEY (task_id) REFERENCES sign_task(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS usage_event (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id         BIGINT NOT NULL,
  company_id        BIGINT NULL,
  user_id           BIGINT NULL,
  event_type        VARCHAR(64) NOT NULL,
  quantity          BIGINT NOT NULL DEFAULT 1,
  unit_price_micros BIGINT NOT NULL DEFAULT 0,
  total_micros      BIGINT NOT NULL DEFAULT 0,
  agent_run_id      BIGINT NULL,
  occurred_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_ue_tenant (tenant_id),
  KEY idx_ue_type (event_type),
  CONSTRAINT fk_ue_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_ue_run    FOREIGN KEY (agent_run_id) REFERENCES agent_run(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
