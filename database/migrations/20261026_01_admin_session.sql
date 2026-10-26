-- Additive migration. Select the existing application database before execution.
-- Existing employee/business rows are untouched. Old JWTs need a fresh login.
CREATE TABLE IF NOT EXISTS `admin_session` (
  `session_id` varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `employee_id` bigint NOT NULL,
  `expires_at` bigint NOT NULL COMMENT 'JWT expiry in Unix epoch milliseconds',
  PRIMARY KEY (`session_id`),
  KEY `idx_admin_session_employee_expiry` (`employee_id`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
