# 数据库增量迁移

`sky.sql` 是全新开发库的初始化脚本，包含删除重建表的语句。已有数据库升级使用本目录增量脚本。

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

回退应用时保留新增字段、索引及 `refund_request`，保存核验与租约记录用于对账；旧版退款逻辑可能再次产生不可靠的资金状态，须保持退款处理停止，完成对账并确定回退方案后再恢复。不要以 `sky.sql`、删表或回填资金状态作为回退方式。

## 20261026_01_admin_session.sql

本次认证修复依赖 `admin_session` 表。脚本只创建该表，不修改员工密码或业务行；可重复执行。会话仅保存随机会话标识、员工 ID 和到期时间，不保存原始 JWT。

部署顺序：

1. 备份目标数据库，确认当前选择的库名和连接环境。
2. 在目标库执行 `20261026_01_admin_session.sql`，再部署新后端。不要使用 `sky.sql` 升级已有库。
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
