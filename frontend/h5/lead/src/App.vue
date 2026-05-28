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
  source_code?: string;
  created_at?: string;
};

const surface = surfaces.lead;
const form = reactive({
  name: "",
  mobile: "",
  sourceCode: "ACCEPTANCE-LEAD-H5",
  intentCourseId: ""
});
const submitting = ref(false);
const error = ref("");
const result = ref<LeadResponse | null>(null);
const fromPromotionCode = ref(false);

const canSubmit = computed(() => {
  return form.name.trim().length >= 2 && /^1\d{10}$/.test(form.mobile.trim());
});

onMounted(() => {
  // 从 URL ?code=XXX 自动填充推广码作为来源
  const params = new URLSearchParams(window.location.search);
  const code = params.get("code");
  if (code && code.trim()) {
    form.sourceCode = code.trim();
    fromPromotionCode.value = true;
  }
});

function formatTime(value?: string): string {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}

function statusText(value?: string): string {
  const labels: Record<string, string> = {
    PENDING_FOLLOW: "待跟进",
    CONTACTED: "已联系",
    CONVERTED: "已转化",
    ABANDONED: "已放弃"
  };
  return labels[value ?? ""] ?? value ?? "-";
}

async function submitLead(): Promise<void> {
  if (!canSubmit.value) {
    error.value = "请填写姓名和 11 位手机号";
    return;
  }

  submitting.value = true;
  error.value = "";
  result.value = null;

  try {
    const response = await fetch("/api/h5/lead/leads", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: form.name.trim(),
        mobile: form.mobile.trim(),
        source_code: form.sourceCode.trim(),
        intent_course_id: form.intentCourseId ? Number(form.intentCourseId) : null
      })
    });
    const envelope = (await response.json()) as ApiEnvelope<LeadResponse>;
    if (!response.ok || !["OK", "CREATED"].includes(envelope.code)) {
      throw new Error(envelope.message || `HTTP ${response.status}`);
    }
    result.value = envelope.data;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "线索提交失败";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <main class="lead-shell">
    <section class="lead-hero">
      <div class="hero-copy">
        <p class="eyebrow">{{ surface.name }}</p>
        <h1>财务实操体验课</h1>
        <p class="hero-text">从公开留资进入线索池，后续跟进、订单和发票都可在管理端追溯。</p>
      </div>
      <div class="hero-meta">
        <span>{{ surface.routeBase }}</span>
        <span>{{ healthEndpoint }}</span>
      </div>
    </section>

    <section class="lead-card">
      <header>
        <div>
          <span class="step-index">01</span>
          <h2>公开留资</h2>
        </div>
        <strong>匿名入口</strong>
      </header>

      <p v-if="fromPromotionCode" class="message success">已识别推广码：{{ form.sourceCode }}</p>

      <form class="lead-form" @submit.prevent="submitLead">
        <label>
          <span>姓名</span>
          <input v-model="form.name" placeholder="请输入您的姓名" autocomplete="name" />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="form.mobile" placeholder="请输入 11 位手机号" inputmode="tel" autocomplete="tel" />
        </label>
        <label>
          <span>来源码</span>
          <input v-model="form.sourceCode" :readonly="fromPromotionCode" />
        </label>
        <label>
          <span>意向课程 ID（选填）</span>
          <input v-model="form.intentCourseId" inputmode="numeric" />
        </label>
        <button type="submit" :disabled="submitting || !canSubmit">
          {{ submitting ? "提交中" : "提交线索" }}
        </button>
      </form>

      <p v-if="error" class="message error">{{ error }}</p>
      <article v-if="result" class="result-panel">
        <span>已入库</span>
        <strong>{{ result.lead_no ?? result.lead_id }}</strong>
        <dl>
          <div><dt>状态</dt><dd>{{ statusText(result.status) }}</dd></div>
          <div><dt>来源</dt><dd>{{ result.source_channel ?? result.source_code ?? form.sourceCode }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ formatTime(result.created_at) }}</dd></div>
        </dl>
      </article>
    </section>

    <section class="flow-strip" aria-label="留资后续流程">
      <span>留资</span>
      <span>跟进</span>
      <span>下单</span>
      <span>支付</span>
      <span>财务协作</span>
    </section>
  </main>
</template>
