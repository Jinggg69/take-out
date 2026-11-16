-- Stop order writers before applying. Existing DATETIME order_time values are local business time.
-- Legacy pickup_credential is retained as an unused nullable column for historical migration compatibility.
DROP PROCEDURE IF EXISTS _junzhou_daily_pickup;
DELIMITER $$
CREATE PROCEDURE _junzhou_daily_pickup()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS _pickup_number_backfill;
    RESIGNAL;
  END;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='pickup_date') THEN
    ALTER TABLE orders ADD pickup_date DATE NULL;
  END IF;
  CREATE TABLE IF NOT EXISTS store_pickup_sequence (
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    pickup_date DATE NOT NULL,
    last_number BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, store_id, pickup_date),
    CONSTRAINT ck_pickup_sequence_positive CHECK(last_number > 0)
  ) ENGINE=InnoDB;

  DROP TEMPORARY TABLE IF EXISTS _pickup_number_backfill;
  CREATE TEMPORARY TABLE _pickup_number_backfill AS
    SELECT o.id, COALESCE(DATE(o.order_time),'1970-01-01') AS pickup_date,
      COALESCE(existing.last_number,0) + ROW_NUMBER() OVER (
        PARTITION BY o.tenant_id,o.store_id,COALESCE(DATE(o.order_time),'1970-01-01')
        ORDER BY o.order_time,o.id
      ) AS pickup_number
    FROM orders o
    LEFT JOIN (
      SELECT tenant_id,store_id,pickup_date,MAX(CAST(pickup_number AS UNSIGNED)) AS last_number
      FROM orders WHERE fulfillment_type='PICKUP' AND pickup_date IS NOT NULL
      GROUP BY tenant_id,store_id,pickup_date
    ) existing ON existing.tenant_id=o.tenant_id AND existing.store_id=o.store_id
      AND existing.pickup_date=COALESCE(DATE(o.order_time),'1970-01-01')
    WHERE o.fulfillment_type='PICKUP' AND o.pickup_date IS NULL;
  START TRANSACTION;
  UPDATE orders o JOIN _pickup_number_backfill b ON b.id=o.id
    SET o.pickup_number=CAST(b.pickup_number AS CHAR),o.pickup_date=b.pickup_date;
  INSERT INTO store_pickup_sequence(tenant_id,store_id,pickup_date,last_number)
    SELECT tenant_id,store_id,pickup_date,MAX(CAST(pickup_number AS UNSIGNED))
    FROM orders WHERE fulfillment_type='PICKUP' AND pickup_date IS NOT NULL
    GROUP BY tenant_id,store_id,pickup_date
    ON DUPLICATE KEY UPDATE last_number=GREATEST(store_pickup_sequence.last_number,VALUES(last_number));
  COMMIT;
  DROP TEMPORARY TABLE _pickup_number_backfill;
  IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='uk_order_pickup_number') THEN
    CREATE UNIQUE INDEX uk_order_pickup_number ON orders(tenant_id,store_id,pickup_date,pickup_number);
  END IF;
END$$
CALL _junzhou_daily_pickup()$$
DROP PROCEDURE _junzhou_daily_pickup$$
DELIMITER ;
