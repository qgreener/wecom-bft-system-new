import { ref } from "vue";
import { defineStore } from "pinia";
import { request } from "@/services/http";
import { normalizeRecords } from "@/utils/format";

type AnyRecord = Record<string, unknown>;

export const useDashboardStore = defineStore("dashboard", () => {
  const summary = ref<AnyRecord | null>(null);
  const todos = ref<AnyRecord | null>(null);
  const recentOrders = ref<AnyRecord[]>([]);
  const loading = ref(false);
  const error = ref("");

  async function load(): Promise<void> {
    loading.value = true;
    error.value = "";
    const errors: string[] = [];
    try {
      await Promise.all([
        request<unknown>("/api/admin/orders", { query: { page_no: "1", page_size: "10" } })
          .then((ordersData) => {
            recentOrders.value = normalizeRecords<AnyRecord>(ordersData);
          })
          .catch((e: unknown) => {
            recentOrders.value = [];
            errors.push((e as { message?: string })?.message ?? "最近订单加载失败");
          }),
        request<unknown>("/api/admin/accounting-workbench/summary")
          .then((financeData) => {
            summary.value = financeData as AnyRecord;
          })
          .catch((e: unknown) => {
            summary.value = null;
            errors.push((e as { message?: string })?.message ?? "财税摘要加载失败");
          }),
        request<unknown>("/api/admin/dashboard/todos")
          .then((todoData) => {
            todos.value = todoData as AnyRecord;
          })
          .catch((e: unknown) => {
            todos.value = null;
            errors.push((e as { message?: string })?.message ?? "待办加载失败");
          })
      ]);
      error.value = errors.join("；");
    } finally {
      loading.value = false;
    }
  }

  return { summary, todos, recentOrders, loading, error, load };
});
