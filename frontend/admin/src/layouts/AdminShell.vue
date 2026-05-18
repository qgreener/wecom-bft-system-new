<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useAuthStore } from "@/stores/auth";
import AppSidebar from "@/components/AppSidebar.vue";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const { currentUser } = storeToRefs(auth);

const pageTitle = computed<string>(() => (route.meta.title as string | undefined) ?? "首页");
const selectedId = computed<string | null>(() => {
  const id = route.params.id;
  if (typeof id === "string") return id;
  if (Array.isArray(id) && id.length > 0) return id[0];
  return null;
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
        <div class="breadcrumb">
          <span>首页</span>
          <span>/</span>
          <span>{{ pageTitle }}</span>
          <span v-if="selectedId">/</span>
          <span v-if="selectedId">{{ selectedId }}</span>
        </div>
        <h1>{{ pageTitle }}</h1>
        <div class="userbox">
          <span>{{ currentUser?.display_name ?? "-" }}</span>
          <span class="roles">{{ roles }}</span>
          <button class="ghost" @click="logout()">退出</button>
        </div>
      </header>

      <router-view />
    </section>
  </main>
</template>
