<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useAuthStore } from "@/stores/auth";
import { groupLabels, type AdminRoute } from "@/router/routes";

const auth = useAuthStore();
const { visibleRoutes } = storeToRefs(auth);
const route = useRoute();
const router = useRouter();
const openGroups = ref<Set<string>>(new Set());

interface RouteGroup {
  group: string;
  label: string;
  routes: AdminRoute[];
}

const groupedRoutes = computed<RouteGroup[]>(() => {
  const groups = new Map<string, AdminRoute[]>();
  for (const r of visibleRoutes.value) {
    const arr = groups.get(r.group) ?? [];
    arr.push(r);
    groups.set(r.group, arr);
  }
  return [...groups.entries()].map(([group, routes]) => ({
    group,
    label: groupLabels[group as AdminRoute["group"]] ?? group,
    routes
  }));
});

watch(
  () => route.meta.group as string | undefined,
  (group) => {
    if (!group) return;
    const next = new Set(openGroups.value);
    next.add(group);
    openGroups.value = next;
  },
  { immediate: true }
);

function navigate(path: string): void {
  router.push(path);
}

function isActive(routeKey: string): boolean {
  return route.meta.routeKey === routeKey;
}

function isGroupOpen(group: string): boolean {
  return openGroups.value.has(group);
}

function toggleGroup(group: RouteGroup): void {
  if (group.group === "home" && group.routes.length === 1) {
    navigate(group.routes[0].path);
    return;
  }
  const next = new Set(openGroups.value);
  if (next.has(group.group)) {
    next.delete(group.group);
  } else {
    next.add(group.group);
  }
  openGroups.value = next;
}

function isGroupActive(group: RouteGroup): boolean {
  return group.routes.some((r) => isActive(r.key));
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-head">
      <div class="brand-mark small">BFT</div>
      <span>管理端</span>
    </div>
    <nav>
      <section v-for="group in groupedRoutes" :key="group.group" class="menu-group">
        <button
          type="button"
          class="group-trigger"
          :class="{ active: isGroupActive(group), open: isGroupOpen(group.group) }"
          @click="toggleGroup(group)"
        >
          <span>{{ group.label }}</span>
          <small>{{ group.routes.length }}</small>
        </button>
        <div v-show="isGroupOpen(group.group)" class="submenu">
          <button
            v-for="r in group.routes"
            :key="r.key"
            type="button"
            :class="{ active: isActive(r.key) }"
            @click="navigate(r.path)"
          >{{ r.title }}</button>
        </div>
      </section>
    </nav>
  </aside>
</template>
