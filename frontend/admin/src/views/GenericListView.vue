<script setup lang="ts">
import DataTable from "@/components/DataTable.vue";
import DetailPanel from "@/components/DetailPanel.vue";
import ActionModal from "@/components/ActionModal.vue";
import { useActionStore } from "@/stores/action";
import { useGenericList } from "@/composables/useGenericList";
import { type RouteAction } from "@/config/actions";

const actionStore = useActionStore();
const {
  routeKey,
  adminRoute,
  columns,
  records,
  detail,
  pageLoading,
  pageError,
  errorTraceId,
  filters,
  hasPageAccess,
  reload,
  selectRecord
} = useGenericList();

function onSelect(record: Record<string, unknown>, id: string | null) {
  selectRecord(record, id);
}

function onAction(action: RouteAction, record: Record<string, unknown>) {
  actionStore.openModal(action.type, record);
}

async function onActionSubmitted() {
  await reload();
}

const filterRouteKeys = ["orders", "refunds", "shipments", "courses", "leads", "students", "inventory", "purchases", "audit"];
function shouldShowKeyword(key: string): boolean {
  return filterRouteKeys.includes(key);
}
</script>

<template>
  <template v-if="hasPageAccess">
    <section class="page-head">
      <h2>{{ adminRoute.title }}</h2>
      <p>数据来自真实后端接口；状态变更由后端状态机裁决。</p>
    </section>

    <section class="filter-row">
      <input
        v-if="shouldShowKeyword(routeKey)"
        v-model="filters.keyword"
        placeholder="搜索..."
        @keyup.enter="reload()"
      />
      <input v-if="routeKey === 'shipments'" v-model="filters.order_no" placeholder="订单号" />
      <input v-if="routeKey === 'shipments'" v-model="filters.tracking_no" placeholder="运单号" />
      <select v-if="routeKey === 'orders'" v-model="filters.payment_status" @change="reload()">
        <option value="">支付状态</option>
        <option value="PENDING">待支付</option>
        <option value="PAID">已支付</option>
        <option value="CLOSED">已关闭</option>
      </select>
      <select v-if="routeKey === 'orders'" v-model="filters.fulfillment_status" @change="reload()">
        <option value="">履约状态</option>
        <option value="NO_SHIPMENT">无需发货</option>
        <option value="PENDING_SHIPMENT">待发货</option>
        <option value="SHIPPED">已发货</option>
        <option value="SIGNED">已签收</option>
      </select>
      <select v-if="routeKey === 'orders'" v-model="filters.refund_status" @change="reload()">
        <option value="">退款状态</option>
        <option value="NONE">无退款</option>
        <option value="REVIEWING">审核中</option>
        <option value="REFUNDED">已退款</option>
      </select>
      <button class="secondary" @click="reload()">刷新</button>
    </section>

    <section v-if="pageError" class="state-panel warning">
      <h2>加载失败</h2>
      <p>{{ pageError }}</p>
      <p v-if="errorTraceId">TraceId: {{ errorTraceId }}</p>
    </section>

    <div class="content-split">
      <DataTable
        :columns="columns"
        :records="records"
        :id-fields="adminRoute.idFields"
        :loading="pageLoading"
        @select="onSelect"
      />
      <DetailPanel :detail="detail" :route="adminRoute" @action="onAction" />
    </div>
  </template>

  <template v-else>
    <section class="state-panel">
      <h2>无访问权限</h2>
      <p>当前角色无权访问此页面，请联系管理员。</p>
    </section>
  </template>

  <ActionModal @submitted="onActionSubmitted" />
</template>
