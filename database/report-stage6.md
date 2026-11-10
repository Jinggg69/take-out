# 阶段6：工作台与经营报表

## 口径与时间

所有时间为 Asia/Shanghai，自然日区间为 `[begin 00:00, end+1日 00:00)`。JVM启动设置上海时区，Druid每条连接初始化 `SET time_zone='+08:00'`，微信支付success_time转换到上海本地时间；不依赖部署主机/MySQL默认时区。历史DATETIME如原来使用其他时区，需要先核对原始流水，禁止猜测回填。

| 指标 | 定义 |
| --- | --- |
| 实收/turnover | checkout_time落在区间内的订单amount之和，包含取消后迟到支付和后来退款订单；不按下单日、不按当前pay_status过滤 |
| 确认退款/refund | refund_request.status=SUCCESS，按updated_at首次本地确认成功时间计amount_fen/100；PROCESSING、FAILED等不提前计入。成功后重复通知不会更新此时间。此时间是本地确认时间，不是支付机构退款事件时间 |
| 净收款/net | 同一时间窗实收减确认退款，允许负数；跨日退款不回扣支付日实收 |
| 下单/支付/完成/取消数 | 分别按order_time、checkout_time、已完成订单delivery_time、已取消订单cancel_time计数；三种履约共用事件口径 |
| validOrderCount兼容字段 | 明确映射为完成事件数；页面显示“完成”，不再称有效订单 |
| 完成率 | 筛选期间下单订单中，当前status=5的占比；按下单队列计算，不用跨日完成事件数除以下单事件数 |
| 平均客单价 | 支付实收/支付订单数，四舍五入两位小数；无支付为0 |
| 销量 | 按支付日纳入商品明细数量，扣除筛选结束前已本地确认成功的全额退款；取消、退款处理中或退款失败不会提前扣减。支付和退款均在结束后发生的变化不改变该期间销量。以dish_id/setmeal_id区分商品，名称用订单快照，套餐不拆组成菜 |
| 顾客 | 所选门店集合内首次下单去重顾客，不读取全平台注册用户；累计值包含筛选开始前首次下单者。多店汇总去重，不简单相加 |

未支付订单不计收款或销量；历史缺checkout_time/delivery_time/cancel_time的记录不臆造事件时间。上线前核查：`SELECT id,number,pay_status FROM orders WHERE pay_status<>0 AND checkout_time IS NULL`，并核对 `status=5 AND delivery_time IS NULL`、`status=6 AND cancel_time IS NULL`。

## 接口与授权

GET `/admin/report/turnoverStatistics`、`ordersStatistics`、`userStatistics`、`top10`：必填begin/end（yyyy-MM-dd，含首尾日期，最多93天），可选storeIds（逗号分隔，最多100家；省略默认当前门店）。旧CSV字段兼容；营业趋势增加refundList/netList，订单增加paidOrderCountList/cancelledOrderCountList；top10增加items结构化商品ID。

GET `/admin/report/export`：同一组begin/end/storeIds。完全省略日期兼容旧调用，默认最近30个完整自然日。需要每家所选门店同时拥有report:read、report:export；只具备通用门店访问权不够。任何一家门店不满足动作权限，整体403，不静默缩减。当前店的动作由拦截器校验，所有所选店再次从数据库授权，服务执行前复核；缺员工/租户上下文拒绝。查询和导出都限制租户和授权门店，不信任客户端tenantId。

导出为真实XLSX，Daily为逐日财务与订单事件汇总，Items为全部商品净销量，Events为逐订单事件核对，Order items为逐订单商品ID/名称快照/数量/单价/退款纳入决定。工作簿属性保存日期、时区、租户和门店；只导出经营信息，不含顾客电话/地址。ID以文本保存避免Excel截断；名称作为字符串写入避免公式解释。事件或商品明细任一超过50000行则明确拒绝，要求缩小范围，不截断。导出在REPEATABLE_READ只读事务中完成。

GET `/admin/workspace/businessData`、`overviewOrders`、`overviewDishes`、`overviewSetmeals`：workspace:read权限，当前门店，今日上海自然日。状态概览是今日下单订单的当前状态；商品概览按门店销售关系和目录启售、上下架、沽清统计。

## 数据库与部署

先完成5C前置迁移，在备份、评估DDL锁等待并停止写入后执行 [20261110_01_report_indexes.sql](migrations/20261110_01_report_indexes.sql)，再部署后端和管理端。五个复合索引覆盖租户/店/下单、支付、完成、取消时间，以及退款状态/确认时间/订单。初始化脚本已同步；迁移可重复执行，无新增业务表、无汇总表、不改历史金额。EXPLAIN目前是小型固定夹具的证据，不宣称生产数据量性能；上线后按真实数据量复核执行计划和延迟。

回退时先回退应用，新增索引可以保留；不要逆改历史支付和退款流水，也不必删除新增索引。所有运行实例应统一时区配置。角色授权使用现有report:read/report:export/workspace:read目录，无新增权限，无自动扩权。

## 2026-11-10验证

- 后端全量416项，0失败/0错误，1项条件跳过；可执行JAR打包成功。新增3项覆盖逐笔跨日财务/三种履约/隔离/销量与四表XLSX、日期边界、直接导出403及执行前撤权。
- 管理端143项单元/契约、2项代理、lint、类型检查、生产构建通过；新增筛选导出、负净收款图表、清空门店后迟到响应与重试失效回归。小程序108项回归通过。
- `python scripts/test-report-stage6-mysql.py`：随机临时MySQL库完成初始化、索引重跑、真实Mapper查询、跨日支付/退款/取消后迟到支付、销量退款截止日、商品逐单核对、门店及租户隔离、菜单概览、会话时区和EXPLAIN；临时库已清理。业务库未修改。
- 独立代码审查的销量取消追溯、逐单商品缺失、空门店残留和MySQL会话时区问题已修正；针对销量/空选择的回归先失败后通过。
- Chrome已检查1440px桌面和390px窄屏统计页，负净收款、四类订单曲线及销量展示正常，无页面横向溢出；窄屏图表支持容器内滚动。
- 本地证据在local-reports/stage6及stage6-*.log；此目录被Git忽略。浏览器截图使用模拟API检查布局，不代表真实商户联调。真实商户、真机和发布验收仍按阶段7执行。
