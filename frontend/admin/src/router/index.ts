import { createRouter, createWebHashHistory, type RouteRecordRaw } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { routeRegistry, type RouteKey } from "@/router/routes";

const PUBLIC_PATHS = new Set<string>(["/login", "/role-application", "/oauth-success"]);
type AdminRouteComponent = NonNullable<RouteRecordRaw["component"]>;

const componentForRoute = (key: RouteKey): AdminRouteComponent => {
  const components: Record<RouteKey, AdminRouteComponent> = {
    dashboard: () => import("@/views/DashboardView.vue"),
    orders: () => import("@/views/trade/OrdersView.vue"),
    payments: () => import("@/views/finance/PaymentsView.vue"),
    refunds: () => import("@/views/trade/RefundsView.vue"),
    shipments: () => import("@/views/trade/ShipmentsView.vue"),
    invoices: () => import("@/views/trade/InvoicesView.vue"),
    reconciliation: () => import("@/views/finance/ReconciliationView.vue"),
    accounting: () => import("@/views/finance/AccountingView.vue"),
    reports: () => import("@/views/finance/ReportsView.vue"),
    courses: () => import("@/views/course/CoursesView.vue"),
    entitlements: () => import("@/views/course/EntitlementsView.vue"),
    leads: () => import("@/views/crm/LeadsView.vue"),
    students: () => import("@/views/crm/StudentsView.vue"),
    inventory: () => import("@/views/supply/InventoryView.vue"),
    purchases: () => import("@/views/supply/PurchasesView.vue"),
    suppliers: () => import("@/views/supply/SuppliersView.vue"),
    taxRules: () => import("@/views/finance/TaxRulesView.vue"),
    logisticsConfig: () => import("@/views/system/LogisticsConfigView.vue"),
    settings: () => import("@/views/system/SettingsView.vue"),
    audit: () => import("@/views/system/AuditView.vue"),
    promotionCodes: () => import("@/views/crm/PromotionCodesView.vue"),
    accountManagement: () => import("@/views/system/AccountManagementView.vue")
  };
  return components[key];
};

const businessRoutes: RouteRecordRaw[] = routeRegistry.map((route) => ({
  path: route.path,
  name: route.key,
  meta: {
    routeKey: route.key,
    title: route.title,
    group: route.group,
    requiresAuth: true
  },
  component: componentForRoute(route.key)
}));

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    component: () => import("@/layouts/BlankLayout.vue"),
    children: [
      // 空 hash（如 https://finhub.tax/admin/）落到这里，由守卫决定去 dashboard 还是 login
      {
        path: "",
        name: "root",
        redirect: "/dashboard"
      },
      {
        path: "login",
        name: "login",
        meta: { title: "登录", public: true },
        component: () => import("@/views/LoginView.vue")
      },
      {
        path: "role-application",
        name: "role-application",
        meta: { title: "申请角色", public: true, requiresAuth: true },
        component: () => import("@/views/RoleApplicationView.vue")
      },
      {
        path: "oauth-success",
        name: "oauth-success",
        meta: { title: "登录中", public: true },
        component: () => import("@/views/OAuthSuccessView.vue")
      }
    ]
  },
  {
    path: "/",
    component: () => import("@/layouts/AdminShell.vue"),
    children: [
      ...businessRoutes,
      {
        path: "courses/:courseId/edit",
        name: "course-edit",
        meta: { routeKey: "courses", title: "课程编辑", group: "course", requiresAuth: true },
        component: () => import("@/views/course/CourseEditView.vue"),
        props: true
      }
    ]
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/dashboard"
  }
];

export const router = createRouter({
  history: createWebHashHistory("/admin/"),
  routes
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();

  // 兼容刷新场景：若 token 在 localStorage 而 currentUser 尚未加载，await 一次 bootstrap
  if (auth.hasToken && !auth.currentUser) {
    await auth.bootstrap();
  }

  const isPublic = PUBLIC_PATHS.has(to.path);
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth);

  if (to.path === "/login") {
    if (auth.hasToken && auth.currentUser) {
      return auth.hasNoRoles ? "/role-application" : "/dashboard";
    }
    return true;
  }

  if (to.path === "/role-application") {
    if (!auth.hasToken) return "/login";
    if (auth.currentUser && !auth.hasNoRoles) return "/dashboard";
    return true;
  }

  if (requiresAuth && !auth.hasToken) {
    return "/login";
  }

  if (auth.hasToken && auth.hasNoRoles) {
    return "/role-application";
  }

  if (!isPublic && requiresAuth && auth.currentUser) {
    const targetKey = to.meta.routeKey as string | undefined;
    if (targetKey && !auth.hasRouteAccess(targetKey)) {
      const fallback = auth.visibleRoutes[0];
      return fallback ? fallback.path : "/role-application";
    }
  }

  return true;
});
