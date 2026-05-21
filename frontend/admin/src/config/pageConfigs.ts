import type { RouteKey } from "@/router/routes";

export type FilterKind = "text" | "select" | "datetime-local" | "month" | "date";

export interface FilterOption {
  label: string;
  value: string;
}

export interface FilterConfig {
  key: string;
  label: string;
  kind?: FilterKind;
  placeholder?: string;
  options?: FilterOption[];
  autoReload?: boolean;
  span?: "wide";
}

export interface DetailGroupConfig {
  title: string;
  fields: string[];
}

export interface PageConfig {
  routeKey: RouteKey;
  emptyMessage: string;
  filters: FilterConfig[];
  detailTitle: string;
  detailNoFields: string[];
  detailGroups: DetailGroupConfig[];
  documentChain?: boolean;
  focusStatuses?: string[];
}

const STATUS = {
  refunds: [
    { label: "审核中", value: "REVIEWING" },
    { label: "已拒绝", value: "REJECTED" },
    { label: "退款处理中", value: "PROCESSING" },
    { label: "待人工退款", value: "MANUAL_REQUIRED" },
    { label: "退款失败", value: "FAILED" },
    { label: "已退款", value: "REFUNDED" }
  ],
  shipments: [
    { label: "待发货", value: "PENDING_SHIPMENT" },
    { label: "已发货", value: "SHIPPED" },
    { label: "已签收", value: "SIGNED" }
  ],
  invoices: [
    { label: "已申请", value: "APPLIED" },
    { label: "待开具", value: "TO_BE_ISSUED" },
    { label: "已开具", value: "ISSUED" },
    { label: "已红冲", value: "RED_REVERSED" }
  ],
  courses: [
    { label: "已上架", value: "ON_SHELF" },
    { label: "已下架", value: "OFF_SHELF" },
    { label: "审批中", value: "PENDING_REVIEW" },
    { label: "待删除", value: "DELETE_PENDING" }
  ],
  leads: [
    { label: "待跟进", value: "PENDING_FOLLOW" },
    { label: "已联系", value: "CONTACTED" },
    { label: "已转化", value: "CONVERTED" },
    { label: "已放弃", value: "ABANDONED" }
  ],
  purchases: [
    { label: "审批中", value: "APPROVING" },
    { label: "审批拒绝", value: "APPROVAL_REJECTED" },
    { label: "待确认", value: "WAIT_CONFIRM" },
    { label: "已确认", value: "CONFIRMED" },
    { label: "已发货", value: "SHIPPED" },
    { label: "已完成", value: "COMPLETED" },
    { label: "已取消", value: "CANCELED" }
  ],
  enabled: [
    { label: "启用", value: "ENABLED" },
    { label: "停用", value: "DISABLED" }
  ],
  accounting: [
    { label: "待补充", value: "PENDING_SUPPLEMENT" },
    { label: "已上传", value: "UPLOADED" },
    { label: "已确认", value: "CONFIRMED" },
    { label: "已关闭", value: "CLOSED" }
  ],
  entitlements: [
    { label: "生效", value: "ACTIVE" },
    { label: "冻结", value: "FROZEN" },
    { label: "撤销", value: "REVOKED" }
  ]
} satisfies Record<string, FilterOption[]>;

const keyword = (placeholder: string): FilterConfig => ({ key: "keyword", label: "搜索", placeholder, span: "wide" });
const status = (options: FilterOption[], label = "业务状态"): FilterConfig => ({
  key: "status",
  label,
  kind: "select",
  options,
  autoReload: true
});

