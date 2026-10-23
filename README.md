# 餐饮 SaaS 重构项目 · 均州鱼庄

基于黑马程序员「苍穹外卖」项目持续重构，面向单店及小型连锁餐饮商家，逐步建设支持多租户、多门店和多种点餐方式的餐饮管理系统。

当前以「均州鱼庄」作为管理端和小程序的品牌界面，保留现有 Java 后端与外卖业务基础。新的 Vue 3 管理端已建立，员工、分类、菜品、套餐、订单、工作台与数据统计页面均已接入前端路由和 API 适配。

**当前仍处于单店系统向 SaaS 演进的阶段。多租户、多门店、RBAC、堂食扫码点餐和到店自取尚未实现；前端迁移完成也不等于真实后端联调和部署验收完成。**

## 重构目标

- 多租户、多门店管理，以及数据库、缓存、消息与后台任务的数据隔离。
- 堂食扫码点餐、到店自取、外卖配送三种业务方式。
- 基于 RBAC 的员工操作权限和门店数据范围控制。
- 门店维度的菜品、套餐上下架与可售状态管理。
- Redis 缓存及门店经营数据报表。
- Vue 3 管理端与微信小程序界面重构。

## 当前进展

下表的「已有实现」指当前源码中存在相关页面或业务逻辑，不代表本次已重新执行测试或完成生产验证。

| 范围 | 当前进展 | 仍需完成 |
| --- | --- | --- |
| Vue 3 管理端 | 独立 Vue 3 工程、品牌样式、登录与认证守卫、导航、请求封装、状态管理、营业状态操作和订单提醒 | 真实后端与部署环境联调；SaaS 门店切换和权限界面 |
| 员工、分类管理 | 查询、分页、新增、编辑、启停；分类删除及关联限制对接 | 租户归属、门店任职、RBAC 和数据权限 |
| 菜品、套餐管理 | 查询、分页、新增编辑、上下架、删除、图片上传、菜品口味及套餐组成配置 | 门店销售关系、沽清状态和统一可售判断 |
| 订单管理 | 前端状态筛选、详情及接单、拒单、取消、派送、完成操作；后端已有对应业务 | 服务端重新计价、下单幂等、多门店和多履约类型支持 |
| 工作台、数据统计 | 工作台页面、统计图表、日期筛选、导出调用及异常反馈已迁移 | 当前后端源码未找到 `/admin/workspace/*`、`/admin/report/*` 对应实现 |
| 修改密码 | 管理端弹窗及 API 调用已迁移 | 当前员工 Controller 未找到 `/admin/employee/editPassword` 实现 |
| 微信小程序 | Vue 2 uni-app 工程，已有菜单、购物车、地址、外卖结算、支付及历史订单页面；已做品牌样式调整 | 选店、扫码场景、堂食、自取及与新后端的联合重构 |
| 登录与用户归属 | 员工登录、微信用户登录、JWT；订单详情、取消等已有用户归属校验 | 地址簿归属保护、令牌失效策略、租户和门店上下文 |
| 支付与退款 | 微信预支付、支付通知、取消退款、固定退款单号及迟到支付处理 | 通知完整核验、退款处理中与完成状态、支付退款结果闭环 |
| Redis | 菜品缓存、套餐缓存、营业状态 | 租户及门店命名空间、细化失效范围 |
| 多租户、多门店、RBAC | 已明确重构方向 | 数据模型、授权和隔离链路尚未落地 |

### Vue 3 管理端

当前主要开发入口为 `frontend/junzhou-admin-vue3`。七个业务菜单分别为工作台、数据统计、订单管理、套餐管理、菜品管理、分类管理和员工管理。

迁移沿用现有接口路径和 Token 协议。页面包含加载、空数据、失败和重试反馈；工作台和统计接口未就绪时展示相应状态，不使用模拟经营数据代替接口结果。

管理端保留营业状态、修改密码和 WebSocket 订单提醒入口；这些功能需分别核对真实后端的支持情况，不能仅依据前端按钮或接口定义判断整条链路已可用。

### 后端业务基础

当前后端已具备：

