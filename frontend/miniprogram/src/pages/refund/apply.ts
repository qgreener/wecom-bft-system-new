import { applyRefund, fetchOrderDetail } from "../../services/app-api";
import { formatYuan } from "../../utils/format";
import type { MiniPageThis, PageOptions } from "../../utils/page";
import { optionNumber, toast } from "../../utils/page";

Page({
  data: {
    orderId: 0,
    amountCent: 0,
    amountText: "-",
    reason: "",
    description: "",
    loading: false,
    error: "",
    reasons: ["不想学了", "课程与描述不符", "重复购买", "其他"]
  },

  onLoad(this: MiniPageThis, options: PageOptions) {
    const orderId = optionNumber(options, "order_id");
    const amountCent = optionNumber(options, "amount_cent");
    this.setData({ orderId, amountCent, amountText: formatYuan(amountCent) });
    if (!amountCent) {
      void this.loadOrder(orderId);
    }
  },

  async loadOrder(this: MiniPageThis, orderId: number) {
    const order = await fetchOrderDetail(orderId);
    const amountCent = order.paid_amount_cent ?? order.payable_amount_cent;
    this.setData({ amountCent, amountText: formatYuan(amountCent) });
  },

  setReason(this: MiniPageThis, event: { currentTarget: { dataset: { reason: string } } }) {
    this.setData({ reason: event.currentTarget.dataset.reason });
  },

  onDescriptionInput(this: MiniPageThis, event: { detail: { value: string } }) {
    this.setData({ description: event.detail.value.slice(0, 200) });
  },

  async submit(this: MiniPageThis) {
    const reason = String(this.data?.reason ?? "");
    if (!reason) {
      toast("请选择退款原因");
      return;
    }
    this.setData({ loading: true, error: "" });
    try {
      const refund = await applyRefund(
        Number(this.data?.orderId),
        Number(this.data?.amountCent),
        reason,
        String(this.data?.description ?? "")
      );
      this.setData({ loading: false });
      wx.redirectTo({ url: `/pages/refund/detail?refund_id=${refund.refund_id}` });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "退款申请失败" });
    }
  }
});
