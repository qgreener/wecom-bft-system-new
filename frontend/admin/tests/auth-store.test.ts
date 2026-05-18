import { beforeEach, describe, expect, it } from "vitest";
import { setActivePinia, createPinia } from "pinia";

import { useAuthStore } from "../src/stores/auth";

describe("useAuthStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("merges permission_codes from currentUser into a Set", () => {
    const auth = useAuthStore();
    auth.currentUser = {
      permission_codes: ["trade:order:read", "refund:review:write", "trade:order:read"]
    };
    expect(auth.permissionSet.has("trade:order:read")).toBe(true);
    expect(auth.permissionSet.has("refund:review:write")).toBe(true);
    expect(auth.permissionSet.size).toBe(2);
  });

  it("computes hasNoRoles=true when currentUser has empty roles array", () => {
    const auth = useAuthStore();
    auth.currentUser = { roles: [] };
    expect(auth.hasNoRoles).toBe(true);
  });

  it("computes hasNoRoles=false when roles is non-empty", () => {
    const auth = useAuthStore();
    auth.currentUser = { roles: [{ role_code: "SUPER_ADMIN" }] };
    expect(auth.hasNoRoles).toBe(false);
  });

  it("hasRouteAccess always allows dashboard, gates business routes by menu/permission", () => {
    const auth = useAuthStore();
    auth.currentUser = {
      permission_codes: ["trade:order:read"],
      menus: [{ menu_code: "trade.orders" }]
    };
    expect(auth.hasRouteAccess("dashboard")).toBe(true);
    expect(auth.hasRouteAccess("orders")).toBe(true);
    expect(auth.hasRouteAccess("audit")).toBe(false);
    expect(auth.hasRouteAccess("nonexistent")).toBe(false);
  });

  it("bootstrap is a no-op when hasToken is false", async () => {
    const auth = useAuthStore();
    expect(auth.hasToken).toBe(false);
    await auth.bootstrap();
    expect(auth.currentUser).toBeNull();
  });
});
