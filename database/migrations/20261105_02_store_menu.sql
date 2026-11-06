-- Stage 4.1. Run after stage 3 migrations with catalog writes paused.
DROP PROCEDURE IF EXISTS _junzhou_store_menu;
DELIMITER $$
CREATE PROCEDURE _junzhou_store_menu()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='dish' AND index_name='uk_dish_tenant_id') THEN
    ALTER TABLE dish ADD UNIQUE KEY uk_dish_tenant_id (tenant_id,id);
  END IF;

  CREATE TABLE IF NOT EXISTS store_dish (
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    dish_id BIGINT NOT NULL,
    on_sale TINYINT NOT NULL DEFAULT 0,
    sold_out TINYINT NOT NULL DEFAULT 0,
    override_price DECIMAL(10,2) DEFAULT NULL,
    sort INT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id,store_id,dish_id),
    CONSTRAINT fk_store_dish_store FOREIGN KEY (tenant_id,store_id)
      REFERENCES store (tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_store_dish_product FOREIGN KEY (tenant_id,dish_id)
      REFERENCES dish (tenant_id,id) ON DELETE CASCADE,
    CHECK (on_sale IN (0,1)),
    CHECK (sold_out IN (0,1)),
    CHECK (override_price IS NULL OR override_price >= 0)
  ) ENGINE=InnoDB;

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='setmeal' AND index_name='uk_setmeal_tenant_id') THEN
    ALTER TABLE setmeal ADD UNIQUE KEY uk_setmeal_tenant_id (tenant_id,id);
  END IF;

  CREATE TABLE IF NOT EXISTS store_setmeal (
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    setmeal_id BIGINT NOT NULL,
    on_sale TINYINT NOT NULL DEFAULT 0,
    sold_out TINYINT NOT NULL DEFAULT 0,
    override_price DECIMAL(10,2) DEFAULT NULL,
    sort INT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id,store_id,setmeal_id),
    CONSTRAINT fk_store_setmeal_store FOREIGN KEY (tenant_id,store_id)
      REFERENCES store (tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_store_setmeal_product FOREIGN KEY (tenant_id,setmeal_id)
      REFERENCES setmeal (tenant_id,id) ON DELETE CASCADE,
    CHECK (on_sale IN (0,1)),
    CHECK (sold_out IN (0,1)),
    CHECK (override_price IS NULL OR override_price >= 0)
  ) ENGINE=InnoDB;

  START TRANSACTION;
  INSERT INTO store_dish (tenant_id,store_id,dish_id,on_sale)
    SELECT p.tenant_id,st.id,p.id,1 FROM dish p
    JOIN store st ON st.id=1 AND st.tenant_id=p.tenant_id
    WHERE p.tenant_id=1 AND NOT EXISTS (
      SELECT 1 FROM store_dish m
      WHERE m.tenant_id=p.tenant_id AND m.store_id=st.id AND m.dish_id=p.id);
  INSERT INTO store_setmeal (tenant_id,store_id,setmeal_id,on_sale)
    SELECT p.tenant_id,st.id,p.id,1 FROM setmeal p
    JOIN store st ON st.id=1 AND st.tenant_id=p.tenant_id
    WHERE p.tenant_id=1 AND NOT EXISTS (
      SELECT 1 FROM store_setmeal m
      WHERE m.tenant_id=p.tenant_id AND m.store_id=st.id AND m.setmeal_id=p.id);
  COMMIT;
END$$
CALL _junzhou_store_menu()$$
DROP PROCEDURE _junzhou_store_menu$$
DELIMITER ;
