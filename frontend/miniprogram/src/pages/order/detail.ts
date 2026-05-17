import { cancelOrder, fetchInvoices, fetchOrderDetail, fetchRefunds, payWithBackendMock } from "../../services/app-api";
import { deriveOrderActions, type OrderAction } from "../../services/order-actions";
import type { InvoiceResponse, OrderDetail, RefundResponse } from "../../types/api";
import { countdownText, formatDateTime, formatYuan, statusLabel, statusTone } from "../../utils/format";
import type { MiniPageThis, PageOptions } from "../../utils/page";
import { go, optionNumber, toast } from "../../utils/page";
import { courseTitle, receiverLine, specName } from "../../utils/snapshot";

type ActionView = { code: OrderAction; label: string; style: string };
const actionLabels: Record<OrderAction, string> = {
  cancel: "取消订单",
  continuePay: "继续支付",
  viewLogistics: "查看物流",
  applyInvoice: "申请开票",
  applyRefund: "申请退款",
  viewRefund: "查看退款进度"
};

function toActionViews(actions: OrderAction[]): ActionView[] {
  return actions.map((code) => ({
    code,
    label: actionLabels[code],
    style: code === "cancel" ? "danger" : code === "continuePay" ? "" : "secondary"
  }));
}

function orderView(order: OrderDetail) {
  return {
    ...order,
    course_title: courseTitle(order.course_snapshot),
    spec_name: specName(order.items?.[0]?.spec_snapshot),
    paid_text: formatYuan(order.paid_amount_cent ?? order.payable_amount_cent),
    total_text: formatYuan(order.total_amount_cent),
    created_text: formatDateTime(order.created_at),
    expire_text: formatDateTime(order.payment_expire_at),
    paid_at_text: formatDateTime(order.paid_at),
    countdown_text: countdownText(order.payment_expire_at, order.server_time),
    receiver_line: receiverLine(order.receiver_snapshot),
    status_tags: [order.payment_status, order.fulfillment_status, order.refund_status, order.invoice_status]
      .map((code) => ({ label: statusLabel(code), tone: statusTone(code) }))
  };
}

Page({
  data: {
    loading: false,
    acting: false,
    error: "",
    orderId: 0,
    order: null,
    actions: [] as ActionView[]
  },

  onLoad(this: MiniPageThis, options: PageOptions) {
    const orderId = optionNumber(options, "order_id");
    this.setData({ orderId });
    void this.load(orderId);
  },

  async load(this: MiniPageThis, orderId = Number(this.data?.orderId ?? 0)) {
    this.setData({ loading: true, error: "" });
    try {
      const order = await fetchOrderDetail(orderId);
      this.setData({
        loading: false,
        order: orderView(order),
        actions: toActionViews(deriveOrderActions(order, order.server_time ?? new Date().toISOString()))
      });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "订单详情加载失败" });
    }
  },

  async runAction(this: MiniPageThis, event: { currentTarget: { dataset: { action: OrderAction } } }) {
    const action = event.currentTarget.dataset.action;
    const order = this.data?.order as OrderDetail | null;
    if (!order) {
      return;
    }
    if (action === "continuePay") {
      await this.continuePay(order);
    } else if (action === "cancel") {
      await this.cancel(order);
    } else if (action === "applyRefund") {
      go(`/pages/refund/apply?order_id=${order.order_id}&amount_cent=${order.paid_amount_cent ?? order.payable_amount_cent}`);
    } else if (action === "applyInvoice") {
      go(`/pages/invoice/apply?order_id=${order.order_id}`);
    } else if (action === "viewLogistics") {
      go(`/pages/logistics/detail?order_id=${order.order_id}`);
    } else if (action === "viewRefund") {
      await this.viewRefund(order.order_id);
    }
  },

  async continuePay(this: MiniPageThis, order: OrderDetail) {
    this.setData({ acting: true, error: "" });
    try {
      const latest = await payWithBackendMock(order.order_id, order.payable_amount_cent);
      this.setData({ acting: false });
      if (latest.payment_status === "PAID") {
        wx.redirectTo({ url: `/pages/payment/success?order_id=${latest.order_id}` });
      } else {
        await this.load(order.order_id);
      }
    } catch (error) {
      this.setData({ acting: false, error: error instanceof Error ? error.message : "继续支付失败" });
    }
  },

  async cancel(this: MiniPageThis, order: OrderDetail) {
    wx.showModal({
      title: "取消订单",
      content: "确认关闭当前待支付订单？",
      confirmText: "确认",
      success: async (res) => {
        if (!res.confirm) {
          return;
        }
        this.setData({ acting: true });
        try {
          await cancelOrder(order.order_id);
          await this.load(order.order_id);
          this.setData({ acting: false });
        } catch (error) {
          this.setData({ acting: false, error: error instanceof Error ? error.message : "取消失败" });
        }
      }
    });
  },

  async viewRefund(orderId: number) {
    const page = await fetchRefunds();
    const matches = page.records.filter((item: RefundResponse) => item.order_id === orderId);
    const latest = matches[matches.length - 1];
    if (!latest) {
      toast("暂无退款单");
      return;
    }
    go(`/pages/refund/detail?refund_id=${latest.refund_id}`);
  },

  async invoiceDetail(this: MiniPageThis) {
    const order = this.data?.order as OrderDetail | null;
    if (!order) {
      return;
    }
    const page = await fetchInvoices();
    const matches = page.records.filter((item: InvoiceResponse) => item.order_id === order.order_id);
    const invoice = matches[matches.length - 1];
    if (!invoice) {
      toast("暂无发票单");
      return;
    }
    go(`/pages/invoice/detail?invoice_id=${invoice.invoice_id}`);
  }
});
