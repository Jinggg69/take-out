-- Stages 4.4 and 4.5. Apply after store menu/cart migrations.
CREATE TABLE IF NOT EXISTS store_settings (
  tenant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  address VARCHAR(255) NOT NULL DEFAULT '',
  latitude DECIMAL(9,6) NULL,
  longitude DECIMAL(9,6) NULL,
  opens_at VARCHAR(5) NOT NULL DEFAULT '00:00',
  closes_at VARCHAR(5) NOT NULL DEFAULT '00:00',
  delivery_radius_meters INT NOT NULL DEFAULT 5000,
  minimum_order_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  pack_fee_per_item DECIMAL(10,2) NOT NULL DEFAULT 1,
  delivery_fee DECIMAL(10,2) NOT NULL DEFAULT 6,
  free_delivery_threshold DECIMAL(10,2) NULL,
  PRIMARY KEY(tenant_id,store_id),
  CONSTRAINT fk_store_settings_store FOREIGN KEY(tenant_id,store_id) REFERENCES store(tenant_id,id) ON DELETE CASCADE,
  CHECK (version >= 0),
  CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)),
  CHECK (opens_at REGEXP '^([01][0-9]|2[0-3]):[0-5][0-9]$' AND closes_at REGEXP '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
  CHECK (delivery_radius_meters BETWEEN 1 AND 100000),
  CHECK (minimum_order_amount >= 0 AND pack_fee_per_item >= 0 AND delivery_fee >= 0 AND (free_delivery_threshold IS NULL OR free_delivery_threshold >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO store_settings(tenant_id,store_id)
SELECT s.tenant_id,s.id FROM store s WHERE NOT EXISTS(
  SELECT 1 FROM store_settings c WHERE c.tenant_id=s.tenant_id AND c.store_id=s.id);
DROP PROCEDURE IF EXISTS _junzhou_order_store_snapshot;
DELIMITER $$
CREATE PROCEDURE _junzhou_order_store_snapshot()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='store_snapshot') THEN
    ALTER TABLE orders ADD store_snapshot TEXT NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='pricing_snapshot') THEN
    ALTER TABLE orders ADD pricing_snapshot TEXT NULL;
  END IF;
END$$
CALL _junzhou_order_store_snapshot()$$
DROP PROCEDURE _junzhou_order_store_snapshot$$
DELIMITER ;
