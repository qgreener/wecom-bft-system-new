import { reactive, ref } from "vue";
import { defineStore } from "pinia";
import { request } from "@/services/http";
import { normalizeRecords } from "@/utils/format";
import { routeRegistry, type AdminRoute, type RouteKey } from "@/router/routes";

type AnyRecord = Record<string, unknown>;

export const usePageStore = defineStore("page", () => {
  const records = ref<AnyRecord[]>([]);
  const detail = ref<AnyRecord | null>(null);
  const pageLoading = ref(false);
  const detailLoading = ref(false);
  const pageError = ref("");
  const detailError = ref("");
  const errorTraceId = ref("");
  const selectedId = ref<string | null>(null);
  const filters = reactive<Record<string, string>>({
    keyword: "",
    status: "",
    order_no: "",
    tracking_no: "",
    payment_status: "",
    fulfillment_status: "",
    refund_status: "",
    invoice_status: "",
    related_month: new Date().toISOString().slice(0, 7),
    config_group: "PURCHASE",
    trace_id: "",
    operation_module: "",
    operation_type: "",
    target_type: "",
    course_type: "",
    source_channel: "",
    mobile: "",
    category_code: "",
    warning_only: "",
    access_status: "",
    payment_result: "",
    merchant_order_no: "",
    order_id: "",
    student_id: "",
    course_id: "",
    paid_at_start: "",
    paid_at_end: "",
    exception_flag: ""
  });

  function reset(): void {
    records.value = [];
    detail.value = null;
    selectedId.value = null;
    pageError.value = "";
    detailError.value = "";
    errorTraceId.value = "";
  }

  function setFilter(key: string, value: string): void {
    filters[key] = value;
  }

  function clearFilters(): void {
    for (const key of Object.keys(filters)) {
      filters[key] = "";
    }
    filters.related_month = new Date().toISOString().slice(0, 7);
    filters.config_group = "PURCHASE";
  }

  function clearDetail(): void {
    detail.value = null;
    selectedId.value = null;
    detailError.value = "";
  }

  function queryForRoute(route: AdminRoute): Record<string, string> {
    const q: Record<string, string> = { page_no: "1", page_size: "20" };
    if (filters.keyword) q.keyword = filters.keyword;
    if (filters.status && route.key !== "orders") q.status = filters.status;
    if (filters.order_no && route.key === "shipments") q.order_no = filters.order_no;
    if (filters.order_no && (route.key === "refunds" || route.key === "invoices")) q.order_no = filters.order_no;
    if (filters.tracking_no && route.key === "shipments") q.tracking_no = filters.tracking_no;
    if (filters.exception_flag && route.key === "shipments") q.exception_flag = filters.exception_flag;
    if (filters.payment_status && route.key === "orders") q.payment_status = filters.payment_status;
    if (filters.fulfillment_status && route.key === "orders") q.fulfillment_status = filters.fulfillment_status;
    if (filters.refund_status && route.key === "orders") q.refund_status = filters.refund_status;
    if (filters.invoice_status && route.key === "orders") q.invoice_status = filters.invoice_status;
    if (filters.paid_at_start && (route.key === "orders" || route.key === "payments")) q.paid_at_start = filters.paid_at_start;
    if (filters.paid_at_end && (route.key === "orders" || route.key === "payments")) q.paid_at_end = filters.paid_at_end;
    if (filters.course_type && route.key === "courses") q.course_type = filters.course_type;
    if (filters.source_channel && route.key === "leads") q.source_channel = filters.source_channel;
    if (filters.mobile && route.key === "leads") q.mobile = filters.mobile;
    if (filters.category_code && route.key === "inventory") q.category_code = filters.category_code;
    if (filters.warning_only && route.key === "inventory") q.warning_only = filters.warning_only;
    if (filters.access_status && route.key === "suppliers") q.access_status = filters.access_status;
    if (filters.payment_result && route.key === "payments") q.payment_result = filters.payment_result;
    if (filters.merchant_order_no && route.key === "payments") q.merchant_order_no = filters.merchant_order_no;
    if (filters.order_id && route.key === "payments") q.order_id = filters.order_id;
    if (filters.student_id && route.key === "entitlements") q.student_id = filters.student_id;
    if (filters.course_id && route.key === "entitlements") q.course_id = filters.course_id;
    if (route.key === "reconciliation" && filters.related_month) q.bill_month = filters.related_month;
    if (route.key === "accounting" && filters.related_month) q.related_month = filters.related_month;
    if (route.key === "audit") {
      if (filters.trace_id) q.trace_id = filters.trace_id;
      if (filters.operation_module) q.operation_module = filters.operation_module;
      if (filters.operation_type) q.operation_type = filters.operation_type;
      if (filters.target_type) q.target_type = filters.target_type;
    }
    if (route.key === "settings") q.config_group = filters.config_group || "PURCHASE";
    if (route.key === "logisticsConfig") q.config_group = "LOGISTICS";
    return q;
  }

  async function loadPageData(routeKey: RouteKey): Promise<void> {
    const route = routeRegistry.find((r) => r.key === routeKey);
    if (!route) return;
    pageLoading.value = true;
    pageError.value = "";
    detailError.value = "";
    detail.value = null;
    try {
      if (!route.listPath) {
        records.value = [];
        pageError.value = "当前页面缺少后端接口，已保留入口用于后续联调。";
        return;
      }
      const listData = await request<unknown>(route.listPath, { query: queryForRoute(route) });
      records.value = normalizeRecords<AnyRecord>(listData);
      if (selectedId.value && route.detailPath) {
        await loadDetail(route, selectedId.value);
      }
    } catch (e: unknown) {
      const err = e as { message?: string; traceId?: string };
      pageError.value = err?.message ?? "加载失败";
      errorTraceId.value = err?.traceId ?? "";
    } finally {
      pageLoading.value = false;
    }
  }

  async function loadDetail(route: AdminRoute, recordId: string): Promise<void> {
    if (!route.detailPath) return;
    detailLoading.value = true;
    detailError.value = "";
    try {
      detail.value = await request<AnyRecord>(route.detailPath(recordId));
    } catch (e: unknown) {
      const err = e as { message?: string; traceId?: string };
      detailError.value = err?.message ?? "详情加载失败";
      errorTraceId.value = err?.traceId ?? "";
    } finally {
      detailLoading.value = false;
    }
  }

  async function selectRecord(routeKey: RouteKey, record: AnyRecord, recordId: string | null): Promise<void> {
    const route = routeRegistry.find((r) => r.key === routeKey);
    selectedId.value = recordId;
    detail.value = record;
    detailError.value = "";
    if (recordId && route?.detailPath) {
      await loadDetail(route, recordId);
    }
  }

  return {
    records,
    detail,
    pageLoading,
    detailLoading,
    pageError,
    detailError,
    errorTraceId,
    selectedId,
    filters,
    reset,
    setFilter,
    clearFilters,
    clearDetail,
    loadPageData,
    loadDetail,
    selectRecord
  };
});
