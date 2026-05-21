<script setup lang="ts">
import { computed } from "vue";
import { statusClass, statusLabel } from "@/config/status";

const props = defineProps<{
  value: string | null | undefined;
  label?: string;
}>();

const text = computed(() => props.label ?? statusLabel(props.value));
</script>

<template>
  <span class="status-tag" :class="statusClass(value)">{{ text }}</span>
</template>
