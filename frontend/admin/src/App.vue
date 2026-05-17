<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { surfaces } from "@wecom-bft/shared";
import {
  request, getAccessToken, setAccessToken, clearAccessToken, createIdempotencyKey,
  buildApiUrl, type ApiEnvelope, type ApiClientError
} from "./services/http";
import {
  formatCent, formatDateTime, emptyText, normalizeRecords,
  getRecordId, labelFromSnapshot
} from "./utils/format";
import {
  findRouteByPath, routeRegistry, groupLabels, type AdminRoute, type RouteKey
} from "./router/routes";

type AnyRecord = Record<string, unknown>;

const surface = surfaces.admin;

const hasToken = ref(!!getAccessToken());
const currentUser = ref<AnyRecord | null>(null);
const username = ref("DEMO_ADMIN");
const password = ref("");
const roleApp = reactive({ role_code: "WAREHOUSE", submit_reason: "" });
const roleAppSubmitted = ref(false);
const loginError = ref("");
const roleAppError = ref("");
const loginLoading = ref(false);
const roleAppLoading = ref(false);

const hash = ref(cleanHash(location.hash));
const currentRoute = computed(() => findRouteByPath(hash.value));
const selectedId = computed(() => {
  const parts = hash.value.split("/").filter(Boolean);
  return parts.length > 1 ? parts[1] : null;
});

const pageLoading = ref(false);
const pageError = ref("");
const errorTraceId = ref("");
const records = ref<AnyRecord[]>([]);
const detail = ref<AnyRecord | null>(null);
const summary = ref<AnyRecord | null>(null);

const filters = reactive<Record<string, string>>({
  keyword: "", status: "", order_no: "", tracking_no: "",
  payment_status: "", fulfillment_status: "", refund_status: "", invoice_status: "",
  related_month: new Date().toISOString().slice(0, 7),
  config_group: "PURCHASE", trace_id: "", operation_module: ""
});

const actionType = ref("");
const actionRecord = ref<AnyRecord | null>(null);
const actionLoading = ref(false);
const actionError = ref("");
const actionForm = reactive<Record<string, string>>({});

const permissionSet = computed(() =>
  new Set((currentUser.value?.permission_codes as string[]) ?? [])
);
const menuSet = computed(() =>
  new Set((currentUser.value?.menus as { menu_code: string }[])?.map(m => m.menu_code) ?? [])
);
const hasNoRoles = computed(() => Boolean(currentUser.value && (currentUser.value.roles as string[]).length === 0));

const visibleRoutes = computed(() =>
  routeRegistry.filter(r => r.menuCodes.some(m => menuSet.value.has(m)) || r.permissionCodes.some(p => permissionSet.value.has(p)))
);
const groupedRoutes = computed(() => {
  const groups = new Map<string, AdminRoute[]>();
  for (const r of visibleRoutes.value) {
    const g = groups.get(r.group) ?? [];
    g.push(r);
    groups.set(r.group, g);
  }
  return [...groups.entries()].map(([group, routes]) => ({ group, label: groupLabels[group as AdminRoute["group"]] ?? group, routes }));
});

const hasPageAccess = computed(() => {
  const r = currentRoute.value;
  if (r.key === "dashboard") return true;
  return r.menuCodes.some(m => menuSet.value.has(m)) || r.permissionCodes.some(p => permissionSet.value.has(p));
});
const pageTitle = computed(() => currentRoute.value.title);
const pageNotice = computed(() =>
  "数据来自真实后端接口；状态变更由后端状态机裁决，写操作完成后自动重新查询。"
);

const financeCards = computed(() => {
  const f = summary.value as AnyRecord | null;
  if (!f) return [];
  return [
    { label: "本月收入", value: formatCent(f.monthly_revenue_cent as number) },
    { label: "本月订单", value: f.monthly_order_count ?? "-" },
    { label: "待退款", value: f.pending_refund_count ?? "-" },
    { label: "待发货", value: f.pending_shipment_count ?? "-" }
  ];
});

const tableColumns = computed(() => (columnDefs as Record<string, Column[]>)[currentRoute.value.key] ?? []);

