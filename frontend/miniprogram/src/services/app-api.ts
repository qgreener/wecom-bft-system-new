import { getAccessToken, saveLoginSession, updateSessionFromPhone } from "../stores/session";
import type {
  AppAddress,
  AppCourse,
  CourseDetail,
  Entitlement,
  InvoiceResponse,
  InvoiceTitle,
  NotificationItem,
  OrderConfirmResponse,
  OrderCreateResponse,
  OrderDetail,
  OrderListItem,
  PageResult,
  PaymentCallbackResponse,
  PaymentPrepareResponse,
  PhoneAuthorizeResponse,
  RefundResponse,
  StudentMe,
  StudentSession
} from "../types/api";
import { request } from "./http";
import { getPhoneAuthorizeCode, getWechatLoginCode } from "./wechat-mock";

export async function loginWithWechatMock(): Promise<StudentSession> {
  const wxCode = await getWechatLoginCode();
  const response = await request<StudentSession>("/api/app/auth/wechat-login", {
    method: "POST",
    data: {
      wx_code: wxCode,
      source_channel: "MINIPROGRAM"
    }
  });
  saveLoginSession(response);
  return response;
}

export async function authorizePhoneMock(): Promise<PhoneAuthorizeResponse> {
  const response = await request<PhoneAuthorizeResponse>("/api/app/auth/phone-authorize", {
    method: "POST",
    accessToken: getAccessToken(),
    data: {
      phone_code: getPhoneAuthorizeCode()
    }
  });
  updateSessionFromPhone(response);
  return response;
}

export async function authorizePhoneWithCode(phoneCode: string): Promise<PhoneAuthorizeResponse> {
  const response = await request<PhoneAuthorizeResponse>("/api/app/auth/phone-authorize", {
    method: "POST",
    accessToken: getAccessToken(),
    data: {
      phone_code: phoneCode
    }
  });
  updateSessionFromPhone(response);
  return response;
}

export function fetchMe(): Promise<StudentMe> {
  return request<StudentMe>("/api/app/students/me", { accessToken: getAccessToken() });
}

export function tradePrecheck(): Promise<{ student_id: number; student_no: string; mobile_bound: boolean }> {
  return request("/api/app/trade/precheck", {
    method: "POST",
    accessToken: getAccessToken()
  });
}

export function fetchCourses(query: {
  keyword?: string;
  course_type?: string;
  page_no?: number;
  page_size?: number;
}): Promise<PageResult<AppCourse>> {
  return request<PageResult<AppCourse>>("/api/app/courses", { query });
}

export function fetchCourseDetail(courseId: number): Promise<CourseDetail> {
  return request<CourseDetail>(`/api/app/courses/${courseId}`);
}

export function confirmOrder(payload: {
  course_id: number;
  spec_id: number;
  quantity: number;
  address_id?: number | null;
  source_code?: string;
}): Promise<OrderConfirmResponse> {
  return request<OrderConfirmResponse>("/api/app/orders/confirm", {
    method: "POST",
    accessToken: getAccessToken(),
    data: {
      ...payload,
      source_channel: "MINIPROGRAM"
    }
  });
}

export function createOrder(payload: {
  course_id: number;
  spec_id: number;
  quantity: number;
  address_id?: number | null;
  client_request_no: string;
  source_code?: string;
  confirmed_payable_amount_cent: number;
  confirm_token: string;
}): Promise<OrderCreateResponse> {
  return request<OrderCreateResponse>("/api/app/orders", {
    method: "POST",
    accessToken: getAccessToken(),
    idempotent: true,
    idempotencyScope: "mp-order-create",
    data: {
      ...payload,
      source_channel: "MINIPROGRAM"
    }
  });
}

export function preparePayment(orderId: number): Promise<PaymentPrepareResponse> {
  return request<PaymentPrepareResponse>(`/api/app/orders/${orderId}/pay`, {
    method: "POST",
    accessToken: getAccessToken(),
    data: {
      payment_channel: "MOCK"
    }
  });
}

export function mockPayOrder(orderId: number, amountCent: number): Promise<PaymentCallbackResponse> {
  const suffix = `${orderId}_${Date.now().toString(16)}`;
  return request<PaymentCallbackResponse>(`/api/app/orders/${orderId}/mock-pay`, {
    method: "POST",
    accessToken: getAccessToken(),
    data: {
      event_no: `S9_MP_PAY_${suffix}`,
      external_payment_no: `S9PAY-${suffix}`,
      paid_amount_cent: amountCent,
      payment_result: "SUCCESS",
      paid_at: new Date().toISOString().slice(0, 19),
      raw_snapshot: {
        scenario: "s9_miniprogram_mock_pay"
      }
    }
  });
}

export async function payWithBackendMock(orderId: number, amountCent: number): Promise<OrderDetail> {
  await preparePayment(orderId);
  await mockPayOrder(orderId, amountCent);
  return fetchOrderDetail(orderId);
}

