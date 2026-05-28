import { reactive, ref } from "vue";
import { defineStore } from "pinia";
import { request } from "@/services/http";
import { getRecordId } from "@/utils/format";
import {
  ACTION_BINDINGS,
  ACTION_FIELD_DEFS,
  NO_ID_ACTIONS,
  RECORD_ID_FIELDS,
  type ActionType
} from "@/config/actions";

type AnyRecord = Record<string, unknown>;

export const useActionStore = defineStore("action", () => {
  const actionType = ref<string>("");
  const actionRecord = ref<AnyRecord | null>(null);
  const actionLoading = ref(false);
  const actionError = ref("");
  const actionForm = reactive<Record<string, any>>({});

  function defaultForm(type: string): Record<string, unknown> {
    const fields = ACTION_FIELD_DEFS[type] ?? [];
    const form: Record<string, unknown> = {};
    for (const f of fields) {
      if (f.type === "checkbox") form[f.key] = false;
      else if (f.type === "itemTable") form[f.key] = [] as Array<Record<string, unknown>>;
      else form[f.key] = "";
    }
    return form;
  }

  function newItemRow(field: { columns?: { key: string; type?: string }[] }): Record<string, unknown> {
    const row: Record<string, unknown> = {};
    for (const col of field.columns ?? []) {
      row[col.key] = col.type === "checkbox" ? false : "";
    }
    return row;
  }

  function openModal(type: string, record: AnyRecord | null): void {
    actionType.value = type;
    actionRecord.value = record;
    actionError.value = "";
    for (const key of Object.keys(actionForm)) delete actionForm[key];
    Object.assign(actionForm, defaultForm(type));
    // itemTable 预填：若字段定义有 prefillFrom，从 record 该路径取数组初始化行
    const fields = ACTION_FIELD_DEFS[type] ?? [];
    for (const f of fields) {
      if (f.type === "itemTable" && f.prefillFrom && record) {
        const raw = record[f.prefillFrom];
        if (Array.isArray(raw) && raw.length > 0) {
          actionForm[f.key] = raw.map((item) => {
            const row: Record<string, unknown> = {};
            for (const col of f.columns ?? []) {
              const value = (item as Record<string, unknown>)[col.key];
              row[col.key] = value == null ? "" : String(value);
            }
            return row;
          });
        }
      }
    }
  }

  function addItemRow(key: string): void {
    const field = (ACTION_FIELD_DEFS[actionType.value] ?? []).find((f) => f.key === key);
    if (!field) return;
    const rows = (actionForm[key] as Array<Record<string, unknown>>) ?? [];
    actionForm[key] = [...rows, newItemRow(field)];
  }

  function removeItemRow(key: string, index: number): void {
    const rows = (actionForm[key] as Array<Record<string, unknown>>) ?? [];
    actionForm[key] = rows.filter((_, i) => i !== index);
  }

  function closeModal(): void {
    actionType.value = "";
    actionRecord.value = null;
    actionError.value = "";
    for (const key of Object.keys(actionForm)) delete actionForm[key];
  }

  function getActionId(): string {
    const type = actionType.value;
    if (NO_ID_ACTIONS.has(type)) return "";
    const record = actionRecord.value;
    if (!record) return "";
    return getRecordId(record, [...RECORD_ID_FIELDS]) ?? "";
  }

  function parseJsonField(key: string, value: string): unknown {
    try {
      return JSON.parse(value);
    } catch {
      throw new Error(`${key} 不是合法 JSON`);
    }
  }

  function payloadKey(key: string): string {
    return key.endsWith("_json") ? key.slice(0, -5) : key;
  }

  function shouldNumber(key: string): boolean {
    return (
      key.endsWith("_cent") ||
      key.endsWith("_count") ||
      key.endsWith("_id") ||
      key === "quantity" ||
      key === "received_quantity" ||
      key === "sort_no" ||
      key === "safety_stock" ||
      key === "total_count" ||
      key === "tax_rate"
    );
  }

  function buildPayload(type: string): Record<string, unknown> {
    const fields = new Map((ACTION_FIELD_DEFS[type] ?? []).map((field) => [field.key, field]));
    const payload: Record<string, unknown> = {};
    for (const [key, rawValue] of Object.entries(actionForm)) {
      const field = fields.get(key);

      // itemTable：把每行的列收集为对象数组，数字列转 number
      if (field?.type === "itemTable") {
        const rows = (rawValue as Array<Record<string, unknown>>) ?? [];
        const cleaned = rows
          .map((row) => {
            const item: Record<string, unknown> = {};
            for (const col of field.columns ?? []) {
              const colVal = row[col.key];
              if (colVal === null || colVal === undefined) continue;
              const trimmed = typeof colVal === "string" ? colVal.trim() : colVal;
              if (trimmed === "") continue;
              if (col.type === "number" || shouldNumber(col.key)) {
                item[col.key] = Number(trimmed) || 0;
              } else if (col.type === "checkbox") {
                item[col.key] = Boolean(trimmed);
              } else {
                item[col.key] = trimmed;
              }
            }
            return item;
          })
          .filter((item) => Object.keys(item).length > 0);
        if (cleaned.length > 0) {
          payload[key] = cleaned;
        }
        continue;
      }

      if (typeof rawValue === "string" && rawValue.trim() === "") {
        continue;
      }

      if (field?.type === "checkbox") {
        payload[payloadKey(key)] = Boolean(rawValue);
        continue;
      }

      const value = typeof rawValue === "string" ? rawValue.trim() : rawValue;
      if (key.endsWith("_json") && typeof value === "string") {
        payload[payloadKey(key)] = parseJsonField(key, value);
      } else if (key === "file_refs" && typeof value === "string") {
        payload[key] = value.split(",").map((item) => item.trim()).filter(Boolean);
      } else if (shouldNumber(key)) {
        payload[key] = Number(value) || 0;
      } else {
        payload[key] = value;
      }
    }
    return payload;
  }

  function hasFileField(type: string): boolean {
    return (ACTION_FIELD_DEFS[type] ?? []).some((f) => f.type === "file");
  }

  function buildFormData(type: string): FormData {
    const fields = new Map((ACTION_FIELD_DEFS[type] ?? []).map((field) => [field.key, field]));
    const fd = new FormData();
    for (const [key, rawValue] of Object.entries(actionForm)) {
      const field = fields.get(key);
      if (rawValue == null) continue;
      if (field?.type === "file") {
        if (rawValue instanceof File) {
          fd.append(key, rawValue);
        }
        continue;
      }
      if (typeof rawValue === "string" && rawValue.trim() === "") continue;
      const value = typeof rawValue === "string" ? rawValue.trim() : rawValue;
      if (field?.type === "checkbox") {
        fd.append(key, String(Boolean(value)));
      } else {
        fd.append(key, String(value));
      }
    }
    return fd;
  }

  async function submit(): Promise<unknown> {
    const type = actionType.value as ActionType;
    const binding = ACTION_BINDINGS[type];
    if (!binding) {
      actionError.value = `未知操作: ${type}`;
      throw new Error(actionError.value);
    }
    actionLoading.value = true;
    actionError.value = "";
    try {
      const isMultipart = hasFileField(type);
      const scope = binding.action ? `${type}:${binding.action}` : String(type);
      if (isMultipart) {
        const fd = buildFormData(type);
        Object.entries(binding.staticPayload ?? {}).forEach(([k, v]) => fd.append(k, String(v)));
        if (binding.action) fd.append("action", binding.action);
        return await request(binding.urlFor(getActionId()), {
          method: "POST",
          body: fd,
          idempotent: true,
          idempotencyScope: scope
        });
      }
      const payload = buildPayload(type);
      Object.assign(payload, binding.staticPayload ?? {});
      if (binding.action) payload.action = binding.action;
      return await request(binding.urlFor(getActionId()), {
        method: "POST",
        body: JSON.stringify(payload),
        idempotent: true,
        idempotencyScope: scope
      });
    } catch (e: unknown) {
      actionError.value = (e as { message?: string })?.message ?? "操作失败";
      throw e;
    } finally {
      actionLoading.value = false;
    }
  }

  return {
    actionType,
    actionRecord,
    actionLoading,
    actionError,
    actionForm,
    openModal,
    addItemRow,
    removeItemRow,
    closeModal,
    submit,
    getActionId
  };
});
