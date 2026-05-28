<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { healthEndpoint, surfaces } from "@wecom-bft/shared";

type ApiEnvelope<T> = {
  code: string;
  message: string;
  trace_id?: string;
  data: T;
};

type SupplierSession = {
  supplier_no: string;
  supplier_name: string;
  session_token: string;
};

type PurchaseSummary = {
  purchase_id: number;
  purchase_no: string;
  total_amount_cent: number;
  purchase_status: string;
  input_invoice_status?: string;
  created_at?: string;
};

type PurchasePage = {
  records: PurchaseSummary[];
  total: number;
};

type PurchaseItem = {
  sku_name?: string;
  quantity?: number;
  received_quantity?: number;
};

type PurchaseDetail = PurchaseSummary & {
  supplier_id?: number;
  expected_arrival_date?: string;
  logistics_company_name?: string;
  tracking_no?: string;
  purchase_items?: PurchaseItem[];
};

const surface = surfaces.supplier;
const loginForm = reactive({
  accessToken: "mock:SUPPLIER_S3",
  supplierNo: "SUP_S3_DEMO"
});
const actionForm = reactive({
  expectedArrivalDate: "",
  rejectReason: "当前批次无法按期供货",
  logisticsCompanyName: "顺丰速运",
  trackingNo: "SF" + Date.now().toString().slice(-10),
  remark: "供应商 H5 演示回填"
});
const session = ref<SupplierSession | null>(null);
const purchases = ref<PurchaseSummary[]>([]);
const detail = ref<PurchaseDetail | null>(null);
const status = ref("");
const busy = ref("");
const error = ref("");
const message = ref("");

const totalText = computed(() => `${purchases.value.length} / ${detail.value ? detail.value.purchase_no : "未选中"}`);

