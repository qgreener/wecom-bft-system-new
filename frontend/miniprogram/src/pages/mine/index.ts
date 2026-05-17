import { fetchMe } from "../../services/app-api";
import { clearSession, getStoredSession, updateSessionFromMe } from "../../stores/session";
import { maskMobile } from "../../utils/format";
import type { MiniPageThis } from "../../utils/page";
import { go } from "../../utils/page";

Page({
  data: {
    loading: false,
    session: null,
    mobileText: "-",
    error: ""
  },

  async onShow(this: MiniPageThis) {
    const session = getStoredSession();
    this.setData({ session, mobileText: maskMobile(session?.mobile), error: "" });
    if (!session) {
      return;
    }
    this.setData({ loading: true });
    try {
      const me = await fetchMe();
      const next = updateSessionFromMe(me);
      this.setData({ loading: false, session: next, mobileText: maskMobile(next?.mobile) });
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "登录态校验失败" });
    }
  },

  login() {
    go("/pages/auth/login");
  },

  orders() {
    go("/pages/order/list");
  },

  invoices() {
    go("/pages/invoice/titles");
  },

  addresses() {
    go("/pages/address/list");
  },

  notifications() {
    go("/pages/notifications/list");
  },

  logout(this: MiniPageThis) {
    clearSession();
    this.setData({ session: null, mobileText: "-" });
  }
});
