<script setup lang="ts">
import { onMounted, ref } from "vue";
import { request } from "@/services/http";
import { formatDateTime } from "@/utils/format";

interface PromotionCodeView {
  code: string;
  name: string;
  channel: string;
  description: string;
  landing_url: string;
  created_at: string;
}

const codes = ref<PromotionCodeView[]>([]);
const loading = ref(false);
const error = ref("");
const focused = ref<PromotionCodeView | null>(null);
const newName = ref("");
const newChannel = ref("XHS");
const submitting = ref(false);
const submitError = ref("");

async function reload(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const data = await request<PromotionCodeView[]>("/api/admin/promotion-codes");
    codes.value = data ?? [];
  } catch (e) {
    error.value = (e as { message?: string })?.message ?? "加载失败";
  } finally {
    loading.value = false;
  }
}

async function submit(): Promise<void> {
  if (!newName.value.trim()) {
    submitError.value = "请填写推广码名称";
    return;
  }
  submitting.value = true;
  submitError.value = "";
  try {
    const created = await request<PromotionCodeView>("/api/admin/promotion-codes", {
      method: "POST",
      body: JSON.stringify({ name: newName.value.trim(), channel: newChannel.value }),
      idempotent: true,
      idempotencyScope: "promotion-code-create"
    });
    if (created) codes.value = [created, ...codes.value];
    newName.value = "";
    focused.value = created;
  } catch (e) {
    submitError.value = (e as { message?: string })?.message ?? "创建失败";
  } finally {
    submitting.value = false;
  }
}

function copy(text: string): void {
  if (!text) return;
  if (navigator?.clipboard?.writeText) {
    navigator.clipboard.writeText(text).catch(() => {});
  }
}

function channelLabel(value: string): string {
  return ({
    XHS: "小红书", WECHAT_MOMENTS: "朋友圈", WECHAT_OFFICIAL: "公众号",
    DOUYIN: "抖音", OTHER: "其他"
  } as Record<string, string>)[value] ?? value;
}

onMounted(() => {
  void reload();
});
</script>

<template>
  <section class="promotion-page">
    <header class="promotion-head">
      <div>
        <h2>推广码管理</h2>
        <p>已生成的推广码可在此随时查看和复制；扫码后落地到留资 H5 表单。</p>
      </div>
      <button class="secondary" :disabled="loading" @click="reload()">{{ loading ? "刷新中" : "刷新" }}</button>
    </header>

    <article class="promotion-create">
      <h3>新建推广码</h3>
      <div class="create-row">
        <label>名称
          <input v-model="newName" placeholder="如：小红书618活动" />
        </label>
        <label>渠道
          <select v-model="newChannel">
            <option value="XHS">小红书</option>
            <option value="WECHAT_MOMENTS">朋友圈</option>
            <option value="WECHAT_OFFICIAL">公众号</option>
            <option value="DOUYIN">抖音</option>
            <option value="OTHER">其他</option>
          </select>
        </label>
        <button class="primary" :disabled="submitting" @click="submit()">{{ submitting ? "生成中" : "生成" }}</button>
      </div>
      <p v-if="submitError" class="error-line">{{ submitError }}</p>
    </article>

    <article v-if="error" class="state-panel warning">
      <h3>加载失败</h3>
      <p>{{ error }}</p>
    </article>

    <table class="promotion-table">
      <thead>
        <tr>
          <th>名称</th>
          <th>渠道</th>
          <th>推广码</th>
          <th>落地页</th>
          <th>生成时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in codes" :key="row.code">
          <td>{{ row.name }}</td>
          <td>{{ channelLabel(row.channel) }}</td>
          <td><code>{{ row.code }}</code></td>
          <td class="ellipsis">
            <a :href="row.landing_url" target="_blank" rel="noopener">{{ row.landing_url }}</a>
          </td>
          <td>{{ formatDateTime(row.created_at) }}</td>
          <td><button class="ghost" @click="focused = row">查看 / 复制</button></td>
        </tr>
        <tr v-if="codes.length === 0 && !loading">
          <td colspan="6" class="empty-line">暂无推广码</td>
        </tr>
      </tbody>
    </table>

    <section v-if="focused" class="modal-layer" @click.self="focused = null">
      <article class="modal">
        <header>
          <h3>{{ focused.name }} 落地页</h3>
          <button class="ghost" @click="focused = null">关闭</button>
        </header>
        <p class="muted-line">渠道：{{ channelLabel(focused.channel) }} · 推广码 <code>{{ focused.code }}</code></p>
        <div class="qr-wrapper">
          <img
            :src="`https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=${encodeURIComponent(focused.landing_url)}`"
            alt="落地页二维码"
          />
        </div>
        <div class="link-row">
          <a :href="focused.landing_url" target="_blank" rel="noopener" class="link-strong">{{ focused.landing_url }}</a>
          <button class="secondary" @click="copy(focused.landing_url)">复制链接</button>
        </div>
      </article>
    </section>
  </section>
</template>

<style scoped>
.promotion-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.promotion-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.promotion-head h2 { margin: 0; }
.promotion-head p { margin: 4px 0 0; color: var(--muted); }
.promotion-create {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 14px 16px;
}
.promotion-create h3 { margin: 0 0 12px; font-size: 14px; }
.create-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 200px auto;
  gap: 12px;
  align-items: end;
}
.create-row label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.create-row input, .create-row select { padding: 8px 10px; border: 1px solid var(--line); }
.promotion-table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border: 1px solid var(--line);
}
.promotion-table th,
.promotion-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}
.promotion-table code { background: rgba(15, 23, 42, 0.05); padding: 2px 6px; border-radius: 4px; }
.empty-line { text-align: center; color: var(--muted); padding: 24px 0; }
.ellipsis {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.qr-wrapper {
  display: flex;
  justify-content: center;
  margin: 16px 0;
}
.link-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.link-strong { color: var(--accent); word-break: break-all; }
</style>
