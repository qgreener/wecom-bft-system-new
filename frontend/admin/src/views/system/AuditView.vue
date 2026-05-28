<script setup lang="ts">
import { storeToRefs } from "pinia";
import BusinessListPage from "@/views/business/BusinessListPage.vue";
import { usePageStore } from "@/stores/page";

const pageStore = usePageStore();
const { records } = storeToRefs(pageStore);

function exportCsv(): void {
  const rows = records.value as Record<string, unknown>[];
  if (!rows || rows.length === 0) return;
  const headers = ["时间", "操作人", "角色", "模块", "动作", "对象类型", "对象编号", "结果", "TraceId"];
  const lines = [headers.join(",")];
  for (const r of rows) {
    const esc = (v: unknown): string => {
      if (v == null) return "";
      const s = String(v).replace(/"/g, "''");
      return s.includes(",") || s.includes("\n") ? `"${s}"` : s;
    };
    lines.push([
      esc(r.created_at ?? r.occurred_at),
      esc(r.operator_name ?? r.operator_user_id),
      esc(r.operator_role),
      esc(r.operation_module),
      esc(r.operation_type),
      esc(r.target_type),
      esc(r.target_no),
      esc(r.result),
      esc(r.trace_id)
    ].join(","));
  }
  const blob = new Blob(["﻿" + lines.join("\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `audit-log-${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}
</script>

<template>
  <div class="audit-page">
    <div class="audit-toolbar">
      <button class="ghost" :disabled="!records.length" @click="exportCsv()">导出当前结果 CSV</button>
    </div>
    <BusinessListPage />
  </div>
</template>

<style scoped>
.audit-toolbar {
  display: flex;
  justify-content: flex-end;
  padding: 0 0 8px;
}
</style>
