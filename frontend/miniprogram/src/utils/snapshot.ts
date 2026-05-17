import type { Snapshot } from "../types/api";

export function snapshotValue(snapshot: Snapshot, keys: string[], fallback = "-"): string {
  if (!snapshot) {
    return fallback;
  }
  for (const key of keys) {
    const value = snapshot[key];
    if (value !== null && value !== undefined && value !== "") {
      return String(value);
    }
  }
  return fallback;
}

export function parseSnapshot(value: Snapshot | string): Snapshot {
  if (typeof value !== "string") {
    return value;
  }
  try {
    const parsed = JSON.parse(value) as Snapshot;
    return parsed;
  } catch {
    return { raw: value };
  }
}

export function courseTitle(snapshot: Snapshot | string): string {
  return snapshotValue(parseSnapshot(snapshot), ["course_title", "courseTitle", "title", "raw"], "课程");
}

export function specName(snapshot: Snapshot | string): string {
  return snapshotValue(parseSnapshot(snapshot), ["spec_name", "specName"], "默认规格");
}

export function receiverLine(snapshot: Snapshot): string {
  if (!snapshot) {
    return "暂无收货地址";
  }
  const region = [
    snapshotValue(snapshot, ["province"], ""),
    snapshotValue(snapshot, ["city"], ""),
    snapshotValue(snapshot, ["district"], "")
  ].join("");
  return `${region}${snapshotValue(snapshot, ["detail_address", "detailAddress"], "")}`;
}
