import { describe, expect, it } from "vitest";

import { surfaces } from "@wecom-bft/shared";

describe("supplier H5 skeleton", () => {
  it("declares the supplier route and port", () => {
    expect(surfaces.supplier.routeBase).toBe("/h5/supplier/");
    expect(surfaces.supplier.port).toBe(5175);
  });
});
