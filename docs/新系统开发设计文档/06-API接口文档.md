# 06-API接口文档

## 1. 文档定位

| 项目 | 说明 |
|---|---|
| 文档目标 | 为小程序端、PC 管理端、企微侧边栏、供货商 H5、审批通知和外部回调提供接口契约建议 |
| 上游依据 | `00-文档说明.md`、`01-需求规格说明书.md`、`02-业务流程与状态机说明.md`、`03-模块设计文档.md`、`04-领域模型与数据字典.md`、`05-数据库设计文档.md` |
| 设计边界 | 本文件只定义新系统接口口径，不继承历史接口、历史路径、历史字段和历史鉴权方式 |
| 下游影响 | 前后端联调、企微联调、供货商 H5 联调、外部 Mock、真实平台回调、权限矩阵和测试验收 |

## 2. 通用接口约定

### 2.1 路径与鉴权前缀

| 端侧 | 路径前缀 | 鉴权方式 | 说明 |
|---|---|---|---|
| 小程序端 | `/api/app` | 微信小程序登录态 `Authorization: Bearer <student_token>` | 学员交易前必须完成手机号授权 |
| PC 管理端 | `/api/admin` | 企业微信扫码或工作台登录态 `Authorization: Bearer <admin_token>` | 菜单、按钮、数据范围由角色权限控制 |
| 企微侧边栏 | `/api/wecom/sidebar` | 企业微信 JS-SDK 上下文签名 + 内部登录态 | 按 `wecom_external_user_id` 识别客户、线索和学员 |
| 供货商 H5 | `/api/supplier-h5` | 供货商访问令牌 `Authorization: Bearer <supplier_access_token>` | 仅可访问自身 `supplier_id` 关联采购单 |
| 审批通知 | `/api/collab` | 内部登录态或企微回调签名 | 承接审批、待办、通知、异常补偿 |
| 外部回调 | `/api/callbacks` | 平台签名、时间戳、随机串、回调事件号 | Mock、沙箱、真实平台共用该前缀；回调必须记录 `integration_callback_event` 并做幂等 |

### 2.2 字段命名与表设计口径

| 口径 | 约定 |
|---|---|
| 金额字段 | 接口与数据库保持一致，统一使用 `_cent` 后缀，如 `paid_amount_cent`、`invoice_amount_cent`，单位为分 |
| 状态枚举 | 复用 04 的英文编码，如 `PaymentStatus.PAID`、`OrderRefundStatus.REVIEWING`、`PurchaseStatus.WAIT_CONFIRM` |
| 主键字段 | 对外请求和响应使用领域字段名，如 `order_id`、`student_id`、`refund_id`、`invoice_id`；落库对应 05 表主键 `id` |
| 业务编号 | 所有核心单据返回业务编号，如 `order_no`、`merchant_order_no`、`refund_no`、`shipment_no`、`invoice_apply_no`、`purchase_no` |
| 订单状态 | 订单接口只返回 `payment_status`、`fulfillment_status`、`refund_status`、`invoice_status`，不定义 `order_status` |
| 快照字段 | 订单、发货、发票等返回 `course_snapshot`、`price_snapshot`、`tax_snapshot`、`receiver_snapshot` 等摘要，不暴露敏感原文 |
| 文件字段 | 文件上传后返回 `file_no`、`file_name`、`access_url`、`file_digest`，业务表只保存 `invoice_file`、`manual_voucher_file` 等文件引用 |
| 审计字段 | 管理端敏感操作必须产生 `audit_operation_log`，接口可返回 `trace_id` 便于排查 |

### 2.3 通用请求头

| 请求头 | 适用范围 | 必填 | 说明 |
|---|---|---|---|
| `Authorization` | 端侧业务接口 | 是 | Bearer Token |
| `Idempotency-Key` | 创建订单、退款申请、发货、开票、采购确认、材料上传等写操作 | 条件必填 | 客户端生成，服务端结合业务键防重复 |
| `X-Trace-Id` | 全部接口 | 否 | 请求链路 ID，缺省由服务端生成 |
| `X-Wecom-Signature` | 企微侧边栏、企微审批回调、企微通知回执 | 条件必填 | 企业微信签名校验 |
| `X-Callback-Timestamp` | 外部回调 | 条件必填 | 回调时间戳，用于防重放 |

### 2.4 通用响应结构

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | String | 业务码，成功为 `OK` |
| `message` | String | 用户可理解的提示 |
| `trace_id` | String | 链路 ID，对应审计、回调或异常日志 |
| `data` | Object / Array | 业务响应体 |
| `page` | Object | 分页接口返回，包含 `page_no`、`page_size`、`total` |

### 2.5 通用状态码

| HTTP 状态码 | 业务含义 | 常见业务码 |
|---|---|---|
| `200` | 查询或动作处理成功 | `OK` |
| `201` | 创建成功 | `CREATED` |
| `202` | 已受理，异步处理 | `ACCEPTED` |
| `204` | 删除、关闭、标记已读等无响应体成功 | `NO_CONTENT` |
| `400` | 请求参数错误 | `INVALID_ARGUMENT` |
| `401` | 未登录或登录态失效 | `UNAUTHORIZED` |
| `403` | 无权限或数据范围不允许 | `FORBIDDEN` |
| `404` | 对象不存在或无权查看 | `NOT_FOUND` |
| `409` | 并发、状态或幂等冲突 | `STATE_CONFLICT`、`DUPLICATE_REQUEST` |
| `422` | 业务校验失败 | `BUSINESS_RULE_BLOCKED` |
| `429` | 频率限制 | `TOO_MANY_REQUESTS` |
| `500` | 服务端异常 | `INTERNAL_ERROR` |

## 3. 小程序端接口

### 3.1 登录与学员

| 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|
| 微信登录 | 通过小程序 `code` 换取学员登录态，创建或更新 `sys_user`、`edu_student` 基础身份 | `POST` | `/api/app/auth/wechat-login` | `wx_code`、`encrypted_data`、`iv`、`source_channel` | `user_id`、`student_id`、`student_no`、`wx_openid`、`wx_unionid`、`mobile_bound`、`access_token`、`expires_in` | `200`、`400`、`401`、`409` | 小程序匿名态 | `wx_openid` 非空唯一；重复登录更新 `last_login_at` | 微信 code 失效；`wx_openid` 已绑定其他学员且需合并；用户被禁用 |
| 手机号授权 | 保存或合并学员手机号，交易前置校验 | `POST` | `/api/app/auth/phone-authorize` | `phone_code`、`wx_openid`、`student_id` | `student_id`、`student_no`、`mobile`、`merged_to_student_id`、`status` | `200`、`400`、`401`、`409`、`422` | 已登录学员 | `mobile + user_type`、`student.mobile` 唯一；重复授权返回现有档案 | 手机号解密失败；手机号已在其他学员档案且不能自动合并；学员已禁用 |
| 学员档案 | 获取当前学员基础信息 | `GET` | `/api/app/students/me` | 无 | `student_id`、`student_no`、`mobile`、`nickname`、`real_name`、`wx_openid`、`primary_lead_id`、`tags`、`status` | `200`、`401`、`404` | 已登录学员 | 查询接口不要求 | 登录态失效；学员已合并或禁用 |

### 3.2 课程与学习

| 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|
| 课程列表 | 展示可售课程和筛选结果 | `GET` | `/api/app/courses` | `keyword`、`category_code`、`course_type`、`page_no`、`page_size` | `course_id`、`course_no`、`course_title`、`cover_url`、`summary`、`course_type`、`sale_start_at`、`sale_end_at`、`min_sale_price_cent`、`status` | `200`、`400` | 可匿名浏览 | 查询接口不要求 | 课程未上架不返回；筛选参数非法 |
| 课程详情 | 获取课程详情、规格、章节摘要和课程群入口提示 | `GET` | `/api/app/courses/{course_id}` | `course_id` | `course_id`、`course_no`、`course_title`、`detail`、`teacher_user_id`、`course_group_qr`、`specs[]`、`lesson_summary[]`、`default_tax_rule_id`、`status` | `200`、`404`、`422` | 可匿名浏览 | 查询接口不要求 | 课程下架或待删除；无启用规格；规格缺税务规则时标记不可自动开票 |
| 学习中心 | 查询已开通课程权益 | `GET` | `/api/app/learning/entitlements` | `status`、`page_no`、`page_size` | `entitlement_id`、`entitlement_no`、`order_id`、`order_no`、`course_id`、`spec_id`、`status`、`opened_at`、`expire_at`、`remind_stopped`、`course_snapshot` | `200`、`401` | 已登录学员 | 查询接口不要求 | 权益被冻结或撤销时不返回学习入口；学员已合并需跳转主档案 |
| 课程学习页 | 获取权益对应章节课节和学习入口 | `GET` | `/api/app/learning/entitlements/{entitlement_id}/lessons` | `entitlement_id` | `entitlement_id`、`course_id`、`course_group_qr`、`nodes[]` 包含 `node_id`、`parent_node_id`、`node_type`、`title`、`lesson_type`、`live_start_at`、`live_end_at`、`replay_url`、`resource_file`、`status` | `200`、`401`、`403`、`404`、`422` | 已登录且拥有 `ACTIVE` 权益 | 查询接口不要求 | 权益冻结或撤销；课程课节隐藏；直播未开始 |

