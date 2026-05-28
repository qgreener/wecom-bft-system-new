const statusLabels: Record<string, string> = {
  PENDING: "待支付",
  PAID: "已支付",
  CLOSED: "已关闭",
  NO_SHIPMENT: "无需发货",
  PENDING_SHIPMENT: "待发货",
  SHIPPED: "已发货",
  SIGNED: "已签收",
  PICKED_UP: "已揽件",
  IN_TRANSIT: "运输中",
  OUT_FOR_DELIVERY: "派送中",
  NONE: "无退款",
  REVIEWING: "审核中",
  REJECTED: "已拒绝",
  PROCESSING: "处理中",
  MANUAL_REQUIRED: "待人工处理",
  FAILED: "失败",
  REFUNDED: "已退款",
  NOT_APPLIED: "未申请",
  APPLIED: "已申请",
  TO_BE_ISSUED: "待开具",
  ISSUED: "已开具",
  RED_REVERSED: "已红冲",
  ACTIVE: "可学习",
  FROZEN: "已冻结",
  REVOKED: "已撤销",
  ON_SHELF: "上架中",
  OFF_SHELF: "已下架",
  DELETE_PENDING: "待删除",
  LIVE: "直播课",
  RECORDED: "录制课",
  VIDEO: "录制课",
  IN_APP: "站内通知",
  SENT: "已发送",
  UNREAD: "未读",
  READ: "已读"
};

export function formatCent(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "-";
  }
  const sign = value < 0 ? "-" : "";
  const absolute = Math.abs(Math.trunc(value));
  const yuan = Math.floor(absolute / 100);
  const cents = String(absolute % 100).padStart(2, "0");
  return `${sign}${yuan.toLocaleString("en-US")}.${cents}`;
}

export function formatYuan(value: number | null | undefined): string {
  const text = formatCent(value);
  return text === "-" ? text : `¥${text}`;
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }
  return value.replace("T", " ").slice(0, 16);
}

export function statusLabel(code: string | null | undefined): string {
  if (!code) {
    return "-";
  }
  return statusLabels[code] ?? code;
}

export function statusTone(code: string | null | undefined): string {
  if (!code) {
    return "muted";
  }
  if (["PAID", "ISSUED", "SIGNED", "ACTIVE", "SENT", "READ"].includes(code)) {
    return "success";
  }
  if (["PENDING", "PENDING_SHIPMENT", "REVIEWING", "PROCESSING", "APPLIED", "TO_BE_ISSUED", "MANUAL_REQUIRED"].includes(code)) {
    return "warning";
  }
  if (["FAILED", "REJECTED", "REFUNDED", "RED_REVERSED", "REVOKED", "CLOSED"].includes(code)) {
    return "danger";
  }
  return "info";
}

export function maskMobile(value: string | null | undefined): string {
  if (!value || value.length < 7) {
    return value ?? "-";
  }
  return `${value.slice(0, 3)}****${value.slice(-4)}`;
}

export function countdownText(expireAt: string | null | undefined, serverNow?: string | null): string {
  if (!expireAt) {
    return "";
  }
  const end = new Date(expireAt).getTime();
  const now = serverNow ? new Date(serverNow).getTime() : Date.now();
  const seconds = Math.max(0, Math.floor((end - now) / 1000));
  if (seconds <= 0) {
    return "支付已超时";
  }
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}分${String(rest).padStart(2, "0")}秒后关闭`;
}
