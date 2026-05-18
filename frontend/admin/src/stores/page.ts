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
  const pageError = ref("");
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
    operation_module: ""
  });

  function reset(): void {
    records.value = [];
    detail.value = null;
    selectedId.value = null;
    pageError.value = "";
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

  async function loadPageData(routeKey: RouteKey): Promise<void> {
    const route = routeRegistry.find((r) => r.key === routeKey);
    if (!route) return;
    pageLoading.value = true;
    pageError.value = "";
    detail.value = null;
    try {
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
    } catch (e: unknown) {
      const err = e as { message?: string; traceId?: string };
      pageError.value = err?.message ?? "加载失败";
      errorTraceId.value = err?.traceId ?? "";
    } finally {
      pageLoading.value = false;
    }
  }

  function selectRecord(routeKey: RouteKey, record: AnyRecord, recordId: string | null): void {
    const route = routeRegistry.find((r) => r.key === routeKey);
    if (recordId && route?.detailPath) {
      selectedId.value = recordId;
      // 详情会在路由变化或 loadPageData 中拉
      detail.value = null;
    } else {
      detail.value = record;
    }
  }

  return {
    records,
    detail,
    pageLoading,
    pageError,
    errorTraceId,
    selectedId,
    filters,
    reset,
    setFilter,
    clearFilters,
    loadPageData,
    selectRecord
  };
});
