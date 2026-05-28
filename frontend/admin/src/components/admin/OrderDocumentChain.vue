<script setup lang="ts">
import StatusTag from "@/components/admin/StatusTag.vue";
import { emptyText, formatCent, formatDateTime, isRecord } from "@/utils/format";

type AnyRecord = Record<string, unknown>;

const props = defineProps<{
  links?: unknown;
}>();

const emit = defineEmits<{
  (e: "navigate", documentType: string, documentId: string | number | null, documentNo: string | null): void;
}>();

const TYPE_TITLE: Record<string, string> = {
  PAYMENT: "支付记录",
  ENTITLEMENT: "学习权益",
  SHIPMENT: "发货单",
  REFUND: "退款单",
  INVOICE: "发票单",
  RECONCILIATION: "对账记录",
  ACCOUNTING: "代账材料",
  AUDIT: "审计日志"
};

// 哪些 documentType 支持点击跳转到对应模块
const NAVIGABLE: Record<string, true> = {
  PAYMENT: true,
  ENTITLEMENT: true,
  SHIPMENT: true,
  REFUND: true,
  INVOICE: true,
  RECONCILIATION: true,
  ACCOUNTING: true,
  AUDIT: true
};

function rows(): AnyRecord[] {
  if (!Array.isArray(props.links)) return [];
  return props.links.filter(isRecord).slice(0, 12);
}

function documentType(row: AnyRecord): string {
  return String(row.document_type ?? row.type ?? "");
}

function documentTitle(row: AnyRecord): string {
  return TYPE_TITLE[documentType(row)] ?? emptyText(documentType(row));
}

function documentNo(row: AnyRecord): string {
  return emptyText(row.document_no ?? row.entity_no ?? row.order_no ?? row.id);
}

function documentMeta(row: AnyRecord): string {
  const amount = row.amount_cent ?? row.paid_amount_cent ?? row.invoice_amount_cent ?? row.apply_amount_cent;
  const time = row.occurred_at ?? row.created_at ?? row.updated_at;
  const pieces = [
    amount === undefined || amount === null ? "" : formatCent(amount as number),
    formatDateTime(time)
  ].filter((item) => item !== "-");
  return pieces.join(" / ") || "-";
}

function isClickable(row: AnyRecord): boolean {
  return Boolean(NAVIGABLE[documentType(row)]);
}

function onClick(row: AnyRecord): void {
  if (!isClickable(row)) return;
  const type = documentType(row);
  const id = (row.document_id ?? row.id ?? null) as string | number | null;
  const no = (row.document_no ?? row.entity_no ?? null) as string | null;
  emit("navigate", type, id, no);
}
</script>

<template>
  <section class="document-chain">
    <header>
      <h4>单据链</h4>
      <small>点击节点查看对应单据</small>
    </header>
    <p v-if="rows().length === 0" class="empty slim">暂无记录</p>
    <ol v-else>
      <li
        v-for="row in rows()"
        :key="`${documentTitle(row)}-${documentNo(row)}`"
        :class="{ clickable: isClickable(row) }"
      >
        <button
          v-if="isClickable(row)"
          type="button"
          class="chain-button"
          @click="onClick(row)"
        >
          <span class="chain-dot"></span>
          <div class="chain-body">
            <strong>{{ documentTitle(row) }}</strong>
            <p>{{ documentNo(row) }}</p>
          </div>
          <StatusTag v-if="row.status" :value="row.status as string" />
          <small>{{ documentMeta(row) }}</small>
        </button>
        <template v-else>
          <span class="chain-dot"></span>
          <div class="chain-body">
            <strong>{{ documentTitle(row) }}</strong>
            <p>{{ documentNo(row) }}</p>
          </div>
          <StatusTag v-if="row.status" :value="row.status as string" />
          <small>{{ documentMeta(row) }}</small>
        </template>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.document-chain header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 8px;
}
.document-chain header small {
  color: var(--muted);
  font-size: 12px;
}
.chain-button {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: transparent;
  border: 1px solid transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.chain-button:hover {
  background: rgba(15, 118, 110, 0.05);
  border-color: rgba(15, 118, 110, 0.25);
}
.chain-body {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.chain-body strong {
  font-size: 14px;
}
.chain-body p {
  margin: 2px 0 0;
  color: var(--muted);
  font-size: 12px;
}
li.clickable {
  padding: 0;
}
</style>
