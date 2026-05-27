<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
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

const loading = ref(false);
const error = ref("");
const summary = ref<SummaryResponse | null>(null);
const relatedMonth = ref(new Date().toISOString().slice(0, 7));

const metricCards = computed(() => {
  const data = summary.value ?? {};
  const materialCounts = data.material_status_counts ?? {};
  return [
    { label: "收入", value: formatCent(data.income_amount_cent ?? 0) },
    { label: "退款", value: formatCent(data.refund_amount_cent ?? 0) },
    { label: "采购", value: formatCent(data.purchase_amount_cent ?? 0) },
    { label: "已开票", value: formatCent(data.issued_invoice_amount_cent ?? 0) },
    { label: "红冲", value: formatCent(data.red_reversed_invoice_amount_cent ?? 0) },
    { label: "对账差异", value: formatNumber(data.reconciliation_diff_count ?? 0) },
    { label: "待补充材料", value: formatNumber(materialCounts.PENDING_SUPPLEMENT ?? 0) },
    { label: "已确认材料", value: formatNumber(materialCounts.CONFIRMED ?? 0) }
  ];
});

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    summary.value = await request<SummaryResponse>("/api/admin/accounting-workbench/summary", {
      query: { related_month: relatedMonth.value }
    });
  } catch (e) {
    error.value = e instanceof Error ? e.message : "财税摘要加载失败";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="business-workbench">
    <section class="business-command-bar">
      <div class="business-context">
        <span>数据报表</span>
        <small>财税工作台摘要</small>
      </div>
      <div class="head-actions">
        <button class="secondary" :disabled="loading" @click="load()">{{ loading ? "刷新中" : "刷新" }}</button>
      </div>
    </section>

    <section class="filter-panel">
      <label>
        <span>关联月份</span>
        <input v-model="relatedMonth" type="month" @change="load()" />
      </label>
    </section>

    <section class="summary-grid dashboard-summary">
      <article v-for="card in metricCards" :key="card.label">
        <p>{{ card.label }}</p>
        <strong>{{ card.value }}</strong>
      </article>
    </section>

    <section v-if="summary" class="state-panel">
      <h2>摘要说明</h2>
      <p>当前月份 {{ summary.related_month ?? relatedMonth }} 已聚合收入、退款、采购、发票、红冲和对账差异。</p>
    </section>

    <section v-if="error" class="state-panel warning">
      <h2>加载失败</h2>
      <p>{{ error }}</p>
    </section>
  </section>
</template>
