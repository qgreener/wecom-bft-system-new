<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { request } from "@/services/http";
import { useActionStore } from "@/stores/action";
import ActionModal from "@/components/ActionModal.vue";
import StatusTag from "@/components/admin/StatusTag.vue";
import { formatCent, formatDateTime } from "@/utils/format";

type CourseSpec = {
  spec_id?: number;
  spec_name?: string;
  sale_price_cent?: number;
  origin_price_cent?: number;
  status?: string;
  contains_physical?: boolean;
  sku_id?: number;
  gift_sku_id?: number;
  tax_rule_id?: number;
  amount_split_snapshot?: string;
};

type LessonNode = {
  lesson_id?: number;
  node_no?: string;
  node_title?: string;
  node_type?: string;
  lesson_type?: string;
  status?: string;
  live_start_at?: string;
  live_end_at?: string;
  replay_url?: string;
  parent_id?: number | null;
};

type CourseDetail = {
  course_id: number;
  course_no: string;
  course_title: string;
  course_type: string;
  cover_url?: string;
  summary?: string;
  detail?: string;
  teacher_user_id?: number;
  category_code?: string;
  course_group_qr?: string;
  default_tax_rule_id?: number;
  status: string;
  specs?: CourseSpec[];
  lesson_summary?: LessonNode[];
};

interface Option { value: string; label: string }

const props = defineProps<{ courseId: string }>();
const router = useRouter();
const actionStore = useActionStore();

const detail = ref<CourseDetail | null>(null);
const loading = ref(false);
const error = ref("");

const baseForm = ref({
  course_title: "",
  course_type: "LIVE",
  cover_url: "",
  summary: "",
  detail: "",
  teacher_user_id: "",
  category_code: "",
  course_group_qr: "",
  default_tax_rule_id: ""
});
const baseDirty = ref(false);
const saving = ref(false);

const teacherOptions = ref<Option[]>([]);
const taxRuleOptions = ref<Option[]>([]);

async function loadOptions(): Promise<void> {
  try {
    const [users, rules] = await Promise.all([
      request<Array<Record<string, unknown>>>("/api/admin/system/admin-users?status=ACTIVE"),
      request<Array<Record<string, unknown>>>("/api/admin/tax-rules")
    ]);
    teacherOptions.value = (users ?? []).map((u) => ({
      value: String(u.id ?? ""),
      label: String(u.display_name ?? u.user_no ?? "")
    })).filter((o) => o.value);
    taxRuleOptions.value = (rules ?? []).map((r) => ({
      value: String(r.rule_id ?? r.id ?? ""),
      label: String(r.rule_name ?? r.name ?? "")
    })).filter((o) => o.value);
  } catch {
    // 下拉拉不到时退化为手填，不影响主流程
  }
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const data = await request<CourseDetail>(`/api/admin/courses/${props.courseId}`);
    detail.value = data;
    baseForm.value = {
      course_title: data.course_title ?? "",
      course_type: data.course_type ?? "LIVE",
      cover_url: data.cover_url ?? "",
      summary: data.summary ?? "",
      detail: data.detail ?? "",
      teacher_user_id: data.teacher_user_id == null ? "" : String(data.teacher_user_id),
      category_code: data.category_code ?? "",
      course_group_qr: data.course_group_qr ?? "",
      default_tax_rule_id: data.default_tax_rule_id == null ? "" : String(data.default_tax_rule_id)
    };
    baseDirty.value = false;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "课程详情加载失败";
  } finally {
    loading.value = false;
  }
}

