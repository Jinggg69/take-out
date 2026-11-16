-- Apply after 20261109_02_store_fulfillment.sql, before deploying discovery APIs.
-- Nullable public metadata only; no tenant/store ownership or existing values change.
DROP PROCEDURE IF EXISTS _junzhou_20261112_discovery;
DELIMITER $$
CREATE PROCEDURE _junzhou_20261112_discovery()
BEGIN
  IF DATABASE() IS NULL OR DATABASE() IN ('mysql','information_schema','performance_schema','sys') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the intended application database';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='tenant' AND engine='InnoDB')
    OR NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='store_settings' AND engine='InnoDB') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Required tenant/store_settings InnoDB tables are missing';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tenant' AND column_name='logo_url') THEN
    ALTER TABLE tenant ADD logo_url VARCHAR(512) NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tenant' AND column_name='introduction') THEN
    ALTER TABLE tenant ADD introduction VARCHAR(500) NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='store_settings' AND column_name='contact_phone') THEN
    ALTER TABLE store_settings ADD contact_phone VARCHAR(32) NULL;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND data_type='varchar' AND is_nullable='YES' AND column_default IS NULL AND
      ((table_name='tenant' AND column_name='logo_url' AND character_maximum_length=512)
      OR (table_name='tenant' AND column_name='introduction' AND character_maximum_length=500)
      OR (table_name='store_settings' AND column_name='contact_phone' AND character_maximum_length=32))) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Discovery column definitions differ; inspect before retrying';
  END IF;
END$$
CALL _junzhou_20261112_discovery()$$
DROP PROCEDURE _junzhou_20261112_discovery$$
DELIMITER ;
