# 阶段 2.2：实体归属与隔离改造契约

2026-11-02，基于 HEAD 833f50c 和工作区已有阶段二迁移草稿。本文约定后续实现的目标与验收条件；当前代码仍为单店，不能据此认定隔离已生效。阶段 2.3—2.7 实施并验证后才可开放第二个真实租户。

## 身份及数据归属

| 实体或数据 | 归属与授权规则 | 现状与后续实现 |
| --- | --- | --- |
| tenant | 商户边界；商户员工只可访问自身租户 | 迁移创建，尚无商户管理接口 |
| store | 属于一个 tenant；门店 ID 必须解析为有效且属于该租户的门店 | store.tenant_id 有外键；员工允许门店范围尚未实现 |
| employee | 一个员工属于一个 tenant；登录身份与平台运维身份分离 | employee.tenant_id 草稿默认 1；用户名现为全局唯一，不能仅凭客户端 tenant 选择员工 |
| admin_session | 从 employee 继承租户；会话有效性、员工状态、允许门店须在服务端校验 | 当前会话仅存 employee_id/过期时间；2.3 建立可信上下文，3 增加任职关系和权限 |
| category、dish、setmeal | 租户目录；管理端按员工租户授权；用户端由已验证门店导出租户后读取，门店可售关系在阶段 4 实现 | 三表 tenant_id 草稿默认 1；分类名和商品名当前全局唯一，不在本任务改变 |
| dish_flavor | 通过 dish_id 对应的已授权 dish 继承租户 | 口味增删查都约束父菜品，不直接信任传入 dish_id |
| setmeal_dish | 通过已授权 setmeal 继承租户；关联 dish 也必须存在且属于同一租户 | 两端须在创建、更新、读取和删除时验证；现有字段可空，历史异常须在迁移或开放前调查，不能静默修复 |
| orders | 同时属于 tenant、store 和下单 user；store.tenant_id 必须等于订单 tenant_id | 订单/购物车字段在迁移草稿中默认 1；管理端按允许门店访问，用户端按本人及有效门店访问 |
| shopping_cart | 属于 tenant、store、user 的交集 | 所有增减、列表、清空、下单及重购操作限制三者；关联商品须同租户并满足门店可售规则 |
| order_detail | 通过已授权 orders 继承租户和门店，作为下单时的历史快照 | 写入时商品应属于订单租户；历史读取以订单为准，不依赖当前商品仍存在或当前商品可售 |
| refund_request | 通过已授权 orders 继承租户及门店；支付金额与账户匹配原订单 | 按订单授权退款操作；回调和任务通过受信订单定位，保持固定退款号、幂等和租约 |
| user | 跨租户的用户身份，不属于商户；当前微信用户按现有单 AppID 身份处理 | 商户无权按 user_id 枚举用户资料或其他订单；多 AppID 身份归并不在首版 |
| address_book | 属于 user；用户只能操作本人地址 | 商户仅通过已授权订单读取履约所需的订单地址快照，不能借订单访问或修改用户地址簿；下单地址必须属于用户 |
| 上传文件 | 目标为上传者租户归属，按商品/业务用途控制访问 | 当前无文件归属表；2.5 实现记录及访问控制，历史文件需制定归属回填与公开访问策略 |

租户及门店归属由可信服务端上下文产生，不接受任意请求头或 DTO 覆盖。管理端依据已认证的员工与受控门店清单；用户端提交的门店仅作为待校验选择，由有效门店推导租户。首版普通员工跨店操作默认关闭；临时允许门店清单必须有受控来源，不能仅依赖前端或客户端头，阶段 3 再提供可配置任职/RBAC。缺失、停用、不一致或无权时拒绝，不回退 ID 1。用户与订单 user_id 是独立约束，不因为商户能看订单而获得整个用户档案。

## 父子与批量操作约束

读取、写入、锁定、统计、导出、批量删除都必须在同一授权范围内；批量 ID 先完整核对归属，再在事务中操作，含无权或不存在项时整批拒绝。新建与修改须验证分类、菜品、套餐、订单、门店和用户之间的关系；涉及状态竞争时在事务中重验并采用适当锁或条件更新。父记录缺失、归属冲突或门店不属于租户均拒绝并留待调查。现有 DDL 大多没有这些外键，服务端验证不能被假定已经存在；数据库约束的具体迁移须先检查历史孤儿和跨租户数据。订单详情保留快照，不将商品删除作为订单历史不可读的理由。

## 现有入口清单及后续责任

