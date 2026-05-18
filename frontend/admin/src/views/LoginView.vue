<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { surfaces } from "@wecom-bft/shared";
import { useAuthStore } from "@/stores/auth";

const surface = surfaces.admin;
const router = useRouter();
const route = useRoute();
const auth = useAuthStore();
const { loginError, loginLoading } = storeToRefs(auth);

const username = ref("DEMO_ADMIN");
const oauthError = ref("");

onMounted(() => {
  const errCode = route.query.oauth_error as string | undefined;
  const errMsg = route.query.oauth_message as string | undefined;
  if (errCode) {
    oauthError.value = errMsg ? `${errCode}：${errMsg}` : errCode;
  }
});

async function submit(): Promise<void> {
  try {
    await auth.login(username.value);
    await router.push(auth.hasNoRoles ? "/role-application" : "/dashboard");
  } catch {
    // error 已被 store 写入 loginError
  }
}

function startWecomLogin(): void {
  // 后端 302 跳到企微，授权后回调 callback，callback 再 302 回前端 /oauth-success?token=...
  window.location.href = "/api/admin/auth/wecom-oauth/start-redirect";
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
      <button class="secondary" type="button" @click="startWecomLogin()">
        使用企业微信登录
      </button>
      <p v-if="loginError" class="error-line">{{ loginError }}</p>
      <p v-if="oauthError" class="error-line">{{ oauthError }}</p>
      <p class="hint">{{ surface.authBoundary }}</p>
    </section>
  </main>
</template>