async function saveBase(): Promise<void> {
  if (!detail.value) return;
  saving.value = true;
  error.value = "";
  try {
    const payload: Record<string, unknown> = {
      course_id: detail.value.course_id,
      course_title: baseForm.value.course_title.trim(),
      course_type: baseForm.value.course_type,
      cover_url: baseForm.value.cover_url || undefined,
      summary: baseForm.value.summary || undefined,
      detail: baseForm.value.detail || undefined,
      category_code: baseForm.value.category_code || undefined,
      course_group_qr: baseForm.value.course_group_qr || undefined
    };
    if (baseForm.value.teacher_user_id) payload.teacher_user_id = Number(baseForm.value.teacher_user_id);
    if (baseForm.value.default_tax_rule_id) payload.default_tax_rule_id = Number(baseForm.value.default_tax_rule_id);
    await request("/api/admin/courses", {
      method: "POST",
      body: JSON.stringify(payload),
      idempotent: true,
      idempotencyScope: `course-base-${detail.value.course_id}`
    });
    await load();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "保存失败";
  } finally {
    saving.value = false;
  }
}

function openSpecModal(spec?: CourseSpec): void {
  actionStore.openModal("courseSpecSave", {
    course_id: detail.value?.course_id,
    spec_id: spec?.spec_id,
    spec_name: spec?.spec_name,
    sale_price_cent: spec?.sale_price_cent,
    origin_price_cent: spec?.origin_price_cent,
    contains_physical: spec?.contains_physical,
    sku_id: spec?.sku_id,
    gift_sku_id: spec?.gift_sku_id,
    tax_rule_id: spec?.tax_rule_id,
    amount_split_snapshot: spec?.amount_split_snapshot,
    status: spec?.status
  } as Record<string, unknown>);
}

function openLessonModal(node?: LessonNode): void {
  actionStore.openModal("lessonNodeSave", {
    course_id: detail.value?.course_id,
    lesson_id: node?.lesson_id,
    node_title: node?.node_title,
    title: node?.node_title,
    node_type: node?.node_type,
    lesson_type: node?.lesson_type,
    parent_node_id: node?.parent_id,
    live_start_at: node?.live_start_at,
    live_end_at: node?.live_end_at,
    replay_url: node?.replay_url,
    status: node?.status
  } as Record<string, unknown>);
}

function openApprovalModal(action: "ON_SHELF" | "OFF_SHELF" | "DELETE"): void {
  actionStore.openModal("courseApproval", {
    course_id: detail.value?.course_id,
    target_action: action
  } as Record<string, unknown>);
}

function back(): void {
  router.push("/courses");
}

onMounted(() => {
  void loadOptions();
  void load();
});

const specsList = computed(() => detail.value?.specs ?? []);
const lessonList = computed(() => detail.value?.lesson_summary ?? []);

function teacherLabel(id?: number): string {
  if (id == null) return "-";
  const found = teacherOptions.value.find((o) => o.value === String(id));
  return found?.label ?? `#${id}`;
}
</script>

