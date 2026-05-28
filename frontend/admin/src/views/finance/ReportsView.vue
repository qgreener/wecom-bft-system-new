<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import * as echarts from "echarts";
import { request } from "@/services/http";
import { formatCent, formatNumber } from "@/utils/format";

type SummaryResponse = {
  related_month?: string;
  income_amount_cent?: number;
  refund_amount_cent?: number;
  purchase_amount_cent?: number;
  issued_invoice_amount_cent?: number;
  red_reversed_invoice_amount_cent?: number;
  reconciliation_diff_count?: number;
  material_status_counts?: Record<string, number>;
};

type RevenuePoint = {
  day?: string;
  order_count?: number;
  revenue_cent?: number;
};

type Funnel = {
  total_leads?: number;
  contacted_leads?: number;
  converted_leads?: number;
  total_orders?: number;
  paid_orders?: number;
};

type Sales = {
  course_id?: number;
  course_title?: string;
  order_count?: number;
  revenue_cent?: number;
};

type Risks = {
  pending_invoice_overdue?: number;
  purchase_without_input_invoice?: number;
  reconciliation_diff?: number;
  refund_failed?: number;
};

type Overview = {
  revenue_trend: RevenuePoint[];
  conversion_funnel: Funnel;
  sales_ranking: Sales[];
  risk_alerts: Risks;
};

const loading = ref(false);
const error = ref("");
const summary = ref<SummaryResponse | null>(null);
const overview = ref<Overview | null>(null);
const relatedMonth = ref(new Date().toISOString().slice(0, 7));
const trendDays = ref(7);

const trendChartEl = ref<HTMLDivElement | null>(null);
const funnelChartEl = ref<HTMLDivElement | null>(null);
const salesChartEl = ref<HTMLDivElement | null>(null);

let trendChart: echarts.ECharts | null = null;
let funnelChart: echarts.ECharts | null = null;
let salesChart: echarts.ECharts | null = null;

const metricCards = computed(() => {
  const data = summary.value ?? {};
  const materialCounts = data.material_status_counts ?? {};
  return [
    { label: "本月收入", value: formatCent(data.income_amount_cent ?? 0) },
    { label: "本月退款", value: formatCent(data.refund_amount_cent ?? 0) },
    { label: "本月采购支出", value: formatCent(data.purchase_amount_cent ?? 0) },
    { label: "本月已开票", value: formatCent(data.issued_invoice_amount_cent ?? 0) },
    { label: "本月红冲", value: formatCent(data.red_reversed_invoice_amount_cent ?? 0) },
    { label: "对账差异", value: formatNumber(data.reconciliation_diff_count ?? 0) },
    { label: "待补充材料", value: formatNumber(materialCounts.PENDING_SUPPLEMENT ?? 0) },
    { label: "已确认材料", value: formatNumber(materialCounts.CONFIRMED ?? 0) }
  ];
});

const riskCards = computed(() => {
  const r = overview.value?.risk_alerts ?? {};
  return [
    { label: "待开票超期", value: r.pending_invoice_overdue ?? 0, tone: "warning" },
    { label: "采购无进项票", value: r.purchase_without_input_invoice ?? 0, tone: "warning" },
    { label: "对账差异未核对", value: r.reconciliation_diff ?? 0, tone: "warning" },
    { label: "退款失败/待人工", value: r.refund_failed ?? 0, tone: "danger" }
  ];
});

const salesTable = computed(() => overview.value?.sales_ranking ?? []);

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const [s, o] = await Promise.all([
      request<SummaryResponse>("/api/admin/accounting-workbench/summary", {
        query: { related_month: relatedMonth.value }
      }),
      request<Overview>("/api/admin/reports/overview", { query: { days: String(trendDays.value) } })
    ]);
    summary.value = s;
    overview.value = o;
    renderCharts();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "报表数据加载失败";
  } finally {
    loading.value = false;
  }
}

function renderCharts(): void {
  renderTrendChart();
  renderFunnelChart();
  renderSalesChart();
}

