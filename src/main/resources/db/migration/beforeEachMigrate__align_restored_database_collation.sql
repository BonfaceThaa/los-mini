-- Restored tables retain their collation, but new databases may use another default.
-- Align future tables and columns with tenant IDs without converting existing data.
SET @los_tenant_collation = (SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tenants' AND COLUMN_NAME = 'id');
SET @los_tenant_charset = (SELECT CHARACTER_SET_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tenants' AND COLUMN_NAME = 'id');
SET @los_align_database_sql = IF(@los_tenant_collation IS NULL, 'SELECT 1', CONCAT('ALTER DATABASE `', REPLACE(DATABASE(), '`', '``'), '` CHARACTER SET ', @los_tenant_charset, ' COLLATE ', @los_tenant_collation));
PREPARE los_align_database FROM @los_align_database_sql;
EXECUTE los_align_database;
DEALLOCATE PREPARE los_align_database;
