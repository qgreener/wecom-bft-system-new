# S8 PC 管理端页面交接

## 1. 阶段结论

| 项目 | 结论 |
|---|---|
| 阶段 | S8「PC 管理端页面」 |
| 范围 | 仅处理 `frontend/admin` PC 管理端；未读取、参考或复用 `D:\java-code\wecom-BFT-system-old-failed` |
| 前端入口 | `frontend/admin`，Vue 3 + TypeScript + Vite |
| 完成结论 | 管理端已从 S1 骨架升级为可登录、按权限菜单渲染、可加载核心业务列表与详情、可执行退款/发货/开票/对账/代账材料核心写操作的后台页面 |
| 后端补口 | 为 S8 页面补齐了管理端退款、开票、代账材料列表与详情接口；补齐超级管理员财税/退款菜单返回 |
| 关键约束 | 前端不硬编码业务状态流转；写操作统一 `Idempotency-Key`；写后重新查询；金额按分转元展示；缺接口处展示缺口而非伪造数据 |

## 2. 页面清单

| 页面 | 路由 | 数据来源 | 状态 |
|---|---|---|---|
| 登录 | 登录态为空时展示 | `POST /api/admin/auth/test-login`、`POST /api/admin/auth/wecom-login` 保留后端能力 | 已实现测试账号登录入口 |
| 角色申请 | 无角色用户 | `POST /api/admin/role-applications` | 已实现 |
| 首页 | `#/dashboard` | `/api/admin/orders`、`/api/admin/accounting-workbench/summary` | 已实现摘要与最近订单 |
| 订单管理 | `#/orders` | `GET /api/admin/orders`、`GET /api/admin/orders/{order_id}` | 已实现列表、详情、单据链展示 |
| 退款处理 | `#/refunds` | `GET /api/admin/refunds`、`GET /api/admin/refunds/{refund_id}`、审核/拒绝/人工退款接口 | 已实现 |
| 发货管理 | `#/shipments` | `GET /api/admin/shipments`、`GET /api/admin/shipments/{shipment_id}`、发货/签收接口 | 已实现 |
| 开票管理 | `#/invoices` | `GET /api/admin/invoices`、`GET /api/admin/invoices/{invoice_id}`、人工开票/红冲接口 | 已实现 |
| 收款对账 | `#/reconciliation` | `GET /api/admin/reconciliations`、`GET /api/admin/reconciliations/{batch_id}`、导入接口 | 已实现 |
| 代账管理 | `#/accounting` | `GET /api/admin/accounting-materials`、详情、上传/确认/关闭/下载、工作台摘要 | 已实现 |
| 课程管理 | `#/courses` | `GET /api/admin/courses`、`GET /api/admin/courses/{course_id}` | 已实现列表与详情 |
| 线索管理 | `#/leads` | `GET /api/admin/leads` | 已实现列表 |
| 学员管理 | `#/students` | 当前无 `GET /api/admin/students` | 页面显示接口缺口，不伪造数据 |
| 库存管理 | `#/inventory` | `GET /api/admin/inventory/skus` | 已实现列表 |
| 采购订单 | `#/purchases` | `GET /api/admin/purchases`、`GET /api/admin/purchases/{purchase_id}` | 已实现列表与详情 |
| 系统设置 | `#/settings` | `GET /api/admin/system/configs?config_group=...` | 已实现配置查看 |
| 操作日志 | `#/audit` | `GET /api/admin/audit/logs` | 已实现列表 |

## 3. 接口清单

| 模块 | 前端接入接口 | 说明 |
|---|---|---|
| 鉴权权限 | `POST /api/admin/auth/test-login`、`POST /api/admin/auth/wecom-login`、`GET /api/admin/auth/me` | 菜单、权限码、数据范围、字段脱敏均以 `auth/me` 返回为准 |
| 订单 | `GET /api/admin/orders`、`GET /api/admin/orders/{order_id}` | 订单详情展示四类状态、支付记录、权益、发货单、单据链、审计日志 |
| 退款 | `GET /api/admin/refunds`、`GET /api/admin/refunds/{refund_id}`、`POST /approve`、`POST /reject`、`POST /manual-complete` | 列表/详情为 S8 补口；写操作后前端重新查询 |
| 发货 | `GET /api/admin/shipments`、`GET /api/admin/shipments/{shipment_id}`、`POST /ship`、`POST /sign` | 发货写操作带幂等键 |
| 开票 | `GET /api/admin/invoices`、`GET /api/admin/invoices/{invoice_id}`、`POST /issue-manual`、`POST /red-reverse` | 列表/详情为 S8 补口 |
| 对账 | `GET /api/admin/reconciliations`、`GET /api/admin/reconciliations/{batch_id}`、`POST /api/admin/reconciliations/import` | 对账导入只生成批次和差异，不改交易状态 |
| 代账 | `GET /api/admin/accounting-materials`、`GET /api/admin/accounting-materials/{material_id}`、`POST /files`、`POST /confirm`、`POST /close`、`GET /download`、`GET /api/admin/accounting-workbench/summary` | 列表/详情为 S8 补口 |
| 课程线索库存采购 | `/api/admin/courses`、`/api/admin/leads`、`/api/admin/inventory/skus`、`/api/admin/purchases` | 页面使用当前 Controller 字段展示 |
| 系统审计 | `/api/admin/system/configs`、`/api/admin/audit/logs` | 权限由后端兜底 |

## 4. 06 文档与当前 Controller/DTO 差异