### 3.3 地址、订单与支付

| 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|
| 地址列表 | 管理学员收货地址 | `GET` | `/api/app/addresses` | `status` | `address_id`、`student_id`、`receiver_name`、`receiver_mobile`、`province`、`city`、`district`、`detail_address`、`postal_code`、`is_default`、`status` | `200`、`401` | 已登录学员 | 查询接口不要求 | 学员无地址时返回空列表 |
| 保存地址 | 新增或更新 `student_address` | `POST` | `/api/app/addresses` | `address_id`、`receiver_name`、`receiver_mobile`、`province`、`city`、`district`、`detail_address`、`postal_code`、`is_default` | `address_id`、`is_default`、`status`、`updated_at` | `200`、`201`、`400`、`401`、`409` | 已登录学员 | `Idempotency-Key` 建议用于新增；默认地址按 `student_id + is_default=1` 保证唯一 | 手机号格式错误；重复默认地址并发；地址被历史订单引用只更新主数据不影响快照 |
| 订单确认 | 下单前校验课程规格、价格、税务、库存和地址 | `POST` | `/api/app/orders/confirm` | `course_id`、`spec_id`、`quantity`、`address_id`、`source_channel`、`source_code` | `course_snapshot`、`price_snapshot`、`tax_snapshot`、`receiver_snapshot`、`total_amount_cent`、`discount_amount_cent`、`payable_amount_cent`、`contains_physical`、`stock_warning` | `200`、`400`、`401`、`422` | 已登录且手机号已授权 | 查询校验类接口不落订单，不要求 | 未授权手机号；课程未上架；规格停用；含实物缺地址；库存不足；税务规则缺失影响自动开票 |
| 创建订单 | 创建 `trade_order`、`trade_order_item` 并生成 `merchant_order_no` | `POST` | `/api/app/orders` | `course_id`、`spec_id`、`quantity`、`address_id`、`client_request_no`、`source_channel`、`source_code` | `order_id`、`order_no`、`merchant_order_no`、`payment_status`、`fulfillment_status`、`refund_status`、`invoice_status`、`payable_amount_cent`、`payment_expire_at`、`server_time` | `201`、`400`、`401`、`409`、`422` | 已登录且手机号已授权 | 必填 `Idempotency-Key`；建议 `student_id + spec_id + client_request_no`，重复请求返回同一 `order_id` | 重复创建；价格变化需重新确认；含实物缺地址；支付截止时间生成失败 |
| 拉起支付 | 为待支付订单生成微信支付或 Mock 支付参数 | `POST` | `/api/app/orders/{order_id}/pay` | `order_id`、`payment_channel` | `order_id`、`order_no`、`merchant_order_no`、`payment_params`、`payment_expire_at`、`server_time` | `200`、`401`、`403`、`404`、`409`、`422` | 订单所属学员 | 按 `merchant_order_no` 幂等，不重复创建订单 | 订单已支付或已关闭；支付超时；金额与订单不一致；支付适配器异常 |
| 取消待支付订单 | 学员主动关闭待支付订单 | `POST` | `/api/app/orders/{order_id}/cancel` | `order_id`、`close_reason` | `order_id`、`order_no`、`payment_status`、`closed_at`、`close_reason` | `200`、`401`、`403`、`404`、`409` | 订单所属学员 | `order_id + CLOSED` 幂等 | 已支付订单不可取消；已关闭重复取消返回当前状态 |
| 订单列表 | 查询学员订单和四类状态 | `GET` | `/api/app/orders` | `payment_status`、`fulfillment_status`、`refund_status`、`invoice_status`、`page_no`、`page_size` | `order_id`、`order_no`、`merchant_order_no`、`course_snapshot`、`paid_amount_cent`、`payable_amount_cent`、`payment_status`、`fulfillment_status`、`refund_status`、`invoice_status`、`payment_expire_at`、`server_time`、`created_at` | `200`、`401` | 订单所属学员 | 查询接口不要求 | 状态枚举非法；学员合并后需按主档案查询 |
| 订单详情 | 展示订单主单、明细和可操作入口 | `GET` | `/api/app/orders/{order_id}` | `order_id` | `order_id`、`order_no`、`items[]`、`course_snapshot`、`price_snapshot`、`tax_snapshot`、`receiver_snapshot`、`payment_status`、`fulfillment_status`、`refund_status`、`invoice_status`、`payment_expire_at`、`server_time`、`document_links[]` | `200`、`401`、`403`、`404` | 订单所属学员 | 查询接口不要求 | 订单不存在；已关闭订单隐藏支付以外动作；退款中阻止新开票 |

### 3.4 退款、发票、物流与通知

| 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|
| 提交退款申请 | 创建 `pay_refund` 并同步订单 `refund_status=REVIEWING` | `POST` | `/api/app/orders/{order_id}/refunds` | `order_id`、`apply_amount_cent`、`refund_reason`、`apply_description`、`refund_items[]` | `refund_id`、`refund_no`、`order_id`、`status`、`apply_amount_cent`、`need_return_goods`、`created_at` | `201`、`400`、`401`、`403`、`409`、`422` | 订单所属学员且订单已支付 | 必填 `Idempotency-Key`；同一订单存在 `REVIEWING`、`PROCESSING`、`MANUAL_REQUIRED` 时禁止重复申请 | 未支付订单；退款金额超过可退金额；已有进行中退款；已关闭订单 |
| 退款进度 | 查询退款单和状态流转 | `GET` | `/api/app/refunds/{refund_id}` | `refund_id` | `refund_id`、`refund_no`、`order_id`、`apply_amount_cent`、`approved_amount_cent`、`status`、`reject_reason`、`failure_reason`、`refunded_at`、`entitlement_action`、`need_return_goods` | `200`、`401`、`403`、`404` | 退款所属学员 | 查询接口不要求 | 退款单不存在；学员无权查看 |
| 发票抬头列表 | 管理学员发票抬头 | `GET` | `/api/app/invoice-titles` | `status` | `title_id`、`student_id`、`title_type`、`title_name`、`tax_no`、`email`、`is_default`、`status` | `200`、`401` | 已登录学员 | 查询接口不要求 | 无抬头时返回空列表 |
| 保存发票抬头 | 新增或更新 `student_invoice_title` | `POST` | `/api/app/invoice-titles` | `title_id`、`title_type`、`title_name`、`tax_no`、`email`、`is_default` | `title_id`、`title_type`、`title_name`、`tax_no`、`email`、`is_default`、`status` | `200`、`201`、`400`、`401`、`409`、`422` | 已登录学员 | 新增建议传 `Idempotency-Key`；默认抬头按 `student_id + is_default=1` 唯一 | 企业抬头缺 `title_name` 或 `tax_no`；邮箱格式错误；默认抬头并发冲突 |
| 申请开票 | 创建 `tax_invoice`，订单 `invoice_status=APPLIED` | `POST` | `/api/app/orders/{order_id}/invoices` | `order_id`、`title_id`、`email` | `invoice_id`、`invoice_apply_no`、`order_id`、`invoice_amount_cent`、`tax_amount_cent`、`status`、`tax_rule_snapshot` | `201`、`400`、`401`、`403`、`409`、`422` | 订单所属学员且订单已支付 | 必填 `Idempotency-Key`；同一 `order_id` 不允许同时存在 `APPLIED`、`TO_BE_ISSUED`、`ISSUED` 正数发票 | 未支付订单；退款中阻止申请；企业抬头不完整；订单缺税务规则 |
| 发票详情 | 查询开票、待开具、已开具、已红冲状态 | `GET` | `/api/app/invoices/{invoice_id}` | `invoice_id` | `invoice_id`、`invoice_apply_no`、`order_id`、`title_type`、`title_name`、`tax_no`、`email`、`invoice_amount_cent`、`tax_amount_cent`、`status`、`invoice_no`、`invoice_file`、`issued_at`、`red_invoice_no`、`red_invoice_file`、`red_reversed_at`、`failure_reason` | `200`、`401`、`403`、`404` | 发票所属学员 | 查询接口不要求 | 发票不存在；已红冲后只能查看不可再次申请 |
| 学员文件下载 | 下载发票文件、红字发票、退款凭证或学习相关附件 | `GET` | `/api/app/files/{file_no}/download` | `file_no` | 文件流或 `temporary_download_url`、`expire_at` | `200`、`302`、`401`、`403`、`404`、`410` | 文件所属学员且业务对象可见 | 查询接口不要求 | 文件不存在；文件已失效；文件所属业务对象无权访问 |
| 物流详情 | 查询订单发货单和物流轨迹 | `GET` | `/api/app/orders/{order_id}/logistics` | `order_id` | `shipment_id`、`shipment_no`、`status`、`logistics_company_name`、`tracking_no`、`shipped_at`、`signed_at`、`traces[]` 包含 `logistics_node_time`、`node_status`、`node_desc` | `200`、`401`、`403`、`404`、`422` | 订单所属学员 | 查询接口不要求 | 订单无需发货；未发货；物流轨迹暂未同步 |
| 通知列表 | 查询站内通知 | `GET` | `/api/app/notifications` | `read_status`、`scene_code`、`page_no`、`page_size` | `notification_id`、`notification_no`、`channel`、`scene_code`、`title`、`content`、`related_object_type`、`related_object_id`、`send_status`、`read_status`、`sent_at` | `200`、`401` | 已登录学员 | 查询接口不要求 | 通知不存在；订阅消息失败不影响站内通知 |
| 标记已读 | 标记站内通知 `read_status=READ` | `POST` | `/api/app/notifications/{notification_id}/read` | `notification_id` | `notification_id`、`read_status`、`read_at` | `200`、`204`、`401`、`403`、`404` | 通知接收学员 | `notification_id + receiver_student_id + READ` 幂等 | 通知不属于当前学员；非站内通知不可标记 |

