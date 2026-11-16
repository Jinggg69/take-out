-- Platform lifecycle metadata only. Preserve all tenant/store/order/payment identities.
-- Stop writers and back up before applying; MySQL DDL commits implicitly.
DELIMITER $$
DROP PROCEDURE IF EXISTS _junzhou_platform_management$$
CREATE PROCEDURE _junzhou_platform_management()
BEGIN
  IF DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the application database';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tenant' AND column_name='deleted_at') THEN
    ALTER TABLE tenant ADD COLUMN deleted_at datetime NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='store' AND column_name='deleted_at') THEN
    ALTER TABLE store ADD COLUMN deleted_at datetime NULL;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('tenant','store') AND column_name='deleted_at' AND data_type='datetime' AND is_nullable='YES') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible tenant/store deleted_at columns';
  END IF;
  CREATE TABLE IF NOT EXISTS platform_audit (
    id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_id bigint NOT NULL,
    action varchar(64) NOT NULL,
    target_type varchar(16) NOT NULL,
    target_id bigint NOT NULL,
    created_at datetime NOT NULL,
    KEY idx_platform_audit_admin(admin_id,created_at),
    KEY idx_platform_audit_target(target_type,target_id,created_at)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
END$$
CALL _junzhou_platform_management()$$
DROP PROCEDURE _junzhou_platform_management$$
DELIMITER ;
