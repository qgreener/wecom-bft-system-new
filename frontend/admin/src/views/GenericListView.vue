<script setup lang="ts">
import { computed } from "vue";
import DataTable from "@/components/DataTable.vue";
import DetailPanel from "@/components/DetailPanel.vue";
import ActionModal from "@/components/ActionModal.vue";
import { useActionStore } from "@/stores/action";
import { useGenericList } from "@/composables/useGenericList";
import { NO_ID_ACTIONS, type RouteAction } from "@/config/actions";

const actionStore = useActionStore();
const {
  routeKey,
  adminRoute,
  columns,
  records,
  detail,
  pageLoading,
  detailLoading,
  pageError,
  detailError,
  errorTraceId,
  filters,
  actions,
  hasPageAccess,
  reload,
  clearAndReload,
  closeDetail,
  selectRecord
} = useGenericList();

const pageActions = computed(() => actions.value.filter((action) => NO_ID_ACTIONS.has(action.type)));

async function onSelect(record: Record<string, unknown>, id: string | null) {
  await selectRecord(record, id);
}

function onAction(action: RouteAction, record: Record<string, unknown> | null) {
  actionStore.openModal(action.type, record);
}

async function onActionSubmitted() {
  await reload();
}

const filterRouteKeys = [
  "orders",
  "refunds",
  "shipments",
  "invoices",
  "courses",
  "leads",
  "students",
  "inventory",
  "purchases",
  "audit",
  "suppliers",
  "taxRules",
  "payments",
  "entitlements",
  "accounting"
];
function shouldShowKeyword(key: string): boolean {
  return filterRouteKeys.includes(key);
}

const statusOptions = computed(() => {
  const map: Record<string, { label: string; value: string }[]> = {
    refunds: [
      { label: "审核中", value: "REVIEWING" },
      { label: "已拒绝", value: "REJECTED" },
      { label: "退款处理中", value: "PROCESSING" },
      { label: "待人工退款", value: "MANUAL_REQUIRED" },
      { label: "退款失败", value: "FAILED" },
      { label: "已退款", value: "REFUNDED" }
    ],
    shipments: [
      { label: "待发货", value: "PENDING_SHIPMENT" },
      { label: "已发货", value: "SHIPPED" },
      { label: "已签收", value: "SIGNED" }
    ],
    invoices: [
      { label: "已申请", value: "APPLIED" },
      { label: "待开具", value: "TO_BE_ISSUED" },
      { label: "已开具", value: "ISSUED" },
      { label: "已红冲", value: "RED_REVERSED" }
    ],
    courses: [
      { label: "已上架", value: "ON_SHELF" },
      { label: "已下架", value: "OFF_SHELF" },
      { label: "审批中", value: "PENDING_REVIEW" },
      { label: "待删除", value: "DELETE_PENDING" }
    ],
    leads: [
      { label: "待跟进", value: "PENDING_FOLLOW" },
      { label: "已联系", value: "CONTACTED" },
      { label: "已转化", value: "CONVERTED" },
      { label: "已放弃", value: "ABANDONED" }
    ],
    purchases: [
      { label: "审批中", value: "APPROVING" },
      { label: "审批拒绝", value: "APPROVAL_REJECTED" },
      { label: "待确认", value: "WAIT_CONFIRM" },
      { label: "已确认", value: "CONFIRMED" },
      { label: "已发货", value: "SHIPPED" },
      { label: "已完成", value: "COMPLETED" },
      { label: "已取消", value: "CANCELED" }
    ],
    inventory: [
      { label: "启用", value: "ENABLED" },
      { label: "停用", value: "DISABLED" }
    ],
    accounting: [
      { label: "待补充", value: "PENDING_SUPPLEMENT" },
      { label: "已上传", value: "UPLOADED" },
      { label: "已确认", value: "CONFIRMED" },
      { label: "已关闭", value: "CLOSED" }
    ],
    suppliers: [
      { label: "启用", value: "ENABLED" },
      { label: "停用", value: "DISABLED" }
    ],
    taxRules: [
      { label: "启用", value: "ENABLED" },
      { label: "停用", value: "DISABLED" }
    ],
    entitlements: [
      { label: "生效", value: "ACTIVE" },
      { label: "冻结", value: "FROZEN" },
      { label: "撤销", value: "REVOKED" }
    ]
  };
  return map[routeKey.value] ?? [];
});
</script>

