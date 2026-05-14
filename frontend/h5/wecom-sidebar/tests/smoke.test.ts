import { describe, expect, it } from "vitest";

import { surfaces } from "@wecom-bft/shared";

describe("wecom sidebar H5 skeleton", () => {
  it("declares the WeCom sidebar route and port", () => {
    expect(surfaces.wecomSidebar.routeBase).toBe("/h5/wecom-sidebar/");
    expect(surfaces.wecomSidebar.port).toBe(5174);
  });
});
