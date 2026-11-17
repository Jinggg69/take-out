# Redis 接口限流与防频繁操作开发计划

> **交接给实现 agent：** 按任务逐项实施，可使用 `superpowers:executing-plans`；需要委派时使用 `superpowers:subagent-driven-development`。以下步骤使用复选框跟踪。本次交付仅为开发文档，不代表功能已经实现。

**目标：** 给现有登录、催单、支付主动核查、报表导出增加跨实例共享的频率保护，并让管理端和小程序正确处理等待与重试。

**架构：** 复用现有 Redis 连接，使用独立的 `StringRedisTemplate` 和单键 Lua 脚本完成原子判断、计数及过期。通用组件只负责配额，身份和资源归属仍由现有业务代码验证；在明确的业务位置调用，不先开发通用 SpEL 注解框架。

**技术栈：** Java 8 字节码、Spring Boot 2.7.3、Spring Data Redis、Spring MVC、JUnit 5、Mockito；Vue 3/TypeScript 与 uni-app。

**需求规格：** 本文第 1～7 节是本期规格；第 8 节是实施步骤；第 9～11 节是验收、发布及交接要求。核对基准为 2026-11-17 当前工作区，实现前重新读取相关文件，保留其他 agent 已有变更。

## 1. 范围与约束

首期只实现以下四类场景，规则值是本次建议的可配置默认值，不是已经观测到的生产容量。

| 规则 ID | 实际后端接口 | 限流主体 | 默认规则 |
| --- | --- | --- | --- |
| login-ip | POST `/admin/employee/login` | 来源 IP | 每 60 秒最多接受 60 次登录尝试，成功、失败均计数 |
| login-failure | 同上 | 来源 IP + 规范化账号名 | 从第一次凭证失败开始，300 秒内记录最多 5 次失败；达到 5 次后，后续尝试在密码校验前被拒绝 |
| order-reminder | GET `/user/order/reminder/{id}` | 租户 + 门店 + 用户 + 订单 | 60 秒内最多接受 1 次催单 |
| payment-confirm | POST `/user/order/{id}/payment/confirm` | 租户 + 门店 + 用户 + 订单 | 2 秒内最多接受 1 次主动核查 |
| report-export | GET `/admin/report/export` | 租户 + 员工 | 从第一次接受导出开始，60 秒内最多接受 2 次导出；切门店或改日期不能重置额度 |

窗口定义：除登录失败保护的“检查、记录”两阶段外，其余规则均使用**从首次接受请求开始计时的固定窗口**。不是任意滑动 60 秒限制，也不承诺“最多两个正在运行的导出”。窗口边界可能出现短时突发，本期接受；严格滑动窗口、导出并发任务队列属于后续需求。

全局约束：

- 不改变现有接口路径、HTTP 方法、成功响应和 Token 请求头；催单现有 GET 方法本期保留。
- 不修改数据库表，不增加 Redisson、网关、消息队列或新 Redis 服务依赖，不要求 Redis 8 专用命令。
- 不将菜单缓存的 JDK 序列化 `RedisTemplate<String,Object>` 改成字符串模板；为限流使用独立的字符串操作。
- 不改订单请求号幂等、唯一索引、购物车用户行锁、支付退款状态机和补偿任务。
- 不给 `/notify/**` 微信通知或后台支付恢复任务套用用户接口限流；不在共享 `reconcileOrder` 方法里统一限流。
- 不给所有接口加全局默认额度；本期不限制下单、加减购物车、菜单浏览、退出、修改密码、小程序微信登录。
- 不信任客户端传入的 tenantId、userId、employeeId，不从未验证的 JWT 载荷取身份。
- 业务失败不退还已接受请求的额度；催单占额后推送失败也保留冷却时间。它是频率保护，不是消息送达保证。
- 本计划允许实现 agent 自行完成例行实现、测试、修复；不要求每个文件或步骤再询问确认。发布到外部环境按现有授权处理。

## 2. 已有代码与接入位置

路径均相对仓库根目录 `D:\take-out`。为缩短列表，下文约定：

