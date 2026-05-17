import { fetchOrders, payWithBackendMock } from "../../services/app-api";
import type { OrderListItem } from "../../types/api";
import { countdownText, formatDateTime, formatYuan, statusLabel, statusTone } from "../../utils/format";
import { courseTitle } from "../../utils/snapshot";
import type { MiniPageThis } from "../../utils/page";
import { go } from "../../utils/page";

type OrderView = OrderListItem & {
  course_title: string;
  amount_text: string;
  created_text: string;
  countdown_text: string;
  status_tags: Array<{ label: string; tone: string }>;
};

function toView(item: OrderListItem): OrderView {
  return {
    ...item,
    course_title: courseTitle(item.course_snapshot),
    amount_text: formatYuan(item.paid_amount_cent ?? item.payable_amount_cent),
    created_text: formatDateTime(item.created_at),
    countdown_text: item.payment_status === "PENDING" ? countdownText(item.payment_expire_at, item.server_time) : "",
    status_tags: [item.payment_status, item.fulfillment_status, item.refund_status, item.invoice_status]
      .map((code) => ({ label: statusLabel(code), tone: statusTone(code) }))
  };
}

Page({
  data: {
    loading: false,
    paying: false,
    error: "",
    paymentStatus: "",
    records: [] as OrderView[]
  },

  onShow(this: MiniPageThis) {
    void this.load();
  },

  onPullDownRefresh(this: MiniPageThis) {
    void this.load().finally(() => wx.stopPullDownRefresh());
  },

  setPayment(this: MiniPageThis, event: { currentTarget: { dataset: { status?: string } } }) {
    this.setData({ paymentStatus: event.currentTarget.dataset.status ?? "" });
    void this.load();
  },

  async load(this: MiniPageThis) {
    this.setData({ loading: true, error: "" });
    try {
      const page = await fetchOrders({
        payment_status: String(this.data?.paymentStatus ?? ""),
        page_no: 1,
        page_size: 20
      });
      this.setData({ loading: false, records: page.records.map(toView) });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "订单加载失败" });
    }
  },

  open(event: { currentTarget: { dataset: { id: number } } }) {
    go(`/pages/order/detail?order_id=${event.currentTarget.dataset.id}`);
  },

  async continuePay(this: MiniPageThis, event: { currentTarget: { dataset: { id: number; amount: number } } }) {
    this.setData({ paying: true, error: "" });
    try {
      const latest = await payWithBackendMock(Number(event.currentTarget.dataset.id), Number(event.currentTarget.dataset.amount));
      this.setData({ paying: false });
      if (latest.payment_status === "PAID") {
        wx.navigateTo({ url: `/pages/payment/success?order_id=${latest.order_id}` });
      } else {
        wx.navigateTo({ url: `/pages/order/detail?order_id=${latest.order_id}` });
      }
    } catch (error) {
      this.setData({ paying: false, error: error instanceof Error ? error.message : "继续支付失败" });
    }
  }
});
