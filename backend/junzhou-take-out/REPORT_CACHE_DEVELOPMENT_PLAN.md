# 工作台与经营报表 Redis 缓存开发计划

> **交接给实现 agent：** 请使用 `superpowers:executing-plans` 按任务逐项实施；确需委派时可使用 `superpowers:subagent-driven-development`。用复选框记录实际完成情况。本次交付是开发文档，缓存功能尚未实现。

**目标：** 减少管理端刷新工作台、重复查询经营报表时的数据库聚合查询，同时保持租户隔离、逐店授权、原统计口径及导出一致性。

**架构：** 在 `ReportServiceImpl` 的授权检查之后，以 Cache-Aside 方式缓存最终展示结果：命中返回缓存，未命中查库并写入有限 TTL 的 Redis 键。首期只做 TTL 自动刷新，不增加交易变更监听、分布式锁、定时预热或通用缓存 AOP。

**技术栈：** Java 8、Spring Boot 2.7.3、Spring Data Redis、现有 MyBatis 报表 SQL、JUnit 5/Mockito/H2；复用现有 Redis 对象模板。

**需求规格：** 本文第 1～7 节定义本期行为，第 8 节为实施步骤，第 9～11 节为验收与交接。依据 2026-11-17 当前源码编写，实现前重新核对工作区。保留其他 agent 的登录、验证码、数据库迁移等已有改动。

## 1. 缓存范围和默认刷新时间

| 接口 | 服务方法 | 缓存内容 | 基础 TTL |
| --- | --- | --- | --- |
| GET `/admin/workspace/businessData` | `business()` | 今日营业额、退款、净收入、订单与客单价等摘要 | 15 秒 |
| GET `/admin/workspace/overviewOrders` | `overview()` | 今日订单当前状态分布 | 15 秒 |
| GET `/admin/workspace/overviewDishes` | `menu("dish")` | 当前门店菜品售卖状态计数 | 15 秒 |
| GET `/admin/workspace/overviewSetmeals` | `menu("setmeal")` | 当前门店套餐售卖状态计数 | 15 秒 |
| GET `/admin/report/turnoverStatistics` | `turnover(f)` | 日期序列、收入、退款、净收入 | 历史范围 120 秒；含今天/未来的范围 15 秒 |
| GET `/admin/report/ordersStatistics` | `orders(f)` | 下单、支付、完成、取消数量与完成率 | 同上 |
| GET `/admin/report/userStatistics` | `users(f)` | 当前筛选门店内首次下单客户的新增与累计 | 同上 |
| GET `/admin/report/top10` | `top10(f)` | 净销量排名，包含原 `items` 列表 | 同上 |

时间定义：

- 全部日期按 `Asia/Shanghai` 解释。沿用客户端含首尾日期，服务层转换为 `[from, until)` 的自然日区间。
- 当 `f.until <= 上海今天00:00` 时归类历史范围；否则使用短 TTL。未来日期查询维持现有允许行为，不借缓存功能新增禁止未来日期的限制。
- 在基础 TTL 上减去随机抖动：`base - random(0..min(jitterSeconds, base-1))`，默认 jitterSeconds=2。因此短缓存实际 13～15 秒，历史缓存 118～120 秒，最小 1 秒。
- TTL 在写入成功时开始，不在命中时续期；缓存快照的年龄还包括原查询耗时，不能承诺数据相对数据库绝对只滞后 15 秒。
- 跨午夜产生新自然日的工作台键；不允许把昨日日报结果当作今日结果。已在午夜前开始的单次请求可以按该请求捕获的日期完成，后续请求使用新日期。

**不缓存：** Excel 文件、`export(f)`、原始订单事件明细、对账数据、认证/权限判断、`ReportFilter`、其他订单列表接口。不是 Redis 持久统计或增量计数方案，不用 Redis 算金额、排行或替代 MySQL 数据源。

## 2. 现有代码与口径约束

路径相对仓库根目录 `D:\take-out`。下文缩写：

- `J = backend/junzhou-take-out/junzhou-server/src/main/java/com/junzhou`
- `R = backend/junzhou-take-out/junzhou-server/src/main/resources`
- `T = backend/junzhou-take-out/junzhou-server/src/test/java/com/junzhou/test`

