# 均州鱼庄管理系统

独立 Vue3 工程。已完成 A 至 I 前端迁移和 J 本地全量验收；原管理端保留在同级源码目录。
七个菜单、三个新增/编辑路由、登录退出、营业状态、修改密码、订单提醒及各业务模块已接入。真实后端联调与部署验收仍待完成。
员工管理支持姓名查询、分页、启用禁用、新增、编辑、取消及保存并继续添加，保留原admin行保护与表单规则。
分类管理支持名称/类型查询、分页、菜品及套餐分类新增、编辑、启停、删除确认和继续添加；保留原字段、校验及后端关联删除规则。
菜品管理支持名称/分类/售卖状态查询、分页、单项及批量删除、启售/停售、新增/编辑、四种预设口味、图片上传和继续添加；价格仍以元提交。

## 本地运行

建议 Node.js 24 LTS；最低22.18（测试使用 Node 内置 TypeScript 支持）。

```powershell
cd D:\take-out\frontend\junzhou-admin-vue3
npm ci
npm run dev
```

开发地址：http://127.0.0.1:8889/ 。固定端口，冲突时直接报错，不自动占用其他端口。
原参考项目8888端口不变。npm缓存保留在本工程 `.npm-cache` 内，已忽略。

## 检查与构建

```powershell
npm run check
npm run build:uat
npm run preview
```

- `check`：ESLint（不自动修复）、110项契约测试、2项实际代理/模式测试、类型检查和生产构建。
- `type-check`：覆盖Vue组件、TS源码、Vite配置及测试文件，无输出编译。
- `test:unit`：使用临时本地HTTP服务验证请求，不访问真实后端，不写数据库。
- `test:proxy`：启动临时Vite和HTTP/WS服务，验证实际配置的路径转发及三种模式，结束后关闭。
- `build`：生成生产配置的 `dist`；`build:uat` 同目录生成UAT配置，后运行的构建替换前者。
- `preview`：预览最后一次构建，默认 http://127.0.0.1:4189/ 。它不替代生产反向代理。

## 配置

| 模式 | API前缀 | Socket地址 | 原删除权限配置值 |
| --- | --- | --- | --- |
| development | /api | ws://localhost:8080/ws/ | true |
| production | /api | /ws/ | false |
| production.uat | /api | /ws/ | true |

`.env.*` 只含可公开配置，已明确纳入版本管理；个人覆盖使用 `.env.development.local` 等忽略文件。
`VITE_*` 会进入浏览器资源，不能放密钥。`API_PROXY_TARGET` 仅供Vite配置读取，不以VITE前缀暴露。

开发代理：`/api/employee/login` → `http://localhost:8080/admin/employee/login`。
WebSocket代理：`/ws/{clientId}` → `http://localhost:8080/ws/{clientId}`，不经过 `/admin`。
生产相对资源和hash路由适配原有静态部署；生产/API代理继续由部署服务器提供。
未修改现有Nginx配置。生产Socket的 `/ws/` 按页面协议解析为同源ws/wss地址。

## 请求边界

`src/utils/http.ts` 提供 `createHttpClient({ baseURL, getToken?, onUnauthorized? })`。
保留token请求头、600000ms超时、旧GET参数格式、完整Axios响应、业务code处理归属、401通知、重复请求取消和二进制下载。
`src/utils/request.ts` 创建请求单例，使用Cookie的token和401跳转回调；`src/api`集中定义本阶段接口。
Pinia管理用户及导航状态，保留原token/username/user_info Cookie和sidebar_status偏好。
登录成功进入工作台；退出请求成功后清理认证Cookie。401跳转登录，沿用原版不主动删除Cookie的行为。
修改密码成功后清除本地认证Cookie并跳转登录；退出仅在后端业务成功后清除认证。后端禁用、改密会撤销员工全部旧会话，旧版Token也需要重新登录；升级前先执行根目录`database/migrations`中的迁移。营业状态仍使用原PUT路径和标量请求体；订单提醒沿用原Socket协议及音频。
单元测试和浏览器夹具验收不能替代真实后端联调，实际账号、营业状态持久化、改密和订单推送需连后端复验。

