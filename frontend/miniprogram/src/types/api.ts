export type QueryParams = Record<string, string | number | boolean | null | undefined>;
export type Snapshot = Record<string, unknown> | null | undefined;

export type ApiEnvelope<T> = {
  code: string;
  message: string;
  trace_id?: string;
  traceId?: string;
  data: T;
};

export type PageResult<T> = {
  records: T[];
  page_no?: number;
  pageNo?: number;
  page_size?: number;
  pageSize?: number;
  total?: number;
};

export type StudentSession = {
  user_no?: string;
  userNo?: string;
  student_id: number;
  student_no: string;
  access_token: string;
  mobile_bound: boolean;
  mobile?: string;
  expires_in?: number;
  expire_at?: string;
};

export type StudentMe = {
  student_id: number;
  student_no: string;
  user_id: number;
  mobile?: string;
  nickname?: string;
  status: string;
  merged_to_student_id?: number | null;
};

export type PhoneAuthorizeResponse = {
  student_id: number;
  student_no: string;
  mobile: string;
  merged_from_student_no?: string | null;
  status: string;
  mobile_bound: boolean;
};

export type AppCourse = {
  course_id: number;
  course_no: string;
  course_title: string;
  cover_url?: string;
  summary?: string;
  course_type?: string;
  sale_start_at?: string;
  sale_end_at?: string;
  min_sale_price_cent: number;
  status: string;
};

export type CourseSpec = {
  spec_id: number;
  spec_no?: string;
  course_id: number;
  spec_name: string;
  sale_price_cent: number;
  origin_price_cent?: number | null;
  stock_mode?: string;
  contains_physical: boolean;
  sku_id?: number | null;
  gift_sku_id?: number | null;
  tax_rule_id?: number | null;
  amount_split_snapshot?: string | null;
  status: string;
  sort_no?: number;
};

export type LessonNode = {
  node_id: number;
  course_id: number;
  parent_node_id?: number | null;
  node_type: string;
  title: string;
  lesson_type?: string | null;
  live_start_at?: string | null;
  live_end_at?: string | null;
  replay_url?: string | null;
  resource_file?: string | null;
  status: string;
  sort_no?: number;
};

export type CourseDetail = AppCourse & {
  detail?: string;
  teacher_user_id?: number | null;
  category_code?: string | null;
  course_group_qr?: string | null;
  default_tax_rule_id?: number | null;
  specs: CourseSpec[];
  lesson_summary: LessonNode[];
};

export type OrderConfirmResponse = {
  course_snapshot: Snapshot;
  price_snapshot: Snapshot;
  tax_snapshot: Snapshot;
  receiver_snapshot: Snapshot;
  total_amount_cent: number;
  discount_amount_cent: number;
  payable_amount_cent: number;
  contains_physical: boolean;
  stock_warning: boolean;
  confirm_token: string;
  server_time: string;
};

export type OrderCreateResponse = {
  order_id: number;
  order_no: string;
  merchant_order_no: string;
  payment_status: string;
  fulfillment_status: string;
  refund_status: string;
  invoice_status: string;
  payable_amount_cent: number;
  payment_expire_at?: string;
  server_time?: string;
};

export type PaymentPrepareResponse = {
  order_id: number;
  order_no: string;
  merchant_order_no: string;
  payment_params: Record<string, unknown>;
  payment_expire_at?: string;
  server_time?: string;
};

export type PaymentCallbackResponse = {
  processing_status: string;
  order_id?: number | null;
  payment_id?: number | null;
  payment_no?: string | null;
  payment_status?: string | null;
  idempotency_key?: string;
  failure_reason?: string | null;
};

export type OrderListItem = {
  order_id: number;
  order_no: string;
  merchant_order_no?: string;
  course_snapshot?: Snapshot;
  paid_amount_cent?: number | null;
  payable_amount_cent: number;
  payment_status: string;
  fulfillment_status: string;
  refund_status: string;
  invoice_status: string;
  payment_expire_at?: string | null;
  server_time?: string | null;
  created_at?: string | null;
};

export type OrderItem = {
  order_item_id: number;
  line_no: number;
  course_id: number;
  spec_id: number;
  item_name: string;
  quantity: number;
  unit_price_cent: number;
  total_amount_cent: number;
  payable_amount_cent: number;
  contains_physical: boolean;
  course_snapshot?: Snapshot;
  spec_snapshot?: Snapshot;
};

export type Shipment = {
  shipment_id: number;
  shipment_no: string;
  status: string;
  receiver_snapshot?: Snapshot;
  exception_flag?: boolean;
  exception_reason?: string | null;
  created_at?: string | null;
};

export type Entitlement = {
  entitlement_id: number;
  entitlement_no: string;
  order_id?: number;
  order_no?: string;
  course_id: number;
  spec_id: number;
  status: string;
  opened_at?: string | null;
  expire_at?: string | null;
  remind_stopped?: boolean;
  course_snapshot?: Snapshot | string;
};

export type NotificationItem = {
  notification_id: number;
  notification_no: string;
  channel: string;
  scene_code: string;
  title: string;
  content: string;
  send_status: string;
  read_status: string;
  sent_at?: string | null;
};

export type OrderDetail = OrderListItem & {
  items: OrderItem[];
  price_snapshot?: Snapshot;
  tax_snapshot?: Snapshot;
  receiver_snapshot?: Snapshot;
  total_amount_cent: number;
  discount_amount_cent: number;
  paid_at?: string | null;
  closed_at?: string | null;
  close_reason?: string | null;
  entitlements?: Entitlement[];
  shipments?: Shipment[];
  notifications?: NotificationItem[];
  document_links?: Array<Record<string, unknown>>;
};

export type RefundResponse = {
  refund_id: number;
  refund_no: string;
  order_id: number;
  order_no?: string;
  apply_amount_cent: number;
  approved_amount_cent?: number | null;
  status: string;
  refund_channel?: string | null;
  external_refund_no?: string | null;
  manual_voucher_no?: string | null;
  manual_voucher_file?: string | null;
  failure_reason?: string | null;
  entitlement_action?: string | null;
  refunded_at?: string | null;
};

export type InvoiceTitle = {
  title_id: number;
  title_type: string;
  title_name: string;
  tax_no?: string | null;
  email?: string | null;
  is_default: boolean;
  status: string;
};

export type InvoiceResponse = {
  invoice_id: number;
  invoice_apply_no: string;
  order_id: number;
  order_no?: string;
  invoice_amount_cent: number;
  status: string;
  invoice_channel?: string | null;
  invoice_no?: string | null;
  invoice_file?: string | null;
  issued_at?: string | null;
  source_refund_id?: number | null;
  red_invoice_no?: string | null;
  red_invoice_file?: string | null;
  red_reversed_at?: string | null;
  failure_reason?: string | null;
};
