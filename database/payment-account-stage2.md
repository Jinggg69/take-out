# 阶段 2.6：支付账户与订单绑定

订单提交时从服务端当前门店映射取得 `payment_account_id`，订单不可更换账户。账户的租户、门店、微信商户号和 AppID 是不可变身份，密钥放在部署配置中。切换门店的当前账户只影响新订单；原账户配置必须保留，供历史订单预支付、查单、关闭交易、退款和退款查询使用。

## 部署与迁移

停写并备份后，在同一个 MySQL 连接中设置原有真实商户身份，再执行 `migrations/20261103_02_payment_account.sql`：

```sql
SET @legacy_payment_mchid = '原微信商户号';
SET @legacy_payment_appid = '原小程序AppID';
SOURCE database/migrations/20261103_02_payment_account.sql;
```

先执行租户/门店与支付生命周期迁移。`junzhou.sql` 初始化后也需执行本迁移；不向初始化脚本写入虚构商户身份。迁移只将默认租户/门店 1 的历史订单回填为 `legacy`；其他门店的未绑定历史订单会阻止迁移完成，必须先按实际资金归属人工核对绑定。MySQL DDL 隐式提交，失败后保持停写、检查错误并重跑，禁止使用 `mysql --force` 忽略错误。重跑不会覆盖已切换的门店映射或历史订单绑定。

数据库通过复合外键约束订单和账户归属、非空字段拒绝缺少账户的新订单，通过触发器禁止替换账户身份和订单账户。应用普通订单更新 SQL 也不接受账户字段。原账户凭证变更可以更新文件路径和证书；商户号或 AppID 变化必须创建新账户。

`legacy` 使用现有 `sky.wechat` 配置，其商户号和 AppID 必须与迁移记录一致。其他账户使用如下配置，所有路径和密钥应从部署环境提供：

```yaml
sky:
  wechat:
    accounts:
      store1-next:
        appid: ${STORE1_NEXT_APPID}
        mchid: ${STORE1_NEXT_MCHID}
        mch-serial-no: ${STORE1_NEXT_SERIAL}
        private-key-file-path: ${STORE1_NEXT_PRIVATE_KEY_PATH}
        api-v3-key: ${STORE1_NEXT_API_V3_KEY}
        platform-public-key-id: ${STORE1_NEXT_PLATFORM_KEY_ID}
        platform-public-key-file-path: ${STORE1_NEXT_PLATFORM_KEY_PATH}
        notify-url: ${WECHAT_NOTIFY_URL}
        refund-notify-url: ${WECHAT_REFUND_NOTIFY_URL}
```

账户 ID 使用简短 ASCII 标识，保留 `legacy` 名称。增加账户时显式插入 `payment_account(id,tenant_id,store_id,mchid,appid)`，再设置该门店的 `store_payment_account` 映射。配置与数据库身份不符、原配置缺失或归属不符时拒绝资金操作，不回退到当前门店或全局账户。当前小程序登录仍为单一 AppID，新增收款账户应使用该小程序 AppID；多 AppID 用户身份路由不属于本阶段。

## 回调与恢复

通知先按可信配置验证原始报文 RSA 签名、时间窗口并用对应 API v3 密钥解密，再匹配已认证的商户号与订单原账户。共用密钥的账户由订单固定账户进一步消歧。此时才通过受控全局查询获取订单归属并建立系统范围，后续支付和退款 SQL 仍限定租户/门店。外部 tenant/store 参数不参与定位。入口和 finally 清理请求范围及身份。

支付确认继续核验商户/AppID、金额、币种、付款人、交易号和交易类型；退款保持固定退款号、短事务、租约、重试退避、查询优先及终态幂等语义。后台任务移除支付专用单店保护，按候选订单逐笔装载范围并读取原账户；单笔异常不阻断其他门店，退出清理上下文。其他认证入口的单店保护仍按后续阶段计划保留。

## 验证

- 回归覆盖新增租户后的原回调、其他租户账户、跨账户拒绝、共享商户密钥退款、配置切换后的新旧订单、旧订单预支付和退款恢复、配置身份篡改与缺失绑定。
- `python scripts/test-payment-account-migration.py`：仅创建并清理随机本机临时库，验证迁移重跑、历史回填、切换保留、不可变触发器、跨门店外键与缺少账户拒绝。
- 全量后端命令：`./scripts/backend.ps1 -Offline`，335 项，0 失败、0 错误、1 项可选 MySQL 测试跳过；独立迁移脚本已连接本机 MySQL 通过。初次沙箱执行有 8 项本机 Redis/HTTP 连接错误，允许本机连接后完整重跑通过。
- 独立代码审查未发现 Important/Critical 问题；执行记录与原始日志在本地 `docs/stage2/payment-account-execution.md` 和 `local-reports/payment-account-verified.log`。

本次不操作真实业务库，不发起真实微信资金请求。真实商户联调与发布迁移验收仍需在部署环境完成；2.7 默认归属字段审计仍待实施。
