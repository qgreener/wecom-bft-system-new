<script setup lang="ts">
import { computed, onMounted } from "vue";
import { storeToRefs } from "pinia";
import { useDashboardStore } from "@/stores/dashboard";
import { formatCent } from "@/utils/format";
import { columnsForRoute } from "@/config/columns";
import DataTable from "@/components/DataTable.vue";

const store = useDashboardStore();
const { summary, recentOrders, loading, error } = storeToRefs(store);

const orderColumns = computed(() => columnsForRoute("orders"));

const financeCards = computed(() => {
  const f = summary.value;
  if (!f) return [];
  return [
    { label: "本月收入", value: formatCent(f.monthly_revenue_cent as number | null | undefined) },
    { label: "本月订单", value: String(f.monthly_order_count ?? "-") },
    { label: "待退款", value: String(f.pending_refund_count ?? "-") },
    { label: "待发货", value: String(f.pending_shipment_count ?? "-") }
  ];
});

onMounted(() => {
  store.load();
});
</script>

<template>
  <section class="page-head">
    <h2>首页</h2>
    <p>数据来自真实后端接口；状态变更由后端状态机裁决。</p>
  </section>

  <section v-if="financeCards.length" class="summary-grid">
    <article v-for="card in financeCards" :key="card.label">
      <p>{{ card.label }}</p>
      <strong>{{ card.value }}</strong>
    </article>
  </section>

  <section v-if="error" class="state-panel warning">
    <h2>加载失败</h2>
    <p>{{ error }}</p>
  </section>

  <DataTable
    :columns="orderColumns"
    :records="recentOrders"
    :id-fields="['order_id', 'id']"
    :loading="loading"
    empty-message="暂无订单数据"
  />
</template>