- 员工登录、微信用户登录及 JWT 认证。
- 员工、分类、菜品、口味、套餐和套餐菜品关系管理。
- 基于用户的购物车、地址簿、订单提交与历史查询。
- 下单时的地址归属校验和基于骑行路线的 5 公里配送范围校验。
- 订单与明细写入、清购物车的事务处理。
- 支付预下单、支付通知处理、订单状态流转和退款相关逻辑。
- 菜品与套餐缓存、营业状态、WebSocket 和订单定时任务。

这些实现目前仍以单店为前提。现有订单状态数量统计也不能替代经营报表服务。

## 技术栈

| 层级 | 当前技术 |
| --- | --- |
| 后端 | Java、Spring Boot 2.7.3、Spring MVC、Spring 事务与 AOP |
| 数据访问 | MyBatis XML / 注解 SQL、PageHelper、Druid、MySQL |
| 缓存 | Spring Data Redis、Spring Cache |
| 认证与消息 | JWT、自定义拦截器、WebSocket、Spring 定时任务 |
| 外部服务 | 微信登录与支付、阿里云 OSS、百度地图配送范围校验 |
| 新管理端 | Vue 3、TypeScript、Vite、Pinia、Vue Router 4、Element Plus、Axios、Sass |
| 小程序 | Vue 2、uni-app、Vuex、uni-ui |
| 测试 | JUnit、Spring Test、部分 H2 集成测试、Node.js 测试 |
| 部署配套 | 仓库内保留 Nginx 配置与旧管理端静态资源 |

后端父 POM 声明 Java 8 / `release=8`，`sky-server` 又声明 `source/target=9`，编译级别尚需统一。不能将这些声明直接视为任意 JDK 环境下都已验证可构建。

## 项目结构

```text
take-out/
├── backend/
│   └── sky-take-out/
│       ├── pom.xml                 # Maven 聚合工程
│       ├── sky-common/             # 通用工具、异常、返回结构、上下文及配置
│       ├── sky-pojo/               # Entity、DTO、VO
│       └── sky-server/             # Controller、Service、Mapper、定时任务、WebSocket
├── frontend/
│   ├── junzhou-admin-vue3/         # 当前 Vue 3 管理端
│   ├── 苍穹外卖前端源码/           # 本地旧版参考源码，已忽略
│   └── nginx-1.20.2/               # 现有静态部署与代理配套
├── weixin-uniapp/                  # 微信小程序源码与测试
├── database/
│   ├── sky.sql                    # 单店数据库初始化脚本
│   └── 数据库设计文档.md
├── api/                           # 管理端、用户端接口定义
├── scripts/                       # 本地 Redis 管理脚本与说明
├── tools/                         # 本地开发工具，已忽略
└── brand-review/                   # 本地界面检查材料，已忽略
```

后端模块依赖为 `sky-server → sky-common`、`sky-server → sky-pojo`。目前是一个 Spring Boot 应用，三个 Maven 模块不代表三个独立服务。

## 本地开发

以下命令以仓库根目录为起点，每组命令在独立终端中执行。启动前需要准备本地配置和外部服务；本 README 编写时未重新执行启动、构建或业务测试。

### 1. 准备环境与数据库

- 安装 JDK、Maven 和 MySQL，先核对上述 Java 编译级别问题。
- 新管理端要求 Node.js `>=22.18.0`，具体依赖以其 `package.json` 和锁文件为准。
- 准备 Redis；小程序使用 HBuilderX 与微信开发者工具。
- 支付、图片上传和配送范围校验分别需要微信支付、OSS、百度地图配置。

`database/sky.sql` 创建并使用 `sky_take_out` 数据库，包含 `DROP TABLE IF EXISTS`。仅在确认可重建的开发库中初始化；已有数据的数据库需要备份和增量迁移，不能直接执行该脚本升级。

### 2. 配置 Java 后端

主配置为 `backend/sky-take-out/sky-server/src/main/resources/application.yml`，默认激活 `dev`。

在同目录准备被 Git 忽略的 `application-dev.yml`，配置至少包括：

