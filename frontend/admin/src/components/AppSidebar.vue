<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useAuthStore } from "@/stores/auth";
import { groupLabels, type AdminRoute } from "@/router/routes";

const auth = useAuthStore();
const { visibleRoutes } = storeToRefs(auth);
const route = useRoute();
const router = useRouter();

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

function navigate(path: string): void {
  router.push(path);
}

function isActive(routeKey: string): boolean {
  return route.meta.routeKey === routeKey;
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
        <p>{{ group.label }}</p>
        <button
          v-for="r in group.routes"
          :key="r.key"
          type="button"
          :class="{ active: isActive(r.key) }"
          @click="navigate(r.path)"
        >{{ r.title }}</button>
      </section>
    </nav>
  </aside>
</template>
