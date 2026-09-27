CREATE TABLE IF NOT EXISTS archive_file (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id     BIGINT NOT NULL,
  case_id       BIGINT NULL,
  file_name     VARCHAR(255) NOT NULL,
  relative_path VARCHAR(500) NOT NULL,
  mime          VARCHAR(120) NULL,
  size_bytes    BIGINT NOT NULL,
  sha256        CHAR(64) NOT NULL,
  parse_status  VARCHAR(20) NOT NULL,
  parse_error   VARCHAR(500) NULL,
  created_by    BIGINT NOT NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_af_case (tenant_id, case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS archive_chunk (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id  BIGINT NOT NULL,
  file_id    BIGINT NOT NULL,
  case_id    BIGINT NULL,
  seq        INT NOT NULL,
  body       MEDIUMTEXT NOT NULL,
  locator    VARCHAR(64) NULL,
  KEY idx_ac_case (tenant_id, case_id),
  CONSTRAINT fk_ac_file FOREIGN KEY (file_id) REFERENCES archive_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS moot_case (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id     BIGINT NOT NULL,
  sign_task_id  BIGINT NULL,
  stance        VARCHAR(20) NOT NULL,
  summary       VARCHAR(2000) NOT NULL,
  status        VARCHAR(20) NOT NULL,
  created_by    BIGINT NOT NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_mc_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS moot_hearing (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_id      BIGINT NOT NULL,
  tenant_id    BIGINT NOT NULL,
  status       VARCHAR(20) NOT NULL,
  human_roles  VARCHAR(200) NOT NULL DEFAULT '',
  started_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  closed_at    DATETIME(3) NULL,
  KEY idx_mh_case (tenant_id, case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS moot_role (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  hearing_id  BIGINT NOT NULL,
  tenant_id   BIGINT NOT NULL,
  role_code   VARCHAR(20) NOT NULL,
  agent_id    VARCHAR(64) NOT NULL,
  session_key VARCHAR(180) NULL,
  UNIQUE KEY uk_mr (hearing_id, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS moot_slot (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  hearing_id  BIGINT NOT NULL,
  tenant_id   BIGINT NOT NULL,
  seq         INT NOT NULL,
  phase       VARCHAR(20) NOT NULL,
  role_code   VARCHAR(20) NOT NULL,
  status      VARCHAR(20) NOT NULL,
  plan_text   VARCHAR(200) NOT NULL,
  KEY idx_ms_hearing (hearing_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS moot_utterance (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  hearing_id  BIGINT NOT NULL,
  slot_id     BIGINT NOT NULL,
  tenant_id   BIGINT NOT NULL,
  role_code   VARCHAR(20) NOT NULL,
  speaker     VARCHAR(20) NOT NULL,
  body        MEDIUMTEXT NOT NULL,
  char_count  INT NOT NULL,
  started_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ended_at    DATETIME(3) NULL,
  KEY idx_mu_hearing (hearing_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS moot_retrieval (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  slot_id   BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  chunk_id  BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  seq       INT NOT NULL,
  excerpt   MEDIUMTEXT NOT NULL,
  KEY idx_mret_slot (slot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
