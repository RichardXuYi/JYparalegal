-- ============================================================
-- V130__legal.sql (本档合并原 V130-V149)
-- 功能组【法律域】版本号区间：V130-V139
-- 内容：租户层 / 共享表 tenant 化 / 签署核心 / 合同文档 / 审计 / Agent 归因 /
--       审批合同挂钩 / 租户配额 / 合同模板 / 证据条目 / 签署文件落盘 /
--       出证记录 / 建任务两段式 / e签宝流程号 / 租户供给修复 / 企业机构号
-- 说明：原模拟法庭(原V137)/语音(原V138)模块已下线，建表已移除；
--       版本对齐(原V144)归入 V110 号段。
-- ============================================================

-- ===== 合并自 V130__tenant_layer =====

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

-- ===== 合并自 V131__tenant_shared_tables =====

-- V131 共享表 tenant 化 + 回填（D12）。M1 先 NULL+KEY，M2 收紧 NOT NULL。
ALTER TABLE knowledge_categories ADD COLUMN tenant_id BIGINT NULL, ADD KEY idx_kc_tenant (tenant_id);
ALTER TABLE knowledge_entries    ADD COLUMN tenant_id BIGINT NULL, ADD KEY idx_ke_tenant (tenant_id);
ALTER TABLE notifications        ADD COLUMN tenant_id BIGINT NULL, ADD KEY idx_nt_tenant (tenant_id);
ALTER TABLE user_messages        ADD COLUMN tenant_id BIGINT NULL, ADD KEY idx_um_tenant (tenant_id);
ALTER TABLE user_skills          ADD COLUMN tenant_id BIGINT NULL, ADD KEY idx_usk_tenant (tenant_id);
ALTER TABLE user_config_entries  ADD COLUMN tenant_id BIGINT NULL, ADD KEY idx_uce_tenant (tenant_id);
ALTER TABLE consultations        ADD COLUMN tenant_id BIGINT NULL, ADD KEY idx_csl_tenant (tenant_id);
ALTER TABLE favorites            ADD COLUMN tenant_id BIGINT NULL, ADD KEY idx_fav_tenant (tenant_id);

-- 回填：按属主 users.tenant_id
UPDATE notifications n JOIN users u ON n.user_id = u.id SET n.tenant_id = u.tenant_id;
UPDATE user_messages m JOIN users u ON m.user_id = u.id SET m.tenant_id = u.tenant_id;
UPDATE user_skills s   JOIN users u ON s.user_id  = u.id SET s.tenant_id = u.tenant_id;
UPDATE user_config_entries c JOIN users u ON c.user_id = u.id SET c.tenant_id = u.tenant_id;
UPDATE consultations c JOIN users u ON c.user_id = u.id SET c.tenant_id = u.tenant_id;
UPDATE favorites f JOIN users u ON f.user_id = u.id SET f.tenant_id = u.tenant_id;

-- 注：knowledge_categories / knowledge_entries 基线无属主列 → 保持 NULL（平台级、全租户可见）。
-- 租户私有 KB 的属主列 + ACL 前置过滤在 V140s + Java 层完成（G-3 / D8-V）；落地前 kb 检索必须 Java 层过滤。

-- ===== 合并自 V132__legal_sign_core =====

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

-- ===== 合并自 V133__legal_contract_doc =====

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

-- ===== 合并自 V134__legal_audit =====

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

-- ===== 合并自 V135__agent_attribution =====

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

-- ===== 合并自 V136__approval_mapping_contract_fk =====

-- V136 完成 V040 预留的合同挂钩（dingtalk/wecom approval_mapping.contract_id → sign_task.id）。
-- 守卫：清理历史脏值（占位列，正常为空）
UPDATE dingtalk_approval_mapping SET contract_id = NULL
 WHERE contract_id IS NOT NULL AND contract_id NOT IN (SELECT id FROM sign_task);
UPDATE wecom_approval_mapping SET contract_id = NULL
 WHERE contract_id IS NOT NULL AND contract_id NOT IN (SELECT id FROM sign_task);

ALTER TABLE dingtalk_approval_mapping
  ADD CONSTRAINT fk_dam_contract FOREIGN KEY (contract_id) REFERENCES sign_task(id) ON DELETE SET NULL;
ALTER TABLE wecom_approval_mapping
  ADD CONSTRAINT fk_wam_contract FOREIGN KEY (contract_id) REFERENCES sign_task(id) ON DELETE SET NULL;

-- ===== 合并自 V139__tenant_quota =====

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

-- ===== 合并自 V140__contract_template =====

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

-- ===== 合并自 V141__evidence_item =====