| 配置范围 | 配置项或环境变量 |
| --- | --- |
| MySQL | `sky.datasource.driver-class-name`、`host`、`port`、`database`、`username`、`password` |
| Redis | `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE`；本地脚本说明使用 `sky.redis.password` 作为密码回退配置 |
| 配送范围 | `SHOP_ADDRESS`、`BAIDU_MAP_AK` |
| OSS | `ALI_OSS_ENDPOINT`、`ALI_OSS_REGION`、`ALI_OSS_ACCESS_KEY_ID`、`ALI_OSS_ACCESS_KEY_SECRET`、`ALI_OSS_BUCKET_NAME` |
| 微信登录与支付 | `sky.wechat` 下的应用、商户、证书、密钥和通知地址，具体环境变量见主配置 |
| JWT | 为实际部署覆盖管理端与用户端签名密钥 |

不要把本机密码、支付私钥或第三方密钥写入 README。配送地址或地图 AK 缺失时，当前配送范围校验会拒绝下单；支付与上传也需要相应配置才能联调。

### 3. 启动 Redis 和后端

已准备仓库配套的本地 Redis 时，可在根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\redis.ps1 start
powershell -ExecutionPolicy Bypass -File .\scripts\redis.ps1 status
```

该脚本依赖被忽略的本地 Redis 安装，并非全新检出后的自动安装器。也可以使用自行安装的 Redis，保持连接配置一致。

后端参考启动命令：

```powershell
cd backend/sky-take-out
mvn -pl sky-server -am package -DskipTests
java -jar sky-server/target/sky-server-1.0-SNAPSHOT.jar
```

默认端口为 `8080`，Knife4j 接口文档入口为 `http://localhost:8080/doc.html`。上述打包命令跳过测试，不表示测试已通过。

### 4. 启动 Vue 3 管理端

```powershell
cd frontend/junzhou-admin-vue3
npm ci
npm run dev
```

开发地址为 `http://127.0.0.1:8889/`，端口被占用时直接报错。

开发服务器默认把 `/api/employee/login` 转发到 `http://localhost:8080/admin/employee/login`。`API_PROXY_TARGET` 可覆盖 API 代理目标；`/ws` 代理保持 WebSocket 路径，不增加 `/admin` 前缀。Socket 实际使用的地址同时取决于前端模式配置。

`VITE_*` 变量会进入浏览器资源，只能保存可公开配置。个人环境覆盖使用被忽略的 `.env.development.local` 等文件。

### 5. 运行小程序

1. 使用 HBuilderX 打开 `weixin-uniapp`。
2. 在小程序配置中设置自己的 AppID，并与后端微信应用配置保持一致。
3. 在 `weixin-uniapp/utils/env.js` 设置 API 地址，当前源码默认 `http://localhost:8080`。
4. 使用微信开发者工具运行，并按实际环境配置请求域名及相关能力。

真机不能通过 `localhost` 访问开发电脑，需要改用设备可访问的后端地址。当前小程序仍使用 Vue 2，不能按新管理端的 Vite 工程方式启动。

### Nginx 是否必需

本地使用 Vite 开发管理端、Spring Boot 提供接口时，不要求启动 Nginx。小程序也可以直接请求后端。

现有 Nginx 配置用于提供旧管理端静态页面，将 `/api/` 转发至 `/admin/`，并代理 `/user/` 与 `/ws/`。新管理端构建产物尚需配置实际部署目录；运行 Vite 构建不会自动替换现有 Nginx 页面。

生产环境可以使用 Nginx 或其他部署入口处理静态资源、HTTPS 和请求转发。租户识别、RBAC 和数据隔离仍由后端负责。

## 检查与测试

本节列出已有检查入口，不报告本次未执行的测试结果。

| 范围 | 已有测试内容 |
| --- | --- |
| Java 后端 | 用户认证、员工状态、菜品修改及删除、套餐管理、地址簿、订单支付退款、配送范围、公共字段填充、Redis 等 |
| Vue 3 管理端 | 请求封装、环境配置、认证、员工、分类、菜品、套餐、订单、消息提醒、工作台、统计及代理行为 |
| 小程序 | 地址保存、结算费用与提交、配送时间选择 |

前端检查与构建，在新管理端目录执行：

```powershell
npm run lint
npm run test:unit
npm run test:proxy
npm run type-check
npm run build
# 或执行组合检查
npm run check
```

后端测试，在 Maven 聚合工程目录执行：

```powershell
mvn -pl sky-server -am test
```

