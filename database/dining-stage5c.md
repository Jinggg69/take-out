# 阶段 5C：扫码堂食最小闭环

桌台管理 → 小程序内扫码 → 确认门店桌号 → 加入桌次 → 选菜、报价、独立下单支付 → 商家接单制作 → 堂食完成 → 结束桌次。沿用先付后做，不实现合单、拆单、共享购物车或先吃后付。

## 桌码与桌次

每张桌台属于唯一租户和门店，保存独立随机桌码 token。管理端本地生成 QR SVG，payload 为 `JZ:` 加 32 位小写十六进制 token；不把 token 发送至第三方二维码服务。顾客先在小程序登录，再点击首页“扫码堂食”，扫描桌牌并确认服务端返回的门店和桌号。该桌码是小程序内扫码内容，不是微信外部扫一扫自动打开小程序的发布码。

客户端只用 token 解析桌台，不能通过 tenantId/storeId/tableId 自行建立堂食身份。解析响应不包含桌次成员或其他顾客信息；确认加入时再次检查当前请求门店、启用状态和桌码。第一位顾客加入时创建桌次，后续顾客加入当前桌次，各自使用自己的购物车、订单、支付和退款。桌码轮换或停用后，旧码不能再创建订单。

提交 DINE_IN 必须携带 `tableId`、`diningSessionId`、`diningToken`，均从扫码确认结果取得。服务端在下单事务内锁桌台，核验当前桌次、本人成员身份、桌码和租户门店归属，保存桌号及桌次快照。仅传桌台 ID 的旧堂食请求被拒绝。堂食仅收商品金额，不校验配送地址或地图，不收打包费和配送费。堂食桌次与桌码加入提交摘要；外卖、自取的原摘要保持兼容。

同桌加菜：返回首页继续点餐，原扫码上下文仍有效时使用同一桌次生成新订单；每单有独立订单号、明细、请求号、支付和退款记录。顾客不能通过桌次读取其他人的订单。店员结束桌次前须先完成或取消全部订单；结束后旧桌次不可继续下单，下一批顾客加入新桌次。个人历史订单保留，不向其他顾客继承。

## 接口与权限

| 接口 | 约束 |
| --- | --- |
| GET /admin/dining | dining:read；当前门店桌台，不返回桌码 |
| POST /admin/dining | dining:manage；新增或修改 id/name/enabled，忽略客户端归属、桌码及桌次 |
| GET /admin/dining/{id}/code | dining:manage；获取当前桌码 payload，用于本地生成与下载 SVG |
| PUT /admin/dining/{id}/code | dining:manage；轮换桌码，立即撤销旧码；须重新打印桌牌 |
| PUT /admin/dining/{id}/close | dining:close；有未完成或未取消订单则拒绝；重复结束空闲桌台成功 |
| POST /user/dining/resolve | 用户认证；body={token}，服务端解析门店桌台 |
| POST /user/dining/join | 用户认证；body={token}，核对当前门店后加入桌次 |
| PUT /admin/order/dine/complete/{id} | order:dine:complete；DINE_IN、status=3/PREPARING、payStatus=1、无退款 → COMPLETED/status=5 |

堂食接单继续使用 order:accept，取消退款使用既有授权入口。完成用条件更新，重复请求不改写完成时间。旧配送与自动配送完成仅处理 DELIVERY。支付及退款通知不倒退履约状态。

新建桌台、停用、改名、换码、加入及结束桌次均按当前范围执行；下单验证与结束桌次共享桌台行锁，防止关闭和新订单同时成功。SQL 构建时验证请求范围，成员写入绑定认证用户。数据库通过复合外键校验桌台、桌次与订单的归属。

## 部署与验证

备份并停写，按序执行此前迁移、**本次修正后的** [5B 迁移](migrations/20261109_04_order_pickup.sql)，再执行 [5C 迁移](migrations/20261109_05_dining_session.sql)，统一发布后端、管理端和小程序。5B 修复了旧权限格式约束不接受三段动作名的问题；重复执行保留取餐凭证及核销审计。5C 重跑保留桌码、桌次及成员，新库初始化已同步。

权限只加入目录，不自动授予已有角色。商户管理员须配置 dining:read、dining:manage、dining:close、order:dine:complete 及相应门店任职。开放堂食后不可回退至不识别桌次的旧应用。历史 5A 堂食单保留原快照，后续新单必须扫码。

2026-11-09 本地结果：

- 后端 413 项，0 失败、0 错误、1 项条件跳过（CATALOG_MYSQL_TEST）；含桌码伪造/撤销、成员与跨店限制、旧桌次拒绝、加菜独立订单、结束与提交并发互斥，以及三种类型的已验证支付通知→取消→退款通知路径。微信调用使用测试替身，未发起真实交易。
- 管理端 140 项测试、2 项代理测试、lint、类型检查及生产构建通过；二维码矩阵与原版编码器基准一致。桌面和手机夹具浏览器检查无脚本错误及页面横向溢出；机械设计扫描无发现。
- 小程序 108 项 Node 测试通过，HBuilderX 配套 Node/uniapp-cli 的 mp-weixin production 编译完成。编译结果在被忽略目录 local-reports/stage5c-mp-weixin。
- `python scripts/test-dining-session-migration.py` 在随机临时 MySQL 库通过：桌码回填与重跑保持、桌次/成员/订单外键拒绝跨店伪造、新权限不隐式授权、新库初始化。`python scripts/test-order-pickup-migration.py` 也已通过，临时库均已清理。
- 独立代码审查及范围守卫修正后的复核未发现 Important/Critical。

真实业务库未迁移；微信真机扫码、真实支付退款及生产发布仍须部署环境验收。未提交 Git，原有未跟踪 PickupService 文件保留。
