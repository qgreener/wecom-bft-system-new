import { createRouter, createWebHashHistory, type RouteRecordRaw } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { routeRegistry } from "@/router/routes";

const PUBLIC_PATHS = new Set<string>(["/login", "/role-application"]);

const businessRoutes: RouteRecordRaw[] = routeRegistry.map((route) => ({
  path: route.path,
  name: route.key,
  meta: {
    routeKey: route.key,
    title: route.title,
    group: route.group,
    requiresAuth: true
  },
  component:
    route.key === "dashboard"
      ? () => import("@/views/DashboardView.vue")
      : () => import("@/views/GenericListView.vue")
}));

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    component: () => import("@/layouts/BlankLayout.vue"),
    children: [
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
      }
    ]
  },
  {
    path: "/",
    component: () => import("@/layouts/AdminShell.vue"),
    children: businessRoutes
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
