export type CreateOrderState = {
  loggedIn: boolean;
  mobileBound: boolean;
};

export type OrderAction =
  | "cancel"
  | "continuePay"
  | "viewLogistics"
  | "applyInvoice"
  | "applyRefund"
  | "viewRefund";

export type OrderActionSource = {
  payment_status: string;
  fulfillment_status: string;
  refund_status: string;
  invoice_status: string;
  payment_expire_at?: string | null;
  shipments?: Array<unknown>;
};

export function canCreateOrder(state: CreateOrderState): { allowed: true } | { allowed: false; reason: string } {
  if (!state.loggedIn) {
    return { allowed: false, reason: "请先完成微信登录" };
  }
  if (!state.mobileBound) {
    return { allowed: false, reason: "完成手机号授权后可继续购买" };
  }
  return { allowed: true };
}

export function deriveOrderActions(order: OrderActionSource, nowIso = new Date().toISOString()): OrderAction[] {
  const actions: OrderAction[] = [];
  const expiresAt = order.payment_expire_at ? new Date(order.payment_expire_at).getTime() : 0;
  const now = new Date(nowIso).getTime();
  const paymentAlive = !expiresAt || expiresAt > now;

  if (order.payment_status === "PENDING") {
    actions.push("cancel");
    if (paymentAlive) {
      actions.push("continuePay");
    }
    return actions;
  }

  if (order.payment_status !== "PAID") {
    return actions;
  }

  if (order.fulfillment_status === "SHIPPED" || order.fulfillment_status === "SIGNED" || (order.shipments?.length ?? 0) > 0) {
    actions.push("viewLogistics");
  }

  if (order.refund_status === "REVIEWING" || order.refund_status === "PROCESSING" || order.refund_status === "MANUAL_REQUIRED" || order.refund_status === "FAILED") {
    actions.push("viewRefund");
    return actions;
  }

  if (order.invoice_status === "NOT_APPLIED" && order.refund_status === "NONE") {
    actions.push("applyInvoice");
  }

  if (order.refund_status === "NONE" || order.refund_status === "REJECTED" || order.refund_status === "FAILED") {
    actions.push("applyRefund");
  }

  return actions;
}