- `S = backend/junzhou-take-out/junzhou-server/src/main`
- `T = backend/junzhou-take-out/junzhou-server/src/test/java/com/junzhou/test`
- `J = S/java/com/junzhou`

| 现有文件 | 必须理解的行为与接入要求 |
| --- | --- |
| `J/controller/admin/EmployeeController.java` | `login` 先尝试 `platformAuth.loginIfPlatform`，返回 null 才调用员工 `loginWithSession`。保护整个分支，只计算一次最终失败；平台分支的 null 不是登录失败 |
| `J/service/impl/EmployeeServiceImpl.java` | `login` 区分账号不存在、密码错误、账号禁用；`loginWithSession` 包含持久会话签发。不能把签发故障算成密码错误 |
| `J/platform/PlatformAuthService.java` | 平台登录凭证异常使用 `LoginFailedException`；会话仍保持原有事务和撤销机制 |
| `J/config/WebMvcConfiguration.java` | 管理员/用户 JWT、RequestScope、RBAC 已有顺序。首期无需新增全局拦截器，避免限流先于认证执行 |
| `J/service/impl/OrderServiceImpl.java` | `confirmPayment` 已先 `requireOrder`、`requireUserOrder`，随后 `paymentRecovery.reconcileOrder`；限流放在归属检查后、外部调用前 |
| 同上 | `reminder` 当前直接 `orderMapper.getById` 再推送；实现时显式复用 `requireOrder(id,false)` 与 `requireUserOrder` 后再占额度，不能只依赖客户端订单 ID |
| `J/controller/admin/ReportController.java` | `export` 先 `reports.filter(...,true)` 完成日期、门店、权限校验；之后占额度，再调用 `reports.export` 生成文件 |
| `J/handler/GlobalExceptionHandler.java` | 业务 `BaseException` 默认可能返回 HTTP 200；必须为限流和限流依赖故障增加明确的 HTTP 响应处理器 |
| `J/config/RedisConfiguration.java` | 普通对象模板保留默认 JDK value 序列化；不要用它执行需要十进制字符串参数的计数 Lua |
| `frontend/junzhou-admin-vue3/src/utils/http.ts` | 管理端 Axios 拦截器；已有 401 处理、重复请求取消和门店切换保护，必须保留 |
| `frontend/junzhou-admin-vue3/src/utils/request.ts`、`platform-request.ts` | 检查上层错误包装，避免错误提示重复或丢失状态码 |
| `weixin-uniapp/utils/request.js` | 小程序公共请求封装；保留身份、门店代次判断，以及 401 处理 |
| `weixin-uniapp/pages/pay/index.vue` | 主动核查最多 6 次，默认间隔 2000ms；有限流时需要专门等待，不能走“支付失败”分支 |
| `weixin-uniapp/tests/order-payment.test.cjs` | 沿用现有定时器、轮询退出和支付恢复测试习惯 |

现有菜单缓存为 `MenuCacheAspect`、`ScopedCache` 及其配置；本功能不得改变它们的键、TTL 或故障策略。

## 3. Redis 组件与键设计

### 3.1 文件与接口

新建以下文件，不需要先创建空壳，按第 8 节任务逐项落地：