function renderTrendChart(): void {
  if (!trendChartEl.value || !overview.value) return;
  if (!trendChart) trendChart = echarts.init(trendChartEl.value);
  // 按 trendDays 配置生成固定 X 轴（最近 N 天），缺数据的日子用 0 填充，避免出现单列空图
  const days: string[] = [];
  for (let i = trendDays.value - 1; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    days.push(d.toISOString().slice(5, 10));
  }
  const dayMap = new Map<string, { count: number; revenue: number }>();
  for (const p of overview.value.revenue_trend ?? []) {
    const key = String(p.day ?? "").slice(-5);
    if (!key) continue;
    dayMap.set(key, {
      count: Number(p.order_count ?? 0),
      revenue: Number(p.revenue_cent ?? 0) / 100
    });
  }
  const counts = days.map((d) => dayMap.get(d)?.count ?? 0);
  const revenues = days.map((d) => dayMap.get(d)?.revenue ?? 0);
  trendChart.setOption({
    tooltip: { trigger: "axis" },
    legend: { data: ["订单数", "营收(元)"], right: 10, top: 6 },
    grid: { left: 56, right: 60, top: 40, bottom: 28 },
    xAxis: { type: "category", data: days, axisTick: { show: false } },
    yAxis: [
      { type: "value", name: "订单数", position: "left", minInterval: 1 },
      { type: "value", name: "营收(元)", position: "right" }
    ],
    series: [
      {
        name: "订单数",
        type: "bar",
        data: counts,
        barMaxWidth: 28,
        itemStyle: { color: "#1f4e5f", borderRadius: [4, 4, 0, 0] }
      },
      {
        name: "营收(元)",
        type: "line",
        yAxisIndex: 1,
        data: revenues,
        smooth: true,
        symbolSize: 7,
        itemStyle: { color: "#0f766e" }
      }
    ]
  });
  trendChart.resize();
}

function renderFunnelChart(): void {
  if (!funnelChartEl.value || !overview.value) return;
  if (!funnelChart) funnelChart = echarts.init(funnelChartEl.value);
  const f = overview.value.conversion_funnel ?? {};
  funnelChart.setOption({
    tooltip: { trigger: "item", formatter: "{b}: {c}" },
    series: [{
      name: "转化漏斗",
      type: "funnel",
      top: 10,
      bottom: 10,
      left: "10%",
      width: "80%",
      sort: "descending",
      gap: 2,
      label: { show: true, position: "inside" },
      labelLine: { show: false },
      data: [
        { value: Number(f.total_leads ?? 0), name: "线索总数" },
        { value: Number(f.contacted_leads ?? 0), name: "已联系" },
        { value: Number(f.converted_leads ?? 0), name: "已转化" },
        { value: Number(f.total_orders ?? 0), name: "已下单" },
        { value: Number(f.paid_orders ?? 0), name: "已支付" }
      ]
    }]
  });
  funnelChart.resize();
}

function renderSalesChart(): void {
  if (!salesChartEl.value || !overview.value) return;
  if (!salesChart) salesChart = echarts.init(salesChartEl.value);
  const ranking = overview.value.sales_ranking ?? [];
  const names = ranking.map((r) => r.course_title ?? `课程${r.course_id}`);
  const revenues = ranking.map((r) => Number(r.revenue_cent ?? 0) / 100);
  salesChart.setOption({
    tooltip: { trigger: "axis" },
    grid: { left: 120, right: 30, top: 16, bottom: 40 },
    xAxis: { type: "value", name: "营收(元)" },
    yAxis: { type: "category", data: names, inverse: true },
    series: [{
      name: "营收(元)",
      type: "bar",
      data: revenues,
      itemStyle: { color: "#1f4e5f" },
      label: { show: true, position: "right" }
    }]
  });
  salesChart.resize();
}

