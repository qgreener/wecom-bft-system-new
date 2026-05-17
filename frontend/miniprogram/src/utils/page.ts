export type MiniPageThis = {
  setData(data: Record<string, unknown>): void;
  data?: Record<string, unknown>;
  [key: string]: any;
};

export type PageOptions = Record<string, string | undefined>;

export function optionNumber(options: PageOptions, key: string): number {
  const value = options[key];
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function toast(title: string): void {
  wx.showToast({ title, icon: "none", duration: 2200 });
}

export function go(url: string): void {
  wx.navigateTo({ url });
}

export function switchTab(url: string): void {
  wx.switchTab({ url });
}

export function setError(page: MiniPageThis, error: unknown): void {
  const message = error instanceof Error ? error.message : "请求失败";
  page.setData({ loading: false, error: message });
}
