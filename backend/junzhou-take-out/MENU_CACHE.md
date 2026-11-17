# 用户端菜单缓存

`MenuCacheAspect` 缓存 `StoreMenuService.publicDishes`、`publicSetmeals` 的返回列表，包括空列表。管理端查询、下单计价、支付与购物车写入仍使用数据库。

- 默认有效期 180 秒，通过 `MENU_CACHE_TTL_SECONDS` 设置，最小按 1 秒处理。
- Redis 键以 `junzhou:v3:tenant:{tenantId}:menu:` 开头，列表键包含门店、商品类型、分类（`all` 表示所有分类）和版本。
- 使用现有 `RedisTemplate` 的 JDK 序列化，保留金额、菜品口味等字段类型。
- 菜品、套餐、分类的新增、更新、删除、启停，以及门店商品价格、上下架、沽清、排序设置成功后，切换当前租户的菜单版本。菜品变化也会使套餐列表失效。为完整覆盖跨店商品依赖，当前按整个租户失效。
- 存在事务时，在提交后切换版本；事务回滚不失效。版本键不设过期，旧列表按 TTL 自动回收。读请求在查库前捕获版本，因此并发旧请求无法把旧数据写入新版本。
- Redis 连接、读写或序列化失败时，列表回查数据库；缓存故障不会把成功的数据库修改报告为失败。若失效失败，已有缓存最多保留至其 TTL 到期。
- 缓存命中前仍要求可信租户、门店上下文，接口原有认证与授权保持有效。

此实现缓存的是用户展示列表，不提供商品库存或交易正确性保障。提交订单继续重新核对数据库中的门店价格、可售状态与金额。当前不缓存套餐详情接口，也不缓存分类列表接口。

验证：`scripts/backend.ps1 -Goal test -Test 'MenuCacheTest,StoreMenuTest,SetmealManagementTest,SpringDataRedisTest' -Offline`。真实 Redis 测试需要本地 Redis 就绪及连接配置正确，测试仅清理自己创建的键。