export const PAGE_CONFIGS: Partial<Record<RouteKey, PageConfig>> = {
  orders: {
    routeKey: "orders",
    emptyMessage: "当前筛选下没有订单",
    detailTitle: "订单详情",
    detailNoFields: ["order_no", "order_id", "merchant_order_no"],
    focusStatuses: ["payment_status", "fulfillment_status", "refund_status", "invoice_status"],
    documentChain: true,
    filters: [
      { key: "paid_at_start", label: "支付开始", kind: "datetime-local" },
      { key: "paid_at_end", label: "支付结束", kind: "datetime-local" },
      {
        key: "payment_status",
        label: "支付状态",
        kind: "select",
        autoReload: true,
        options: [
          { label: "待支付", value: "PENDING" },
          { label: "已支付", value: "PAID" },
          { label: "已关闭", value: "CLOSED" }
        ]
      },
      {
        key: "fulfillment_status",
        label: "履约状态",
        kind: "select",
        autoReload: true,
        options: [
          { label: "无需发货", value: "NO_SHIPMENT" },
          { label: "待发货", value: "PENDING_SHIPMENT" },
          { label: "已发货", value: "SHIPPED" },
          { label: "已签收", value: "SIGNED" }
        ]
      },
      { key: "refund_status", label: "退款状态", kind: "select", options: STATUS.refunds, autoReload: true },
      { key: "invoice_status", label: "开票状态", kind: "select", options: STATUS.invoices, autoReload: true },
      keyword("订单编号 / 学员昵称")
    ],
    detailGroups: [
      { title: "订单信息", fields: ["order_no", "student_name", "nickname", "mobile", "created_at", "paid_at", "pay_deadline_at", "payment_method"] },
      { title: "商品与金额", fields: ["course_title", "course_type", "spec_name", "quantity", "payable_amount_cent", "paid_amount_cent", "freight_amount_cent"] },
      { title: "物流信息", fields: ["receiver_name", "receiver_mobile", "receiver_address", "logistics_company_name", "tracking_no", "shipped_at", "signed_at"] },
      { title: "退款与发票", fields: ["refund_no", "apply_amount_cent", "refund_reason", "invoice_no", "invoice_title", "invoice_amount_cent"] }
    ]
  },
  refunds: {
    routeKey: "refunds",
    emptyMessage: "当前筛选下没有退款单",
    detailTitle: "退款详情",
    detailNoFields: ["refund_no", "refund_id"],
    focusStatuses: ["status"],
    filters: [status(STATUS.refunds, "退款状态"), { key: "order_no", label: "订单号", placeholder: "订单编号" }, keyword("退款编号 / 学员昵称")],
    detailGroups: [
      { title: "退款单信息", fields: ["refund_no", "order_no", "student_name", "apply_amount_cent", "approved_amount_cent", "reason", "apply_reason", "created_at"] },
      { title: "审核与处理", fields: ["reviewer_name", "review_comment", "reject_reason", "reviewed_at", "refund_channel", "refunded_at", "manual_voucher_no"] },
      { title: "关联订单", fields: ["payment_status", "fulfillment_status", "invoice_status", "course_title", "paid_amount_cent"] }
    ]
  },
  shipments: {
    routeKey: "shipments",
    emptyMessage: "当前筛选下没有发货单",
    detailTitle: "发货详情",
    detailNoFields: ["shipment_no", "shipment_id", "order_no"],
    focusStatuses: ["status"],
    filters: [
      status(STATUS.shipments, "履约状态"),
      { key: "order_no", label: "订单号", placeholder: "订单编号" },
      { key: "tracking_no", label: "运单号", placeholder: "快递单号" },
      { key: "exception_flag", label: "异常", kind: "select", autoReload: true, options: [
        { label: "仅异常", value: "true" },
        { label: "仅正常", value: "false" }
      ] },
      keyword("收货人 / 商品")
    ],
    detailGroups: [
      { title: "发货信息", fields: ["shipment_no", "order_no", "logistics_company_name", "tracking_no", "shipped_at", "signed_at", "operator_name"] },
      { title: "收货信息", fields: ["receiver_name", "receiver_mobile", "receiver_address"] },
      { title: "异常处理", fields: ["exception_flag", "exception_reason", "trace_id", "waybill_file", "remark"] }
    ]
  },
  invoices: {
    routeKey: "invoices",
    emptyMessage: "当前筛选下没有开票申请",
    detailTitle: "发票详情",
    detailNoFields: ["invoice_no", "invoice_id", "order_no"],
    focusStatuses: ["status"],
    filters: [status(STATUS.invoices, "开票状态"), { key: "order_no", label: "订单号", placeholder: "订单编号" }, keyword("抬头 / 邮箱 / 学员")],
    detailGroups: [
      { title: "开票申请", fields: ["invoice_no", "order_no", "invoice_type", "invoice_title", "tax_no", "invoice_amount_cent", "email", "created_at"] },
      { title: "开具结果", fields: ["issued_at", "invoice_file", "fail_reason", "red_invoice_no", "red_reversed_at", "red_invoice_file"] },
      { title: "关联订单", fields: ["student_name", "course_title", "paid_amount_cent", "refund_status"] }
    ]
  },
  courses: {
    routeKey: "courses",
    emptyMessage: "当前筛选下没有课程",
    detailTitle: "课程详情",
    detailNoFields: ["course_no", "course_id"],
    focusStatuses: ["status", "course_type"],
    filters: [
      { key: "course_type", label: "课程类型", kind: "select", autoReload: true, options: [
        { label: "直播课", value: "LIVE" },
        { label: "录制课", value: "RECORDED" }
      ] },
      status(STATUS.courses, "课程状态"),
      keyword("课程名 / 讲师")
    ],
    detailGroups: [
      { title: "基本信息", fields: ["course_no", "course_title", "course_type", "teacher_name", "category_code", "min_sale_price_cent", "created_at"] },
      { title: "售卖与税务", fields: ["spec_name", "sale_price_cent", "default_tax_rule_id", "course_group_qr", "cover_url"] },
      { title: "内容维护", fields: ["live_start_at", "live_end_at", "replay_url", "resource_file", "updated_at"] }
    ]
  },
  leads: {
    routeKey: "leads",
    emptyMessage: "当前筛选下没有线索",
    detailTitle: "线索详情",
    detailNoFields: ["lead_no", "lead_id"],
    focusStatuses: ["status", "source_channel"],
    filters: [
      status(STATUS.leads, "线索状态"),
      { key: "source_channel", label: "来源入口", kind: "select", autoReload: true, options: [
        { label: "企微侧边栏", value: "WECOM_SIDEBAR" },
        { label: "推广码落地页", value: "PROMOTION" },
        { label: "其他", value: "OTHER" }
      ] },
      { key: "mobile", label: "手机号", placeholder: "手机号" },
      keyword("姓名 / 线索编号")
    ],
    detailGroups: [
      { title: "线索信息", fields: ["lead_no", "name", "mobile", "source_channel", "source_code", "intent_course_id", "created_at"] },
      { title: "跟进状态", fields: ["latest_follow_at", "next_follow_at", "follow_method", "abandon_reason", "remark"] }
    ]
  },
  students: {
    routeKey: "students",
    emptyMessage: "当前筛选下没有学员",
    detailTitle: "学员详情",
    detailNoFields: ["student_no", "student_id"],
    filters: [keyword("昵称 / 手机号 / 姓名")],
    detailGroups: [
      { title: "基本信息", fields: ["student_no", "nickname", "real_name", "mobile", "source", "created_at", "status"] },
      { title: "消费与学习", fields: ["order_count", "paid_amount_cent", "latest_order_no", "latest_course_title"] }
    ]
  },
  inventory: {
    routeKey: "inventory",
    emptyMessage: "当前筛选下没有 SKU",
    detailTitle: "库存详情",
    detailNoFields: ["sku_no", "sku_id"],
    focusStatuses: ["status"],
    filters: [status(STATUS.enabled, "启用状态"), { key: "category_code", label: "类目", placeholder: "商品类目" }, { key: "warning_only", label: "库存预警", kind: "select", autoReload: true, options: [{ label: "仅低库存", value: "true" }] }, keyword("商品名 / SKU")],
    detailGroups: [
      { title: "SKU 信息", fields: ["sku_no", "sku_name", "category_code", "sku_type", "unit", "default_supplier_id", "image_url"] },
      { title: "库存与成本", fields: ["available_stock", "safety_stock", "cost_price_cent", "latest_in_at", "latest_out_at"] }
    ]
  },
  purchases: {
    routeKey: "purchases",
    emptyMessage: "当前筛选下没有采购单",
    detailTitle: "采购详情",
    detailNoFields: ["purchase_no", "purchase_id"],
    focusStatuses: ["purchase_status", "input_invoice_status"],
    filters: [status(STATUS.purchases, "采购状态"), keyword("采购单号 / 供货商")],
    detailGroups: [
      { title: "采购信息", fields: ["purchase_no", "supplier_name", "supplier_id", "total_amount_cent", "expected_arrival_date", "created_at"] },
      { title: "履约与发票", fields: ["purchase_status", "input_invoice_status", "tracking_no", "invoice_no", "invoice_amount_cent", "issued_at"] }
    ]
  },
  payments: {
    routeKey: "payments",
    emptyMessage: "当前筛选下没有支付记录",
    detailTitle: "支付详情",
    detailNoFields: ["payment_no", "payment_id"],
    focusStatuses: ["payment_result"],
    filters: [
      { key: "payment_result", label: "支付结果", kind: "select", autoReload: true, options: [
        { label: "成功", value: "SUCCESS" },
        { label: "处理中", value: "PROCESSING" },
        { label: "失败", value: "FAILED" }
      ] },
      { key: "merchant_order_no", label: "商户订单", placeholder: "商户订单号" },
      { key: "order_id", label: "订单 ID", placeholder: "订单 ID" },
      keyword("支付单号 / 订单编号")
    ],
    detailGroups: [
      { title: "支付信息", fields: ["payment_no", "order_no", "merchant_order_no", "channel", "payment_method", "paid_amount_cent", "paid_at"] },
      { title: "回调与异常", fields: ["payment_result", "fail_reason", "callback_trace_id", "trace_id"] }
    ]
  },
  entitlements: {
    routeKey: "entitlements",
    emptyMessage: "当前筛选下没有学习权益",
    detailTitle: "权益详情",
    detailNoFields: ["entitlement_no", "entitlement_id"],
    focusStatuses: ["status"],
    filters: [status(STATUS.entitlements, "权益状态"), { key: "student_id", label: "学员 ID", placeholder: "学员 ID" }, { key: "course_id", label: "课程 ID", placeholder: "课程 ID" }],
    detailGroups: [
      { title: "权益信息", fields: ["entitlement_no", "order_no", "student_id", "course_id", "course_title", "opened_at", "expire_at", "status"] },
      { title: "变更记录", fields: ["frozen_at", "revoked_at", "reason", "updated_at"] }
    ]
  },
  reconciliation: {
    routeKey: "reconciliation",
    emptyMessage: "当前筛选下没有对账批次",
    detailTitle: "对账详情",
    detailNoFields: ["batch_no", "batch_id"],
    filters: [{ key: "related_month", label: "账单月份", kind: "month" }, keyword("批次编号")],
    detailGroups: [
      { title: "批次汇总", fields: ["batch_no", "bill_month", "bill_source", "total_count", "matched_count", "diff_count", "created_at"] },
      { title: "差异处理", fields: ["system_amount_cent", "bill_amount_cent", "refund_amount_cent", "fee_amount_cent", "difference_amount_cent"] }
    ]
  },
  accounting: {
    routeKey: "accounting",
    emptyMessage: "当前筛选下没有代账材料",
    detailTitle: "代账材料详情",
    detailNoFields: ["material_no", "material_id"],
    focusStatuses: ["status"],
    filters: [{ key: "related_month", label: "关联月份", kind: "month" }, status(STATUS.accounting, "材料状态"), keyword("材料编号 / 用途")],
    detailGroups: [
      { title: "材料申请", fields: ["material_no", "material_type", "related_month", "order_id", "related_object_type", "related_object_id", "purpose", "due_at"] },
      { title: "处理结果", fields: ["status", "file_refs", "confirmed_at", "closed_reason", "updated_at"] }
    ]
  },
  suppliers: {
    routeKey: "suppliers",
    emptyMessage: "当前筛选下没有供货商",
    detailTitle: "供货商详情",
    detailNoFields: ["supplier_no", "supplier_id"],
    focusStatuses: ["access_status", "status"],
    filters: [
      { key: "access_status", label: "接入状态", kind: "select", autoReload: true, options: [
        { label: "合作中", value: "ACTIVE" },
        { label: "已停用", value: "DISABLED" }
      ] },
      keyword("供货商 / 联系人")
    ],
    detailGroups: [
      { title: "供货商信息", fields: ["supplier_no", "supplier_name", "short_name", "contact_name", "contact_mobile", "contact_email", "status"] },
      { title: "企微与结算", fields: ["wecom_corp_id", "tax_no", "address", "settlement_method", "access_status"] }
    ]
  },
  taxRules: {
    routeKey: "taxRules",
    emptyMessage: "当前筛选下没有税务规则",
    detailTitle: "税务规则详情",
    detailNoFields: ["rule_no", "rule_id"],
    focusStatuses: ["status"],
    filters: [status(STATUS.enabled, "启用状态")],
    detailGroups: [
      { title: "规则信息", fields: ["rule_no", "rule_name", "tax_category", "tax_rate", "invoice_item_name", "status"] },
      { title: "说明", fields: ["description", "created_at", "updated_at"] }
    ]
  },
  settings: {
    routeKey: "settings",
    emptyMessage: "当前配置组暂无配置项",
    detailTitle: "系统配置详情",
    detailNoFields: ["config_key"],
    filters: [{ key: "config_group", label: "配置组", kind: "select", autoReload: true, options: [
      { label: "采购配置", value: "PURCHASE" },
      { label: "支付配置", value: "PAYMENT" },
      { label: "物流配置", value: "LOGISTICS" },
      { label: "开票配置", value: "INVOICE" },
      { label: "税务配置", value: "TAX" },
      { label: "演示配置", value: "MOCK" }
    ] }],
    detailGroups: [{ title: "配置项", fields: ["config_key", "display_name", "masked_value", "editable_flag", "updated_at"] }]
  },
  logisticsConfig: {
    routeKey: "logisticsConfig",
    emptyMessage: "当前物流配置组暂无配置项",
    detailTitle: "物流配置详情",
    detailNoFields: ["config_key"],
    filters: [],
    detailGroups: [{ title: "物流配置", fields: ["config_key", "display_name", "masked_value", "editable_flag", "updated_at"] }]
  },
  audit: {
    routeKey: "audit",
    emptyMessage: "当前筛选下没有操作日志",
    detailTitle: "操作日志详情",
    detailNoFields: ["audit_log_id", "id", "trace_id"],
    filters: [
      { key: "trace_id", label: "TraceId", placeholder: "TraceId" },
      { key: "operation_module", label: "模块", placeholder: "模块" },
      { key: "operation_type", label: "动作", placeholder: "动作" }
    ],
    detailGroups: [
      { title: "操作信息", fields: ["created_at", "operator_name", "operator_user_id", "operation_module", "operation_type", "operation_result", "trace_id"] },
      { title: "对象与详情", fields: ["target_type", "target_no", "detail", "request_ip"] }
    ]
  }
};

const FALLBACK_DETAIL_GROUPS: DetailGroupConfig[] = [
  { title: "基本信息", fields: ["created_at", "updated_at", "status"] }
];

export function pageConfigFor(routeKey: RouteKey): PageConfig {
  return PAGE_CONFIGS[routeKey] ?? {
    routeKey,
    emptyMessage: "当前筛选下暂无数据",
    filters: [],
    detailTitle: "详情",
    detailNoFields: ["id"],
    detailGroups: FALLBACK_DETAIL_GROUPS
  };
}
