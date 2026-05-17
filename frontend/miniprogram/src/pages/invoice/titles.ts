import { fetchInvoiceTitles, saveInvoiceTitle } from "../../services/app-api";
import type { InvoiceTitle } from "../../types/api";
import type { MiniPageThis } from "../../utils/page";
import { toast } from "../../utils/page";

Page({
  data: {
    loading: false,
    error: "",
    records: [] as InvoiceTitle[],
    titleType: "PERSONAL",
    titleName: "个人",
    taxNo: "",
    email: ""
  },

  onShow(this: MiniPageThis) {
    void this.load();
  },

  async load(this: MiniPageThis) {
    this.setData({ loading: true, error: "" });
    try {
      const page = await fetchInvoiceTitles();
      this.setData({ loading: false, records: page.records });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "发票抬头加载失败" });
    }
  },

  setType(this: MiniPageThis, event: { currentTarget: { dataset: { type: string } } }) {
    const titleType = event.currentTarget.dataset.type;
    this.setData({ titleType, titleName: titleType === "PERSONAL" ? "个人" : "" });
  },

  input(this: MiniPageThis, event: { currentTarget: { dataset: { field: string } }; detail: { value: string } }) {
    this.setData({ [event.currentTarget.dataset.field]: event.detail.value });
  },

  async save(this: MiniPageThis) {
    const titleType = String(this.data?.titleType ?? "PERSONAL");
    const titleName = String(this.data?.titleName ?? "");
    const taxNo = String(this.data?.taxNo ?? "");
    if (titleType === "ENTERPRISE" && (!titleName || !taxNo)) {
      toast("企业抬头需填写单位名称和税号");
      return;
    }
    this.setData({ loading: true, error: "" });
    try {
      await saveInvoiceTitle({
        title_type: titleType,
        title_name: titleType === "PERSONAL" ? "个人" : titleName,
        tax_no: taxNo,
        email: String(this.data?.email ?? ""),
        is_default: true
      });
      await this.load();
      toast("保存成功");
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "保存失败" });
    }
  }
});
