# 数据库增量迁移

`junzhou.sql` 是全新开发库的初始化脚本，包含删除重建表的语句。已有数据库升级使用本目录增量脚本。

## 20261112_01_merchant_discovery.sql：小程序公开商家目录

依赖 `20261109_02_store_fulfillment.sql`。在维护窗口备份并核对选定业务库后，先执行此迁移再发布对应后端；本轮交付未对真实业务库执行迁移。初始化库同样需要执行本脚本。

新增可空 `tenant.logo_url VARCHAR(512)`、`tenant.introduction VARCHAR(500)`、`store_settings.contact_phone VARCHAR(32)`，存量数据初始 NULL，既有身份归属、订单与费用不变。脚本可重复执行，已有值保留；若同名列定义不匹配则报错。MySQL DDL 隐式提交，失败后检查已生效字段并修复再重入，不能用 `--force`。应用回退保留字段与数据。

商家资料仅通过受控配置维护，此轮没有资料管理/上传页面。`logo_url` 只填写可匿名读取的公开静态图片 HTTPS URL，当前服务端仅接收 `/public/` 下 `.png/.jpg/.jpeg/.webp/.gif/.svg` 路径，且无查询串、片段、用户信息、编码或 `..` 路径；例如 `https://cdn.example.com/public/merchants/merchant-2.png`。其他值在公开接口返回 NULL。部署负责人须确认 CDN/对象匿名读取授权；不得填写员工鉴权接口或含 token 的签名链接，也不改变已有 file_asset 归属和权限。

联系电话为空可保存，空白会转 NULL；有值时最多32字符，允许数字、空格、`+()-` 且必须含数字。电话通过已有门店配置保存接口更新，沿用版本锁与版本递增，不修改其他店配置。

升级后核对三列类型和 NULL 默认值、tenant/store_settings 行数及原始字段未变；抽查无资料商家返回空图片/简介、无电话门店返回 NULL，以及更新电话后 configVersion 增长。测试使用随机 H2 合成库覆盖实际 Mapper，MySQL 脚本的真实库执行与备份恢复演练由部署阶段完成。

运行 `python scripts/test-merchant-discovery-migration.py` 可在本机 MySQL 一次性随机测试库验证首次升级、重复执行保留已填资料与版本/归属、拒绝不匹配列、完整初始化后追加迁移。只读取开发配置中的localhost连接，密码通过MYSQL_PWD传递且不打印，不选择开发业务schema；最终仅清理本脚本创建的随机库。需要本机测试账号具备创建/删除测试库和DDL权限。沙箱网络阻止时不能跳过校验，应在授权后重跑。真实业务库迁移和可恢复备份演练仍须部署阶段单独完成。

2026-11-12 本机授权连续两轮验证以上四项通过，随机测试库均已清理；这证明合成数据上的迁移行为，未执行真实业务库升级。

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

## 20261105_02_store_menu.sql

阶段 4.1。在阶段 3 迁移完成后，备份并暂停目录和门店菜单写入，在应用库执行增量脚本。新库可直接使用 junzhou.sql。store_dish 和 store_setmeal 通过复合外键限制门店和商品属于同一租户。删除门店或商品会一并删除对应销售关系。

升级时仅为旧单店（租户 1、门店 1）补齐目录商品的销售关系，默认上架、未沽清、排序 0，覆盖价为 NULL 表示沿用目录价。目录自身的停售状态仍保存在目录上。其他门店显式配置商品，新建关系默认下架。重复执行保留已有配置并补齐旧单店缺少的关系；要保留下架状态请设置 on_sale=0。可运行 python scripts/test-store-menu-migration.py 在临时 MySQL 库验证。

## 20261109_01_cart_identity.sql

阶段 4.3。购物车新增数据库生成列 product_type、product_id、flavor_key，并对 tenant_id、store_id、user_id 和三个生成列建立唯一键。菜品与套餐即使 ID 相同也各自计数。口味键使用二进制字节比较，与 Java 字符串比较一致。

口味规则沿用应用现有行为。菜品口味的 NULL 转为空串，首尾 U+0000 至 U+0020 字符按 String.trim() 去除，保留内部空白、大小写和口味文本顺序。套餐口味统一为空串。业务写入继续通过 ShoppingCartService，所有加减购、清空、重购和下单沿用用户行锁。数据库检查约束要求已规范化口味、有效商品类型和正数数量。

