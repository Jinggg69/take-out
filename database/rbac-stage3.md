# 阶段 3.1：租户角色与员工任职模型

交付五张 InnoDB 表：`rbac_role`、`rbac_permission`、`employee_role`、`role_permission`、`employee_store`。所有主键包含 tenant_id，所有关联使用包含 tenant_id 的复合外键；租户字段无默认值。员工和门店增加 `(tenant_id,id)` 唯一索引，关系删除采用 RESTRICT，避免静默丢失授权记录。

角色与权限采用租户内 code 标识，不依赖全局固定数字 ID。ASCII 二进制比较区分大小写。固定模板是每个租户独立的数据副本：

| 模板 | 初始权限 |
| --- | --- |
| MERCHANT_ADMIN 商户管理员 | dish:read、dish:update、order:read、order:accept、order:refund、report:read、report:export、employee:manage |
| MANAGER 店长 | dish:read、dish:update、order:read、order:accept、report:read |
| CLERK 店员 | dish:read、order:read、order:accept |

这是初始模板，动作全集和数据范围规则将在 3.2 扩展；接口鉴权、权限撤销和最后管理员保护在 3.3 实现，授权管理界面在 3.4 实现。当前后端仍沿用阶段二授权边界，新增关系尚未参与请求鉴权，不能将本步骤当作 RBAC 已上线。

## 部署

先完成阶段二迁移。停写、备份并选择应用库，执行 `database/migrations/20261103_04_rbac.sql`。新库的 `database/junzhou.sql` 已同步包含该脚本。禁止用完整初始化脚本升级已有业务库。

DDL 非原子，失败后检查已创建表和父索引，保持停写修复后重跑。表字段或租户外键不兼容时拒绝继续。模板插入单独事务，失败回滚。仅为没有任何角色的租户初始化模板；重跑不会恢复被撤销权限、覆盖角色名称或重建员工授权。不要将删除所有角色当作撤权操作，否则重跑会再次初始化该租户模板。对已有部分角色的租户，不自动补齐模板，需核对后显式维护。

不根据员工 ID、用户名或启用状态猜测管理员或任职，不自动回填员工角色和门店。上线运行时鉴权前，操作者必须逐租户核对员工和门店并显式建立关系。例如在已选应用库中，以核实过的实际值设置参数后执行：

```sql
SET @grant_tenant = 1, @grant_employee = 1, @grant_store = 1;
START TRANSACTION;
INSERT INTO employee_role(tenant_id,employee_id,role_code)
VALUES(@grant_tenant,@grant_employee,'MERCHANT_ADMIN');
INSERT INTO employee_store(tenant_id,employee_id,store_id)
VALUES(@grant_tenant,@grant_employee,@grant_store);
COMMIT;
```

该 SQL 是维护窗口的人工引导操作，不是对外授权 API。新增租户需显式准备模板与管理员，不允许请求路径执行迁移。员工可有多个角色和任职，角色本身不自动授予任何门店范围。员工停职不会删除关系，后续鉴权必须继续校验员工与租户、门店状态。

回退应用时保留五表及索引，不删除授权数据。恢复备份前需评估后续写入，禁止以删表回退运行中的多租户库。

## 验证与执行记录

命令：`python scripts/test-rbac-migration.py`。只连接本机 MySQL，用随机 `rbac_verify_` 临时库验证并清理，不修改应用库。

已验证：两租户模板、无隐式员工授权、角色与任职的跨租户拒绝、重复和不存在关联拒绝、缺省租户拒绝、父员工删除保护、店员无退款/导出权限、撤权与改名后重跑不覆盖、外键缺失拒绝、历史归属异常在建表前拒绝、模板插入失败整体回滚及修复后重试、完整初始化后再运行升级脚本。

执行裁定：3.1 限于持久化模型和模板，不引入提前启用的鉴权路径；存量角色和任职由明确核对后的人工操作建立，因为阶段二没有可信任职来源。若未在 3.3 上线前完成引导，员工将缺少运行时授权。

独立审查：发现已有关系表缺主键时迁移未拒绝；已添加五表主键校验，回归测试先失败后通过。外键校验同时限定当前数据库。
