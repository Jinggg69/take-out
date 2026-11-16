# 平台超级管理员

平台与商户员工使用现有同一个登录页面。后端 `/admin/employee/login` 返回 `accountType=PLATFORM` 时进入平台的商户管理和门店管理；普通员工继续使用商户后台。前端身份标记只决定界面，平台 API 每次独立检查平台会话和账号状态。

## 能力与边界

- 商户、门店分页搜索，新增、基本信息修改、启停及逻辑删除。
- 开通商户时，同一事务创建首家门店、角色与权限目录、商户管理员和门店任职，任何步骤失败均回滚。
- 平台不作为商户员工参与订单、退款和商品操作。平台令牌不能访问商户业务 API，商户令牌不能访问 `/platform/**`。
- 删除保留订单、员工、商品、收款账户等历史数据；存在未结束订单、未确定支付或未结清退款时拒绝删除。已删除对象不能重新启用。
- 停用商户会同时停用其门店并撤销员工会话；重新启用商户后，需要逐一启用要恢复经营的门店。
- 新建商户管理员获得本商户全部门店范围的明确权限。已有商户角色、员工和权限不会被自动覆盖。
- `sky.payment.channel=mock` 时给新门店建立独立模拟支付账户关联。真实支付模式下不猜测商户收款身份，新店仍须按支付账户部署文档配置真实收款信息。真实微信支付联调不是本功能的交付内容。

## 数据库升级

先备份并在维护窗口选择正确的应用库，依次执行：

1. `database/migrations/20261116_01_platform_auth.sql`
2. `database/migrations/20261116_02_platform_management.sql`

新增 `platform_admin`、`platform_session`、`platform_audit`，并给 `tenant`、`store` 增加可空的 `deleted_at`。新库完整初始化 `database/junzhou.sql` 已包含两份迁移。重复执行不会创建账号、覆盖密码或恢复已删除对象。既有业务库不能使用完整初始化脚本升级。

## 初始化第一个平台账号

平台用户名必须使用 `platform_` 前缀；普通员工创建或改名不允许使用此前缀。初始化命令不会覆盖同名账号，也拒绝与现有员工同名。

打包后在交互终端执行（替换数据库连接及 jar 路径）：

```powershell
$env:PLATFORM_DB_URL='jdbc:mysql://127.0.0.1:3306/junzhou_take_out?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8'
$env:PLATFORM_DB_USER='your_database_user'
java '-Dloader.main=com.junzhou.platform.PlatformAdminBootstrap' -cp 'backend/junzhou-take-out/junzhou-server/target/junzhou-server-1.0-SNAPSHOT.jar' org.springframework.boot.loader.PropertiesLauncher
```

命令依次询问用户名、显示名、数据库口令和初始平台密码。密码不通过命令行参数传递；平台密码留空时生成随机凭据。无交互终端时拒绝运行。数据库存储既有密码服务格式的 BCrypt 哈希，平台会话仅保存随机令牌的 SHA-256 摘要。退出撤销当前会话，修改密码撤销该平台账号全部会话。

## 前端与代理

继续使用现有 Vue 3 项目。生产反向代理除原来的 `/api` 商户接口映射外，还需将 `/platform/` 原路径代理到同一后端，例如：

```nginx
location /platform/ {
    proxy_pass http://127.0.0.1:8080;
}
```

Vite 开发代理已提供 `/platform`。平台布局先调用 `/platform/auth/me` 核实身份，成功后才显示管理内容；不会加载商户门店工作区和订单 WebSocket。

## 验证

- 后端全套：466 项，0 失败、0 错误，1 项既有跳过。
- 前端完整检查：161 项单测、2 项代理集成测试、lint、类型检查与构建通过；旧密码兼容修复另有针对测试与最终构建验证。
- 临时 MySQL：完整初始化、迁移重跑、共享登录、身份隔离、开通回滚、商户门店生命周期、会话撤销均通过。
- 实际 MySQL REPEATABLE READ：删除等待下单的门店锁；订单提交后，通过当前读正确拒绝删除。
- 本机独立端口真实浏览器只读验收通过，两家已有商户仍各一家门店。操作证据与初始凭据存放在被 Git 忽略的 `local-reports/platform-admin/`，不要提交或公开该目录。
