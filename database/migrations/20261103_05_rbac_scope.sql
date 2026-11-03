-- Stage 3.2. Requires stage 3.1. Stop writers and back up before migration.
-- DDL auto-commits. Scope defaults narrow existing assignments, never infer broader grants.
DELIMITER $$
DROP PROCEDURE IF EXISTS _junzhou_rbac_scope$$
CREATE PROCEDURE _junzhou_rbac_scope()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the application database';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('employee_role','rbac_permission','role_permission','rbac_role','employee_store')
      AND engine='InnoDB' AND table_type='BASE TABLE') <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Requires stage 3.1 RBAC tables';
  END IF;
  IF EXISTS(SELECT 1 FROM rbac_permission WHERE NOT REGEXP_LIKE(code,'^[a-z]+:[a-z]+$','c')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid existing permission action; inspect manually';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='employee_role' AND column_name='data_scope') THEN
    ALTER TABLE employee_role ADD COLUMN data_scope varchar(32) CHARACTER SET ascii COLLATE ascii_bin
      NOT NULL DEFAULT 'CURRENT_STORE';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='employee_role' AND column_name='data_scope' AND column_type='varchar(32)'
      AND collation_name='ascii_bin' AND is_nullable='NO' AND column_default='CURRENT_STORE')
     OR EXISTS(SELECT 1 FROM employee_role WHERE NOT REGEXP_LIKE(data_scope,'^(CURRENT_STORE|SPECIFIED_STORES|TENANT_STORES)$','c')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible data scope column or values';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
      AND table_name='employee_role' AND constraint_name='ck_employee_role_scope') THEN
    ALTER TABLE employee_role ADD CONSTRAINT ck_employee_role_scope
      CHECK(REGEXP_LIKE(data_scope,'^(CURRENT_STORE|SPECIFIED_STORES|TENANT_STORES)$','c'));
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
      AND table_name='rbac_permission' AND constraint_name='ck_permission_action') THEN
    ALTER TABLE rbac_permission ADD CONSTRAINT ck_permission_action
      CHECK(REGEXP_LIKE(code,'^[a-z]+:[a-z]+$','c'));
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints t
      JOIN information_schema.check_constraints c ON c.constraint_schema=t.constraint_schema AND c.constraint_name=t.constraint_name
      WHERE t.constraint_schema=DATABASE() AND t.table_name='employee_role' AND t.constraint_name='ck_employee_role_scope'
      AND t.constraint_type='CHECK' AND t.enforced='YES'
      AND BINARY REPLACE(c.check_clause,CHAR(92),'') = BINARY CONCAT('regexp_like(`data_scope`,_utf8mb4',CHAR(39),'^(CURRENT_STORE|SPECIFIED_STORES|TENANT_STORES)$',CHAR(39),',_utf8mb4',CHAR(39),'c',CHAR(39),')')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible ck_employee_role_scope constraint';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints t
      JOIN information_schema.check_constraints c ON c.constraint_schema=t.constraint_schema AND c.constraint_name=t.constraint_name
      WHERE t.constraint_schema=DATABASE() AND t.table_name='rbac_permission' AND t.constraint_name='ck_permission_action'
      AND t.constraint_type='CHECK' AND t.enforced='YES'
      AND BINARY REPLACE(c.check_clause,CHAR(92),'') = BINARY CONCAT('regexp_like(`code`,_utf8mb4',CHAR(39),'^[a-z]+:[a-z]+$',CHAR(39),',_utf8mb4',CHAR(39),'c',CHAR(39),')')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible ck_permission_action constraint';
  END IF;
  CREATE TABLE IF NOT EXISTS employee_role_store (
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    role_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    store_id bigint NOT NULL,
    PRIMARY KEY(tenant_id,employee_id,role_code,store_id),
    CONSTRAINT fk_ers_role FOREIGN KEY(tenant_id,employee_id,role_code)
      REFERENCES employee_role(tenant_id,employee_id,role_code),
    CONSTRAINT fk_ers_store FOREIGN KEY(tenant_id,employee_id,store_id)
      REFERENCES employee_store(tenant_id,employee_id,store_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='employee_role_store' AND is_nullable='NO' AND column_default IS NULL
      AND ((column_name IN ('tenant_id','employee_id','store_id') AND column_type='bigint')
        OR (column_name='role_code' AND column_type='varchar(64)' AND collation_name='ascii_bin'))) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible role store columns';
  END IF;
  IF NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='employee_role_store' AND index_name='PRIMARY' AND non_unique=0 AND sub_part IS NULL)
      <=> 'tenant_id,employee_id,role_code,store_id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible employee role store primary key';
  END IF;
  IF NOT ((SELECT GROUP_CONCAT(CONCAT(column_name,':',referenced_column_name) ORDER BY ordinal_position)
      FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='employee_role_store'
      AND constraint_name='fk_ers_role' AND referenced_table_schema=DATABASE() AND referenced_table_name='employee_role')
      <=> 'tenant_id:tenant_id,employee_id:employee_id,role_code:role_code') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible role store tenant foreign keys';
  END IF;
  IF NOT ((SELECT GROUP_CONCAT(CONCAT(column_name,':',referenced_column_name) ORDER BY ordinal_position)
      FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='employee_role_store'
      AND constraint_name='fk_ers_store' AND referenced_table_schema=DATABASE() AND referenced_table_name='employee_store')
      <=> 'tenant_id:tenant_id,employee_id:employee_id,store_id:store_id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible role store tenant foreign keys';
  END IF;
  -- Catalogue only: adding an action never grants it to existing roles.
  START TRANSACTION;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'dish:read','dish:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='dish:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'dish:create','dish:create' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='dish:create');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'dish:update','dish:update' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='dish:update');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'dish:delete','dish:delete' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='dish:delete');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'dish:status','dish:status' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='dish:status');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'category:read','category:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='category:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'category:create','category:create' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='category:create');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'category:update','category:update' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='category:update');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'category:delete','category:delete' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='category:delete');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'setmeal:read','setmeal:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='setmeal:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'setmeal:create','setmeal:create' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='setmeal:create');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'setmeal:update','setmeal:update' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='setmeal:update');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'setmeal:delete','setmeal:delete' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='setmeal:delete');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'setmeal:status','setmeal:status' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='setmeal:status');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:read','order:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:accept','order:accept' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:accept');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:reject','order:reject' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:reject');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:cancel','order:cancel' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:cancel');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:deliver','order:deliver' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:deliver');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:complete','order:complete' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:complete');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:refund','order:refund' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:refund');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'report:read','report:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='report:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'report:export','report:export' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='report:export');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'employee:read','employee:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='employee:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'employee:create','employee:create' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='employee:create');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'employee:update','employee:update' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='employee:update');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'employee:status','employee:status' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='employee:status');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'employee:manage','employee:manage' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='employee:manage');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'shop:read','shop:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='shop:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'shop:update','shop:update' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='shop:update');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'workspace:read','workspace:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='workspace:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'file:upload','file:upload' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='file:upload');
  COMMIT;
END$$
CALL _junzhou_rbac_scope()$$
DROP PROCEDURE _junzhou_rbac_scope$$
DELIMITER ;
