# 阶段 3.2：动作权限与数据范围

本阶段提供可持久化的范围与可复用的 Java 判定模型，供 3.3 接入。当前 HTTP 请求仍沿用阶段二边界；尚未根据新 RBAC 关系进行接口拦截。

## 权限动作

`PermissionAction` 是后端认可的动作目录，按精确 code 匹配，未知、大小写错误、空值和 `*` 均不能解析。数据库约束要求小写 `资源:动作`，禁止跨租户或资源通配；数据库允许保存未来的具体动作名称，但本版本 Java 不认识的动作不会获得执行权限。

| 资源 | 动作 |
| --- | --- |
| dish、setmeal | read、create、update、delete、status |
| category | read、create、update、delete |
| order | read、accept、reject、cancel、deliver、complete、refund |
| report | read、export |
| employee | read、create、update、status、manage |
| shop | read、update |
| workspace | read |
| file | upload |

`employee:manage` 保留 3.1 语义，用于角色、任职授权维护；员工基础信息操作使用对应的具体动作。订单拒绝和取消若可能引发资金退款，3.3 接入时必须额外校验 `order:refund`。动作之间没有隐含继承，`read` 不代表允许导出，管理员角色名也不是绕过检查的条件。

## 范围与合并规则

在 `employee_role` 增加 `data_scope`，范围随每条员工角色关系保存：

| 范围 | 含义 |
| --- | --- |
| CURRENT_STORE | 仅当前选中且已在 employee_store 任职的门店 |
| SPECIFIED_STORES | employee_role_store 中为此角色明确列出的本租户门店；空清单代表无门店权限 |
| TENANT_STORES | 当前员工所属租户的全部门店，包含以后新增的同租户门店 |

新增 `employee_role_store(tenant_id,employee_id,role_code,store_id)` 保存每条角色任职自己的指定门店。它同时用复合外键引用员工角色关系与员工门店关系，只有员工已任职且已拥有该角色的门店才能配置。各角色的清单互相独立，迁移不从员工全部任职自动复制指定门店。

已有员工角色关系默认 CURRENT_STORE；不会按管理员角色名称自动扩大范围。模板仍保留 3.1 已授权动作，3.2 只扩充动作目录，不自动为任何角色添加新动作。操作者在引导商户管理员时需明确核对后将其任职范围设为 TENANT_STORES，店长可按需要设为 SPECIFIED_STORES。例如：

```sql
UPDATE employee_role SET data_scope='SPECIFIED_STORES'
WHERE tenant_id=1 AND employee_id=1 AND role_code='MANAGER';
-- 前提：员工已在门店 10 任职，且具有该角色。
INSERT INTO employee_role_store(tenant_id,employee_id,role_code,store_id)
VALUES(1,1,'MANAGER',10);
```

`PermissionGrant` 表示某一个角色带来的某个动作与该角色任职范围。多角色判定须对完整授权逐一判断，任意一个完整授权通过才允许；禁止先合并动作全集再合并门店全集，否则“全租户查看＋本店退款”会错误变成全租户退款。

每次判定要求员工租户、授权租户、目标门店所属租户完全一致。目标租户必须由服务端根据目标门店/业务行查出，不能相信请求参数。构造授权时只能使用服务端查到的同租户任职清单：本店使用 employee_store；指定店必须使用 employee_role_store 按当前员工和当前角色过滤后的清单。TENANT_STORES 也没有跨租户通配含义。未知范围无法构建枚举，空范围不可回退为全部。

3.3 必须负责：从已认证员工加载角色权限与任职；校验员工、租户、门店启用状态；数据库范围过滤、接口动作校验和会话撤销。当前纯判定类不会代替这些检查，也不应接收前端提交的授权对象。租户目录菜品等没有 store_id 的资源，应在 3.3 定义对应的租户级接口校验，不能伪造门店参数复用门店判定。

## 部署和验证

顺序：阶段 3.1 → `20261103_05_rbac_scope.sql` → 后续 3.3 应用。要求 MySQL 8.4，停写、备份后选择应用库执行；完整初始化已同步。DDL 隐式提交，失败后保留维护窗口，核查结构后重试；兼容预检拒绝非法历史动作、未知范围和同名但错误/未启用的 CHECK 约束。SQL 会校验 MySQL 8.4 保存的约束表达式，其他版本需先在临时库验证。

迁移重跑不覆盖员工范围、权限名称或恢复撤销的角色权限。动作目录中缺失的动作会重新补齐，但不恢复任何授权关系。回退应用保留新字段和约束，禁止以删表回退。对 employee_role 的插入必须写列名，新增字段默认本店；不要依赖整行 VALUES 的列顺序。

验证命令：

- `./scripts/backend.ps1 -Test RbacPolicyTest -Offline`：动作精确匹配、三种范围、跨租户拒绝、多角色动作与范围不混用、空清单和无效值、集合防修改。
- `python scripts/test-rbac-scope-migration.py`：3.1 升级、范围默认值及枚举限制、禁止通配动作、撤权和范围设置保留、完整初始化及重复执行、错误同名约束拒绝。仅使用本机随机临时库。
- `python scripts/test-rbac-migration.py`：3.1 回归。

执行记录：先运行失败测试（Java 模型不存在、范围字段不存在），再实现并通过；同名无效 CHECK 测试先复现失败，再补充约束定义和启用状态校验并通过。范围按任职而非员工全局绑定，避免多角色范围放大。实际应用库未执行迁移。

独立审查：指出员工总任职无法表达不同角色的独立指定门店，已通过失败测试复现，新增 employee_role_store 及双重租户复合外键后回归通过。

最终验证：后端全量测试 342 项，0 失败、0 错误、1 跳过（含 RbacPolicyTest 6 项）；3.1 和 3.2 临时 MySQL 库验证均通过；git diff --check 通过。首次全量测试受沙箱本机连接限制，放行后发现 Redis 未启动，临时启动后重跑全绿并停止 Redis。

阶段 3.3 接入后，本店范围也收紧为该角色的 employee_role_store 清单，身份自服务不依赖业务门店；运行时行为与部署说明以 [阶段 3.3](rbac-authorization-stage3.md) 为准。
