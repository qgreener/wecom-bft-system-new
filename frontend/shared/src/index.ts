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
  miniprogram: {
    name: "小程序端",
    routeBase: "/miniprogram/",
    apiPrefix: "/api/app/**",
    port: 0,
    authBoundary: "微信登录态、手机号授权和学员交易边界"
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

export const orderStatusFields = [
  "payment_status",
  "fulfillment_status",
  "refund_status",
  "invoice_status"
] as const;

export const coreEnums = {
  paymentStatus: ["PENDING", "PAID", "CLOSED"],
  paymentResult: ["SUCCESS", "FAILED", "PROCESSING", "CLOSED"],
  fulfillmentStatus: ["NO_SHIPMENT", "PENDING_SHIPMENT", "SHIPPED", "SIGNED"],
  orderRefundStatus: ["NONE", "REVIEWING", "REJECTED", "PROCESSING", "MANUAL_REQUIRED", "FAILED", "REFUNDED"],
  orderInvoiceStatus: ["NOT_APPLIED", "APPLIED", "TO_BE_ISSUED", "ISSUED", "RED_REVERSED"],
  leadStatus: ["PENDING_FOLLOW", "CONTACTED", "CONVERTED", "ABANDONED"],
  courseStatus: ["DRAFT", "PENDING_REVIEW", "ON_SHELF", "OFF_SHELF", "DELETE_PENDING", "DELETED"],
  specStatus: ["ENABLED", "DISABLED"],
  lessonNodeType: ["CHAPTER", "LESSON"],
  lessonStatus: ["DRAFT", "PUBLISHED", "HIDDEN"],
  entitlementStatus: ["ACTIVE", "FROZEN", "REVOKED"],
  shipmentStatus: ["PENDING_SHIPMENT", "SHIPPED", "SIGNED"],
  purchaseStatus: [
    "APPROVING",
    "APPROVAL_REJECTED",
    "WAIT_CONFIRM",
    "CONFIRMED",
    "SHIPPED",
    "REJECTED",
    "COMPLETED",
    "CANCELED"
  ],
  inputInvoiceStatus: ["NOT_INVOICED", "INVOICED"],
  reconciliationResult: ["MATCHED", "AMOUNT_DIFF", "FEE_DIFF", "UNMATCHED", "DUPLICATE"],
  accountingMaterialStatus: ["PENDING_SUPPLEMENT", "UPLOADED", "CONFIRMED", "CLOSED"],
  notificationChannel: ["IN_APP", "WECHAT_SUBSCRIBE", "WECOM_CARD"],
  sendStatus: ["PENDING", "SENT", "FAILED", "CANCELED"],
  readStatus: ["UNREAD", "READ"],
  approvalStatus: ["PENDING", "APPROVED", "REJECTED", "CANCELED"],
  operationResult: ["SUCCESS", "FAILED"]
} as const;

export type OrderStatusField = (typeof orderStatusFields)[number];
export type CoreEnumName = keyof typeof coreEnums;
export type CoreEnumCode<T extends CoreEnumName> = (typeof coreEnums)[T][number];

export type PaymentStatus = CoreEnumCode<"paymentStatus">;
export type FulfillmentStatus = CoreEnumCode<"fulfillmentStatus">;
export type OrderRefundStatus = CoreEnumCode<"orderRefundStatus">;
export type OrderInvoiceStatus = CoreEnumCode<"orderInvoiceStatus">;

export function isCoreEnumCode(enumName: CoreEnumName, code: string): boolean {
  return (coreEnums[enumName] as readonly string[]).includes(code);
}

export function formatSurfaceLine(surface: AppSurface): string {
  return `${surface.name} | ${surface.routeBase} | ${surface.apiPrefix}`;
}
