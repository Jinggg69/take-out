# 数据库增量迁移

`junzhou.sql` 是全新开发库的初始化脚本，包含删除重建表的语句。已有数据库升级使用本目录增量脚本。

## 20261103_01_file_asset.sql：公开菜单图片归属

依赖阶段一迁移及 `20261029_01_tenant_store.sql`，先执行此增量脚本，再部署配套后端。新增 `file_asset`，记录租户、上传员工、固定用途 `PUBLIC_CATALOG`、OSS 对象键、公开 URL 和上传时间；归属字段没有默认值，不自动回填历史 URL。新建库初始化脚本包含同一建表定义。

当前上传入口仅接受小于 2MB 的有效 PNG/JPEG，服务端解码重编码并按租户生成对象键；仍返回原有字符串 URL。管理端元数据查询和菜品/套餐新增或更换图片必须属于当前租户。既有商品保留原 URL 时不要求追溯认领，修改其他字段不受影响；将旧 URL 复制给新商品或替换其他商品图片须先经人工归属核验后导入记录或重新上传。

历史对象不自动移动、删除或改变 OSS ACL。上线前盘点既有对象及菜品/套餐引用，记录可核验的租户、来源和公开用途；存在多租户共享 URL 时分别复制到各租户目录后再回填，不以路径猜测归属。无法核验的对象保留待调查，不创建默认租户归属。仅公开菜单图片适用此表，私有附件不能调用此上传接口。

菜单图片为公开资源，直接 URL 读取不要求员工身份；它们不得包含私有资料。OSS/自定义域名应允许菜单图片公开读取，禁止匿名列举与写入；现有桶权限和历史对象由部署负责人盘点，本次代码不会修改远程 ACL。应用不提供未经归属校验的管理端文件读取、删除或签名 URL 接口。

`python scripts/test-file-asset-migration.py` 在随机临时本机 MySQL 库验证重复执行、已有数据保留、租户外键、用途和无默认归属约束，结束后只清理该临时库。迁移仅新增表，应用回退时保留文件记录及对象；不得在已有多租户数据时回退到未隔离后端。

## 20261029_01_tenant_store.sql：阶段二单店基础

此脚本必须在下述阶段一迁移之后执行。先核对数据库、验证可恢复备份，并停止所有应用实例和定时任务；只在明确的维护窗口对选定业务库运行。新建库的 `junzhou.sql` 已包含相同结构，不需要再单独执行本脚本。

脚本新增 `tenant`、`store`，其中门店外键指向租户；员工、分类、菜品、套餐、订单、购物车增加 `tenant_id`，订单和购物车另增加 `store_id`。历史单店数据归属 ID 为 1 的默认商户和默认门店；订单明细及退款继续通过订单确定归属。用户名和目录名称的现有全局唯一约束暂不改变。

**这只是数据模型，不提供多租户或多门店隔离。** 现有应用的插入语句尚未传递归属，因此新增字段暂时以 1 为默认值，兼容旧的单店写入。上线多租户之前必须先完成身份上下文、授权、所有 SQL/关联表、缓存、WebSocket、支付通知和任务隔离，移除不安全的默认归属并完成跨租户验收。不要用当前版本创建第二个实际商户或门店。

```sql
SELECT DATABASE();
SOURCE D:/take-out/database/migrations/20261029_01_tenant_store.sql;
SELECT id, code, name, status FROM tenant;
SELECT id, tenant_id, code, name, status FROM store;
SELECT COUNT(*) AS unexpected FROM employee WHERE tenant_id <> 1;
SELECT COUNT(*) AS unexpected FROM category WHERE tenant_id <> 1;
SELECT COUNT(*) AS unexpected FROM dish WHERE tenant_id <> 1;
SELECT COUNT(*) AS unexpected FROM setmeal WHERE tenant_id <> 1;
SELECT COUNT(*) AS unexpected FROM orders WHERE tenant_id <> 1 OR store_id <> 1;
SELECT COUNT(*) AS unexpected FROM shopping_cart WHERE tenant_id <> 1 OR store_id <> 1;
```

以上检查适用于首次从单店升级，所有 `unexpected` 均应为 0。还应核对升级前后的各表行数、主键、订单和退款金额、索引及单店业务流程。实际连接信息和路径按部署环境调整；不要使用 `--force`。脚本在相同定义下可重入且不覆盖现有归属；若已有同名结构或默认身份不兼容，会报错，需要人工调查。

