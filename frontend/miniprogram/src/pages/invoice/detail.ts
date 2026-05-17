import { fetchInvoices } from "../../services/app-api";
import type { InvoiceResponse } from "../../types/api";
import { formatDateTime, formatYuan, statusLabel, statusTone } from "../../utils/format";
import type { MiniPageThis, PageOptions } from "../../utils/page";
import { optionNumber } from "../../utils/page";

Page({
  data: {
    loading: false,
    error: "",
    invoice: null,
    amountText: "-",
    statusText: "-",
    statusTone: "muted"
  },

  onLoad(this: MiniPageThis, options: PageOptions) {
    void this.load(optionNumber(options, "invoice_id"));
  },

  async load(this: MiniPageThis, invoiceId: number) {
    this.setData({ loading: true, error: "" });
    try {
      const page = await fetchInvoices();
      const invoice = page.records.find((item: InvoiceResponse) => item.invoice_id === invoiceId) ?? page.records[0];
      if (!invoice) {
        throw new Error("未找到发票记录");
      }
      this.setData({
        loading: false,
        invoice,
        amountText: formatYuan(invoice.invoice_amount_cent),
        statusText: statusLabel(invoice.status),
        statusTone: statusTone(invoice.status)
      });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "发票详情加载失败" });
    }
  }
});