function cleanHash(h: string): string {
  return h.replace(/^#/, "") || "/dashboard";
}

function navigate(path: string): void {
  location.hash = path;
}

function selectRecord(record: AnyRecord): void {
  const route = currentRoute.value;
  const id = getRecordId(record, route.idFields);
  if (id && route.detailPath) {
    location.hash = route.detailPath(id);
  } else {
    detail.value = record;
  }
}

function cellValue(record: AnyRecord, column: Column): unknown {
  if (column.type === "snapshot" && column.snapshotKey && column.snapshotField) {
    return labelFromSnapshot(record, column.snapshotKey, column.snapshotField);
  }
  return record[column.key];
}

function displayValue(record: AnyRecord, column: Column): string {
  const v = cellValue(record, column);
  if (column.type === "amount") return formatCent(v as number | null | undefined);
  if (column.type === "datetime") return formatDateTime(v);
  if (column.type === "boolean") return v ? "是" : "否";
  return emptyText(v);
}

function statusClass(code: string | null | undefined): string {
  if (!code) return "muted";
  if (["PAID", "ISSUED", "SIGNED", "ACTIVE", "SENT", "READ", "CONVERTED", "ON_SHELF", "COMPLETED", "ENABLED", "PUBLISHED", "SUCCESS", "MATCHED", "UPLOADED", "CONFIRMED", "INVOICED"].includes(code)) return "success";
  if (["PENDING", "PENDING_SHIPMENT", "REVIEWING", "PROCESSING", "APPLIED", "TO_BE_ISSUED", "MANUAL_REQUIRED", "PENDING_REVIEW", "PENDING_FOLLOW", "CONTACTED", "PENDING_SUPPLEMENT", "WAIT_CONFIRM", "APPROVING", "NOT_INVOICED", "UNREAD", "DRAFT"].includes(code)) return "warning";
  if (["FAILED", "REJECTED", "REFUNDED", "RED_REVERSED", "REVOKED", "CLOSED", "OFF_SHELF", "DELETED", "DELETE_PENDING", "ABANDONED", "APPROVAL_REJECTED", "CANCELED", "FROZEN", "AMOUNT_DIFF", "FEE_DIFF", "UNMATCHED", "DUPLICATE"].includes(code)) return "danger";
  if (["NO_SHIPMENT", "NONE", "NOT_APPLIED", "DISABLED", "MERGED"].includes(code)) return "muted";
  return "info";
}

function statusLabel(code: string | null | undefined): string {
  if (!code) return "-";
  const m: Record<string, string> = {
    PENDING: "待支付", PAID: "已支付", CLOSED: "已关闭",
    NO_SHIPMENT: "无需发货", PENDING_SHIPMENT: "待发货", SHIPPED: "已发货", SIGNED: "已签收",
    NONE: "无退款", REVIEWING: "审核中", REJECTED: "已拒绝", PROCESSING: "处理中",
    MANUAL_REQUIRED: "待人工处理", FAILED: "失败", REFUNDED: "已退款",
    NOT_APPLIED: "未申请", APPLIED: "已申请", TO_BE_ISSUED: "待开具", ISSUED: "已开具", RED_REVERSED: "已红冲",
    ACTIVE: "已启用", DISABLED: "已停用", ENABLED: "已启用",
    ON_SHELF: "已上架", OFF_SHELF: "已下架", DRAFT: "草稿", PENDING_REVIEW: "待审核",
    DELETE_PENDING: "待删除", DELETED: "已删除",
    FROZEN: "已冻结", REVOKED: "已撤销", MERGED: "已合并",
    APPROVING: "审批中", APPROVAL_REJECTED: "审批拒绝", WAIT_CONFIRM: "待确认",
    CONFIRMED: "已确认", COMPLETED: "已完成", CANCELED: "已取消",
    NOT_INVOICED: "未开票", INVOICED: "已开票",
    MATCHED: "已匹配", AMOUNT_DIFF: "金额差异", FEE_DIFF: "手续费差异", UNMATCHED: "未匹配", DUPLICATE: "重复",
    PENDING_SUPPLEMENT: "待补充", UPLOADED: "已上传",
    IN_APP: "站内通知", SENT: "已发送", UNREAD: "未读", READ: "已读",
    PENDING_FOLLOW: "待跟进", CONTACTED: "已联系", CONVERTED: "已转化", ABANDONED: "已放弃",
    PUBLISHED: "已发布", HIDDEN: "已隐藏",
    LIVE: "直播课", RECORDED: "录制课", MATERIAL: "实物",
    SUCCESS: "成功", CHAPTER: "章节", LESSON: "课节"
  };
  return m[code] ?? code;
}

function fieldLabel(key: string): string {
  const m: Record<string, string> = {
    order_no: "订单编号", shipment_no: "发货单号", purchase_no: "采购单号",
    payment_status: "支付状态", fulfillment_status: "履约状态", refund_status: "退款状态", invoice_status: "开票状态",
    payable_amount_cent: "应付金额", paid_amount_cent: "实付金额",
    logistics_company_name: "物流公司", tracking_no: "运单号",
    shipped_at: "发货时间", signed_at: "签收时间",
    course_title: "课程", student_name: "学员",
    apply_amount_cent: "申请金额", approved_amount_cent: "批准金额",
    invoice_amount_cent: "发票金额", total_amount_cent: "采购金额",
    purchase_status: "采购状态", input_invoice_status: "进项票状态",
    created_at: "创建时间", updated_at: "更新时间",
    status: "状态", exception_flag: "异常", exception_reason: "异常原因",
    receiver_name: "收货人", receiver_mobile: "收货手机",
    supplier_name: "供货商", sku_name: "商品名"
  };
  return m[key] ?? key;
}

interface Column {
  key: string;
  label: string;
  type?: "status" | "amount" | "datetime" | "snapshot" | "boolean";
  snapshotKey?: string;
  snapshotField?: string;
  width?: string;
}

const columnDefs: Record<string, Column[]> = {
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
    { key: "status", label: "状态", type: "status" },
    { key: "created_at", label: "创建时间", type: "datetime" }
  ],
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
    { key: "status", label: "状态", type: "status" },
    { key: "latest_follow_at", label: "最近跟进", type: "datetime" }
  ],
  students: [
    { key: "student_no", label: "学员编号" },
    { key: "nickname", label: "昵称" },
    { key: "mobile", label: "手机号" },
    { key: "source_channel", label: "来源" },
    { key: "total_orders", label: "购课数" },
    { key: "total_amount_cent", label: "消费金额", type: "amount" },
    { key: "status", label: "状态", type: "status" },
    { key: "created_at", label: "注册时间", type: "datetime" }
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
    { key: "config_value", label: "配置值" },
    { key: "masked", label: "脱敏", type: "boolean" }
  ],
  audit: [
    { key: "created_at", label: "时间", type: "datetime" },
    { key: "operator_user_id", label: "操作人" },
    { key: "operation_module", label: "模块" },
    { key: "operation_type", label: "动作" },
    { key: "operation_result", label: "结果", type: "status" }
  ]
};

