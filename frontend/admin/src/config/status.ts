const STATUS_LABEL_MAP: Record<string, string> = {
  PENDING: "待支付", PAID: "已支付", CLOSED: "已关闭",
  NO_SHIPMENT: "无需发货", PENDING_SHIPMENT: "待发货", SHIPPED: "已发货", SIGNED: "已签收",
  NONE: "无退款", REVIEWING: "审核中", REJECTED: "已拒绝", PROCESSING: "处理中",
  MANUAL_REQUIRED: "待人工处理", FAILED: "失败", REFUNDED: "已退款",
  NOT_APPLIED: "未申请", APPLIED: "已申请", TO_BE_ISSUED: "待开具", ISSUED: "已开具", RED_REVERSED: "已红冲",
  ACTIVE: "已启用", DISABLED: "已停用", ENABLED: "已启用",
  ON_SHELF: "已上架", OFF_SHELF: "已下架", DRAFT: "草稿", PENDING_REVIEW: "待审核",
  DELETE_PENDING: "待删除", DELETED: "已删除",
  FROZEN: "已冻结", REVOKED: "已撤销", MERGED: "已合并",
  APPROVING: "审批中", APPROVAL_REJECTED: "审批拒绝", WAIT_CONFIRM: "待确认",
  CONFIRMED: "已确认", COMPLETED: "已完成", CANCELED: "已取消",
  NOT_INVOICED: "未开票", INVOICED: "已开票",
  MATCHED: "已匹配", AMOUNT_DIFF: "金额差异", FEE_DIFF: "手续费差异", UNMATCHED: "未匹配", DUPLICATE: "重复",
  PENDING_SUPPLEMENT: "待补充", UPLOADED: "已上传",
  IN_APP: "站内通知", SENT: "已发送", UNREAD: "未读", READ: "已读",
  PENDING_FOLLOW: "待跟进", CONTACTED: "已联系", CONVERTED: "已转化", ABANDONED: "已放弃",
  PUBLISHED: "已发布", HIDDEN: "已隐藏",
  LIVE: "直播课", RECORDED: "录制课", MATERIAL: "实物",
  SUCCESS: "成功", CHAPTER: "章节", LESSON: "课节"
};

const SUCCESS_CODES = new Set([
  "PAID", "ISSUED", "SIGNED", "ACTIVE", "SENT", "READ", "CONVERTED", "ON_SHELF",
  "COMPLETED", "ENABLED", "PUBLISHED", "SUCCESS", "MATCHED", "UPLOADED",
  "CONFIRMED", "INVOICED"
]);

const WARNING_CODES = new Set([
  "PENDING", "PENDING_SHIPMENT", "REVIEWING", "PROCESSING", "APPLIED",
  "TO_BE_ISSUED", "MANUAL_REQUIRED", "PENDING_REVIEW", "PENDING_FOLLOW",
  "CONTACTED", "PENDING_SUPPLEMENT", "WAIT_CONFIRM", "APPROVING",
  "NOT_INVOICED", "UNREAD", "DRAFT"
]);

const DANGER_CODES = new Set([
  "FAILED", "REJECTED", "REFUNDED", "RED_REVERSED", "REVOKED", "CLOSED",
  "OFF_SHELF", "DELETED", "DELETE_PENDING", "ABANDONED", "APPROVAL_REJECTED",
  "CANCELED", "FROZEN", "AMOUNT_DIFF", "FEE_DIFF", "UNMATCHED", "DUPLICATE"
]);

const MUTED_CODES = new Set([
  "NO_SHIPMENT", "NONE", "NOT_APPLIED", "DISABLED", "MERGED"
]);

export function statusLabel(code: string | null | undefined): string {
  if (!code) return "-";
  return STATUS_LABEL_MAP[code] ?? code;
}

export function statusClass(code: string | null | undefined): string {
  if (!code) return "muted";
  if (SUCCESS_CODES.has(code)) return "success";
  if (WARNING_CODES.has(code)) return "warning";
  if (DANGER_CODES.has(code)) return "danger";
  if (MUTED_CODES.has(code)) return "muted";
  return "info";
}

const FIELD_LABEL_MAP: Record<string, string> = {
  order_no: "订单编号", shipment_no: "发货单号", purchase_no: "采购单号",
  payment_status: "支付状态", fulfillment_status: "履约状态", refund_status: "退款状态", invoice_status: "开票状态",
  payable_amount_cent: "应付金额", paid_amount_cent: "实付金额",
  logistics_company_name: "物流公司", tracking_no: "运单号",
  shipped_at: "发货时间", signed_at: "签收时间",
  course_title: "课程", student_name: "学员",
  apply_amount_cent: "申请金额", approved_amount_cent: "批准金额",
  invoice_amount_cent: "发票金额", total_amount_cent: "采购金额",
  purchase_status: "采购状态", input_invoice_status: "进项票状态",
  created_at: "创建时间", updated_at: "更新时间",
  status: "状态", exception_flag: "异常", exception_reason: "异常原因",
  receiver_name: "收货人", receiver_mobile: "收货手机",
  supplier_name: "供货商", sku_name: "商品名"
};

export function fieldLabel(key: string): string {
  return FIELD_LABEL_MAP[key] ?? key;
}
