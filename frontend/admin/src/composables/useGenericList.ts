import { computed, onMounted, watch } from "vue";
import { useRoute } from "vue-router";
import { storeToRefs } from "pinia";
import { usePageStore } from "@/stores/page";
import { useAuthStore } from "@/stores/auth";
import { columnsForRoute } from "@/config/columns";
import { actionsForRoute } from "@/config/actions";
import { routeRegistry, type AdminRoute, type RouteKey } from "@/router/routes";

export function useGenericList() {
  const route = useRoute();
  const pageStore = usePageStore();
  const authStore = useAuthStore();
  const {
    records,
    detail,
    pageLoading,
    detailLoading,
    pageError,
    detailError,
    errorTraceId,
    filters,
    selectedId
  } = storeToRefs(pageStore);

  const routeKey = computed<RouteKey>(() => {
    return (route.meta.routeKey as RouteKey | undefined) ?? "dashboard";
  });

  const adminRoute = computed<AdminRoute>(() => {
    return routeRegistry.find((r) => r.key === routeKey.value) ?? routeRegistry[0];
  });

  const columns = computed(() => columnsForRoute(routeKey.value));
  const actions = computed(() => actionsForRoute(routeKey.value));

  const hasPageAccess = computed<boolean>(() => authStore.hasRouteAccess(routeKey.value));

  async function reload(): Promise<void> {
    if (!authStore.currentUser || authStore.hasNoRoles) return;
    await pageStore.loadPageData(routeKey.value);
    await maybeAutoOpenFromQuery();
  }

  async function maybeAutoOpenFromQuery(): Promise<void> {
    const targetRoute = adminRoute.value;
    if (!targetRoute?.listPath) return;
    const queryId = (route.query.open_document_id ?? null) as string | null;
    const queryNo = (route.query.open_document_no ?? null) as string | null;
    if (!queryId && !queryNo) return;
    // 优先尝试 id 直接 detail 接口
    if (queryId && targetRoute.detailPath) {
      await pageStore.loadDetail(targetRoute, queryId);
      pageStore.selectedId = queryId;
      return;
    }
    // 否则在 records 中按 number 字段匹配
    if (queryNo) {
      const match = pageStore.records.find((r) => {
        const candidates: unknown[] = [
          (r as Record<string, unknown>).order_no,
          (r as Record<string, unknown>).payment_no,
          (r as Record<string, unknown>).refund_no,
          (r as Record<string, unknown>).invoice_no,
          (r as Record<string, unknown>).shipment_no,
          (r as Record<string, unknown>).entitlement_no,
          (r as Record<string, unknown>).batch_no,
          (r as Record<string, unknown>).material_no,
          (r as Record<string, unknown>).log_no
        ];
        return candidates.some((v) => v && String(v) === queryNo);
      });
      if (match) {
        const idField = targetRoute.idFields[0];
        const id = idField ? (match[idField] as string | number | undefined) : undefined;
        await pageStore.selectRecord(routeKey.value, match, id == null ? null : String(id));
      }
    }
  }

  async function selectRecord(record: Record<string, unknown>, id: string | null): Promise<void> {
    await pageStore.selectRecord(routeKey.value, record, id);
  }

  async function clearAndReload(): Promise<void> {
    pageStore.clearFilters();
    await reload();
  }

  function closeDetail(): void {
    pageStore.clearDetail();
  }

  onMounted(() => {
    pageStore.reset();
    reload();
  });

  watch(routeKey, () => {
    pageStore.reset();
    reload();
  });

  return {
    routeKey,
    adminRoute,
    columns,
    actions,
    records,
    detail,
    pageLoading,
    detailLoading,
    pageError,
    detailError,
    errorTraceId,
    filters,
    selectedId,
    hasPageAccess,
    reload,
    clearAndReload,
    closeDetail,
    selectRecord
  };
}
