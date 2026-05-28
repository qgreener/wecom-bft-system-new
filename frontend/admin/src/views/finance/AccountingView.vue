<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { request } from "@/services/http";
import BusinessListPage from "@/views/business/BusinessListPage.vue";
import { formatCent, formatDateTime, formatNumber } from "@/utils/format";

type IncomeRow = { order_id: number; order_no: string; student_id: number; paid_amount_cent: number; paid_at: string };
type RefundRow = { refund_id: number; refund_no: string; order_id: number; apply_amount_cent: number; approved_amount_cent: number; status: string; refund_reason: string; refunded_at?: string; created_at: string };
type PurchaseRow = { purchase_id: number; purchase_no: string; supplier_id?: number; total_amount_cent: number; purchase_status: string; input_invoice_status: string; received_at?: string; created_at: string };
type PendingInvoiceRow = { invoice_id: number; invoice_apply_no: string; order_id: number; order_no: string; title_name: string; tax_no?: string; email?: string; invoice_amount_cent: number; status: string; created_at: string };
type IssuedInvoiceRow = { invoice_id: number; invoice_apply_no: string; invoice_no: string; order_id: number; order_no: string; title_name: string; tax_no?: string; email?: string; invoice_amount_cent: number; tax_amount_cent?: number; issued_at: string };
type TaxBurden = {
  related_month: string;
  revenue_cent: number;
  output_tax_cent: number;
  input_tax_cent: number;
  payable_tax_cent: number;
  burden_rate_percent: number;
};
type Tabs = {
  income: IncomeRow[];
  refunds: RefundRow[];
  purchases: PurchaseRow[];
  pending_invoices: PendingInvoiceRow[];
  issued_invoices: IssuedInvoiceRow[];
  tax_burden: TaxBurden;
};

const TAB_LIST = [
  { key: "summary", label: "月度快报" },
  { key: "income", label: "收入明细" },
  { key: "refunds", label: "退款明细" },
  { key: "purchases", label: "采购支出" },
  { key: "pending", label: "待开票" },
  { key: "issued", label: "开票汇总" },
  { key: "tax", label: "销项税 / 税负" },
  { key: "materials", label: "材料申请" }
] as const;

type TabKey = typeof TAB_LIST[number]["key"];

const activeTab = ref<TabKey>("summary");
const relatedMonth = ref(new Date().toISOString().slice(0, 7));
const loading = ref(false);
const error = ref("");
const tabs = ref<Tabs | null>(null);

const summaryCards = computed(() => {
  const t = tabs.value;
  if (!t) return [];
  const burden = t.tax_burden;
  return [
    { label: "本月营收", value: formatCent(burden.revenue_cent ?? 0) },
    { label: "本月退款单数", value: formatNumber(t.refunds.length ?? 0) },
    { label: "本月采购支出", value: formatCent(t.purchases.reduce((s, r) => s + (r.total_amount_cent ?? 0), 0)) },
    { label: "本月已开票", value: formatCent(t.issued_invoices.reduce((s, r) => s + (r.invoice_amount_cent ?? 0), 0)) },
    { label: "本月销项税", value: formatCent(burden.output_tax_cent ?? 0) },
    { label: "本月进项税(估)", value: formatCent(burden.input_tax_cent ?? 0) },
    { label: "应纳税额(估)", value: formatCent(burden.payable_tax_cent ?? 0) },
    { label: "税负率", value: `${(burden.burden_rate_percent ?? 0).toFixed(2)}%` }
  ];
});

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    tabs.value = await request<Tabs>("/api/admin/accounting-workbench/tabs", {
      query: { related_month: relatedMonth.value }
    });
  } catch (e) {
    error.value = e instanceof Error ? e.message : "工作台数据加载失败";
  } finally {
    loading.value = false;
  }
}