## 4. PC 管理端接口

### 4.1 用户、权限与首页

| 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|
| 企微扫码登录 | 内部人员通过企业微信身份登录 PC 管理端 | `POST` | `/api/admin/auth/wecom-login` | `auth_code`、`redirect_uri` | `user_id`、`user_no`、`display_name`、`wecom_user_id`、`status`、`roles[]`、`access_token`、`expires_in` | `200`、`400`、`401`、`403` | 企微内部成员 | `wecom_user_id` 非空唯一；重复登录更新 `last_login_at` | 非企业成员；未分配角色仅返回角色申请权限；用户禁用 |
| 当前用户权限 | 获取菜单、按钮、数据范围和脱敏规则 | `GET` | `/api/admin/auth/me` | 无 | `user_id`、`display_name`、`roles[]`、`permission_codes[]`、`data_scope`、`menus[]`、`field_masks[]` | `200`、`401`、`403` | 已登录内部人员 | 查询接口不要求 | 角色停用；权限配置缺失 |
| 首页待办概览 | 查询退款、发货、开票、库存、对账等待办 | `GET` | `/api/admin/dashboard/todos` | `module`、`date_range` | `pending_refund_count`、`pending_shipment_count`、`pending_invoice_count`、`stock_warning_count`、`reconciliation_diff_count`、`approval_pending_count`、`material_pending_count` | `200`、`401`、`403` | 有首页访问权限 | 查询接口不要求 | 数据范围导致部分统计为 0；模块无权限 |
| 角色申请 | 未分配人员提交角色申请，生成 `approval_record` | `POST` | `/api/admin/role-applications` | `role_code`、`submit_reason` | `approval_id`、`approval_no`、`approval_type`、`status`、`submitted_at` | `201`、`400`、`401`、`409`、`422` | 已登录但未分配角色或管理员代提交 | 必填 `Idempotency-Key`；同一用户同一角色存在 `PENDING` 审批时禁止重复 | 角色不存在或停用；已有有效角色；重复待审批 |

### 4.2 线索与学员

| 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|
| 线索列表 | 运营查询留资、跟进和转化线索 | `GET` | `/api/admin/leads` | `keyword`、`mobile`、`source_channel`、`status`、`owner_user_id`、`page_no`、`page_size` | `lead_id`、`lead_no`、`name`、`mobile`、`source_channel`、`source_code`、`intent_course_id`、`owner_user_id`、`status`、`next_follow_at`、`latest_follow_at`、`student_id`、`converted_order_id` | `200`、`401`、`403` | 运营、客服、管理员按数据范围 | 查询接口不要求 | 无数据范围；手机号按权限脱敏 |
| 创建或更新线索 | PC 手工录入或编辑 `crm_lead` | `POST` | `/api/admin/leads` | `lead_id`、`name`、`mobile`、`source_channel`、`source_code`、`intent_course_id`、`owner_user_id`、`next_follow_at`、`remark` | `lead_id`、`lead_no`、`status`、`match_exception_flag`、`created_at`、`updated_at` | `200`、`201`、`400`、`401`、`403`、`409` | 运营、管理员 | 新增建议传 `Idempotency-Key`；公开来源可按 `source_channel + source_code + mobile + created_date` 合并 | 手机号格式错误；负责人无权限；外部联系人和手机号冲突 |
| 写跟进记录 | 记录线索跟进并更新 `latest_follow_at`、`next_follow_at` | `POST` | `/api/admin/leads/{lead_id}/follow-records` | `lead_id`、`follow_method`、`content`、`next_follow_at` | `follow_record_id`、`lead_id`、`status`、`latest_follow_at`、`next_follow_at` | `201`、`400`、`401`、`403`、`404` | 线索负责人、运营主管、管理员 | 必填 `Idempotency-Key`，避免重复提交跟进 | 线索不存在；已转化线索只允许备注；已放弃需先重新跟进 |
| 放弃或重新跟进 | 更新 `LeadStatus` | `POST` | `/api/admin/leads/{lead_id}/status` | `lead_id`、`target_status`、`abandon_reason`、`next_follow_at` | `lead_id`、`status`、`abandon_reason`、`next_follow_at`、`updated_at` | `200`、`400`、`401`、`403`、`404`、`409` | 线索负责人、运营主管、管理员 | `lead_id + target_status` 幂等 | 已转化线索不可放弃；恢复跟进缺少下次跟进时间 |
| 学员列表 | 查询 `edu_student` 及交易摘要 | `GET` | `/api/admin/students` | `keyword`、`mobile`、`status`、`tag`、`page_no`、`page_size` | `student_id`、`student_no`、`mobile`、`nickname`、`real_name`、`wecom_external_user_id`、`primary_lead_id`、`status`、`order_count`、`paid_amount_cent` | `200`、`401`、`403` | 运营、客服、教务按数据范围 | 查询接口不要求 | 手机号脱敏；讲师仅可看负责课程学员 |
| 学员详情 | 查询学员档案、订单、权益、退款和发票摘要 | `GET` | `/api/admin/students/{student_id}` | `student_id` | `student_id`、`student_no`、`user_id`、`mobile`、`nickname`、`real_name`、`wx_openid`、`wecom_external_user_id`、`primary_lead_id`、`tags`、`orders[]`、`entitlements[]`、`invoice_titles[]` | `200`、`401`、`403`、`404` | 运营、客服、教务、管理员按数据范围 | 查询接口不要求 | 学员已合并；跨课程或跨角色数据受限 |

### 4.3 课程与学习

| 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|
| 课程管理列表 | 查询课程主数据 | `GET` | `/api/admin/courses` | `keyword`、`course_type`、`status`、`teacher_user_id`、`page_no`、`page_size` | `course_id`、`course_no`、`course_title`、`course_type`、`teacher_user_id`、`category_code`、`default_tax_rule_id`、`status`、`published_at`、`delete_notice_deadline` | `200`、`401`、`403` | 教务、讲师、管理员按数据范围 | 查询接口不要求 | 讲师只能看负责课程 |
| 保存课程 | 创建或编辑 `course` | `POST` | `/api/admin/courses` | `course_id`、`course_title`、`course_type`、`cover_url`、`summary`、`detail`、`teacher_user_id`、`category_code`、`course_group_qr`、`default_tax_rule_id`、`sale_start_at`、`sale_end_at` | `course_id`、`course_no`、`status`、`updated_at` | `200`、`201`、`400`、`401`、`403`、`409`、`422` | 教务、管理员；讲师仅可维护内容字段 | 新增必填 `Idempotency-Key`；`course_no` 全局唯一 | 已上架课程关键价格字段需走审批或下架；已删除课程不可编辑 |
| 保存课程规格 | 创建或编辑 `course_spec` | `POST` | `/api/admin/courses/{course_id}/specs` | `course_id`、`spec_id`、`spec_name`、`sale_price_cent`、`origin_price_cent`、`stock_mode`、`contains_physical`、`sku_id`、`gift_sku_id`、`tax_rule_id`、`amount_split_snapshot`、`status`、`sort_no` | `spec_id`、`spec_no`、`course_id`、`status`、`contains_physical`、`updated_at` | `200`、`201`、`400`、`401`、`403`、`409`、`422` | 教务、管理员 | `course_id + spec_name` 唯一；新增传 `Idempotency-Key` | 含实物未绑定 SKU；税务规则缺失；规格已被订单引用后不允许破坏快照 |
| 保存章节课节 | 创建或编辑 `course_lesson_node` | `POST` | `/api/admin/courses/{course_id}/lesson-nodes` | `node_id`、`course_id`、`parent_node_id`、`node_type`、`title`、`lesson_type`、`live_start_at`、`live_end_at`、`replay_url`、`resource_file`、`status`、`sort_no`、`remind_enabled` | `node_id`、`course_id`、`parent_node_id`、`status`、`sort_no`、`updated_at` | `200`、`201`、`400`、`401`、`403`、`409`、`422` | 教务、讲师、管理员按课程范围 | `course_id + parent_node_id + sort_no` 唯一；新增传 `Idempotency-Key` | 章节挂到课节下；直播课节缺开始或结束时间；调整直播触发通知失败不回滚保存 |
| 提交课程审批 | 提交上架、下架或删除审批 | `POST` | `/api/admin/courses/{course_id}/approval` | `course_id`、`approval_type`、`submit_reason`、`delete_notice_deadline` | `approval_id`、`approval_no`、`approval_type`、`status`、`related_object_type`、`related_object_id` | `201`、`400`、`401`、`403`、`409`、`422` | 教务、管理员 | 同一课程同一审批类型存在 `PENDING` 时禁止重复 | 无启用规格不可上架；删除需通知已购学员；审批配置缺失 |
| 权益查询 | 管理端查看课程权益 | `GET` | `/api/admin/entitlements` | `student_id`、`course_id`、`order_id`、`status`、`page_no`、`page_size` | `entitlement_id`、`entitlement_no`、`student_id`、`user_id`、`order_id`、`order_no`、`course_id`、`spec_id`、`status`、`opened_at`、`frozen_at`、`revoked_at`、`source_refund_id` | `200`、`401`、`403` | 教务、客服、讲师、管理员按数据范围 | 查询接口不要求 | 讲师只能查询负责课程；退款成功后的冻结或撤销不允许手工绕过 |