MySQL DDL 会隐式提交，脚本并非原子操作；报错后保持停写，检查已创建的表、字段与自用 `_junzhou_20261029_tenant_store` 存储过程，修复原因后重试。回退应用版本时保留新增表和字段，旧应用会继续使用单店默认值；不要通过删除列回退真实业务数据。数据库恢复应按已验证的备份方案执行。

本地迁移回归可设置 `TENANT_TEST_MYSQL_USER`、`TENANT_TEST_MYSQL_PASSWORD` 和可选 `TENANT_TEST_MYSQL_PORT`，运行 `python scripts/test-tenant-migration.py`。脚本只连接本机 MySQL，创建随机临时数据库并在结束时删除，测试账号需要创建和删除数据库的权限。

2026-11-02 阶段 2.1 已在本机 MySQL 8.4.9 验证。回归脚本以初始化文件中阶段二之前的单店结构模拟已完成阶段一的旧库，并补入合成订单、明细、退款、购物车和套餐数据；比较所有原始表的全部原始字段（含主键、金额、支付退款状态），覆盖首次升级、重复执行、错误列定义修复、多个 ALTER 已提交后的注入失败与重入，以及六张业务表已有非默认归属不被覆盖。通过所有表的 `SHOW CREATE TABLE` 比较初始化与增量结构，忽略数据决定的自增计数。另验证旧写入默认值、门店外键拒绝及已有租户表冲突。测试结束清理随机临时库。

这份结果证明本地合成数据的迁移行为；真实业务库备份副本演练仍属于阶段 7.1，未操作或验证真实业务库，也不代表后续隔离任务已完成。此次沿用现有迁移，无新增业务 schema 变更。

## 已有库升级顺序与检查

先备份目标数据库并验证备份可恢复。在维护窗口停止所有旧、新后端实例及定时任务的写入与退款处理，再执行只读检查；检查通过后依次执行 `20261026_01_admin_session.sql`、`20261027_01_order_payment_lifecycle.sql`，核对结果后发布并启动新后端。不要让旧实例与新退款 worker 同时处理订单。

脚本面向 MySQL 8.0/8.4，需具备建表、改表、索引、创建/调用/删除存储过程与读写业务表的权限。使用 MySQL 客户端的 `SOURCE` 执行，保留 `DELIMITER`；不支持它的工具需要按存储过程边界拆分执行。务必先选择业务库，不要使用 `--force` 忽略错误。未选择库时客户端会报错；系统库、缺少前置表或非 InnoDB 会由迁移检查拒绝。

先运行以下只读检查（按真实检出路径与数据库连接信息调整）：

```sql
SELECT DATABASE();
SELECT table_name, engine, table_type
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('orders', 'user', 'shopping_cart', 'refund_request');

-- 必须为零行；NULL 订单号不参与唯一性，但待补录退款的订单号不能为空。
SELECT number, COUNT(*) AS duplicate_count
FROM orders WHERE number IS NOT NULL
GROUP BY number HAVING COUNT(*) > 1;

-- 必须为零行；迁移不会自动删除、合并或改写这些真实财务数据。
SELECT id, number, amount, status, pay_status
FROM orders
WHERE status = 6 AND pay_status IN (1, 2)
  AND (number IS NULL OR CHAR_LENGTH(TRIM(number)) = 0 OR amount IS NULL OR amount <= 0);

SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'orders'
  AND column_name IN ('amount', 'wechat_transaction_id', 'client_request_id');
```

若上面的列检查显示已存在 `wechat_transaction_id` 或 `client_request_id`，分别执行对应重复检查；未创建的列不要提前查询：

```sql
SELECT wechat_transaction_id, COUNT(*) AS duplicate_count
FROM orders WHERE wechat_transaction_id IS NOT NULL
GROUP BY wechat_transaction_id HAVING COUNT(*) > 1;

SELECT user_id, client_request_id, COUNT(*) AS duplicate_count
FROM orders WHERE client_request_id IS NOT NULL
GROUP BY user_id, client_request_id HAVING COUNT(*) > 1;
```

重复订单号、微信交易号、同一用户的请求号必须调查实际资金归属后人工处理；迁移遇到重复会 `SIGNAL` 终止，绝不自动清洗。`orders`、`user`、`shopping_cart` 必须已存在且使用 InnoDB；已有 `refund_request` 也必须为 InnoDB。订单金额必须为精确的 `DECIMAL(...,2)`，精度不超过 18，保证转换为分可精确装入有符号 bigint。不满足条件时先调查并制定数据修复方案。

