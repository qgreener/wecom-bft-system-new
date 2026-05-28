const STATUS_LABEL_MAP: Record<string, string> = {
  PENDING: "待支付", PAID: "已支付", CLOSED: "已关闭",
  APPROVED: "已通过",
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
  CONFIRM: "已确认",
  IN_APP: "站内通知", SENT: "已发送", UNREAD: "未读", READ: "已读",
  PENDING_FOLLOW: "待跟进", CONTACTED: "已联系", CONVERTED: "已转化", ABANDONED: "已放弃",
  PUBLISHED: "已发布", HIDDEN: "已隐藏",
  LIVE: "直播课", RECORDED: "录制课", MATERIAL: "实物",
  SUCCESS: "成功", CHAPTER: "章节", LESSON: "课节",
  WECOM_SIDEBAR: "企微侧边栏", PROMOTION: "推广码落地页", OTHER: "其他",
  BANK_TRANSFER: "银行转账", WECHAT_TRANSFER: "微信转账", ORIGINAL: "原路退款",
  IN: "入库", OUT: "出库"
};

const SUCCESS_CODES = new Set([
  "PAID", "ISSUED", "SIGNED", "ACTIVE", "SENT", "READ", "CONVERTED", "ON_SHELF",
  "COMPLETED", "ENABLED", "PUBLISHED", "SUCCESS", "MATCHED", "UPLOADED",
  "CONFIRMED", "INVOICED", "APPROVED", "CONFIRM"
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
  "NO_SHIPMENT", "NONE", "NOT_APPLIED", "DISABLED", "MERGED", "OTHER"
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
  refund_no: "退款单号", invoice_no: "发票号", material_no: "材料编号",
  payment_status: "支付状态", fulfillment_status: "履约状态", refund_status: "退款状态", invoice_status: "开票状态",
  payable_amount_cent: "应付金额", paid_amount_cent: "实付金额",
  apply_amount_cent: "申请金额", tax_amount_cent: "税额",
  logistics_company_name: "物流公司", tracking_no: "运单号",
  shipped_at: "发货时间", signed_at: "签收时间",
  course_title: "课程", course_type: "课程类型", student_name: "学员", nickname: "昵称", mobile: "手机号",
  approved_amount_cent: "批准金额",
  invoice_amount_cent: "发票金额", total_amount_cent: "采购金额",
  purchase_status: "采购状态", input_invoice_status: "进项票状态",
  created_at: "创建时间", updated_at: "更新时间",
  status: "状态", exception_flag: "异常", exception_reason: "异常原因",
  receiver_name: "收货人", receiver_mobile: "收货手机",
  supplier_name: "供货商", sku_name: "商品名",
  lead_no: "线索编号", source_channel: "来源渠道", source_code: "来源明细",
  config_key: "配置键", display_name: "名称", masked_value: "当前值", editable_flag: "可编辑",
  trace_id: "TraceId", operator_name: "操作人", operation_module: "模块", operation_type: "动作",
  result: "结果", target_type: "对象类型", target_no: "对象编号",
  // 扩充
  course_id: "课程ID", spec_id: "规格ID", spec_name: "规格名", sale_price_cent: "规格单价",
  contains_physical: "含实物", tax_rule_id: "税务规则ID", default_tax_rule_id: "默认税务规则",
  category_code: "类目", category_id: "类目ID", category_name: "类目名",
  course_no: "课程编号", course_group_qr: "课程群二维码",
  teacher_user_id: "讲师ID", student_id: "学员ID", user_id: "用户ID", user_no: "账号",
  refund_reason: "退款原因", refund_channel: "退款渠道", refunded_at: "退款时间",
  failure_reason: "失败原因", reject_reason: "拒绝原因",
  invoice_apply_no: "申请号", title_type: "抬头类型", title_name: "抬头名称",
  email: "邮箱", tax_no: "税号",
  red_invoice_no: "红字发票号", red_reversed_at: "红冲时间",
  paid_at: "支付时间", payment_no: "支付流水号", payment_method: "支付方式", channel: "支付渠道",
  payment_result: "支付结果",
  bill_month: "账单月份", bill_source: "账单来源",
  batch_no: "对账批次号", file_name: "文件名",
  matched_count: "匹配数", diff_count: "差异数", total_count: "总数",
  notification_no: "通知编号", scene_code: "通知场景", title: "标题", content: "内容",
  send_status: "发送状态", read_status: "阅读状态", sent_at: "发送时间",
  // 通用
  expected_arrival_date: "预计到货", entitlement_no: "权益编号",
  current_stock: "当前库存", available_stock: "可用库存", safety_stock: "安全库存",
  cost_price_cent: "成本价",
  short_name: "简称", contact_name: "联系人", contact_mobile: "联系电话", contact_email: "联系邮箱",
  address: "地址", access_status: "接入状态",
  approval_no: "审批编号", approval_type: "审批类型", approval_status: "审批状态",
  applicant_user_id: "申请人ID", approver_user_id: "审批人ID",
  approval_comment: "审批意见", submit_reason: "提交原因",
  related_object_type: "关联对象类型", related_object_id: "关联对象ID",
  related_object_no: "关联对象编号"
};

/**
 * 详情面板/抽屉中默认隐藏的内部字段——这些字段或来自数据库主键、或对终端用户没有意义。
 */
export const HIDDEN_DETAIL_FIELDS: ReadonlySet<string> = new Set<string>([
  "id",
  "version",
  "deleted_flag",
  "deleted_at",
  "deleted_by",
  "created_by",
  "updated_by",
  "merchant_order_no",
  "idempotency_key",
  "callback_event_no",
  "external_payment_no",
  "external_refund_no",
  "external_transaction_no",
  "source_system",
  "source_system_event_no",
  "wx_openid",
  "wx_unionid",
  "wecom_external_user_id",
  "course_snapshot",
  "tax_rule_snapshot",
  "price_snapshot",
  "receiver_snapshot",
  "raw_snapshot"
]);

export function fieldLabel(key: string): string {
  return FIELD_LABEL_MAP[key] ?? key;
}

export function isHiddenDetailField(key: string): boolean {
  return HIDDEN_DETAIL_FIELDS.has(key);
}
