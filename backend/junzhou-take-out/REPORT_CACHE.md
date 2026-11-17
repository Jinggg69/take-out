# 工作台与经营报表展示缓存

**状态：** 已实现并通过本地验证（真实 Redis、H2 真实 SQL 与 HTTP 集成）。仅后端展示缓存，无数据库迁移，前端无需改动。

**开发计划与验收依据：** [REPORT_CACHE_DEVELOPMENT_PLAN.md](REPORT_CACHE_DEVELOPMENT_PLAN.md)

## 1. 缓存范围

以 Cache-Aside 方式缓存**最终展示结果**，命中直接返回，未命中查库后写入有限 TTL 的 Redis 键。

| 接口 | 服务方法 | 缓存视图 | 基础 TTL |
| --- | --- | --- | --- |
| `GET /admin/workspace/businessData` | `business()` | `workspace-business` | 15 秒 |
| `GET /admin/workspace/overviewOrders` | `overview()` | `workspace-orders` | 15 秒 |
| `GET /admin/workspace/overviewDishes` | `menu("dish")` | `workspace-dishes` | 15 秒 |
| `GET /admin/workspace/overviewSetmeals` | `menu("setmeal")` | `workspace-setmeals` | 15 秒 |
| `GET /admin/report/turnoverStatistics` | `turnover(f)` | `report-turnover` | 历史 120 秒；含今天/未来 15 秒 |
| `GET /admin/report/ordersStatistics` | `orders(f)` | `report-orders` | 同上 |
| `GET /admin/report/userStatistics` | `users(f)` | `report-users` | 同上 |
| `GET /admin/report/top10` | `top10(f)` | `report-top10` | 同上 |

时间口径：

- 全部日期按 `Asia/Shanghai` 解释，服务层把客户端含首尾日期转为 `[from, until)` 自然日区间。
- `until <= 上海今天 00:00` 视为历史范围（长 TTL），否则使用短 TTL；未来日期仍按原逻辑允许查询，不因缓存新增限制。
- 写入 TTL = `base - random(0..min(jitter, base-1))`，最小 1 秒。默认短缓存实际 13～15 秒，历史缓存 118～120 秒。
- TTL 在写入成功时开始，命中不续期；快照年龄还包含原查询耗时，不承诺相对数据库绝对只滞后 15/120 秒。
- 跨午夜后工作台使用新的日期与新的缓存键，不会把昨日结果当作今日结果。

**不缓存：** XLSX 导出与 `export(f)`、原始订单事件明细、对账数据、认证/权限判断、`ReportFilter`、其他订单列表接口。本方案不是 Redis 持久统计或增量计数方案，不用 Redis 计算金额或替代 MySQL。

## 2. 执行顺序（每次请求）

工作台：

1. `rbac.require(WORKSPACE_READ)`（拦截器与服务工作台方法均保留原检查）。
2. 由注入的 `reportClock` 捕获一次上海日期，创建今日 `ReportFilter`。
3. 生成缓存键、计算 TTL、`getOrLoad`。
4. `menu(kind)` 只接受 `dish`/`setmeal`，其他值在读取缓存前拒绝。

经营报表：

1. filter 结构校验与快照复制（租户匹配可信上下文、自然日对齐、`from < until`、最多 93 天、1～100 家门店、排序去重）。
2. `authorize(filter,false)`：每家门店当前都必须有 `REPORT_READ`，任一家无权则整次拒绝，不静默缩小为“只查有权门店”。
3. 授权通过后才生成键、计算 TTL、读取缓存。
4. 命中同样先执行第 1、2 步，因此预热缓存不能绕过撤权。

缓存读取失败（连接、超时、反序列化、结构不合约）按 miss 处理并回查数据库；数据库、权限与业务异常按原方式抛出，不写入缓存，也不返回伪造的全零数据。

## 3. 缓存键

```text
junzhou:<namespace>:report-cache:v1:tenant:1:view:workspace-business:stores:<sha256>:from:20261117T000000:until:20261118T000000:zone:Asia_Shanghai
junzhou:<namespace>:report-cache:v1:tenant:1:view:report-turnover:stores:<sha256>:from:20261101T000000:until:20261117T000000:zone:Asia_Shanghai
```

