import { describe, expect, it } from "vitest";

import appConfig from "../src/app.json";

describe("S9 miniprogram page surface", () => {
  it("registers the student core route set and three tab entries", () => {
    expect(appConfig.pages).toEqual(expect.arrayContaining([
      "pages/courses/list",
      "pages/courses/detail",
      "pages/order/confirm",
      "pages/order/list",
      "pages/order/detail",
      "pages/payment/success",
      "pages/learning/index",
      "pages/learning/detail",
      "pages/mine/index",
      "pages/auth/login",
      "pages/refund/apply",
      "pages/refund/detail",
      "pages/invoice/titles",
      "pages/invoice/apply",
      "pages/invoice/detail",
      "pages/address/list",
      "pages/address/edit",
      "pages/logistics/detail",
      "pages/notifications/list",
      "pages/notifications/detail"
    ]));

    expect(appConfig.tabBar.list.map((item) => item.pagePath)).toEqual([
      "pages/courses/list",
      "pages/learning/index",
      "pages/mine/index"
    ]);
  });
});