function yuan(value?: number): string {
  if (typeof value !== "number") return "-";
  return `¥${(value / 100).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function statusText(value?: string): string {
  const labels: Record<string, string> = {
    APPROVING: "审批中",
    APPROVAL_REJECTED: "审批拒绝",
    WAIT_CONFIRM: "待确认",
    CONFIRMED: "已确认",
    SHIPPED: "已发货",
    REJECTED: "已拒绝",
    COMPLETED: "已完成",
    CANCELED: "已取消",
    NOT_INVOICED: "未开票",
    INVOICED: "已开票"
  };
  return labels[value ?? ""] ?? value ?? "-";
}

function tone(value?: string): string {
  if (["CONFIRMED", "SHIPPED", "COMPLETED", "INVOICED"].includes(value ?? "")) return "success";
  if (["WAIT_CONFIRM", "APPROVING", "NOT_INVOICED"].includes(value ?? "")) return "warning";
  if (["REJECTED", "APPROVAL_REJECTED", "CANCELED"].includes(value ?? "")) return "danger";
  return "muted";
}

function idempotencyKey(scope: string): string {
  const random = crypto.randomUUID ? crypto.randomUUID() : `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`;
  return `${scope}-${random}`;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set("Content-Type", "application/json");
  if (session.value?.session_token) headers.set("Authorization", `Bearer ${session.value.session_token}`);
  const response = await fetch(path, { ...options, headers });
  const envelope = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || !["OK", "CREATED"].includes(envelope.code)) {
    throw new Error(envelope.message || `HTTP ${response.status}`);
  }
  return envelope.data;
}

async function login(): Promise<void> {
  busy.value = "login";
  error.value = "";
  message.value = "";
  try {
    const data = await request<SupplierSession>("/api/supplier-h5/auth/token", {
      method: "POST",
      body: JSON.stringify({
        access_token: loginForm.accessToken.trim(),
        supplier_no: loginForm.supplierNo.trim()
      })
    });
    session.value = data;
    localStorage.setItem("supplier_h5_session", JSON.stringify(data));
    message.value = `${data.supplier_name} 已登录`;
    await loadPurchases();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "供货商登录失败";
  } finally {
    busy.value = "";
  }
}

async function loadPurchases(): Promise<void> {
  if (!session.value) return;
  busy.value = "list";
  error.value = "";
  try {
    const query = new URLSearchParams({ page_no: "1", page_size: "20" });
    if (status.value) query.set("status", status.value);
    const data = await request<PurchasePage>(`/api/supplier-h5/purchases?${query.toString()}`);
    purchases.value = data.records ?? [];
    if (!detail.value && purchases.value[0]) {
      await openDetail(purchases.value[0].purchase_id);
    }
  } catch (e) {
    purchases.value = [];
    error.value = e instanceof Error ? e.message : "采购单加载失败";
  } finally {
    busy.value = "";
  }
}

async function openDetail(id: number): Promise<void> {
  busy.value = `detail-${id}`;
  error.value = "";
  try {
    detail.value = await request<PurchaseDetail>(`/api/supplier-h5/purchases/${id}`);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "采购详情加载失败";
  } finally {
    busy.value = "";
  }
}

async function performAction(action: "confirm" | "reject" | "logistics"): Promise<void> {
  if (!detail.value) return;
  busy.value = action;
  error.value = "";
  message.value = "";
  const payload = action === "confirm"
    ? { expected_arrival_date: actionForm.expectedArrivalDate || null, remark: actionForm.remark }
    : action === "reject"
      ? { supplier_reject_reason: actionForm.rejectReason }
      : {
          logistics_company_name: actionForm.logisticsCompanyName,
          tracking_no: actionForm.trackingNo,
          remark: actionForm.remark
        };
  try {
    detail.value = await request<PurchaseDetail>(`/api/supplier-h5/purchases/${detail.value.purchase_id}/${action}`, {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey(`supplier-${action}`) },
      body: JSON.stringify(payload)
    });
    message.value = `采购单 ${detail.value.purchase_no} 已更新为 ${statusText(detail.value.purchase_status)}`;
    await loadPurchases();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "采购单操作失败";
  } finally {
    busy.value = "";
  }
}

onMounted(() => {
  // 1) 优先检查 URL ?invite=<token>，自动免登（finhub.tax 替代企微上下游链消息）
  const params = new URLSearchParams(window.location.search);
  const invite = params.get("invite");
  if (invite && invite.trim()) {
    void inviteLogin(invite.trim());
    return;
  }
  // 2) 复用本地 session
  const saved = localStorage.getItem("supplier_h5_session");
  if (!saved) return;
  try {
    session.value = JSON.parse(saved) as SupplierSession;
    void loadPurchases();
  } catch {
    localStorage.removeItem("supplier_h5_session");
  }
});

async function inviteLogin(invite: string): Promise<void> {
  busy.value = "login";
  error.value = "";
  message.value = "";
  try {
    const data = await request<SupplierSession>("/api/supplier-h5/auth/token", {
      method: "POST",
      body: JSON.stringify({
        access_token: `invite:${invite}`,
        // supplier_no 由后端从 invite token 解析，前端任意填
        supplier_no: ""
      })
    });
    session.value = data;
    localStorage.setItem("supplier_h5_session", JSON.stringify(data));
    message.value = `已通过邀请链接登录：${data.supplier_name}`;
    await loadPurchases();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "邀请链接登录失败";
  } finally {
    busy.value = "";
  }
}
</script>

<template>
  <main class="supplier-shell">
    <section class="hero">
      <p>{{ surface.name }}</p>
      <h1>采购协同工作台</h1>
      <div>
        <span>{{ surface.routeBase }}</span>
        <span>{{ healthEndpoint }}</span>
      </div>
      <small>{{ surface.authBoundary }}</small>
    </section>

    <section class="panel login-panel">
      <label>
        <span>Mock Access Token</span>
        <input v-model="loginForm.accessToken" />
      </label>
      <label>
        <span>供货商编号</span>
        <input v-model="loginForm.supplierNo" />
      </label>
      <button type="button" :disabled="busy === 'login'" @click="login">{{ busy === "login" ? "登录中" : "登录供货商" }}</button>
    </section>

    <section class="panel toolbar">
      <div>
        <strong>{{ session?.supplier_name ?? "未登录" }}</strong>
        <small>{{ totalText }}</small>
      </div>
      <select v-model="status" @change="loadPurchases">
        <option value="">全部采购单</option>
        <option value="WAIT_CONFIRM">待确认</option>
        <option value="CONFIRMED">已确认</option>
        <option value="SHIPPED">已发货</option>
        <option value="COMPLETED">已完成</option>
      </select>
    </section>

    <p v-if="message" class="toast success">{{ message }}</p>
    <p v-if="error" class="toast error">{{ error }}</p>

    <section class="layout">
      <div class="purchase-list">
        <button
          v-for="row in purchases"
          :key="row.purchase_id"
          type="button"
          :class="{ active: detail?.purchase_id === row.purchase_id }"
          @click="openDetail(row.purchase_id)"
        >
          <span>{{ row.purchase_no }}</span>
          <strong>{{ yuan(row.total_amount_cent) }}</strong>
          <em :class="tone(row.purchase_status)">{{ statusText(row.purchase_status) }}</em>
        </button>
        <p v-if="session && purchases.length === 0" class="empty">当前筛选下暂无采购单</p>
      </div>

      <article v-if="detail" class="detail-card">
        <header>
          <div>
            <span>采购详情</span>
            <h2>{{ detail.purchase_no }}</h2>
          </div>
          <em :class="tone(detail.purchase_status)">{{ statusText(detail.purchase_status) }}</em>
        </header>
        <dl>
          <div><dt>金额</dt><dd>{{ yuan(detail.total_amount_cent) }}</dd></div>
          <div><dt>进项票</dt><dd>{{ statusText(detail.input_invoice_status) }}</dd></div>
          <div><dt>预计到货</dt><dd>{{ detail.expected_arrival_date ?? "-" }}</dd></div>
          <div><dt>物流</dt><dd>{{ detail.logistics_company_name ?? "-" }} {{ detail.tracking_no ?? "" }}</dd></div>
        </dl>
        <ul>
          <li v-for="item in detail.purchase_items ?? []" :key="item.sku_name">
            <span>{{ item.sku_name }}</span>
            <strong>{{ item.received_quantity ?? 0 }} / {{ item.quantity ?? 0 }}</strong>
          </li>
        </ul>
        <div class="action-grid">
          <label><span>预计到货</span><input v-model="actionForm.expectedArrivalDate" type="date" /></label>
          <button type="button" :disabled="busy === 'confirm'" @click="performAction('confirm')">确认接单</button>
          <label><span>物流公司</span><input v-model="actionForm.logisticsCompanyName" /></label>
          <label><span>运单号</span><input v-model="actionForm.trackingNo" /></label>
          <button type="button" :disabled="busy === 'logistics'" @click="performAction('logistics')">回填物流</button>
          <label><span>拒绝原因</span><input v-model="actionForm.rejectReason" /></label>
          <button type="button" class="danger" :disabled="busy === 'reject'" @click="performAction('reject')">拒绝接单</button>
        </div>
      </article>
    </section>
  </main>
</template>