已有库先备份，在备份恢复出的临时库演练。首次执行脚本会列出重复项的租户、门店、用户、商品、口味十六进制、原记录 ID、原数量、保留 ID 和合计数量，遇到重复项会中止并保留原数据。核对后，在同一 MySQL 会话执行 SET @cart_duplicates_reviewed=1，再重新执行脚本。保留最小 ID 的记录，数量累加，其余展示和缓存价格字段沿用保留行；结算与重购会重新读取当前门店价格。数量溢出或无效商品记录会中止，需先核查来源。

正式升级期间暂停购物车写入，完成合并和 ALTER TABLE 后恢复写入。MySQL DDL 隐式提交，若添加约束失败，合并后的数据可能已提交；应保持停写，修正故障后重跑，或从备份恢复。重跑已完成的迁移会跳过处理。新库直接执行同步更新的 junzhou.sql。

验证可运行 python scripts/test-cart-identity-migration.py，在隔离的本机 MySQL 临时库中检查重复项核对、数量合并、唯一键、作用域隔离、新库初始化、异常保留和重跑。购物车与订单模块测试覆盖带唯一键的并发加减购及下单与加购互相等待。

## 20261109_02_store_fulfillment.sql

阶段 4.4、4.5。先应用门店销售关系和购物车唯一键迁移，再在维护窗口备份并执行本脚本。新增 store_settings 和订单 store_snapshot、pricing_snapshot 两个文本快照字段。新库初始化已同步。重复执行保留已有配置和历史订单，缺失的快照列可分别补齐。

每个门店可设置地址、百度 BD-09 经纬度、一个每天重复的营业时段、骑行配送距离上限、起送金额、每件打包费、固定配送费和可空的免配送费门槛。相同起止时间表示全天营业，结束早于开始表示跨午夜，时间按 Asia/Shanghai。起送与免配送门槛均以商品小计判定。默认全天营业、5 公里、起送 0 元、每件打包 1 元、配送 6 元；空的免配送门槛表示按配置收费，0 表示免配送费。

管理端“门店设置与菜单”入口使用 shop:read 和 shop:update 权限。保存配置携带 version，过期版本拒绝覆盖。菜单可逐项设置上架、沽清、排序和可空覆盖价，空值沿用租户目录价。用户菜单按当前门店读取有效价格，套餐逐项检查组成菜品。用户端原单店请求限制已移除，X-Store-Id 仍为必填，租户由服务端根据门店推导。门店列表只返回当前商户启用的门店。

小程序首页支持选店，切店保留服务端各店购物车，清空本地结算草稿并丢弃迟到响应。结算展示服务端报价，提交携带 storeId 和 configVersion。旧客户端省略这两个字段时仍按请求头选店，并由服务端重查配置及价格。

下单先读取当前配置、地址并在事务外调用地图，再在 READ_COMMITTED 事务内锁用户和门店，重验配置版本、地址、可售规则与报价，成功后写入订单及快照并清空当前店购物车。地图耗时期间可继续加购，若改变了已确认金额，提交会要求重新确认。订单的 pack_amount 保留件数字段，实际打包费、配送费、商品金额和门店配置在 JSON 快照中；管理端和小程序历史详情优先读取快照。旧单的快照为空时使用原有展示字段。

旧租户 1、门店 1 在尚未填写坐标时，配送校验沿用已有部署的门店地址配置。其他门店必须先在管理端填写坐标。保存任何门店配置都要求完整地址和坐标。上线前需检查地图密钥、门店实际营业时间和配送规则。

验证脚本 python scripts/test-store-fulfillment-migration.py 只使用临时 MySQL 库，覆盖升级默认值、重跑保留配置、数据库校验和跨租户关系拒绝。后端测试覆盖费用快照、配置冲突、营业时段、门店坐标和地图调用期间的购物车并发。管理端已完成构建、测试与桌面/手机宽度浏览器检查；小程序已完成 Node 测试和 HBuilderX 微信小程序编译。
## 20261109_03_order_fulfillment.sql

阶段 5A，先完成此前所有迁移。停旧实例并备份，执行本脚本后统一发布后端。增加独立 fulfillment_type、fulfillment_status、联系人/桌台快照及购物车摘要；历史类型回填 DELIVERY，按旧 status 回填履约状态，原 status/pay_status 数值不变。address_book_id 改为可空，但外卖仍由服务端强制校验地址归属。

