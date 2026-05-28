<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import DataTable from "@/components/DataTable.vue";
import ActionModal from "@/components/ActionModal.vue";
import BusinessDetailDrawer from "@/components/admin/BusinessDetailDrawer.vue";
import StatusTag from "@/components/admin/StatusTag.vue";
import { useActionStore } from "@/stores/action";
import { useGenericList } from "@/composables/useGenericList";
import { NO_ID_ACTIONS, type RouteAction } from "@/config/actions";
import { actionsForRecord } from "@/config/actionVisibility";
import { pageConfigFor, type FilterConfig } from "@/config/pageConfigs";
import { formatNumber } from "@/utils/format";
import { routeRegistry, type RouteKey } from "@/router/routes";

type AnyRecord = Record<string, unknown>;

const router = useRouter();
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

const config = computed(() => pageConfigFor(routeKey.value));
const pageActions = computed(() => actions.value.filter((action) => NO_ID_ACTIONS.has(action.type)));
const recordActions = computed(() => actions.value.filter((action) => !NO_ID_ACTIONS.has(action.type)));

const statusOverview = computed(() => {
  const fields = config.value.focusStatuses ?? adminRoute.value.statusFields ?? [];
  return fields.slice(0, 4).map((field) => {
    const counts = new Map<string, number>();
    for (const record of records.value) {
      const value = record[field];
      if (value === null || value === undefined || value === "") continue;
      const key = String(value);
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    const top = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 3);
    return { field, top };
  }).filter((item) => item.top.length > 0);
});

function rowActionItems(record: AnyRecord): RouteAction[] {
  const actions = actionsForRecord(routeKey.value, record, recordActions.value);
  if (routeKey.value === "courses") {
    return [{ type: "__courseOpenEdit", label: "打开编辑页" }, ...actions];
  }
  return actions;
}

async function onSelect(record: AnyRecord, id: string | null) {
  await selectRecord(record, id);
}

function onAction(action: RouteAction, record: AnyRecord | null) {
  if (action.type === "__courseOpenEdit" && record) {
    openCourseEdit(record);
    return;
  }
  actionStore.openModal(action.type, record);
}

async function onActionSubmitted() {
  await reload();
}

function fieldType(field: FilterConfig): string {
  if (field.kind === "datetime-local" || field.kind === "month" || field.kind === "date") return field.kind;
  return "text";
}

function onFilterChange(field: FilterConfig): void {
  if (field.autoReload) {
    reload();
  }
}

const DOCUMENT_TYPE_TO_ROUTE: Record<string, RouteKey> = {
  PAYMENT: "payments",
  ENTITLEMENT: "entitlements",
  SHIPMENT: "shipments",
  REFUND: "refunds",
  INVOICE: "invoices",
  RECONCILIATION: "reconciliation",
  ACCOUNTING: "accounting",
  AUDIT: "audit"
};

function onNavigateDocument(documentType: string, documentId: string | number | null, documentNo: string | null) {
  const targetKey = DOCUMENT_TYPE_TO_ROUTE[documentType];
  if (!targetKey) return;
  const targetRoute = routeRegistry.find((r) => r.key === targetKey);
  if (!targetRoute) return;
  router.push({
    path: targetRoute.path,
    query: {
      open_document_id: documentId == null ? undefined : String(documentId),
      open_document_no: documentNo ?? undefined
    }
  });
}

function openCourseEdit(record: AnyRecord): void {
  const id = record.course_id ?? record.id;
  if (id) router.push(`/courses/${id}/edit`);
}
</script>

<template>
  <template v-if="hasPageAccess">
    <section class="business-workbench">
      <div class="business-command-bar">
        <div class="business-context">
          <span>{{ adminRoute.title }}</span>
          <small>{{ formatNumber(records.length) }} 条当前结果</small>
        </div>
        <div class="head-actions">
          <button
            v-for="action in pageActions"
            :key="action.type"
            type="button"
            class="primary"
            @click="onAction(action, null)"
          >{{ action.label }}</button>
          <button class="secondary" :disabled="pageLoading" @click="reload()">{{ pageLoading ? "刷新中" : "刷新" }}</button>
        </div>
      </div>

      <section v-if="statusOverview.length" class="status-overview">
        <article v-for="item in statusOverview" :key="item.field">
          <span>{{ item.field.includes('payment') ? '支付' : item.field.includes('fulfillment') ? '履约' : item.field.includes('refund') ? '退款' : item.field.includes('invoice') ? '开票' : '状态' }}</span>
          <div>
            <span v-for="[code, count] in item.top" :key="code" class="overview-status">
              <StatusTag :value="code" />
              <b>{{ count }}</b>
            </span>
          </div>
        </article>
      </section>

      <section class="filter-panel">
        <label
          v-for="field in config.filters"
          :key="field.key"
          :class="{ wide: field.span === 'wide' }"
        >
          <span>{{ field.label }}</span>
          <select
            v-if="field.kind === 'select'"
            v-model="filters[field.key]"
            @change="onFilterChange(field)"
          >
            <option value="">全部</option>
            <option v-for="option in field.options ?? []" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <input
            v-else
            v-model="filters[field.key]"
            :type="fieldType(field)"
            :placeholder="field.placeholder"
            @keyup.enter="reload()"
            @change="onFilterChange(field)"
          />
        </label>
        <div class="filter-actions">
          <button class="secondary" @click="reload()">查询</button>
          <button class="ghost" @click="clearAndReload()">重置</button>
        </div>
      </section>

      <section v-if="pageError" class="state-panel warning">
        <h2>加载失败</h2>
        <p>{{ pageError }}</p>
        <p v-if="errorTraceId">TraceId: {{ errorTraceId }}</p>
      </section>

      <DataTable
        :columns="columns"
        :records="records"
        :id-fields="adminRoute.idFields"
        :loading="pageLoading"
        :empty-message="config.emptyMessage"
        :row-actions="rowActionItems"
        @select="onSelect"
        @row-action="onAction"
      />

      <BusinessDetailDrawer
        :detail="detail"
        :route="adminRoute"
        :config="config"
        :actions="actionsForRecord(routeKey, detail, recordActions)"
        :loading="detailLoading"
        :error="detailError"
        @action="onAction"
        @close="closeDetail"
        @navigate-document="onNavigateDocument"
      />
    </section>
  </template>

  <template v-else>
    <section class="state-panel">
      <h2>无访问权限</h2>
      <p>当前角色无权访问此页面，请联系管理员。</p>
    </section>
  </template>

  <ActionModal @submitted="onActionSubmitted" />
</template>
