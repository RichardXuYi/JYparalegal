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