-- V141 证据条目表。按 biz_type+biz_id 挂接业务对象；sha256 固化内容指纹；状态 UNVERIFIED/VERIFIED/REJECTED；租户隔离（tenant_id）。
CREATE TABLE evidence_item (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id     BIGINT NOT NULL,
  biz_type      VARCHAR(20) NOT NULL,
  biz_id        BIGINT,
  name          VARCHAR(200) NOT NULL,
  evidence_type VARCHAR(30),
  source        VARCHAR(50),
  file_path     VARCHAR(500),
  sha256        VARCHAR(64),
  status        VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
  note          VARCHAR(500),
  created_by    BIGINT,
  created_at    DATETIME(6),
  KEY idx_ev_tenant_biz (tenant_id, biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== 合并自 V143__sign_doc_file =====

-- V143 sign_doc 文件落盘列：sign_doc 表（V132 建）已含 sha256 CHAR(64) NOT NULL，
-- 故本迁移只补 file_path / source 两列（仅新增不存在的列）。
ALTER TABLE sign_doc
  ADD COLUMN file_path VARCHAR(500) NULL,
  ADD COLUMN source    VARCHAR(30)  NULL DEFAULT 'UPLOAD';

-- ===== 合并自 V145__certificate_record =====

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

-- ===== 合并自 V146__sign_task_create =====

-- 创建任务两段式（docs/11）：任务单上的到期日/审批占位/配额快照，收件人参与方式与签署要求。
-- 控件表留到 P1。既有参与方默认「只签署」，避免旧任务失去签署资格。
ALTER TABLE sign_task
  ADD COLUMN contract_due_at DATETIME(3) NULL,
  ADD COLUMN contract_due_end_at DATETIME(3) NULL,
  ADD COLUMN approval_flow_id BIGINT NULL,
  ADD COLUMN quota_snapshot INT NULL;

ALTER TABLE sign_party
  ADD COLUMN can_fill TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN can_sign TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN identity_check TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN member_user_id BIGINT NULL,
  ADD COLUMN sign_requirement_json VARCHAR(4000) NULL;

-- ===== 合并自 V147__esign_flow =====

-- V4：e签宝流程号，以及回调幂等。
ALTER TABLE sign_task
  ADD COLUMN provider VARCHAR(32) NULL,
  ADD COLUMN provider_flow_id VARCHAR(128) NULL,
  ADD COLUMN provider_mode VARCHAR(16) NULL;

CREATE TABLE IF NOT EXISTS sign_provider_event (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_key  VARCHAR(191) NOT NULL,
  flow_id    VARCHAR(128) NULL,
  action     VARCHAR(64)  NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_sign_provider_event (event_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== 合并自 V148__provision_missing_tenants =====

-- V148 存量租户供给修复。
-- 背景：V130 只在迁移当时回填了一次 tenant_id；此后由 bootstrap / 注册 / 建公司创建的
-- companies / users 无任何代码建 tenant，tenant_id 恒为 NULL → 法律域 requireTenant 一律拒绝。
-- 运行时代码已补（TenantProvisioningService）；本迁移负责修复历史存量行。
-- 幂等：所有写入均以 tenant_id IS NULL 为守卫，可安全重跑；空库上为 no-op。

-- ① 无 tenant 的公司 → 每公司一 ENTERPRISE 租户（按 owner+name 去重，与 V130 同口径）
INSERT INTO tenants (name, tenant_type, status, owner_user_id)
SELECT c.name, 'ENTERPRISE', 'ACTIVE', c.owner_user_id
FROM companies c
WHERE c.tenant_id IS NULL
  AND c.owner_user_id IS NOT NULL
  AND NOT EXISTS (
        SELECT 1 FROM tenants t
        WHERE t.owner_user_id = c.owner_user_id
          AND t.tenant_type = 'ENTERPRISE'
          AND t.name = c.name
  );

UPDATE companies c
  JOIN tenants t
    ON t.owner_user_id = c.owner_user_id
   AND t.tenant_type = 'ENTERPRISE'
   AND t.name = c.name
   SET c.tenant_id = t.id
 WHERE c.tenant_id IS NULL;

-- ② 有公司的用户 → 挂公司租户
UPDATE users u
  JOIN companies c ON u.company_id = c.id
   SET u.tenant_id = c.tenant_id
 WHERE u.tenant_id IS NULL
   AND c.tenant_id IS NOT NULL;

-- ③ 无公司的用户 → 个人租户
INSERT INTO tenants (name, tenant_type, status, owner_user_id)
SELECT CONCAT('personal-', u.id), 'INDIVIDUAL', 'ACTIVE', u.id
FROM users u
WHERE u.company_id IS NULL
  AND u.tenant_id IS NULL
  AND NOT EXISTS (
        SELECT 1 FROM tenants t
        WHERE t.owner_user_id = u.id
          AND t.tenant_type = 'INDIVIDUAL'
  );

UPDATE users u
  JOIN tenants t
    ON t.owner_user_id = u.id
   AND t.tenant_type = 'INDIVIDUAL'
   SET u.tenant_id = t.id
 WHERE u.company_id IS NULL
   AND u.tenant_id IS NULL;

-- ===== 合并自 V149__company_esign_org =====

-- V149：企业 e签宝机构号（机构实名后回填），用于企业章签署（signers[].orgSignerInfo.orgId）。
ALTER TABLE companies
  ADD COLUMN esign_org_id VARCHAR(64) NULL;

