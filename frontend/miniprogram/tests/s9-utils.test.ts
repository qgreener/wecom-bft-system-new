import { describe, expect, it } from "vitest";

import { buildApiUrl, buildRequestHeaders, createIdempotencyKey } from "../src/services/http";
import { canCreateOrder, deriveOrderActions } from "../src/services/order-actions";
import { formatCent, formatDateTime, statusLabel } from "../src/utils/format";

describe("S9 miniprogram shared client rules", () => {
  it("builds app api urls with snake_case filters and skips empty values", () => {
    expect(buildApiUrl("/api/app/orders", {
      payment_status: "PENDING",
      refund_status: "",
      page_no: 1,
      page_size: undefined
    })).toBe("/api/app/orders?payment_status=PENDING&page_no=1");
  });

  it("adds bearer token and idempotency key for write requests", () => {
    expect(buildRequestHeaders({
      accessToken: "student-token",
      idempotent: true,
      idempotencyKey: "fixed-key"
    })).toEqual({
      Authorization: "Bearer student-token",
      "Content-Type": "application/json",
      "Idempotency-Key": "fixed-key"
    });

    expect(createIdempotencyKey("mp-order-create")).toMatch(/^mp-order-create-/);
  });

  it("formats money from cents without floating point business math", () => {
    expect(formatCent(123456)).toBe("1,234.56");
    expect(formatCent(-99)).toBe("-0.99");
    expect(formatCent(null)).toBe("-");
    expect(formatDateTime("2026-05-15T09:03:04")).toBe("2026-05-15 09:03");
  });

  it("keeps status labels driven by enum codes used by backend", () => {
    expect(statusLabel("PENDING")).toBe("待支付");
    expect(statusLabel("NO_SHIPMENT")).toBe("无需发货");
    expect(statusLabel("RED_REVERSED")).toBe("已红冲");
    expect(statusLabel("UNKNOWN_STATE")).toBe("UNKNOWN_STATE");
  });

  it("blocks order creation before phone authorization", () => {
    expect(canCreateOrder({ loggedIn: false, mobileBound: false })).toEqual({
      allowed: false,
      reason: "请先完成微信登录"
    });
    expect(canCreateOrder({ loggedIn: true, mobileBound: false })).toEqual({
      allowed: false,
      reason: "完成手机号授权后可继续购买"
    });
    expect(canCreateOrder({ loggedIn: true, mobileBound: true })).toEqual({ allowed: true });
  });

  it("derives visible order actions from returned status fields only", () => {
    expect(deriveOrderActions({
      payment_status: "PENDING",
      fulfillment_status: "NO_SHIPMENT",
      refund_status: "NONE",
      invoice_status: "NOT_APPLIED",
      payment_expire_at: "2026-05-15T12:00:00"
    }, "2026-05-15T11:00:00")).toEqual(["cancel", "continuePay"]);

    expect(deriveOrderActions({
      payment_status: "PAID",
      fulfillment_status: "SHIPPED",
      refund_status: "NONE",
      invoice_status: "NOT_APPLIED",
      shipments: [{ shipment_id: 1 }]
    }, "2026-05-15T11:00:00")).toEqual(["viewLogistics", "applyInvoice", "applyRefund"]);

    expect(deriveOrderActions({
      payment_status: "PAID",
      fulfillment_status: "NO_SHIPMENT",
      refund_status: "PROCESSING",
      invoice_status: "NOT_APPLIED"
    }, "2026-05-15T11:00:00")).toEqual(["viewRefund"]);
  });
});
