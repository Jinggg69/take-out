# 接口限流与防频繁操作

限流使用现有 Redis 连接，以独立字符串键和 Lua 脚本共享额度。它保护操作频率，不替代订单请求号幂等、数据库事务或支付退款状态机。

| 操作 | 默认规则 | 额度范围 |
| --- | --- | --- |
| 登录尝试 | 60秒最多60次 | 来源IP，成功失败均计算 |
| 登录凭证失败 | 300秒内5次，第6次在认证前拒绝 | 来源IP＋规范化账号 |
| 催单 | 60秒最多1次 | 租户、门店、本人用户、订单 |
| 主动核查支付 | 2秒最多1次 | 租户、门店、本人用户、订单 |
| 导出经营报表 | 60秒最多2次 | 租户、员工；切店或改日期不重置 |

窗口从第一次接受操作开始，拒绝不会延长有效期。失败凭证窗口在第一次失败时开始，成功登录不清空该窗口。达到失败上限前已进入认证的并发请求可能继续完成；IP总额度限制总体尝试量。

登录保留图片验证码；验证码失败不计入密码失败，登录入口仍受IP额度保护。平台账号和商家员工共用入口，最终凭证错误只记一次，员工会话签发故障不算输错密码。

催单、支付核查在本人订单和门店归属检查后占额，导出在日期、门店与导出权限验证后占额。拒绝不会发送催单、访问支付提供方或生成Excel。已接受后发生业务失败不退还额度。

超限返回 HTTP429、原有 `Result.error` JSON、整数秒的 `Retry-After` 和 `Cache-Control: no-store`。Redis限流存储故障返回HTTP503、`Retry-After: 3`，不继续受保护操作；与菜单、报表缓存的故障回查策略分别处理。微信通知和后台支付恢复不套用此限制。

客户端不因429退出登录，不自动重放登录、导出、下单。催单按等待时间禁用；主动支付核查按服务端等待，受6次实际核查和60秒总等待预算约束。限流或暂不可用仅表示尚未确认支付，不代表付款失败或成功。离页或身份、门店变化时取消等待。

## 配置与部署

配置在 `junzhou-server/src/main/resources/application.yml` 的 `sky.rate-limit`：

- `RATE_LIMIT_ENABLED`：默认true，关闭并重启全部实例可回退为原业务操作，无需数据库迁移。
- `RATE_LIMIT_NAMESPACE`：默认dev；生产显式设prod，同部署所有实例使用同一Redis逻辑库和namespace。
- `RATE_LIMIT_LOGIN_IP_LIMIT` / `RATE_LIMIT_LOGIN_IP_WINDOW_MS`：60 / 60000。
- `RATE_LIMIT_LOGIN_FAILURE_LIMIT` / `RATE_LIMIT_LOGIN_FAILURE_WINDOW_MS`：5 / 300000。
- `RATE_LIMIT_REMINDER_LIMIT` / `RATE_LIMIT_REMINDER_WINDOW_MS`：1 / 60000。
- `RATE_LIMIT_PAYMENT_CONFIRM_LIMIT` / `RATE_LIMIT_PAYMENT_CONFIRM_WINDOW_MS`：1 / 2000。
- `RATE_LIMIT_REPORT_EXPORT_LIMIT` / `RATE_LIMIT_REPORT_EXPORT_WINDOW_MS`：2 / 60000。

调整配置通过重启生效，已有键按原TTL自然过期，不清空Redis。namespace只接受1～32位英文字母、数字、下划线或连字符；非法阈值、窗口导致启动校验失败。

IP默认仅取 `HttpServletRequest.getRemoteAddr()`，不相信客户端伪造的转发头。使用反向代理时确认应用看到的来源；若全是代理IP，用户会共享额度。真实来源解析必须在受控的可信代理/容器配置中完成，同时阻止客户端伪造和绕过代理直连，不能直接取任意X-Forwarded-For最左项。

## 验证

仓库根目录使用现有Java/Maven和本地Redis：

```powershell
./scripts/redis.ps1 -Action start
./scripts/backend.ps1 -Goal test -Offline
```

分别在 `frontend/junzhou-admin-vue3` 和 `weixin-uniapp` 运行各自 `npm run test:unit` / `npm test` 及构建。真实Redis测试只清理其随机测试键，不使用FLUSHDB。测试无需真实微信付款，也不能替代生产多实例、代理与微信开发者工具验收。

监测时区分429（达到额度）和503（保护服务故障），不要把限流计数用作账务统计，不记录密码、原始账号/IP键、Token或微信code。
