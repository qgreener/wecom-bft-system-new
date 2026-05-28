<script setup lang="ts">
import { ref } from "vue";
import { storeToRefs } from "pinia";
import { useActionStore } from "@/stores/action";
import { ACTION_FIELD_DEFS, ACTION_LABELS } from "@/config/actions";

const emit = defineEmits<{ (e: "submitted"): void }>();

const actionStore = useActionStore();
const { actionType, actionLoading, actionError, actionForm } = storeToRefs(actionStore);

const successMessage = ref("");
const successLink = ref("");

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
  // actionForm 类型是 Record<string, any>，可直接赋值 File 对象，buildFormData 会识别并走 multipart
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
        <label v-for="field in fieldsFor(actionType)" :key="field.key" :class="{ checkbox: field.type === 'checkbox' }">
          <span>{{ field.label }}<strong v-if="field.required">*</strong></span>
          <select v-if="field.type === 'select'" v-model="actionForm[field.key]">
            <option value="">请选择</option>
            <option v-for="option in field.options ?? []" :key="option.value" :value="option.value">
              {{ option.label }}
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
</style>
