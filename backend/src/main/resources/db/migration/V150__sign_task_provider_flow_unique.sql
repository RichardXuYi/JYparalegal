-- 送签并发防护（BE-4）：为 sign_task.provider_flow_id 增加唯一索引。
--
-- 背景：chargeForSigning 原本为"读 providerFlowId 判空 → 调 CP 建流程 → 回写"，
-- 无锁且该列无唯一约束，并发 SUBMIT 会双双越过判空、各自创建一条 e签宝流程并双扣费。
-- 应用层已加 taskId 分段锁（单实例串行化）；此唯一索引作为跨实例的最终兜底：
-- 第二个写入者会触发 DataIntegrityViolation，无法产生重复的 provider_flow_id。
--
-- provider_flow_id 可空，MySQL 唯一索引允许多行 NULL（尚未送签的任务），
-- 仅约束"已生成的流程号"唯一，不影响正常草稿/未送签任务。
ALTER TABLE sign_task
  ADD UNIQUE KEY uk_sign_task_provider_flow_id (provider_flow_id);
