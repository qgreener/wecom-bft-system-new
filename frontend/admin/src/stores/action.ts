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

  function defaultForm(type: string): Record<string, string | boolean | number> {
    const fields = ACTION_FIELD_DEFS[type] ?? [];
    const form: Record<string, string | boolean | number> = {};
    for (const f of fields) form[f.key] = f.type === "checkbox" ? false : "";
    return form;
  }

  function openModal(type: string, record: AnyRecord | null): void {
    actionType.value = type;
    actionRecord.value = record;
    actionError.value = "";
    for (const key of Object.keys(actionForm)) delete actionForm[key];
    Object.assign(actionForm, defaultForm(type));
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
    closeModal,
    submit,
    getActionId
  };
});
