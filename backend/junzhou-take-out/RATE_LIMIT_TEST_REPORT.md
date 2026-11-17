# 接口限流、防频繁操作开发验证报告

验证日期：2026-11-17（Asia/Shanghai）。当前结论：**已补齐限流业务接入与客户端处理，原工程完整回归通过。**

## 修复后的原工程复验

以下是本次修复完成后的新结果；后续编号章节保留首次诊断证据，不代表当前仍存在同样问题。

| 检查 | 实际结果 |
| --- | --- |
| 原工程完整后端 `./scripts/backend.ps1 -Goal test -Offline` | 615项，614通过、1项条件跳过，0失败、0错误，BUILD SUCCESS |
| 管理端 `npm run check` | lint、164项单测、2项代理测试、类型检查、生产构建全部通过 |
| 小程序 `npm run check` | 200项测试全部通过，微信包、36个WXSS原生编译检查及H5构建通过 |
| 独立只读代码审查 | 未发现证据充分的P1/P2问题 |

完成内容：

- 补齐LoginAttemptGuard，兼容验证码、平台及员工登录，真实Redis+MVC测试验证第6次失败登录返回429且只认证5次。
- 催单和支付核查在本人订单校验后限流；导出在权限/筛选校验后限流；拒绝时不触发相应业务副作用。
- 明确HTTP429/503、Retry-After及no-store响应；保护故障返回统一消息。
- 修正原并发测试同步和线程池清理；旧认证测试显式装配依赖并使用真实验证码会话，未跳过凭证及权限断言。
- 管理端保留等待信息、解析JSON Blob导出错误；小程序实现催单冷却和支付有限等待，不把限流误判为支付失败。
- 测试专用disabled配置使用@TestConfiguration，验证其不被Boot默认组件扫描加载。

最新完整命令日志位于 `local-reports/rate-limit-backend-final.log`、`local-reports/rate-limit-admin-final.log`、`local-reports/rate-limit-miniapp-final.log`。配置和回退见同目录 `RATE_LIMIT.md`。

边界：真实Redis测试使用本机服务；未进行生产多进程部署、代理来源IP、微信真机或真实商户资金联调。保留工作区其他任务已有改动，未提交或发布。

---

以下为首次诊断记录；当时只测试和记录问题，没有修改业务实现。

## 1. 实际执行与结果

### 当前工程针对性测试：编译失败

