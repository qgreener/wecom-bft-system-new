import { ref } from "vue";
import { defineStore } from "pinia";
import { request } from "@/services/http";
import { normalizeRecords } from "@/utils/format";

type AnyRecord = Record<string, unknown>;

export const useDashboardStore = defineStore("dashboard", () => {
  const summary = ref<AnyRecord | null>(null);
  const recentOrders = ref<AnyRecord[]>([]);
  const loading = ref(false);
  const error = ref("");

  async function load(): Promise<void> {
    loading.value = true;
    error.value = "";
    try {
      const [ordersData, financeData] = await Promise.all([
        request<unknown>("/api/admin/orders", { query: { page_no: "1", page_size: "10" } }),
        request<unknown>("/api/admin/accounting-workbench/summary")
      ]);
      recentOrders.value = normalizeRecords<AnyRecord>(ordersData);
      summary.value = financeData as AnyRecord;
    } catch (e: unknown) {
      error.value = (e as { message?: string })?.message ?? "首页加载失败";
    } finally {
      loading.value = false;
    }
  }

  return { summary, recentOrders, loading, error, load };
});