<template>
  <section class="business-workbench course-edit">
    <section class="business-command-bar">
      <div class="business-context">
        <a class="back-link" href="javascript:void(0)" @click="back()">← 返回课程列表</a>
        <span>{{ detail ? "编辑课程" : "课程编辑" }}</span>
        <small v-if="detail">{{ detail.course_no }} · {{ detail.course_title }}</small>
      </div>
      <div class="head-actions">
        <button class="ghost" :disabled="loading" @click="load()">{{ loading ? "刷新中" : "刷新" }}</button>
        <button class="secondary" @click="openApprovalModal('ON_SHELF')">申请上架</button>
        <button class="secondary" @click="openApprovalModal('OFF_SHELF')">申请下架</button>
        <button class="danger" @click="openApprovalModal('DELETE')">申请删除</button>
      </div>
    </section>

    <section v-if="error" class="state-panel warning">
      <h3>加载失败</h3>
      <p>{{ error }}</p>
    </section>

    <section v-if="detail" class="course-edit-grid">
      <!-- 基本信息 -->
      <details class="edit-card" open>
        <summary class="edit-card-head">
          <h3>基本信息</h3>
          <span><StatusTag :value="detail.status" /></span>
        </summary>
        <div class="edit-form">
          <label>
            <span>课程名称<strong>*</strong></span>
            <input v-model="baseForm.course_title" @input="baseDirty = true" />
          </label>
          <label>
            <span>课程类型</span>
            <select v-model="baseForm.course_type" @change="baseDirty = true">
              <option value="LIVE">直播课</option>
              <option value="RECORDED">录制课</option>
            </select>
          </label>
          <label class="full">
            <span>课程封面</span>
            <div class="cover-row">
              <img v-if="baseForm.cover_url" :src="baseForm.cover_url" class="cover-preview" alt="封面预览" />
              <input v-model="baseForm.cover_url" placeholder="填写图片 URL（演示阶段，可后续替换为上传）" @input="baseDirty = true" />
            </div>
          </label>
          <label class="full">
            <span>课程详情（富文本占位）</span>
            <textarea v-model="baseForm.detail" rows="4" @input="baseDirty = true"></textarea>
          </label>
          <label class="full">
            <span>课程摘要</span>
            <textarea v-model="baseForm.summary" rows="2" @input="baseDirty = true"></textarea>
          </label>
          <label>
            <span>负责讲师</span>
            <select v-if="teacherOptions.length" v-model="baseForm.teacher_user_id" @change="baseDirty = true">
              <option value="">请选择</option>
              <option v-for="o in teacherOptions" :key="o.value" :value="o.value">{{ o.label }}（#{{ o.value }}）</option>
            </select>
            <input v-else v-model="baseForm.teacher_user_id" type="number" placeholder="讲师用户 ID" @input="baseDirty = true" />
          </label>
          <label>
            <span>默认税务规则</span>
            <select v-if="taxRuleOptions.length" v-model="baseForm.default_tax_rule_id" @change="baseDirty = true">
              <option value="">请选择</option>
              <option v-for="o in taxRuleOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
            <input v-else v-model="baseForm.default_tax_rule_id" type="number" placeholder="税务规则 ID" @input="baseDirty = true" />
          </label>
          <label>
            <span>课程类目</span>
            <input v-model="baseForm.category_code" placeholder="类目编码" @input="baseDirty = true" />
          </label>
        </div>
        <footer class="edit-card-foot">
          <button class="primary" :disabled="!baseDirty || saving" @click="saveBase()">{{ saving ? "保存中…" : "保存基本信息" }}</button>
        </footer>
      </details>

      <!-- 售卖规格 -->
      <details class="edit-card" open>
        <summary class="edit-card-head">
          <h3>售卖规格 <small class="muted-line">课程价格由最低规格价自动确定</small></h3>
          <button class="secondary" @click.stop="openSpecModal()">添加规格</button>
        </summary>
        <table class="mini-table">
          <thead><tr><th>规格名</th><th>价格</th><th>税务规则</th><th>包含实物</th><th>赠品 SKU</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="spec in specsList" :key="spec.spec_id">
              <td>{{ spec.spec_name ?? "-" }}</td>
              <td>{{ formatCent(spec.sale_price_cent ?? 0) }}</td>
              <td>{{ spec.tax_rule_id ?? "-" }}</td>
              <td>{{ spec.contains_physical ? `SKU #${spec.sku_id ?? "?"}` : "否" }}</td>
              <td>{{ spec.gift_sku_id ? `SKU #${spec.gift_sku_id}` : "无" }}</td>
              <td><StatusTag v-if="spec.status" :value="spec.status" /></td>
              <td><button class="ghost" @click="openSpecModal(spec)">编辑</button></td>
            </tr>
            <tr v-if="specsList.length === 0"><td colspan="7" class="empty">暂无规格，点击"添加规格"开始配置</td></tr>
          </tbody>
        </table>
      </details>

      <!-- 课程群区 -->
      <details class="edit-card">
        <summary class="edit-card-head">
          <h3>课程群</h3>
          <span class="muted-line">仅维护群编号 / 入群二维码，由教务在企微中线下建群</span>
        </summary>
        <div class="course-group">
          <img v-if="baseForm.course_group_qr" :src="baseForm.course_group_qr" class="qr-preview" alt="课程群二维码" />
          <div class="qr-meta">
            <label>
              <span>入群二维码地址或文件号</span>
              <input v-model="baseForm.course_group_qr" placeholder="支持 URL 或上传后返回的 file-no" @input="baseDirty = true" />
            </label>
            <p class="muted-line">未配置时小程序端会自动隐藏入群提示。</p>
          </div>
        </div>
      </details>

      <!-- 课程安排 -->
      <details class="edit-card" open>
        <summary class="edit-card-head">
          <h3>课程安排</h3>
          <button class="secondary" @click.stop="openLessonModal()">添加章节/小节</button>
        </summary>
        <table class="mini-table">
          <thead><tr><th>编号</th><th>标题</th><th>类型</th><th>状态</th><th>直播时间</th><th>回放</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="node in lessonList" :key="node.lesson_id">
              <td>{{ node.node_no ?? "-" }}</td>
              <td>{{ node.node_title ?? "-" }}</td>
              <td>{{ node.node_type === "CHAPTER" ? "章节" : node.lesson_type === "LIVE" ? "直播课节" : node.lesson_type === "RECORDED" ? "录制课节" : (node.node_type ?? "-") }}</td>
              <td><StatusTag v-if="node.status" :value="node.status" /></td>
              <td>{{ formatDateTime(node.live_start_at) }}</td>
              <td>
                <a v-if="node.replay_url" :href="node.replay_url" target="_blank" rel="noopener">查看回放</a>
                <span v-else class="muted-line">-</span>
              </td>
              <td><button class="ghost" @click="openLessonModal(node)">编辑</button></td>
            </tr>
            <tr v-if="lessonList.length === 0"><td colspan="7" class="empty">暂无章节，点击"添加章节/小节"开始</td></tr>
          </tbody>
        </table>
      </details>

      <p v-if="detail" class="footer-hint muted-line">
        当前讲师：{{ teacherLabel(detail.teacher_user_id) }} · 课程状态：<StatusTag :value="detail.status" />
      </p>
    </section>

    <ActionModal @submitted="load()" />
  </section>