| 现有文件 | 接入前必须了解 |
| --- | --- |
| `J/service/impl/ReportServiceImpl.java` | 报表方法目前每次先 `authorize(f,false)`，工作台先 `rbac.require(WORKSPACE_READ)`；保留这些检查，缓存代码只能放在之后 |
| 同上 `filter` | 默认日期范围为今天前30天至昨天；最多93天、100家门店，门店排序去重，并校验每一家门店的动作权限 |
| 同上 `export` | `@Transactional(readOnly=true,isolation=REPEATABLE_READ)`，再次校验读/导出权限，读取 daily/sales/ledger/itemDetails；必须保持整条导出路径绕开缓存 |
| `J/service/ReportService.java` | 公共服务合同及原统计含义；接口签名和返回字段不变 |
| `J/report/ReportFilter.java` | `tenant/stores/from/until`；不能仅相信调用方“已授权”，服务方法接收旧 filter 时仍需重新授权 |
| `J/report/ReportRow.java` | 当前未实现 Serializable；`top10.items` 内是此类型，不能只测试不包含 items 的缓存 |
| `R/mapper/ReportMapper.xml` | 已有隔离 SQL 和统计口径，首期不改 SQL；缓存命中主要应减少这里的查询 |
| `J/controller/admin/WorkspaceController.java` | 四个工作台接口均要求 WORKSPACE_READ；不能误要求 REPORT_READ 才允许工作台缓存命中 |
| `J/controller/admin/ReportController.java` | filter、读报表、导出分别校验；不要在 controller 最外层先返回缓存绕过 service |
| `J/service/RbacService.java` | `require` 与 `permits`，继续按原机制实时检查；不缓存权限结果 |
| `J/config/RedisConfiguration.java` | key为字符串，value默认JDK序列化；不修改全局序列化方式 |
| `J/cache/MenuCacheAspect.java`、`ScopedCache.java` | 用户端菜单缓存；不复用其版本号、键、失效操作，不因菜单变更扫描报表键 |
| `T/ReportStage6Test.java` | 已有日期、退款、跨店、导出权限和统计口径测试 |
| `T/WorkspaceMenuTest.java` | 已有真实 SQL 和 HTTP 工作台菜单数量测试 |
| `frontend/junzhou-admin-vue3/src/api/dashboard.ts`、`statistics.ts` | 原响应消费方；首期无需修改 API 或前端字段 |

口径必须保持：收款按支付时间，退款按本地确认成功时间；下单、支付、完成、取消为不同事件；新客户按筛选门店集合内首次下单统计，不是全局注册用户数；销量按现有支付及全额退款规则。历史范围的订单完成率等仍可能因当前状态、迟到事件、数据更正而变化，因此不能永久缓存或固定历史结果。

当前各图表与导出本来就是独立查询。引入展示缓存后，不承诺所有卡片来自同一快照，也不承诺当前缓存图表与刚生成的导出文件逐项即时相等。无新写入、缓存刷新后的结果必须和原数据库计算一致。

## 3. 文件结构和接口设计

新增文件均放在当前后端模块，不增加新 Maven 模块：

| 新文件 | 职责与接口 |
| --- | --- |
| `J/report/cache/ReportCacheProperties.java` | `@ConfigurationProperties(prefix="sky.report-cache")`，enabled、namespace、workspace/live/historical TTL和jitter；启动校验；`long baseTtlSeconds(ReportCacheKey.View view,ReportFilter filter,LocalDate today)`确定基础TTL |
| `J/report/cache/ReportCacheKey.java` | 定义8个视图枚举；`static String create(String namespace, View view, ReportFilter filter)`：只接收已规范化授权的过滤器，生成稳定键；workspace菜单kind必须有不同view |
| `J/report/cache/CachedReportResult.java` | `implements Serializable`，固定 serialVersionUID；schemaVersion=1和`Map<String,Object> data`，不缓存 Result、HTTP响应、身份或权限 |
| `J/report/cache/ReportResultCache.java` | `Map<String,Object> getOrLoad(String key,ReportCacheKey.View view,long ttlSeconds,Supplier<Map<String,Object>> loader)`；处理缓存命中、查库、写入、降级及抖动。enabled=false完全不调用Redis |
| `J/config/ReportClockConfiguration.java` | 提供命名为 `reportClock` 的 Clock bean，`Clock.system(ZoneId.of("Asia/Shanghai"))`，测试可替换为固定时钟 |

需要修改：

