import type { RouteKey } from "@/router/routes";
import { ACTION_BINDINGS, type RouteAction } from "@/config/actions";

type AnyRecord = Record<string, unknown>;

function recordStatus(routeKey: RouteKey, record: AnyRecord): string {
  if (routeKey === "orders") return String(record.fulfillment_status ?? record.payment_status ?? "");
  return String(record.status ?? record.refund_status ?? record.fulfillment_status ?? record.invoice_status ?? "");
}

export function isActionRelevant(routeKey: RouteKey, actionType: string, record: AnyRecord): boolean {
  const status = recordStatus(routeKey, record);
  if (routeKey === "refunds") {
    if (["refundApprove", "refundReject"].includes(actionType)) return status === "REVIEWING";
    if (actionType === "refundManual") return status === "MANUAL_REQUIRED";
    if (actionType === "refundRetry") return status === "FAILED";
  }
  if (routeKey === "shipments") {
    if (actionType === "ship") return ["PENDING", "PENDING_SHIPMENT", "WAIT_SHIP"].includes(status);
    if (actionType === "sign") return status === "SHIPPED";
  }
  if (routeKey === "invoices") {
    if (actionType === "invoiceIssue") return ["APPLIED", "TO_BE_ISSUED"].includes(status);
    if (actionType === "redReverse") return status === "ISSUED";
  }
  if (routeKey === "accounting") {
    if (actionType === "materialUpload") return ["PENDING_SUPPLEMENT", "UPLOADED"].includes(status);
    if (actionType === "materialConfirm") return status === "UPLOADED";
    if (actionType === "materialClose") return ["PENDING_SUPPLEMENT", "UPLOADED"].includes(status);
  }
  if (routeKey === "leads") {
    if (actionType === "leadFollow") return status !== "CONVERTED";
    if (actionType === "leadConvert") return !["CONVERTED", "ABANDONED"].includes(status);
    if (actionType === "leadAbandon") return status !== "ABANDONED";
  }
  return ![
    "refundApprove",
    "refundReject",
    "refundManual",
    "refundRetry",
    "ship",
    "sign",
    "invoiceIssue",
    "redReverse"
  ].includes(actionType);
}

export function actionsForRecord(routeKey: RouteKey, record: AnyRecord | null, actions: RouteAction[]): RouteAction[] {
  if (!record) return [];
  const allowed = Array.isArray(record.allowed_actions) ? record.allowed_actions.map(String) : null;
  if (allowed) {
    return actions.filter((action) => {
      const binding = ACTION_BINDINGS[action.type];
      return allowed.includes(action.type) || (binding?.action ? allowed.includes(binding.action) : false);
    });
  }
  return actions.filter((action) => isActionRelevant(routeKey, action.type, record));
}
