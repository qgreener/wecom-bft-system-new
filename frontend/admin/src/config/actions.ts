import type { RouteKey } from "@/router/routes";

export interface ActionFieldDef {
  key: string;
  label: string;
  type?: string;
}

export interface ActionBinding {
  urlFor: (id: string) => string;
  action?: string;
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
  taxRuleSave: "保存税务规则"
};

export const ACTION_FIELD_DEFS: Record<string, ActionFieldDef[]> = {
  refundApprove: [
    { key: "approved_amount_cent", label: "批准金额(分)" },
    { key: "remark", label: "备注" }
  ],
  refundReject: [
    { key: "reject_reason", label: "拒绝原因" }
  ],
  refundManual: [
    { key: "manual_refund_no", label: "退款流水号" },
    { key: "remark", label: "备注" }
  ],
  refundRetry: [
    { key: "retry_mode", label: "重试模式 (ORIGINAL/MANUAL_REQUIRED)" },
    { key: "remark", label: "备注" }
  ],
  ship: [
    { key: "logistics_company_code", label: "物流公司代码" },
    { key: "logistics_company_name", label: "物流公司名称" },
    { key: "tracking_no", label: "物流单号" },
    { key: "waybill_file", label: "面单文件" },
    { key: "remark", label: "备注" }
  ],
  sign: [{ key: "remark", label: "备注" }],
  invoiceIssue: [
    { key: "invoice_no", label: "发票号" },
    { key: "invoice_file", label: "发票文件" }
  ],
  redReverse: [
    { key: "red_invoice_no", label: "红字发票号" },
    { key: "reason", label: "红冲原因" }
  ],
  reconciliationImport: [
    { key: "bill_month", label: "账单月份(YYYY-MM)" },
    { key: "bill_source", label: "账单来源" },
    { key: "file_ref", label: "文件引用" }
  ],
  reconciliationCheck: [
    { key: "difference_reason", label: "差异原因" },
    { key: "remark", label: "备注" }
  ],
  materialCreate: [
    { key: "material_type", label: "材料类型" },
    { key: "title", label: "标题" },
    { key: "related_month", label: "关联月份" }
  ],
  materialUpload: [
    { key: "file_refs", label: "文件引用(逗号分隔)" },
    { key: "remark", label: "备注" }
  ],
  materialConfirm: [{ key: "remark", label: "确认备注" }],
  materialClose: [{ key: "close_reason", label: "关闭原因" }],
  supplierSave: [
    { key: "supplier_name", label: "供货商名称" },
    { key: "contact_name", label: "联系人" },
    { key: "contact_mobile", label: "联系电话" },
    { key: "tax_no", label: "税号" }
  ],
  taxRuleSave: [
    { key: "rule_name", label: "规则名称" },
    { key: "tax_category", label: "税目" },
    { key: "tax_rate", label: "税率 (0-1)" },
    { key: "invoice_item_name", label: "发票项目名称" }
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
  reconciliationImport: { urlFor: () => `/api/admin/reconciliation/batches` },
  reconciliationCheck:  { urlFor: (id: string) => `/api/admin/reconciliation/records/${id}/check` },
  materialCreate:       { urlFor: () => `/api/admin/accounting/materials` },
  materialUpload:       { urlFor: (id: string) => `/api/admin/accounting/materials/${id}/actions`, action: "UPLOAD" },
  materialConfirm:      { urlFor: (id: string) => `/api/admin/accounting/materials/${id}/actions`, action: "CONFIRM" },
  materialClose:        { urlFor: (id: string) => `/api/admin/accounting/materials/${id}/actions`, action: "CLOSE" },
  supplierSave:         { urlFor: () => `/api/admin/suppliers` },
  taxRuleSave:          { urlFor: () => `/api/admin/tax-rules` }
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
  suppliers: [{ type: "supplierSave", label: "保存供货商" }],
  taxRules: [{ type: "taxRuleSave", label: "保存税务规则" }]
};

export const NO_ID_ACTIONS: ReadonlySet<ActionType> = new Set<ActionType>([
  "reconciliationImport",
  "materialCreate",
  "supplierSave",
  "taxRuleSave"
]);

export const RECORD_ID_FIELDS: readonly string[] = [
  "id", "shipment_id", "refund_id", "invoice_id", "batch_id", "material_id",
  "order_id", "purchase_id", "approval_id", "reconciliation_id", "supplier_id", "rule_id"
];

export function actionsForRoute(routeKey: RouteKey): RouteAction[] {
  return ROUTE_ACTIONS[routeKey] ?? [];
}
