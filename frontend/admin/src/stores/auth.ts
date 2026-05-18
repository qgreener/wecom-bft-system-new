import { computed, reactive, ref } from "vue";
import { defineStore } from "pinia";
import { clearAccessToken, getAccessToken, request, setAccessToken } from "@/services/http";
import { type AdminRoute, routeRegistry } from "@/router/routes";

type AnyRecord = Record<string, unknown>;

export const useAuthStore = defineStore("auth", () => {
  const hasToken = ref<boolean>(!!getAccessToken());
  const currentUser = ref<AnyRecord | null>(null);
  const loginError = ref("");
  const loginLoading = ref(false);
  const roleApp = reactive({ role_code: "WAREHOUSE", submit_reason: "" });
  const roleAppLoading = ref(false);
  const roleAppError = ref("");
  const roleAppSubmitted = ref(false);

  let bootstrapPromise: Promise<void> | null = null;

  async function bootstrap(): Promise<void> {
    if (!hasToken.value) return;
    if (bootstrapPromise) return bootstrapPromise;
    bootstrapPromise = loadUser().catch(() => {
      // 拉取失败说明 token 失效，清理状态让守卫重定向到登录
      logout();
    });
    return bootstrapPromise;
  }

  async function loadUser(): Promise<void> {
    currentUser.value = await request<AnyRecord>("/api/admin/auth/me");
  }

  async function login(userNo: string): Promise<void> {
    loginLoading.value = true;
    loginError.value = "";
    try {
      const data = await request<AnyRecord>("/api/admin/auth/test-login", {
        method: "POST",
        body: JSON.stringify({ user_no: userNo })
      });
      setAccessToken(data.access_token as string);
      hasToken.value = true;
      bootstrapPromise = null;
      await bootstrap();
    } catch (e: unknown) {
      loginError.value = (e as { message?: string })?.message ?? "登录失败";
      throw e;
    } finally {
      loginLoading.value = false;
    }
  }

  function logout(): void {
    clearAccessToken();
    hasToken.value = false;
    currentUser.value = null;
    bootstrapPromise = null;
  }

  async function submitRoleApplication(): Promise<void> {
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
    } catch (e: unknown) {
      roleAppError.value = (e as { message?: string })?.message ?? "提交失败";
      throw e;
    } finally {
      roleAppLoading.value = false;
    }
  }

  const permissionSet = computed(
    () => new Set((currentUser.value?.permission_codes as string[]) ?? [])
  );

  const menuSet = computed(
    () => new Set(
      (currentUser.value?.menus as { menu_code: string }[] | undefined)?.map((m) => m.menu_code) ?? []
    )
  );

  const hasNoRoles = computed<boolean>(() => {
    if (!currentUser.value) return false;
    const roles = currentUser.value.roles as unknown[] | undefined;
    return Array.isArray(roles) && roles.length === 0;
  });

  const visibleRoutes = computed<AdminRoute[]>(() =>
    routeRegistry.filter(
      (r) =>
        r.menuCodes.some((m) => menuSet.value.has(m)) ||
        r.permissionCodes.some((p) => permissionSet.value.has(p))
    )
  );

  function hasRouteAccess(routeKey: string): boolean {
    if (routeKey === "dashboard") return true;
    const route = routeRegistry.find((r) => r.key === routeKey);
    if (!route) return false;
    return (
      route.menuCodes.some((m) => menuSet.value.has(m)) ||
      route.permissionCodes.some((p) => permissionSet.value.has(p))
    );
  }

  return {
    hasToken,
    currentUser,
    loginError,
    loginLoading,
    roleApp,
    roleAppLoading,
    roleAppError,
    roleAppSubmitted,
    bootstrap,
    login,
    logout,
    submitRoleApplication,
    permissionSet,
    menuSet,
    hasNoRoles,
    visibleRoutes,
    hasRouteAccess
  };
});