<template>
  <template v-if="hasPageAccess">
    <section class="page-head">
      <div>
        <h2>{{ adminRoute.title }}</h2>
        <p>接口：{{ adminRoute.listPath ?? "未实现" }}</p>
      </div>
      <div v-if="pageActions.length" class="head-actions">
        <button
          v-for="action in pageActions"
          :key="action.type"
          type="button"
          class="primary"
          @click="onAction(action, null)"
        >{{ action.label }}</button>
      </div>
    </section>

    <section class="filter-row">
      <input
        v-if="shouldShowKeyword(routeKey)"
        v-model="filters.keyword"
        placeholder="搜索..."
        @keyup.enter="reload()"
      />
      <input v-if="['shipments', 'refunds', 'invoices'].includes(routeKey)" v-model="filters.order_no" placeholder="订单号" />
      <input v-if="routeKey === 'shipments'" v-model="filters.tracking_no" placeholder="运单号" />
      <select v-if="routeKey === 'shipments'" v-model="filters.exception_flag" @change="reload()">
        <option value="">异常状态</option>
        <option value="true">仅异常</option>
        <option value="false">仅正常</option>
      </select>
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
      <select v-if="routeKey === 'orders'" v-model="filters.invoice_status" @change="reload()">
        <option value="">开票状态</option>
        <option value="NOT_APPLIED">未申请</option>
        <option value="APPLIED">已申请</option>
        <option value="TO_BE_ISSUED">待开具</option>
        <option value="ISSUED">已开具</option>
        <option value="RED_REVERSED">已红冲</option>
      </select>
      <input v-if="routeKey === 'orders'" v-model="filters.paid_at_start" type="datetime-local" title="支付开始时间" />
      <input v-if="routeKey === 'orders'" v-model="filters.paid_at_end" type="datetime-local" title="支付结束时间" />
      <select v-if="statusOptions.length" v-model="filters.status" @change="reload()">
        <option value="">业务状态</option>
        <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
      <select v-if="routeKey === 'courses'" v-model="filters.course_type" @change="reload()">
        <option value="">课程类型</option>
        <option value="LIVE">直播课</option>
        <option value="RECORDED">录制课</option>
      </select>
      <input v-if="routeKey === 'leads'" v-model="filters.mobile" placeholder="手机号" />
      <select v-if="routeKey === 'leads'" v-model="filters.source_channel" @change="reload()">
        <option value="">来源入口</option>
        <option value="WECOM_SIDEBAR">企微侧边栏</option>
        <option value="PROMOTION">推广码落地页</option>
        <option value="OTHER">其他</option>
      </select>
      <input v-if="routeKey === 'inventory'" v-model="filters.category_code" placeholder="商品类目" />
      <select v-if="routeKey === 'inventory'" v-model="filters.warning_only" @change="reload()">
        <option value="">库存预警</option>
        <option value="true">仅低库存</option>
      </select>
      <select v-if="routeKey === 'suppliers'" v-model="filters.access_status" @change="reload()">
        <option value="">接入状态</option>
        <option value="ACTIVE">合作中</option>
        <option value="DISABLED">已停用</option>
      </select>
      <input v-if="routeKey === 'payments'" v-model="filters.merchant_order_no" placeholder="商户订单号" />
      <input v-if="routeKey === 'payments'" v-model="filters.order_id" placeholder="订单 ID" />
      <select v-if="routeKey === 'payments'" v-model="filters.payment_result" @change="reload()">
        <option value="">支付结果</option>
        <option value="SUCCESS">成功</option>
        <option value="PROCESSING">处理中</option>
        <option value="FAILED">失败</option>
      </select>
      <input v-if="routeKey === 'entitlements'" v-model="filters.student_id" placeholder="学员 ID" />
      <input v-if="routeKey === 'entitlements'" v-model="filters.course_id" placeholder="课程 ID" />
      <input v-if="routeKey === 'accounting' || routeKey === 'reconciliation'" v-model="filters.related_month" type="month" />
      <select v-if="routeKey === 'settings'" v-model="filters.config_group" @change="reload()">
        <option value="PURCHASE">采购配置</option>
        <option value="PAYMENT">支付配置</option>
        <option value="LOGISTICS">物流配置</option>
        <option value="INVOICE">开票配置</option>
        <option value="TAX">税务配置</option>
        <option value="MOCK">演示配置</option>
      </select>
      <input v-if="routeKey === 'audit'" v-model="filters.trace_id" placeholder="TraceId" />
      <input v-if="routeKey === 'audit'" v-model="filters.operation_module" placeholder="模块" />
      <input v-if="routeKey === 'audit'" v-model="filters.operation_type" placeholder="动作" />
      <button class="secondary" @click="reload()">刷新</button>
      <button class="ghost" @click="clearAndReload()">清空筛选</button>
    </section>

    <section v-if="pageError" class="state-panel warning">
      <h2>加载失败</h2>
      <p>{{ pageError }}</p>
      <p v-if="errorTraceId">TraceId: {{ errorTraceId }}</p>
    </section>

    <div class="list-shell">
      <DataTable
        :columns="columns"
        :records="records"
        :id-fields="adminRoute.idFields"
        :loading="pageLoading"
        @select="onSelect"
      />
      <DetailPanel
        :detail="detail"
        :route="adminRoute"
        :loading="detailLoading"
        :error="detailError"
        @action="onAction"
        @close="closeDetail"
      />
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
