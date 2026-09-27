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
