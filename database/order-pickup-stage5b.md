# 阶段 5B：到店自取

在 5A 契约上开放 PICKUP：小程序结算可选“到店自取”，必填取餐联系人及 11 位手机号，无需地址或地图。商品金额与打包费由服务端报价，不收配送费；切换履约方式重新报价，用户确认后提交。网络结果不确定时保留原请求和履约参数重试。

订单保存联系人、门店快照、展示取餐号、独立随机核销凭证和核销员工/时间。展示号不能推导随机凭证。凭证只在订单本人的详情接口返回 pickupToken，且限已支付、未退款、未取消、未完成订单；管理端及双方列表均不返回凭证。顾客在详情复制凭证向店员出示；凭证不是取餐号。详情显示门店地址及制作/待取状态，可手动刷新。

| 操作 | 接口及权限 | 前置条件 / 结果 |
| --- | --- | --- |
| 接单 | PUT /admin/order/confirm；order:accept | 沿用 5A：已支付、待接单 → PREPARING，status=3 |
| 制作完成 | PUT /admin/order/pickup/ready/{id}；order:pickup:ready | PICKUP、status=3、payStatus=1、无退款记录、PREPARING → READY；旧 status 仍为3 |
| 核销取餐 | PUT /admin/order/pickup/verify/{id}；order:pickup:verify；JSON `{ "credential": "32位小写十六进制凭证" }` | 当前店 PICKUP、READY、status=3、payStatus=1、无退款记录、正确凭证 → COMPLETED/status=5，保存核销员工和时间 |

两次状态转换使用带租户、门店、前置状态和支付条件的 SQL 更新。并发核销仅一次成功，重复核销返回业务失败，不改写时间和员工。顾客身份不能调用店员业务。取消及退款继续使用原入口；制作中和待取均可由授权商家取消退款，已完成异常退款沿用原入口。旧配送接口及自动配送完成任务不处理 PICKUP。

管理端订单页支持 fulfillmentType、fulfillmentStatus 筛选；制作中显示“制作完成”，待取显示“核销取餐”，权限由独立动作控制。店员输入顾客出示的凭证，管理端不能从详情读取凭证。工作台同步识别自取类型及动作。

部署：备份并停止写入，先执行 5A 及此前迁移，再执行 [5B 迁移](migrations/20261109_04_order_pickup.sql)，统一发布后端、管理端和小程序。迁移为已有自取单生成凭证，重复执行保留凭证及核销记录。新权限只加入权限目录，不自动授予已有角色；商户管理员须在角色权限及员工门店任职中配置 order:pickup:ready / order:pickup:verify。新库初始化已同步。不要将识别新类型的版本回退为旧版。

验证：后端 399 项、0 失败/错误、1 项条件跳过（CATALOG_MYSQL_TEST）；含 6 并发仅一次核销成功、错误凭证/未支付/取消/退款/跨店跨租户拒绝、凭证隐私和无动作权限拒绝。管理端 138 项单元/契约、2 项代理及 lint、类型检查、生产构建通过。小程序 107 项 Node 测试通过。机械 UI 检查无发现。

MySQL 演练脚本：`python scripts/test-order-pickup-migration.py`，仅创建和删除随机临时库，验证历史自取回填、外卖保持、权限不隐式扩大、重复迁移及新库初始化。当前会话该命令因自动审批额度限制未执行，真实业务库未修改。微信真机及真实支付退款仍需部署环境验收。

微信小程序生产编译：tools/HBuilderX/plugins/node/node.exe 配合已安装 uniapp-cli，mp-weixin production 构建完成，结果 local-reports/stage5b-mp-weixin；尚未真机验收。
