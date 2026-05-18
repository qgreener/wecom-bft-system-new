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
  const actionForm = reactive<Record<string, string>>({});

  function defaultForm(type: string): Record<string, string> {
    const fields = ACTION_FIELD_DEFS[type] ?? [];
    const form: Record<string, string> = {};
    for (const f of fields) form[f.key] = "";
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

  async function submit(): Promise<void> {
    const type = actionType.value as ActionType;
    const binding = ACTION_BINDINGS[type];
    if (!binding) {
      actionError.value = `未知操作: ${type}`;
      throw new Error(actionError.value);
    }
    actionLoading.value = true;
    actionError.value = "";
    try {
      const payload: Record<string, unknown> = { ...actionForm };
      for (const key of Object.keys(payload)) {
        if (key.endsWith("_cent") || key === "total_count" || key === "quantity") {
          payload[key] = Number(payload[key]) || 0;
        }
      }
      if (binding.action) payload.action = binding.action;
      const scope = binding.action ? `${type}:${binding.action}` : String(type);
      await request(binding.urlFor(getActionId()), {
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
