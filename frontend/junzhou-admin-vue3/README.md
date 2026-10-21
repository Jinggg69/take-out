# 均州鱼庄管理系统

独立 Vue3 工程。当前交付至阶段 B：认证与基础布局；原管理端保留在同级源码目录。
已接入登录退出、七项导航、营业状态、修改密码和订单提醒；业务菜单暂为占位页，按后续阶段迁移。

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

- `check`：ESLint（不自动修复）、28项契约测试、2项实际代理/模式测试、类型检查和生产构建。
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
修改密码不自动注销；营业状态仍使用原PUT路径和标量请求体；订单提醒沿用原Socket协议及音频。
单元测试和浏览器夹具验收不能替代真实后端联调，实际账号、营业状态持久化、改密和订单推送需连后端复验。

原项目MD5仅用于防重键；新工厂直接使用同一组URL/方法/请求体作为Map键，不增加MD5依赖。
修复了旧防重实现中“取消的重复请求可能释放原请求锁”的前端缺陷；不改变取消后到重复请求的规则。
删除权限变量目前仅保留配置值；旧页面未实际使用它，本工程没有新增禁删规则。

## 后续阶段

C员工 → D分类 → E菜品 → F套餐 → G订单 → H工作台 → I统计 → J全量验收。
每次仅推进获授权阶段。产品与视觉约束见 `PRODUCT.md`、`DESIGN.md`。
阶段交付、验收记录保存在本地 `docs/phase-*.md`，已加入Git忽略；原源码归档也不纳入提交。
