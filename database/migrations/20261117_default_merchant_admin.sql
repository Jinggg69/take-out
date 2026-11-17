-- Run after the existing tenant/RBAC migrations. Adds a default account only to tenant 1.
-- Existing accounts and their passwords are never overwritten.
START TRANSACTION;
INSERT INTO employee (name,username,password,phone,sex,id_number,status,create_time,update_time,create_user,update_user,tenant_id)
SELECT '商户管理员','junzhou','{bcrypt}$2a$12$TtEZixOt6RQex3alq7Ze8OpV5tzQLwNFx1GW7jsd3vpmzlWIj3h9C',
 '', '1', '', 1, NOW(), NOW(), 1, 1, 1
WHERE EXISTS (SELECT 1 FROM tenant WHERE id=1)
AND NOT EXISTS (SELECT 1 FROM employee WHERE username='junzhou');
INSERT INTO employee_role (tenant_id,employee_id,role_code,data_scope)
SELECT 1,e.id,'MERCHANT_ADMIN','TENANT_STORES' FROM employee e
JOIN rbac_role r ON r.tenant_id=e.tenant_id AND r.code='MERCHANT_ADMIN'
WHERE e.username='junzhou' AND e.tenant_id=1
AND NOT EXISTS (SELECT 1 FROM employee_role er WHERE er.tenant_id=1 AND er.employee_id=e.id AND er.role_code='MERCHANT_ADMIN');
INSERT INTO employee_store (tenant_id,employee_id,store_id)
SELECT 1,e.id,s.id FROM employee e JOIN store s ON s.tenant_id=e.tenant_id
WHERE e.username='junzhou' AND e.tenant_id=1
AND NOT EXISTS (SELECT 1 FROM employee_store es WHERE es.tenant_id=1 AND es.employee_id=e.id AND es.store_id=s.id);
COMMIT;