在仓库根目录执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
./scripts/backend.ps1 -Goal test -Test 'RedisRateLimiterTest,RateLimitPropertiesTest,LoginRateLimitTest' -Offline
```

Maven 在 testCompile 阶段失败：`LoginRateLimitTest.java` 第11、35行引用的 `com.junzhou.ratelimit.LoginAttemptGuard` 不存在。

因此原工程这次没有执行到测试阶段；全量回归也未运行，不能宣称原工程或登录测试通过。`-Dtest` 只筛选执行目标，不会自动排除其他测试的编译。

日志：`D:\take-out\rate-limit-verification.log`。

### 独立源码副本诊断：17项，11通过、6失败

为避免改动开发中的文件，将当前后端复制到：

`D:\take-out\local-reports\rate-limit-audit-20261117\backend`

仅在副本排除不可编译的 `LoginRateLimitTest.java`，保留原业务实现；额外加入 HTTP、业务入口和正确同步的并发探针。使用真实本地 Redis 执行组件测试，业务入口探针使用 mock 依赖，不向真实商户下单、推送或付款。

| 测试组 | 数量 | 结果 |
| --- | --- | --- |
| 原 RateLimitPropertiesTest | 6 | 全部通过 |
| 原 RedisRateLimiterTest | 5 | 4通过，1个并发测试因同步写法失败 |
| 新 RateLimitHttpAuditTest | 2 | 2失败，实际HTTP均为200 |
| 新 RateLimitBusinessAuditTest | 3 | 3失败，登录/催单/导出未拦截 |
| 新 RateLimitConcurrencyAuditTest | 1 | 通过，100次请求只接受10次 |

这是两轮隔离测试的合计，不是当前工程的一次全量测试。两个限流对象处于同一 JVM、共享真实 Redis；不等于两个独立后端进程的部署验证。

日志：

- `local-reports/rate-limit-audit-20261117/component-tests.log`
- `local-reports/rate-limit-audit-20261117/audit-probes.log`
- 副本 `junzhou-server/target/surefire-reports/` 中的各测试报告。

## 2. 阻塞与缺陷

### P1：登录保护缺少实现，测试无法编译

- `LoginRateLimitTest` 已写入，但其依赖 `LoginAttemptGuard` 不存在。
- `EmployeeController.login` 当前仍是验证码校验后直接调用平台/员工认证，没有限流包装。
- 副本入口探针对同一账号和IP连续模拟6次密码错误，认证函数实际调用6次；计划要求第6次被拒绝、只进行5次认证。
- 验证码是已有另一功能，不能作为本次 Redis 登录失败保护已完成的证据。探针模拟每次验证码有效，以单独检查限流接入。

### P1：催单、支付核查、导出尚未接入限流

已定义 `RateLimitGuard.reminder/paymentConfirm/reportExport`，但业务文件中未找到调用。

- `OrderServiceImpl.reminder`：连续调用两次，`WebSocketServer.sendToStore` 实际调用2次，未形成60秒冷却。
- `ReportController.export`：连续调用三次，`ReportService.export` 实际调用3次，未执行每分钟最多2次的限制。
- `OrderServiceImpl.confirmPayment`：源码仍在归属校验后直接调用 `paymentRecovery.reconcileOrder`，缺少限流步骤。此项为静态接入检查，未对真实支付提供方发送请求。

相关源码位置（以验证时文件为准）：

- `junzhou-server/src/main/java/com/junzhou/controller/admin/EmployeeController.java:52`
- `junzhou-server/src/main/java/com/junzhou/service/impl/OrderServiceImpl.java:378`
- `junzhou-server/src/main/java/com/junzhou/service/impl/OrderServiceImpl.java:651`
- `junzhou-server/src/main/java/com/junzhou/controller/admin/ReportController.java:21`

### P1：异常未映射为429/503，Retry-After缺失

`RateLimitExceededException` 和 `RateLimitUnavailableException` 均继承 `BaseException`，`GlobalExceptionHandler` 尚无两者的专门处理器。

MockMvc 探针加载真实 `GlobalExceptionHandler` 并分别抛出这两类异常：

- 预期超限 HTTP429，实际 HTTP200。
- 预期保护存储故障 HTTP503，实际 HTTP200。
- 现有通用业务异常处理返回 Result.error，不设置计划所需的 Retry-After。

即使后续业务接入 Guard，这个响应协议问题仍会影响客户端等待、重试和错误识别。

### P2：现有并发测试自身存在同步缺陷

`RedisRateLimiterTest.twoLimiterInstancesShareQuotaAcrossOneHundredConcurrentRequests`：

- 线程池20个线程，却创建 `CountDownLatch ready(100)`。
- 工作线程先递减ready，再等待start；主线程要求全部100个任务ready后才释放start。
- 首批20个线程等待期间，剩余80个任务排队，无法全部ready；工作线程5秒等待超时后还会继续执行，因为没有检查await返回值。
- 实际测试在第99行 `ready.await(10秒)` 断言失败，不能凭这个失败认定Redis原子计数错误。

独立探针改用20个ready名额，对100个任务统一释放，finally终止线程池并清理专用key。验证接受数10、计数键值10且具有正TTL，测试通过。此调整只存在于独立探针，未修改原测试。

### P2：客户端等待逻辑尚未落地

静态搜索管理端请求层、小程序公共请求层与支付页，未找到本计划对应的 HTTP429、Retry-After、retryAfterSeconds 分支。

因此不能验收以下要求：429不登出、支付核查按服务端等待但不误报支付失败、60秒核查预算、催单倒计时、导出JSON Blob错误不被保存为Excel。

这部分未进行已部署浏览器/真机端到端验证；当前证据是源码缺少相应接入。

## 3. 已验证可用的部分

- 默认阈值与配置校验；关闭Guard时不访问Redis。
- 可信租户/门店/用户/订单维度的键隔离；导出额度不因切店变化；登录键摘要与账号规范化。
- 真实Redis固定窗口：达到额度后拒绝，不增加计数、不刷新TTL，过期后恢复。
- 登录失败计数达到5后封顶；无TTL、错误类型、负计数、脚本异常等转为限流存储异常。
- 正确同步的双对象100请求并发探针：额度10时正好允许10次。

上述证明组件能力，不能代替业务接口与HTTP合同验证。

## 4. 建议实现 agent 的修复顺序

1. 补齐 LoginAttemptGuard 和登录包装，兼容当前新验证码流程，区分凭证错误与会话签发故障。
2. 在本人订单归属检查后接入催单/主动核查Guard，在导出完整授权后接入导出Guard。
3. 为两类限流异常实现429/503、Retry-After及Cache-Control响应，验证拒绝时无业务副作用。
4. 修复原并发测试同步和线程池finally清理，保留真实Redis验证。
5. 补齐管理端与小程序等待、错误提示及导出Blob处理；不影响支付通知、后台恢复和原有幂等。
6. 重新运行开发计划中的针对性测试与完整回归；补充真实部署多实例及客户端联调后再验收。

请遵循同目录 `RATE_LIMIT_DEVELOPMENT_PLAN.md` 的规则，不能通过删除未通过测试、放宽断言或把HTTP200当作限流成功来关闭问题。

## 5. 首次诊断的验证边界

- 初次诊断没有修复业务功能；当时的测试副本与探针位于被Git忽略的local-reports中，原始证据保留本机。当前修复与复验结果见本文开头。
- 未运行生产环境压测、真实微信支付或完整前端端到端测试。
- 验证期间对Java/Lua/YAML等源码做摘要比对，未发现本次所测业务源码变更；IDE工作区配置存在变化，不影响上述源码快照结论。