员工接口见 `src/api/employee.ts`，沿用原 `/employee/page`、`/employee/{id}`、`/employee` 和 `/employee/status/{status}?id=...`。
员工列表及表单复用公共请求、错误摘要、分页和设计变量；相同参数的在途读取共享结果，防止快速查询/前进后退触发错误状态。
此共享仅作用于员工GET读取，结束即释放，保留原Axios全局防重与提交行为，不缓存已完成业务数据。

分类接口见 `src/api/category.ts`，沿用 `/category/page`、`/category` 和 `/category/status/{status}?id=...`。
新增只提交name/type/sort，编辑只提交id/name/sort；删除仍以query.id传参，无请求体。
分类GET同样只共享在途请求；写入成功后若旧查询仍在途，等待其结束再读取新结果，避免连续保存或删除后显示旧快照。
分类分页沿用总数大于10才显示；当前页失效时读取有效页，避免删除末页最后一条后被困在空页。

菜品接口见 `src/api/dish.ts`，沿用 `/dish/page`、`/dish/{id}`、`/dish`、`/dish/status/{status}?id=...`、`/category/list?type=1` 和 `/common/upload`。
单项及批量删除均使用query.ids，多个ID以逗号分隔；原注释中的批量启停没有新增入口。
新增默认停售，价格保留字符串和元单位；口味value仍为JSON字符串，编辑保留详情元数据与口味ID。
上传使用multipart的file字段、原Token及严格小于2MiB限制；失败不覆盖旧图，上传未完成不能保存。
菜品GET只共享在途请求并在写后必要时刷新，不缓存已完成数据。列表按钮查询保留当前页，名称Enter回第一页。
保存中阻止侧栏/历史导航丢失提交结果；上传中离开则取消上传，未增加自动保存或草稿业务。

原项目MD5仅用于防重键；新工厂直接使用同一组URL/方法/请求体作为Map键，不增加MD5依赖。
修复了旧防重实现中“取消的重复请求可能释放原请求锁”的前端缺陷；不改变取消后到重复请求的规则。
删除权限变量目前仅保留配置值；旧页面未实际使用它，本工程没有新增禁删规则。

## 后续阶段

已完成H工作台、I统计和J本地全量验收。真实环境检查清单在本地忽略文档`docs/phase-j.md`。

工作台使用原四项`/workspace/*`接口，并保留待接单/待派送内嵌列表、详情、直接操作、排序与自动下一条。
统计使用原`/report/turnoverStatistics`、`userStatistics`、`ordersStatistics`、`top10`，保留昨日/近7日/近30日/本周/本月；数据图表附可查看的数据表、键盘可达的数据点。
导出保持`GET /report/export`无日期参数，确认导出最近30天，文件名`运营数据统计报表.xlsx`。
当前Spring Boot源码未找到report接口实现；统计页面具备错误与重试反馈，真实报表/导出可用性需后端联调。本次不改后端、不引入图表依赖。

套餐支持筛选、分页、单项/批量删除、行启停、新增/编辑、菜品选择与份数、上传和继续添加。
订单支持六个状态页签及数量、订单号/手机号/日期查询、分页、完整详情、接单/拒单/取消/派送/完成；消息提醒可在当前订单页打开相应详情。
套餐接口保持 `/setmeal`、`/category/list`、`/dish/list`、`/common/upload`；订单接口保持 `/order/conditionSearch`、`/order/details/{id}`、`/order/statistics` 及原PUT操作路径。
集成验收与真实环境人工检查清单见本地忽略文档 `docs/phase-fg.md`，不上传GitHub。
每次仅推进获授权阶段。产品与视觉约束见 `PRODUCT.md`、`DESIGN.md`。
阶段交付、验收记录保存在本地 `docs/phase-*.md`，已加入Git忽略；原源码归档也不纳入提交。
