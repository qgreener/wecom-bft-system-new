<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { healthEndpoint, surfaces } from "@wecom-bft/shared";

type ApiEnvelope<T> = {
  code: string;
  message: string;
  trace_id?: string;
  data: T;
};

type LeadResponse = {
  lead_id?: number;
  lead_no?: string;
  name?: string;
  mobile?: string;
  status?: string;
  source_channel?: string;
};

type JsapiSignaturePayload = {
  corp_id: string;
  agent_id: string;
  timestamp: number;
  nonce_str: string;
  url: string;
  wx_config_signature: string;
  agent_config_signature: string;
};

declare global {
  interface Window {
    wx?: any;
    WWOpenData?: any;
  }
}

const surface = surfaces.wecomSidebar;
const token = ref(localStorage.getItem("wecom_sidebar_token") ?? "");

// 企微 JS-SDK 状态
const jssdkStatus = ref<"idle" | "loading" | "ready" | "fallback" | "error">("idle");
const jssdkError = ref("");
const externalUserId = ref("");
const externalUserName = ref("");

const lead = reactive({
  name: "",
  mobile: "",
  sourceCode: "WECOM-SIDEBAR",
  intentCourseId: "",
  remark: ""
});
const follow = reactive({
  leadId: "",
  content: "",
  nextFollowAt: ""
});
const busy = ref("");
const error = ref("");
const message = ref("");
const latestLead = ref<LeadResponse | null>(null);

const isInWeCom = computed(() => /wxwork/i.test(navigator.userAgent));
const canCreate = computed(() => token.value.trim() && /^1\d{10}$/.test(lead.mobile.trim()));
const canFollow = computed(() => token.value.trim() && follow.leadId.trim() && follow.content.trim());

function saveToken(): void {
  localStorage.setItem("wecom_sidebar_token", token.value.trim());
  message.value = "已保存当前演示登录态";
  error.value = "";
}

function idempotencyKey(scope: string): string {
  const random = crypto.randomUUID ? crypto.randomUUID() : `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`;
  return `${scope}-${random}`;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (token.value.trim()) {
    headers.set("Authorization", `Bearer ${token.value.trim()}`);
  }
  const response = await fetch(path, { ...options, headers });
  const envelope = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || !["OK", "CREATED"].includes(envelope.code)) {
    throw new Error(envelope.message || `HTTP ${response.status}`);
  }
  return envelope.data;
}

// 动态加载企微 JS-SDK 脚本
function loadWecomJsSdk(): Promise<void> {
  if (window.wx && typeof window.wx.config === "function" && typeof window.wx.agentConfig === "function") {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://res.wx.qq.com/open/js/jweixin-1.2.0.js";
    script.onload = () => {
      const extra = document.createElement("script");
      extra.src = "https://wwcdn.weixin.qq.com/node/open/js/wwLogin-1.2.7.js";
      extra.onload = () => resolve();
      extra.onerror = () => resolve();   // wwLogin 失败不影响 wx.config
      document.head.appendChild(extra);
    };
    script.onerror = () => reject(new Error("加载企微 JS-SDK 脚本失败"));
    document.head.appendChild(script);
  });
}

async function fetchSignature(): Promise<JsapiSignaturePayload> {
  const url = window.location.href.split("#")[0];
  const data = await fetch(`/api/h5/wecom/jsapi-signature?url=${encodeURIComponent(url)}`)
    .then((r) => r.json()) as ApiEnvelope<JsapiSignaturePayload>;
  if (!["OK", "CREATED"].includes(data.code)) {
    throw new Error(data.message || "签名接口异常");
  }
  return data.data;
}

function callWxConfig(payload: JsapiSignaturePayload): Promise<void> {
  return new Promise((resolve, reject) => {
    const wx = window.wx;
    if (!wx) {
      reject(new Error("wx not available"));
      return;
    }
    wx.config({
      beta: true,
      debug: false,
      appId: payload.corp_id,
      timestamp: payload.timestamp,
      nonceStr: payload.nonce_str,
      signature: payload.wx_config_signature,
      jsApiList: ["agentConfig"]
    });
    wx.ready(() => resolve());
    wx.error((err: { errMsg?: string }) => reject(new Error(err.errMsg || "wx.config error")));
  });
}

