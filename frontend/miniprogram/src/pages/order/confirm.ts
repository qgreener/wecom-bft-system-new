import { confirmOrder, createOrder, fetchAddresses, payWithBackendMock } from "../../services/app-api";
import { canCreateOrder } from "../../services/order-actions";
import { getStoredSession } from "../../stores/session";
import type { OrderConfirmResponse } from "../../types/api";
import { formatYuan } from "../../utils/format";
import type { MiniPageThis, PageOptions } from "../../utils/page";
import { go, optionNumber, toast } from "../../utils/page";
import { courseTitle, receiverLine, snapshotValue, specName } from "../../utils/snapshot";

function toView(confirm: OrderConfirmResponse) {
  return {
    ...confirm,
    course_title: courseTitle(confirm.course_snapshot),
    spec_name: specName(confirm.price_snapshot),
    receiver_line: receiverLine(confirm.receiver_snapshot),
    amount_text: formatYuan(confirm.payable_amount_cent),
    total_text: formatYuan(confirm.total_amount_cent),
    tax_rule: snapshotValue(confirm.tax_snapshot, ["tax_rule_no", "rule_no"], "-")
  };
}

Page({
  data: {
    loading: false,
    submitting: false,
    error: "",
    courseId: 0,
    specId: 0,
    quantity: 1,
    addressId: null as number | null,
    pickedAddressId: null as number | null,
    confirm: null,
    loggedIn: false,
    mobileBound: false
  },

  onLoad(this: MiniPageThis, options: PageOptions) {
    const courseId = optionNumber(options, "course_id");
    const specId = optionNumber(options, "spec_id");
    const addressId = optionNumber(options, "address_id") || null;
    this.setData({ courseId, specId, addressId });
    void this.load();
  },

  async onShow(this: MiniPageThis) {
    // 从 address/list?mode=picker 选地址回来后，setData 注入了 pickedAddressId
    const picked = this.data?.pickedAddressId;
    if (picked && picked !== this.data?.addressId) {
      this.setData({ addressId: picked, pickedAddressId: null });
      await this.load();
    }
  },

  async load(this: MiniPageThis) {
    const session = getStoredSession();
    const loggedIn = Boolean(session);
    const mobileBound = Boolean(session?.mobileBound);
    this.setData({ loggedIn, mobileBound, error: "" });
    const gate = canCreateOrder({ loggedIn, mobileBound });
    if (!gate.allowed) {
      this.setData({ error: gate.reason });
      return;
    }
    this.setData({ loading: true });
    try {
      // 第一次拉确认时，若 addressId 为空，先尝试取默认地址
      let addressId = this.data?.addressId as number | null;
      if (!addressId) {
        try {
          const list = await fetchAddresses();
          const defaultOne = list.find((a) => a.is_default) ?? list[0];
          if (defaultOne) {
            addressId = defaultOne.id;
            this.setData({ addressId });
          }
        } catch {
          // 拉地址失败不阻塞，让用户手动选
        }
      }
      const confirm = await confirmOrder({
        course_id: Number(this.data?.courseId),
        spec_id: Number(this.data?.specId),
        quantity: Number(this.data?.quantity ?? 1),
        address_id: addressId,
        source_code: "S9_MP"
      });
      this.setData({ loading: false, confirm: toView(confirm) });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "订单确认失败" });
    }
  },

  login() {
    go("/pages/auth/login");
  },

  address() {
    go("/pages/address/list?mode=picker");
  },

  async submit(this: MiniPageThis) {
    const session = getStoredSession();
    const gate = canCreateOrder({ loggedIn: Boolean(session), mobileBound: Boolean(session?.mobileBound) });
    if (!gate.allowed) {
      toast(gate.reason);
      return;
    }
    const confirm = this.data?.confirm as (OrderConfirmResponse & { amount_text?: string }) | null;
    if (!confirm) {
      toast("请先完成订单确认");
      return;
    }
    this.setData({ submitting: true, error: "" });
    try {
      const created = await createOrder({
        course_id: Number(this.data?.courseId),
        spec_id: Number(this.data?.specId),
        quantity: Number(this.data?.quantity ?? 1),
        address_id: this.data?.addressId as number | null,
        client_request_no: `S9_MP_${Date.now().toString(16)}`,
        source_code: "S9_MP",
        confirmed_payable_amount_cent: confirm.payable_amount_cent,
        confirm_token: confirm.confirm_token
      });
      const latest = await payWithBackendMock(created.order_id, created.payable_amount_cent);
      this.setData({ submitting: false });
      if (latest.payment_status === "PAID") {
        wx.redirectTo({ url: `/pages/payment/success?order_id=${latest.order_id}` });
      } else {
        wx.redirectTo({ url: `/pages/order/detail?order_id=${latest.order_id}` });
      }
    } catch (error) {
      this.setData({ submitting: false, error: error instanceof Error ? error.message : "提交订单失败" });
    }
  }
});