function exportCsv(name: string, headers: string[], rows: (string | number | undefined)[][]): void {
  const lines = [headers.join(",")];
  for (const row of rows) {
    lines.push(row.map((v) => {
      if (v == null) return "";
      const s = String(v).replace(/"/g, "''");
      return s.includes(",") ? `"${s}"` : s;
    }).join(","));
  }
  const blob = new Blob(["﻿" + lines.join("\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${name}-${relatedMonth.value}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

onMounted(() => { void load(); });
watch(relatedMonth, () => { void load(); });
</script>

<template>
  <section class="business-workbench accounting-page">
    <section class="business-command-bar">
      <div class="business-context">
        <span>代账工作台</span>
        <small>月度快报 / 收入退款采购 / 开票销项税</small>
      </div>
      <div class="head-actions">
        <label class="inline-field">
          <span>关联月份</span>
          <input v-model="relatedMonth" type="month" />
        </label>
        <button class="secondary" :disabled="loading" @click="load()">{{ loading ? "刷新中" : "刷新" }}</button>
      </div>
    </section>

    <nav class="tab-nav">
      <button
        v-for="tab in TAB_LIST"
        :key="tab.key"
        type="button"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >{{ tab.label }}</button>
    </nav>

    <section v-if="error" class="state-panel warning">
      <h3>加载失败</h3>
      <p>{{ error }}</p>
    </section>

    <!-- 月度快报 -->
    <section v-if="activeTab === 'summary'" class="summary-grid dashboard-summary">
      <article v-for="card in summaryCards" :key="card.label">
        <p>{{ card.label }}</p>
        <strong>{{ card.value }}</strong>
      </article>
    </section>

    <!-- 收入明细 -->
    <section v-else-if="activeTab === 'income'" class="state-panel">
      <header class="tab-header">
        <h3>收入明细</h3>
        <button class="ghost" :disabled="!tabs?.income.length" @click="exportCsv('income', ['订单ID','订单号','学员ID','实付金额(元)','支付时间'], (tabs?.income ?? []).map(r => [r.order_id, r.order_no, r.student_id, (r.paid_amount_cent/100).toFixed(2), r.paid_at]))">导出 CSV</button>
      </header>
      <table class="mini-table">
        <thead><tr><th>订单号</th><th>学员ID</th><th>实付金额</th><th>支付时间</th></tr></thead>
        <tbody>
          <tr v-for="row in tabs?.income ?? []" :key="row.order_id">
            <td>{{ row.order_no }}</td>
            <td>{{ row.student_id }}</td>
            <td>{{ formatCent(row.paid_amount_cent) }}</td>
            <td>{{ formatDateTime(row.paid_at) }}</td>
          </tr>
          <tr v-if="(tabs?.income ?? []).length === 0"><td colspan="4" class="empty">暂无数据</td></tr>
        </tbody>
      </table>
    </section>

    <!-- 退款明细 -->
    <section v-else-if="activeTab === 'refunds'" class="state-panel">
      <header class="tab-header">
        <h3>退款明细</h3>
        <button class="ghost" :disabled="!tabs?.refunds.length" @click="exportCsv('refunds', ['退款单号','原订单','申请金额(元)','批准金额(元)','状态','原因','退款时间'], (tabs?.refunds ?? []).map(r => [r.refund_no, r.order_id, (r.apply_amount_cent/100).toFixed(2), (r.approved_amount_cent/100).toFixed(2), r.status, r.refund_reason, r.refunded_at]))">导出 CSV</button>
      </header>
      <table class="mini-table">
        <thead><tr><th>退款单号</th><th>原订单</th><th>申请金额</th><th>批准金额</th><th>状态</th><th>原因</th><th>退款时间</th></tr></thead>
        <tbody>
          <tr v-for="row in tabs?.refunds ?? []" :key="row.refund_id">
            <td>{{ row.refund_no }}</td>
            <td>{{ row.order_id }}</td>
            <td>{{ formatCent(row.apply_amount_cent) }}</td>
            <td>{{ formatCent(row.approved_amount_cent) }}</td>
            <td>{{ row.status }}</td>
            <td>{{ row.refund_reason }}</td>
            <td>{{ formatDateTime(row.refunded_at) }}</td>
          </tr>
          <tr v-if="(tabs?.refunds ?? []).length === 0"><td colspan="7" class="empty">暂无数据</td></tr>
        </tbody>
      </table>
    </section>

    <!-- 采购支出 -->
    <section v-else-if="activeTab === 'purchases'" class="state-panel">
      <header class="tab-header">
        <h3>采购支出</h3>
        <button class="ghost" :disabled="!tabs?.purchases.length" @click="exportCsv('purchases', ['采购单号','供货商ID','金额(元)','状态','发票状态','收货时间'], (tabs?.purchases ?? []).map(r => [r.purchase_no, r.supplier_id, (r.total_amount_cent/100).toFixed(2), r.purchase_status, r.input_invoice_status, r.received_at]))">导出 CSV</button>
      </header>
      <table class="mini-table">
        <thead><tr><th>采购单号</th><th>供货商ID</th><th>金额</th><th>状态</th><th>发票状态</th><th>收货时间</th></tr></thead>
        <tbody>
          <tr v-for="row in tabs?.purchases ?? []" :key="row.purchase_id">
            <td>{{ row.purchase_no }}</td>
            <td>{{ row.supplier_id ?? "-" }}</td>
            <td>{{ formatCent(row.total_amount_cent) }}</td>
            <td>{{ row.purchase_status }}</td>
            <td>{{ row.input_invoice_status }}</td>
            <td>{{ formatDateTime(row.received_at) }}</td>
          </tr>
          <tr v-if="(tabs?.purchases ?? []).length === 0"><td colspan="6" class="empty">暂无数据</td></tr>
        </tbody>
      </table>
    </section>

    <!-- 待开票 -->
    <section v-else-if="activeTab === 'pending'" class="state-panel">
      <h3>待开票申请</h3>
      <table class="mini-table">
        <thead><tr><th>申请号</th><th>订单号</th><th>抬头</th><th>税号</th><th>邮箱</th><th>金额</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="row in tabs?.pending_invoices ?? []" :key="row.invoice_id">
            <td>{{ row.invoice_apply_no }}</td>
            <td>{{ row.order_no }}</td>
            <td>{{ row.title_name }}</td>
            <td>{{ row.tax_no ?? "-" }}</td>
            <td>{{ row.email ?? "-" }}</td>
            <td>{{ formatCent(row.invoice_amount_cent) }}</td>
            <td>{{ row.status }}</td>
          </tr>
          <tr v-if="(tabs?.pending_invoices ?? []).length === 0"><td colspan="7" class="empty">暂无待开票申请</td></tr>
        </tbody>
      </table>
    </section>

    <!-- 开票汇总 -->
    <section v-else-if="activeTab === 'issued'" class="state-panel">
      <header class="tab-header">
        <h3>开票汇总</h3>
        <button class="ghost" :disabled="!tabs?.issued_invoices.length" @click="exportCsv('issued-invoices', ['发票号','订单号','抬头','金额(元)','税额(元)','开票时间'], (tabs?.issued_invoices ?? []).map(r => [r.invoice_no, r.order_no, r.title_name, (r.invoice_amount_cent/100).toFixed(2), ((r.tax_amount_cent ?? 0)/100).toFixed(2), r.issued_at]))">导出 CSV</button>
      </header>
      <table class="mini-table">
        <thead><tr><th>发票号</th><th>订单号</th><th>抬头</th><th>金额</th><th>税额</th><th>开票时间</th></tr></thead>
        <tbody>
          <tr v-for="row in tabs?.issued_invoices ?? []" :key="row.invoice_id">
            <td>{{ row.invoice_no }}</td>
            <td>{{ row.order_no }}</td>
            <td>{{ row.title_name }}</td>
            <td>{{ formatCent(row.invoice_amount_cent) }}</td>
            <td>{{ formatCent(row.tax_amount_cent ?? 0) }}</td>
            <td>{{ formatDateTime(row.issued_at) }}</td>
          </tr>
          <tr v-if="(tabs?.issued_invoices ?? []).length === 0"><td colspan="6" class="empty">暂无已开具发票</td></tr>
        </tbody>
      </table>
    </section>

    <!-- 销项税 / 税负 -->
    <section v-else-if="activeTab === 'tax'" class="state-panel">
      <h3>销项税与税负概览（{{ tabs?.tax_burden.related_month ?? relatedMonth }}）</h3>
      <p class="hint">仅作经营参考，不作为正式税务申报依据。</p>
      <dl class="drawer-desc tax-desc">
        <dt>本月营收</dt><dd>{{ formatCent(tabs?.tax_burden.revenue_cent ?? 0) }}</dd>
        <dt>销项税</dt><dd>{{ formatCent(tabs?.tax_burden.output_tax_cent ?? 0) }}</dd>
        <dt>进项税(估算)</dt><dd>{{ formatCent(tabs?.tax_burden.input_tax_cent ?? 0) }}</dd>
        <dt>应纳税额(估算)</dt><dd>{{ formatCent(tabs?.tax_burden.payable_tax_cent ?? 0) }}</dd>
        <dt>税负率</dt><dd>{{ (tabs?.tax_burden.burden_rate_percent ?? 0).toFixed(2) }}%</dd>
      </dl>
    </section>

    <!-- 材料申请：复用现有 BusinessListPage（accounting 路由的代账材料） -->
    <section v-else-if="activeTab === 'materials'">
      <BusinessListPage />
    </section>
  </section>
</template>

<style scoped>
.accounting-page .inline-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-right: 12px;
  font-size: 13px;
  color: var(--muted);
}
.accounting-page .inline-field input {
  padding: 4px 8px;
  border: 1px solid var(--line);
}
.tab-nav {
  display: flex;
  gap: 4px;
  margin: 16px 0 12px;
  border-bottom: 1px solid var(--line);
}
.tab-nav button {
  background: transparent;
  border: none;
  padding: 10px 16px;
  font-size: 14px;
  color: var(--muted);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.tab-nav button.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
  font-weight: 600;
}
.tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.empty {
  text-align: center;
  color: var(--muted);
  padding: 16px;
}
.tax-desc {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 8px 16px;
  margin: 16px 0;
}
.tax-desc dt {
  color: var(--muted);
}
.tax-desc dd {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}
.hint {
  color: var(--muted);
  font-size: 12px;
}
</style>
