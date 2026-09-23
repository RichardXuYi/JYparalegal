-- Add missing indexes on admins table
-- MySQL 8.0 does not support CREATE INDEX IF NOT EXISTS (MariaDB syntax),
-- so use information_schema + prepared statement for idempotency.

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'admins' AND index_name = 'idx_admins_username');
SET @sql = IF(@idx_exists = 0, 'CREATE UNIQUE INDEX `idx_admins_username` ON `admins` (`username`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'admins' AND index_name = 'idx_admins_email');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX `idx_admins_email` ON `admins` (`email`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
