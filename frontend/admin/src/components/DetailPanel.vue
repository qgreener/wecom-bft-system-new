<script setup lang="ts">
import { computed } from "vue";
import { type AdminRoute } from "@/router/routes";
import { fieldLabel, statusClass, statusLabel } from "@/config/status";
import { emptyText, formatCent, formatDateTime, formatNumber, isRecord } from "@/utils/format";
import { ACTION_BINDINGS, NO_ID_ACTIONS, actionsForRoute, type RouteAction } from "@/config/actions";

type AnyRecord = Record<string, unknown>;

const props = defineProps<{
  detail: AnyRecord | null;
  route: AdminRoute;
  loading?: boolean;
  error?: string;
}>();

const emit = defineEmits<{
  (e: "action", action: RouteAction, record: AnyRecord): void;
  (e: "close"): void;
}>();

const primitiveEntries = computed(() => {
  if (!props.detail) return [];
  return Object.entries(props.detail).filter(([, val]) => isPlain(val));
});

const statusEntries = computed(() => {
  if (!props.detail) return [];
  return (props.route.statusFields ?? [])
    .map((field) => [field, props.detail?.[field]] as const)
    .filter(([, value]) => value !== null && value !== undefined && value !== "");
});

const arrayEntries = computed(() => {
  if (!props.detail) return [];
  return Object.entries(props.detail).filter(([, val]) => Array.isArray(val) && (val as unknown[]).length > 0);
});

const visibleActions = computed(() => {
  const detail = props.detail;
  const base = actionsForRoute(props.route.key).filter((action) => !NO_ID_ACTIONS.has(action.type));
  if (!detail) return [];
  const allowed = Array.isArray(detail.allowed_actions) ? detail.allowed_actions.map(String) : null;
  if (allowed) {
    return base.filter((action) => {
      const binding = ACTION_BINDINGS[action.type];
      return allowed.includes(action.type) || (binding?.action ? allowed.includes(binding.action) : false);
    });
  }
  return base.filter((action) => isActionRelevant(action.type, detail));
});

function isPlain(val: unknown): boolean {
  return typeof val !== "object" || val === null;
}

function isActionRelevant(actionType: string, detail: AnyRecord): boolean {
  const status = String(detail.status ?? detail.refund_status ?? detail.fulfillment_status ?? detail.invoice_status ?? "");
  if (props.route.key === "refunds") {
    if (["refundApprove", "refundReject"].includes(actionType)) return status === "REVIEWING";
    if (actionType === "refundManual") return status === "MANUAL_REQUIRED";
    if (actionType === "refundRetry") return status === "FAILED";
  }
  if (props.route.key === "shipments") {
    if (actionType === "ship") return ["PENDING", "PENDING_SHIPMENT", "WAIT_SHIP"].includes(status);
    if (actionType === "sign") return status === "SHIPPED";
  }
  if (props.route.key === "invoices") {
    if (actionType === "invoiceIssue") return ["APPLIED", "TO_BE_ISSUED"].includes(status);
    if (actionType === "redReverse") return status === "ISSUED";
  }
  if (props.route.key === "accounting") {
    if (actionType === "materialUpload") return ["PENDING_SUPPLEMENT", "UPLOADED"].includes(status);
    if (actionType === "materialConfirm") return status === "UPLOADED";
    if (actionType === "materialClose") return ["PENDING_SUPPLEMENT", "UPLOADED"].includes(status);
  }
  if (props.route.key === "leads") {
    if (actionType === "leadFollow") return status !== "CONVERTED";
    if (actionType === "leadConvert") return !["CONVERTED", "ABANDONED"].includes(status);
    if (actionType === "leadAbandon") return status !== "ABANDONED";
  }
  return status === "" || !["refundApprove", "refundReject", "refundManual", "refundRetry", "ship", "sign", "invoiceIssue", "redReverse"].includes(actionType);
}