### 4.4 交易订单与支付退款

| 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|
| 订单管理列表 | 按四类状态筛选订单 | `GET` | `/api/admin/orders` | `keyword`、`student_id`、`payment_status`、`fulfillment_status`、`refund_status`、`invoice_status`、`paid_at_start`、`paid_at_end`、`page_no`、`page_size` | `order_id`、`order_no`、`merchant_order_no`、`student_id`、`user_id`、`lead_id`、`total_amount_cent`、`payable_amount_cent`、`paid_amount_cent`、`payment_status`、`fulfillment_status`、`refund_status`、`invoice_status`、`paid_at`、`created_at` | `200`、`401`、`403` | 客服、运营、仓管、代账、管理员按数据范围 | 查询接口不要求 | 枚举非法；敏感字段按角色脱敏 |
| 订单详情 | 查询订单主单、明细、快照和单据链 | `GET` | `/api/admin/orders/{order_id}` | `order_id` | `order_id`、`order_no`、`merchant_order_no`、`student_id`、`user_id`、`items[]`、`course_snapshot`、`price_snapshot`、`tax_snapshot`、`receiver_snapshot`、`payment_status`、`fulfillment_status`、`refund_status`、`invoice_status`、`document_links[]`、`audit_logs[]` | `200`、`401`、`403`、`404` | 客服、运营、仓管、代账、管理员按数据范围 | 查询接口不要求 | 订单不存在；角色无权查看支付、税务或地址完整信息 |
| 支付记录列表 | 查询 `pay_payment` | `GET` | `/api/admin/payments` | `order_id`、`merchant_order_no`、`payment_result`、`paid_at_start`、`paid_at_end`、`page_no`、`page_size` | `payment_id`、`payment_no`、`order_id`、`order_no`、`merchant_order_no`、`channel`、`payment_method`、`payment_result`、`paid_amount_cent`、`external_payment_no`、`paid_at`、`failure_reason` | `200`、`401`、`403` | 客服、代账、管理员 | 查询接口不要求 | 支付异常记录需脱敏 `raw_callback_snapshot` |
| 退款处理列表 | 查询待审核、处理中、失败和人工退款 | `GET` | `/api/admin/refunds` | `status`、`order_no`、`student_id`、`created_at_start`、`created_at_end`、`page_no`、`page_size` | `refund_id`、`refund_no`、`order_id`、`order_no`、`student_id`、`apply_amount_cent`、`approved_amount_cent`、`refund_reason`、`status`、`refund_channel`、`failure_reason`、`refunded_at`、`need_return_goods` | `200`、`401`、`403` | 客服、代账、管理员 | 查询接口不要求 | 无退款权限；手机号脱敏 |
| 审核退款 | 客服拒绝或通过退款申请 | `POST` | `/api/admin/refunds/{refund_id}/review` | `refund_id`、`action` 为 `APPROVE` 或 `REJECT`、`approved_amount_cent`、`review_comment`、`reject_reason`、`refund_channel`、`entitlement_action` | `refund_id`、`refund_no`、`status`、`reviewer_user_id`、`review_comment`、`reject_reason`、`refund_channel` | `200`、`400`、`401`、`403`、`404`、`409`、`422` | 客服、管理员 | `refund_id + action` 幂等；状态仅允许从 `REVIEWING` 流转 | 非审核中状态；通过金额超可退；拒绝缺原因；原路退款超时进入 `PROCESSING` 待回调或失败 |
| 登记人工退款 | 线下退款完成后登记凭证 | `POST` | `/api/admin/refunds/{refund_id}/manual-complete` | `refund_id`、`refund_channel`、`manual_voucher_no`、`manual_voucher_file`、`refunded_at`、`entitlement_action`、`remark` | `refund_id`、`refund_no`、`status`、`manual_voucher_no`、`manual_voucher_file`、`refunded_at`、`entitlement_action` | `200`、`400`、`401`、`403`、`404`、`409`、`422` | 代账、客服主管、管理员 | `refund_id + manual_voucher_no` 幂等；完成后不得重复登记 | 缺凭证；状态不是 `MANUAL_REQUIRED` 或 `FAILED` 转人工；退款成功副作用部分失败进入异常补偿 |
| 重试退款 | 对 `FAILED` 退款重试原路退款或转人工 | `POST` | `/api/admin/refunds/{refund_id}/retry` | `refund_id`、`retry_mode` 为 `ORIGINAL` 或 `MANUAL_REQUIRED`、`remark` | `refund_id`、`refund_no`、`status`、`failure_reason`、`updated_at` | `202`、`400`、`401`、`403`、`404`、`409` | 客服、管理员 | `refund_id + retry_mode + Idempotency-Key` | 非失败状态不可重试；外部退款接口超时；已退款终态不被失败覆盖 |

### 4.5 履约库存与采购供应

| 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|
| SKU 列表 | 查询 `inventory_sku` 和库存预警 | `GET` | `/api/admin/inventory/skus` | `keyword`、`category_code`、`status`、`warning_only`、`page_no`、`page_size` | `sku_id`、`sku_no`、`sku_name`、`category_code`、`sku_type`、`unit`、`spec_attrs`、`default_supplier_id`、`cost_price_cent`、`current_stock`、`locked_stock`、`available_stock`、`safety_stock`、`status` | `200`、`401`、`403` | 仓管、管理员、代账只读 | 查询接口不要求 | 库存为负需显示异常；成本价按权限脱敏 |
| 保存 SKU | 新增或更新库存 SKU | `POST` | `/api/admin/inventory/skus` | `sku_id`、`sku_name`、`category_code`、`sku_type`、`unit`、`spec_attrs`、`default_supplier_id`、`cost_price_cent`、`safety_stock`、`status`、`image_url` | `sku_id`、`sku_no`、`status`、`available_stock`、`updated_at` | `200`、`201`、`400`、`401`、`403`、`409`、`422` | 仓管、管理员 | `sku_no` 全局唯一；`sku_name + spec_attrs_hash` 唯一；新增传 `Idempotency-Key` | 已被订单或采购引用后不可物理删除；规格属性冲突 |
| 库存流水 | 查询 `inventory_stock_flow` | `GET` | `/api/admin/inventory/stock-flows` | `sku_id`、`biz_type`、`biz_id`、`order_id`、`occurred_at_start`、`occurred_at_end`、`page_no`、`page_size` | `flow_id`、`flow_no`、`sku_id`、`biz_type`、`biz_id`、`biz_no`、`order_id`、`shipment_id`、`purchase_id`、`direction`、`quantity`、`before_stock`、`after_stock`、`operator_user_id`、`occurred_at` | `200`、`401`、`403` | 仓管、管理员、代账只读 | 查询接口不要求 | 无库存权限；订单维度补写失败需人工补偿 |
| 发货单列表 | 查询待发货、已发货、异常发货 | `GET` | `/api/admin/shipments` | `status`、`order_no`、`tracking_no`、`exception_flag`、`page_no`、`page_size` | `shipment_id`、`shipment_no`、`order_id`、`order_no`、`student_id`、`status`、`logistics_company_name`、`tracking_no`、`shipped_at`、`signed_at`、`exception_flag`、`exception_reason` | `200`、`401`、`403` | 仓管、客服、管理员 | 查询接口不要求 | 退款成功订单不可继续发货；地址按权限脱敏 |
| 确认发货 | 保存发货信息、扣减库存、同步订单 `fulfillment_status=SHIPPED` | `POST` | `/api/admin/shipments/{shipment_id}/ship` | `shipment_id`、`logistics_company_code`、`logistics_company_name`、`tracking_no`、`waybill_file`、`sku_lines[]`、`remark` | `shipment_id`、`shipment_no`、`order_id`、`status`、`tracking_no`、`shipped_at`、`stock_flow_ids[]` | `200`、`400`、`401`、`403`、`404`、`409`、`422` | 仓管、管理员 | 必填 `Idempotency-Key`；按 `shipment_no` 或 `tracking_no` 幂等，不重复扣减库存 | 订单未支付；退款已成功；库存不足；外部运单已生成但内部保存失败时 `exception_flag=true` |
| 人工确认签收 | 手工同步发货单签收 | `POST` | `/api/admin/shipments/{shipment_id}/sign` | `shipment_id`、`signed_at`、`remark` | `shipment_id`、`shipment_no`、`status`、`signed_at` | `200`、`401`、`403`、`404`、`409` | 仓管、客服、管理员 | `shipment_id + SIGNED` 幂等 | 未发货不可签收；重复签收返回当前状态 |
| 供货商列表 | 管理 `supplier` 主数据 | `GET` | `/api/admin/suppliers` | `keyword`、`access_status`、`status`、`page_no`、`page_size` | `supplier_id`、`supplier_no`、`supplier_name`、`short_name`、`contact_name`、`contact_mobile`、`contact_email`、`tax_no`、`access_status`、`status` | `200`、`401`、`403` | 仓管、管理员、代账只读 | 查询接口不要求 | 联系方式按权限脱敏 |
| 保存供货商 | 新增或更新供货商 | `POST` | `/api/admin/suppliers` | `supplier_id`、`supplier_name`、`short_name`、`contact_name`、`contact_mobile`、`contact_email`、`tax_no`、`address`、`settlement_method`、`access_status`、`status` | `supplier_id`、`supplier_no`、`status`、`access_status`、`updated_at` | `200`、`201`、`400`、`401`、`403`、`409`、`422` | 仓管、管理员 | `supplier_no`、`supplier_name` 唯一；新增传 `Idempotency-Key` | 停用供货商不可新建采购；有采购单后不可物理删除 |
| 创建采购单 | 创建 `purchase_order` 和明细，小额待确认、大额审批中 | `POST` | `/api/admin/purchases` | `supplier_id`、`purchase_items[]` 包含 `sku_id`、`quantity`、`unit_price_cent`、`submit_reason`、`expected_arrival_date` | `purchase_id`、`purchase_no`、`supplier_id`、`total_amount_cent`、`purchase_status`、`input_invoice_status`、`approval_id`、`threshold_snapshot_cent` | `201`、`400`、`401`、`403`、`409`、`422` | 仓管、管理员 | 必填 `Idempotency-Key`；`purchase_no` 全局唯一 | 供货商停用；SKU 停用；大额阈值缺失；审批创建失败 |
| 确认收货 | 采购收货入库，生成 `purchase_receipt` 和库存流水 | `POST` | `/api/admin/purchases/{purchase_id}/receive` | `purchase_id`、`inbound_batch_no`、`received_items[]` 包含 `sku_id`、`received_quantity`、`remark` | `receipt_id`、`receipt_no`、`purchase_id`、`purchase_no`、`purchase_status`、`received_at`、`stock_flow_ids[]` | `200`、`201`、`400`、`401`、`403`、`404`、`409`、`422` | 仓管、管理员 | `purchase_no + inbound_batch_no` 幂等；重复收货不重复入库 | 非已发货采购不可收货；收货数量与采购数量不一致进入人工确认；已完成不可重复入库 |

