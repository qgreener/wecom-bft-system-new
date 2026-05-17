export type RouteKey =
  | "dashboard"
  | "orders"
  | "refunds"
  | "shipments"
  | "invoices"
  | "reconciliation"
  | "accounting"
  | "courses"
  | "leads"
  | "students"
  | "inventory"
  | "purchases"
  | "settings"
  | "audit";

export type AdminRoute = {
  key: RouteKey;
  path: string;
  title: string;
  group: "home" | "crm" | "course" | "supply" | "trade" | "finance" | "system";
  menuCodes: string[];
  permissionCodes: string[];
  listPath?: string;
  detailPath?: (id: string) => string;
  idFields: string[];
  statusFields?: string[];
  amountFields?: string[];
};

export const groupLabels: Record<AdminRoute["group"], string> = {
  home: "首页",
  crm: "客户与学员",
  course: "课程与履约",
  supply: "供应与库存",
  trade: "交易售后",
  finance: "财税与报表",
  system: "系统配置"
};

export const routeRegistry: AdminRoute[] = [
  {
    key: "dashboard",
    path: "/dashboard",
    title: "首页",
    group: "home",
    menuCodes: ["home.dashboard"],
    permissionCodes: [],
    idFields: []
  },
  {
    key: "orders",
    path: "/orders",
    title: "订单管理",
    group: "trade",
    menuCodes: ["trade.orders"],
    permissionCodes: ["trade:order:read"],
    listPath: "/api/admin/orders",
    detailPath: (id) => `/api/admin/orders/${id}`,
    idFields: ["order_id"],
    statusFields: ["payment_status", "fulfillment_status", "refund_status", "invoice_status"],
    amountFields: ["paid_amount_cent", "payable_amount_cent"]
  },
  {
    key: "refunds",
    path: "/refunds",
    title: "退款处理",
    group: "trade",
    menuCodes: ["refund.reviews"],
    permissionCodes: ["refund:review:write", "accounting:material:write"],
    listPath: "/api/admin/refunds",
    detailPath: (id) => `/api/admin/refunds/${id}`,
    idFields: ["refund_id"],
    statusFields: ["status"],
    amountFields: ["apply_amount_cent", "approved_amount_cent"]
  },
  {
    key: "shipments",
    path: "/shipments",
    title: "发货管理",
    group: "supply",
    menuCodes: ["fulfillment.shipments"],
    permissionCodes: ["fulfillment:shipment:write"],
    listPath: "/api/admin/shipments",
    detailPath: (id) => `/api/admin/shipments/${id}`,
    idFields: ["shipment_id"],
    statusFields: ["status"]
  },
  {
    key: "invoices",
    path: "/invoices",
    title: "开票管理",
    group: "finance",
    menuCodes: ["invoice.manage", "invoice.readonly"],
    permissionCodes: ["tax:invoice:write", "invoice:read"],
    listPath: "/api/admin/invoices",
    detailPath: (id) => `/api/admin/invoices/${id}`,
    idFields: ["invoice_id"],
    statusFields: ["status"],
    amountFields: ["invoice_amount_cent"]
  },
  {
    key: "reconciliation",
    path: "/reconciliation",
    title: "收款对账",
    group: "finance",
    menuCodes: ["finance.reconciliation"],
    permissionCodes: ["finance:reconciliation:write"],
    listPath: "/api/admin/reconciliation/batches",
    detailPath: (id) => `/api/admin/reconciliation/batches/${id}`,
    idFields: ["batch_id"]
  },
  {
    key: "accounting",
    path: "/accounting",
    title: "代账管理",
    group: "finance",
    menuCodes: ["accounting.workspace"],
    permissionCodes: ["accounting:material:write"],
    listPath: "/api/admin/accounting/materials",
    detailPath: (id) => `/api/admin/accounting/materials/${id}`,
    idFields: ["material_id"],
    statusFields: ["status"]
  },
  {
    key: "courses",
    path: "/courses",
    title: "课程管理",
    group: "course",
    menuCodes: ["course.manage", "course.lesson-content"],
    permissionCodes: ["course:spec:write", "course:lesson:read", "course:lesson:write"],
    listPath: "/api/admin/courses",
    detailPath: (id) => `/api/admin/courses/${id}`,
    idFields: ["course_id"],
    statusFields: ["status"],
    amountFields: ["min_sale_price_cent"]
  },
  {
    key: "leads",
    path: "/leads",
    title: "线索管理",
    group: "crm",
    menuCodes: ["crm.leads"],
    permissionCodes: ["crm:lead:read"],
    listPath: "/api/admin/leads",
    idFields: ["lead_id"],
    statusFields: ["status"]
  },
  {
    key: "students",
    path: "/students",
    title: "学员管理",
    group: "crm",
    menuCodes: ["crm.students"],
    permissionCodes: ["student:read"],
    idFields: ["student_id"]
  },
  {
    key: "inventory",
    path: "/inventory",
    title: "库存管理",
    group: "supply",
    menuCodes: ["inventory.skus"],
    permissionCodes: ["inventory:sku:write"],
    listPath: "/api/admin/inventory/skus",
    idFields: ["sku_id"],
    statusFields: ["status"],
    amountFields: ["cost_price_cent"]
  },
  {
    key: "purchases",
    path: "/purchases",
    title: "采购订单",
    group: "supply",
    menuCodes: ["purchase.orders"],
    permissionCodes: ["purchase:order:write"],
    listPath: "/api/admin/purchases",
    detailPath: (id) => `/api/admin/purchases/${id}`,
    idFields: ["purchase_id"],
    statusFields: ["purchase_status", "input_invoice_status"],
    amountFields: ["total_amount_cent"]
  },
  {
    key: "settings",
    path: "/settings",
    title: "系统设置",
    group: "system",
    menuCodes: ["system.settings"],
    permissionCodes: ["system:config:read", "system:config:write"],
    listPath: "/api/admin/system/configs",
    idFields: []
  },
  {
    key: "audit",
    path: "/audit",
    title: "操作日志",
    group: "system",
    menuCodes: ["system.audit-logs"],
    permissionCodes: ["system:audit:read"],
    listPath: "/api/admin/audit/logs",
    idFields: ["audit_log_id", "id"]
  }
];

export function findRouteByPath(path: string): AdminRoute {
  const normalized = path === "/" ? "/dashboard" : path;
  return routeRegistry.find((route) => normalized.startsWith(route.path)) ?? routeRegistry[0];
}
