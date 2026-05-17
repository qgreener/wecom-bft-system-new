import { fetchOrderDetail } from "../../services/app-api";
import type { Entitlement, OrderDetail } from "../../types/api";
import { formatYuan, statusLabel, statusTone } from "../../utils/format";
import type { MiniPageThis, PageOptions } from "../../utils/page";
import { optionNumber } from "../../utils/page";

Page({
  data: {
    loading: false,
    error: "",
    order: null,
    amountText: "-",
    paymentText: "-",
    paymentTone: "muted",
    entitlementId: 0,
    hasPhysical: false
  },

  onLoad(this: MiniPageThis, options: PageOptions) {
    void this.load(optionNumber(options, "order_id"));
  },

  async load(this: MiniPageThis, orderId: number) {
    this.setData({ loading: true, error: "" });
    try {
      const order = await fetchOrderDetail(orderId);
      const entitlement = (order.entitlements ?? []).find((item: Entitlement) => item.status === "ACTIVE");
      this.setData({
        loading: false,
        order,
        amountText: formatYuan(order.paid_amount_cent ?? order.payable_amount_cent),
        paymentText: statusLabel(order.payment_status),
        paymentTone: statusTone(order.payment_status),
        entitlementId: entitlement?.entitlement_id ?? 0,
        hasPhysical: order.fulfillment_status !== "NO_SHIPMENT"
      });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "支付结果加载失败" });
    }
  },

  learn(this: MiniPageThis) {
    const entitlementId = Number(this.data?.entitlementId ?? 0);
    if (entitlementId) {
      wx.redirectTo({ url: `/pages/learning/detail?entitlement_id=${entitlementId}` });
      return;
    }
    wx.switchTab({ url: "/pages/learning/index" });
  },

  order(this: MiniPageThis) {
    const order = this.data?.order as OrderDetail | null;
    if (order) {
      wx.redirectTo({ url: `/pages/order/detail?order_id=${order.order_id}` });
    }
  }
});
