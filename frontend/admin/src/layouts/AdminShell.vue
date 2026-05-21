<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useAuthStore } from "@/stores/auth";
import { usePageStore } from "@/stores/page";
import AppSidebar from "@/components/AppSidebar.vue";
import { groupLabels, routeRegistry, type AdminRoute, type RouteKey } from "@/router/routes";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const pageStore = usePageStore();
const { currentUser } = storeToRefs(auth);
const { selectedId: activeDetailId } = storeToRefs(pageStore);

const currentAdminRoute = computed(() => {
  const routeKey = route.meta.routeKey as RouteKey | undefined;
  return routeKey ? routeRegistry.find((item) => item.key === routeKey) : undefined;
});

const pageTitle = computed<string>(() => currentAdminRoute.value?.title ?? (route.meta.title as string | undefined) ?? "首页");
const groupLabel = computed<string | null>(() => {
  const group = currentAdminRoute.value?.group ?? (route.meta.group as AdminRoute["group"] | undefined);
  return group ? groupLabels[group] ?? group : null;
});
const selectedId = computed<string | null>(() => {
  const id = route.params.id;
  if (typeof id === "string") return id;
  if (Array.isArray(id) && id.length > 0) return id[0];
  return activeDetailId.value;
});
const breadcrumbItems = computed<string[]>(() => {
  const items = ["首页"];
  const section = groupLabel.value;
  const title = pageTitle.value;

  if (section && section !== "首页") items.push(section);
  if (title && title !== "首页" && title !== section) items.push(title);
  if (selectedId.value) items.push(selectedId.value);

  return items;
});

const roles = computed<string>(() => {
  const r = currentUser.value?.roles as unknown[] | undefined;
  if (!Array.isArray(r) || r.length === 0) return "-";
  return r
    .map((item) => (typeof item === "string" ? item : (item as { role_code?: string }).role_code ?? ""))
    .filter(Boolean)
    .join(", ");
});

function logout(): void {
  auth.logout();
  router.push("/login");
}
</script>

<template>
  <main class="admin-shell">
    <AppSidebar />

    <section class="workspace">
      <header class="topbar">
        <div class="breadcrumb" aria-label="当前位置">
          <template v-for="(item, index) in breadcrumbItems" :key="`${item}-${index}`">
            <span>{{ item }}</span>
            <span v-if="index < breadcrumbItems.length - 1" class="separator">/</span>
          </template>
        </div>
        <div class="userbox">
          <div class="user-meta">
            <span>{{ currentUser?.display_name ?? "-" }}</span>
            <span class="roles">{{ roles }}</span>
          </div>
          <button class="ghost" @click="logout()">退出</button>
        </div>
      </header>

      <router-view />
    </section>
  </main>
</template>
