-- Stage 5C; apply after 5B with writers stopped and a backup.
DROP PROCEDURE IF EXISTS _junzhou_stage5c;
DELIMITER $$
CREATE PROCEDURE _junzhou_stage5c()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='store_dining_table' AND column_name='qr_token') THEN
    ALTER TABLE store_dining_table ADD qr_token VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
      ADD current_session_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
      ADD UNIQUE KEY uk_dining_token(qr_token), ADD UNIQUE KEY uk_dining_scope(tenant_id,store_id,id);
  END IF;
  UPDATE store_dining_table SET qr_token=LOWER(HEX(RANDOM_BYTES(16))) WHERE qr_token IS NULL;
  CREATE TABLE IF NOT EXISTS dining_session(
    id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tenant_id BIGINT NOT NULL,store_id BIGINT NOT NULL,table_id BIGINT NOT NULL,
    table_snapshot VARCHAR(64) NOT NULL,opened_at DATETIME NOT NULL,closed_at DATETIME NULL,
    PRIMARY KEY(id), UNIQUE KEY uk_session_scope(tenant_id,store_id,table_id,id),
    CONSTRAINT fk_session_table FOREIGN KEY(tenant_id,store_id,table_id) REFERENCES store_dining_table(tenant_id,store_id,id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  CREATE TABLE IF NOT EXISTS dining_session_member(
    session_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,user_id BIGINT NOT NULL,
    PRIMARY KEY(session_id,user_id),
    CONSTRAINT fk_member_session FOREIGN KEY(session_id) REFERENCES dining_session(id),
    CONSTRAINT fk_member_user FOREIGN KEY(user_id) REFERENCES `user`(id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='dining_session_id') THEN
    ALTER TABLE orders ADD dining_session_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
      ADD KEY ix_orders_session(tenant_id,store_id,dining_session_id,status),
      ADD CONSTRAINT fk_order_dining_session FOREIGN KEY(tenant_id,store_id,table_id,dining_session_id) REFERENCES dining_session(tenant_id,store_id,table_id,id);
  END IF;
  INSERT INTO rbac_permission(tenant_id,code,name)
    SELECT t.id,c.code,c.name FROM tenant t CROSS JOIN (
      SELECT 'dining:read' code,'查看桌台' name UNION ALL SELECT 'dining:manage','管理桌台与桌码'
      UNION ALL SELECT 'dining:close','结束桌次' UNION ALL SELECT 'order:dine:complete','完成堂食订单'
    ) c WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code=c.code);
END$$
CALL _junzhou_stage5c()$$
DROP PROCEDURE _junzhou_stage5c$$
DELIMITER ;