- `ReportServiceImpl`：引入 `ReportResultCache` 和 `@Qualifier("reportClock") Clock`，完成八个视图接入；把原计算提取为私有 loader，保留授权和原返回 Map 结构。
- `ReportRow`：实现 `java.io.Serializable`，显式 serialVersionUID；保留字段、SQL映射与JSON输出，不对金额做 double 转换。
- `application.yml`：追加第7节配置。
- 现有直接 `new ReportServiceImpl(mapper,rbac)` 的测试：当前在 `ReportStage6Test` 和 `WorkspaceMenuTest`。改用新构造函数并注入显式禁用缓存的测试实例及固定 Clock；不要用 null 依赖或 `if(cache==null)` 隐藏生产装配错误。

生产构造函数固定为：

```java
ReportServiceImpl(ReportMapper mapper, RbacService rbac,
                  ReportResultCache cache, @Qualifier("reportClock") Clock clock)
```

缓存组件构造函数：`ReportResultCache(RedisTemplate<String,Object> redis, ReportCacheProperties properties)`。禁用测试实例使用真实配置 enabled=false，加mock Redis模板；不需要引入本地缓存实现。

不要在含 `authorize` 的公共服务方法最外面直接加 `@Cacheable` 或 Around AOP：命中会跳过方法体的权限检查。不要为了缓存提取第二套统计SQL或把各端点组合成大聚合接口。

## 4. 权限、过滤器和键

### 4.1 执行顺序

历史报表每次执行：

1. 保留现有 HTTP 认证、RequestScope 和 RBAC。
2. 对 filter 做必要的结构验证和快照复制：tenant匹配当前可信上下文；日期非null、按自然日对齐、from<until且最多93天；门店非空、正ID、最多100家、排序去重。
3. 保留 `authorize(snapshot,false)`，每家门店当前都必须有 REPORT_READ，任一家无权则整次拒绝，不把请求静默改成“只查有权门店”。
4. 通过授权后生成缓存键，计算TTL，调用 `getOrLoad`。

`filter` 已校验过的HTTP调用同样重查权限；服务直接调用或持有旧 ReportFilter 的请求不能绕过。拒绝时不查询报表Redis键，不查询报表Mapper，不修改缓存。权限检查本身访问数据库属于正常开销。

工作台每次执行：`requireEmployeeTenantId/requireStoreId`、`rbac.require(WORKSPACE_READ)` → 捕获一次上海日期并创建今日 filter → 生成key → getOrLoad。当前门店范围继续由原RequestScope/RBAC校验。menu方法只接受dish/setmeal，非法kind在读缓存前拒绝。

`today`、TTL分类、缓存键与loader共用同一次捕获的 Clock/ReportFilter，不能分别调用 `LocalDate.now()` 导致跨午夜时键和SQL日期不一致。

### 4.2 键格式

```text
junzhou:dev:report-cache:v1:tenant:1:view:workspace-business:stores:{sha256}:from:20261117T000000:until:20261118T000000:zone:Asia_Shanghai
junzhou:dev:report-cache:v1:tenant:1:view:report-turnover:stores:{sha256}:from:20261101T000000:until:20261117T000000:zone:Asia_Shanghai
```

8个view固定为：`workspace-business`、`workspace-orders`、`workspace-dishes`、`workspace-setmeals`、`report-turnover`、`report-orders`、`report-users`、`report-top10`。

- stores先按正整数ID升序去重，以逗号连接，UTF-8取SHA-256小写十六进制。`[2,1,2]`与`[1,2]`相同，`[1,23]`与`[12,3]`不同。
- 不同租户、门店集合、日期边界、视图、环境都必须隔离；不要只用分类名或请求URL作key。
- 日期用规范化服务端边界，不用原始请求字符串；同范围默认值与显式日期应命中同一个key。
- 同租户、同门店集合、同日期、同视图的不同员工可共享展示数据，但每个员工每次必须重新授权；不缓存权限和员工对象。未来出现员工级字段裁剪时必须升级键版本并调整共享策略。
- 工作台单店与跨店报表使用不同view，不能因相同过滤器共用最终结果。
- 报表范围已明确门店集合时，不额外把当前选中门店放入key。切换当前门店但查询同一已授权集合应复用；默认单店查询则随RequestScope门店变化。
- 表达式或字段语义变更时升级 `report-cache:v1`，不复用旧schema；不使用 KEYS、SCAN、FLUSHDB 或无界 JVM Map 管理缓存。

## 5. 缓存行为和故障策略

`getOrLoad`顺序：

