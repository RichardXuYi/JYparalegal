-- V138 语音核心（M3' 接口层）。录音原件不可重编码（prd12）；转写带说话人与时间戳双轨留痕。
-- ⚠️ 真实 RTC 需腾讯云 TRTC 凭据（外部凭据，未到位前为接口+留痕骨架）。
CREATE TABLE IF NOT EXISTS voice_session (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  biz_type    VARCHAR(32) NOT NULL DEFAULT 'AGENT_CHAT',
  biz_id      BIGINT NULL,
  status      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  provider    VARCHAR(64) NOT NULL DEFAULT 'trtc-conversational-ai(stub)',
  started_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ended_at    DATETIME(3) NULL,
  KEY idx_vs_tenant (tenant_id),
  CONSTRAINT fk_vs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS voice_recording (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT NOT NULL,
  session_id  BIGINT NOT NULL,
  storage_key VARCHAR(256) NOT NULL,
  sha256      CHAR(64) NOT NULL,
  immutable   TINYINT(1) NOT NULL DEFAULT 1,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_vr_session (session_id),
  CONSTRAINT fk_vr_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_vr_session FOREIGN KEY (session_id) REFERENCES voice_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS voice_transcript_segment (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT NOT NULL,
  session_id  BIGINT NOT NULL,
  speaker     VARCHAR(64) NOT NULL,
  content     TEXT NOT NULL,
  started_at  DATETIME(3) NOT NULL,
  ended_at    DATETIME(3) NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_vt_session (session_id),
  CONSTRAINT fk_vt_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_vt_session FOREIGN KEY (session_id) REFERENCES voice_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