- 门店 ID 先按正整数升序去重，以逗号连接后取 SHA-256 小写十六进制；`[2,1,2]` 与 `[1,2]` 相同，`[1,23]` 与 `[12,3]` 不同。
- 不同租户、门店集合、日期边界、视图与环境互相隔离；不使用分类名或请求 URL 作键。
- 日期使用规范化服务端边界，默认日期范围与同范围显式日期命中同一个键。
- 同一租户、门店集合、日期、视图的不同员工共享展示数据，但每次请求都重新授权；不缓存员工或权限对象。
- 表达式或字段语义变更时必须升级 `report-cache:v1`，不复用旧 schema；不使用 `KEYS`、`SCAN`、`FLUSHDB` 或无界 JVM Map 管理缓存。

## 4. 配置与回退

`application.yml`（已合并到现有 `sky` 下，未新增同名顶层键）：

```yaml
sky:
  report-cache:
    enabled: ${REPORT_CACHE_ENABLED:true}
    namespace: ${REPORT_CACHE_NAMESPACE:dev}
    workspace-ttl-seconds: ${REPORT_CACHE_WORKSPACE_TTL_SECONDS:15}
    live-report-ttl-seconds: ${REPORT_CACHE_LIVE_TTL_SECONDS:15}
    historical-ttl-seconds: ${REPORT_CACHE_HISTORICAL_TTL_SECONDS:120}
    jitter-seconds: ${REPORT_CACHE_JITTER_SECONDS:2}
```

校验规则（非法配置启动失败，不静默归零或改为永久缓存）：

- `namespace` 匹配 `[A-Za-z0-9_-]{1,32}`。
- 三个 TTL 均在 1～86400 秒。
- `jitter-seconds` 在 0～86400 之间；抖动后 TTL 不低于 1 秒。

生产部署应为每个环境设置稳定且唯一的 `REPORT_CACHE_NAMESPACE`（如 `prod`）。属性变更重启生效，不承诺热更新。

回退：设置 `REPORT_CACHE_ENABLED=false` 并重启全部实例，服务直接查库，旧键按 TTL 自然回收；无数据库迁移，不清空 Redis，也不影响用户端菜单缓存与限流等其他 Redis 功能。

## 5. 故障与并发边界

- Redis 读取失败：本次请求直接回查数据库，且不再尝试写入，避免一次请求重复等待超时。
- Redis 写入失败或序列化失败：仍返回本次数据库结果，只记录视图名与故障类型（不打印金额、token 或完整请求）。
- 写入使用 `SETNX`（`setIfAbsent`）加 TTL：较晚结束的并发查询不会覆盖已有快照；NX 失败不重新查库。
- 首期允许同一热点冷启动时多次查库，不承诺“一百个并发只查一次”；TTL 抖动只打散不同键的到期时间，不是单热点防击穿。
  后续若压测证明需要，再单独设计有界等待或租约，不临时加未验证的全局锁。
- 本期只通过 TTL 失效，不监听下单/取消/支付/退款/商品变更，也不扫描历史报表键。有效期内管理端刷新可能看到旧值，属于明确接受的展示行为。
- 无绕过缓存参数、强制刷新按钮或新响应字段。

## 6. 导出与交易正确性

- `export(f)` 保留原 `REPEATABLE_READ` 只读事务、`REPORT_READ`+`REPORT_EXPORT` 双权限、50000 事件/明细上限、4 张 sheet 与字段顺序。
- 导出不调用 `getOrLoad`；原 `days`、`cohortRate` 等计算助手不加缓存，缓存只包在指定公开展示方法内。导出不经过已缓存的 `turnover/orders/top10`。
- 下单计价、结算、退款、对账与数据库写入路径完全不接入本缓存。
- 工作台读取不要求 `REPORT_READ`；导出仍需 `REPORT_READ`+`REPORT_EXPORT`。
- 图表与导出本就独立查询，不承诺两者逐项即时相等；无新写入且缓存刷新后，结果必须与原数据库计算一致。

## 7. 代码结构

新增：

| 文件 | 职责 |
| --- | --- |
| `junzhou-server/src/main/java/com/junzhou/report/cache/ReportCacheProperties.java` | 配置、启动校验、基础 TTL 与抖动 |
| `junzhou-server/src/main/java/com/junzhou/report/cache/ReportCacheKey.java` | 8 个视图枚举与确定性键编码 |
| `junzhou-server/src/main/java/com/junzhou/report/cache/CachedReportResult.java` | 可序列化信封（`schemaVersion=1` + `data`） |
| `junzhou-server/src/main/java/com/junzhou/report/cache/ReportResultCache.java` | Cache-Aside 读写、命中校验与降级 |
| `junzhou-server/src/main/java/com/junzhou/config/ReportClockConfiguration.java` | 命名 `reportClock` 的上海时钟 |