### 4.6 发票税务、对账代账、配置审计

| 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|
| 开票管理列表 | 查询 `tax_invoice` | `GET` | `/api/admin/invoices` | `status`、`order_no`、`invoice_no`、`student_id`、`issued_at_start`、`issued_at_end`、`page_no`、`page_size` | `invoice_id`、`invoice_apply_no`、`order_id`、`order_no`、`student_id`、`title_type`、`title_name`、`invoice_amount_cent`、`tax_amount_cent`、`status`、`invoice_channel`、`invoice_no`、`issued_at`、`failure_reason` | `200`、`401`、`403` | 代账、客服只读、管理员 | 查询接口不要求 | 税号、邮箱按权限脱敏；自动开票失败需转待开具 |
| 人工开票完成 | 上传发票号码和文件，状态到 `ISSUED` | `POST` | `/api/admin/invoices/{invoice_id}/issue-manual` | `invoice_id`、`invoice_no`、`invoice_file`、`issued_at`、`tax_amount_cent`、`remark` | `invoice_id`、`invoice_apply_no`、`status`、`invoice_no`、`invoice_file`、`issued_at` | `200`、`400`、`401`、`403`、`404`、`409`、`422` | 代账、管理员 | `invoice_apply_no + invoice_no` 幂等 | 非 `APPLIED` 或 `TO_BE_ISSUED` 不可开具；发票号重复；文件缺失 |
| 红冲完成 | 保存红字发票或红冲凭证，状态到 `RED_REVERSED` | `POST` | `/api/admin/invoices/{invoice_id}/red-reverse` | `invoice_id`、`source_refund_id`、`red_invoice_no`、`red_invoice_file`、`red_reversed_at`、`remark` | `invoice_id`、`invoice_apply_no`、`status`、`red_invoice_no`、`red_invoice_file`、`red_reversed_at` | `200`、`400`、`401`、`403`、`404`、`409`、`422` | 代账、管理员 | `invoice_apply_no + red_invoice_no` 幂等 | 未开具不可红冲；红冲失败保留 `ISSUED` 并记录 `failure_reason`；红冲不能替代退款 |
| 税务规则保存 | 管理 `tax_rule` | `POST` | `/api/admin/tax-rules` | `rule_id`、`rule_name`、`tax_category`、`tax_rate`、`invoice_item_name`、`status`、`description` | `rule_id`、`rule_no`、`status`、`updated_at` | `200`、`201`、`400`、`401`、`403`、`409` | 代账、管理员 | `rule_no`、`rule_name` 唯一；新增传 `Idempotency-Key` | 已引用规则停用不影响历史快照；税率格式非法 |
| 上传对账账单 | 导入微信支付结算单或交易账单生成对账批次 | `POST` | `/api/admin/reconciliation/batches` | `bill_month`、`bill_source`、`file_no`、`file_digest` | `batch_id`、`batch_no`、`bill_month`、`bill_source`、`import_status`、`total_count`、`matched_count`、`diff_count` | `201`、`400`、`401`、`403`、`409`、`422` | 代账、管理员 | `bill_month + bill_source + file_digest` 防重复导入 | 文件格式错误；重复上传；账单月份不匹配；导入失败记录 `failure_reason` |
| 对账明细 | 查询对账结果和差异 | `GET` | `/api/admin/reconciliation/batches/{batch_id}/records` | `batch_id`、`result`、`record_type`、`order_no`、`checked_flag`、`page_no`、`page_size` | `reconciliation_id`、`batch_no`、`bill_month`、`record_type`、`order_id`、`order_no`、`payment_id`、`refund_id`、`merchant_order_no`、`external_transaction_no`、`system_amount_cent`、`bill_amount_cent`、`fee_amount_cent`、`result`、`difference_reason`、`checked_flag` | `200`、`401`、`403` | 代账、管理员 | 查询接口不要求 | 对账批次不存在；差异原因缺失时提示补充 |
| 标记核对 | 人工确认对账差异 | `POST` | `/api/admin/reconciliation/records/{reconciliation_id}/check` | `reconciliation_id`、`difference_reason`、`checked_flag`、`remark` | `reconciliation_id`、`checked_flag`、`checked_by`、`checked_at`、`difference_reason` | `200`、`400`、`401`、`403`、`404`、`409` | 代账、管理员 | `reconciliation_id + checked_flag` 幂等 | 已核对重复提交返回当前状态；不能反向修改订单或支付流水 |
| 代账材料管理 | 创建材料申请、查询材料列表 | `POST` | `/api/admin/accounting/materials` | `material_type`、`related_month`、`order_id`、`related_object_type`、`related_object_id`、`request_user_id`、`assignee_user_id`、`purpose`、`due_at` | `material_id`、`material_no`、`status`、`related_month`、`related_object_type`、`related_object_no`、`due_at` | `201`、`400`、`401`、`403`、`409`、`422` | 代账、管理员 | `material_no` 唯一；新增传 `Idempotency-Key` | 关联对象不存在；待补充材料关闭缺原因 |
| 上传或确认材料 | 上传文件、确认可用或关闭材料 | `POST` | `/api/admin/accounting/materials/{material_id}/actions` | `material_id`、`action` 为 `UPLOAD`、`CONFIRM`、`CLOSE`、`file_refs`、`closed_reason`、`remark` | `material_id`、`material_no`、`status`、`file_refs`、`uploaded_by`、`uploaded_at`、`confirmed_by`、`confirmed_at`、`closed_reason` | `200`、`400`、`401`、`403`、`404`、`409`、`422` | 材料责任人、代账、管理员 | `material_id + action + Idempotency-Key` | 确认时无文件且无系统明细；关闭缺原因；状态流转非法 |
| 文件上传 | 上传发票文件、红冲凭证、退款凭证、对账账单和代账材料附件 | `POST` | `/api/admin/files` | `file`、`biz_type`、`biz_id`、`order_id` | `file_id`、`file_no`、`file_name`、`file_type`、`storage_key`、`access_url`、`file_digest`、`file_size`、`biz_type`、`biz_id`、`order_id`、`status`、`uploaded_by`、`uploaded_at` | `201`、`400`、`401`、`403`、`409`、`422` | 按业务模块权限校验，如代账、客服、仓管、管理员 | `file_digest + biz_type + biz_id` 幂等，重复上传返回已有 `file_no` | 文件格式不允许；文件过大；业务对象不存在；敏感文件访问需鉴权 |
| 文件下载 | 统一受控下载发票、红冲凭证、退款凭证、面单、对账账单和代账材料 | `GET` | `/api/admin/files/{file_no}/download` | `file_no`、`download_reason` | 文件流或 `temporary_download_url`、`expire_at` | `200`、`302`、`400`、`401`、`403`、`404`、`410` | 按业务模块权限、数据范围和字段脱敏权限校验 | 查询接口不要求；下载动作写操作日志 | 文件不存在；文件已失效；业务对象无权访问；下载原因缺失 |
| 系统配置查询 | 查询支付、物流、开票、采购阈值、通知模板和 Mock 接入模式的脱敏配置 | `GET` | `/api/admin/system/configs` | `config_group` | `config_group`、`config_items[]` 包含 `config_key`、`display_name`、`masked_value`、`editable_flag`、`updated_at`、`updated_by` | `200`、`401`、`403` | 超级管理员；部分配置代账或仓管只读 | 查询接口不要求 | 真实密钥不明文返回；无权限字段隐藏 |
| 系统配置保存 | 修改支付、物流、开票、采购阈值、通知模板或 Mock/沙箱/真实接入模式 | `POST` | `/api/admin/system/configs` | `config_group`、`config_items[]`、`change_reason` | `config_group`、`updated_keys[]`、`version`、`updated_at`、`audit_log_id` | `200`、`400`、`401`、`403`、`409`、`422` | 超级管理员 | `config_group + version` 乐观锁；重复提交按 `Idempotency-Key` 防抖 | 版本冲突；敏感密钥格式错误；接入模式切换缺少必需配置；必须写 `audit_operation_log` |
| Mock 场景列表 | 查询可用于演示和联调的 Mock 场景、能力类型和默认参数 | `GET` | `/api/admin/mock/scenes` | `capability`、`enabled_only` | `scenes[]` 包含 `scene_code`、`capability`、`display_name`、`description`、`default_payload`、`enabled` | `200`、`401`、`403` | 超级管理员、测试授权角色 | 查询接口不要求 | 当前环境禁用 Mock；无权限查看场景 |
| 触发 Mock 场景 | 触发指定 Mock 场景并投递到统一回调链路 | `POST` | `/api/admin/mock/scenes/{scene}/trigger` | `scene`、`target_no`、`payload_override`、`trigger_reason` | `mock_event_no`、`scene_code`、`callback_path`、`processing_status`、`integration_callback_event_no` | `202`、`400`、`401`、`403`、`404`、`409`、`422` | 超级管理员、测试授权角色 | 必填 `Idempotency-Key`；`scene + target_no + Idempotency-Key` 防重复投递 | 场景不存在；目标单据不存在；非 Mock 环境；重复触发同一幂等键 |
| 操作日志查询 | 查询 `audit_operation_log` | `GET` | `/api/admin/audit/logs` | `operation_module`、`operation_type`、`target_type`、`target_id`、`order_id`、`operator_user_id`、`occurred_at_start`、`occurred_at_end`、`page_no`、`page_size` | `log_id`、`trace_id`、`operator_user_id`、`operator_name`、`operation_module`、`operation_type`、`target_type`、`target_id`、`target_no`、`order_id`、`result`、`failure_reason`、`occurred_at` | `200`、`401`、`403` | 管理员、审计授权角色 | 查询接口不要求 | 日志不可编辑不可删除；敏感快照脱敏 |

