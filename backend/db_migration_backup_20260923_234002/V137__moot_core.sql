-- V137 模拟法庭核心（M4'）。庭审状态机含反向跳转（prd13 §5）；评分 rubric 可解释（prd13 §6）。
CREATE TABLE IF NOT EXISTS moot_case (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT NOT NULL,
  title       VARCHAR(256) NOT NULL,
  case_type   VARCHAR(32) NOT NULL DEFAULT 'CIVIL',
  phase       VARCHAR(32) NOT NULL DEFAULT 'PREP',
  status      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by  BIGINT NOT NULL,
  deleted_at  DATETIME(3) NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_mc_tenant (tenant_id),
  CONSTRAINT fk_mc_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS moot_role (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id  BIGINT NOT NULL,
  case_id    BIGINT NOT NULL,
  role_type  VARCHAR(32) NOT NULL,
  user_id    BIGINT NULL,
  agent_flag TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_mr_case (case_id),
  CONSTRAINT fk_mr_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_mr_case FOREIGN KEY (case_id) REFERENCES moot_case(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS moot_phase_event (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT NOT NULL,
  case_id     BIGINT NOT NULL,
  from_phase  VARCHAR(32) NULL,
  to_phase    VARCHAR(32) NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  reason      VARCHAR(256) NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_mpe_case (case_id),
  CONSTRAINT fk_mpe_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_mpe_case FOREIGN KEY (case_id) REFERENCES moot_case(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS moot_score (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT NOT NULL,
  case_id     BIGINT NOT NULL,
  role_id     BIGINT NULL,
  dimension   VARCHAR(64) NOT NULL,
  score       INT NOT NULL,
  rationale   VARCHAR(512) NOT NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_ms_case (case_id),
  CONSTRAINT fk_ms_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_ms_case FOREIGN KEY (case_id) REFERENCES moot_case(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS moot_minute (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id  BIGINT NOT NULL,
  case_id    BIGINT NOT NULL,
  speaker    VARCHAR(64) NOT NULL,
  content    TEXT NOT NULL,
  spoken_at  DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_mm_case (case_id),
  CONSTRAINT fk_mm_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_mm_case FOREIGN KEY (case_id) REFERENCES moot_case(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
