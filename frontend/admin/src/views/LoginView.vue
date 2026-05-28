<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { surfaces } from "@wecom-bft/shared";
import { useAuthStore } from "@/stores/auth";
import { request } from "@/services/http";

const surface = surfaces.admin;
const router = useRouter();
const route = useRoute();
const auth = useAuthStore();
const { loginError, loginLoading } = storeToRefs(auth);

const oauthError = ref("");
const showFallback = ref(false);
const fallbackUserNo = ref("DEMO_ADMIN");
const qrError = ref("");

interface QrConfig {
  corp_id: string;
  agent_id: string;
  redirect_uri: string;
  state: string;
}

onMounted(async () => {
  const errCode = route.query.oauth_error as string | undefined;
  const errMsg = route.query.oauth_message as string | undefined;
  if (errCode) {
    oauthError.value = errMsg ? `${errCode}：${errMsg}` : errCode;
  }
  await renderWecomQr();
});

async function renderWecomQr(): Promise<void> {
  try {
    const cfg = await request<QrConfig>("/api/admin/auth/wecom-oauth/qr-config");
    if (!cfg?.corp_id || !cfg?.agent_id) {
      qrError.value = "未配置企微 corpId/agentId，请联系管理员";
      return;
    }
    await loadWwLogin();
    const w = window as unknown as {
      ww?: {
        register: (config: Record<string, unknown>) => void;
        createWWLoginPanel: (opts: Record<string, unknown>) => unknown;
      };
    };
    if (!w.ww || typeof w.ww.createWWLoginPanel !== "function") {
      qrError.value = "企微登录脚本加载失败";
      return;
    }
    // 渲染企微 SSO 登录面板（官方文档 path/98478）
    w.ww.createWWLoginPanel({
      el: "#wecom-qr-container",
      params: {
        login_type: "CorpApp",
        appid: cfg.corp_id,
        agentid: cfg.agent_id,
        // 官方文档明确：redirect_uri 无需 URLEncode
        redirect_uri: cfg.redirect_uri,
        state: cfg.state,
        redirect_type: "callback",
        lang: "zh"
      },
      onCheckWeComLogin: () => undefined,
      onLoginSuccess: ({ code }: { code: string }) => {
        // redirect_type=callback 时由前端拿 code 跳到后端 callback
        const redirect = `/api/admin/auth/wecom-oauth/callback?code=${encodeURIComponent(code)}&state=${encodeURIComponent(cfg.state)}`;
        window.location.href = redirect;
      },
      onLoginFail: (err: unknown) => {
        qrError.value = "企微登录失败：" + JSON.stringify(err);
      }
    });
  } catch (e) {
    qrError.value = (e as { message?: string })?.message ?? "二维码加载失败";
  }
}

function loadWwLogin(): Promise<void> {
  return new Promise((resolve, reject) => {
    const w = window as unknown as { ww?: unknown };
    if (w.ww && typeof (w.ww as { createWWLoginPanel?: unknown }).createWWLoginPanel === "function") {
      resolve();
      return;
    }
    const existing = document.getElementById("wecom-wwlogin-script") as HTMLScriptElement | null;
    if (existing) {
      existing.addEventListener("load", () => resolve());
      existing.addEventListener("error", () => reject(new Error("WwLogin 加载失败")));
      return;
    }
    const script = document.createElement("script");
    script.id = "wecom-wwlogin-script";
    script.src = "https://wwcdn.weixin.qq.com/node/wework/wwopen/js/wecom-jssdk-2.4.0.js";
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("WwLogin 加载失败"));
    document.head.appendChild(script);
  });
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
      <p class="login-tip">请使用企业微信扫描下方二维码完成登录。</p>
      <div id="wecom-qr-container" class="wecom-qr"></div>
      <p v-if="qrError" class="error-line">{{ qrError }}</p>
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
  margin: 0 0 16px;
  color: var(--muted);
}
.wecom-qr {
  width: 100%;
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.wecom-qr :deep(iframe) {
  border: 0;
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
