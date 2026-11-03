-- Stage 2.7: stop all writers and back up first. Requires stage 2.1 schema.
-- All preflight checks run before the first ALTER. MySQL DDL commits implicitly;
-- after a partial failure inspect the error and rerun with writers still stopped.
DELIMITER $$
DROP PROCEDURE IF EXISTS _junzhou_explicit_ownership$$
CREATE PROCEDURE _junzhou_explicit_ownership()
BEGIN
  IF DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the application database';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('employee','category','dish','setmeal','orders','shopping_cart','tenant','store')
      AND table_type='BASE TABLE' AND engine='InnoDB') <> 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Required ownership tables must be InnoDB base tables';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND ((table_name IN ('employee','category','dish','setmeal','orders','shopping_cart') AND column_name='tenant_id')
        OR (table_name IN ('orders','shopping_cart') AND column_name='store_id'))
      AND column_type='bigint' AND is_nullable='NO' AND extra=''
      AND (column_default IS NULL OR column_default='1')) <> 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected eight NOT NULL bigint ownership columns with default 1 or no default';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM tenant WHERE id=1)
     OR NOT EXISTS(SELECT 1 FROM store WHERE id=1 AND tenant_id=1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical default tenant/store must be retained';
  END IF;
  IF EXISTS(SELECT 1 FROM employee e LEFT JOIN tenant t ON t.id=e.tenant_id WHERE t.id IS NULL)
     OR EXISTS(SELECT 1 FROM category e LEFT JOIN tenant t ON t.id=e.tenant_id WHERE t.id IS NULL)
     OR EXISTS(SELECT 1 FROM dish e LEFT JOIN tenant t ON t.id=e.tenant_id WHERE t.id IS NULL)
     OR EXISTS(SELECT 1 FROM setmeal e LEFT JOIN tenant t ON t.id=e.tenant_id WHERE t.id IS NULL)
     OR EXISTS(SELECT 1 FROM orders e LEFT JOIN store s ON s.id=e.store_id AND s.tenant_id=e.tenant_id
          LEFT JOIN tenant t ON t.id=e.tenant_id WHERE s.id IS NULL OR t.id IS NULL)
     OR EXISTS(SELECT 1 FROM shopping_cart e LEFT JOIN store s ON s.id=e.store_id AND s.tenant_id=e.tenant_id
          LEFT JOIN tenant t ON t.id=e.tenant_id WHERE s.id IS NULL OR t.id IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid historical ownership; inspect without rewriting business data';
  END IF;
  ALTER TABLE employee ALTER COLUMN tenant_id DROP DEFAULT;
  ALTER TABLE category ALTER COLUMN tenant_id DROP DEFAULT;
  ALTER TABLE dish ALTER COLUMN tenant_id DROP DEFAULT;
  ALTER TABLE setmeal ALTER COLUMN tenant_id DROP DEFAULT;
  ALTER TABLE orders ALTER COLUMN tenant_id DROP DEFAULT, ALTER COLUMN store_id DROP DEFAULT;
  ALTER TABLE shopping_cart ALTER COLUMN tenant_id DROP DEFAULT, ALTER COLUMN store_id DROP DEFAULT;
END$$
CALL _junzhou_explicit_ownership()$$
DROP PROCEDURE _junzhou_explicit_ownership$$
DELIMITER ;
