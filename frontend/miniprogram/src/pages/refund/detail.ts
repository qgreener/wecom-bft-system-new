import { fetchRefundDetail } from "../../services/app-api";
import { formatDateTime, formatYuan, statusLabel, statusTone } from "../../utils/format";
import type { MiniPageThis, PageOptions } from "../../utils/page";
import { optionNumber } from "../../utils/page";

Page({
  data: {
    loading: false,
    error: "",
    refund: null,
    amountText: "-",
    approvedText: "-",
    statusText: "-",
    statusTone: "muted",
    refundedText: "-",
    steps: [] as Array<{ title: string; active: boolean }>
  },

  onLoad(this: MiniPageThis, options: PageOptions) {
    void this.load(optionNumber(options, "refund_id"));
  },

  async load(this: MiniPageThis, refundId: number) {
    this.setData({ loading: true, error: "" });
    try {
      const refund = await fetchRefundDetail(refundId);
      const order = ["REVIEWING", "REJECTED", "PROCESSING", "MANUAL_REQUIRED", "FAILED", "REFUNDED"];
      const index = Math.max(0, order.indexOf(refund.status));
      this.setData({
        loading: false,
        refund,
        amountText: formatYuan(refund.apply_amount_cent),
        approvedText: formatYuan(refund.approved_amount_cent ?? refund.apply_amount_cent),
        statusText: statusLabel(refund.status),
        statusTone: statusTone(refund.status),
        refundedText: formatDateTime(refund.refunded_at),
        steps: order.map((code, currentIndex) => ({ title: statusLabel(code), active: currentIndex <= index }))
      });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "退款详情加载失败" });
    }
  },

  backOrder(this: MiniPageThis) {
    const refund = this.data?.refund as { order_id: number } | null;
    if (refund) {
      wx.redirectTo({ url: `/pages/order/detail?order_id=${refund.order_id}` });
    }
  }
});
