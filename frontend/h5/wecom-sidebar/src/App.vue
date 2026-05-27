<script setup lang="ts">
import { computed, reactive, ref } from "vue";
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

const surface = surfaces.wecomSidebar;
const token = ref(localStorage.getItem("wecom_sidebar_token") ?? "");
const lead = reactive({
  name: "企微客户",
  mobile: "13970000002",
  sourceCode: "WECOM-SIDEBAR-DEMO",
  intentCourseId: "2000000000000000301",
  remark: "侧边栏录入：客户关注财务实操课和发票处理。"
});
const follow = reactive({
  leadId: "",
  content: "已确认试听意向，提醒小程序完成课程下单。",
  nextFollowAt: ""
});
const busy = ref("");
const error = ref("");
const message = ref("");
const latestLead = ref<LeadResponse | null>(null);

const canCreate = computed(() => token.value.trim() && /^1\d{10}$/.test(lead.mobile.trim()));
const canFollow = computed(() => token.value.trim() && follow.leadId.trim() && follow.content.trim());

function saveToken(): void {
  localStorage.setItem("wecom_sidebar_token", token.value.trim());
  message.value = "已保存当前企微演示登录态";
  error.value = "";
}

function idempotencyKey(scope: string): string {
  const random = crypto.randomUUID ? crypto.randomUUID() : `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`;
  return `${scope}-${random}`;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
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

async function createLead(): Promise<void> {
  busy.value = "create";
  error.value = "";
  message.value = "";
  try {
    const data = await request<LeadResponse>("/api/wecom/sidebar/leads", {
      method: "POST",
      body: JSON.stringify({
        name: lead.name.trim(),
        mobile: lead.mobile.trim(),
        source_channel: "WECOM_SIDEBAR",
        source_code: lead.sourceCode.trim(),
        intent_course_id: lead.intentCourseId ? Number(lead.intentCourseId) : null,
        remark: lead.remark.trim()
      })
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

    <section class="panel auth-panel">
      <label>
        <span>管理端访问令牌</span>
        <input v-model="token" placeholder="Bearer token，演示时可用 DEMO_OPS 登录后复制" />
      </label>
      <button type="button" class="secondary" @click="saveToken">保存</button>
    </section>

    <section class="panel">
      <header>
        <strong>录入线索</strong>
        <span>正式接口</span>
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
