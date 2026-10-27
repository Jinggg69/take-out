-- MySQL 8.0/8.4. Select the existing application database first.
-- Stop all application writers/workers and take a backup before running.
-- DDL implicitly commits: only the final legacy backfill is transactional.
-- Never use --force: a failed preflight must stop this deployment.
-- No business table is dropped; these procedure names belong to this migration.
SELECT DATABASE() AS migration_target_database;

DELIMITER $$
DROP PROCEDURE IF EXISTS `_sky_20261027_lifecycle_index`$$
CREATE PROCEDURE `_sky_20261027_lifecycle_index`(
  IN p_table varchar(64), IN p_name varchar(64),
  IN p_columns varchar(255), IN p_unique tinyint, IN p_ddl text
)
BEGIN
  DECLARE v_columns text;
  DECLARE v_non_unique int;
  IF EXISTS (SELECT 1 FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_name) THEN
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','), MAX(non_unique)
      INTO v_columns, v_non_unique
      FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_name;
    IF NOT (v_columns <=> p_columns) OR v_non_unique <> (1 - p_unique)
       OR EXISTS (SELECT 1 FROM information_schema.statistics
                  WHERE table_schema = DATABASE() AND table_name = p_table
                    AND index_name = p_name AND (sub_part IS NOT NULL OR is_visible <> 'YES')) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Lifecycle index has incompatible definition; inspect schema manually';
    END IF;
  ELSE
    SET @sky_lifecycle_ddl = p_ddl;
    PREPARE sky_lifecycle_statement FROM @sky_lifecycle_ddl;
    EXECUTE sky_lifecycle_statement;
    DEALLOCATE PREPARE sky_lifecycle_statement;
  END IF;
END$$

