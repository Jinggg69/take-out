-- 积分批量赠送和导出沿用会员积分账本，由商户管理员默认授权。
INSERT INTO rbac_permission (tenant_id, code, name)
SELECT t.id, v.code, v.name
FROM tenant t
CROSS JOIN (
    SELECT 'member:point:batch' AS code, '批量赠送积分' AS name
    UNION ALL SELECT 'member:point:export', '导出积分流水'
) v
WHERE NOT EXISTS (
    SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code=v.code
);

INSERT INTO role_permission (tenant_id, role_code, permission_code)
SELECT r.tenant_id, r.code, p.code
FROM rbac_role r
JOIN rbac_permission p ON p.tenant_id=r.tenant_id
    AND p.code IN ('member:point:batch','member:point:export')
WHERE r.code='MERCHANT_ADMIN'
AND NOT EXISTS (
    SELECT 1 FROM role_permission g
    WHERE g.tenant_id=r.tenant_id AND g.role_code=r.code AND g.permission_code=p.code
);

SET @points_management_index = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='member_asset_ledger'
          AND index_name='ix_member_asset_ledger_operations'),
    'SELECT 1',
    'ALTER TABLE member_asset_ledger ADD INDEX ix_member_asset_ledger_operations (tenant_id,environment,asset,create_time,id)'
);
PREPARE points_management_statement FROM @points_management_index;
EXECUTE points_management_statement;
DEALLOCATE PREPARE points_management_statement;
