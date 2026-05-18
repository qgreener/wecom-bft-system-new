<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { surfaces } from "@wecom-bft/shared";
import { useAuthStore } from "@/stores/auth";

const surface = surfaces.admin;
const router = useRouter();
const auth = useAuthStore();
const { loginError, loginLoading } = storeToRefs(auth);

const username = ref("DEMO_ADMIN");

async function submit(): Promise<void> {
  try {
    await auth.login(username.value);
    await router.push(auth.hasNoRoles ? "/role-application" : "/dashboard");
  } catch {
    // error 已被 store 写入 loginError
  }
}
</script>

<template>
  <main class="login-screen">
    <section class="login-card">
      <div class="brand-block">
        <div class="brand-mark">BFT</div>
        <span>{{ surface.name }}</span>
        <h1>登录</h1>
      </div>
      <label>测试账号
        <input v-model="username" type="text" @keyup.enter="submit()" />
      </label>
      <button class="primary" :disabled="loginLoading" @click="submit()">
        {{ loginLoading ? "登录中..." : "登录" }}
      </button>
      <p v-if="loginError" class="error-line">{{ loginError }}</p>
      <p class="hint">{{ surface.authBoundary }}</p>
    </section>
  </main>
</template>
