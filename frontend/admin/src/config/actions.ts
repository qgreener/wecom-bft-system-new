import type { RouteKey } from "@/router/routes";

export interface ActionFieldDef {
  key: string;
  label: string;
  type?: "text" | "number" | "textarea" | "select" | "datetime-local" | "date" | "month" | "checkbox" | "json" | "file" | "remoteSelect" | "itemTable";
  placeholder?: string;
  options?: { label: string; value: string }[];
  required?: boolean;
  accept?: string;
  /** 用于 remoteSelect：拉取选项的 API 路径；返回数据中按 valueKey/labelKey 取值 */
  remote?: { url: string; valueKey: string; labelKey: string };
  /** 用于 itemTable：每行的列定义；提交时把所有行收集成 Array<Record> */
  columns?: ActionFieldDef[];
  /** 用于 itemTable：从某个上下文 record 字段（如 purchase 的 items）预填初始行 */
  prefillFrom?: string;
}

export interface ActionBinding {
  urlFor: (id: string) => string;
  action?: string;
  staticPayload?: Record<string, unknown>;
}

export interface RouteAction {
  type: ActionType;
  label: string;
}

export const ACTION_LABELS: Record<string, string> = {
  refundApprove: "审核通过退款",
  refundReject: "驳回退款",
  refundManual: "人工退款完成",
  refundRetry: "重试失败退款",
  ship: "确认发货",
  sign: "确认签收",
  invoiceIssue: "开具发票",
  redReverse: "发票红冲",
  reconciliationImport: "导入对账单",
  reconciliationCheck: "核对对账记录",
  materialCreate: "创建代账材料",
  materialUpload: "上传材料文件",
  materialConfirm: "确认材料",
  materialClose: "关闭材料",
  supplierSave: "保存供货商",
  supplierSyncWecom: "从企微上下游同步",
  taxRuleSave: "保存税务规则",
  leadCreate: "新建线索",
  leadFollow: "记录跟进",
  promotionCodeCreate: "生成推广码",
  leadConvert: "确认转化",
  leadAbandon: "放弃线索",
  courseCreate: "新增课程",
  courseSpecSave: "保存售卖规格",
  lessonNodeSave: "保存课节",
  courseApproval: "提交课程审批",
  skuSave: "保存 SKU",
  stockFlowCreate: "新增库存流水",
  purchaseCreate: "新建采购单",
  purchaseReceive: "确认收货",
  purchaseInputInvoice: "回填进项票",
  purchaseShareLink: "生成推送链接"
};

