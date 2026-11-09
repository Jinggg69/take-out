-- Stage 5A: independent fulfillment contract. Stop all writers before upgrading.
CREATE TABLE IF NOT EXISTS store_dining_table (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY(id),
  UNIQUE KEY uk_dining_table_name(tenant_id,store_id,name),
  CONSTRAINT fk_dining_table_store FOREIGN KEY(tenant_id,store_id) REFERENCES store(tenant_id,id),
  CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
DROP PROCEDURE IF EXISTS _junzhou_stage5a;
DELIMITER $$
CREATE PROCEDURE _junzhou_stage5a()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='fulfillment_type') THEN
    ALTER TABLE orders ADD fulfillment_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DELIVERY';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='fulfillment_status') THEN
    ALTER TABLE orders ADD fulfillment_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='contact_name') THEN
    ALTER TABLE orders ADD contact_name VARCHAR(32) CHARACTER SET utf8mb4 NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='contact_phone') THEN
    ALTER TABLE orders ADD contact_phone VARCHAR(11) NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='table_id') THEN
    ALTER TABLE orders ADD table_id BIGINT NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='table_snapshot') THEN
    ALTER TABLE orders ADD table_snapshot VARCHAR(64) CHARACTER SET utf8mb4 NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='cart_fingerprint') THEN
    ALTER TABLE orders ADD cart_fingerprint VARCHAR(64) NULL;
  END IF;
  ALTER TABLE orders MODIFY address_book_id BIGINT NULL COMMENT 'Delivery address only';
  UPDATE orders SET fulfillment_status=CASE status WHEN 1 THEN 'AWAITING_PAYMENT' WHEN 2 THEN 'AWAITING_ACCEPTANCE' WHEN 3 THEN 'PREPARING' WHEN 4 THEN 'OUT_FOR_DELIVERY' WHEN 5 THEN 'COMPLETED' WHEN 6 THEN 'CANCELLED' ELSE 'CANCELLED' END WHERE fulfillment_status IS NULL;
  ALTER TABLE orders MODIFY fulfillment_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'AWAITING_PAYMENT';
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='orders' AND constraint_name='ck_orders_fulfillment_type') THEN
    ALTER TABLE orders ADD CONSTRAINT ck_orders_fulfillment_type CHECK(fulfillment_type IN ('DELIVERY','PICKUP','DINE_IN'));
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='orders' AND constraint_name='ck_orders_fulfillment_status') THEN
    ALTER TABLE orders ADD CONSTRAINT ck_orders_fulfillment_status CHECK(fulfillment_status IN ('AWAITING_PAYMENT','AWAITING_ACCEPTANCE','PREPARING','READY','OUT_FOR_DELIVERY','COMPLETED','CANCELLED'));
  END IF;
END$$
CALL _junzhou_stage5a()$$
DROP PROCEDURE _junzhou_stage5a$$
DELIMITER ;
