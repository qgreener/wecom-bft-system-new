import { describe, expect, it } from "vitest";

import { formatSurfaceLine, healthEndpoint, surfaces } from "../src/index";

describe("shared frontend boundaries", () => {
  it("keeps health endpoint and app surfaces stable", () => {
    expect(healthEndpoint).toBe("/api/health");
    expect(Object.keys(surfaces).sort()).toEqual(["admin", "lead", "supplier", "wecomSidebar"]);
  });

  it("formats surface lines for skeleton pages", () => {
    expect(formatSurfaceLine(surfaces.admin)).toBe("PC 管理端 | /admin/ | /api/admin/**");
  });
});