function callAgentConfig(payload: JsapiSignaturePayload): Promise<void> {
  return new Promise((resolve, reject) => {
    const wx = window.wx;
    if (!wx || typeof wx.agentConfig !== "function") {
      reject(new Error("wx.agentConfig not available"));
      return;
    }
    wx.agentConfig({
      corpid: payload.corp_id,
      agentid: payload.agent_id,
      timestamp: payload.timestamp,
      nonceStr: payload.nonce_str,
      signature: payload.agent_config_signature,
      jsApiList: ["getCurExternalContact"],
      success: () => resolve(),
      fail: (err: { errMsg?: string }) => reject(new Error(err.errMsg || "wx.agentConfig error"))
    });
  });
}

function callGetCurExternalContact(): Promise<{ userId: string; name?: string }> {
  return new Promise((resolve, reject) => {
    const wx = window.wx;
    if (!wx || typeof wx.invoke !== "function") {
      reject(new Error("wx.invoke not available"));
      return;
    }
    wx.invoke("getCurExternalContact", {}, (res: { err_msg?: string; userId?: string; name?: string }) => {
      if (res && /ok$/.test(res.err_msg ?? "")) {
        resolve({ userId: res.userId ?? "", name: res.name });
      } else {
        reject(new Error(res?.err_msg || "getCurExternalContact failed"));
      }
    });
  });
}

async function bootstrapWecom(): Promise<void> {
  if (!isInWeCom.value) {
    jssdkStatus.value = "fallback";
    return;
  }
  jssdkStatus.value = "loading";
  jssdkError.value = "";
  try {
    await loadWecomJsSdk();
    const sign = await fetchSignature();
    await callWxConfig(sign);
    await callAgentConfig(sign);
    const contact = await callGetCurExternalContact();
    externalUserId.value = contact.userId;
    externalUserName.value = contact.name ?? "";
    if (contact.name) {
      lead.name = contact.name;
    }
    jssdkStatus.value = "ready";
  } catch (e) {
    jssdkStatus.value = "error";
    jssdkError.value = e instanceof Error ? e.message : String(e);
  }
}

async function createLead(): Promise<void> {
  busy.value = "create";
  error.value = "";
  message.value = "";
  try {
    const payload: Record<string, unknown> = {
      name: lead.name.trim(),
      mobile: lead.mobile.trim(),
      source_channel: "WECOM_SIDEBAR",
      source_code: lead.sourceCode.trim(),
      intent_course_id: lead.intentCourseId ? Number(lead.intentCourseId) : null,
      remark: lead.remark.trim()
    };
    if (externalUserId.value) {
      payload.wecom_external_user_id = externalUserId.value;
    }
    const data = await request<LeadResponse>("/api/wecom/sidebar/leads", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    latestLead.value = data;
    follow.leadId = String(data.lead_id ?? "");
    message.value = `线索 ${data.lead_no ?? data.lead_id} 已进入后台线索池`;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "企微侧边栏录入失败";
  } finally {
    busy.value = "";
  }
}

async function addFollow(): Promise<void> {
  busy.value = "follow";
  error.value = "";
  message.value = "";
  try {
    await request(`/api/wecom/sidebar/leads/${follow.leadId.trim()}/follow-records`, {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey("wecom-follow") },
      body: JSON.stringify({
        follow_method: "WECOM",
        content: follow.content.trim(),
        next_follow_at: follow.nextFollowAt || null
      })
    });
    message.value = "跟进记录已写入同一线索，可在 PC 管理端查看审计与后续转化";
  } catch (e) {
    error.value = e instanceof Error ? e.message : "跟进记录提交失败";
  } finally {
    busy.value = "";
  }
}

onMounted(() => { void bootstrapWecom(); });
</script>