```sql
SOURCE D:/take-out/database/migrations/20261026_01_admin_session.sql;
SOURCE D:/take-out/database/migrations/20261027_01_order_payment_lifecycle.sql;
SHOW CREATE TABLE orders;
SHOW CREATE TABLE shopping_cart;
SHOW CREATE TABLE refund_request;
```

## 20261027_01_order_payment_lifecycle.sql

新增配送时间、微信交易号、退款状态、支付核查时间、微信交易状态、客户端幂等请求号及提交指纹。订单号、非 NULL 微信交易号、同一用户的非 NULL 客户端请求号受唯一索引保护；历史 NULL 值保留。状态时间索引支持订单扫描，购物车用户索引用于按用户锁定与清空购物车，减少跨用户扫描锁。

`refund_request` 持久化同单一次、全额退款。`order_id` 唯一，`out_refund_no` 为主键；本应用发起和历史补录均沿用 `orders.number`。已验签的外部全额退款通知可按通知原退款号建档，仍受同单唯一约束。微信退款 ID 可空且唯一；任务领取使用状态、下次处理时间与租约索引。每单退款使用固定的原退款号，重试不得生成新号。

历史补录仅处理 `status=6` 且 `pay_status IN (1,2)` 的订单，金额以精确的 `amount * 100` 转为分：

| 原状态 | 补录退款状态 | query_only | attempts | 后续行为 |
| --- | --- | --- | --- | --- |
| 已取消、pay_status=2 | VERIFYING | 1 | 0 | 旧实现标为退款不能证明微信已退款，只查询原退款号核验；不自动申请新退款 |
| 已取消、pay_status=1 | REQUESTED | 0 | 1 | 先查询原退款号，再按查询结果以同号重试全额退款 |

只插入缺少 `order_id` 的退款记录，不覆盖已有请求；订单 `refund_status` 为空时从对应请求补为 `VERIFYING` 或 `REQUESTED`，已有非空状态保留。`pay_status` 全程不被迁移修改，微信确认前不会伪造资金完成。历史 `pay_status=2` 的记录在查询核验前仍属于待核实数据，须通过真实微信查询结果及人工对账确认。

字段与索引通过 `information_schema` 检查后条件创建，可在失败修复后重入，兼容 MySQL 8.4 不支持 `ADD COLUMN IF NOT EXISTS` 的限制。同名但不兼容的字段/索引会报错，需人工核对，脚本不会替换业务表。迁移只创建、删除以 `_sky_20261027_lifecycle` 开头的自用存储过程；失败可能留下这些过程，下次执行会重新创建。

**整个脚本不是原子事务。** MySQL DDL 有隐式提交，前半段发生故障时，已完成的字段、索引或新表可能保留。最后的退款补录与订单退款状态回填置于单独事务，失败一起回滚；修复报错原因后重新执行即可。保持维护窗口直到迁移与验收完成。

迁移完成会输出历史退款审核清单。还需确认初始化库与升级库字段/索引一致、同一脚本重复执行不改变已有退款请求、补录前后订单 `pay_status` 相同、每个待处理历史订单恰有一条退款请求。上线前在临时库覆盖：原始结构升级、重复运行、部分 DDL 后重入、重复财务标识拒绝、非 InnoDB/缺前置表拒绝、无效历史金额/单号拒绝、已有退款记录保留，以及历史两类退款的状态与金额。

回退应用时保留新增字段、索引及 `refund_request`，保存核验与租约记录用于对账；旧版退款逻辑可能再次产生不可靠的资金状态，须保持退款处理停止，完成对账并确定回退方案后再恢复。不要以 `junzhou.sql`、删表或回填资金状态作为回退方式。

## 20261103_03_explicit_ownership.sql

阶段 2.7 在写入口审计后移除员工、目录、订单和购物车共八个租户/门店归属默认值，保留 NOT NULL、全部历史值及默认父记录。停写并备份后执行；预检或 DDL 失败需检查并在停写期间重跑。完整初始化脚本已包含此步骤。审计清单、严格 SQL 模式要求及验证见 [显式归属说明](../explicit-ownership-stage2.md)。

## 20261103_02_payment_account.sql