</template>

<style scoped>
.course-edit .back-link {
  color: var(--accent);
  font-size: 13px;
  text-decoration: none;
  margin-right: 12px;
}
.course-edit-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}
.edit-card {
  border: 1px solid var(--line);
  background: #fff;
  padding: 0;
}
.edit-card[open] > .edit-card-head {
  border-bottom: 1px solid var(--line);
}
.edit-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  list-style: none;
}
.edit-card-head::-webkit-details-marker { display: none; }
.edit-card-head h3 {
  margin: 0;
  font-size: 15px;
  display: flex;
  gap: 10px;
  align-items: baseline;
}
.edit-card-head h3 small {
  font-size: 12px;
  font-weight: normal;
}
.edit-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
  padding: 16px;
}
.edit-form label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--muted);
}
.edit-form label strong { color: var(--danger); }
.edit-form label.full { grid-column: span 2; }
.edit-form input,
.edit-form select,
.edit-form textarea {
  padding: 6px 10px;
  border: 1px solid var(--line);
  font-size: 14px;
  color: var(--ink);
}
.cover-row { display: flex; gap: 12px; align-items: flex-start; }
.cover-row input { flex: 1; }
.cover-preview {
  width: 96px;
  height: 96px;
  object-fit: cover;
  border: 1px solid var(--line);
  border-radius: 4px;
}
.edit-card-foot {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--line);
}
.mini-table { margin: 0; }
.course-group {
  display: flex;
  gap: 16px;
  padding: 16px;
}
.qr-preview {
  width: 120px;
  height: 120px;
  border: 1px solid var(--line);
  object-fit: cover;
}
.qr-meta { flex: 1; display: flex; flex-direction: column; gap: 8px; }
.empty {
  text-align: center;
  color: var(--muted);
  padding: 16px;
}
.footer-hint { padding: 0 4px; }
</style>