DROP PROCEDURE IF EXISTS `_sky_20261027_lifecycle`$$
CREATE PROCEDURE `_sky_20261027_lifecycle`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF DATABASE() IS NULL OR DATABASE() IN ('mysql', 'sys', 'information_schema', 'performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the application database before running this migration';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name IN ('orders', 'user', 'shopping_cart')
        AND table_type = 'BASE TABLE' AND engine = 'InnoDB') <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'orders, user and shopping_cart must exist and use InnoDB';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND ((table_name = 'orders' AND column_name IN ('id','number','user_id','status','pay_status','amount','order_time'))
          OR (table_name IN ('user','shopping_cart') AND column_name = 'id')
          OR (table_name = 'shopping_cart' AND column_name = 'user_id'))) <> 10 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required pre-lifecycle order/user/cart columns are missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'amount'
        AND data_type = 'decimal' AND numeric_scale = 2 AND numeric_precision <= 18) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'orders.amount must be exact DECIMAL with scale 2 and precision <= 18';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
             AND table_name = 'refund_request' AND (table_type <> 'BASE TABLE' OR engine <> 'InnoDB')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing refund_request must be an InnoDB base table';
  END IF;

  -- Financial identity conflicts require investigation, never merge/delete rows.
  IF EXISTS (SELECT 1 FROM orders WHERE number IS NOT NULL GROUP BY number HAVING COUNT(*) > 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate orders.number; audit and resolve manually before migration';
  END IF;
  IF EXISTS (SELECT 1 FROM orders WHERE status = 6 AND pay_status IN (1, 2)
             AND (number IS NULL OR CHAR_LENGTH(TRIM(number)) = 0 OR amount IS NULL OR amount <= 0)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy cancelled paid/refunded order has invalid number/amount; audit manually';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
             AND table_name = 'orders' AND column_name = 'wechat_transaction_id') THEN
    IF EXISTS (SELECT 1 FROM orders WHERE wechat_transaction_id IS NOT NULL
               GROUP BY wechat_transaction_id HAVING COUNT(*) > 1) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate orders.wechat_transaction_id; audit manually';
    END IF;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
             AND table_name = 'orders' AND column_name = 'client_request_id') THEN
    IF EXISTS (SELECT 1 FROM orders WHERE client_request_id IS NOT NULL
               GROUP BY user_id, client_request_id HAVING COUNT(*) > 1) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate orders user/client request identity; audit manually';
    END IF;
  END IF;

  -- MySQL 8.4 has no ADD COLUMN IF NOT EXISTS; allow safe retries after partial DDL.
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'orders' AND column_name = 'dispatch_time') THEN
    ALTER TABLE orders ADD COLUMN dispatch_time datetime NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'orders' AND column_name = 'wechat_transaction_id') THEN
    ALTER TABLE orders ADD COLUMN wechat_transaction_id varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'orders' AND column_name = 'refund_status') THEN
    ALTER TABLE orders ADD COLUMN refund_status varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'orders' AND column_name = 'payment_check_time') THEN
    ALTER TABLE orders ADD COLUMN payment_check_time datetime NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'orders' AND column_name = 'wechat_trade_state') THEN
    ALTER TABLE orders ADD COLUMN wechat_trade_state varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'orders' AND column_name = 'client_request_id') THEN
    ALTER TABLE orders ADD COLUMN client_request_id varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'orders' AND column_name = 'submit_fingerprint') THEN
    ALTER TABLE orders ADD COLUMN submit_fingerprint varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'orders'
      AND is_nullable = 'YES' AND (
        (column_name IN ('dispatch_time','payment_check_time') AND data_type = 'datetime')
        OR (column_name IN ('wechat_transaction_id','client_request_id','submit_fingerprint') AND data_type = 'varchar' AND character_maximum_length = 64)
        OR (column_name = 'refund_status' AND data_type = 'varchar' AND character_maximum_length = 16)
        OR (column_name = 'wechat_trade_state' AND data_type = 'varchar' AND character_maximum_length = 32))) <> 7 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing orders lifecycle columns have incompatible types/nullability';
  END IF;

  CALL _sky_20261027_lifecycle_index('orders', 'uk_orders_number', 'number', 1,
    'ALTER TABLE orders ADD UNIQUE KEY uk_orders_number (number)');
  CALL _sky_20261027_lifecycle_index('orders', 'uk_orders_wechat_transaction', 'wechat_transaction_id', 1,
    'ALTER TABLE orders ADD UNIQUE KEY uk_orders_wechat_transaction (wechat_transaction_id)');
  CALL _sky_20261027_lifecycle_index('orders', 'uk_orders_user_request', 'user_id,client_request_id', 1,
    'ALTER TABLE orders ADD UNIQUE KEY uk_orders_user_request (user_id,client_request_id)');
  CALL _sky_20261027_lifecycle_index('orders', 'idx_orders_payment_check', 'status,pay_status,payment_check_time', 0,
    'ALTER TABLE orders ADD KEY idx_orders_payment_check (status,pay_status,payment_check_time)');
  CALL _sky_20261027_lifecycle_index('orders', 'idx_orders_status_order_time', 'status,order_time', 0,
    'ALTER TABLE orders ADD KEY idx_orders_status_order_time (status,order_time)');
  CALL _sky_20261027_lifecycle_index('orders', 'idx_orders_status_dispatch_time', 'status,dispatch_time', 0,
    'ALTER TABLE orders ADD KEY idx_orders_status_dispatch_time (status,dispatch_time)');
  CALL _sky_20261027_lifecycle_index('shopping_cart', 'idx_shopping_cart_user', 'user_id', 0,
    'ALTER TABLE shopping_cart ADD KEY idx_shopping_cart_user (user_id)');

  CREATE TABLE IF NOT EXISTS refund_request (
    out_refund_no varchar(64) NOT NULL,
    order_id bigint NOT NULL,
    out_trade_no varchar(50) NOT NULL,
    transaction_id varchar(64) DEFAULT NULL,
    amount_fen bigint NOT NULL,
    status varchar(16) NOT NULL,
    query_only tinyint NOT NULL DEFAULT '0',
    provider_refund_id varchar(64) DEFAULT NULL,
    attempts int NOT NULL DEFAULT '0',
    next_attempt_at datetime NOT NULL,
    lease_until datetime DEFAULT NULL,
    lease_token varchar(36) DEFAULT NULL,
    created_at datetime NOT NULL,
    updated_at datetime NOT NULL,
    last_error varchar(255) DEFAULT NULL,
    PRIMARY KEY (out_refund_no),
    UNIQUE KEY uk_refund_request_order (order_id),
    UNIQUE KEY uk_refund_request_provider (provider_refund_id),
    KEY idx_refund_request_due (status, next_attempt_at, lease_until)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

  IF (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'refund_request' AND (
        (column_name = 'out_refund_no' AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'NO')
        OR (column_name = 'order_id' AND column_type = 'bigint' AND is_nullable = 'NO')
        OR (column_name = 'out_trade_no' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'NO')
        OR (column_name IN ('transaction_id','provider_refund_id') AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'YES')
        OR (column_name = 'amount_fen' AND column_type = 'bigint' AND is_nullable = 'NO')
        OR (column_name = 'status' AND data_type = 'varchar' AND character_maximum_length = 16 AND is_nullable = 'NO')
        OR (column_name = 'query_only' AND data_type = 'tinyint' AND is_nullable = 'NO' AND column_default = '0')
        OR (column_name = 'attempts' AND data_type = 'int' AND is_nullable = 'NO' AND column_default = '0')
        OR (column_name IN ('next_attempt_at','created_at','updated_at') AND data_type = 'datetime' AND is_nullable = 'NO')
        OR (column_name = 'lease_until' AND data_type = 'datetime' AND is_nullable = 'YES')
        OR (column_name = 'lease_token' AND data_type = 'varchar' AND character_maximum_length = 36 AND is_nullable = 'YES')
        OR (column_name = 'last_error' AND data_type = 'varchar' AND character_maximum_length = 255 AND is_nullable = 'YES'))) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing refund_request has incompatible columns; inspect schema manually';
  END IF;
  CALL _sky_20261027_lifecycle_index('refund_request', 'PRIMARY', 'out_refund_no', 1,
    'ALTER TABLE refund_request ADD PRIMARY KEY (out_refund_no)');
  CALL _sky_20261027_lifecycle_index('refund_request', 'uk_refund_request_order', 'order_id', 1,
    'ALTER TABLE refund_request ADD UNIQUE KEY uk_refund_request_order (order_id)');
  CALL _sky_20261027_lifecycle_index('refund_request', 'uk_refund_request_provider', 'provider_refund_id', 1,
    'ALTER TABLE refund_request ADD UNIQUE KEY uk_refund_request_provider (provider_refund_id)');
  CALL _sky_20261027_lifecycle_index('refund_request', 'idx_refund_request_due', 'status,next_attempt_at,lease_until', 0,
    'ALTER TABLE refund_request ADD KEY idx_refund_request_due (status,next_attempt_at,lease_until)');

  -- Only this section is atomic. No DDL is allowed between START and COMMIT.
  START TRANSACTION;
  INSERT INTO refund_request
    (out_refund_no, order_id, out_trade_no, transaction_id, amount_fen, status,
     query_only, attempts, next_attempt_at, created_at, updated_at)
  SELECT o.number, o.id, o.number, o.wechat_transaction_id, CAST(o.amount * 100 AS SIGNED),
         CASE WHEN o.pay_status = 2 THEN 'VERIFYING' ELSE 'REQUESTED' END,
         CASE WHEN o.pay_status = 2 THEN 1 ELSE 0 END,
         CASE WHEN o.pay_status = 2 THEN 0 ELSE 1 END, NOW(), NOW(), NOW()
  FROM orders o
  WHERE o.status = 6 AND o.pay_status IN (1, 2) AND o.amount > 0
    AND o.number IS NOT NULL AND CHAR_LENGTH(TRIM(o.number)) > 0
    AND NOT EXISTS (SELECT 1 FROM refund_request r WHERE r.order_id = o.id);

  -- Preserve existing request state, non-null order state and all pay_status values.
  UPDATE orders o JOIN refund_request r ON r.order_id = o.id
  SET o.refund_status = r.status
  WHERE o.status = 6 AND o.pay_status IN (1, 2) AND o.refund_status IS NULL
    AND r.status IN ('VERIFYING', 'REQUESTED');
  COMMIT;
END$$

CALL `_sky_20261027_lifecycle`()$$
DROP PROCEDURE `_sky_20261027_lifecycle`$$
DROP PROCEDURE `_sky_20261027_lifecycle_index`$$
DELIMITER ;

-- Audit only: old pay_status=2 is not evidence of provider-confirmed refund.
SELECT o.id, o.number, o.pay_status, o.refund_status, r.status, r.query_only, r.attempts
FROM orders o LEFT JOIN refund_request r ON r.order_id = o.id
WHERE o.status = 6 AND o.pay_status IN (1, 2)
ORDER BY o.id;
