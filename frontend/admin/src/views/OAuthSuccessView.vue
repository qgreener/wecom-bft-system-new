<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { setAccessToken } from "@/services/http";
import { useAuthStore } from "@/stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const error = ref("");

onMounted(async () => {
  const token = route.query.token as string | undefined;
  if (!token) {
    error.value = "OAuth 回调缺少 token";
    setTimeout(() => router.replace("/login"), 1500);
    return;
  }
  try {
    setAccessToken(token);
    auth.markAuthenticated();
    await auth.loadUser();
    await router.replace(auth.hasNoRoles ? "/role-application" : "/dashboard");
  } catch (e: unknown) {
    error.value = (e as { message?: string })?.message ?? "登录态注入失败";
    setTimeout(() => router.replace("/login"), 1500);
  }
});
</script>

<template>
  <main class="login-screen">
    <section class="login-card">
      <p v-if="!error">登录中，请稍候...</p>
      <p v-else class="error-line">{{ error }}</p>
    </section>
  </main>
</template>
