-- Stop all writers. Requires tenant/store and order-payment lifecycle migrations.
-- In THIS connection set @legacy_payment_mchid and @legacy_payment_appid to the
-- original production merchant/app identity before sourcing this file. No secrets.
DELIMITER $$
DROP PROCEDURE IF EXISTS _junzhou_payment_account$$
CREATE PROCEDURE _junzhou_payment_account()
BEGIN
  IF DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the application database';
  END IF;
  IF @legacy_payment_mchid IS NULL OR CHAR_LENGTH(TRIM(@legacy_payment_mchid))=0
     OR CHAR_LENGTH(@legacy_payment_mchid)>64 OR @legacy_payment_appid IS NULL
     OR CHAR_LENGTH(TRIM(@legacy_payment_appid))=0 OR CHAR_LENGTH(@legacy_payment_appid)>64 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Supply original merchant/app identity explicitly';
  END IF;
  CREATE TABLE IF NOT EXISTS payment_account (
    id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    tenant_id bigint NOT NULL,
    store_id bigint NOT NULL,
    mchid varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    appid varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE KEY uk_payment_account_owner(id,tenant_id,store_id),
    CONSTRAINT fk_payment_account_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_payment_account_store FOREIGN KEY(store_id) REFERENCES store(id)
  ) ENGINE=InnoDB;
  CREATE TABLE IF NOT EXISTS store_payment_account (
    tenant_id bigint NOT NULL,
    store_id bigint NOT NULL,
    payment_account_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY(tenant_id,store_id),
    CONSTRAINT fk_store_payment_account FOREIGN KEY(payment_account_id,tenant_id,store_id)
      REFERENCES payment_account(id,tenant_id,store_id)
  ) ENGINE=InnoDB;
  IF EXISTS(SELECT 1 FROM payment_account WHERE id='legacy' AND
      (tenant_id<>1 OR store_id<>1 OR mchid<>@legacy_payment_mchid OR appid<>@legacy_payment_appid)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Original payment identity conflicts; do not overwrite';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM store WHERE id=1 AND tenant_id=1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Default store ownership is missing';
  END IF;
  INSERT INTO payment_account(id,tenant_id,store_id,mchid,appid)
    SELECT 'legacy',1,1,@legacy_payment_mchid,@legacy_payment_appid
    WHERE NOT EXISTS(SELECT 1 FROM payment_account WHERE id='legacy');
  INSERT INTO store_payment_account(tenant_id,store_id,payment_account_id)
    SELECT 1,1,'legacy' WHERE NOT EXISTS(SELECT 1 FROM store_payment_account WHERE tenant_id=1 AND store_id=1);
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='orders' AND column_name='payment_account_id') THEN
    ALTER TABLE orders ADD payment_account_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL;
  END IF;
  -- Only known historical default-store orders can be assigned automatically.
  UPDATE orders SET payment_account_id='legacy' WHERE payment_account_id IS NULL AND tenant_id=1 AND store_id=1;
  IF EXISTS(SELECT 1 FROM orders o LEFT JOIN payment_account a ON a.id=o.payment_account_id
      AND a.tenant_id=o.tenant_id AND a.store_id=o.store_id WHERE a.id IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unresolved historical payment ownership; map manually before retry';
  END IF;
  ALTER TABLE orders MODIFY payment_account_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
      AND table_name='orders' AND constraint_name='fk_order_payment_account') THEN
    ALTER TABLE orders ADD CONSTRAINT fk_order_payment_account FOREIGN KEY(payment_account_id,tenant_id,store_id)
      REFERENCES payment_account(id,tenant_id,store_id);
  END IF;
END$$
CALL _junzhou_payment_account()$$
DROP PROCEDURE _junzhou_payment_account$$
DROP TRIGGER IF EXISTS payment_account_identity_immutable$$
CREATE TRIGGER payment_account_identity_immutable BEFORE UPDATE ON payment_account FOR EACH ROW
BEGIN
  IF NOT(OLD.id <=> NEW.id) OR NOT(OLD.tenant_id <=> NEW.tenant_id) OR NOT(OLD.store_id <=> NEW.store_id)
      OR NOT(OLD.mchid <=> NEW.mchid) OR NOT(OLD.appid <=> NEW.appid) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Create a new payment account instead of replacing its identity';
  END IF;
END$$
DROP TRIGGER IF EXISTS order_payment_account_immutable$$
CREATE TRIGGER order_payment_account_immutable BEFORE UPDATE ON orders FOR EACH ROW
BEGIN
  IF NOT(OLD.payment_account_id <=> NEW.payment_account_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An order must retain its original payment account';
  END IF;
END$$
DELIMITER ;
