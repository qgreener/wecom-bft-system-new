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

const oauthError = ref("");
const showFallback = ref(false);
const fallbackUserNo = ref("DEMO_ADMIN");

onMounted(() => {
  const errCode = route.query.oauth_error as string | undefined;
  const errMsg = route.query.oauth_message as string | undefined;
  if (errCode) {
    oauthError.value = errMsg ? `${errCode}：${errMsg}` : errCode;
  }
});

function startWecomLogin(): void {
  // 后端 302 跳到企微，授权后回调 callback，callback 再 302 回前端 /oauth-success?token=...
  window.location.href = "/api/admin/auth/wecom-oauth/start-redirect";
}

async function submitFallback(): Promise<void> {
  try {
    await auth.login(fallbackUserNo.value);
    await router.push(auth.hasNoRoles ? "/role-application" : "/dashboard");
  } catch {
    // error 已被 store 写入 loginError
  }
}
</script>

<template>
  <main class="login-screen">
    <section class="login-card wide">
      <div class="brand-block">
        <div class="brand-mark">BFT</div>
        <span>{{ surface.name }}</span>
      </div>
      <h1 class="login-title">企业微信扫码登录</h1>
      <p class="login-tip">
        请使用企业微信扫描下方按钮跳转后的二维码，或在企业微信工作台内点击应用直接登录。
      </p>
      <div class="login-actions">
        <button class="primary login-cta" type="button" @click="startWecomLogin()">
          使用企业微信登录
        </button>
      </div>
      <p v-if="oauthError" class="error-line">{{ oauthError }}</p>
      <p class="hint">{{ surface.authBoundary }}</p>

      <details class="fallback-details" :open="showFallback">
        <summary @click="showFallback = !showFallback">演示兜底入口（仅在企微不可用时使用）</summary>
        <div class="fallback-body">
          <label>测试账号编号
            <input v-model="fallbackUserNo" type="text" @keyup.enter="submitFallback()" />
          </label>
          <button class="secondary" :disabled="loginLoading" @click="submitFallback()">
            {{ loginLoading ? "登录中..." : "测试登录" }}
          </button>
          <p v-if="loginError" class="error-line">{{ loginError }}</p>
        </div>
      </details>
    </section>
  </main>
</template>

<style scoped>
.login-title {
  margin: 4px 0 8px;
  font-size: 22px;
  color: var(--ink);
}
.login-tip {
  margin: 0 0 22px;
  color: var(--muted);
}
.login-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.login-cta {
  font-size: 16px;
  padding: 14px 18px;
}
.fallback-details {
  margin-top: 18px;
  border-top: 1px dashed var(--line);
  padding-top: 12px;
}
.fallback-details summary {
  cursor: pointer;
  font-size: 13px;
  color: var(--muted);
  user-select: none;
  list-style: none;
}
.fallback-details summary::-webkit-details-marker {
  display: none;
}
.fallback-body {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.fallback-body label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}
.fallback-body input {
  padding: 8px 10px;
  border: 1px solid var(--line);
}
</style>