部分后端测试使用自行建表的 H2 数据库，Redis 或外部 HTTP 相关测试需要另外检查各自前置条件；不能把整套测试都当作完全离线测试。H2 与模拟支付测试不能替代真实 MySQL、微信支付和部署验证。

小程序测试，在 `weixin-uniapp` 目录执行：

```powershell
node --test tests/address-save.test.cjs tests/order-checkout.test.cjs tests/order-time.test.cjs
```

## 当前已知缺口

后续重构需要先处理以下源码中可确认的问题：

- 数据表与业务查询没有租户、门店边界；分类、菜品、套餐名称和员工用户名仍使用全局唯一约束。
- 订单提交直接复制客户端金额，未在服务端重新计算费用并复核商品可售状态；订单号使用毫秒时间戳，初始化脚本未提供订单号唯一约束。
- 购物车同款查询忽略口味，读写与清空仅按用户划分；需要修正不同口味及并发操作行为。
- 地址簿详情、修改、删除、默认地址操作缺少完整用户归属校验；修改 SQL 未覆盖省市区字段。已有测试预期与这些实现存在差异。
- 超时任务用支付状态 `UN_PAID=0` 查询订单业务状态，正常待付款状态为 `1`，查询条件不匹配。
- 支付通知缺少完整签名及交易信息一致性核验；退款受理中和完成状态未分开建模。
- 用户催单 Controller 当前只返回成功，未调用催单业务；已有 WebSocket 采用全局连接广播，未按门店隔离。
- `/workspace/*`、`/report/*` 和修改密码的前端适配已存在，但相应后端接口尚待补齐。
- 小程序中的桌台接口定义和店铺字段不代表已经实现堂食或多门店业务。

上述问题需要专项验证和修复后再扩大业务范围。实际运行库是否与 `sky.sql` 一致，也需要在迁移前核对。

## 分阶段重构计划

以下为后续计划，不属于已完成功能。

| 阶段 | 依赖 | 主要工作 |
| --- | --- | --- |
| 1. 稳定单店基线 | 当前代码分析 | 统一构建配置，核对真实数据库与接口，修正金额、归属、购物车、超时任务及支付退款问题，完成真实联调 |
| 2. 租户与门店基础 | 阶段 1 | 明确账号与商户关系，建立租户、门店和请求上下文；回填旧数据，覆盖 SQL、缓存、消息、通知与任务隔离 |
| 3. 员工与 RBAC | 阶段 2 | 建立门店任职、角色权限、数据范围及门店切换；由后端执行授权 |
| 4. 门店菜单与购物车 | 阶段 2、3 | 建立租户目录与门店销售关系，统一菜品和套餐可售判断，拆分门店购物车及缓存 |
| 5. 三种点餐方式 | 阶段 4 | 服务端报价和订单快照、幂等提交；堂食桌台或桌次、自取凭证、门店配送、支付退款闭环 |
| 6. 经营报表 | 阶段 3、5 | 定义营业额、退款、有效订单、销量口径，实现门店报表、授权汇总与导出 |
| 7. SaaS 界面与发布验证 | 对应接口逐步稳定 | 在现有 Vue 3 工程上增加 SaaS 能力，继续重构小程序，完成端到端验证和数据库迁移演练 |

优先沿用当前单体应用逐步拆分业务边界。租户数据隔离应覆盖读取、写入、关联表、批量操作、缓存与消息，不能仅通过列表参数或隐藏菜单实现。

## 文档入口与项目来源

- 新管理端的运行方式与迁移边界：`frontend/junzhou-admin-vue3/README.md`、`PRODUCT.md`、`DESIGN.md`。
- 数据库结构：`database/sky.sql`、`database/数据库设计文档.md`。
- 接口定义：`api/管理端接口.json`、`api/用户端接口.json`；实际可用接口以当前 Controller 为准。
- 本地 Redis：`scripts/redis.md`。
- 小程序开发参考：`weixin-uniapp/README.md`、`weixin-uniapp/小程序开发流程.md`；历史文档中的功能描述需与当前代码核对。

项目基于黑马程序员「苍穹外卖」进行学习与持续重构。当前保留其后端模块组织和业务基础，并逐步迁移管理端、调整小程序品牌界面及建设餐饮 SaaS 所需能力。