| 文件 | 职责 |
| --- | --- |
| `J/ratelimit/RateLimitProperties.java` | `@ConfigurationProperties(prefix="sky.rate-limit")`，总开关、环境命名空间、各规则阈值；启动校验正数与合法命名空间 |
| `J/ratelimit/RateLimitDecision.java` | Java 8 普通不可变类：`boolean allowed`、`long retryAfterMillis`；不得使用 record |
| `J/ratelimit/RedisRateLimiter.java` | 字符串模板执行 Lua、解析结果；`acquire(String key,int limit,long windowMillis)`；`checkFailures(String key,int limit)`；`recordFailure(String key,int limit,long windowMillis)`，均返回 `RateLimitDecision` |
| `J/ratelimit/RateLimitGuard.java` | 业务入口：`checkLogin(String ip,String username)`、`recordLoginFailure(String ip,String username)`、`reminder(Long orderId)`、`paymentConfirm(Long orderId)`、`reportExport()`；组装规则键和转换拒绝异常 |
| `J/ratelimit/ClientIpResolver.java` | `String resolve(HttpServletRequest request)`；默认只使用 `getRemoteAddr()` |
| `J/ratelimit/LoginAttemptGuard.java` | 包装整个登录分支：`EmployeeLoginVO login(EmployeeLoginDTO input,HttpServletRequest request,Supplier<EmployeeLoginVO> authenticate)` |
| `J/ratelimit/RateLimitExceededException.java` | 规则 ID、等待毫秒数、用户可读消息；使用专门异常避免被通用异常转换为 200 |
| `J/ratelimit/RateLimitUnavailableException.java` | 限流存储不可用、脚本返回异常等；对外统一 503，不包含内部 Redis 信息 |
| `S/resources/redis/rate-limit-acquire.lua` | 原子占额度 |
| `S/resources/redis/login-failure-check.lua` | 原子读取失败计数与剩余 TTL |
| `S/resources/redis/login-failure-record.lua` | 原子记录一次失败，计数封顶，首次设置 TTL |

`RateLimitGuard` 负责 enabled 开关；disabled 时完全不访问 Redis，但必须继续正常认证与授权。

### 3.2 命名空间与隔离

示例键：

```text
junzhou:dev:rl:v1:login-ip:{ipDigest}
junzhou:dev:rl:v1:login-failure:{ipAndUsernameDigest}
junzhou:dev:rl:v1:tenant:1:store:2:user:10:order:888:reminder
junzhou:dev:rl:v1:tenant:1:store:2:user:10:order:888:payment-confirm
junzhou:dev:rl:v1:tenant:1:employee:5:report-export
```

- `dev` 来自必配或有明确默认值的环境配置；生产使用独立 `prod`，同一部署各实例必须相同。
- 登录尚未有可信租户上下文，所以不接收客户端租户 ID 拼键；账号来源依赖当前统一登录协议。本期不重构租户登录。
- 账号仅为拼键执行 `trim().toLowerCase(Locale.ROOT)`，不得把规范化后的值替换认证输入。此规则只会让账号别名共用更严格的额度。
- 用 SHA-256 摘要隐藏键内的原始账号/IP；组合输入用长度前缀或结构化编码，不能用可歧义的直接字符串拼接。摘要并非加密，不作匿名化承诺。
- 账号输入长度大于 128 或为空的无效请求不创建账号专属键，仍受 IP 总额度保护；保持项目原有参数验证行为，不截断账号后继续登录。
- 业务身份通过 `RequestScope.requireTenantId/requireStoreId/requireUserId` 和当前员工身份取得；先验证上下文，再构造键。
- 每个键都必须有 TTL，不使用 KEYS/SCAN 处理限流，不增加无界 JVM Map 作为替代，不打印账号、密码、JWT、微信 code。

### 3.3 原子算法约定

`acquire` 的一个脚本完成：

1. 验证 limit、windowMillis 为正数；读取当前计数。
2. 当前计数达到 limit：返回拒绝和当前 PTTL，不递增，不延长过期时间。
3. 否则 INCR；首次创建时 PEXPIRE，返回接受和剩余 TTL。
4. 返回格式统一为两个整数 `[1或0, ttlMillis]`。

读取已有计数却发现键没有 TTL、值不是有效非负整数，或脚本返回 null/不合法结构，应视为限流存储故障返回 503，不能无限放行或返回负等待时间。Lua 在写入前完成必要校验；脚本报错不会自动回滚之前的命令。

`checkFailures` 不增计数；不存在时允许；达到 5 时拒绝；未达到时允许。`recordFailure` 首次记 1 并设置 300000ms，后续只递增到上限 5，不刷新 TTL。首次返回 5 的那次错误仍返回原凭证错误，后续请求才 429。

这是“窗口内失败次数”，不是“连续失败次数”：成功登录不清空窗口计数，避免并发成功请求清掉刚记录的失败。达到上限之前已经通过检查的并发请求可能继续完成密码校验；文档和测试必须保留这个边界，不能宣称严格最多只有 5 次密码计算。IP 总请求额度用于限制并发尝试规模。