function displayPrimitive(key: string, val: unknown): string {
  if (props.route.amountFields?.includes(key)) return formatCent(val as number | null | undefined);
  if (key.endsWith("_at") || key.endsWith("_time")) return formatDateTime(val);
  if (typeof val === "number") return formatNumber(val);
  if (typeof val === "boolean") return val ? "是" : "否";
  return emptyText(val);
}

function arrayRows(value: unknown): AnyRecord[] {
  if (!Array.isArray(value)) return [];
  return value.filter(isRecord).slice(0, 6);
}

function arrayColumns(rows: AnyRecord[]): string[] {
  const keys = new Set<string>();
  for (const row of rows) {
    Object.keys(row).slice(0, 8).forEach((key) => keys.add(key));
  }
  return [...keys].slice(0, 8);
}

function arrayTitle(key: string): string {
  const titles: Record<string, string> = {
    document_links: "单据链",
    payment_records: "支付记录",
    shipment_items: "发货明细",
    items: "明细",
    order_items: "商品明细",
    refund_records: "退款记录",
    invoice_records: "开票记录",
    audit_logs: "审计记录",
    config_items: "配置项",
    records: "明细记录"
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

function close(): void {
  emit("close");
}
</script>

<template>
  <Teleport to="body">
    <section v-if="detail" class="detail-drawer-layer" @click.self="close">
      <aside class="detail-panel detail-drawer" role="dialog" aria-modal="true" aria-label="业务详情">
        <div class="detail-title">
          <div>
            <h3>详情</h3>
            <p v-if="loading" class="hint">详情加载中...</p>
            <p v-if="error" class="error-line">{{ error }}</p>
          </div>
          <div class="detail-title-actions">
            <div class="detail-actions">
              <button
                v-for="act in visibleActions"
                :key="act.type"
                type="button"
                :class="actionClass(act.type)"
                @click="onAction(act)"
              >{{ act.label }}</button>
            </div>
            <button class="icon-close" type="button" aria-label="关闭详情" @click="close">×</button>
          </div>
        </div>

        <section v-if="statusEntries.length" class="status-strip">
          <div v-for="[key, value] in statusEntries" :key="key">
            <span>{{ fieldLabel(key) }}</span>
            <strong class="status-tag" :class="statusClass(value as string)">
              {{ statusLabel(value as string) }}
            </strong>
          </div>
        </section>

        <dl class="description-list">
          <template v-for="[key, val] in primitiveEntries" :key="String(key)">
              <dt>{{ fieldLabel(String(key)) }}</dt>
              <dd>
                <span
                  v-if="route.statusFields?.includes(String(key))"
                  class="status-tag"
                  :class="statusClass(val as string)"
                >{{ statusLabel(val as string) }}</span>
                <span v-else>{{ displayPrimitive(String(key), val) }}</span>
              </dd>
          </template>
        </dl>

        <section v-for="[key, val] in arrayEntries" :key="key" class="detail-block">
          <h4>{{ arrayTitle(key) }}</h4>
          <ul v-if="key === 'document_links'" class="timeline">
            <li v-for="item in arrayRows(val)" :key="String(item.document_no ?? item.entity_no ?? item.id ?? JSON.stringify(item))">
              <strong>{{ emptyText(item.document_type ?? item.type) }}</strong>
              <span>{{ emptyText(item.document_no ?? item.entity_no ?? item.order_no) }}</span>
              <span v-if="item.status" class="status-tag" :class="statusClass(item.status as string)">
                {{ statusLabel(item.status as string) }}
              </span>
            </li>
          </ul>
          <div v-else class="mini-table-wrap">
            <table class="mini-table">
              <thead>
                <tr>
                  <th v-for="col in arrayColumns(arrayRows(val))" :key="col">{{ fieldLabel(col) }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIndex) in arrayRows(val)" :key="rowIndex">
                  <td v-for="col in arrayColumns(arrayRows(val))" :key="col">
                    <span v-if="String(col).includes('status')" class="status-tag" :class="statusClass(row[col] as string)">
                      {{ statusLabel(row[col] as string) }}
                    </span>
                    <span v-else>{{ displayPrimitive(col, row[col]) }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </aside>
    </section>
  </Teleport>
</template>