<template>
  <main class="phone-shell">
    <section class="top-card">
      <p>{{ surface.name }}</p>
      <h1>客户侧边协作</h1>
      <dl>
        <div><dt>入口</dt><dd>{{ surface.routeBase }}</dd></div>
        <div><dt>边界</dt><dd>{{ surface.authBoundary }}</dd></div>
        <div><dt>健康</dt><dd>{{ healthEndpoint }}</dd></div>
      </dl>
    </section>

    <section class="panel jssdk-panel" :class="jssdkStatus">
      <header>
        <strong>企微 JS-SDK 上下文</strong>
        <span v-if="jssdkStatus === 'ready'">已识别外部联系人</span>
        <span v-else-if="jssdkStatus === 'loading'">连接中…</span>
        <span v-else-if="jssdkStatus === 'fallback'">非企微环境，按手贴 token 演示</span>
        <span v-else-if="jssdkStatus === 'error'">JS-SDK 异常</span>
      </header>
      <p v-if="jssdkStatus === 'ready'" class="muted">
        external_userid =
        <code>{{ externalUserId }}</code>
        <span v-if="externalUserName"> &nbsp; 昵称：{{ externalUserName }}</span>
      </p>
      <p v-else-if="jssdkStatus === 'error'" class="error">{{ jssdkError }}</p>
      <p v-else-if="jssdkStatus === 'fallback'" class="muted">
        在企微 App 内打开聊天工具栏可自动识别当前客户，本浏览器仅做演示和兜底。
      </p>
    </section>

    <section class="panel auth-panel">
      <label>
        <span>管理端访问令牌</span>
        <input v-model="token" placeholder="Bearer token（演示时复制超管登录后的 admin_access_token）" />
      </label>
      <button type="button" class="secondary" @click="saveToken">保存</button>
    </section>

    <section class="panel">
      <header>
        <strong>录入线索</strong>
        <span v-if="externalUserId">将自动绑定 external_userid</span>
        <span v-else>正式接口</span>
      </header>
      <div class="grid-form">
        <label><span>姓名</span><input v-model="lead.name" /></label>
        <label><span>手机号</span><input v-model="lead.mobile" inputmode="tel" /></label>
        <label><span>来源码</span><input v-model="lead.sourceCode" /></label>
        <label><span>课程 ID</span><input v-model="lead.intentCourseId" inputmode="numeric" /></label>
      </div>
      <label>
        <span>备注</span>
        <textarea v-model="lead.remark" rows="3"></textarea>
      </label>
      <button type="button" :disabled="busy === 'create' || !canCreate" @click="createLead">
        {{ busy === "create" ? "提交中" : "创建线索" }}
      </button>
    </section>

    <section class="panel">
      <header>
        <strong>追加跟进</strong>
        <span>幂等写入</span>
      </header>
      <div class="grid-form">
        <label><span>线索 ID</span><input v-model="follow.leadId" inputmode="numeric" /></label>
        <label><span>下次跟进</span><input v-model="follow.nextFollowAt" type="datetime-local" /></label>
      </div>
      <label>
        <span>跟进内容</span>
        <textarea v-model="follow.content" rows="3"></textarea>
      </label>
      <button type="button" :disabled="busy === 'follow' || !canFollow" @click="addFollow">
        {{ busy === "follow" ? "写入中" : "保存跟进" }}
      </button>
    </section>

    <p v-if="message" class="toast success">{{ message }}</p>
    <p v-if="error" class="toast error">{{ error }}</p>

    <section v-if="latestLead" class="mini-result">
      <span>最近线索</span>
      <strong>{{ latestLead.lead_no ?? latestLead.lead_id }}</strong>
      <small>{{ latestLead.source_channel ?? "WECOM_SIDEBAR" }} / {{ latestLead.status ?? "PENDING_FOLLOW" }}</small>
    </section>
  </main>
</template>

<style scoped>
.jssdk-panel {
  border-left: 4px solid #cbd5e1;
}
.jssdk-panel.ready {
  border-left-color: #15803d;
}
.jssdk-panel.error {
  border-left-color: #b91c1c;
}
.jssdk-panel.fallback {
  border-left-color: #b54708;
}
.jssdk-panel header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.jssdk-panel code {
  font-family: ui-monospace, monospace;
  font-size: 12px;
  word-break: break-all;
}
.muted {
  color: #687782;
  font-size: 13px;
}
.error {
  color: #b42318;
  font-size: 13px;
}
</style>
