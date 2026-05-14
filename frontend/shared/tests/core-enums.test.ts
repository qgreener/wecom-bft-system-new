import { describe, expect, it } from "vitest";

import { coreEnums, isCoreEnumCode, orderStatusFields } from "../src/index";

describe("core enum contracts", () => {
  it("exposes split order state fields without a single combined field", () => {
    const forbiddenSingleStatus = "order" + "_status";

    expect(orderStatusFields).toEqual([
      "payment_status",
      "fulfillment_status",
      "refund_status",
      "invoice_status"
    ]);
    expect(orderStatusFields).not.toContain(forbiddenSingleStatus);
  });

  it("keeps frontend status codes aligned with backend English enum codes", () => {
    expect(coreEnums.paymentStatus).toEqual(["PENDING", "PAID", "CLOSED"]);
    expect(coreEnums.fulfillmentStatus).toEqual(["NO_SHIPMENT", "PENDING_SHIPMENT", "SHIPPED", "SIGNED"]);
    expect(coreEnums.orderRefundStatus).toEqual([
      "NONE",
      "REVIEWING",
      "REJECTED",
      "PROCESSING",
      "MANUAL_REQUIRED",
      "FAILED",
      "REFUNDED"
    ]);
    expect(coreEnums.orderInvoiceStatus).toEqual([
      "NOT_APPLIED",
      "APPLIED",
      "TO_BE_ISSUED",
      "ISSUED",
      "RED_REVERSED"
    ]);
  });

  it("recognizes enum codes by enum name", () => {
    expect(isCoreEnumCode("paymentStatus", "PAID")).toBe(true);
    expect(isCoreEnumCode("paymentStatus", "已支付")).toBe(false);
    expect(Object.values(coreEnums).flat().every((code) => /^[A-Z][A-Z0-9_]*$/.test(code))).toBe(true);
  });
});