| 差异 | 当前代码事实 | S8 处理 |
|---|---|---|
| 管理端测试登录 | 当前存在 `POST /api/admin/auth/test-login` | 前端用于本地演示登录；企微登录路径仍保留 |
| 退款申请路径 | S7 同时支持 `POST /api/app/refunds` 与 `POST /api/app/orders/{order_id}/refunds` | PC 不接 App 申请，只接 Admin 审核处理 |
| 退款审核路径 | 当前有 `approve`、`reject`，并保留 `review` 别名 | 前端接 `approve`、`reject` 真实接口 |
| 开票人工路径 | 当前有 `issue` 与 `issue-manual` | 前端接 `issue-manual`，后端复用同一服务 |
| 对账别名 | 当前有 `/api/admin/reconciliations/import` 与 `/api/admin/reconciliation/batches` | 前端接主路径 `/api/admin/reconciliations/import` |
| 代账别名 | 当前有 `/api/admin/accounting-materials` 与 `/api/admin/accounting/materials` | 前端接主路径 `/api/admin/accounting-materials` |
| 管理端退款/开票/材料列表 | 06 和 S7 交接未给完整管理端列表/详情，当前 S8 已补 `GET /api/admin/refunds`、`GET /api/admin/invoices`、`GET /api/admin/accounting-materials` | 作为真实页面接入依据 |
| `allowed_actions` | 当前核心 DTO 多数未返回 `allowed_actions` | 前端操作按钮按权限显示，最终状态由后端裁决；此项记录为 S9 前建议补字段 |

## 5. 接口缺口与 S9 注意事项

| 缺口 | 影响 | 建议 |
|---|---|---|
| 缺少 `GET /api/admin/students` 与 `GET /api/admin/students/{student_id}` | 学员管理页无法展示真实学员列表和学员详情 | S9 前补 PC 学员列表/详情 DTO，或在 S8 后续小任务补后端 |
| 缺少供货商管理 PC 接口 | PC 供货商管理无法单独展示 | 采购/库存主链路已可用，供货商页面建议补 `/api/admin/suppliers` |
| 多数业务详情缺 `allowed_actions` | 前端无法完全按“当前对象状态 + 权限”精确裁剪按钮 | 建议后端在订单、退款、发货、开票、材料详情返回 `allowed_actions[]` |
| 菜单父级只返回子菜单 | 前端已按 `parent_code` 自行分组展示，但没有父级菜单显示名 | 可在 `auth/me` 中补父级菜单或沿用前端固定分组 |
| 导出、文件上传真实控件未统一 | 当前写接口只填写文件编号引用 | 文件上传客户端和受控下载可在后续阶段按统一文件接口补齐 |

## 6. 验证结果

| 验证项 | 命令 | 结果 |
|---|---|---|
| 前端 S8 契约测试红灯 | `pnpm --filter @wecom-bft/admin test -- tests/s8-contracts.test.ts` | 首次因缺少 `src/services/http` 等模块失败，红灯有效 |
| 后端 S8 补口红灯 | `mvn -f backend/pom.xml "-Dtest=com.wecombft.interfaces.admin.AdminAuthAndPermissionControllerTest,com.wecombft.interfaces.admin.S7AfterSalesFinanceControllerTest" test` | 首次因菜单缺 `refund.reviews`、`GET /api/admin/refunds` 404 失败，红灯有效 |
| 后端定向测试 | `D:\java\apache-maven-3.9.15\bin\mvn.cmd -f backend\pom.xml "-Dtest=com.wecombft.interfaces.admin.AdminAuthAndPermissionControllerTest,com.wecombft.interfaces.admin.S7AfterSalesFinanceControllerTest" test` | 通过，`Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` |
| 管理端契约测试 | `pnpm --filter @wecom-bft/admin test -- tests/s8-contracts.test.ts` | 通过，5 个测试通过 |
| 管理端测试 | `pnpm --filter @wecom-bft/admin test` | 通过，2 个测试文件、6 个测试通过 |
| 管理端构建 | `pnpm --filter @wecom-bft/admin build` | 通过，Vite 产物生成成功 |
| 旧失败工程引用扫描 | `rg -n "old-failed|wecom-BFT-system-old-failed" -g "!docs/**" .` | 非文档目录无命中 |

## 7. 修改文件

| 类型 | 文件 |
|---|---|
| 前端页面 | `frontend/admin/src/App.vue`、`frontend/admin/src/styles.css` |
| 前端基础 | `frontend/admin/src/services/http.ts`、`frontend/admin/src/router/routes.ts`、`frontend/admin/src/utils/format.ts` |
| 前端测试 | `frontend/admin/tests/s8-contracts.test.ts` |
| 后端补口 | `backend/src/main/java/com/wecombft/interfaces/finance/AfterSalesFinanceController.java`、`backend/src/main/java/com/wecombft/application/finance/AfterSalesFinanceApplicationService.java`、`backend/src/main/java/com/wecombft/interfaces/dto/finance/AccountingMaterialPage.java` |
| 权限菜单 | `backend/src/main/java/com/wecombft/infrastructure/security/PermissionCatalog.java` |
| 后端测试 | `backend/src/test/java/com/wecombft/interfaces/admin/AdminAuthAndPermissionControllerTest.java`、`backend/src/test/java/com/wecombft/interfaces/admin/S7AfterSalesFinanceControllerTest.java` |
| 交接文档 | `docs/新系统开发设计文档/S8-PC管理端页面交接.md` |
