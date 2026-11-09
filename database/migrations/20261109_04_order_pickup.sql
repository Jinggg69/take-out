-- Stage 5B. Stop writers; apply after stage 5A. Re-runnable.
DROP PROCEDURE IF EXISTS _junzhou_stage5b;
DELIMITER $$
CREATE PROCEDURE _junzhou_stage5b()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='pickup_credential') THEN
    ALTER TABLE orders ADD pickup_number VARCHAR(32) NULL,
      ADD pickup_credential VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
      ADD pickup_verified_at DATETIME NULL, ADD pickup_verified_by BIGINT NULL;
  END IF;
  UPDATE orders SET pickup_number=CAST(id AS CHAR),pickup_credential=LOWER(HEX(RANDOM_BYTES(16)))
    WHERE fulfillment_type='PICKUP' AND pickup_credential IS NULL;
  INSERT INTO rbac_permission(tenant_id,code,name)
    SELECT id,'order:pickup:ready','自取制作完成' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:pickup:ready');
  INSERT INTO rbac_permission(tenant_id,code,name)
    SELECT id,'order:pickup:verify','核销自取订单' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:pickup:verify');
  -- Existing roles receive no new action implicitly. Tenant administrators grant per role/store.
END$$
CALL _junzhou_stage5b()$$
DROP PROCEDURE _junzhou_stage5b$$
DELIMITER ;