export const ACTION_FIELD_DEFS: Record<string, ActionFieldDef[]> = {
  refundApprove: [
    { key: "approved_amount_cent", label: "批准金额(分)", type: "number", required: true },
    { key: "refund_channel", label: "退款渠道", type: "select", options: [
      { label: "原路退款", value: "ORIGINAL" },
      { label: "人工退款", value: "MANUAL" }
    ] },
    { key: "entitlement_action", label: "权益处理", type: "select", options: [
      { label: "冻结权益", value: "FREEZE" },
      { label: "撤销权益", value: "REVOKE" },
      { label: "暂不处理", value: "NONE" }
    ] },
    { key: "review_comment", label: "审核备注", type: "textarea" }
  ],
  refundReject: [
    { key: "reject_reason", label: "拒绝原因", type: "textarea", required: true }
  ],
  refundManual: [
    { key: "refund_channel", label: "退款渠道", type: "select", options: [
      { label: "银行转账", value: "BANK_TRANSFER" },
      { label: "微信转账", value: "WECHAT_TRANSFER" },
      { label: "其他", value: "OTHER" }
    ], required: true },
    { key: "manual_voucher_no", label: "退款凭证号", required: true },
    { key: "manual_voucher_file", label: "凭证文件号" },
    { key: "refunded_at", label: "退款时间", type: "datetime-local" },
    { key: "entitlement_action", label: "权益处理", type: "select", options: [
      { label: "冻结权益", value: "FREEZE" },
      { label: "撤销权益", value: "REVOKE" },
      { label: "暂不处理", value: "NONE" }
    ] },
    { key: "remark", label: "备注", type: "textarea" }
  ],
  refundRetry: [
    { key: "retry_mode", label: "重试模式", type: "select", options: [
      { label: "重试原路退款", value: "ORIGINAL" },
      { label: "转人工处理", value: "MANUAL_REQUIRED" }
    ], required: true },
    { key: "remark", label: "备注", type: "textarea" }
  ],
  ship: [
    { key: "logistics_company_code", label: "快递公司", type: "select", required: true, options: [
      { label: "顺丰速运 (SF)", value: "SF" },
      { label: "京东物流 (JD)", value: "JD" },
      { label: "中通快递 (ZTO)", value: "ZTO" },
      { label: "圆通速递 (YTO)", value: "YTO" },
      { label: "韵达快递 (YD)", value: "YD" },
      { label: "申通快递 (STO)", value: "STO" },
      { label: "邮政 (EMS)", value: "EMS" }
    ] },
    { key: "logistics_company_name", label: "快递公司名称（备用）" },
    { key: "tracking_no", label: "物流单号", required: true },
    { key: "package_weight_kg", label: "包裹重量(kg)", type: "number" },
    { key: "logistics_fee_cent", label: "物流费用(分)", type: "number" },
    { key: "print_mode", label: "打印方式", type: "select", options: [
      { label: "不打印", value: "NONE" },
      { label: "下载面单", value: "DOWNLOAD" },
      { label: "云打印", value: "CLOUD" }
    ] },
    { key: "waybill_file", label: "面单文件" },
    { key: "mock_scenario", label: "Mock 场景", type: "select", options: [
      { label: "正常", value: "" },
      { label: "面单失败", value: "WAYBILL_FAILED" },
      { label: "外部已生成内部失败", value: "EXTERNAL_CREATED_INTERNAL_FAILED" },
      { label: "创建运单失败", value: "CREATE_WAYBILL_FAILED" }
    ] },
    { key: "remark", label: "备注", type: "textarea" }
  ],
  sign: [
    { key: "signed_at", label: "签收时间", type: "datetime-local" },
    { key: "remark", label: "备注", type: "textarea" }
  ],
  invoiceIssue: [
    { key: "invoice_no", label: "发票号", required: true },
    { key: "invoice_file", label: "发票文件号", required: true },
    { key: "issued_at", label: "开票时间", type: "datetime-local" },
    { key: "remark", label: "备注", type: "textarea" }
  ],
  redReverse: [
    { key: "red_invoice_no", label: "红字发票号", required: true },
    { key: "red_invoice_file", label: "红冲凭证文件号" },
    { key: "red_reversed_at", label: "红冲时间", type: "datetime-local" },
    { key: "remark", label: "红冲原因", type: "textarea", required: true }
  ],
  reconciliationImport: [
    { key: "bill_month", label: "账单月份", type: "month", required: true },
    { key: "bill_source", label: "账单来源", type: "select", options: [
      { label: "微信支付结算单", value: "WECHAT_SETTLEMENT" },
      { label: "微信支付交易账单", value: "WECHAT_TRADE" }
    ] },
    { key: "file", label: "对账单文件 (CSV)", type: "file", accept: ".csv,text/csv", required: true }
  ],
  reconciliationCheck: [
    { key: "checked_flag", label: "标记为已核对", type: "checkbox" },
    { key: "difference_reason", label: "差异原因", type: "textarea", required: true },
    { key: "remark", label: "备注", type: "textarea" }
  ],
  materialCreate: [
    { key: "material_type", label: "材料类型", type: "select", options: [
      { label: "收入资料", value: "INCOME" },
      { label: "退款资料", value: "REFUND" },
      { label: "采购资料", value: "PURCHASE" },
      { label: "开票资料", value: "INVOICE" },
      { label: "其他", value: "OTHER" }
    ], required: true },
    { key: "related_month", label: "关联月份", type: "month", required: true },
    { key: "order_id", label: "关联订单 ID", type: "number" },
    { key: "related_object_type", label: "关联对象类型" },
    { key: "related_object_id", label: "关联对象 ID", type: "number" },
    { key: "purpose", label: "用途说明", type: "textarea", required: true },
    { key: "due_at", label: "截止时间", type: "datetime-local" }
  ],
  materialUpload: [
    { key: "file_refs", label: "文件号(逗号分隔)", type: "text", required: true },
    { key: "remark", label: "备注", type: "textarea" }
  ],
  materialConfirm: [{ key: "remark", label: "确认备注", type: "textarea" }],
  materialClose: [{ key: "closed_reason", label: "关闭原因", type: "textarea", required: true }],
  supplierSave: [
    { key: "supplier_id", label: "供货商 ID(编辑时填写)", type: "number" },
    { key: "supplier_name", label: "供货商名称", required: true },
    { key: "short_name", label: "简称" },
    { key: "contact_name", label: "联系人" },
    { key: "contact_mobile", label: "联系电话" },
    { key: "contact_email", label: "联系邮箱" },
    { key: "tax_no", label: "税号" },
    { key: "address", label: "地址" },
    { key: "settlement_method", label: "结算方式" },
    { key: "access_status", label: "接入状态", type: "select", options: [
      { label: "合作中", value: "ACTIVE" },
      { label: "已停用", value: "DISABLED" }
    ] },
    { key: "status", label: "状态", type: "select", options: [
      { label: "启用", value: "ENABLED" },
      { label: "停用", value: "DISABLED" }
    ] }
  ],
  supplierSyncWecom: [
    { key: "wecom_corp_id", label: "上下游企微 corp_id", required: true, placeholder: "企微开放平台获取" },
    { key: "supplier_name", label: "供货商名称", required: true, placeholder: "建议从企微通讯录复制" },
    { key: "contact_name", label: "对接人姓名", required: true },
    { key: "contact_mobile", label: "对接人手机号" },
    { key: "supply_skus", label: "供应 SKU 编码（逗号分隔）", placeholder: "可选，便于后续采购下单" }
  ],
  taxRuleSave: [
    { key: "rule_id", label: "规则 ID(编辑时填写)", type: "number" },
    { key: "rule_name", label: "规则名称", required: true },
    { key: "tax_category", label: "税目", required: true },
    { key: "tax_rate", label: "税率 (0-1)", type: "number", required: true },
    { key: "invoice_item_name", label: "发票项目名称", required: true },
    { key: "status", label: "状态", type: "select", options: [
      { label: "启用", value: "ENABLED" },
      { label: "停用", value: "DISABLED" }
    ] },
    { key: "description", label: "说明", type: "textarea" }
  ],
  leadCreate: [
    { key: "name", label: "姓名", required: true },
    { key: "mobile", label: "手机号", required: true },
    { key: "source_channel", label: "来源渠道", type: "select", options: [
      { label: "企微侧边栏", value: "WECOM_SIDEBAR" },
      { label: "推广码落地页", value: "PROMOTION" },
      { label: "其他", value: "OTHER" }
    ] },
    { key: "source_code", label: "来源明细" },
    { key: "intent_course_id", label: "意向课程 ID", type: "number" },
    { key: "next_follow_at", label: "下次跟进", type: "datetime-local" },
    { key: "remark", label: "备注", type: "textarea" }
  ],
  promotionCodeCreate: [
    { key: "name", label: "推广码名称", required: true, placeholder: "例：小红书春季招生" },
    { key: "channel", label: "推广渠道", type: "select", required: true, options: [
      { label: "小红书", value: "XHS" },
      { label: "朋友圈", value: "WECHAT_MOMENTS" },
      { label: "公众号", value: "WECHAT_OFFICIAL" },
      { label: "抖音", value: "DOUYIN" },
      { label: "其他", value: "OTHER" }
    ] }
  ],
  leadFollow: [
    { key: "follow_method", label: "跟进方式", type: "select", options: [
      { label: "电话", value: "PHONE" },
      { label: "微信", value: "WECHAT" },
      { label: "面谈", value: "MEETING" }
    ], required: true },
    { key: "content", label: "跟进内容", type: "textarea", required: true },
    { key: "next_follow_at", label: "下次跟进", type: "datetime-local" }
  ],
  leadConvert: [
    { key: "next_follow_at", label: "确认时间", type: "datetime-local" }
  ],
  leadAbandon: [
    { key: "abandon_reason", label: "放弃原因", type: "select", options: [
      { label: "无意向", value: "NO_INTENT" },
      { label: "价格不合适", value: "PRICE" },
      { label: "联系不上", value: "UNREACHABLE" },
      { label: "其他", value: "OTHER" }
    ], required: true }
  ],
  courseCreate: [
    { key: "course_title", label: "课程名", required: true },
    { key: "course_type", label: "课程类型", type: "select", options: [
      { label: "直播课", value: "LIVE" },
      { label: "录制课", value: "RECORDED" }
    ], required: true },
    { key: "cover_url", label: "封面 URL", placeholder: "图片地址；课程编辑页支持上传" },
    { key: "summary", label: "课程摘要", type: "textarea" },
    { key: "detail", label: "课程详情", type: "textarea" },
    { key: "teacher_user_id", label: "负责讲师", type: "remoteSelect",
      remote: { url: "/api/admin/system/admin-users?status=ACTIVE", valueKey: "id", labelKey: "display_name" } },
    { key: "category_code", label: "课程类目" },
    { key: "course_group_qr", label: "入群二维码文件号" },
    { key: "default_tax_rule_id", label: "默认税务规则", type: "remoteSelect",
      remote: { url: "/api/admin/tax-rules", valueKey: "rule_id", labelKey: "rule_name" } }
  ],
  courseSpecSave: [
    { key: "spec_name", label: "规格名", required: true },
    { key: "sale_price_cent", label: "售价(分)", type: "number", required: true },
    { key: "origin_price_cent", label: "原价(分)", type: "number" },
    { key: "stock_mode", label: "库存模式" },
    { key: "contains_physical", label: "包含实物", type: "checkbox" },
    { key: "sku_id", label: "实物 SKU", type: "remoteSelect",
      remote: { url: "/api/admin/inventory/skus", valueKey: "sku_id", labelKey: "sku_name" } },
    { key: "gift_sku_id", label: "赠品 SKU", type: "remoteSelect",
      remote: { url: "/api/admin/inventory/skus", valueKey: "sku_id", labelKey: "sku_name" } },
    { key: "tax_rule_id", label: "税务规则 ID", type: "number" },
    { key: "amount_split_snapshot", label: "金额拆分 JSON", type: "json" },
    { key: "status", label: "状态", type: "select", options: [
      { label: "启用", value: "ENABLED" },
      { label: "停用", value: "DISABLED" }
    ] },
    { key: "sort_no", label: "排序", type: "number" }
  ],
  lessonNodeSave: [
    { key: "parent_node_id", label: "父节点 ID", type: "number" },
    { key: "node_type", label: "节点类型", type: "select", options: [
      { label: "章节", value: "CHAPTER" },
      { label: "课节", value: "LESSON" }
    ], required: true },
    { key: "title", label: "标题", required: true },
    { key: "lesson_type", label: "课节类型", type: "select", options: [
      { label: "直播", value: "LIVE" },
      { label: "录制", value: "RECORDED" }
    ] },
    { key: "live_start_at", label: "直播开始", type: "datetime-local" },
    { key: "live_end_at", label: "直播结束", type: "datetime-local" },
    { key: "replay_url", label: "回放链接" },
    { key: "resource_file", label: "资料文件号" },
    { key: "status", label: "状态" },
    { key: "sort_no", label: "排序", type: "number" },
    { key: "remind_enabled", label: "开启提醒", type: "checkbox" }
  ],
  courseApproval: [
    { key: "approval_type", label: "审批类型", type: "select", options: [
      { label: "申请上架", value: "ON_SHELF" },
      { label: "申请下架", value: "OFF_SHELF" },
      { label: "申请删除", value: "DELETE" }
    ], required: true },
    { key: "submit_reason", label: "申请原因", type: "textarea", required: true },
    { key: "delete_notice_deadline", label: "删除通知截止", type: "datetime-local" }
  ],
  skuSave: [
    { key: "sku_id", label: "SKU ID(编辑时填写)", type: "number" },
    { key: "sku_name", label: "商品名", required: true },
    { key: "category_code", label: "类目", required: true },
    { key: "sku_type", label: "SKU 类型" },
    { key: "unit", label: "单位" },
    { key: "spec_attrs_json", label: "规格属性 JSON", type: "json" },
    { key: "default_supplier_id", label: "默认供货商 ID", type: "number" },
    { key: "cost_price_cent", label: "成本价(分)", type: "number" },
    { key: "safety_stock", label: "安全库存", type: "number" },
    { key: "status", label: "状态", type: "select", options: [
      { label: "启用", value: "ENABLED" },
      { label: "停用", value: "DISABLED" }
    ] },
    { key: "image_url", label: "图片 URL" }
  ],
  stockFlowCreate: [
    { key: "sku_id", label: "SKU", type: "remoteSelect", required: true,
      remote: { url: "/api/admin/inventory/skus", valueKey: "sku_id", labelKey: "sku_name" } },
    { key: "direction", label: "方向", type: "select", options: [
      { label: "入库", value: "IN" },
      { label: "出库", value: "OUT" }
    ], required: true },
    { key: "quantity", label: "数量", type: "number", required: true },
    { key: "biz_type", label: "业务类型" },
    { key: "biz_id", label: "业务 ID", type: "number" },
    { key: "order_id", label: "关联订单 ID", type: "number" },
    { key: "remark", label: "备注", type: "textarea" }
  ],
  purchaseCreate: [
    { key: "supplier_id", label: "供货商", type: "remoteSelect", required: true,
      remote: { url: "/api/admin/suppliers", valueKey: "supplier_id", labelKey: "supplier_name" } },
    { key: "purchase_items", label: "商品明细", type: "itemTable", required: true,
      columns: [
        { key: "sku_id", label: "SKU", type: "remoteSelect", required: true,
          remote: { url: "/api/admin/inventory/skus", valueKey: "sku_id", labelKey: "sku_name" } },
        { key: "quantity", label: "数量", type: "number", required: true },
        { key: "unit_price_cent", label: "单价(分)", type: "number", required: true }
      ] },
    { key: "submit_reason", label: "采购原因", type: "textarea" },
    { key: "expected_arrival_date", label: "预计到货日期", type: "date" }
  ],
  purchaseReceive: [
    { key: "inbound_batch_no", label: "入库批次号", required: true },
    { key: "received_items", label: "收货明细", type: "itemTable", required: true,
      prefillFrom: "purchase_items",
      columns: [
        { key: "sku_id", label: "SKU", type: "remoteSelect", required: true,
          remote: { url: "/api/admin/inventory/skus", valueKey: "sku_id", labelKey: "sku_name" } },
        { key: "received_quantity", label: "实收数量", type: "number", required: true }
      ] },
    { key: "remark", label: "备注", type: "textarea" }
  ],
  purchaseInputInvoice: [
    { key: "invoice_no", label: "进项发票号", required: true },
    { key: "invoice_amount_cent", label: "发票金额(分)", type: "number", required: true },
    { key: "invoice_file", label: "发票文件号" },
    { key: "issued_at", label: "开票时间", type: "datetime-local" },
    { key: "remark", label: "备注", type: "textarea" }
  ]
};

