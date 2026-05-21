<script setup lang="ts">
import StatusTag from "@/components/admin/StatusTag.vue";
import { emptyText, formatCent, formatDateTime, isRecord } from "@/utils/format";

type AnyRecord = Record<string, unknown>;

const props = defineProps<{
  links?: unknown;
}>();

function rows(): AnyRecord[] {
  if (!Array.isArray(props.links)) return [];
  return props.links.filter(isRecord).slice(0, 8);
}

function documentTitle(row: AnyRecord): string {
  const type = String(row.document_type ?? row.type ?? "");
  const map: Record<string, string> = {
    PAYMENT: "支付记录",
    ENTITLEMENT: "学习权益",
    SHIPMENT: "发货单",
    REFUND: "退款单",
    INVOICE: "发票单",
    RECONCILIATION: "对账记录",
    ACCOUNTING: "代账材料",
    AUDIT: "审计日志"
  };
  return map[type] ?? emptyText(row.document_type ?? row.type);
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
</script>

<template>
  <section class="document-chain">
    <header>
      <h4>单据链</h4>
    </header>
    <p v-if="rows().length === 0" class="empty slim">暂无记录</p>
    <ol v-else>
      <li v-for="row in rows()" :key="`${documentTitle(row)}-${documentNo(row)}`">
        <span class="chain-dot"></span>
        <div>
          <strong>{{ documentTitle(row) }}</strong>
          <p>{{ documentNo(row) }}</p>
        </div>
        <StatusTag v-if="row.status" :value="row.status as string" />
        <small>{{ documentMeta(row) }}</small>
      </li>
    </ol>
  </section>
</template>