## 5. 企微侧边栏接口

| 模块 | 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|---|
| 客户识别 | 侧边栏上下文识别 | 按外部联系人识别线索、学员和订单摘要 | `GET` | `/api/wecom/sidebar/context` | `wecom_external_user_id`、`wecom_user_id`、`corp_id` | `wecom_external_user_id`、`lead`、`student`、`latest_orders[]`、`match_exception_flag`、`allowed_actions[]` | `200`、`400`、`401`、`403`、`404` | 运营、客服、管理员；需企微上下文签名 | 查询接口不要求 | 外部联系人未建档；手机号匹配冲突标记 `match_exception_flag=true` |
| 线索建档 | 侧边栏创建或绑定线索 | 运营在客户会话中创建 `crm_lead` | `POST` | `/api/wecom/sidebar/leads` | `wecom_external_user_id`、`name`、`mobile`、`source_channel`、`source_code`、`intent_course_id`、`owner_user_id` | `lead_id`、`lead_no`、`status`、`match_exception_flag`、`student_id` | `201`、`400`、`401`、`403`、`409`、`422` | 运营、管理员 | `wecom_external_user_id + mobile + created_date` 或 `Idempotency-Key` | 手机号与现有外部联系人冲突；线索已转化；运营无数据范围 |
| 写跟进 | 侧边栏记录跟进 | 快速写入跟进记录并更新线索状态 | `POST` | `/api/wecom/sidebar/leads/{lead_id}/follow-records` | `lead_id`、`follow_method`、`content`、`next_follow_at` | `follow_record_id`、`lead_id`、`status`、`latest_follow_at`、`next_follow_at` | `201`、`400`、`401`、`403`、`404` | 线索负责人、运营主管 | 必填 `Idempotency-Key` | 线索不属于当前运营；已放弃需先恢复；内容为空 |
| 匹配学员 | 侧边栏把外部联系人绑定到学员或线索 | 处理手机号和企微外部联系人匹配 | `POST` | `/api/wecom/sidebar/match` | `wecom_external_user_id`、`lead_id`、`student_id`、`mobile`、`match_reason` | `lead_id`、`student_id`、`wecom_external_user_id`、`match_exception_flag`、`updated_at` | `200`、`400`、`401`、`403`、`404`、`409`、`422` | 运营主管、管理员 | `wecom_external_user_id + student_id` 幂等 | 外部联系人与手机号匹配不同对象；学员已合并；跨运营数据范围 |
| 学员摘要 | 侧边栏查看学员订单和售后摘要 | 支持运营跟进时快速识别成交与售后状态 | `GET` | `/api/wecom/sidebar/students/{student_id}/summary` | `student_id` | `student_id`、`student_no`、`mobile`、`orders[]` 包含 `order_no`、`payment_status`、`refund_status`、`invoice_status`、`paid_amount_cent`、`entitlements[]` | `200`、`401`、`403`、`404` | 运营、客服按数据范围 | 查询接口不要求 | 无权查看交易字段；手机号脱敏 |

## 6. 供货商 H5 接口

| 模块 | 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|---|
| 鉴权 | 供货商访问令牌校验 | 换取供货商 H5 会话 | `POST` | `/api/supplier-h5/auth/token` | `access_token`、`supplier_no` | `supplier_id`、`supplier_no`、`supplier_name`、`contact_name`、`access_status`、`session_token`、`expires_in` | `200`、`400`、`401`、`403` | 供货商访问令牌 | 同一 `access_token` 有效期内重复返回同会话 | 令牌过期；供货商停用；令牌不匹配供应商 |
| 采购单列表 | 查询自身待确认和处理中采购单 | `GET` | `/api/supplier-h5/purchases` | `purchase_status`、`page_no`、`page_size` | `purchase_id`、`purchase_no`、`total_amount_cent`、`purchase_status`、`input_invoice_status`、`expected_arrival_date`、`supplier_confirm_at`、`logistics_company_name`、`tracking_no` | `200`、`401`、`403` | 当前供货商 | 查询接口不要求 | 不返回其他供货商采购单；审批中采购不可见 |
| 采购单详情 | 查看采购明细和状态 | `GET` | `/api/supplier-h5/purchases/{purchase_id}` | `purchase_id` | `purchase_id`、`purchase_no`、`supplier_id`、`purchase_items[]`、`total_amount_cent`、`purchase_status`、`input_invoice_status`、`expected_arrival_date`、`supplier_reject_reason`、`logistics_company_name`、`tracking_no`、`input_invoice_no`、`input_invoice_amount_cent`、`input_invoice_file` | `200`、`401`、`403`、`404` | 当前供货商 | 查询接口不要求 | 采购单不属于当前供货商；采购已取消 |
| 确认接单 | 供货商确认采购，状态到 `CONFIRMED` | `POST` | `/api/supplier-h5/purchases/{purchase_id}/confirm` | `purchase_id`、`expected_arrival_date`、`remark` | `purchase_id`、`purchase_no`、`purchase_status`、`supplier_confirm_at`、`expected_arrival_date` | `200`、`400`、`401`、`403`、`404`、`409` | 当前供货商 | `purchase_id + CONFIRMED` 幂等 | 非 `WAIT_CONFIRM` 状态不可确认；采购已取消或审批拒绝 |
| 拒绝接单 | 供货商拒绝采购，状态到 `REJECTED` | `POST` | `/api/supplier-h5/purchases/{purchase_id}/reject` | `purchase_id`、`supplier_reject_reason` | `purchase_id`、`purchase_no`、`purchase_status`、`supplier_reject_reason` | `200`、`400`、`401`、`403`、`404`、`409`、`422` | 当前供货商 | `purchase_id + REJECTED` 幂等 | 拒绝原因为空；非 `WAIT_CONFIRM` 不可拒绝 |
| 填写采购物流 | 供货商发货，状态到 `SHIPPED` | `POST` | `/api/supplier-h5/purchases/{purchase_id}/logistics` | `purchase_id`、`logistics_company_name`、`tracking_no`、`remark` | `purchase_id`、`purchase_no`、`purchase_status`、`logistics_company_name`、`tracking_no` | `200`、`400`、`401`、`403`、`404`、`409`、`422` | 当前供货商 | `purchase_id + tracking_no` 幂等 | 非 `CONFIRMED` 状态不可发货；物流单号重复或格式非法 |
| 回填进项发票 | 供货商上传进项发票 | `POST` | `/api/supplier-h5/purchases/{purchase_id}/input-invoice` | `purchase_id`、`input_invoice_no`、`input_invoice_amount_cent`、`input_invoice_file` | `purchase_id`、`purchase_no`、`input_invoice_status`、`input_invoice_no`、`input_invoice_amount_cent`、`input_invoice_file` | `200`、`400`、`401`、`403`、`404`、`409`、`422` | 当前供货商 | `purchase_id + input_invoice_no` 幂等 | 金额不匹配进入代账风险提示；文件缺失；采购已取消 |
| 供货商文件下载 | 下载当前供货商可见的采购单、面单或进项票附件 | `GET` | `/api/supplier-h5/files/{file_no}/download` | `file_no` | 文件流或 `temporary_download_url`、`expire_at` | `200`、`302`、`401`、`403`、`404`、`410` | 当前供货商且文件归属自身采购单 | 查询接口不要求 | 文件不存在；采购单不属于当前供货商；文件已失效 |

