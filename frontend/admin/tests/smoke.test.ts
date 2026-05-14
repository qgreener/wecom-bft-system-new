import { describe, expect, it } from "vitest";

import { healthEndpoint, surfaces } from "@wecom-bft/shared";

describe("admin skeleton", () => {
  it("declares the PC admin route and health contract", () => {
    expect(surfaces.admin.routeBase).toBe("/admin/");
    expect(surfaces.admin.port).toBe(5173);
    expect(healthEndpoint).toBe("/api/health");
  });
});