路径以 `backend/junzhou-take-out/junzhou-server/src/main/` 为根。SQL 同时存在 `java/com/junzhou/mapper/` 注解和 `resources/mapper/` XML；以下每组覆盖该 Mapper 的所有方法及对应 XML，实施时逐方法检查，包括内部调用和批量操作。

| 入口 | 现有位置 | 所需边界 / 阶段 |
| --- | --- | --- |
| 登录、员工、会话 | EmployeeMapper、AdminSessionMapper；admin/EmployeeController、AdminSessionServiceImpl、拦截器 | 登录凭据定位员工；会话解析租户并限制员工操作，撤销保持有效；2.3/2.4 |
| 分类、菜品、口味 | CategoryMapper、DishMapper、DishFlavorMapper 及同名 XML；admin/user 对应 Controller、Service | 目录租户过滤；分类及口味父记录校验，批量删除关联限制；2.4 |
| 套餐及组成 | SetmealMapper、SetmealDishMapper 及 XML；admin/user SetmealController、SetmealServiceImpl | 同租户分类及菜品校验；连接查询和反向查找均受限；2.4 |
| 用户、地址 | UserMapper、AddressBookMapper 及 XML；user/UserController、AddressBookController、UserServiceImpl、AddressBookServiceImpl | 用户本人访问；商户仅限订单快照；2.3/2.4 |
| 购物车 | ShoppingCartMapper 及 XML；user/ShoppingCartController、ShoppingCartServiceImpl、CartAccessServiceImpl | 用户、租户、门店三重约束；锁和清空不能影响其他门店；2.4/4 |
| 订单、明细 | OrderMapper、OrderDetailMapper 及 XML；admin/user OrderController、OrderServiceImpl、OrderPricingServiceImpl | 管理端门店范围、用户本人、父子授权、幂等、报价与条件更新；2.4/4 |
| 退款和资金状态 | RefundRequestMapper、OrderMapper；PaymentLifecycleServiceImpl、RefundTransactionServiceImpl、PaymentRecoveryServiceImpl | 经订单定位归属，匹配原支付配置，维持事务、固定退款号和租约；2.4/2.6 |
| 后台任务 | OrderMaintenanceMapper、OrderTask、PaymentRecoveryTask、PaymentRecoveryServiceImpl | 全局扫描只能作为受控系统入口，逐订单携带可信归属；更新仍受条件和归属约束，清理上下文；2.5/2.6 |
| 微信通知 | notify/PayNotifyController、WeChatNotificationServiceImpl、支付/退款服务 | 先验签，再由订单及原支付账户确定归属和处理；不信任外部租户参数；2.6 |
| 缓存 | user/admin DishController 的 `dish_` 键；user/admin SetmealController 的 `setmealCache`；user/admin ShopController 的 `SHOP_STATUS` | 菜品/套餐键及失效范围带租户和适用门店维度；营业状态按门店；防止全局失效或跨店命中；2.5 |
| 消息 | websocket/WebSocketServer；OrderServiceImpl 催单、PaymentLifecycleServiceImpl 支付通知 | 当前 `/ws/{sid}` 与全局广播不提供门店授权；握手认证、门店订阅和撤权处理；2.5 |
| 文件 | admin/CommonController、AliOssUtil | 上传归属及访问策略；2.5 |

当前 `BaseContext` 只有用户或员工 ID，请求拦截器负责清理；尚无租户、门店或允许门店集合。当前未发现应用消息队列入口；支付回调、WebSocket 和定时任务分别按上表治理。工作台/报表接口目前缺失，将来新增的查询与导出遵循同一授权范围。

## 验收与交接

使用 `database/fixtures/saas-isolation.json` 的 A/B 租户、A1/A2/B1 门店和共享用户，分别验证跨租户读写删、批量混合 ID、伪造关联、门店范围、用户地址、订单明细及退款；无权结果不能泄露数据且原数据不变。额外构造不存在父记录、跨租户套餐菜品、门店与租户不一致及缺上下文情况。用缓存命中、WebSocket 订阅、任务重入和支付通知重放覆盖非 HTTP 入口，并验证异常后上下文清理。夹具目前只验证自身一致性，不代表服务端隔离测试通过。

2.3 按本契约实现上下文和可信门店来源；2.4 逐 Mapper 执行范围和关联约束；2.5/2.6 完成非请求链路；2.7 审计所有写入后移除默认归属。当前仍有真实业务库迁移、真实微信与真机验收待办；本次不新增迁移、不修改业务行为。