## 7. 审批通知接口

| 模块 | 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|---|
| 审批 | 审批列表 | 查询当前审批人或申请人的 `approval_record` | `GET` | `/api/collab/approvals` | `role` 为 `APPLICANT` 或 `APPROVER`、`approval_type`、`status`、`page_no`、`page_size` | `approval_id`、`approval_no`、`approval_type`、`title`、`applicant_user_id`、`approver_user_id`、`related_object_type`、`related_object_id`、`related_object_no`、`status`、`submitted_at`、`finished_at` | `200`、`401`、`403` | 内部人员按审批权限 | 查询接口不要求 | 无审批权限；企微审批实例未同步 |
| 审批 | 处理审批 | 审批通过或拒绝并回写业务模块 | `POST` | `/api/collab/approvals/{approval_id}/actions` | `approval_id`、`action` 为 `APPROVE` 或 `REJECT`、`approval_comment` | `approval_id`、`approval_no`、`status`、`approval_comment`、`finished_at`、`business_result` | `200`、`400`、`401`、`403`、`404`、`409`、`422` | 当前审批人、超级管理员 | `approval_id + action` 幂等；同一 `approval_type + related_object_type + related_object_id + PENDING` 唯一 | 非待审批；审批人不匹配；业务状态已变化导致回写失败 |
| 待办 | 待办任务列表 | 查询退款、发货、开票、红冲、库存、材料等待办 | `GET` | `/api/collab/todos` | `scene_code`、`related_object_type`、`status`、`page_no`、`page_size` | `todo_id`、`scene_code`、`title`、`content`、`receiver_user_id`、`related_object_type`、`related_object_id`、`order_id`、`status`、`created_at` | `200`、`401`、`403` | 内部人员按角色待办 | 查询接口不要求 | 待办对象被业务关闭；无数据范围 |
| 通知 | 站内通知查询 | 查询内部人员站内通知和企微卡片发送记录 | `GET` | `/api/collab/notifications` | `channel`、`scene_code`、`send_status`、`read_status`、`page_no`、`page_size` | `notification_id`、`notification_no`、`receiver_user_id`、`channel`、`scene_code`、`title`、`content`、`related_object_type`、`related_object_id`、`order_id`、`send_status`、`read_status`、`sent_at`、`failure_reason` | `200`、`401`、`403` | 当前接收人或通知管理员 | 查询接口不要求 | 企微卡片发送失败仅记录失败原因，不回滚主业务 |
| 通知 | 标记内部通知已读 | 更新 `notify_message.read_status=READ` | `POST` | `/api/collab/notifications/{notification_id}/read` | `notification_id` | `notification_id`、`read_status`、`read_at` | `200`、`204`、`401`、`403`、`404` | 当前接收人 | `notification_id + receiver_user_id + READ` 幂等 | 非当前用户通知；非站内通知不可标记 |
| 异常补偿 | 异常任务重试或关闭 | 处理支付记录补写、权益补开、发货异常、开票红冲失败等补偿 | `POST` | `/api/collab/compensations/{task_id}/actions` | `task_id`、`action` 为 `RETRY` 或 `CLOSE`、`failure_reason`、`remark` | `task_id`、`related_object_type`、`related_object_id`、`order_id`、`processing_status`、`retry_count`、`failure_reason`、`processed_at` | `200`、`202`、`400`、`401`、`403`、`404`、`409` | 对应模块负责人、管理员 | `task_id + action + retry_count` 幂等 | 主状态终态不可被失败回调覆盖；关闭必须填写原因；重试仍失败保留待处理 |
| 企微卡片 | 发送企微通知卡片 | 向审批人、仓管、代账等发送企微卡片 | `POST` | `/api/collab/wecom-cards` | `receiver_user_id`、`scene_code`、`template_code`、`title`、`content`、`related_object_type`、`related_object_id`、`order_id` | `notification_id`、`notification_no`、`channel`、`send_status`、`sent_at`、`failure_reason` | `202`、`400`、`401`、`403`、`409` | 系统服务、管理员 | `idempotency_key + channel + receiver_user_id` 唯一 | 接收人未绑定 `wecom_user_id`；企微发送失败不影响业务主状态 |

## 8. 外部回调接口

### 8.1 回调通用处理规则

Mock、沙箱和真实平台回调共用本节路径，通过接入模式、适配层、签名校验、事件号和幂等键区分来源，不新增 Mock 专用业务回调路径。

| 场景 | HTTP 状态码 | 响应业务码 | 处理要求 |
|---|---|---|---|
| 验签失败 | `401` | `CALLBACK_SIGN_FAILED` | 拒绝业务处理，不写入支付、退款、物流、发票等核心业务单据；只允许保留安全审计摘要 |
| 重复回调 | `200` | `OK` | 返回已有处理结果，不重复生成支付记录、权益、发货、通知或发票状态变更 |
| 验签通过但业务状态冲突 | `200` 或 `202` | 业务错误码 | 记录 `integration_callback_event` 和异常待处理，不要求外部平台无限重试 |
| 参数格式非法 | `400` | `INVALID_ARGUMENT` | 不进入业务处理；保留 `trace_id` 和脱敏摘要便于排查 |

### 8.2 回调接口列表

