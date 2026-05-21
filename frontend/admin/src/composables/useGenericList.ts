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
  }

  async function selectRecord(record: Record<string, unknown>, id: string | null): Promise<void> {
    await pageStore.selectRecord(routeKey.value, record, id);
  }

  async function clearAndReload(): Promise<void> {
    pageStore.clearFilters();
    await reload();
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
    selectRecord
  };
}
