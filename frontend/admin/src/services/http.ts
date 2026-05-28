export type ApiEnvelope<T> = {
  code: string;
  message: string;
  trace_id?: string;
  data: T;
};

export type QueryParams = Record<string, string | number | boolean | null | undefined>;

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

const storageKey = "admin_access_token";
const browserStorage = typeof localStorage === "undefined" ? null : localStorage;

let accessToken = browserStorage?.getItem(storageKey) ?? "";

export function setAccessToken(token: string): void {
  accessToken = token;
  browserStorage?.setItem(storageKey, token);
}

export function clearAccessToken(): void {
  accessToken = "";
  browserStorage?.removeItem(storageKey);
}

export function getAccessToken(): string {
  return accessToken;
}

export function createIdempotencyKey(scope = "admin-write"): string {
  const randomValue = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`;
  return `${scope}-${randomValue}`;
}

export function buildApiUrl(path: string, params?: QueryParams): string {
  if (!params) {
    return path;
  }
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") {
      return;
    }
    searchParams.set(key, String(value));
  });
  const query = searchParams.toString();
  return query ? `${path}?${query}` : path;
}

export async function request<T>(
  path: string,
  options: RequestInit & { query?: QueryParams; idempotent?: boolean; idempotencyScope?: string } = {}
): Promise<T> {
  const headers = new Headers(options.headers);
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  if (options.body && !headers.has("Content-Type") && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (options.idempotent && !headers.has("Idempotency-Key")) {
    headers.set("Idempotency-Key", createIdempotencyKey(options.idempotencyScope));
  }

  const response = await fetch(buildApiUrl(path, options.query), {
    ...options,
    headers
  });
  const contentType = response.headers.get("Content-Type") ?? "";
  const envelope = contentType.includes("application/json")
    ? await response.json() as ApiEnvelope<T>
    : null;

  if (!response.ok || !envelope) {
    throw new ApiClientError(
      response.status,
      envelope?.code ?? `HTTP_${response.status}`,
      envelope?.message ?? response.statusText,
      envelope?.trace_id
    );
  }

  if (envelope.code === "OK" || envelope.code === "CREATED") {
    return envelope.data;
  }

  throw new ApiClientError(
    response.status,
    envelope.code,
    envelope.message || "接口返回业务错误",
    envelope.trace_id
  );
}