| 回调来源 | 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|---|
| 微信支付或 Mock 支付 | 支付结果回调 | 写入 `integration_callback_event` 和 `pay_payment`，驱动订单支付成功后置动作 | `POST` | `/api/callbacks/payments/wechat` | `event_no`、`merchant_order_no`、`external_payment_no`、`paid_amount_cent`、`payment_result`、`paid_at`、`raw_snapshot`、签名字段 | `processing_status`、`order_id`、`payment_id`、`payment_no`、`payment_status`、`idempotency_key` | `200`、`202`、`400`、`401`、`409`、`422` | 平台签名校验通过 | `source_system + event_no`、`merchant_order_no + external_payment_no`；重复成功回调不重复开通权益、发货和通知 | 金额不一致进入人工核对；超时后成功回调进入异常待处理；签名失败拒绝 |
| 微信退款或 Mock 退款 | 退款结果回调 | 更新 `pay_refund.status`，退款成功后执行权益、提醒、红冲、对账副作用 | `POST` | `/api/callbacks/refunds/wechat` | `event_no`、`refund_no`、`external_refund_no`、`refund_status`、`refunded_amount_cent`、`refunded_at`、`failure_reason`、`raw_snapshot`、签名字段 | `processing_status`、`refund_id`、`refund_no`、`order_id`、`status`、`idempotency_key` | `200`、`202`、`400`、`401`、`409`、`422` | 平台签名校验通过 | `source_system + event_no`、`refund_no + external_refund_no`；`REFUNDED` 终态不被失败回调覆盖 | 乱序失败回调；金额不一致；退款单不存在；副作用部分失败生成补偿任务 |
| 物流平台或 Mock 物流 | 物流轨迹回调 | 写入 `logistics_trace`，签收时同步 `fulfillment_shipment.status=SIGNED` | `POST` | `/api/callbacks/logistics/traces` | `event_no`、`shipment_no`、`tracking_no`、`logistics_node_time`、`node_status`、`node_desc`、`signed_flag`、`raw_snapshot`、签名字段 | `processing_status`、`shipment_id`、`order_id`、`tracking_no`、`status` | `200`、`202`、`400`、`401`、`409`、`422` | 平台签名校验通过 | `tracking_no + logistics_node_time + node_status`；重复轨迹不重复写入 | 未找到运单；轨迹乱序；已签收重复回调；签名失败 |
| 电子发票或 Mock 开票 | 开票结果回调 | 自动开票成功或失败，更新 `tax_invoice` | `POST` | `/api/callbacks/invoices/issue` | `event_no`、`invoice_apply_no`、`invoice_no`、`invoice_file`、`tax_amount_cent`、`issued_at`、`status`、`failure_reason`、`raw_snapshot`、签名字段 | `processing_status`、`invoice_id`、`invoice_apply_no`、`order_id`、`status` | `200`、`202`、`400`、`401`、`409`、`422` | 平台签名校验通过 | `invoice_apply_no + invoice_no`；成功重复回调不重复通知 | 自动失败转 `TO_BE_ISSUED`；发票号重复；订单已退款且未开票需人工确认 |
| 电子发票或 Mock 红冲 | 红冲结果回调 | 保存红字发票，状态到 `RED_REVERSED` | `POST` | `/api/callbacks/invoices/red-reverse` | `event_no`、`invoice_apply_no`、`red_invoice_no`、`red_invoice_file`、`red_reversed_at`、`status`、`failure_reason`、`raw_snapshot`、签名字段 | `processing_status`、`invoice_id`、`invoice_apply_no`、`status`、`red_invoice_no` | `200`、`202`、`400`、`401`、`409`、`422` | 平台签名校验通过 | `invoice_apply_no + red_invoice_no`；红冲完成后不重复处理 | 发票未开具不可红冲；红冲失败保留 `ISSUED` 并进入补偿 |
| 企业微信审批 | 企微审批回调 | 同步企微审批实例结果到 `approval_record`，由业务模块回写状态 | `POST` | `/api/callbacks/wecom/approvals` | `event_no`、`wecom_approval_id`、`approval_status`、`approval_comment`、`approver_user_id`、`finished_at`、`raw_snapshot`、签名字段 | `processing_status`、`approval_id`、`approval_no`、`status`、`business_result` | `200`、`202`、`400`、`401`、`409`、`422` | 企微签名校验通过 | `source_system + event_no`、`wecom_approval_id`；重复回调不重复业务回写 | 审批单不存在；业务对象状态已变化；拒绝原因缺失 |
| 企业微信通知 | 企微卡片回执 | 更新企微卡片发送、点击或失败状态 | `POST` | `/api/callbacks/wecom/cards` | `event_no`、`notification_no`、`receiver_user_id`、`send_status`、`failure_reason`、`raw_snapshot`、签名字段 | `processing_status`、`notification_id`、`notification_no`、`send_status` | `200`、`202`、`400`、`401`、`409` | 企微签名校验通过 | `source_system + event_no`、`notification_no + receiver_user_id + send_status` | 发送失败不回滚主业务；通知不存在时记录回调异常 |

## 9. 基础设施与运维接口

| 接口 | 用途 | 请求方法 | 路径建议 | 请求参数 | 响应字段 | 状态码 | 权限要求 | 幂等要求 | 异常场景 |
|---|---|---|---|---|---|---|---|---|---|
| 存活检查 | 判断后端进程是否存活，用于 Nginx、Docker Compose 或负载均衡基础探活 | `GET` | `/api/health` | 无 | `status`、`app_name`、`version`、`server_time`、`trace_id` | `200`、`503` | 无需登录；仅返回非敏感信息 | 查询接口不要求 | 进程启动中或进入维护模式返回 `503` |
| 就绪检查 | 判断依赖是否可用，用于上线、演示前检查和容器健康检查 | `GET` | `/api/health/ready` | 无 | `status`、`checks[]` 包含 `name`、`status`、`latency_ms`、`message`，覆盖数据库、Redis、文件存储、外部适配配置 | `200`、`503` | 内网或运维授权访问；不得返回密钥 | 查询接口不要求 | 数据库、Redis、文件存储或必要配置不可用时返回 `503` |

## 10. 接口与数据库表映射

| 业务域 | 主要接口分组 | 主要落库表 |
|---|---|---|
| 用户与权限 | 小程序登录、PC 登录、角色申请、权限查询 | `sys_user`、`sys_role`、`sys_user_role`、`approval_record`、`audit_operation_log` |
| 线索与学员 | 线索管理、学员管理、企微侧边栏、公开留资 | `crm_lead`、`edu_student`、`student_address`、`student_invoice_title` |
| 课程与学习 | 课程、规格、课节、学习中心、权益查询 | `course`、`course_spec`、`course_lesson_node`、`learning_entitlement` |
| 交易订单 | 订单确认、创建、列表、详情、单据链 | `trade_order`、`trade_order_item`、`order_document_link` |
| 支付退款 | 拉起支付、支付回调、退款申请、审核、人工退款 | `pay_payment`、`pay_refund`、`pay_refund_item`、`integration_callback_event` |
| 履约库存 | 发货、物流、库存、库存流水 | `fulfillment_shipment`、`fulfillment_shipment_item`、`logistics_trace`、`inventory_sku`、`inventory_stock_flow` |
| 采购供应 | 供货商、采购单、供货商 H5、收货入库 | `supplier`、`supplier_sku`、`purchase_order`、`purchase_order_item`、`purchase_receipt` |
| 发票税务 | 发票抬头、开票申请、人工开票、红冲、税务规则 | `student_invoice_title`、`tax_rule`、`tax_invoice`、`tax_invoice_item` |
| 对账代账 | 账单导入、差异核对、材料申请和上传 | `finance_reconciliation_batch`、`finance_reconciliation_record`、`acct_material`、`acct_material_relation` |
| 通知审批 | 审批、待办、站内通知、企微卡片、异常补偿 | `approval_record`、`notify_message`、`integration_callback_event`、`audit_operation_log` |
| 文件与审计 | 文件上传、受控下载、导出、操作日志 | `sys_file_asset`、`audit_operation_log` |

## 11. 幂等与异常补偿汇总

| 场景 | 幂等键 | 成功处理 | 异常补偿 |
|---|---|---|---|
| 创建订单 | `Idempotency-Key` + `student_id + spec_id + client_request_no` | 返回同一 `order_id` 和 `merchant_order_no` | 订单创建成功但响应失败时按幂等键查询返回 |
| 支付回调 | `merchant_order_no + external_payment_no` | 支付状态到 `PAID`，生成支付记录、权益、发货任务和通知 | 金额不一致、超时后成功、后置动作失败进入异常待处理 |
| 退款申请 | `order_id + student_id + Idempotency-Key` | 创建新 `pay_refund`，订单 `refund_status=REVIEWING` | 存在进行中退款时返回 `409` |
| 退款回调 | `refund_no + external_refund_no` | 退款状态到 `REFUNDED` 并执行副作用 | 已退款终态不被失败覆盖；副作用失败进入补偿 |
| 发货确认 | `shipment_no` 或 `tracking_no` | 发货单到 `SHIPPED`，库存流水出库 | 外部运单已生成但内部失败时标记 `exception_flag` |
| 物流轨迹 | `tracking_no + logistics_node_time + node_status` | 写入轨迹，签收同步 `SIGNED` | 乱序轨迹保留但不回退主状态 |
| 开票回调 | `invoice_apply_no + invoice_no` | 发票状态到 `ISSUED` | 失败转 `TO_BE_ISSUED`，生成代账待办 |
| 红冲回调 | `invoice_apply_no + red_invoice_no` | 发票状态到 `RED_REVERSED` | 红冲失败保留 `ISSUED` 并可重试或人工上传凭证 |
| 采购入库 | `purchase_no + inbound_batch_no` | 采购完成并生成入库库存流水 | 数量不一致进入人工确认，不自动完成 |
| 对账导入 | `bill_month + bill_source + file_digest` | 生成对账批次和明细 | 重复上传返回已有批次；导入失败记录原因 |
| 通知发送 | `idempotency_key + channel + receiver_user_id` | 生成一条通知或发送记录 | 订阅消息、企微卡片失败不回滚主业务 |

## 12. 后续细化要求

| 后续文档或实现 | 需要细化的内容 |
|---|---|
| `08-权限矩阵文档.md` | 将本文件权限要求拆到菜单、按钮、接口、数据范围和字段脱敏规则 |
| `09-测试与验收用例.md` | 按本文件接口设计正向、异常、重复回调、乱序回调、人工补偿和 Mock 场景用例 |
| `10-服务器部署与演示说明.md` | 给出 Mock、沙箱、真实接入的域名、环境变量和演示账号配置，不在本文件暴露真实密钥 |
| 后端实现 | 统一返回结构、错误码、枚举编码、幂等拦截器、审计日志和回调事件落库 |
| 前端实现 | 按四类订单状态、子单据状态和权限返回控制按钮可见性，不在前端硬编码业务状态流转 |
