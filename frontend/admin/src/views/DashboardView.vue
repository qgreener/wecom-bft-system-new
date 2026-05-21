<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useDashboardStore } from "@/stores/dashboard";
import { formatCent, formatDateTime, formatNumber } from "@/utils/format";
import { columnsForRoute } from "@/config/columns";
import DataTable from "@/components/DataTable.vue";
import { statusClass, statusLabel } from "@/config/status";

const store = useDashboardStore();
const router = useRouter();
const { summary, todos, recentOrders, loading, error } = storeToRefs(store);
const activeRole = ref("SUPER_ADMIN");

const orderColumns = computed(() => columnsForRoute("orders"));

const dashboardCards = computed(() => {
  const t = todos.value ?? {};
  return [
    { label: "今日新增线索", value: "-", path: "/leads" },
    { label: "今日新增订单", value: todayOrderCount.value, path: "/orders" },
    { label: "待处理退款", value: formatNumber(t.pending_refund_review_count), path: "/refunds" },
    { label: "待发货订单", value: formatNumber(t.pending_shipment_count), path: "/shipments" },
    { label: "待开票申请", value: formatNumber(t.pending_invoice_issue_count), path: "/invoices" },
    { label: "库存预警数", value: "-", path: "/inventory" }
  ];
});

const todayOrderCount = computed(() => {
  const today = new Date().toISOString().slice(0, 10);
  return String(recentOrders.value.filter((order) => String(order.created_at ?? "").startsWith(today)).length || "-");
});

const orderTrend = computed(() => buildTrend("count"));
const revenueTrend = computed(() => buildTrend("amount"));

const roleTabs = [
  { key: "SUPER_ADMIN", label: "超级管理员" },
  { key: "OPS", label: "运营" },
  { key: "EDU_ADMIN", label: "教务/讲师" },
  { key: "WAREHOUSE", label: "仓管" },
  { key: "SERVICE", label: "客服" },
  { key: "ACCOUNTING", label: "代账人员" }
];

const activeTodoGroups = computed(() => {
  const t = todos.value ?? {};
  const groups = [
    {
      roles: ["SUPER_ADMIN", "SERVICE"],
      label: "待审核退款",
      count: t.pending_refund_review_count,
      samples: t.pending_refund_review_samples,
      path: "/refunds"
    },
    {
      roles: ["SUPER_ADMIN", "WAREHOUSE"],
      label: "待发货订单",
      count: t.pending_shipment_count,
      samples: t.pending_shipment_samples,
      path: "/shipments"
    },
    {
      roles: ["SUPER_ADMIN", "SERVICE", "ACCOUNTING"],
      label: "待开票申请",
      count: t.pending_invoice_issue_count,
      samples: t.pending_invoice_issue_samples,
      path: "/invoices"
    },
    {
      roles: ["SUPER_ADMIN", "ACCOUNTING"],
      label: "待处理红冲",
      count: t.pending_red_reverse_count,
      samples: t.pending_red_reverse_samples,
      path: "/invoices"
    },
    {
      roles: ["SUPER_ADMIN", "ACCOUNTING"],
      label: "对账差异",
      count: t.pending_reconciliation_diff_count,
      samples: t.pending_reconciliation_diff_samples,
      path: "/reconciliation"
    },
    {
      roles: ["SUPER_ADMIN", "WAREHOUSE"],
      label: "采购审批",
      count: t.pending_purchase_approval_count,
      samples: t.pending_purchase_approval_samples,
      path: "/purchases"
    },
    {
      roles: ["OPS"],
      label: "线索跟进",
      count: null,
      samples: [],
      path: "/leads"
    },
    {
      roles: ["EDU_ADMIN"],
      label: "课程内容维护",
      count: null,
      samples: [],
      path: "/courses"
    }
  ];
  return groups.filter((group) => group.roles.includes(activeRole.value));
});

