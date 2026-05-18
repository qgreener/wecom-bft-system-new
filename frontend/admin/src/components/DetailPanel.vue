<script setup lang="ts">
import { type AdminRoute } from "@/router/routes";
import { fieldLabel, statusClass, statusLabel } from "@/config/status";
import { emptyText, formatCent } from "@/utils/format";
import { actionsForRoute, type RouteAction } from "@/config/actions";

type AnyRecord = Record<string, unknown>;

const props = defineProps<{
  detail: AnyRecord | null;
  route: AdminRoute;
}>();

const emit = defineEmits<{
  (e: "action", action: RouteAction, record: AnyRecord): void;
}>();

function actions(): RouteAction[] {
  return actionsForRoute(props.route.key);
}

function isPlain(val: unknown): boolean {
  return typeof val !== "object" || val === null;
}

function onAction(action: RouteAction): void {
  if (props.detail) emit("action", action, props.detail);
}
</script>

<template>
  <aside v-if="detail" class="detail-panel">
    <div class="detail-title">
      <h3>详情</h3>
      <div class="detail-actions">
        <button
          v-for="act in actions()"
          :key="act.type"
          type="button"
          @click="onAction(act)"
        >{{ act.label }}</button>
      </div>
    </div>
    <dl class="description-list">
      <template v-for="(val, key) in detail" :key="String(key)">
        <template v-if="isPlain(val)">
          <dt>{{ fieldLabel(String(key)) }}</dt>
          <dd>
            <span
              v-if="route.statusFields?.includes(String(key))"
              class="status-tag"
              :class="statusClass(val as string)"
            >{{ statusLabel(val as string) }}</span>
            <span v-else-if="route.amountFields?.includes(String(key))">{{ formatCent(val as number) }}</span>
            <span v-else>{{ emptyText(val) }}</span>
          </dd>
        </template>
      </template>
    </dl>
  </aside>
</template>
