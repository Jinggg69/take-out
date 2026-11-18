-- 首页装修由租户共享。只有显式发布的配置才提供给小程序。

CREATE TABLE IF NOT EXISTS tenant_home_decoration (
    tenant_id BIGINT NOT NULL,
    draft_json JSON NULL,
    published_json JSON NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id),
    CONSTRAINT fk_home_decoration_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

INSERT INTO rbac_permission (tenant_id, code, name)
SELECT t.id, 'decoration:manage', '页面装修'
FROM tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='decoration:manage'
);

INSERT INTO role_permission (tenant_id, role_code, permission_code)
SELECT r.tenant_id, r.code, 'decoration:manage'
FROM rbac_role r
WHERE r.code='MERCHANT_ADMIN'
AND NOT EXISTS (
    SELECT 1 FROM role_permission p
    WHERE p.tenant_id=r.tenant_id AND p.role_code=r.code AND p.permission_code='decoration:manage'
);
