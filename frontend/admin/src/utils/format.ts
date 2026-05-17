export type AnyRecord = Record<string, unknown>;

export function formatCent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return "-";
  }
  const sign = value < 0 ? "-" : "";
  const absolute = Math.abs(Math.trunc(value));
  const yuan = Math.floor(absolute / 100);
  const cent = String(absolute % 100).padStart(2, "0");
  return `${sign}${yuan.toLocaleString("zh-CN")}.${cent}`;
}

export function formatDateTime(value: unknown): string {
  if (!value || typeof value !== "string") {
    return "-";
  }
  return value.replace("T", " ").slice(0, 19);
}

export function emptyText(value: unknown): string {
  if (value === null || value === undefined || value === "") {
    return "-";
  }
  if (typeof value === "object") {
    return compactJson(value);
  }
  return String(value);
}

export function compactJson(value: unknown): string {
  try {
    return JSON.stringify(value);
  } catch {
    return "-";
  }
}

export function normalizeRecords<T = AnyRecord>(value: unknown): T[] {
  if (Array.isArray(value)) {
    return value as T[];
  }
  if (value && typeof value === "object" && Array.isArray((value as { records?: unknown }).records)) {
    return (value as { records: T[] }).records;
  }
  return [];
}

export function getRecordId(record: AnyRecord, keys: string[]): string | null {
  for (const key of keys) {
    const value = record[key];
    if (value !== null && value !== undefined && value !== "") {
      return String(value);
    }
  }
  return null;
}

export function labelFromSnapshot(record: AnyRecord, snapshotKey: string, field: string): string {
  const snapshot = record[snapshotKey];
  if (snapshot && typeof snapshot === "object" && field in snapshot) {
    return emptyText((snapshot as AnyRecord)[field]);
  }
  return "-";
}