export const ACTION_BINDINGS: Record<string, ActionBinding> = {
  refundApprove:        { urlFor: (id: string) => `/api/admin/refunds/${id}/review`, action: "APPROVE" },
  refundReject:         { urlFor: (id: string) => `/api/admin/refunds/${id}/review`, action: "REJECT" },
  refundManual:         { urlFor: (id: string) => `/api/admin/refunds/${id}/manual-complete` },
  refundRetry:          { urlFor: (id: string) => `/api/admin/refunds/${id}/retry` },
  ship:                 { urlFor: (id: string) => `/api/admin/shipments/${id}/ship` },
  sign:                 { urlFor: (id: string) => `/api/admin/shipments/${id}/sign` },
  invoiceIssue:         { urlFor: (id: string) => `/api/admin/invoices/${id}/issue-manual` },
  redReverse:           { urlFor: (id: string) => `/api/admin/invoices/${id}/red-reverse` },
  reconciliationImport: { urlFor: () => `/api/admin/reconciliation/batches/upload` },
  reconciliationCheck:  { urlFor: (id: string) => `/api/admin/reconciliation/records/${id}/check` },
  materialCreate:       { urlFor: () => `/api/admin/accounting/materials` },
  materialUpload:       { urlFor: (id: string) => `/api/admin/accounting/materials/${id}/actions`, action: "UPLOAD" },
  materialConfirm:      { urlFor: (id: string) => `/api/admin/accounting/materials/${id}/actions`, action: "CONFIRM" },
  materialClose:        { urlFor: (id: string) => `/api/admin/accounting/materials/${id}/actions`, action: "CLOSE" },
  supplierSave:         { urlFor: () => `/api/admin/suppliers` },
  supplierSyncWecom:    { urlFor: () => `/api/admin/suppliers/sync-wecom` },
  taxRuleSave:          { urlFor: () => `/api/admin/tax-rules` },
  leadCreate:           { urlFor: () => `/api/admin/leads` },
  leadFollow:           { urlFor: (id: string) => `/api/admin/leads/${id}/follow-records` },
  leadConvert:          { urlFor: (id: string) => `/api/admin/leads/${id}/status`, staticPayload: { target_status: "CONVERTED" } },
  leadAbandon:          { urlFor: (id: string) => `/api/admin/leads/${id}/status`, staticPayload: { target_status: "ABANDONED" } },
  promotionCodeCreate:  { urlFor: () => `/api/admin/promotion-codes` },
  courseCreate:         { urlFor: () => `/api/admin/courses` },
  courseSpecSave:       { urlFor: (id: string) => `/api/admin/courses/${id}/specs` },
  lessonNodeSave:       { urlFor: (id: string) => `/api/admin/courses/${id}/lesson-nodes` },
  courseApproval:       { urlFor: (id: string) => `/api/admin/courses/${id}/approval` },
  skuSave:              { urlFor: () => `/api/admin/inventory/skus` },
  stockFlowCreate:      { urlFor: () => `/api/admin/inventory/stock-flows` },
  purchaseCreate:       { urlFor: () => `/api/admin/purchases` },
  purchaseReceive:      { urlFor: (id: string) => `/api/admin/purchases/${id}/receive` },
  purchaseInputInvoice: { urlFor: (id: string) => `/api/admin/purchases/${id}/input-invoice` },
  purchaseShareLink:    { urlFor: (id: string) => `/api/admin/purchases/${id}/share-link` }
};

