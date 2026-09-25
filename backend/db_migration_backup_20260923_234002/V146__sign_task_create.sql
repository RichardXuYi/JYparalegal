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