阶段 2.6 支付账户与订单固定绑定。先完成租户/门店及支付生命周期迁移；停写后在同一连接显式设置原商户号、原 AppID，再执行脚本。新建库导入 `junzhou.sql` 后同样需要执行。原账户凭证必须保留，不允许切换门店配置时覆盖历史订单账户。部署配置、迁移失败重入和验证命令见 [支付账户绑定说明](../payment-account-stage2.md)。

## 20261026_01_admin_session.sql

本次认证修复依赖 `admin_session` 表。脚本只创建该表，不修改员工密码或业务行；可重复执行。会话仅保存随机会话标识、员工 ID 和到期时间，不保存原始 JWT。

部署顺序：

1. 备份目标数据库，确认当前选择的库名和连接环境。
2. 在目标库执行 `20261026_01_admin_session.sql`，再部署新后端。不要使用 `junzhou.sql` 升级已有库。
3. 配套发布 Vue 3 管理端，使改密成功后清除本地认证并跳转登录。
4. 重新登录。旧版 JWT 缺少会话标识，将返回 401。

在仓库根目录，可先进入 MySQL 客户端（将占位符替换为实际值；密码通过提示输入）：

```text
mysql --host=<数据库地址> --port=<端口> --user=<用户名> -p <目标库名>
```

在客户端执行，路径按实际检出位置调整：

```sql
SELECT DATABASE();
SOURCE D:/take-out/database/migrations/20261026_01_admin_session.sql;
SHOW CREATE TABLE admin_session;
```

验收登录、受保护接口、退出后旧 Token 返回 401、禁用和改密后该员工所有旧会话失效。退出只撤销当前会话；重新启用账号需要重新登录。数据库不可用时，受保护的管理端请求返回 503。

多实例部署需统一数据库、签名密钥与时间。升级期间旧版本实例仍遵循旧的验签逻辑，应完成所有实例切换后再开放访问。已开始执行的业务请求不会因会话随后撤销而被中断。

回退应用时保留新增表即可；旧后端忽略该表，也不具备本次会话撤销能力。之后重新升级前应清空遗留会话，让所有员工重新登录，避免恢复曾在旧版本中退出的会话。

## 20261103_04_rbac.sql

阶段 3.1 新建五张租户隔离角色、权限与员工任职表，初始化商户管理员、店长、店员模板。停写备份后执行，不自动猜测员工授权；重跑保留已有租户模板及撤权。完整初始化已同步。模型、人工授权引导、回退和临时库验证见 [RBAC 模型说明](../rbac-stage3.md)。接口鉴权在 3.3 接入。

## 20261103_05_rbac_scope.sql

阶段 3.2 新增角色任职门店关联，为员工角色关系增加本店/指定店/租户全部店范围，默认本店；动作目录采用明确的 `资源:动作`，禁止通配。仅补充动作目录，不增加现有角色授权。先执行 3.1，停写备份后升级；完整初始化已同步。范围合并规则、Java 模型与验证见 [动作权限和范围说明](../rbac-scope-stage3.md)。接口鉴权在 3.3 接入。

## 20261105_01_employee_password_hash.sql

阶段 3.5 将 employee.password 扩为 varchar(255)。在维护窗口备份并选择应用库，先执行该增量脚本，再统一切换后端实例。新库的 junzhou.sql 已包含字段定义和 BCrypt 格式的开发管理员初始凭据。已有库使用增量脚本，脚本保留原有凭据且可重跑。

新建员工和改密采用带随机盐的 BCrypt，工作因子为 12。普通记录前缀为 `{bcrypt}`。为完整保留旧密码中超过 72 个 UTF-8 字节或含 NUL 的输入，此类密码先对原始 UTF-8 字节计算 SHA-256 并用 Base64 编码，再使用相同的 BCrypt 和随机盐，前缀为 `{bcrypt-sha256}`。验证按记录前缀区分格式。

未标记的 32 位十六进制 MD5 在员工首次成功登录时升级。应用先校验输入，再锁定租户、员工并核对凭据快照，会话签发和凭据更新一起提交。两次并发首次登录遇到快照冲突时，后一个请求可重新登录。失败登录、停职账号、会话签发或升级事务失败时保留原凭据。主动改密在同一事务撤销该员工全部会话。

明文及未知格式的存量凭据拒绝登录，需通过受控方式重置。已有账号开始升级后应使用能验证新格式的应用版本。恢复旧版 MD5 应用前需要恢复配套备份。
