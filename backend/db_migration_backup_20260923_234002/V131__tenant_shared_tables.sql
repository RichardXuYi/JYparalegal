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
