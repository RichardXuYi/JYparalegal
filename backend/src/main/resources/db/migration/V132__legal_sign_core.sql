-- V132 法律签署核心 5 表。状态机/枚举继承 旧版 prd/10 §5 与 旧版 DB §2.1。
CREATE TABLE IF NOT EXISTS sign_task (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id            BIGINT NOT NULL,
  company_id           BIGINT NULL,
  title                VARCHAR(256) NOT NULL,
  task_no              VARCHAR(64)  NOT NULL,
  status               VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
  sign_mode            VARCHAR(32)  NOT NULL DEFAULT 'PARALLEL',
  finalize_mode        VARCHAR(32)  NOT NULL DEFAULT 'AUTO',
  source               VARCHAR(32)  NOT NULL DEFAULT 'WEB',
  initiator_company_id BIGINT NULL,
  expire_at            DATETIME(3) NULL,
  version              INT NOT NULL DEFAULT 0,
  created_by           BIGINT NOT NULL,
  updated_by           BIGINT NULL,
  deleted_at           DATETIME(3) NULL,
  created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_sign_task_no (task_no),
  KEY idx_sign_task_tenant (tenant_id),
  KEY idx_sign_task_company (company_id),
  KEY idx_sign_task_status (status),
  KEY idx_sign_task_expire (expire_at),
  CONSTRAINT fk_st_tenant  FOREIGN KEY (tenant_id)  REFERENCES tenants(id),
  CONSTRAINT fk_st_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL,
  CONSTRAINT fk_st_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sign_party (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id      BIGINT NOT NULL,
  task_id        BIGINT NOT NULL,
  party_role     VARCHAR(32) NOT NULL DEFAULT 'SIGNER',
  party_type     VARCHAR(32) NOT NULL DEFAULT 'ORG',
  party_status   VARCHAR(32) NOT NULL DEFAULT 'PENDING_FILL',
  sign_order     INT NOT NULL DEFAULT 1,
  user_id        BIGINT NULL,
  external_name  VARCHAR(64)  NULL,
  external_phone VARCHAR(32)  NULL,
  external_email VARCHAR(128) NULL,
  sign_invite_id BIGINT NULL,
  signed_at      DATETIME(3) NULL,
  created_by     BIGINT NOT NULL,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_sp_task (task_id),
  KEY idx_sp_tenant (tenant_id),
  KEY idx_sp_user (user_id),
  CONSTRAINT fk_sp_task   FOREIGN KEY (task_id) REFERENCES sign_task(id) ON DELETE CASCADE,
  CONSTRAINT fk_sp_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_sp_user   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS doc_file (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id     BIGINT NOT NULL,
  company_id    BIGINT NULL,
  storage_key   VARCHAR(256) NOT NULL,
  file_name     VARCHAR(256) NOT NULL,
  mime          VARCHAR(64)  NULL,
  size_bytes    BIGINT NOT NULL DEFAULT 0,
  sha256        CHAR(64) NOT NULL,
  owner_user_id BIGINT NOT NULL,
  deleted_at    DATETIME(3) NULL,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_df_tenant (tenant_id),
  KEY idx_df_owner (owner_user_id),
  CONSTRAINT fk_df_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_df_owner  FOREIGN KEY (owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sign_doc (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id       BIGINT NOT NULL,
  task_id         BIGINT NOT NULL,
  doc_file_id     BIGINT NOT NULL,
  evidence_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
  sha256          CHAR(64) NOT NULL,
  created_by      BIGINT NOT NULL,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_sd_task (task_id),
  KEY idx_sd_tenant (tenant_id),
  CONSTRAINT fk_sd_task  FOREIGN KEY (task_id) REFERENCES sign_task(id) ON DELETE CASCADE,
  CONSTRAINT fk_sd_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_sd_file  FOREIGN KEY (doc_file_id) REFERENCES doc_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sign_invite (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id         BIGINT NOT NULL,
  task_id           BIGINT NOT NULL,
  party_id          BIGINT NULL,
  invite_status     VARCHAR(32) NOT NULL DEFAULT 'WAIT',
  target_user_id    BIGINT NULL,
  target_company_id BIGINT NULL,
  token_hash        CHAR(64) NOT NULL,
  expire_at         DATETIME(3) NOT NULL,
  created_by        BIGINT NOT NULL,
  created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_si_task (task_id),
  KEY idx_si_target_user (target_user_id),
  KEY idx_si_status (invite_status),
  CONSTRAINT fk_si_task  FOREIGN KEY (task_id) REFERENCES sign_task(id) ON DELETE CASCADE,
  CONSTRAINT fk_si_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sign_cc (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id  BIGINT NOT NULL,
  task_id    BIGINT NOT NULL,
  user_id    BIGINT NOT NULL,
  read_at    DATETIME(3) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_cc_task (task_id),
  KEY idx_cc_user (user_id),
  CONSTRAINT fk_cc_task  FOREIGN KEY (task_id) REFERENCES sign_task(id) ON DELETE CASCADE,
  CONSTRAINT fk_cc_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_cc_user  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
