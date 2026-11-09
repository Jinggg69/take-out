-- Stage 4.3. Pause cart writes during the upgrade; review duplicate groups before merging.
DROP PROCEDURE IF EXISTS _junzhou_cart_identity;
DELIMITER $$
CREATE PROCEDURE _junzhou_cart_identity()
main: BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS cart_identity_review;
    RESIGNAL;
  END;
  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
             AND table_name='shopping_cart' AND index_name='uk_cart_identity' AND non_unique=0
             GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)=
                 'tenant_id,store_id,user_id,product_type,product_id,flavor_key') THEN
    LEAVE main;
  END IF;
  IF EXISTS (SELECT 1 FROM shopping_cart WHERE (dish_id IS NULL) = (setmeal_id IS NULL)
             OR COALESCE(dish_id,setmeal_id) <= 0 OR number <= 0) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid cart product or quantity; review source rows';
  END IF;

  -- Match Java String.trim(): only strip leading/trailing U+0000 through U+0020.
  CREATE TEMPORARY TABLE cart_identity_review AS
    SELECT id, flavor, MIN(id) OVER sku AS keep_id, SUM(number) OVER sku AS total
    FROM (SELECT id, tenant_id, store_id, user_id, dish_id, setmeal_id, number,
          IF(setmeal_id IS NULL, REGEXP_REPLACE(COALESCE(dish_flavor,''),
             CONCAT('^[',CHAR(0 USING utf8mb3),'-',CHAR(32 USING utf8mb3),']+|[',CHAR(0 USING utf8mb3),'-',CHAR(32 USING utf8mb3),']+\\z'), ''), '') AS flavor
          FROM shopping_cart) normalized
    WINDOW sku AS (PARTITION BY tenant_id,store_id,user_id,dish_id,setmeal_id,CAST(flavor AS BINARY));
  IF EXISTS (SELECT 1 FROM cart_identity_review WHERE id <> keep_id) THEN
    SELECT c.tenant_id,c.store_id,c.user_id,c.dish_id,c.setmeal_id,HEX(r.flavor) AS flavor_hex,
           c.id,c.number,r.keep_id,r.total
      FROM shopping_cart c JOIN cart_identity_review r ON c.id=r.id
      WHERE r.total > c.number ORDER BY r.keep_id,c.id;
    IF COALESCE(@cart_duplicates_reviewed,0) <> 1 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Review listed cart duplicates before enabling merge';
    END IF;
  END IF;
  IF EXISTS (SELECT 1 FROM cart_identity_review WHERE total > 2147483647) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Merged cart quantity exceeds INT range';
  END IF;
  START TRANSACTION;
  DELETE c FROM shopping_cart c JOIN cart_identity_review r ON r.id=c.id WHERE r.id<>r.keep_id;
  UPDATE shopping_cart c JOIN cart_identity_review r ON r.id=c.id
    SET c.number=r.total,c.dish_flavor=r.flavor;
  COMMIT;
  DROP TEMPORARY TABLE cart_identity_review;
  ALTER TABLE shopping_cart
    MODIFY dish_flavor VARCHAR(50) CHARACTER SET utf8mb3 COLLATE utf8_bin NOT NULL DEFAULT '',
    ADD product_type TINYINT GENERATED ALWAYS AS (dish_id IS NULL) STORED,
    ADD product_id BIGINT GENERATED ALWAYS AS (COALESCE(dish_id,setmeal_id)) STORED,
    ADD flavor_key VARBINARY(200) GENERATED ALWAYS AS (CAST(dish_flavor AS BINARY)) STORED,
    ADD CONSTRAINT ck_cart_product CHECK ((dish_id IS NULL) <> (setmeal_id IS NULL) AND product_id > 0),
    ADD CONSTRAINT ck_cart_quantity CHECK (number > 0),
    ADD CONSTRAINT ck_cart_flavor CHECK (OCTET_LENGTH(dish_flavor)=0 OR
      (setmeal_id IS NULL AND ASCII(LEFT(dish_flavor,1))>32 AND ASCII(RIGHT(dish_flavor,1))>32)),
    ADD UNIQUE KEY uk_cart_identity (tenant_id,store_id,user_id,product_type,product_id,flavor_key);
END$$
CALL _junzhou_cart_identity()$$
DROP PROCEDURE _junzhou_cart_identity$$
DELIMITER ;