export function cancelOrder(orderId: number): Promise<{ order_id: number; payment_status: string }> {
  return request(`/api/app/orders/${orderId}/cancel`, {
    method: "POST",
    accessToken: getAccessToken(),
    data: {
      close_reason: "USER_CANCEL"
    }
  });
}

export function fetchOrders(query: {
  payment_status?: string;
  fulfillment_status?: string;
  refund_status?: string;
  invoice_status?: string;
  page_no?: number;
  page_size?: number;
}): Promise<PageResult<OrderListItem>> {
  return request<PageResult<OrderListItem>>("/api/app/orders", {
    accessToken: getAccessToken(),
    query
  });
}

export function fetchOrderDetail(orderId: number): Promise<OrderDetail> {
  return request<OrderDetail>(`/api/app/orders/${orderId}`, {
    accessToken: getAccessToken()
  });
}

export function fetchEntitlements(query: { status?: string; page_no?: number; page_size?: number }): Promise<PageResult<Entitlement>> {
  return request<PageResult<Entitlement>>("/api/app/learning/entitlements", {
    accessToken: getAccessToken(),
    query
  });
}

export function fetchLessons(entitlementId: number): Promise<{ entitlement_id: number; course_id: number; course_group_qr?: string; nodes: unknown[] }> {
  return request(`/api/app/learning/entitlements/${entitlementId}/lessons`, {
    accessToken: getAccessToken()
  });
}

export function applyRefund(orderId: number, amountCent: number, reason: string, description: string): Promise<RefundResponse> {
  return request<RefundResponse>(`/api/app/orders/${orderId}/refunds`, {
    method: "POST",
    accessToken: getAccessToken(),
    idempotent: true,
    idempotencyScope: "mp-refund-apply",
    data: {
      apply_amount_cent: amountCent,
      refund_reason: reason,
      apply_description: description,
      entitlement_action: "FREEZE"
    }
  });
}

export function fetchRefunds(): Promise<{ records: RefundResponse[] }> {
  return request("/api/app/refunds", {
    accessToken: getAccessToken()
  });
}

export function fetchRefundDetail(refundId: number): Promise<RefundResponse> {
  return request<RefundResponse>(`/api/app/refunds/${refundId}`, {
    accessToken: getAccessToken()
  });
}

export function fetchInvoiceTitles(): Promise<{ records: InvoiceTitle[] }> {
  return request("/api/app/invoice-titles", {
    accessToken: getAccessToken()
  });
}

export function saveInvoiceTitle(payload: {
  title_type: string;
  title_name: string;
  tax_no?: string;
  email?: string;
  is_default: boolean;
}): Promise<InvoiceTitle> {
  return request<InvoiceTitle>("/api/app/invoice-titles", {
    method: "POST",
    accessToken: getAccessToken(),
    idempotent: true,
    idempotencyScope: "mp-invoice-title",
    data: payload
  });
}

export function applyInvoice(orderId: number, titleId: number, email: string): Promise<InvoiceResponse> {
  return request<InvoiceResponse>(`/api/app/orders/${orderId}/invoices`, {
    method: "POST",
    accessToken: getAccessToken(),
    idempotent: true,
    idempotencyScope: "mp-invoice-apply",
    data: {
      title_id: titleId,
      email
    }
  });
}

export function fetchInvoices(): Promise<{ records: InvoiceResponse[] }> {
  return request("/api/app/invoices", {
    accessToken: getAccessToken()
  });
}

export function fetchOrderLogistics(orderId: number): Promise<import("../types/api").ShipmentDetail[]> {
  return request<import("../types/api").ShipmentDetail[]>(`/api/app/orders/${orderId}/logistics`, {
    accessToken: getAccessToken()
  });
}

// 通知信息
export function fetchNotifications(): Promise<NotificationItem[]> {
  return request<NotificationItem[]>("/api/app/notifications", {
    accessToken: getAccessToken()
  });
}

export function markNotificationRead(notificationId: number): Promise<NotificationItem> {
  return request<NotificationItem>(`/api/app/notifications/${notificationId}/read`, {
    method: "POST",
    accessToken: getAccessToken(),
    idempotent: true,
    idempotencyScope: "mp-notification-read"
  });
}

// 收货地址
export function fetchAddresses(): Promise<AppAddress[]> {
  return request<AppAddress[]>("/api/app/addresses", {
    accessToken: getAccessToken()
  });
}

export function createAddress(payload: Partial<AppAddress>): Promise<AppAddress> {
  return request<AppAddress>("/api/app/addresses", {
    method: "POST",
    accessToken: getAccessToken(),
    idempotent: true,
    idempotencyScope: "mp-address-create",
    data: payload
  });
}

export function updateAddress(addressId: number, payload: Partial<AppAddress>): Promise<AppAddress> {
  return request<AppAddress>(`/api/app/addresses/${addressId}`, {
    method: "PUT",
    accessToken: getAccessToken(),
    idempotent: true,
    idempotencyScope: "mp-address-update",
    data: payload
  });
}

export function deleteAddress(addressId: number): Promise<void> {
  return request<void>(`/api/app/addresses/${addressId}`, {
    method: "DELETE",
    accessToken: getAccessToken()
  });
}
