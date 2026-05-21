<script setup lang="ts">
import { type ColumnDef } from "@/config/columns";
import { statusClass, statusLabel } from "@/config/status";
import {
  emptyText,
  formatCent,
  formatDateTime,
  formatNumber,
  getRecordId,
  isRecord,
  labelFromSnapshot
} from "@/utils/format";

type AnyRecord = Record<string, unknown>;

const props = defineProps<{
  columns: ColumnDef[];
  records: AnyRecord[];
  idFields: string[];
  loading?: boolean;
  emptyMessage?: string;
}>();

const emit = defineEmits<{
  (e: "select", record: AnyRecord, id: string | null): void;
}>();

function cellValue(record: AnyRecord, column: ColumnDef): unknown {
  if (column.type === "snapshot" && column.snapshotKey && column.snapshotField) {
    return labelFromSnapshot(record, column.snapshotKey, column.snapshotField);
  }
  return record[column.key];
}

function displayValue(record: AnyRecord, column: ColumnDef): string {
  const v = cellValue(record, column);
  if (column.type === "amount") return formatCent(v as number | null | undefined);
  if (column.type === "datetime") return formatDateTime(v);
  if (column.type === "boolean") return v ? "是" : "否";
  if (typeof v === "number") return formatNumber(v);
  return emptyText(v);
}

function rowKey(record: AnyRecord): string {
  return getRecordId(record, props.idFields) ?? JSON.stringify(record).slice(0, 80);
}

function onClickRow(record: AnyRecord): void {
  const id = getRecordId(record, props.idFields);
  emit("select", record, id);
}

function isWarningStock(record: AnyRecord): boolean {
  const stock = record.available_stock;
  const safety = record.safety_stock;
  return typeof stock === "number" && typeof safety === "number" && stock < safety;
}

function titleFor(record: AnyRecord, column: ColumnDef): string {
  const value = cellValue(record, column);
  if (isRecord(value) || Array.isArray(value)) return emptyText(value);
  return displayValue(record, column);
}
</script>

<template>
  <div class="table-panel">
    <p v-if="loading" class="loading">加载中...</p>
    <p v-else-if="records.length === 0" class="empty">{{ emptyMessage ?? "暂无数据" }}</p>
    <table v-else>
      <thead>
        <tr>
          <th v-for="col in columns" :key="col.key" :style="{ width: col.width }">{{ col.label }}</th>
          <th class="action-cell">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="record in records"
          :key="rowKey(record)"
          :class="{ 'row-warning': isWarningStock(record) }"
          @click="onClickRow(record)"
        >
          <td
            v-for="col in columns"
            :key="col.key"
            :class="{ 'amount-cell': col.type === 'amount' }"
            :title="titleFor(record, col)"
          >
            <span
              v-if="col.type === 'status'"
              class="status-tag"
              :class="statusClass(cellValue(record, col) as string)"
            >{{ statusLabel(cellValue(record, col) as string) }}</span>
            <span v-else>{{ displayValue(record, col) }}</span>
          </td>
          <td class="action-cell"><button class="link" type="button">查看</button></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