function exportSalesCsv(): void {
  const rows = salesTable.value;
  if (rows.length === 0) return;
  const lines = ["课程ID,课程名,订单数,营收(元)"];
  for (const r of rows) {
    const revenue = ((r.revenue_cent ?? 0) / 100).toFixed(2);
    const title = (r.course_title ?? "").replace(/"/g, "''");
    lines.push(`${r.course_id ?? ""},"${title}",${r.order_count ?? 0},${revenue}`);
  }
  const blob = new Blob(["﻿" + lines.join("\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `sales-ranking-${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

onMounted(() => {
  void load();
  window.addEventListener("resize", onResize);
});

watch([trendDays, relatedMonth], () => { void load(); });

function onResize(): void {
  trendChart?.resize();
  funnelChart?.resize();
  salesChart?.resize();
}
</script>

<template>
  <section class="business-workbench reports-page">
    <section class="business-command-bar">
      <div class="business-context">
        <span>数据报表</span>
        <small>经营指标 / 财税摘要 / 转化漏斗 / 销量排行</small>
      </div>
      <div class="head-actions">
        <label class="inline-field">
          <span>关联月份</span>
          <input v-model="relatedMonth" type="month" />
        </label>
        <label class="inline-field">
          <span>趋势天数</span>
          <select v-model.number="trendDays">
            <option :value="7">近 7 日</option>
            <option :value="14">近 14 日</option>
            <option :value="30">近 30 日</option>
          </select>
        </label>
        <button class="secondary" :disabled="loading" @click="load()">{{ loading ? "刷新中" : "刷新" }}</button>
        <button class="ghost" :disabled="salesTable.length === 0" @click="exportSalesCsv()">导出销量 CSV</button>
      </div>
    </section>

    <section class="summary-grid dashboard-summary">
      <article v-for="card in metricCards" :key="card.label">
        <p>{{ card.label }}</p>
        <strong>{{ card.value }}</strong>
      </article>
    </section>

    <section class="risk-grid">
      <article v-for="card in riskCards" :key="card.label" :class="['risk-card', card.tone]">
        <p>{{ card.label }}</p>
        <strong>{{ card.value }}</strong>
      </article>
    </section>

    <section class="chart-grid">
      <article class="chart-card tall">
        <header><h3>近期营收趋势</h3><small>近 {{ trendDays }} 日</small></header>
        <div ref="trendChartEl" class="chart-block-canvas"></div>
      </article>
      <article class="chart-card">
        <header><h3>线索 → 订单 转化漏斗</h3></header>
        <div ref="funnelChartEl" class="chart-block-canvas"></div>
      </article>
      <article class="chart-card tall">
        <header><h3>课程销量排行（按营收）</h3></header>
        <div ref="salesChartEl" class="chart-block-canvas"></div>
      </article>
    </section>

    <section class="state-panel">
      <h3>课程销量明细</h3>
      <table class="mini-table">
        <thead>
          <tr><th>课程名</th><th>订单数</th><th>营收</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in salesTable" :key="row.course_id">
            <td>{{ row.course_title || `课程${row.course_id}` }}</td>
            <td>{{ row.order_count ?? 0 }}</td>
            <td>{{ formatCent(row.revenue_cent ?? 0) }}</td>
          </tr>
          <tr v-if="salesTable.length === 0"><td colspan="3" class="empty">暂无销量数据</td></tr>
        </tbody>
      </table>
    </section>

    <section v-if="error" class="state-panel warning">
      <h2>加载失败</h2>
      <p>{{ error }}</p>
    </section>
  </section>
</template>

<style scoped>
.reports-page .inline-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-right: 12px;
  font-size: 13px;
  color: var(--muted);
}
.reports-page .inline-field input,
.reports-page .inline-field select {
  padding: 4px 8px;
  border: 1px solid var(--line);
}
.risk-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 16px;
}
.risk-card {
  border: 1px solid var(--line);
  padding: 14px 16px;
  background: #fff;
}
.risk-card.warning {
  border-left: 3px solid var(--warning);
}
.risk-card.danger {
  border-left: 3px solid var(--danger);
}
.risk-card p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}
.risk-card strong {
  font-size: 22px;
  display: block;
  margin-top: 6px;
}
.chart-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
  margin-top: 20px;
}
.chart-card {
  background: #fff;
  border: 1px solid var(--line);
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
}
.chart-card header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
}
.chart-card header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}
.chart-card header small {
  color: var(--muted);
  font-size: 12px;
}
.chart-block-canvas {
  flex: 1;
  min-height: 220px;
}
.chart-card.tall {
  min-height: 320px;
}
.chart-card.tall .chart-block-canvas {
  min-height: 280px;
}
.chart-grid > .chart-card.tall:last-child {
  grid-column: span 2;
  min-height: 340px;
}
.empty {
  text-align: center;
  color: var(--muted);
  padding: 16px;
}
</style>