修改：

- `ReportServiceImpl`：构造函数改为 `(ReportMapper, RbacService, ReportResultCache, @Qualifier("reportClock") Clock)`；8 个视图接入缓存，原计算提取为私有 loader，授权与返回结构不变。
- `ReportRow`：实现 `Serializable`（固定 `serialVersionUID`），字段与 JSON 输出不变。
- `application.yml`：新增 `sky.report-cache`。
- `ReportStage6Test`、`WorkspaceMenuTest`：改用新构造函数，显式禁用缓存并注入固定时钟。

## 8. 测试与命令

本地 Redis（`./scripts/redis.ps1 -Action start`）必须先启动；`ReportCacheRedisTest`、`ReportCacheIntegrationTest` 使用真实模板与 JDK 序列化，其余为单元/真实 SQL 测试。

```powershell
./scripts/backend.ps1 -Goal test -Test 'ReportCacheKeyTest,ReportCachePropertiesTest,ReportResultCacheTest,ReportCacheRedisTest,WorkspaceCacheTest,ReportReadCacheTest,ReportCacheExportTest,ReportCacheIntegrationTest,ReportCacheWiringTest,ReportStage6Test,WorkspaceMenuTest' -Offline
./scripts/backend.ps1 -Goal test -Offline
git diff --check
```

缓存相关测试覆盖：

- 8 个视图隔离、租户/环境/日期/门店集合隔离、`[1,23]` 与 `[12,3]` 不混淆、93 天/100 店边界、非法 filter 与命名空间拒绝。
- 默认日期范围与等价显式日期同键。
- 配置默认值与 `application.yml` 完全一致、非法 TTL/jitter/namespace 启动失败、抖动区间与 1 秒下限。
- 命中不查库、全零/空 items 可缓存、`enabled=false` 完全不访问 Redis、GET 失败只查库一次且不写、NX 失败不重查、写失败仍返回结果、loader 异常不被吞。
- 真实 Redis：`BigDecimal 12.50`、`Long` 商品 ID、`Double` 完成率、嵌套 `ReportRow` 列表往返后 JSON 语义与精度一致；有限正 TTL、命中不续期、过期后按新数据重建；双实例共享；NX 保留较早快照；坏值按 miss 处理。
- 工作台：二次请求不再查聚合 Mapper；每次仍校验 `WORKSPACE_READ`，预热后撤权立即拒绝且不读缓存；无 `REPORT_READ` 的员工仍可访问；同店授权员工共享；跨店/跨租户隔离；`dish` 与 `setmeal` 不串；缺少 RequestScope 拒绝；固定时钟跨午夜换键换日期；Redis 宕机回查而数据库故障照原样抛出。
- 报表：二次命中；日期/集合/视图隔离；`top10.items` 完整；每日补零、排名、退款与客户 cohort 口径不变；预热后撤销门店权限整体拒绝；旧/伪造 filter、缺失身份、空门店、超限范围在 Redis 前拒绝；历史 118～120 秒与含今天 13～15 秒；历史状态变化在过期重建后可见。
- 导出：预热图表后改库再导出读到最新数据；导出不访问任何报表缓存键；撤权、超过 50000 事件、4 张 sheet 与明细保持原行为。
- HTTP 集成：缓存开关两种模式对同一数据库快照返回完全相同的响应体，权限检查继续执行，聚合 Mapper 调用由 24 次降到 12 次。

## 9. 已知限制

- 期间内管理端刷新可能看到旧展示值；这是接受的展示行为，财务对账请以导出（始终查库）或数据库为准。
- 仅 TTL 失效，交易变更不会立即刷新图表，最长滞后为基础 TTL（含抖动余量）。
- 首期无单飞/租约，热点冷启动可能同时多次查库。
- 未新增监控平台；日志仅记录视图名与故障类型，hit/miss 通过现有日志与 Mapper 调用量观察。
- 未做生产发布、业务库迁移、真实商户压测与前端预热改造。
