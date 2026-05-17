import { describe, expect, it } from "vitest";

import { buildApiUrl, createIdempotencyKey } from "../src/services/http";
import { routeRegistry } from "../src/router/routes";
import { formatCent, normalizeRecords } from "../src/utils/format";

describe("S8 admin frontend contracts", () => {
  it("builds admin api urls with snake_case query names and skips empty filters", () => {
    expect(buildApiUrl("/api/admin/orders", {
      page_no: 1,
      page_size: 20,
      keyword: "ORDER",
      refund_status: "",
      invoice_status: undefined
    })).toBe("/api/admin/orders?page_no=1&page_size=20&keyword=ORDER");
  });

  it("formats cent amounts without floating point business math", () => {
    expect(formatCent(123456)).toBe("1,234.56");
    expect(formatCent(-99)).toBe("-0.99");
    expect(formatCent(null)).toBe("-");
  });

  it("creates scoped idempotency keys for write requests", () => {
    expect(createIdempotencyKey("refund-review")).toMatch(/^refund-review-[a-f0-9-]{8,}$/);
  });

  it("normalizes backend page and plain record collections", () => {
    expect(normalizeRecords({ records: [{ id: 1 }] })).toEqual([{ id: 1 }]);
    expect(normalizeRecords([{ id: 2 }])).toEqual([{ id: 2 }]);
    expect(normalizeRecords(null)).toEqual([]);
  });

  it("registers core PC admin routes from the S8 scope", () => {
    expect(routeRegistry.map((route) => route.key)).toEqual(expect.arrayContaining([
      "dashboard",
      "orders",
      "refunds",
      "shipments",
      "invoices",
      "reconciliation",
      "accounting",
      "courses",
      "leads",
      "students",
      "inventory",
      "purchases",
      "settings",
      "audit"
    ]));
  });
});
