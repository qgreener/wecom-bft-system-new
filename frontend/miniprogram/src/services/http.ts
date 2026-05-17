import type { ApiEnvelope, QueryParams } from "../types/api";

export class ApiClientError extends Error {
  code: string;
  traceId?: string;
  status: number;

  constructor(status: number, code: string, message: string, traceId?: string) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
    this.code = code;
    this.traceId = traceId;
  }
}

export type RequestOptions = {
  method?: WechatMiniprogram.RequestMethod;
  query?: QueryParams;
  data?: unknown;
  accessToken?: string;
  idempotent?: boolean;
  idempotencyKey?: string;
  idempotencyScope?: string;
  timeoutMs?: number;
};

export function createIdempotencyKey(scope = "mp-write"): string {
  const randomValue = `${Date.now().toString(16)}-${Math.random().toString(16).slice(2, 10)}`;
  return `${scope}-${randomValue}`;
}

export function buildApiUrl(path: string, params?: QueryParams): string {
  if (!params) {
    return path;
  }
  const pairs: string[] = [];
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") {
      return;
    }
    pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  });
  return pairs.length === 0 ? path : `${path}?${pairs.join("&")}`;
}

export function buildRequestHeaders(options: {
  accessToken?: string;
  idempotent?: boolean;
  idempotencyKey?: string;
  idempotencyScope?: string;
} = {}): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json"
  };
  if (options.accessToken) {
    headers.Authorization = `Bearer ${options.accessToken}`;
  }
  if (options.idempotent) {
    headers["Idempotency-Key"] = options.idempotencyKey ?? createIdempotencyKey(options.idempotencyScope);
  }
  return headers;
}

export function getApiBaseUrl(): string {
  const app = typeof getApp === "function" ? getApp<{ globalData?: { apiBaseUrl?: string } }>() : null;
  return app?.globalData?.apiBaseUrl ?? "http://localhost:8080";
}

function toFullUrl(path: string, query?: QueryParams): string {
  const url = buildApiUrl(path, query);
  if (/^https?:\/\//.test(url)) {
    return url;
  }
  return `${getApiBaseUrl()}${url}`;
}

export function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    wx.request<ApiEnvelope<T>>({
      url: toFullUrl(path, options.query),
      method: options.method ?? "GET",
      data: options.data,
      timeout: options.timeoutMs ?? 12000,
      header: buildRequestHeaders({
        accessToken: options.accessToken,
        idempotent: options.idempotent,
        idempotencyKey: options.idempotencyKey,
        idempotencyScope: options.idempotencyScope
      }),
      success: (res) => {
        const envelope = res.data;
        const ok = res.statusCode >= 200
          && res.statusCode < 300
          && envelope
          && (envelope.code === "OK" || envelope.code === "CREATED");
        if (ok) {
          resolve(envelope.data);
          return;
        }
        reject(new ApiClientError(
          res.statusCode,
          envelope?.code ?? `HTTP_${res.statusCode}`,
          envelope?.message ?? "请求失败",
          envelope?.trace_id ?? envelope?.traceId
        ));
      },
      fail: (error) => {
        reject(new ApiClientError(0, "NETWORK_ERROR", error.errMsg || "网络请求失败"));
      }
    });
  });
}

export function showApiError(error: unknown): void {
  const message = error instanceof ApiClientError
    ? `${error.message}${error.traceId ? `（TraceId: ${error.traceId}）` : ""}`
    : error instanceof Error
      ? error.message
      : "操作失败";
  wx.showToast({ title: message.slice(0, 120), icon: "none", duration: 2600 });
}