1. disabled时直接执行loader，不访问Redis。
2. 尝试GET；只有 `CachedReportResult`、schemaVersion=1、data非null且结构满足当前view返回合同才命中；合法全零/空items结果也可缓存。
3. 连接、超时、序列化错误或无效值按miss降级。Redis GET故障时本次直接回查数据库，不再尝试写入，避免一次请求重复等待超时。
4. **在捕获Redis异常的try/catch之外执行loader一次。** 数据库、权限或业务异常按原方式抛出，不写缓存，不用全零数据伪装成功。
5. 查库成功后，在独立try/catch中写入带TTL的完整信封，使用 `setIfAbsent(key,envelope,ttl,SECONDS)`，避免较晚结束的并发查询覆盖已有快照。不因NX失败重查数据库。
6. Redis写入/序列化失败仍返回本次数据库结果；只记录必要的view和故障类型，不打印缓存财务内容、员工token或完整请求。

默认故障策略是**回查数据库**，不是接口限流文档的503策略。仅当原数据库/业务链路也失败时，沿用原错误处理。不要改动全局异常处理器来吞掉其他依赖错误。

JDK序列化必须验证以下类型：BigDecimal、long/Long、Double完成率、字符串日期序列、`List<ReportRow>`。命中前后JSON字段、数值精度和类型兼容；不能把top10.items变成字符串或丢掉商品ID。使用现有应用控制的Redis，不接受客户端提供的序列化内容。

结构验证由视图对应的预期字段完成；至少验证顶层Map必需键及关键值类型，top10.items为ReportRow列表，不能仅凭 `instanceof Map` 返回明显不合约的数据。坏键可保留到TTL到期，首期不强制删除以免并发误删新值；本次以数据库结果返回即可。

并发边界：NX写只阻止覆盖，不能合并并发miss。首期允许同一热点冷启动时多次查库，不承诺“一百个并发只查一次”。TTL抖动只减少不同key同时到期，不是单热点防击穿。后续若压测证明需要，再单独设计有界等待、租约、降级策略；不得临时加未验证的全局锁。

本期只通过TTL失效，不监听下单、取消、支付、退款、商品变更，不扫描历史报表键。管理端刷新页面也可能在有效期内继续看到旧数据；这是明确接受的展示行为。不提供绕过缓存参数、强制刷新按钮或新响应字段。

## 6. 导出与交易正确性

- `export(f)`保留原事务、权限、50000事件/明细上限、工作簿和字段顺序。
- 导出不调用 `getOrLoad`；原 `days`、`cohortRate` 等底层计算助手本身不缓存，缓存只包在指定公开展示方法内。
- 不把public方法通过self调用作为缓存机制；显式私有loader即可。不要让export经重构调用已缓存的 `turnover/orders/top10`。
- 展示缓存不用于下单计价、结算、退款或对账决策；数据库写入路径完全不接入本缓存。
- 工作台读取不应突然要求REPORT_READ；导出仍需REPORT_READ和REPORT_EXPORT。
- 用户端菜单缓存、将来可能实现的接口限流各有独立键和故障策略，三者互不复用版本或开关。

## 7. 配置、发布与监测

在现有 `sky` 下合并以下字段，不追加第二个同名顶层sky：

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

校验：namespace为`[A-Za-z0-9_-]{1,32}`，三个TTL都在1～3600秒，jitter在0～60秒。无效配置启动失败；抖动按第1节公式裁剪，不让TTL变为0。默认开启，生产明确使用prod命名空间，同一部署实例必须一致。属性变更重启生效，不承诺热更新。

配置校验：三种基础TTL均在1～86400秒内，jitterSeconds在0～86400之间，namespace匹配`[A-Za-z0-9_-]{1,32}`；非法配置启动失败，不静默归零或设为永久缓存。

回退：`REPORT_CACHE_ENABLED=false`并重启全部实例，服务直接查库，旧键按TTL自然回收，无数据库迁移，不清空Redis。若改TTL，不承诺已存在键立即采用新时长。

复用已有日志/指标基础设施观察hit/miss、Redis失败、loader耗时，不新增监控平台；禁止按每个命中请求输出INFO日志。验证SQL减少时只计算报表聚合Mapper，不能宣称缓存消除了认证/RBAC查询。

## 8. 开发任务