const actionLabels: Record<string, string> = {
  refundApprove: "审核通过退款",
  refundReject: "驳回退款",
  refundManual: "人工退款完成",
  ship: "确认发货",
  sign: "确认签收",
  invoiceIssue: "开具发票",
  redReverse: "发票红冲",
  reconciliationImport: "导入对账单",
  materialCreate: "创建代账材料",
  materialUpload: "上传材料文件",
  materialConfirm: "确认材料",
  materialClose: "关闭材料"
};

const actionFieldDefs: Record<string, { key: string; label: string; type?: string }[]> = {
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
  materialClose: [{ key: "close_reason", label: "关闭原因" }]
};

function defaultActionForm(type: string): Record<string, string> {
  const fields = actionFieldDefs[type] ?? [];
  const form: Record<string, string> = {};
  for (const f of fields) form[f.key] = "";
  return form;
}

async function doLogin(): Promise<void> {
  loginLoading.value = true;
  loginError.value = "";
  try {
    const data = await request<AnyRecord>("/api/admin/auth/test-login", {
      method: "POST", body: JSON.stringify({ user_no: username.value })
    });
    setAccessToken(data.access_token as string);
    hasToken.value = true;
    await loadUser();
    navigate("/dashboard");
  } catch (e: any) {
    loginError.value = e?.message ?? "登录失败";
  } finally {
    loginLoading.value = false;
  }
}

