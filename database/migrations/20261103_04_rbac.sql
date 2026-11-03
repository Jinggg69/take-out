-- Stage 3.1. Stop writers and back up first; MySQL DDL is not atomic.
-- No inferred employee grants. Existing tenant templates are never reseeded.
DELIMITER $$
DROP PROCEDURE IF EXISTS _junzhou_rbac$$
CREATE PROCEDURE _junzhou_rbac()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;
  IF DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the application database';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('tenant','store','employee') AND engine='InnoDB' AND table_type='BASE TABLE') <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Requires tenant/store/employee InnoDB tables';
  END IF;
  IF EXISTS(SELECT 1 FROM employee e LEFT JOIN tenant t ON t.id=e.tenant_id WHERE t.id IS NULL)
     OR EXISTS(SELECT 1 FROM store s LEFT JOIN tenant t ON t.id=s.tenant_id WHERE t.id IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid historical tenant ownership';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='employee' AND index_name='uk_employee_tenant_id') THEN
    ALTER TABLE employee ADD UNIQUE KEY uk_employee_tenant_id(tenant_id,id);
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='store' AND index_name='uk_store_tenant_id') THEN
    ALTER TABLE store ADD UNIQUE KEY uk_store_tenant_id(tenant_id,id);
  END IF;
  IF NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='employee' AND index_name='uk_employee_tenant_id' AND non_unique=0) <=> 'tenant_id,id')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='store' AND index_name='uk_store_tenant_id' AND non_unique=0) <=> 'tenant_id,id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible tenant parent indexes';
  END IF;
  CREATE TABLE IF NOT EXISTS rbac_role (
    tenant_id bigint NOT NULL,
    code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name varchar(100) NOT NULL,
    PRIMARY KEY(tenant_id,code),
    CONSTRAINT fk_role_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  CREATE TABLE IF NOT EXISTS rbac_permission (
    tenant_id bigint NOT NULL,
    code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name varchar(100) NOT NULL,
    PRIMARY KEY(tenant_id,code),
    CONSTRAINT fk_permission_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  CREATE TABLE IF NOT EXISTS employee_role (
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    role_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY(tenant_id,employee_id,role_code),
    CONSTRAINT fk_er_employee FOREIGN KEY(tenant_id,employee_id) REFERENCES employee(tenant_id,id),
    CONSTRAINT fk_er_role FOREIGN KEY(tenant_id,role_code) REFERENCES rbac_role(tenant_id,code)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  CREATE TABLE IF NOT EXISTS role_permission (
    tenant_id bigint NOT NULL,
    role_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    permission_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY(tenant_id,role_code,permission_code),
    CONSTRAINT fk_rp_role FOREIGN KEY(tenant_id,role_code) REFERENCES rbac_role(tenant_id,code),
    CONSTRAINT fk_rp_permission FOREIGN KEY(tenant_id,permission_code) REFERENCES rbac_permission(tenant_id,code)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  CREATE TABLE IF NOT EXISTS employee_store (
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    store_id bigint NOT NULL,
    PRIMARY KEY(tenant_id,employee_id,store_id),
    CONSTRAINT fk_es_employee FOREIGN KEY(tenant_id,employee_id) REFERENCES employee(tenant_id,id),
    CONSTRAINT fk_es_store FOREIGN KEY(tenant_id,store_id) REFERENCES store(tenant_id,id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND is_nullable='NO' AND column_default IS NULL AND ((table_name='rbac_role' AND column_name='tenant_id' AND column_type='bigint') OR
        (table_name='rbac_role' AND column_name='code' AND column_type='varchar(64)' AND collation_name='ascii_bin') OR
        (table_name='rbac_role' AND column_name='name' AND column_type='varchar(100)') OR
        (table_name='rbac_permission' AND column_name='tenant_id' AND column_type='bigint') OR
        (table_name='rbac_permission' AND column_name='code' AND column_type='varchar(64)' AND collation_name='ascii_bin') OR
        (table_name='rbac_permission' AND column_name='name' AND column_type='varchar(100)') OR
        (table_name='employee_role' AND column_name='tenant_id' AND column_type='bigint') OR
        (table_name='employee_role' AND column_name='employee_id' AND column_type='bigint') OR
        (table_name='employee_role' AND column_name='role_code' AND column_type='varchar(64)' AND collation_name='ascii_bin') OR
        (table_name='role_permission' AND column_name='tenant_id' AND column_type='bigint') OR
        (table_name='role_permission' AND column_name='role_code' AND column_type='varchar(64)' AND collation_name='ascii_bin') OR
        (table_name='role_permission' AND column_name='permission_code' AND column_type='varchar(64)' AND collation_name='ascii_bin') OR
        (table_name='employee_store' AND column_name='tenant_id' AND column_type='bigint') OR
        (table_name='employee_store' AND column_name='employee_id' AND column_type='bigint') OR
        (table_name='employee_store' AND column_name='store_id' AND column_type='bigint'))) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible RBAC columns';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND referenced_table_schema=DATABASE() AND ((table_name='rbac_role' AND constraint_name='fk_role_tenant' AND column_name='tenant_id' AND referenced_table_name='tenant' AND referenced_column_name='id') OR
      (table_name='rbac_permission' AND constraint_name='fk_permission_tenant' AND column_name='tenant_id' AND referenced_table_name='tenant' AND referenced_column_name='id') OR
      (table_name='employee_role' AND constraint_name='fk_er_employee' AND column_name='tenant_id' AND referenced_table_name='employee' AND referenced_column_name='tenant_id') OR
      (table_name='employee_role' AND constraint_name='fk_er_employee' AND column_name='employee_id' AND referenced_table_name='employee' AND referenced_column_name='id') OR
      (table_name='employee_role' AND constraint_name='fk_er_role' AND column_name='tenant_id' AND referenced_table_name='rbac_role' AND referenced_column_name='tenant_id') OR
      (table_name='employee_role' AND constraint_name='fk_er_role' AND column_name='role_code' AND referenced_table_name='rbac_role' AND referenced_column_name='code') OR
      (table_name='role_permission' AND constraint_name='fk_rp_role' AND column_name='tenant_id' AND referenced_table_name='rbac_role' AND referenced_column_name='tenant_id') OR
      (table_name='role_permission' AND constraint_name='fk_rp_role' AND column_name='role_code' AND referenced_table_name='rbac_role' AND referenced_column_name='code') OR
      (table_name='role_permission' AND constraint_name='fk_rp_permission' AND column_name='tenant_id' AND referenced_table_name='rbac_permission' AND referenced_column_name='tenant_id') OR
      (table_name='role_permission' AND constraint_name='fk_rp_permission' AND column_name='permission_code' AND referenced_table_name='rbac_permission' AND referenced_column_name='code') OR
      (table_name='employee_store' AND constraint_name='fk_es_employee' AND column_name='tenant_id' AND referenced_table_name='employee' AND referenced_column_name='tenant_id') OR
      (table_name='employee_store' AND constraint_name='fk_es_employee' AND column_name='employee_id' AND referenced_table_name='employee' AND referenced_column_name='id') OR
      (table_name='employee_store' AND constraint_name='fk_es_store' AND column_name='tenant_id' AND referenced_table_name='store' AND referenced_column_name='tenant_id') OR
      (table_name='employee_store' AND constraint_name='fk_es_store' AND column_name='store_id' AND referenced_table_name='store' AND referenced_column_name='id'))) <> 14 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible RBAC tenant foreign keys';
  END IF;
  IF NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='rbac_role' AND index_name='PRIMARY' AND non_unique=0 AND sub_part IS NULL) <=> 'tenant_id,code')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='rbac_permission' AND index_name='PRIMARY' AND non_unique=0 AND sub_part IS NULL) <=> 'tenant_id,code')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='employee_role' AND index_name='PRIMARY' AND non_unique=0 AND sub_part IS NULL) <=> 'tenant_id,employee_id,role_code')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='role_permission' AND index_name='PRIMARY' AND non_unique=0 AND sub_part IS NULL) <=> 'tenant_id,role_code,permission_code')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='employee_store' AND index_name='PRIMARY' AND non_unique=0 AND sub_part IS NULL) <=> 'tenant_id,employee_id,store_id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible RBAC primary keys';
  END IF;
  -- Only provision tenants with no roles, inside one DML transaction.
  DROP TEMPORARY TABLE IF EXISTS _rbac_new_tenants;
  CREATE TEMPORARY TABLE _rbac_new_tenants(tenant_id bigint PRIMARY KEY);
  START TRANSACTION;
  INSERT INTO _rbac_new_tenants SELECT id FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_role r WHERE r.tenant_id=t.id);
  INSERT INTO rbac_role(tenant_id,code,name) SELECT tenant_id,'MERCHANT_ADMIN','商户管理员' FROM _rbac_new_tenants;
  INSERT INTO rbac_role(tenant_id,code,name) SELECT tenant_id,'MANAGER','店长' FROM _rbac_new_tenants;
  INSERT INTO rbac_role(tenant_id,code,name) SELECT tenant_id,'CLERK','店员' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'dish:read','查看菜品' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'dish:update','修改菜品' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'order:read','查看订单' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'order:accept','接单' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'order:refund','退款' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'report:read','查看报表' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'report:export','导出报表' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'employee:manage','管理员工授权' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','dish:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','dish:update' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','order:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','order:accept' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','order:refund' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','report:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','report:export' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','employee:manage' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MANAGER','dish:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MANAGER','dish:update' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MANAGER','order:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MANAGER','order:accept' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MANAGER','report:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'CLERK','dish:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'CLERK','order:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'CLERK','order:accept' FROM _rbac_new_tenants;
  COMMIT;
  DROP TEMPORARY TABLE _rbac_new_tenants;
END$$
CALL _junzhou_rbac()$$
DROP PROCEDURE _junzhou_rbac$$
DELIMITER ;