每项任务执行“写测试并确认失败 → 实现 → 测试通过 → 检查差异”。例行选择按本文执行，无需逐文件确认。提交、worktree沿用当前项目约定，勿覆盖其他agent的未提交工作。

### 任务一：配置、键和时钟

**新增：** ReportCacheProperties、ReportCacheKey、ReportClockConfiguration，位置见第3节。

**测试：** `T/ReportCacheKeyTest.java`、`T/ReportCachePropertiesTest.java`。

- [x] 写失败测试：8个view互相隔离；租户/环境/日期/门店集合不同key不同；门店排序和重复不影响规范key；默认日期和显式同范围相同。
- [x] 写失败测试：非法filter不能生成key；93天/100店边界及正ID；`[1,23]`与`[12,3]`不混淆。缺失身份在任务三、四的服务测试中验证。
- [x] 写失败测试：固定上海Clock验证历史/含今天TTL分类，昨天结束的until恰等于今天00:00使用历史TTL；跨年、跨月日期和午夜不会混用。
- [x] 配置默认值与YAML完全相同；非法TTL/namespace/jitter启动校验失败；抖动不超过base且不低于1秒。
- [x] 实现本任务文件；ReportCacheKey只做确定性编码，授权留在服务层，不在工具类中猜测员工权限。
- [x] 运行这两个测试并记录结果。

**后续依赖接口：** `ReportCacheKey.create(namespace,view,normalizedFilter)`及Properties；测试使用可注入Clock，不依赖测试机器时区或真实午夜。

### 任务二：Redis结果缓存与真实序列化

**新增：** CachedReportResult、ReportResultCache；修改ReportRow实现Serializable。

**测试：** `T/ReportResultCacheTest.java`、`T/ReportCacheRedisTest.java`。

- [x] 单元测试：首次查库、二次命中、全零报表和空items可缓存；disabled不访问Redis；Redis GET失败loader只调用一次且本次不写Redis。
- [x] 单元测试：SETNX返回false不重查；Redis写失败仍返回数据库结果；数据库抛错不得被缓存catch吞掉；null或坏schema不命中。
- [x] 真实Redis验证BigDecimal如12.50、Long商品ID、Double完成率、嵌套ReportRow列表完整往返，至少比较命中前后API JSON语义和精度。
- [x] 真实Redis验证所有写入有有限正TTL，命中不续期，过期后按新数据库结果重建；不要用生产默认120秒sleep，测试覆盖配置为短TTL并有有限截止时间。
- [x] 双实例缓存组件共享Redis验证：实例A写入，实例B命中；不同key不串。并发情况下NX保证已有快照不被较晚写覆盖，但不要求loader单次执行。
- [x] 使用随机专用环境前缀和测试key，finally只删测试自建键；不FLUSHDB，不扫描业务库，不打印或提交本地Redis密码。
- [x] 运行新测试，确认不仅mock场景通过，也通过真实模板的JDK序列化。

**产物接口：** 第3节getOrLoad，显式接收View用于结果结构验证。所有调用方传入与key一致的View，不从任意key字符串反射或猜测类型。

### 任务三：工作台四个视图接入

**修改：** ReportServiceImpl构造函数、business/overview/menu/today；适配已有WorkspaceMenuTest和ReportStage6Test的构造调用。

**测试：** `T/WorkspaceCacheTest.java`，保留现有WorkspaceMenuTest。

- [x] 测试四个视图第二次相同查询不再访问相关ReportMapper，原字段、零数据、金额、上下架计数保持。
- [x] 每次命中仍验证WORKSPACE_READ；预热后撤权，下一次拒绝且不读缓存；有工作台权限但无REPORT_READ的员工仍能正常访问。
- [x] 同店不同已授权员工复用；跨租户、跨店隔离；menu(dish)与menu(setmeal)不串；缺少RequestScope直接拒绝。
- [x] 固定Clock模拟上海23:59:59至次日00:00:01，新请求用新的from/until和key；同一loader不重新计算另一日期。
- [x] 按“授权→捕获filter→key→cache→私有loader”接入，保持原controller API和SQL不变。
- [x] Redis离线工作台回查，数据库错误仍走原错误；已缓存结果不得绕过原权限存储故障。
- [x] 运行WorkspaceCacheTest、WorkspaceMenuTest、ReportStage6Test。

### 任务四：经营报表四个视图与导出回归

**修改：** ReportServiceImpl的turnover/orders/users/top10及必要私有规范化辅助方法，不改ReportMapper.xml。