export type ActionType = string;

export const ROUTE_ACTIONS: Partial<Record<RouteKey, RouteAction[]>> = {
  refunds: [
    { type: "refundApprove", label: "审核通过" },
    { type: "refundReject", label: "驳回" },
    { type: "refundManual", label: "人工退款" },
    { type: "refundRetry", label: "重试失败退款" }
  ],
  shipments: [
    { type: "ship", label: "确认发货" },
    { type: "sign", label: "确认签收" }
  ],
  invoices: [
    { type: "invoiceIssue", label: "开具发票" },
    { type: "redReverse", label: "红冲" }
  ],
  reconciliation: [
    { type: "reconciliationImport", label: "导入对账单" },
    { type: "reconciliationCheck", label: "核对差异记录" }
  ],
  accounting: [
    { type: "materialCreate", label: "创建材料" },
    { type: "materialUpload", label: "上传文件" },
    { type: "materialConfirm", label: "确认" },
    { type: "materialClose", label: "关闭" }
  ],
  leads: [
    { type: "leadCreate", label: "新建线索" },
    { type: "promotionCodeCreate", label: "生成推广码" },
    { type: "leadFollow", label: "跟进" },
    { type: "leadAbandon", label: "放弃" }
  ],
  courses: [
    { type: "courseCreate", label: "新增课程" },
    { type: "courseSpecSave", label: "保存规格" },
    { type: "lessonNodeSave", label: "保存课节" },
    { type: "courseApproval", label: "上架/下架/删除审批" }
  ],
  inventory: [
    { type: "skuSave", label: "保存 SKU" },
    { type: "stockFlowCreate", label: "入库/出库" }
  ],
  purchases: [
    { type: "purchaseCreate", label: "新建采购单" },
    { type: "purchaseShareLink", label: "生成推送链接" },
    { type: "purchaseReceive", label: "确认收货" },
    { type: "purchaseInputInvoice", label: "回填进项票" }
  ],
  suppliers: [
    { type: "supplierSave", label: "保存供货商" },
    { type: "supplierSyncWecom", label: "从企微上下游同步" }
  ],
  taxRules: [{ type: "taxRuleSave", label: "保存税务规则" }]
};

export const NO_ID_ACTIONS: ReadonlySet<ActionType> = new Set<ActionType>([
  "reconciliationImport",
  "materialCreate",
  "supplierSave",
  "supplierSyncWecom",
  "taxRuleSave",
  "leadCreate",
  "promotionCodeCreate",
  "courseCreate",
  "skuSave",
  "stockFlowCreate",
  "purchaseCreate"
]);

export const RECORD_ID_FIELDS: readonly string[] = [
  "id", "shipment_id", "refund_id", "invoice_id", "batch_id", "material_id",
  "order_id", "purchase_id", "approval_id", "reconciliation_id", "supplier_id", "rule_id",
  "lead_id", "student_id", "course_id", "entitlement_id", "payment_id", "sku_id",
  "user_id", "audit_log_id", "config_key", "promotion_code_id"
];

export function actionsForRoute(routeKey: RouteKey): RouteAction[] {
  return ROUTE_ACTIONS[routeKey] ?? [];
}
