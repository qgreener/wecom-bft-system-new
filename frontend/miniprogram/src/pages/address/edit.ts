import { createAddress, fetchAddresses, updateAddress } from "../../services/app-api";
import type { AppAddress } from "../../types/api";
import type { MiniPageThis } from "../../utils/page";
import { toast } from "../../utils/page";

Page({
  data: {
    addressId: 0,
    form: {
      receiver_name: "",
      receiver_mobile: "",
      province: "",
      city: "",
      district: "",
      detail_address: "",
      postal_code: "",
      is_default: false
    },
    loading: false,
    error: ""
  },

  async onLoad(this: MiniPageThis, options: Record<string, string>) {
    if (options.id) {
      this.setData({ addressId: Number(options.id) });
      try {
        const list = await fetchAddresses();
        const target = list.find((a) => a.id === Number(options.id));
        if (target) {
          this.setData({
            form: {
              receiver_name: target.receiver_name,
              receiver_mobile: target.receiver_mobile,
              province: target.province,
              city: target.city,
              district: target.district,
              detail_address: target.detail_address,
              postal_code: target.postal_code ?? "",
              is_default: target.is_default
            }
          });
        }
      } catch (error) {
        this.setData({ error: error instanceof Error ? error.message : "加载失败" });
      }
    }
  },

  onInput(this: MiniPageThis, e: { currentTarget: { dataset: { field: keyof AppAddress } }; detail: { value: string } }) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: e.detail.value });
  },

  onDefaultChange(this: MiniPageThis, e: { detail: { value: string[] } }) {
    this.setData({ "form.is_default": e.detail.value.includes("default") });
  },

  async save(this: MiniPageThis) {
    const f = this.data.form;
    if (!f.receiver_name || !/^1\d{10}$/.test(f.receiver_mobile) || !f.province || !f.city || !f.district || !f.detail_address) {
      this.setData({ error: "请填写完整收货信息" });
      return;
    }
    this.setData({ loading: true, error: "" });
    try {
      if (this.data.addressId) {
        await updateAddress(this.data.addressId, f);
      } else {
        await createAddress(f);
      }
      toast("已保存");
      this.setData({ loading: false });
      wx.navigateBack();
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "保存失败" });
    }
  }
});