每个脚本只访问一个 key，便于 Redis Cluster；不要用 GET 判断后在 Java 单独 INCR/EXPIRE。Lua 参数和结果明确用字符串/整数序列化；不能让对象模板把数字编码成 Java 序列化二进制。

### 3.4 来源 IP

首期默认仅使用 `request.getRemoteAddr()`；不直接相信 `X-Forwarded-For`、`X-Real-IP` 或 `Forwarded`。

本地代理场景可能把所有浏览器归为同一来源，这会共享 IP 额度，必须在部署说明中写明。生产若需要真实来源，部署方必须先在明确的可信反向代理/容器配置中完成来源解析、覆盖客户端伪造头并阻止直连绕过；再验证应用看到的 remoteAddr。此能力未配置时保持 remoteAddr，不自行取转发头最左项。不要把 Vite、Nginx 或公网代理地址无条件设为“可信所有”。

## 4. 业务接入顺序

### 4.1 登录

`EmployeeController.login` 通过 `LoginAttemptGuard.login` 包装原有完整认证 Supplier，保留平台分支与员工分支。

执行顺序：

1. 取得来源 IP，检查/消耗 IP 尝试额度。
2. 对有效账号输入检查失败计数，已达到阈值则返回 429，不执行认证 Supplier。
3. 调用原有平台/员工完整认证与会话签发，成功按原响应返回。
4. 只捕获最终凭证错误类别：员工的 `AccountNotFoundException`、`PasswordErrorException`、`AccountLockedException`；平台认证分支的 `LoginFailedException`。
5. 员工会话签发也可能抛 `LoginFailedException`，不可仅凭基类全量计数。实现时在认证分支明确标识凭证错误，必要时抽取不改变响应的异常子类/内部分类方法；测试区分平台密码错误与员工签发时账号变化。
6. 记录一次失败，再抛出原有凭证异常。数据库故障、输入解析错误、Redis 故障、会话签发失败不记作密码失败。

平台认证返回 null 时只继续员工认证，不计失败。不得通过扩大 AOP 切点把两个认证函数各计一次，也不得把原登录行为改成新建第二套会话。

如果记录失败时 Redis 故障，对外按第 5 节返回 503，不返回认证内部信息。

### 4.2 催单

`OrderServiceImpl.reminder`：有效 ID及订单查询 → 当前租户/门店与本人归属检查 → `guard.reminder(id)` → 原 WebSocket 推送。

对他人、跨店、跨租户订单必须沿用拒绝行为，不推送、不为该订单占额度。现有状态行为保持，不借本功能新增可催单状态枚举。

### 4.3 支付主动核查

`confirmPayment`：保留 `requireOrder`、`requireUserOrder` → `guard.paymentConfirm(id)` → 原 `reconcileOrder` → 原订单返回。

拒绝时不得调用微信或支付恢复服务。不能把限流放到支付通知、后台恢复或共享支付状态更新方法中。原查单数据库租约、支付幂等继续生效；本期 Redis 只减少接口入口的频繁操作。

### 4.4 导出

`ReportController.export`：先 `reports.filter(begin,end,storeIds,true)` → `guard.reportExport()` → `reports.export(f)` → 原二进制响应。

参数无效或无导出权限不得占额度。第 3 次拒绝时不生成工作簿；错误响应是 JSON，不能带 `.xlsx` 的 Content-Disposition，不能返回伪造 Excel 文件。

## 5. HTTP 响应、故障及前端行为

### 5.1 返回协议

限流使用 HTTP 429，同时保持现有 Result 结构：

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 42
Cache-Control: no-store

