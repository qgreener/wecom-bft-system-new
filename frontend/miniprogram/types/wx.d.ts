declare function App(options: Record<string, unknown>): void;
declare function Page(options: Record<string, unknown>): void;
declare function getApp<T = { globalData?: Record<string, unknown> }>(): T;
declare function getCurrentPages(): Array<{ setData?: (data: Record<string, unknown>) => void }>;

declare const wx: WechatMiniprogram.Wx;

declare namespace WechatMiniprogram {
  type RequestMethod = "GET" | "POST" | "PUT" | "DELETE";

  type RequestSuccess<T = unknown> = {
    statusCode: number;
    data: T;
    header?: Record<string, string>;
  };

  type RequestOptions<T = unknown> = {
    url: string;
    method?: RequestMethod;
    data?: unknown;
    header?: Record<string, string>;
    timeout?: number;
    success?: (res: RequestSuccess<T>) => void;
    fail?: (error: { errMsg: string }) => void;
  };

  type NavigateOptions = {
    url: string;
  };

  type ToastOptions = {
    title: string;
    icon?: "success" | "error" | "loading" | "none";
    duration?: number;
  };

  type ModalOptions = {
    title: string;
    content: string;
    confirmText?: string;
    cancelText?: string;
    showCancel?: boolean;
    success?: (res: { confirm: boolean; cancel: boolean }) => void;
  };

  type LoginOptions = {
    success?: (res: { code: string }) => void;
    fail?: (error: { errMsg: string }) => void;
  };

  type Wx = {
    request<T = unknown>(options: RequestOptions<T>): void;
    navigateTo(options: NavigateOptions): void;
    redirectTo(options: NavigateOptions): void;
    switchTab(options: NavigateOptions): void;
    navigateBack(options?: { delta?: number }): void;
    showToast(options: ToastOptions): void;
    showModal(options: ModalOptions): void;
    setStorageSync(key: string, value: unknown): void;
    getStorageSync<T = unknown>(key: string): T;
    removeStorageSync(key: string): void;
    login(options: LoginOptions): void;
    previewImage(options: { urls: string[]; current?: string }): void;
    stopPullDownRefresh(): void;
  };
}
