# 阶段 5A：统一履约订单契约

`fulfillmentType` 使用字符串 `DELIVERY`、`PICKUP`、`DINE_IN`，省略时默认 `DELIVERY`。旧 `status` 1—6 及 `payStatus` 编码保留。`fulfillmentStatus` 独立保存，退款只更新支付/退款信息，不重新打开履约。

GET /user/order/quote 可传 fulfillmentType，返回同名字段。POST /user/order/submit 沿用原接口：

| 类型 | 必填履约参数 | 服务端收费 |
| --- | --- | --- |
| DELIVERY | addressBookId，必须属于当前用户 | 商品 + 门店打包费 + 配送费；起送价、免配送门槛沿用门店设置 |
| PICKUP | contactName（非空、最多32字符）、contactPhone（11位手机号） | 商品 + 门店打包费；无配送费及外卖起送限制 |
| DINE_IN | tableId，必须为当前租户/门店已启用桌台 | 仅商品金额；无打包费、配送费及外卖起送限制 |

金额仍由服务端重新计算，与客户端确认的 amount 不同则拒绝，并要求重新报价确认。外卖独占地址簿和地图校验，自取/堂食不依赖地址。堂食保存服务端桌台名称快照。桌台基础表 store_dining_table 供后续 5C 管理及二维码能力接入；本阶段没有顾客扫码入口、桌次或自取核销凭证。暂不在小程序开放新履约选择。

| 操作/操作者 | 前置条件 | 履约状态变化 | 重复请求 |
| --- | --- | --- | --- |
| 用户提交 | 当前店可售、参数及已确认金额有效 | AWAITING_PAYMENT，旧status=1 | 相同请求号及内容返回原订单 |
| 微信已验证成功通知 | status=1，payStatus=0 | AWAITING_ACCEPTANCE，旧status=2 | 已支付重复通知不改变履约 |
| 商家接单/order:accept | status=2、payStatus=1 | PREPARING，旧status=3 | 已接单成功返回；更后状态拒绝 |
| 商家派送/order:deliver | DELIVERY、status=3、payStatus=1 | OUT_FOR_DELIVERY，旧status=4 | 派送中成功返回，不重写开始时间 |
| 商家完成/order:complete | DELIVERY、status=4、payStatus=1 | COMPLETED，旧status=5 | 已完成成功返回，不重写完成时间 |
| 系统自动完成 | DELIVERY、status=4、payStatus=1、实际派送超过1小时 | COMPLETED | 条件更新无变化 |
| 用户取消 | status=1/2，或已取消 | CANCELLED，旧status=6 | 保留首次原因时间，复用退款意图 |
| 商家拒单/order:reject | status=2，或已取消 | CANCELLED | 保留首次拒单原因时间，复用退款意图 |
| 商家取消/order:cancel | status=1/2/3/4，或已取消 | CANCELLED | 保留首次原因时间，复用退款意图 |
| 系统超时取消 | status=1、payStatus=0、超过15分钟 | CANCELLED | 条件更新无变化 |
| 迟到支付/退款回调 | 已取消或履约更后状态 | 保持履约状态 | 同交易重复回调幂等 |

READY 为 5B 预留；制作到待取、核销和堂食完成由后续阶段的专用授权接口实现，旧配送接口不用于这些类型。调用接口仍执行现有租户、门店及 RBAC 校验。

提交指纹包含租户、门店、履约类型、地址/联系人/桌台、配置版本、用户确认金额、备注、餐具、预计送达信息及规范化商品/数量/口味组合；客户端费用不参与计价。购物车顺序和行ID不影响指纹。原 user_id + client_request_id 唯一键保持不变。幂等查找仅允许当前认证用户，在返回订单前校验原租户、门店及指纹；跨门店/类型、同金额不同购物车返回冲突，不能误返原单。购物车已消费后使用订单保存的 cartFingerprint 校验重试；新购物车非空时比较其实际内容。历史无购物车指纹的 DELIVERY 订单保留原请求摘要兼容；当前购物车非空时，从原订单明细恢复商品摘要并比较。其他类型不能借用该兼容路径。

部署先停旧实例并备份，执行 database/migrations/20261109_03_order_fulfillment.sql，然后统一发布后端。历史类型回填 DELIVERY，履约状态由原 status 映射（旧异常/退款status映射为CANCELLED，原status保留）。新地址列允许空，外卖应用层仍必填。迁移可重跑，保留新类型及现有状态。新库使用同步更新后的 database/junzhou.sql。开放新类型后不能回退至不识别它们的旧应用。

验证入口：scripts/backend.ps1 -Offline；python scripts/test-order-fulfillment-migration.py。后者只连接本机 MySQL，创建随机命名临时库并在 finally 中清理，不修改业务库。

2026-11-09 本地验收：后端 386 项，0 失败、0 错误、1 项条件跳过（CATALOG_MYSQL_TEST 未配置）；本阶段 MySQL 迁移脚本已单独在临时库通过。独立代码审查的历史订单幂等问题已复现并修复。未修改实际业务库。
