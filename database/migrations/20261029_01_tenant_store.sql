-- Single-store foundation only. Stop all writers before migration.
-- MySQL DDL commits implicitly; repair a failure before rerunning.
-- The default identity preserves legacy writes until isolation is implemented.
DELIMITER $$
DROP PROCEDURE IF EXISTS `_junzhou_20261029_tenant_store`$$
CREATE PROCEDURE `_junzhou_20261029_tenant_store`()
BEGIN
  IF DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the application database';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('employee','category','dish','setmeal','orders','shopping_cart')
      AND table_type='BASE TABLE' AND engine='InnoDB') <> 6 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required business tables must be InnoDB base tables';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('tenant','store') AND (table_type <> 'BASE TABLE' OR engine <> 'InnoDB')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing tenant/store table is incompatible';
  END IF;
  CREATE TABLE IF NOT EXISTS tenant (
    id bigint NOT NULL AUTO_INCREMENT,
    code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name varchar(100) NOT NULL,
    status tinyint NOT NULL DEFAULT 1,
    PRIMARY KEY (id), UNIQUE KEY uk_tenant_code (code)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  CREATE TABLE IF NOT EXISTS store (
    id bigint NOT NULL AUTO_INCREMENT,
    tenant_id bigint NOT NULL,
    code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name varchar(100) NOT NULL,
    status tinyint NOT NULL DEFAULT 1,
    PRIMARY KEY (id), UNIQUE KEY uk_store_tenant_code (tenant_id,code),
    CONSTRAINT fk_store_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  -- CREATE IF NOT EXISTS must not silently accept an unrelated existing table.
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name IN ('tenant','store') AND (
        (column_name='id' AND column_type='bigint' AND is_nullable='NO' AND extra='auto_increment') OR
        (table_name='store' AND column_name='tenant_id' AND column_type='bigint' AND is_nullable='NO') OR
        (column_name='code' AND column_type='varchar(64)' AND is_nullable='NO' AND collation_name='ascii_bin') OR
        (column_name='name' AND column_type='varchar(100)' AND is_nullable='NO') OR
        (column_name='status' AND column_type='tinyint' AND is_nullable='NO' AND column_default='1')
      )) <> 9
     OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('tenant','store')) <> 9
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
         WHERE table_schema=DATABASE() AND table_name='tenant' AND index_name='PRIMARY' AND non_unique=0) <=> 'id')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
         WHERE table_schema=DATABASE() AND table_name='store' AND index_name='PRIMARY' AND non_unique=0) <=> 'id')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
         WHERE table_schema=DATABASE() AND table_name='tenant' AND index_name='uk_tenant_code' AND non_unique=0 AND sub_part IS NULL) <=> 'code')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
         WHERE table_schema=DATABASE() AND table_name='store' AND index_name='uk_store_tenant_code' AND non_unique=0 AND sub_part IS NULL) <=> 'tenant_id,code')
     OR NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE table_schema=DATABASE()
                    AND table_name='store' AND column_name='tenant_id' AND referenced_table_name='tenant' AND referenced_column_name='id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing tenant/store definition is incompatible';
  END IF;
  IF (SELECT COUNT(*) FROM tenant WHERE id=1 AND code='legacy-single-store') <> 1
     AND (SELECT COUNT(*) FROM tenant) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Default tenant identity conflicts; inspect manually';
  END IF;
  INSERT INTO tenant (id,code,name,status)
    SELECT 1,'legacy-single-store','默认商户',1 WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE id=1);
  IF (SELECT COUNT(*) FROM store WHERE id=1 AND tenant_id=1 AND code='legacy-single-store') <> 1
     AND (SELECT COUNT(*) FROM store) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Default store identity conflicts; inspect manually';
  END IF;
  INSERT INTO store (id,tenant_id,code,name,status)
    SELECT 1,1,'legacy-single-store','默认门店',1 WHERE NOT EXISTS (SELECT 1 FROM store WHERE id=1);
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND ((table_name IN ('employee','category','dish','setmeal','orders','shopping_cart') AND column_name='tenant_id')
        OR (table_name IN ('orders','shopping_cart') AND column_name='store_id'))
      AND (column_type <> 'bigint' OR is_nullable <> 'NO' OR NOT (column_default <=> '1'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing ownership column is incompatible';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='employee' AND column_name='tenant_id') THEN
    ALTER TABLE employee ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='category' AND column_name='tenant_id') THEN
    ALTER TABLE category ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='dish' AND column_name='tenant_id') THEN
    ALTER TABLE dish ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='setmeal' AND column_name='tenant_id') THEN
    ALTER TABLE setmeal ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='tenant_id') THEN
    ALTER TABLE orders ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='store_id') THEN
    ALTER TABLE orders ADD COLUMN store_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shopping_cart' AND column_name='tenant_id') THEN
    ALTER TABLE shopping_cart ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shopping_cart' AND column_name='store_id') THEN
    ALTER TABLE shopping_cart ADD COLUMN store_id bigint NOT NULL DEFAULT 1;
  END IF;
END$$
CALL `_junzhou_20261029_tenant_store`()$$
DROP PROCEDURE `_junzhou_20261029_tenant_store`$$
DELIMITER ;