async function loadUser(): Promise<void> {
  try {
    currentUser.value = await request<AnyRecord>("/api/admin/auth/me");
  } catch {
    currentUser.value = null;
  }
}

function doLogout(): void {
  clearAccessToken();
  hasToken.value = false;
  currentUser.value = null;
  records.value = [];
  detail.value = null;
  summary.value = null;
  hash.value = "/dashboard";
}

async function submitRoleApp(): Promise<void> {
  roleAppLoading.value = true;
  roleAppError.value = "";
  try {
    await request("/api/admin/role-applications", {
      method: "POST",
      body: JSON.stringify(roleApp),
      idempotent: true,
      idempotencyScope: "role-app"
    });
    roleAppSubmitted.value = true;
  } catch (e: any) {
    roleAppError.value = e?.message ?? "提交失败";
  } finally {
    roleAppLoading.value = false;
  }
}

function queryForRoute(route: AdminRoute): Record<string, string> {
  const q: Record<string, string> = { page_no: "1", page_size: "20" };
  if (filters.keyword) q.keyword = filters.keyword;
  if (filters.status) q.status = filters.status;
  if (filters.order_no && route.key === "shipments") q.order_no = filters.order_no;
  if (filters.tracking_no && route.key === "shipments") q.tracking_no = filters.tracking_no;
  if (filters.payment_status && route.key === "orders") q.payment_status = filters.payment_status;
  if (filters.fulfillment_status && route.key === "orders") q.fulfillment_status = filters.fulfillment_status;
  if (filters.refund_status && route.key === "orders") q.refund_status = filters.refund_status;
  if (filters.invoice_status && route.key === "orders") q.invoice_status = filters.invoice_status;
  if (route.key === "reconciliation" && filters.related_month) q.bill_month = filters.related_month;
  return q;
}

async function loadPageData(): Promise<void> {
  const route = currentRoute.value;
  if (!currentUser.value || hasNoRoles.value) return;
  pageLoading.value = true;
  pageError.value = "";
  detail.value = null;
  summary.value = null;
  try {
    if (route.key === "dashboard") {
      await loadDashboard();
      return;
    }
    if (!route.listPath) {
      records.value = [];
      pageError.value = "当前页面缺少后端列表接口。";
      return;
    }
    const listData = await request<unknown>(route.listPath, { query: queryForRoute(route) });
    records.value = normalizeRecords<AnyRecord>(listData);
    if (selectedId.value && route.detailPath) {
      detail.value = await request<AnyRecord>(route.detailPath(selectedId.value));
    }
  } catch (e: any) {
    pageError.value = e?.message ?? "加载失败";
    errorTraceId.value = e?.traceId ?? "";
  } finally {
    pageLoading.value = false;
  }
}

async function loadDashboard(): Promise<void> {
  try {
    const [ordersData, financeData] = await Promise.all([
      request<unknown>("/api/admin/orders", { query: { page_no: "1", page_size: "10" } }),
      request<unknown>("/api/admin/accounting-workbench/summary")
    ]);
    records.value = normalizeRecords<AnyRecord>(ordersData);
    summary.value = financeData as AnyRecord;
  } catch (e: any) {
    pageError.value = e?.message ?? "首页加载失败";
  }
}

function openModal(type: string, record: AnyRecord): void {
  actionType.value = type;
  actionRecord.value = record;
  actionError.value = "";
  Object.assign(actionForm, defaultActionForm(type));
}

function closeModal(): void {
  actionType.value = "";
  actionRecord.value = null;
  actionError.value = "";
}

