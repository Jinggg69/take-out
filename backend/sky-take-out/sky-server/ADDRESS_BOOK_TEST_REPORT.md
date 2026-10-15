# 地址簿模块测试记录

测试日期：2026-10-15。

地址簿专项测试共 23 项：10 项通过，13 项失败，0 项执行异常，0 项跳过。
本次新增自动化测试，没有修改业务代码。失败用例保留，用于后续修复后复测。

## 测试方式

- 使用项目现有 JUnit 5、Spring MockMvc、MyBatis 和 H2 依赖，不新增依赖。
- 加载真实 Controller、Service、Mapper Java 注解和 Mapper XML；H2 使用 MySQL 兼容模式，表字段和非空约束参考 `database/sky.sql`。
- 每个用例创建独立内存数据库，测试结束后销毁，不连接业务数据库。
- 业务用例提供用户 42 的当前身份，以独立检验地址操作；登录用例加载真实 `WebMvcConfiguration` 和 JWT 拦截器，通过请求头发送签名令牌，不预设当前用户。
- 测试用户 42 有地址 101、102，用户 84 有地址 201；检查返回值的同时直接查询数据库，验证修改是否落库和是否误改其他用户数据。
- 本轮未进行小程序界面联调、真实 MySQL 兼容性验证或并发压力测试。

## 已通过的 10 项

| 测试 | 结果 |
| --- | --- |
| 新增地址保存全部字段，强制归属当前用户，忽略伪造 userId 和默认标记 | 通过 |
| 当前用户地址列表不包含其他用户地址（业务层已提供身份） | 通过 |
| 无地址用户返回空列表 | 通过 |
| 按 ID 查询本人地址，省市区字段正确映射 | 通过 |
| 修改收货人、性别、手机号、详细地址和标签，保留未提供字段 | 通过 |
| 设置本人的默认地址，取消旧默认且不影响其他用户 | 通过 |
| 无默认地址时返回业务错误 | 通过 |
| 删除本人地址，不影响其他地址；删除默认地址后查询提示无默认地址 | 通过 |
| 设置默认地址第二步出现数据库异常时，整个事务回滚 | 通过 |
| 用户、手机号和默认标记的组合过滤 | 通过 |

## 已复现的 5 类问题（13 项失败）

### 1. 地址簿没有接入登录拦截器（6 项）

`WebMvcConfiguration.addInterceptors` 的用户拦截器只匹配 `/user/shoppingCart/**`，不匹配 `/user/addressBook/**`。

实测结果：

- 不携带令牌查询列表返回 HTTP 200，返回所有用户的地址。
- 不携带令牌删除地址返回成功，测试数据库中的目标记录确实被删除。
- 有效令牌查询本用户列表时，预期 2 条，实际返回两个用户的全部 3 条。
- 有效令牌新增地址时，当前用户 ID 未被填充，触发 `user_id` 非空约束，返回 `code=0`。
- 过期令牌和错误签名的令牌查询列表均返回 HTTP 200，未拒绝访问。

失败用例：`anonymousListIsRejected`、`anonymousDeleteIsRejectedWithoutDeletingData`、`signedTokenRestrictsListToTokenOwner`、`signedTokenPopulatesUserWhenCreatingAddress`、`expiredTokenIsRejected`、`invalidTokenSignatureIsRejected`。

### 2. 按 ID 操作没有校验地址归属（4 项）

Mapper 的按 ID 查询、修改、删除仅以 `id` 定位记录；Service 未验证地址是否属于当前用户。

实测用户 42 可以读取、修改、删除用户 84 的地址，也可以把用户 84 的地址设为默认；最后一种操作还清除了用户 42 原有的默认地址。

失败用例：`rejectsReadingAnotherUsersAddress`、`rejectsUpdatingAnotherUsersAddress`、`rejectsDeletingAnotherUsersAddress`、`rejectsSettingAnotherUsersAddressAsDefault`。

### 3. 编辑地址不会更新省市区（1 项）

`AddressBookMapper.xml` 的 `update` 缺少 `province_code`、`province_name`、`city_code`、`city_name`、`district_code`、`district_name` 六个字段。

实测把上海地址改为广东深圳南山，接口返回成功，但六个字段仍为上海旧值，详细地址等其他字段已更新。

失败用例：`updatesAllRegionFields`。

### 4. 把不存在的地址设为默认，会清除原默认地址（1 项）

`setDefault` 先取消用户所有默认地址，再执行目标 ID 更新；目标不存在时更新 0 行，没有抛出异常或检查影响行数，事务正常提交。

实测设置不存在的 ID 999 返回成功，原默认地址 101 的标记从 1 变为 0。

失败用例：`unknownDefaultAddressIsRejectedWithoutClearingExistingDefault`。

### 5. 普通修改接口可产生多个默认地址（1 项）

普通 `PUT /user/addressBook` 直接接受并保存 `isDefault`，没有统一执行取消旧默认的逻辑。

实测给地址 102 提交 `isDefault=1` 后，同一用户同时存在两条默认地址。

失败用例：`ordinaryUpdateCannotCreateMultipleDefaults`。

## 项目全量测试

另外执行了项目现有的 `mvn -o -B test`。本轮共 61 项：38 项通过、13 项失败、10 项执行异常、0 项跳过。
13 项失败均为上述地址簿用例；额外 10 项异常如下，不属于地址簿专项结果：

| 测试 | 异常原因 |
| --- | --- |
| `DishDeletionTest.deletesStoppedDishesAndTheirFlavorsOnly` | 原测试手动构造 Controller，只注入 Service，没有注入现在使用的 RedisTemplate，清理缓存时空指针 |
| `DishStatusTest.stopsAndStartsOnlyTheSelectedDish` | 同上，RedisTemplate 为空 |
| `HttpClientTest.testGet`、`HttpClientTest.testPost` | 沙箱拒绝本机 HTTP 连接，报 `Permission denied: connect` |
| `SpringDataRedisTest.testString`、`testHash`、`testList`、`testSet`、`testZSet`、`testKeyOperations` | 沙箱拒绝本机回环连接，Redis 客户端无法建立连接 |

本次没有修改上述其他模块或测试；因此不能宣称项目全量测试通过。

## 复测命令

在项目根目录 `D:\take-out\backend\sky-take-out` 下运行：

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.2\plugins\maven\lib\maven3\bin\mvn.cmd' -o -B -pl sky-server -am '-Dtest=AddressBookModuleTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

测试源文件：`src/test/java/com/sky/test/AddressBookModuleTest.java`。

Surefire 详细结果：`target/surefire-reports/TEST-com.sky.test.AddressBookModuleTest.xml`。
