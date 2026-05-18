import { getMockPhone, getOrCreateMockOpenid } from "../stores/session";

/**
 * Returns the wx.login() code when available so the backend
 * WechatMiniappAuthAdapter (real or mock) can resolve the real openid.
 * Falls back to a `mock:<id>` string when wx.login is unavailable
 * (开发者工具 + appid 未配置时) so end-to-end mock 流程仍可走通。
 */
export function getWechatLoginCode(): Promise<string> {
  return new Promise((resolve) => {
    if (typeof wx?.login === "function") {
      wx.login({
        success: (res) => {
          if (res && res.code) {
            resolve(res.code);
            return;
          }
          resolve(`mock:${getOrCreateMockOpenid()}`);
        },
        fail: () => resolve(`mock:${getOrCreateMockOpenid()}`)
      });
      return;
    }
    resolve(`mock:${getOrCreateMockOpenid()}`);
  });
}

export function getPhoneAuthorizeCode(): string {
  return `mock:${getMockPhone()}`;
}
