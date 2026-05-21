<script setup lang="ts">
import { computed } from "vue";

const metricCards = computed(() => [
  { label: "总营收", value: "-" },
  { label: "总订单数", value: "-" },
  { label: "退款率", value: "-" },
  { label: "新增学员", value: "-" },
  { label: "待开票金额", value: "-" },
  { label: "对账差异", value: "-" }
]);
</script>

<template>
  <section class="business-workbench">
    <section class="business-command-bar">
      <div class="business-context">
        <span>数据报表</span>
        <small>报表接口待联调</small>
      </div>
      <div class="head-actions">
        <button class="secondary" disabled>导出 Excel</button>
      </div>
    </section>

    <section class="filter-panel">
      <label>
        <span>时间范围</span>
        <select>
          <option>近 7 日</option>
          <option>近 30 日</option>
        </select>
      </label>
    </section>

    <section class="summary-grid dashboard-summary">
      <article v-for="card in metricCards" :key="card.label">
        <p>{{ card.label }}</p>
        <strong>{{ card.value }}</strong>
      </article>
    </section>

    <section class="state-panel warning">
      <h2>接口未完成</h2>
      <p>经营指标、转化漏斗、财税预测、课程销量和导出接口尚未在后端提供。</p>
    </section>
  </section>
</template>
