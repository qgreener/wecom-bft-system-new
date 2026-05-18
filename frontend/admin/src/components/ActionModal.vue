<script setup lang="ts">
import { storeToRefs } from "pinia";
import { useActionStore } from "@/stores/action";
import { ACTION_FIELD_DEFS, ACTION_LABELS } from "@/config/actions";

const emit = defineEmits<{ (e: "submitted"): void }>();

const actionStore = useActionStore();
const { actionType, actionLoading, actionError, actionForm } = storeToRefs(actionStore);

async function onSubmit(): Promise<void> {
  try {
    await actionStore.submit();
    actionStore.closeModal();
    emit("submitted");
  } catch {
    // 错误已写入 actionError，UI 渲染即可
  }
}

function fieldsFor(type: string) {
  return ACTION_FIELD_DEFS[type] ?? [];
}
</script>

<template>
  <section v-if="actionType" class="modal-layer">
    <form class="modal" @submit.prevent="onSubmit()">
      <header>
        <h2>{{ ACTION_LABELS[actionType] ?? actionType }}</h2>
        <button type="button" class="ghost" @click="actionStore.closeModal()">关闭</button>
      </header>
      <label v-for="field in fieldsFor(actionType)" :key="field.key">
        {{ field.label }}
        <input v-model="actionForm[field.key]" type="text" />
      </label>
      <p v-if="actionError" class="error-line">{{ actionError }}</p>
      <footer>
        <button type="button" class="secondary" @click="actionStore.closeModal()">取消</button>
        <button type="submit" class="primary" :disabled="actionLoading">
          {{ actionLoading ? "提交中..." : "提交" }}
        </button>
      </footer>
    </form>
  </section>
</template>
