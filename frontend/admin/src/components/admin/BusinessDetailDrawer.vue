<script setup lang="ts">
import { computed } from "vue";
import type { AdminRoute } from "@/router/routes";
import type { PageConfig } from "@/config/pageConfigs";
import type { RouteAction } from "@/config/actions";
import { fieldLabel } from "@/config/status";
import { emptyText, firstPresent, formatCent, formatDateTime, formatNumber, isRecord } from "@/utils/format";
import OrderDocumentChain from "@/components/admin/OrderDocumentChain.vue";
import StatusTag from "@/components/admin/StatusTag.vue";

type AnyRecord = Record<string, unknown>;

const props = defineProps<{
  detail: AnyRecord | null;
  route: AdminRoute;
  config: PageConfig;
  actions: RouteAction[];
  loading?: boolean;
  error?: string;
}>();

const emit = defineEmits<{
  (e: "action", action: RouteAction, record: AnyRecord): void;
  (e: "close"): void;
}>();

const titleNo = computed(() => {
  if (!props.detail) return "-";
  return emptyText(firstPresent(props.detail, props.config.detailNoFields));
});

const statusFields = computed(() => {
  const fields = props.config.focusStatuses ?? props.route.statusFields ?? [];
  if (!props.detail) return [];
  return fields
    .map((field) => ({ field, value: props.detail?.[field] }))
    .filter((item) => item.value !== null && item.value !== undefined && item.value !== "");
});

const usedFields = computed(() => {
  const used = new Set<string>();
  for (const group of props.config.detailGroups) {
    group.fields.forEach((field) => used.add(field));
  }
  statusFields.value.forEach((item) => used.add(item.field));
  return used;
});

const extraEntries = computed(() => {
  if (!props.detail) return [];
  return Object.entries(props.detail)
    .filter(([key, value]) => !usedFields.value.has(key) && isPlain(value))
    .slice(0, 10);
});

const arrayEntries = computed(() => {
  if (!props.detail) return [];
  return Object.entries(props.detail)
    .filter(([key, value]) => key !== "document_links" && Array.isArray(value) && value.length > 0)
    .slice(0, 4);
});

function isPlain(value: unknown): boolean {
  return typeof value !== "object" || value === null;
}

function hasAnyField(fields: string[]): boolean {
  if (!props.detail) return false;
  return fields.some((field) => {
    const value = props.detail?.[field];
    return value !== null && value !== undefined && value !== "";
  });
}

function displayValue(key: string, value: unknown): string {
  if (props.route.amountFields?.includes(key) || key.endsWith("_amount_cent") || key.endsWith("_price_cent")) {
    return formatCent(value as number | null | undefined);
  }
  if (key.endsWith("_at") || key.endsWith("_time") || key.endsWith("_date")) return formatDateTime(value);
  if (typeof value === "number") return formatNumber(value);
  if (typeof value === "boolean") return value ? "是" : "否";
  return emptyText(value);
}

function arrayRows(value: unknown): AnyRecord[] {
  if (!Array.isArray(value)) return [];
  return value.filter(isRecord).slice(0, 5);
}

function arrayColumns(rows: AnyRecord[]): string[] {
  const keys = new Set<string>();
  for (const row of rows) {
    Object.keys(row).slice(0, 6).forEach((key) => keys.add(key));
  }
  return [...keys].slice(0, 6);
}

function arrayTitle(key: string): string {
  const titles: Record<string, string> = {
    payment_records: "支付记录",
    shipment_items: "发货明细",
    items: "明细",
    order_items: "商品明细",
    refund_records: "退款记录",
    invoice_records: "开票记录",
    audit_logs: "审计记录",
    config_items: "配置项",
    records: "明细记录",
    lesson_nodes: "课节",
    specs: "售卖规格"
  };
  return titles[key] ?? fieldLabel(key);
}

function actionClass(actionType: string): string {
  return actionType.includes("Reject") || actionType.includes("red") || actionType.includes("Abandon")
    ? "danger"
    : "secondary";
}

function onAction(action: RouteAction): void {
  if (props.detail) emit("action", action, props.detail);
}
</script>

<template>
  <Teleport to="body">
    <section v-if="detail" class="detail-drawer-layer" @click.self="emit('close')">
      <aside class="business-drawer" role="dialog" aria-modal="true" :aria-label="config.detailTitle">
        <header class="business-drawer-head">
          <div>
            <span class="drawer-kicker">{{ config.detailTitle }}</span>
            <h3>{{ titleNo }}</h3>
            <p v-if="loading" class="hint">详情加载中...</p>
            <p v-if="error" class="error-line">{{ error }}</p>
          </div>
          <div class="detail-title-actions">
            <div class="detail-actions">
              <button
                v-for="act in actions"
                :key="act.type"
                type="button"
                :class="actionClass(act.type)"
                @click="onAction(act)"
              >{{ act.label }}</button>
            </div>
            <button class="icon-close" type="button" aria-label="关闭详情" @click="emit('close')">×</button>
          </div>
        </header>

        <section v-if="statusFields.length" class="drawer-status-grid">
          <article v-for="item in statusFields" :key="item.field">
            <span>{{ fieldLabel(item.field) }}</span>
            <StatusTag :value="item.value as string" />
          </article>
        </section>

        <section
          v-for="group in config.detailGroups"
          v-show="hasAnyField(group.fields)"
          :key="group.title"
          class="drawer-section"
        >
          <h4>{{ group.title }}</h4>
          <dl class="drawer-desc">
            <template v-for="field in group.fields" :key="field">
              <template v-if="detail[field] !== null && detail[field] !== undefined && detail[field] !== ''">
                <dt>{{ fieldLabel(field) }}</dt>
                <dd>
                  <StatusTag v-if="field.includes('status') || field === 'course_type'" :value="detail[field] as string" />
                  <span v-else>{{ displayValue(field, detail[field]) }}</span>
                </dd>
              </template>
            </template>
          </dl>
        </section>

        <OrderDocumentChain v-if="config.documentChain" :links="detail.document_links" />

        <section v-for="[key, val] in arrayEntries" :key="key" class="drawer-section">
          <h4>{{ arrayTitle(key) }}</h4>
          <div class="mini-table-wrap">
            <table class="mini-table">
              <thead>
                <tr>
                  <th v-for="col in arrayColumns(arrayRows(val))" :key="col">{{ fieldLabel(col) }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIndex) in arrayRows(val)" :key="rowIndex">
                  <td v-for="col in arrayColumns(arrayRows(val))" :key="col">
                    <StatusTag v-if="String(col).includes('status')" :value="row[col] as string" />
                    <span v-else>{{ displayValue(col, row[col]) }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section v-if="extraEntries.length" class="drawer-section muted-section">
          <h4>其他信息</h4>
          <dl class="drawer-desc">
            <template v-for="[key, val] in extraEntries" :key="key">
              <dt>{{ fieldLabel(String(key)) }}</dt>
              <dd>{{ displayValue(String(key), val) }}</dd>
            </template>
          </dl>
        </section>
      </aside>
    </section>
  </Teleport>
</template>
