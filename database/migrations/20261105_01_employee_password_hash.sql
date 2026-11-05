-- Stage 3.5: widen only the existing password column; preserve all credentials.
DELIMITER $$
DROP PROCEDURE IF EXISTS _junzhou_employee_password_width$$
CREATE PROCEDURE _junzhou_employee_password_width()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'employee' AND column_name = 'password'
      AND column_type IN ('varchar(64)', 'varchar(255)') AND is_nullable = 'NO'
      AND collation_name IN ('utf8_bin', 'utf8mb3_bin') AND column_default IS NULL AND extra = ''
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unexpected employee.password definition; inspect before migration';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'employee' AND column_name = 'password' AND column_type = 'varchar(64)') THEN
    ALTER TABLE employee MODIFY COLUMN password varchar(255) COLLATE utf8_bin NOT NULL COMMENT '密码';
  END IF;
END$$
CALL _junzhou_employee_password_width()$$
DROP PROCEDURE _junzhou_employee_password_width$$
DELIMITER ;
