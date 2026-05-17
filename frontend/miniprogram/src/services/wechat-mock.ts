import { getMockPhone, getOrCreateMockOpenid } from "../stores/session";

export function getWechatLoginCode(): Promise<string> {
  return new Promise((resolve) => {
    // 当前后端仅支持 mock: 前缀的小程序登录码，真实微信 code 在 S11/S12 接入。
    if (typeof wx?.login === "function") {
      wx.login({
        success: () => resolve(getOrCreateMockOpenid()),
        fail: () => resolve(getOrCreateMockOpenid())
      });
      return;
    }
    resolve(getOrCreateMockOpenid());
  });
}

export function getPhoneAuthorizeCode(): string {
  return `mock:${getMockPhone()}`;
}