async function submitModal(): Promise<void> {
  actionLoading.value = true;
  actionError.value = "";
  try {
    await executeAction(actionType.value, getActionId());
    closeModal();
    await loadPageData();
  } catch (e: any) {
    actionError.value = e?.message ?? "操作失败";
  } finally {
    actionLoading.value = false;
  }
}

function getActionId(): string {
  if (["reconciliationImport", "materialCreate"].includes(actionType.value)) return "";
  const record = actionRecord.value;
  if (!record) return "";
  return getRecordId(record, ["id", "shipment_id", "refund_id", "invoice_id", "batch_id", "material_id", "order_id", "purchase_id", "approval_id"]) ?? "";
}

const ACTION_BINDINGS: Record<string, { urlFor: (id: string) => string; action?: string }> = {
  refundApprove:        { urlFor: (id) => `/api/admin/refunds/${id}/review`, action: "APPROVE" },
  refundReject:         { urlFor: (id) => `/api/admin/refunds/${id}/review`, action: "REJECT" },
  refundManual:         { urlFor: (id) => `/api/admin/refunds/${id}/manual-complete` },
  ship:                 { urlFor: (id) => `/api/admin/shipments/${id}/ship` },
  sign:                 { urlFor: (id) => `/api/admin/shipments/${id}/sign` },
  invoiceIssue:         { urlFor: (id) => `/api/admin/invoices/${id}/issue-manual` },
  redReverse:           { urlFor: (id) => `/api/admin/invoices/${id}/red-reverse` },
  reconciliationImport: { urlFor: () => `/api/admin/reconciliation/batches` },
  materialCreate:       { urlFor: () => `/api/admin/accounting/materials` },
  materialUpload:       { urlFor: (id) => `/api/admin/accounting/materials/${id}/actions`, action: "UPLOAD" },
  materialConfirm:      { urlFor: (id) => `/api/admin/accounting/materials/${id}/actions`, action: "CONFIRM" },
  materialClose:        { urlFor: (id) => `/api/admin/accounting/materials/${id}/actions`, action: "CLOSE" }
};

async function executeAction(type: string, id: string): Promise<void> {
  const binding = ACTION_BINDINGS[type];
  if (!binding) throw new Error(`未知操作: ${type}`);
  const payload: Record<string, unknown> = { ...actionForm };
  for (const key of Object.keys(payload)) {
    if (key.endsWith("_cent") || key === "total_count" || key === "quantity") {
      payload[key] = Number(payload[key]) || 0;
    }
  }
  if (binding.action) payload.action = binding.action;
  const opts = { method: "POST", body: JSON.stringify(payload), idempotent: true, idempotencyScope: type };
  await request(binding.urlFor(id), opts);
}

function routeActions(routeKey: RouteKey): { type: string; label: string }[] {
  const all: Record<string, { type: string; label: string }[]> = {
    refunds: [
      { type: "refundApprove", label: "审核通过" },
      { type: "refundReject", label: "驳回" },
      { type: "refundManual", label: "人工退款" }
    ],
    shipments: [
      { type: "ship", label: "确认发货" },
      { type: "sign", label: "确认签收" }
    ],
    invoices: [
      { type: "invoiceIssue", label: "开具发票" },
      { type: "redReverse", label: "红冲" }
    ],
    reconciliation: [{ type: "reconciliationImport", label: "导入对账单" }],
    accounting: [
      { type: "materialCreate", label: "创建材料" },
      { type: "materialUpload", label: "上传文件" },
      { type: "materialConfirm", label: "确认" },
      { type: "materialClose", label: "关闭" }
    ]
  };
  return all[routeKey] ?? [];
}

onMounted(async () => {
  window.addEventListener("hashchange", () => {
    hash.value = cleanHash(location.hash);
  });
  if (hasToken.value) await loadUser();
});

watch([currentRoute, selectedId, currentUser], () => {
  if (currentUser.value && !hasNoRoles.value) loadPageData();
}, { immediate: false });
</script>