function buildTrend(mode: "count" | "amount") {
  const days = Array.from({ length: 7 }, (_, index) => {
    const date = new Date();
    date.setDate(date.getDate() - (6 - index));
    return date.toISOString().slice(5, 10);
  });
  const values = days.map((day) => {
    const rows = recentOrders.value.filter((order) => String(order.created_at ?? "").slice(5, 10) === day);
    if (mode === "count") return rows.length;
    return rows.reduce((sum, order) => {
      const amount = Number(order.paid_amount_cent ?? order.payable_amount_cent ?? 0);
      return sum + (Number.isFinite(amount) ? amount : 0);
    }, 0);
  });
  const max = Math.max(...values, 1);
  return days.map((day, index) => ({
    day,
    value: values[index],
    height: `${Math.max(8, (values[index] / max) * 100)}%`
  }));
}

function navigate(path: string): void {
  router.push(path);
}

function todoSamples(samples: unknown): Record<string, unknown>[] {
  return Array.isArray(samples) ? samples.slice(0, 4) as Record<string, unknown>[] : [];
}

onMounted(() => {
  store.load();
});
</script>

<template>
  <section class="business-command-bar">
    <div class="business-context">
      <span>今日待办</span>
      <small>按角色流程处理</small>
    </div>
    <button class="secondary" :disabled="loading" @click="store.load()">{{ loading ? "刷新中..." : "刷新" }}</button>
  </section>

  <section class="summary-grid dashboard-summary">
    <article v-for="card in dashboardCards" :key="card.label" @click="navigate(card.path)">
      <p>{{ card.label }}</p>
      <strong>{{ card.value }}</strong>
    </article>
  </section>

  <section v-if="error" class="state-panel warning">
    <h2>加载失败</h2>
    <p>{{ error }}</p>
  </section>

  <section class="dashboard-grid">
    <article class="chart-panel">
      <header>
        <h3>近7日订单趋势</h3>
        <span>按最近订单聚合</span>
      </header>
      <div class="bars">
        <div v-for="bar in orderTrend" :key="bar.day" class="bar-col">
          <i :style="{ height: bar.height }"></i>
          <small>{{ bar.day }}</small>
          <strong>{{ bar.value }}</strong>
        </div>
      </div>
    </article>

    <article class="chart-panel">
      <header>
        <h3>近7日营收</h3>
        <span>单位：元</span>
      </header>
      <div class="bars revenue">
        <div v-for="bar in revenueTrend" :key="bar.day" class="bar-col">
          <i :style="{ height: bar.height }"></i>
          <small>{{ bar.day }}</small>
          <strong>{{ formatCent(bar.value).replace('.00', '') }}</strong>
        </div>
      </div>
    </article>
  </section>

  <section class="workflow-board">
    <header>
      <h3>流程看板</h3>
      <div class="segmented">
        <button
          v-for="role in roleTabs"
          :key="role.key"
          type="button"
          :class="{ active: activeRole === role.key }"
          @click="activeRole = role.key"
        >{{ role.label }}</button>
      </div>
    </header>
    <div class="todo-grid">
      <article v-for="group in activeTodoGroups" :key="group.label" class="todo-card" @click="navigate(group.path)">
        <div>
          <p>{{ group.label }}</p>
          <strong>{{ group.count === null || group.count === undefined ? "-" : formatNumber(group.count) }}</strong>
        </div>
        <ul>
          <li v-for="sample in todoSamples(group.samples)" :key="String(sample.entity_id ?? sample.entity_no)">
            <span>{{ sample.entity_no }}</span>
            <em>{{ sample.summary }}</em>
            <b v-if="sample.status" class="status-tag" :class="statusClass(sample.status as string)">
              {{ statusLabel(sample.status as string) }}
            </b>
            <small>{{ formatDateTime(sample.occurred_at) }}</small>
          </li>
          <li v-if="todoSamples(group.samples).length === 0" class="muted-line">暂无样例</li>
        </ul>
      </article>
    </div>
  </section>

  <section class="list-section">
    <div class="section-title">
      <h3>最近订单</h3>
    </div>
    <DataTable
      :columns="orderColumns"
      :records="recentOrders"
      :id-fields="['order_id', 'id']"
      :loading="loading"
      empty-message="暂无订单数据"
      @select="navigate('/orders')"
    />
  </section>
</template>
