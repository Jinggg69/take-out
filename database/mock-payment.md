# Mock 支付使用与验收

用于本地学习和演示，不扣款；保留真实微信 APIv3 实现。Mock 不替代微信登录，仍需要已登录的小程序用户和有效的当前门店范围。

## 启用

1. 备份目标开发库并停止后端写入。按已有迁移顺序完成订单生命周期、租户/门店和支付账户迁移，再执行 `database/migrations/20261112_02_mock_payment.sql`。本次开发没有替你修改业务库。
2. 确认该门店已有 `payment_account` 与 `store_payment_account` 归属记录。Mock 使用其归属，不要求其中商户身份与真实微信凭据匹配；不要更改已有账户身份或历史订单绑定。新演示库可使用仓库 SaaS 测试夹具，或在确认是独立开发库后创建明确的演示账户。
3. 在启动后端的同一 PowerShell 或 IDEA 运行配置中设置：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:PAYMENT_CHANNEL = 'mock'
$env:PAYMENT_JOBS_ENABLED = 'true'
```

随后用原来的方式启动后端。只有 dev/demo/test 环境允许 Mock，任何同时包含 prod、production 或其他环境名的组合都会拒绝启动。默认 `PAYMENT_CHANNEL=wechat`。不需要填写微信支付商户私钥、APIv3密钥或平台公钥来执行模拟交易。

4. 重新运行微信小程序。开启后创建**新订单**并进入付款页，点击“确认支付”后显示“模拟收银台 / 学习演示·不会扣款”。旧微信渠道订单不会被自动转换，Mock 模式会拒绝给这类订单发起预支付。

## 演示

- 模拟支付成功：后端记录模拟渠道成功，再走原支付确认事务；确认状态后进入成功页，商家收到订单提醒。
- 模拟支付失败 / 取消模拟支付：订单保持待付款，可再次选择结果。
- 模拟通知丢失：只记录渠道成功，不投递业务回调；小程序主动查单恢复订单。也可离开付款页，等待每分钟后台查单。
- 模拟退款：支付后在订单详情取消，原退款任务领取持久退款意图，通过 Mock provider 退款并核验成功。需要打开 PAYMENT_JOBS_ENABLED；也可通过已有管理员退款重试接口触发恢复。
- 重启后恢复：Mock 渠道状态保存在 mock_payment 表，重复成功、重启与退款重试沿用同一交易和退款身份。

模拟交易号以 MOCK_ 开头；模拟结果只接受服务端计算的订单金额、付款人和订单号，客户端只提交 SUCCESS / FAILED / CANCELLED / LOST_NOTIFY。

## 接口与隔离

- PUT /user/order/payment：返回 channel=MOCK 与 orderId；真实渠道仍返回原微信调起参数。
- POST /user/order/{id}/payment/mock，JSON：`{"outcome":"SUCCESS"}`；仅本人当前门店的 MOCK 订单、且仅 Mock 环境可操作。
- POST /user/order/{id}/payment/confirm：沿用原主动查单确认。
- 真实微信通知继续验签，明确拒绝更新 MOCK 订单。

订单 payment_channel 在下单时固定，数据库触发器禁止更换；历史订单默认 WECHAT。关闭 Mock 后历史模拟订单会拒绝资金操作，重新打开原演示环境即可继续恢复。真实订单仍按原账户处理，不会走模拟退款。

## 数据库验证与回退

`python scripts/test-mock-payment-migration.py` 仅连接本机 MySQL，创建并清理随机临时库，不修改业务库。覆盖迁移重入、历史默认渠道、已有模拟交易保留、渠道不可变、外键和金额/状态约束。

回退时保留新增列、表和交易记录，不删除真实订单，不改写历史渠道。涉及已有业务数据时，恢复原版本前应停止写入并核对其对模拟订单的兼容性。