<template>
  <main v-if="!hasToken" class="login-screen">
    <section class="login-card">
      <div class="brand-block">
        <div class="brand-mark">BFT</div>
        <span>{{ surface.name }}</span>
        <h1>登录</h1>
      </div>
      <label>测试账号
        <input v-model="username" type="text" @keyup.enter="doLogin()" />
      </label>
      <button class="primary" :disabled="loginLoading" @click="doLogin()">
        {{ loginLoading ? "登录中..." : "登录" }}
      </button>
      <p v-if="loginError" class="error-line">{{ loginError }}</p>
      <p class="hint">{{ surface.authBoundary }}</p>
    </section>
  </main>

  <main v-else-if="hasNoRoles" class="login-screen">
    <section class="login-card wide">
      <div class="brand-block">
        <div class="brand-mark">BFT</div>
        <span>{{ surface.name }}</span>
        <h1>未分配权限</h1>
      </div>
      <p v-if="roleAppSubmitted" class="success-line">角色申请已提交，请等待管理员审核。</p>
      <form v-else @submit.prevent="submitRoleApp()">
        <label>申请角色
          <select v-model="roleApp.role_code">
            <option value="WAREHOUSE">仓管</option>
            <option value="OPS">运营</option>
            <option value="EDU_ADMIN">教务</option>
            <option value="TEACHER">讲师</option>
            <option value="CS">客服</option>
            <option value="ACCOUNTING">代账人员</option>
          </select>
        </label>
        <label>申请理由
          <textarea v-model="roleApp.submit_reason" rows="3"></textarea>
        </label>
        <button class="primary" type="submit" :disabled="roleAppLoading">
          {{ roleAppLoading ? "提交中..." : "提交申请" }}
        </button>
      </form>
      <p v-if="roleAppError" class="error-line">{{ roleAppError }}</p>
      <button class="ghost" @click="doLogout()">退出登录</button>
    </section>
  </main>

  <main v-else class="admin-shell">
    <aside class="sidebar">
      <div class="sidebar-head">
        <div class="brand-mark small">BFT</div>
        <span>管理端</span>
      </div>
      <nav>
        <section v-for="group in groupedRoutes" :key="group.group" class="menu-group">
          <p>{{ group.label }}</p>
          <button
            v-for="route in group.routes" :key="route.key" type="button"
            :class="{ active: currentRoute.key === route.key }"
            @click="navigate(route.path)"
          >{{ route.title }}</button>
        </section>
      </nav>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div class="breadcrumb">
          <span>首页</span>
          <span>/</span>
          <span>{{ pageTitle }}</span>
          <span v-if="selectedId">/</span>
          <span v-if="selectedId">{{ selectedId }}</span>
        </div>
        <h1>{{ pageTitle }}</h1>
        <div class="userbox">
          <span>{{ currentUser?.display_name ?? "-" }}</span>
          <span class="roles">{{ (currentUser?.roles as string[])?.join(", ") ?? "-" }}</span>
          <button class="ghost" @click="doLogout()">退出</button>
        </div>
      </header>

      <template v-if="hasPageAccess">
        <section class="page-head">
          <h2>{{ pageTitle }}</h2>
          <p>{{ pageNotice }}</p>
        </section>

        <section v-if="currentRoute.key === 'dashboard' && financeCards.length" class="summary-grid">
          <article v-for="card in financeCards" :key="card.label">
            <p>{{ card.label }}</p>
            <strong>{{ card.value }}</strong>
          </article>
        </section>

        <section v-if="currentRoute.key !== 'dashboard'" class="filter-row">
          <input v-if="['orders','refunds','shipments','courses','leads','students','inventory','purchases','audit'].includes(currentRoute.key)"
            v-model="filters.keyword" placeholder="搜索..." @keyup.enter="loadPageData()" />
          <input v-if="currentRoute.key === 'shipments'" v-model="filters.order_no" placeholder="订单号" />
          <input v-if="currentRoute.key === 'shipments'" v-model="filters.tracking_no" placeholder="运单号" />
          <select v-if="currentRoute.key === 'orders'" v-model="filters.payment_status" @change="loadPageData()">
            <option value="">支付状态</option>
            <option value="PENDING">待支付</option>
            <option value="PAID">已支付</option>
            <option value="CLOSED">已关闭</option>
          </select>
          <select v-if="currentRoute.key === 'orders'" v-model="filters.fulfillment_status" @change="loadPageData()">
            <option value="">履约状态</option>
            <option value="NO_SHIPMENT">无需发货</option>
            <option value="PENDING_SHIPMENT">待发货</option>
            <option value="SHIPPED">已发货</option>
            <option value="SIGNED">已签收</option>
          </select>
          <select v-if="currentRoute.key === 'orders'" v-model="filters.refund_status" @change="loadPageData()">
            <option value="">退款状态</option>
            <option value="NONE">无退款</option>
            <option value="REVIEWING">审核中</option>
            <option value="REFUNDED">已退款</option>
          </select>
          <button class="secondary" @click="loadPageData()">刷新</button>
        </section>

        <section v-if="pageError" class="state-panel warning">
          <h2>加载失败</h2>
          <p>{{ pageError }}</p>
          <p v-if="errorTraceId">TraceId: {{ errorTraceId }}</p>
        </section>

        <div class="content-split">
          <div class="table-panel">
            <p v-if="pageLoading">加载中...</p>
            <p v-else-if="records.length === 0 && !pageError">暂无数据</p>
            <table v-else>
              <thead>
                <tr>
                  <th v-for="col in tableColumns" :key="col.key" :style="{ width: col.width }">{{ col.label }}</th>
                  <th class="action-cell">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in records" :key="getRecordId(record, currentRoute.idFields) ?? JSON.stringify(record).slice(0,80)" @click="selectRecord(record)">
                  <td v-for="col in tableColumns" :key="col.key">
                    <span v-if="col.type === 'status'" class="status-tag" :class="statusClass(cellValue(record, col) as string)">{{ statusLabel(cellValue(record, col) as string) }}</span>
                    <span v-else>{{ displayValue(record, col) }}</span>
                  </td>
                  <td class="action-cell"><button class="link" type="button">查看</button></td>
                </tr>
              </tbody>
            </table>
          </div>

          <aside v-if="detail && currentRoute.key !== 'dashboard'" class="detail-panel">
            <div class="detail-title">
              <h3>详情</h3>
              <div class="detail-actions">
                <button v-for="act in routeActions(currentRoute.key)" :key="act.type" type="button" @click="openModal(act.type, detail)">{{ act.label }}</button>
              </div>
            </div>
            <dl class="description-list">
              <template v-for="(val, key) in detail" :key="key">
                <template v-if="typeof val !== 'object' || val === null">
                  <dt>{{ fieldLabel(key as string) }}</dt>
                  <dd>
                    <span v-if="currentRoute.statusFields?.includes(key as string)" class="status-tag" :class="statusClass(val as string)">{{ statusLabel(val as string) }}</span>
                    <span v-else-if="currentRoute.amountFields?.includes(key as string)">{{ formatCent(val as number) }}</span>
                    <span v-else>{{ emptyText(val) }}</span>
                  </dd>
                </template>
              </template>
            </dl>
          </aside>
        </div>
      </template>

      <template v-else>
        <section class="state-panel">
          <h2>无访问权限</h2>
          <p>当前角色无权访问此页面，请联系管理员。</p>
        </section>
      </template>
    </section>

    <section v-if="actionType" class="modal-layer">
      <form class="modal" @submit.prevent="submitModal()">
        <header>
          <h2>{{ actionLabels[actionType] ?? actionType }}</h2>
          <button type="button" class="ghost" @click="closeModal()">关闭</button>
        </header>
        <label v-for="field in (actionFieldDefs[actionType] ?? [])" :key="field.key">
          {{ field.label }}
          <input v-model="actionForm[field.key]" type="text" />
        </label>
        <p v-if="actionError" class="error-line">{{ actionError }}</p>
        <footer>
          <button type="button" class="secondary" @click="closeModal()">取消</button>
          <button type="submit" class="primary" :disabled="actionLoading">{{ actionLoading ? "提交中..." : "提交" }}</button>
        </footer>
      </form>
    </section>
  </main>
</template>
