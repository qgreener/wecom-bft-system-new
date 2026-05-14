export type AppSurface = {
  name: string;
  routeBase: string;
  apiPrefix: string;
  port: number;
  authBoundary: string;
};

export const surfaces: Record<string, AppSurface> = {
  admin: {
    name: "PC 管理端",
    routeBase: "/admin/",
    apiPrefix: "/api/admin/**",
    port: 5173,
    authBoundary: "内部登录态、角色、按钮、数据范围和字段脱敏"
  },
  wecomSidebar: {
    name: "企微侧边栏 H5",
    routeBase: "/h5/wecom-sidebar/",
    apiPrefix: "/api/wecom/sidebar/**",
    port: 5174,
    authBoundary: "企微 JS-SDK 上下文签名、内部登录态和运营数据范围"
  },
  supplier: {
    name: "供货商 H5",
    routeBase: "/h5/supplier/",
    apiPrefix: "/api/supplier-h5/**",
    port: 5175,
    authBoundary: "供货商访问令牌，只能访问自身 supplier_id 数据"
  },
  lead: {
    name: "公开留资 H5",
    routeBase: "/h5/lead/",
    apiPrefix: "/api/app/**",
    port: 5176,
    authBoundary: "匿名入口，后端负责频率限制、手机号校验和来源码记录"
  }
};

export const healthEndpoint = "/api/health";

export function formatSurfaceLine(surface: AppSurface): string {
  return `${surface.name} | ${surface.routeBase} | ${surface.apiPrefix}`;
}
