import { authorizePhoneMock, fetchMe, loginWithWechatMock } from "../../services/app-api";
import { getMockPhone, getStoredSession, setMockPhone, updateSessionFromMe } from "../../stores/session";
import { maskMobile } from "../../utils/format";
import type { MiniPageThis } from "../../utils/page";
import { toast } from "../../utils/page";

Page({
  data: {
    loading: false,
    session: null,
    phone: getMockPhone(),
    mobileText: "-",
    error: ""
  },

  onShow(this: MiniPageThis) {
    const session = getStoredSession();
    this.setData({
      session,
      mobileText: maskMobile(session?.mobile),
      phone: getMockPhone()
    });
  },

  onPhoneInput(this: MiniPageThis, event: { detail: { value: string } }) {
    const value = event.detail.value.trim();
    setMockPhone(value);
    this.setData({ phone: value });
  },

  async login(this: MiniPageThis) {
    this.setData({ loading: true, error: "" });
    try {
      const session = await loginWithWechatMock();
      const me = await fetchMe();
      updateSessionFromMe(me);
      this.setData({
        loading: false,
        session: getStoredSession(),
        mobileText: maskMobile(session.mobile)
      });
      toast("登录成功");
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "登录失败" });
    }
  },

  async authorizePhone(this: MiniPageThis) {
    this.setData({ loading: true, error: "" });
    try {
      const response = await authorizePhoneMock();
      this.setData({
        loading: false,
        session: getStoredSession(),
        mobileText: maskMobile(response.mobile)
      });
      toast("手机号授权成功");
    } catch (error) {
      this.setData({ loading: false, error: error instanceof Error ? error.message : "手机号授权失败" });
    }
  }
});
