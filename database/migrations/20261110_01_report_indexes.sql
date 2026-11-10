-- Stage 6: repeatable report indexes. Apply after 5C with backup and writers stopped.
DROP PROCEDURE IF EXISTS _junzhou_stage6;
DELIMITER $$
CREATE PROCEDURE _junzhou_stage6()
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='ix_report_order') THEN
  ALTER TABLE orders ADD INDEX ix_report_order(tenant_id,store_id,order_time);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='ix_report_paid') THEN
  ALTER TABLE orders ADD INDEX ix_report_paid(tenant_id,store_id,checkout_time);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='ix_report_completed') THEN
  ALTER TABLE orders ADD INDEX ix_report_completed(tenant_id,store_id,delivery_time);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='ix_report_cancelled') THEN
  ALTER TABLE orders ADD INDEX ix_report_cancelled(tenant_id,store_id,cancel_time);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='refund_request' AND index_name='ix_report_refund') THEN
  ALTER TABLE refund_request ADD INDEX ix_report_refund(status,updated_at,order_id);
 END IF;
END$$
DELIMITER ;
CALL _junzhou_stage6();
DROP PROCEDURE _junzhou_stage6;
