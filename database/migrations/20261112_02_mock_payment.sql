-- Apply after payment-account and order lifecycle migrations, with writers stopped.
-- Historical orders stay WECHAT. This migration does not modify merchant identity or store mappings.
DROP PROCEDURE IF EXISTS _junzhou_mock_payment;
DELIMITER $$
CREATE PROCEDURE _junzhou_mock_payment()
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='payment_channel') THEN
  ALTER TABLE orders ADD COLUMN payment_channel varchar(16) NOT NULL DEFAULT 'WECHAT';
 END IF;
 IF EXISTS(SELECT 1 FROM orders WHERE payment_channel NOT IN ('WECHAT','MOCK') OR payment_channel IS NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid existing payment channel; investigate before migration';
 END IF;
 CREATE TABLE IF NOT EXISTS mock_payment (
  order_id bigint NOT NULL PRIMARY KEY,
  state varchar(16) NOT NULL,
  amount_fen bigint NOT NULL,
  payer_openid varchar(128) NOT NULL,
  paid_at datetime(6) DEFAULT NULL,
  out_refund_no varchar(64) DEFAULT NULL,
  CONSTRAINT fk_mock_payment_order FOREIGN KEY(order_id) REFERENCES orders(id),
  CONSTRAINT ck_mock_payment_amount CHECK(amount_fen>0),
  CONSTRAINT ck_mock_payment_state CHECK(state IN ('NOTPAY','SUCCESS','CLOSED','REFUND'))
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
END$$
DELIMITER ;
CALL _junzhou_mock_payment();
DROP PROCEDURE _junzhou_mock_payment;
DROP TRIGGER IF EXISTS orders_payment_channel_immutable;
DROP TRIGGER IF EXISTS orders_payment_channel_validate;
DELIMITER $$
CREATE TRIGGER orders_payment_channel_immutable BEFORE UPDATE ON orders FOR EACH ROW
BEGIN
 IF NOT (NEW.payment_channel <=> OLD.payment_channel) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Order payment channel is immutable';
 END IF;
END$$
CREATE TRIGGER orders_payment_channel_validate BEFORE INSERT ON orders FOR EACH ROW
BEGIN
 IF NEW.payment_channel NOT IN ('WECHAT','MOCK') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unknown order payment channel';
 END IF;
END$$
DELIMITER ;
