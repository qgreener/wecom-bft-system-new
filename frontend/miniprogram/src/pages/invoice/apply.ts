import { applyInvoice, fetchInvoiceTitles, fetchOrderDetail } from "../../services/app-api";
import { formatYuan } from "../../utils/format";
import type { InvoiceTitle } from "../../types/api";
import type { MiniPageThis, PageOptions } from "../../utils/page";
import { optionNumber, toast } from "../../utils/page";

Page({
  data: {
    orderId: 0,
    orderNo: "-",
    amountText: "-",
    loading: false,
    submitting: false,
    error: "",
    titles: [] as InvoiceTitle[],
    selectedTitleId: 0,
    email: ""
  },

  onLoad(this: MiniPageThis, options: PageOptions) {
    const orderId = optionNumber(options, "order_id");
    this.setData({ orderId });
    void this.load(orderId);
  },

  async load(this: MiniPageThis, orderId: number) {
    this.setData({ loading: true, error: "" });
    try {
      const [order, titles] = await Promise.all([
        fetchOrderDetail(orderId),
        fetchInvoiceTitles()
      ]);
      const selectedTitle = titles.records.find((item) => item.is_default) ?? titles.records[0];
      this.setData({
        loading: false,
        orderNo: order.order_no,
        amountText: formatYuan(order.paid_amount_cent ?? order.payable_amount_cent),
        titles: titles.records,
        selectedTitleId: selectedTitle?.title_id ?? 0,
        email: selectedTitle?.email ?? ""
      });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "开票页面加载失败" });
    }
  },

  selectTitle(this: MiniPageThis, event: { currentTarget: { dataset: { id: number; email?: string } } }) {
    this.setData({
      selectedTitleId: event.currentTarget.dataset.id,
      email: event.currentTarget.dataset.email ?? this.data?.email ?? ""
    });
  },

  onEmailInput(this: MiniPageThis, event: { detail: { value: string } }) {
    this.setData({ email: event.detail.value });
  },

  async submit(this: MiniPageThis) {
    if (!this.data?.selectedTitleId) {
      toast("请选择发票抬头");
      return;
    }
    if (!String(this.data?.email ?? "").includes("@")) {
      toast("请输入接收邮箱");
      return;
    }
    this.setData({ submitting: true, error: "" });
    try {
      const invoice = await applyInvoice(
        Number(this.data?.orderId),
        Number(this.data?.selectedTitleId),
        String(this.data?.email ?? "")
      );
      this.setData({ submitting: false });
      wx.redirectTo({ url: `/pages/invoice/detail?invoice_id=${invoice.invoice_id}` });
    } catch (error) {
      this.setData({ submitting: false, error: error instanceof Error ? error.message : "开票申请失败" });
    }
  }
});
