<script setup lang="ts">
import { ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useActionStore } from "@/stores/action";
import { ACTION_FIELD_DEFS, ACTION_LABELS, type ActionFieldDef } from "@/config/actions";
import { request } from "@/services/http";

const emit = defineEmits<{ (e: "submitted"): void }>();

const actionStore = useActionStore();
const { actionType, actionLoading, actionError, actionForm } = storeToRefs(actionStore);

const successMessage = ref("");
const successLink = ref("");
const remoteOptions = ref<Record<string, { label: string; value: string }[]>>({});
const remoteLoading = ref<Record<string, boolean>>({});

watch(actionType, async (type) => {
  remoteOptions.value = {};
  remoteLoading.value = {};
  if (!type) return;
  const fields = fieldsFor(type);
  for (const field of fields) {
    if (field.type === "remoteSelect" && field.remote) {
      void loadRemote(field);
    }
  }
});

async function loadRemote(field: ActionFieldDef): Promise<void> {
  if (!field.remote) return;
  remoteLoading.value = { ...remoteLoading.value, [field.key]: true };
  try {
    const data = await request<unknown>(field.remote.url);
    const list = extractList(data);
    const opts = list.map((item) => ({
      value: String((item as Record<string, unknown>)[field.remote!.valueKey] ?? ""),
      label: String((item as Record<string, unknown>)[field.remote!.labelKey] ?? "")
    })).filter((o) => o.value && o.label);
    remoteOptions.value = { ...remoteOptions.value, [field.key]: opts };
  } catch {
    remoteOptions.value = { ...remoteOptions.value, [field.key]: [] };
  } finally {
    remoteLoading.value = { ...remoteLoading.value, [field.key]: false };
  }
}

function extractList(data: unknown): unknown[] {
  if (Array.isArray(data)) return data;
  if (data && typeof data === "object") {
    const obj = data as Record<string, unknown>;
    if (Array.isArray(obj.records)) return obj.records;
    if (Array.isArray(obj.list)) return obj.list;
    if (Array.isArray(obj.items)) return obj.items;
  }
  return [];
}

async function onSubmit(): Promise<void> {
  successMessage.value = "";
  successLink.value = "";
  try {
    const result = await actionStore.submit() as Record<string, unknown> | null;
    if (actionType.value === "promotionCodeCreate" && result) {
      const code = result["code"] as string | undefined;
      const landingUrl = result["landing_url"] as string | undefined;
      successMessage.value = `推广码 ${code ?? ""} 已生成，落地页地址：`;
      successLink.value = landingUrl ?? "";
      emit("submitted");
      return;
    }
    if (actionType.value === "purchaseShareLink" && result) {
      const purchaseNo = result["purchase_no"] as string | undefined;
      const supplierNo = result["supplier_no"] as string | undefined;
      const landingUrl = result["landing_url"] as string | undefined;
      successMessage.value = `采购单 ${purchaseNo ?? ""} → 供货商 ${supplierNo ?? ""} 推送链接已生成，将下方地址在企微会话中分享给对接人即可：`;
      successLink.value = landingUrl ?? "";
      emit("submitted");
      return;
    }
    actionStore.closeModal();
    emit("submitted");
  } catch {
    // 错误已写入 actionError，UI 渲染即可
  }
}

function closeAndReset(): void {
  successMessage.value = "";
  successLink.value = "";
  actionStore.closeModal();
}

function copyLink(): void {
  if (!successLink.value) return;
  if (navigator?.clipboard?.writeText) {
    navigator.clipboard.writeText(successLink.value).catch(() => {});
  }
}

function fieldsFor(type: string) {
  return ACTION_FIELD_DEFS[type] ?? [];
}

function onFileChange(key: string, event: Event): void {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] ?? null;
  (actionForm.value as Record<string, unknown>)[key] = file;
}
</script>

<template>
  <section v-if="actionType" class="modal-layer">
    <form class="modal" @submit.prevent="onSubmit()">
      <header>
        <h2>{{ ACTION_LABELS[actionType] ?? actionType }}</h2>
        <button type="button" class="ghost" @click="closeAndReset()">关闭</button>
      </header>
      <template v-if="!successLink">
        <p v-if="fieldsFor(actionType).length === 0" class="modal-hint">
          该操作无需额外参数，点击"提交"直接执行；操作完成后会展示结果。
        </p>
        <label v-for="field in fieldsFor(actionType)" :key="field.key" :class="{ checkbox: field.type === 'checkbox' }">
          <span>{{ field.label }}<strong v-if="field.required">*</strong></span>
          <select v-if="field.type === 'select'" v-model="actionForm[field.key]">
            <option value="">请选择</option>
            <option v-for="option in field.options ?? []" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <select v-else-if="field.type === 'remoteSelect'" v-model="actionForm[field.key]">
            <option value="">{{ remoteLoading[field.key] ? "加载中..." : "请选择" }}</option>
            <option v-for="option in remoteOptions[field.key] ?? []" :key="option.value" :value="option.value">
              {{ option.label }}（#{{ option.value }}）
            </option>
          </select>
          <textarea
            v-else-if="field.type === 'textarea' || field.type === 'json'"
            v-model="actionForm[field.key]"
            :placeholder="field.placeholder"
            rows="4"
          />
          <input
            v-else-if="field.type === 'checkbox'"
            v-model="actionForm[field.key]"
            type="checkbox"
          />
          <input
            v-else-if="field.type === 'file'"
            type="file"
            :accept="field.accept"
            @change="onFileChange(field.key, $event)"
          />
          <input
            v-else
            v-model="actionForm[field.key]"
            :type="field.type ?? 'text'"
            :placeholder="field.placeholder"
          />
        </label>
        <p v-if="actionError" class="error-line">{{ actionError }}</p>
        <footer>
          <button type="button" class="secondary" @click="closeAndReset()">取消</button>
          <button type="submit" class="primary" :disabled="actionLoading">
            {{ actionLoading ? "提交中..." : "提交" }}
          </button>
        </footer>
      </template>
      <template v-else>
        <section class="success-panel">
          <p>{{ successMessage }}</p>
          <a :href="successLink" target="_blank" rel="noopener" class="link-strong">{{ successLink }}</a>
          <p class="hint">扫描该链接的二维码即可进入公开 H5 留资页，所产生的线索会自动带上此推广码。</p>
        </section>
        <footer>
          <button type="button" class="secondary" @click="copyLink()">复制链接</button>
          <button type="button" class="primary" @click="closeAndReset()">完成</button>
        </footer>
      </template>
    </form>
  </section>
</template>

<style scoped>
.success-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 0 16px;
}
.success-panel .link-strong {
  word-break: break-all;
  color: var(--accent);
  font-weight: 600;
  text-decoration: underline;
}
.success-panel .hint {
  color: var(--muted);
  font-size: 12px;
  margin: 0;
}
.modal-hint {
  margin: 8px 0 12px;
  padding: 10px 12px;
  background: rgba(13, 148, 136, 0.08);
  color: #047857;
  border-radius: 6px;
  font-size: 13px;
}
</style>