{"code":0,"msg":"操作太频繁，请42秒后再试","data":null}
```

`Retry-After` 为 `max(1, ceil(retryAfterMillis/1000))`。数据字段是否显式输出 null 沿用现有 Jackson 行为，客户端不得依赖 null 字段一定存在；不新增与 Result 不兼容的顶层结构。

`GlobalExceptionHandler` 为两种限流异常增加精确处理器。429 不触发退出，不发成功提醒，不用通用 BaseException 返回 200。真实跨域部署若要让浏览器读 Retry-After，仅在既有明确跨域配置下暴露该响应头，不新增任意来源的宽松 CORS。

### 5.2 Redis 不可用

四类规则统一**拒绝受保护操作并返回 HTTP 503**，`Result.error("操作保护服务暂不可用，请稍后重试")`，`Retry-After: 3`、`Cache-Control: no-store`；不伪装 429，不退化为每实例独立额度。

适用于连接/超时、Lua 执行故障、序列化故障、协议异常；精确捕获并映射，不吞掉业务异常。登录不签发新会话、催单不推送、导出不生成文件、用户主动核查不调用支付提供方。已经登录的其他接口、微信通知、后台支付恢复不受这个限流组件影响。

限流不会回滚已经完成的业务操作；所有 acquire 在业务副作用之前完成。现有 Redis 网络超时配置可以复用，不增加循环重试以免接口阻塞。运维可关闭限流总开关作为明确降级，必须记录风险：关闭后额度保护失效。

### 5.3 管理端与小程序

- 公共请求层为拒绝对象保留 `statusCode/status`、`retryAfterSeconds`、`msg`；只为 429 以及带保护重试信息的 503 生成明确提示，不能误登出。
- 保留现有请求去重、门店切换取消、过期响应忽略以及加载状态释放；重复弹窗只保留一处。
- 催单按钮按 Retry-After 倒计时禁用；退出页面清理定时器；重新进页服务端仍能拒绝，不能仅靠按钮防抖。
- 登录和导出遇限流只提示、允许用户稍后主动操作；不自动重放登录、导出、下单等业务请求。
- 支付核查的 429 是“等待核查”，不是支付失败或付款成功。按 `max(2000ms, Retry-After*1000)` 等待；429 不消耗原有 6 次实际核查额度，但新增从本轮核查开始的 60 秒总预算，避免一直被限流后无限轮询。
- 保护 503 的核查也可按 Retry-After 等待，同样服从 60 秒总预算。超过预算显示“支付结果暂未确认，请稍后查看订单”；不显示“支付失败”，不自动创建订单、取消订单或退款。
- 离页、门店切换、会话变化立即终止等待；恢复页面后沿用已有恢复逻辑。等待时间超过剩余预算则直接停止，不必先等待超出预算。
- 管理端导出可能以 Blob 接收响应：429/503 的 JSON Blob 必须读取并展示 msg，不能当成 Excel 下载；实现前核对现有下载工具具体位置。

## 6. 配置约定

在 `S/resources/application.yml` 中追加，不覆盖现有 redis/menu-cache/payment 配置：

```yaml
sky:
  rate-limit:
    enabled: ${RATE_LIMIT_ENABLED:true}
    namespace: ${RATE_LIMIT_NAMESPACE:dev}
    login-ip:
      limit: ${RATE_LIMIT_LOGIN_IP_LIMIT:60}
      window-millis: ${RATE_LIMIT_LOGIN_IP_WINDOW_MS:60000}
    login-failure:
      limit: ${RATE_LIMIT_LOGIN_FAILURE_LIMIT:5}
      window-millis: ${RATE_LIMIT_LOGIN_FAILURE_WINDOW_MS:300000}
    reminder:
      limit: ${RATE_LIMIT_REMINDER_LIMIT:1}
      window-millis: ${RATE_LIMIT_REMINDER_WINDOW_MS:60000}
    payment-confirm:
      limit: ${RATE_LIMIT_PAYMENT_CONFIRM_LIMIT:1}
      window-millis: ${RATE_LIMIT_PAYMENT_CONFIRM_WINDOW_MS:2000}
    report-export:
      limit: ${RATE_LIMIT_REPORT_EXPORT_LIMIT:2}
      window-millis: ${RATE_LIMIT_REPORT_EXPORT_WINDOW_MS:60000}
