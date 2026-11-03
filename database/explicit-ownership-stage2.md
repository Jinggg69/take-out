# 阶段 2.7：业务归属默认值移除

新增迁移 `migrations/20261103_03_explicit_ownership.sql`，只移除以下八个字段的默认值，保留 NOT NULL、既有数据、默认租户 1 和默认门店 1：

| 表 | 字段 |
| --- | --- |
| employee、category、dish、setmeal | tenant_id |
| orders、shopping_cart | tenant_id、store_id |

不修改阶段 2.1 的历史迁移。`junzhou.sql` 在最后追加同一迁移，使完整初始化也收敛到无归属默认值的结构。支付账户迁移仍按 2.6 说明另行执行，需显式提供原商户身份。

## 写入口审计

独立审查核对生产 MyBatis 的 47 个写入语句（25 个注解、22 个 XML），未发现其他 JDBC、ORM 或原生 SQL 写入路径。

| 写入组 | 归属来源与拒绝行为 |
| --- | --- |
| EmployeeMapper | requireEmployeeTenantId，新增显式 tenant_id；员工更改、禁用和改密按员工租户授权 |
| CategoryMapper、DishMapper、SetmealMapper | requireTenantId，新增显式 tenant_id；更改、状态和删除限定当前租户，关联分类同租户 |
| DishFlavorMapper、DishMapper.insertFlavors、SetmealDishMapper | 经受范围约束的菜品/套餐父记录继承；组成关联两端同租户，批量检查完整范围 |
| ShoppingCartMapper | requireUserId/requireTenantId/requireStoreId，新增显式三者，数量变化、清空及重购均限定范围 |
| OrderMapper、OrderDetailMapper | 新增显式租户/门店/用户和支付账户；修改经 OrderMapper.scope，明细校验授权父订单和同租户商品 |
| RefundRequestMapper | 原订单范围、固定退款标识、金额和交易约束；状态与租约修改限定父订单 |
| AddressBookMapper | 用户本人归属；不增加租户字段 |
| FileAssetMapper | requireEmployeeTenantId，经有效上传员工登记租户，员工与租户匹配 |
| OrderMaintenanceMapper | 全局只扫描候选；写入必须经逐订单系统上下文和条件更新 |

全局用户注册 `UserMapper.insert` 为单 AppID 微信认证引导，不是商户所属数据。`AdminSessionMapper` 会话签发在凭据验证及员工锁定后，由员工行与服务端租户签发；过期会话清理为全局认证维护。这些入口不伪造业务上下文，也不依赖业务归属默认值。

通知从验签后的原支付账户/订单定位范围，定时任务从数据库候选逐订单建立范围，处理完成或异常时清理。请求入口与 WebSocket 现有单店保护仍保留；移除字段默认值不代表开放多租户业务。兼容类 LegacySystemScope 没有生产调用方，不属于活动写入回退路径。

新增 `OwnershipWriteGuardTest` 使用实际生产映射 SQL 构造过程，遍历业务 INSERT/UPDATE/DELETE，要求无 RequestScope 时在到达数据库前以 SecurityException 拒绝；全局认证写入按上述边界排除。

## 发布与验证

停写、备份并确认目标库后，先执行阶段 2.1—2.6 迁移和代码审计，再执行本迁移。预检要求八个字段均为 bigint NOT NULL、默认值为 1 或不存在，且默认父记录仍存在、历史租户/门店归属有效；发现冲突直接拒绝，不改写历史归属。业务库应启用 STRICT_TRANS_TABLES 或 STRICT_ALL_TABLES，使直接 SQL 遗漏必填字段时也拒绝；应用缺上下文拒绝不依赖 SQL mode。

MySQL DDL 隐式提交。预检失败时不执行 ALTER；中途 DDL 失败须保持停写、检查错误后重跑，已移除的默认值可重复移除。禁止使用 mysql --force 忽略错误。回退旧应用时先确认其显式传入全部归属，不能依靠恢复默认 1 掩盖缺上下文路径。

- `python scripts/test-explicit-ownership-migration.py`：本机随机临时库，验证八字段移除、遗漏租户/门店或显式 NULL 拒绝、显式新归属写入、完整历史单元格保留、默认父记录保留、重复执行、预检拒绝和部分 DDL 恢复；完整初始化脚本也验证通过。
- 后端全量验证：`./scripts/backend.ps1 -Offline`，336 项，0 失败、0 错误、1 项可选跳过，BUILD SUCCESS；证据见本地 `local-reports/explicit-ownership-full.log`。

本次未修改真实业务库；生产迁移和实际商户验收仍需在发布环境完成。
