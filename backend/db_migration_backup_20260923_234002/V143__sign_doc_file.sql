-- V143 sign_doc 文件落盘列：sign_doc 表（V132 建）已含 sha256 CHAR(64) NOT NULL，
-- 故本迁移只补 file_path / source 两列（仅新增不存在的列）。
ALTER TABLE sign_doc
  ADD COLUMN file_path VARCHAR(500) NULL,
  ADD COLUMN source    VARCHAR(30)  NULL DEFAULT 'UPLOAD';
