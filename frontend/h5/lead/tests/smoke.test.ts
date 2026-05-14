import { describe, expect, it } from "vitest";

import { surfaces } from "@wecom-bft/shared";

describe("lead H5 skeleton", () => {
  it("declares the public lead route and port", () => {
    expect(surfaces.lead.routeBase).toBe("/h5/lead/");
    expect(surfaces.lead.port).toBe(5176);
  });
});
