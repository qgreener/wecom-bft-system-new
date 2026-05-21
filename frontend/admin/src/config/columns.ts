import type { RouteKey } from "@/router/routes";

export interface ColumnDef {
  key: string;
  label: string;
  type?: "status" | "amount" | "datetime" | "snapshot" | "boolean";
  snapshotKey?: string;
  snapshotField?: string;
  width?: string;
}

export const COLUMN_DEFS: Partial<Record<RouteKey, ColumnDef[]>> = {
  dashboard: [],
  orders: [
    { key: "order_no", label: "订单编号", width: "160px" },
    { key: "course_title", label: "课程", type: "snapshot", snapshotKey: "course_snapshot", snapshotField: "course_title" },
    { key: "payment_status", label: "支付", type: "status" },
    { key: "fulfillment_status", label: "履约", type: "status" },
    { key: "refund_status", label: "退款", type: "status" },
    { key: "invoice_status", label: "开票", type: "status" },
    { key: "payable_amount_cent", label: "应付", type: "amount" },
    { key: "created_at", label: "下单时间", type: "datetime" }
  ],
  refunds: [
    { key: "refund_no", label: "退款编号" },
    { key: "order_no", label: "关联订单" },
    { key: "apply_amount_cent", label: "退款金额", type: "amount" },
    { key: "status", label: "状态", type: "status" },
    { key: "created_at", label: "申请时间", type: "datetime" }
  ],
  shipments: [
    { key: "shipment_no", label: "发货单号" },
    { key: "order_no", label: "订单编号" },
    { key: "logistics_company_name", label: "物流公司" },
    { key: "tracking_no", label: "运单号" },
    { key: "status", label: "状态", type: "status" },
    { key: "exception_flag", label: "异常", type: "boolean" },
    { key: "shipped_at", label: "发货时间", type: "datetime" }
  ],
  invoices: [
    { key: "invoice_no", label: "发票编号" },
    { key: "order_no", label: "关联订单" },
    { key: "invoice_amount_cent", label: "金额", type: "amount" },
    { key: "invoice_type", label: "类型" },
    { key: "status", label: "状态", type: "status" },
    { key: "created_at", label: "申请时间", type: "datetime" }
  ],
  reconciliation: [
    { key: "batch_no", label: "批次编号" },
    { key: "bill_month", label: "账单月份" },
    { key: "total_count", label: "总笔数" },
    { key: "matched_count", label: "已匹配" },
    { key: "diff_count", label: "差异" },
    { key: "created_at", label: "导入时间", type: "datetime" }
  ],
  accounting: [
    { key: "material_no", label: "材料编号" },
    { key: "title", label: "标题" },
    { key: "related_month", label: "月份" },
    { key: "status", label: "状态", type: "status" },
    { key: "created_at", label: "创建时间", type: "datetime" }
  ],
  reports: [],
  courses: [
    { key: "course_no", label: "课程编号" },
    { key: "course_title", label: "课程名称" },
    { key: "course_type", label: "类型", type: "status" },
    { key: "min_sale_price_cent", label: "最低售价", type: "amount" },
    { key: "status", label: "状态", type: "status" },
    { key: "created_at", label: "创建时间", type: "datetime" }
  ],
  leads: [
    { key: "lead_no", label: "线索编号" },
    { key: "name", label: "姓名" },
    { key: "mobile", label: "手机号" },
    { key: "source_channel", label: "来源" },
    { key: "source_code", label: "来源明细" },
    { key: "status", label: "状态", type: "status" },
    { key: "latest_follow_at", label: "最近跟进", type: "datetime" }
  ],
  students: [
    { key: "student_no", label: "学员编号" },
    { key: "nickname", label: "昵称" },
    { key: "mobile", label: "手机号" },
    { key: "real_name", label: "姓名" },
    { key: "status", label: "状态", type: "status" },
    { key: "created_at", label: "注册时间", type: "datetime" }
  ],
  payments: [
    { key: "payment_no", label: "支付单号" },
    { key: "order_no", label: "订单编号" },
    { key: "channel", label: "渠道" },
    { key: "payment_method", label: "支付方式" },
    { key: "payment_result", label: "结果", type: "status" },
    { key: "paid_amount_cent", label: "金额", type: "amount" },
    { key: "paid_at", label: "支付时间", type: "datetime" }
  ],
  entitlements: [
    { key: "entitlement_no", label: "权益编号" },
    { key: "order_no", label: "订单编号" },
    { key: "course_id", label: "课程 ID" },
    { key: "status", label: "状态", type: "status" },
    { key: "opened_at", label: "开通时间", type: "datetime" },
    { key: "expire_at", label: "到期时间", type: "datetime" }
  ],
  suppliers: [
    { key: "supplier_no", label: "供货商编号" },
    { key: "supplier_name", label: "名称" },
    { key: "contact_name", label: "联系人" },
    { key: "contact_mobile", label: "电话" },
    { key: "access_status", label: "接入状态", type: "status" },
    { key: "status", label: "状态", type: "status" }
  ],
  taxRules: [
    { key: "rule_no", label: "规则编号" },
    { key: "rule_name", label: "名称" },
    { key: "tax_category", label: "税目" },
    { key: "tax_rate", label: "税率" },
    { key: "status", label: "状态", type: "status" }
  ],
  inventory: [
    { key: "sku_no", label: "SKU 编号" },
    { key: "sku_name", label: "商品名" },
    { key: "category_code", label: "类目" },
    { key: "available_stock", label: "可用库存" },
    { key: "safety_stock", label: "安全库存" },
    { key: "status", label: "状态", type: "status" }
  ],
  purchases: [
    { key: "purchase_no", label: "采购单号" },
    { key: "supplier_id", label: "供货商" },
    { key: "total_amount_cent", label: "金额", type: "amount" },
    { key: "purchase_status", label: "采购状态", type: "status" },
    { key: "input_invoice_status", label: "进项票", type: "status" },
    { key: "created_at", label: "创建时间", type: "datetime" }
  ],
  settings: [
    { key: "config_key", label: "配置键" },
    { key: "display_name", label: "名称" },
    { key: "masked_value", label: "当前值" },
    { key: "editable_flag", label: "可编辑", type: "boolean" },
    { key: "updated_at", label: "更新时间", type: "datetime" }
  ],
  logisticsConfig: [
    { key: "config_key", label: "配置键" },
    { key: "display_name", label: "名称" },
    { key: "masked_value", label: "当前值" },
    { key: "editable_flag", label: "可编辑", type: "boolean" },
    { key: "updated_at", label: "更新时间", type: "datetime" }
  ],
  audit: [
    { key: "created_at", label: "时间", type: "datetime" },
    { key: "operator_user_id", label: "操作人" },
    { key: "operation_module", label: "模块" },
    { key: "operation_type", label: "动作" },
    { key: "operation_result", label: "结果", type: "status" }
  ]
};

export function columnsForRoute(routeKey: RouteKey): ColumnDef[] {
  return COLUMN_DEFS[routeKey] ?? [];
}
