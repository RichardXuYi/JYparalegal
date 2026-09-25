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