**测试：** `T/ReportReadCacheTest.java`、`T/ReportCacheExportTest.java`，保留ReportStage6Test原断言。

- [x] 相同规范过滤器第二次命中；不同日期/集合/view隔离；top10.items完整；原每日补零、排名、退款及客户cohort口径保持。
- [x] 预热包含门店1、2的缓存后，撤销门店2权限：下一次整体拒绝；换另一无权员工也拒绝，不能返回授权子集或沿用原员工检查结果。
- [x] 持有旧ReportFilter、伪造tenant、缺失身份、空门店或超限范围都在Redis前拒绝；直接调用service也不能绕过。
- [x] 验证历史120秒和含今天15秒配置选择，抖动裁剪正确；历史状态变化在过期重建后可见，不声明永久冻结。
- [x] 预热图表后修改数据库并立即导出：导出必须读到其原事务允许的最新数据；图表允许在TTL内保留旧值。验证export完全不访问ReportResultCache或报表Redis键。
- [x] 导出撤权、超过50000事件、4张sheet及明细原测试继续通过；不要为了“图表导出一致”把导出换成缓存内容。
- [x] 运行本任务新测试和既有报表测试。

### 任务五：集成、文档与回归

**新增说明：** `backend/junzhou-take-out/REPORT_CACHE.md`，更新README相关入口；不要覆盖同目录限流开发计划。

- [x] 文档列出8个接口、TTL/抖动、权限重查、导出绕过、故障回查、回退开关及并发miss边界。
- [x] 有真实Redis的HTTP/服务集成测试比较缓存开关两种模式：同一数据库快照返回语义相同，授权检查继续执行，聚合SQL调用明显减少；用Mapper次数作证据，不凭响应耗时猜测。
- [x] 正常返回不增加新字段、缓存标记或更新时间，不在前端展示实现细节。无需业务前端改造，仍运行现有dashboard/statistics测试确认兼容。
- [x] 完成第9、10节验收并记录实际结果；`git diff --check`；审查没有修改交易、权限缓存或其他agent正在工作的文件。
- [x] 交接实际文件、测试数量/跳过/失败、配置、未执行项与限制；不复制历史测试结果冒充本次结果。

## 9. 测试命令

仓库根目录PowerShell，使用机器已安装的JDK/Maven及现有脚本；若机器要求JAVA_HOME，设置为真实安装路径，不把机器路径写入仓库配置：

```powershell
./scripts/redis.ps1 -Action start
./scripts/backend.ps1 -Goal test -Test 'ReportCacheKeyTest,ReportCachePropertiesTest,ReportResultCacheTest,ReportCacheRedisTest,WorkspaceCacheTest,ReportReadCacheTest,ReportCacheExportTest,ReportStage6Test,WorkspaceMenuTest' -Offline
./scripts/backend.ps1 -Goal test -Offline
git diff --check
```

管理端目录 `frontend/junzhou-admin-vue3`：

```powershell
npm run test:unit
npm run type-check
```

若实际修改前端，再执行npm run lint/build。不开启业务预热、不跑真实商户支付压测，不需要为这项纯后端展示缓存修改小程序。缺本地Redis、网络权限、Maven离线依赖时明确记录阻塞原因，不能把mock测试写成真实集成验证。

## 10. 验收清单与回退演练

- [x] 所有8个接口在同范围二次请求有命中，金额和响应JSON语义与原计算一致；聚合Mapper少查，权限查询仍存在。
- [x] 同店授权员工共享，跨租户/门店/范围隔离；预热后撤权立即在下一次权限检查拒绝。
- [x] 工作台15秒、历史报表120秒基础TTL及向下抖动可观测；命中不延期；上海跨午夜正确。
- [x] 零业务、净收入负数、全部退款、同名不同商品ID、无排名数据、多个门店集合均保持原语义。
- [x] Redis连接和序列化故障可回查；数据库/权限故障保持原错误，不返回伪造成功或兜底全零。
- [x] 已有菜单缓存与可能存在的限流功能不受影响；不通过共用开关顺便关闭别的Redis功能。
- [x] 导出始终查库，原事务与读/导出权限保留，不能把过期财务展示值用于对账。
- [x] 配置关闭后重启，直接查库且无报表Redis访问；旧键自然到期，无全库清理或迁移。

注意：停整个Redis会同时影响本项目营业状态等已有依赖。测试缓存降级时优先对报表缓存组件单独注入故障，不要把其他功能的连带失败误认成报表缓存无法降级。