store_dining_table 是当前门店有效桌台的基础登记，使用复合外键限制租户和门店归属。本阶段不提供桌台管理、二维码或桌次；不在小程序开放新的履约入口。重复迁移保留已存在的订单类型、状态和桌台。详细计费、接口参数、状态转换和后续阶段边界见 [阶段 5A 契约](../order-fulfillment-stage5a.md)。部署开放新类型后不可回退到旧版应用。

本地验证：python scripts/test-order-fulfillment-migration.py，仅创建和清理随机临时 MySQL 库，覆盖历史回填、重跑、枚举约束、桌台归属与新库初始化。业务库尚未执行该增量迁移。


## 20261109_04_order_pickup.sql

阶段 5B，在 5A 后执行。增加展示取餐号、随机核销凭证及核销员工/时间，已有自取单回填独立随机凭证；重跑保留现有值。新增 order:pickup:ready 和 order:pickup:verify 权限目录，不自动给角色授权。发布后由商户管理员配置角色动作和门店任职；未授权店员不能制作完成或核销。

验证入口 python scripts/test-order-pickup-migration.py，只修改随机临时库；当前会话受自动审批额度限制，尚未运行 MySQL 演练。业务库未变更。详见 [5B 契约](../order-pickup-stage5b.md)。


## 20261109_05_dining_session.sql

阶段5C，在已更新的5B迁移后执行。新增桌码、当前桌次、桌次成员及订单桌次快照，使用复合外键约束归属；重跑保留已有桌码和桌次。5B前置脚本同步修正了权限格式约束，使其接受 order:pickup:verify / order:dine:complete 等三段动作；即使已升级5B，也应先重跑修正版5B脚本。

新权限 dining:read、dining:manage、dining:close、order:dine:complete 仅加入权限目录，由商户管理员按角色和门店配置。桌码为小程序内扫码内容，轮换后重新下载桌牌。详见 [5C 契约](../dining-stage5c.md)。

python scripts/test-dining-session-migration.py 与 python scripts/test-order-pickup-migration.py 均已在随机临时MySQL库通过；包含回填、重跑、外键拒绝跨店关系和新库初始化。临时库已清理，业务库未迁移。


## 20261110_01_report_indexes.sql

阶段6经营报表复合索引，依赖阶段5C。备份并停止写入后执行，五个索引支持门店事件时间窗口与确认退款查询，重跑不会重复添加。不回填历史时间、不改金额、不新增汇总表。全新初始化已同步；随机临时MySQL库已验证重跑与真实SQL，当前业务库未迁移。部署后端时同时使用上海JVM时区和Druid会话时区配置；历史其他时区时间须先对账。详细指标、接口、授权、导出及回退边界见 [阶段6契约](../report-stage6.md)。


## 2026-11-10 本机当前库实际迁移完成

本机 `localhost:3306/sky_take_out` 已在完整备份、恢复演练和停写确认后补齐20261105_01起的8项迁移；全部16项迁移目标结构已到位。30张表的字段、索引、外键、CHECK及触发器与全新初始化加支付账户迁移的基准一致，原始数据指纹保持，后端启动及5项只读HTTP/鉴权检查通过。详细记录、脚本哈希与备份路径保存在本地忽略目录 `local-reports/migration-execution-20261110/completion.md`；完整备份保留在 `database/backups/`。此前章节“业务库未迁移”是当时的历史状态，此记录为当前本机库的较新状态；不代表其他部署环境已迁移。新增功能权限仍由商户管理员配置。


## 2026-11-10 本机数据库名称切换

按用户要求，当前本机活动库已由 `sky_take_out` 切换为 `junzhou_take_out`。完整备份后复制并逐表校验30张表全部记录、结构及自增编号一致；后端连接已切换并通过启动/只读HTTP检查，旧库保留用于回退。默认配置模板和初始化目标已同步。迁移完成及改名的历史记录中的旧库名不改写；最新改名证据与备份路径见本地 `local-reports/database-rename-20261110/completion.md`。DataGrip等工具需手动刷新并选择新库。


## 20261112_02_mock_payment.sql：学习演示支付渠道

新增订单不可变 payment_channel（历史默认 WECHAT）和持久 mock_payment 表。先完成支付账户及生命周期迁移，停写备份后执行本迁移，再部署后端。脚本不改商户身份、门店账户映射或历史资金记录。Mock 默认关闭，启用与完整演示步骤见 [Mock支付说明](../mock-payment.md)。迁移演练命令：`python scripts/test-mock-payment-migration.py`，仅使用本机随机临时库。