```

`RateLimitProperties` 内部结构、默认值和 YAML 必须一致；namespace 只接受 `[A-Za-z0-9_-]{1,32}`。阈值与窗口必须为正且可安全传入 Redis，不因配置负数而永久封锁用户；非法配置启动失败。配置变更通过重启生效，不承诺实时热更新；旧键按原 TTL 自动到期。

## 7. 重点审查项

以下五项必须在对应任务中有测试：

1. 未认证、跨租户/跨门店或他人订单请求不得越权，也不得污染合法用户额度。
2. 多实例并发不能通过 Java 的先读后写绕过额度；所有计数键均有 TTL，拒绝不能延长窗口。
3. 平台登录 fallback、员工会话签发错误不得误算或重复算凭证错误；登录成功不清空并发失败。
4. Redis 故障返回 503，与正常 429 区分，且不发起业务副作用；不能影响微信通知与后台恢复。
5. 支付 429 等待、页面离开、总时间预算及导出 JSON Blob 错误必须正确处理，不能误报支付结果或下载错误文件。

## 8. 分步骤实施

每个任务按“先测试失败 → 实现 → 测试通过 → 检查差异”的顺序完成；阶段提交是否执行沿用工作区约定，不擅自覆盖其他未提交修改。

### 任务一：通用 Redis 限流组件

**新建：** 第 3.1 节的 Properties、Decision、RedisRateLimiter、Guard、IP Resolver、两种异常和三个 Lua 脚本。LoginAttemptGuard 可在任务三创建。

**测试：** `T/RedisRateLimiterTest.java`、`T/RateLimitPropertiesTest.java`。

- [ ] 写失败测试：额度 2，前两次 allowed=true，第 3 次 allowed=false；拒绝返回正 TTL；拒绝不 INCR、不刷新窗口；窗口结束后重新允许。
- [ ] 写失败测试：失败窗口第一次计 1，第 5 次封顶，check 在第 5 次之后拒绝；成功路径不清空；空键允许；无 TTL、错误类型、null 脚本结果转为存储异常。
- [ ] 写失败测试：配置默认值一致、非法 limit/window/namespace 拒绝启动、enabled=false 不访问 Redis；相同用户在不同租户/门店不共用订单额度，同一员工切店仍共用导出额度。
- [ ] 实现第 3、6 节接口与脚本，使用 `DefaultRedisScript<List>` 或等效明确的整数列表结果解析；脚本注册一次复用。
- [ ] 单元测试通过后添加真实 Redis 并发测试：两个独立 limiter 实例共享模板/连接，100 个并发请求只允许 limit 个；真实 Lua 而非仅模拟 Redis 返回值。
- [ ] 真实测试使用 UUID 测试命名空间、短窗口、有限等待与独立键清理；不要执行 FLUSHDB/FLUSHALL，不依赖真实业务键，不把密码写入测试文件。
- [ ] 运行 `./scripts/backend.ps1 -Goal test -Test 'RedisRateLimiterTest,RateLimitPropertiesTest' -Offline`，记录结果和使用的 Redis 环境。

**产物接口：** 第 3.1 节签名；后续任务不得另造第二种额度算法或使用业务对象模板计数。

### 任务二：HTTP 协议与订单、导出接入

**修改：** `J/handler/GlobalExceptionHandler.java`、`J/service/impl/OrderServiceImpl.java`、`J/controller/admin/ReportController.java`、`S/resources/application.yml`。

**新测试：** `T/RateLimitHttpTest.java`、`T/OrderRateLimitTest.java`、`T/ReportRateLimitTest.java`。

- [ ] 测试 HTTP 429、Retry-After 向上取整且最小 1、Result.code=0；503 独立；不影响已有 401/403、业务错误处理。
- [ ] 测试本人订单首次催单推送一次，60 秒内再次拒绝；他人/跨店/跨租户/不存在订单不占额度且不推送。保留原响应语义。
- [ ] 测试支付主动核查两秒内第二次拒绝，且不调用 `paymentRecovery.reconcileOrder`；原请求归属检查继续存在。
- [ ] 测试导出前 2 次接受、第 3 次拒绝；改日期和切门店不绕过员工配额；无权限/参数错误不占额度，429 不设置 Excel 下载头。
- [ ] 在第 4 节指定位置调用 `RateLimitGuard`，不要改变交易内部共用方法。
- [ ] 覆盖 Redis 故障的 503，并验证 WebSocket、支付提供方和文件生成无副作用；通知与后台恢复仍能运行。
- [ ] 运行上述新测试及现有订单归属、报表权限、支付恢复测试；从测试目录确认具体类名，不凭空填写不存在的测试名。

**依赖：** 使用任务一的 Guard；输出沿用现有 controller 成功协议。

### 任务三：登录失败保护

**新建：** `J/ratelimit/LoginAttemptGuard.java`。

**修改：** `J/controller/admin/EmployeeController.java`；必要时为登录凭证错误增加精确的内部分类，不改变旧接口行为。

**新测试：** `T/LoginRateLimitTest.java`。

- [ ] 用同一 IP/账号连续 5 次凭证失败，第 6 次 429；前 5 次仍为原凭证错误；第 6 次不调用认证 Supplier、不签发会话。
- [ ] 账号不存在、禁用、平台密码错误都进入失败保护；平台分支 null 然后员工成功/失败分别正确处理，不能记录两次。
- [ ] 账号规范化只影响键；不同 IP+账号组合隔离；同 IP 大量更换账号仍受 60/60s 总限制；伪造转发头不能更换默认 IP 主体。
- [ ] 数据库故障、员工会话签发异常、请求体解析错误不记为凭证失败；成功不清空窗口；不记录原始敏感字段。
- [ ] Redis 读/写故障走 503；完全禁用限流后不访问 Redis，原平台及员工登录、退出、会话撤销继续通过。
- [ ] 实现第 4.1 节包装与精确错误分类，运行新测试及现有员工登录、平台登录、管理员会话撤销测试。

**依赖：** 任务一 checkLogin/recordLoginFailure；任务二异常处理。

### 任务四：客户端等待与错误展示

**修改：** 第 2 节列出的管理端请求层、小程序请求层、支付页，以及通过搜索定位到的催单按钮、导出下载工具。

**测试：** 扩展 `frontend/junzhou-admin-vue3/tests/http.test.ts`、`statistics.test.ts`，`weixin-uniapp/tests/order-payment.test.cjs`；为请求层和催单冷却添加同目录风格测试。

- [ ] 先验证 429 不登出，Retry-After 可读取、缺失/无效值有有限默认等待，错误提示只显示一次；现有切店取消逻辑保持。
- [ ] 用 JSON Blob 模拟导出 429/503，断言展示 msg、不触发下载；正常 Excel 下载保持。
- [ ] 支付测试：先 429 后确认成功、连续 429 达 60 秒预算、503 等待、等待中离页/换店、正常 2 秒间隔及最多 6 次实际核查；不得因 429 显示支付失败。
- [ ] 催单首拒绝后倒计时、重复点击无请求、离页清理；重新进页仍靠后端额度保护。
- [ ] 实现第 5.3 节，避免公共请求层自动重试任意 POST/PUT。
- [ ] 管理端运行 `npm run test:unit`、`npm run type-check`、`npm run lint`、`npm run build`；小程序运行 `npm test`、`npm run build:mp-weixin`。在各自工程目录执行，不在仓库根目录混跑。

### 任务五：完整验收与交接

**文档：** 新建 `backend/junzhou-take-out/RATE_LIMIT.md`，简述启用方式、实际默认值、503 策略、IP 部署前提、验证与关闭方式，并更新 README 对应入口。

- [ ] 执行第 9 节全量测试，检查本次失败和既有失败的区别，不把依赖缺失当作通过。
- [ ] 使用两个 limiter/应用实例共享 Redis 验证额度合并，不宣称单进程测试等同跨实例验证。
- [ ] 按第 10 节操作手工冒烟，记录 429/503/恢复结果；测试隔离于真实商户支付，不对真实商户进行压测。
- [ ] `git diff --check`；检查未修改菜单缓存、幂等、支付回调、购物车事务逻辑，未泄露密码与 Token。
- [ ] 输出最终改动文件、测试结果、未执行项、配置和回退说明。本文中的复选框只在实际完成后勾选。

## 9. 验收命令与通过标准

后端，仓库根目录 PowerShell：

```powershell
# 使用机器已安装的 JDK/Maven，参考现有 scripts/backend.ps1。
# 需要时将 JAVA_HOME 设为实际 JDK 安装目录，不写入仓库配置。
./scripts/redis.ps1 -Action start
./scripts/backend.ps1 -Goal test -Test 'RedisRateLimiterTest,RateLimitPropertiesTest,RateLimitHttpTest,OrderRateLimitTest,ReportRateLimitTest,LoginRateLimitTest' -Offline
./scripts/backend.ps1 -Goal test -Offline
git diff --check
```

前端按任务四分别在 `frontend/junzhou-admin-vue3`、`weixin-uniapp` 目录运行。使用现有锁文件，不无故升级依赖；离线缓存缺少依赖时记录实际错误，不默默跳过。

必须通过：

- 真实 Redis 原子并发、TTL 恢复、身份隔离、故障行为测试。
- 四类业务拒绝时无副作用，登录失败分类正确。
- 正常支付轮询、交易恢复、管理端会话撤销、菜单缓存回归不受影响。
- 原有全部测试通过，或明确证明失败是环境/既有问题并列出阻塞项；不得把跳过当作通过。

参考基线：菜单缓存开发时曾运行 520 项后端测试，519 通过、1 跳过；这是历史结果，实现 agent 必须重新运行，不得复制为本功能的验证结果。新增测试后总数会变化。

## 10. 部署冒烟和回退

1. 测试环境启用保护，namespace 设置为当前环境，确认所有后端实例共用同一 Redis 逻辑库和命名空间。
2. 确认代理后的 remoteAddr；若都为同一代理 IP，先按第 3.4 节配置可信来源解析，不能直接信任用户转发头。
3. 专用测试账号验证第 6 次错误登录被拒绝；等待窗口到期恢复。不得用真实员工账号试错锁住操作。
4. 专用本人订单连续催单，两次最多一条提醒；另一个用户不能催该订单，也不影响本人额度。
5. 支付沙箱/现有 mock 流程确认 429 不被展示为支付失败，通知和后台恢复不依赖限流键。
6. 同一员工导出 3 次，第三次 429；切店不能绕过，另一个有权限员工额度独立。
7. 在隔离环境模拟限流组件 Redis 异常，验证 503 和业务无副作用。停整个 Redis 也会影响现有营业状态与缓存，不能把这些连带错误误判为限流组件行为。
8. 回退：设置 `RATE_LIMIT_ENABLED=false`，重启部署全部实例；旧键自动过期，无数据库迁移，不清空 Redis。若需恢复旧版本应用，按现有发布流程执行。

上线观察限流拒绝数量、503数量、规则 ID、接口延迟；优先复用现有日志或指标设施，不为本期新增整套监控平台。正常频繁拒绝用低级别/采样记录，存储故障告警要避免每次请求刷堆栈。

## 11. 官方参考与交接提示

- [Redis INCR：计数和限流，Lua 避免 INCR/EXPIRE 的竞态](https://redis.io/docs/latest/commands/incr/)
- [Redis EXPIRE：过期行为](https://redis.io/docs/latest/commands/expire/)
- [RFC 6585 第 4 节：HTTP 429 与 Retry-After](https://www.rfc-editor.org/rfc/rfc6585.html#section-4)

建议交给下一个 agent 的指令：

> 请阅读 `backend/junzhou-take-out/RATE_LIMIT_DEVELOPMENT_PLAN.md`，核对当前工作区已有代码和其他未提交修改，按本文第 8 节逐项完成接口限流、客户端适配和验证。本文规则作为默认规格执行，无需重新询问例行实现选择；遇到影响认证、支付正确性或与当前源码冲突的事项，先定位证据并明确说明。完成后报告实际测试结果、配置及未完成项，不直接发布生产。