## 11. 参考与 agent 交接指令

实现模式参考 [Redis 官方 Cache-Aside 说明](https://redis.io/docs/latest/develop/use-cases/cache-aside/)。本计划TTL、权限顺序、导出边界为依据本项目代码制定的具体要求，不是Redis自动提供的保证。

可直接发送给实现agent：

> 请阅读 `backend/junzhou-take-out/REPORT_CACHE_DEVELOPMENT_PLAN.md`，核对当前ReportServiceImpl、统计口径与其他agent未提交变更，按第8节逐项实现工作台与经营报表Redis缓存。严格保持“每次先授权，再读取缓存”；导出、交易与菜单缓存逻辑保持原职责。本文默认规则直接执行，无需重新询问例行实现选择。完成新测试、真实Redis验证和既有回归，报告实际结果、配置与限制，不直接发布生产。若发现当前源码与本文有影响权限或财务口径的冲突，先定位证据并说明。

## 12. 实施记录（2026-11-17）

**交付：** 工作台与经营报表 8 个视图的 Redis 展示缓存已实现并通过本地验证；导出、交易、菜单缓存与权限判断保持原职责。

**新增文件：**

- `junzhou-server/src/main/java/com/junzhou/report/cache/ReportCacheProperties.java`
- `junzhou-server/src/main/java/com/junzhou/report/cache/ReportCacheKey.java`
- `junzhou-server/src/main/java/com/junzhou/report/cache/CachedReportResult.java`
- `junzhou-server/src/main/java/com/junzhou/report/cache/ReportResultCache.java`
- `junzhou-server/src/main/java/com/junzhou/config/ReportClockConfiguration.java`
- `REPORT_CACHE.md`（配置、键、故障策略、回退与限制说明）
- 测试：`ReportCacheKeyTest`、`ReportCachePropertiesTest`、`ReportResultCacheTest`、`ReportCacheRedisTest`、`WorkspaceCacheTest`、`ReportReadCacheTest`、`ReportCacheExportTest`、`ReportCacheIntegrationTest`、`ReportCacheWiringTest`，以及测试支持类 `MutableClock`、`InMemoryRedis`、`CountingReportMapper`、`ReportTestDatabase`、`ReportTestSupport`

**修改文件：** `ReportServiceImpl`（四参构造函数 + 8 视图接入 + 私有 loader）、`ReportRow`（`Serializable`）、`application.yml`（`sky.report-cache`）、`ReportStage6Test` 与 `WorkspaceMenuTest`（新构造函数、显式禁用缓存、固定时钟）、`README.md`（入口链接）。

**验证结果（本次实际执行，未沿用历史数字）：**

- 缓存相关测试：`./scripts/backend.ps1 -Goal test -Test '...11个缓存与报表类...' -Offline` → **74 项通过，0 失败，0 错误，0 跳过**（含真实 Redis 的 `ReportCacheRedisTest` 与 HTTP 集成 `ReportCacheIntegrationTest`）。
- 后端全量回归：在工作区副本上执行全量 `test` → **616 项，0 失败，0 错误，1 项条件跳过**（`CatalogMySqlIsolationTest` 需真实 MySQL）。使用副本是因为同一工作区有其他 agent 并发执行构建，避免 target/ 与 surefire 报告互相覆盖；副本源码与当时工作区一致。
- 管理端：`npm run test:unit` → **164 项通过**；`npm run type-check` → 通过。本次未改前端。
- `git diff --check` → 无空白错误。
- 集成证据：同一数据库快照下缓存开启与关闭的 8 个接口响应体完全一致；两轮请求的聚合 Mapper 调用由 24 次降到 12 次；工作台权限检查在两种模式下每次执行；预热后撤权返回 403。

**实施中发现并修复的问题：** `top10` 原实现把 `List.subList` 视图放进返回 Map，JDK 序列化抛出 `NotSerializableException`，缓存写入被静默降级（真实 Redis 集成测试发现）。已改为复制为 `ArrayList`，并新增“每个视图载荷可直接 JDK 序列化”的回归测试。

**未执行项与限制：** 未在真实 MySQL 业务库上运行缓存集成（H2 MySQL 模式）；未做生产部署、真实商户支付压测、缓存预热与命中率压测；未监听交易变更（仅 TTL 失效）；未提供强制刷新或绕过参数。
