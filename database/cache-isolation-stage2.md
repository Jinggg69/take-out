# 阶段 2.5：缓存隔离子项

2026-11-03，基于 `d026aeb` 的工作区增量。仅交付 2.5 的缓存隔离，WebSocket、后台任务和上传文件归属仍待完成，2.5 总项保持未完成；保留现有单店保护。

## 实施范围与决策

- [✓] 统一键入口 `ScopedCache`；所有缓存读写与失效从可信 `RequestScope` 取值，包含缓存命中在内均校验租户、门店及员工门店权限。缺少范围时拒绝，不采用默认租户。
- [✓] 菜品及套餐按租户隔离，分类为空使用独立的 `all` 键。依照实体归属契约，当前商品目录属于租户，同租户门店共享目录缓存；阶段 4 增加门店销售关系后，门店菜单缓存须另加门店维度。
- [✓] 营业状态按租户与门店隔离；同租户的不同门店和不同租户均不共享状态。
- [✓] 菜品、套餐的新增、修改、批量删除及起停售，业务调用成功后仅清理本租户对应资源的全部分类缓存，覆盖换分类前后的缓存和不传分类的查询。业务失败不清缓存。目录写入前校验范围。
- [✓] 套餐取消全局 `@Cacheable` / `@CacheEvict(allEntries=true)`，统一使用显式 Redis 缓存；失效采用限定租户和资源前缀的 SCAN，关闭游标，不再使用全局 KEYS 或全局套餐清理。

键格式：

| 资源 | 示例 |
| --- | --- |
| 菜品分类 | `junzhou:v2:tenant:101:dish:category:7` |
| 全部菜品 | `junzhou:v2:tenant:101:dish:category:all` |
| 套餐分类 | `junzhou:v2:tenant:101:setmeal:category:7` |
| 门店营业状态 | `junzhou:v2:tenant:101:store:1001:shop-status` |

Ruling：继续在当前检出目录实施，未创建额外工作树；用户要求的交付范围仅为缓存子项，不扩展到 2.5 其他入口。

Ruling：商品缓存按租户归属，而非当前请求门店分片；现有 SQL 尚无门店商品销售关系，同租户目录修改应对所有门店生效。门店菜单引入时必须调整缓存范围。

## 发布兼容

新实现不读旧的 `dish_*`、`setmealCache::*`、`SHOP_STATUS`，避免继承无归属数据；目录缓存从已隔离 SQL 重建，无需数据库迁移。

营业状态是 Redis 中的持久业务状态，发布前必须在已核验仍为默认租户 1 / 默认门店 1 的单店环境中，将旧 `SHOP_STATUS` 的现值复制到 `junzhou:v2:tenant:1:store:1:shop-status`，保留原值类型；目标已存在时不覆盖。可在停写营业状态后使用 Redis 的 `COPY SHOP_STATUS junzhou:v2:tenant:1:store:1:shop-status`（不加 REPLACE），并核对结果。若旧键不存在，新键沿用原接口的默认营业状态 1。不得将旧键复制给其他门店。本次未操作真实营业状态，也未清理历史缓存。

## 验证

`CacheIsolationTest` 首次运行 4 项全部按预期失败，复现跨租户菜品/套餐命中、无作用域仍命中及旧全局营业状态读取。实现后相关 26 项回归通过，随后扩展菜品全分类失效、资源边界、租户前缀碰撞及失败写入不清缓存。

新增真实 Redis SCAN 测试使用随机租户范围和一分钟 TTL，只清理测试自行生成的键，覆盖本租户目录失效、其他租户保留、套餐与营业状态保留及空扫描。

完整回归：`scripts/backend.ps1 -Goal test -Offline` 在允许 localhost 连接的环境运行，316 项，0 失败、0 错误、1 项可选 MySQL 测试跳过，包含 7 项真实 Redis 测试。结果见本地 `local-reports/cache-final.log`；`git diff --check` 通过。初次沙箱内运行因 Windows 禁止 localhost 连接导致 `SpringDataRedisTest` 的 6 项及 `WeChatPayUtilTest.officialSdkValidatesPinnedPublicKeyResponsesWithoutLoggingForgedBody` 报错；解除连接限制后全部通过，未改动业务代码绕过测试。
