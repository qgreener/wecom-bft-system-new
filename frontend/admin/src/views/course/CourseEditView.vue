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
  status?: string;
  contains_physical?: boolean;
  tax_rule_id?: number;
};

type LessonNode = {
  lesson_id?: number;
  node_no?: string;
  node_title?: string;
  node_type?: string;
  status?: string;
  live_start_at?: string;
  live_end_at?: string;
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
    contains_physical: spec?.contains_physical,
    tax_rule_id: spec?.tax_rule_id,
    status: spec?.status
  } as Record<string, unknown>);
}

function openLessonModal(node?: LessonNode): void {
  actionStore.openModal("lessonNodeSave", {
    course_id: detail.value?.course_id,
    lesson_id: node?.lesson_id,
    node_title: node?.node_title,
    node_type: node?.node_type,
    parent_id: node?.parent_id,
    live_start_at: node?.live_start_at,
    live_end_at: node?.live_end_at,
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

onMounted(() => { void load(); });

const specsList = computed(() => detail.value?.specs ?? []);
const lessonList = computed(() => detail.value?.lesson_summary ?? []);
</script>

<template>
  <section class="business-workbench course-edit">
    <section class="business-command-bar">
      <div class="business-context">
        <a class="back-link" href="javascript:void(0)" @click="back()">← 返回课程列表</a>
        <span>课程编辑</span>
        <small v-if="detail">{{ detail.course_no }}</small>
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
      <article class="edit-card">
        <header class="edit-card-head">
          <h3>基本信息</h3>
          <span><StatusTag :value="detail.status" /></span>
        </header>
        <div class="edit-form">
          <label>
            <span>课程名称</span>
            <input v-model="baseForm.course_title" @input="baseDirty = true" />
          </label>
          <label>
            <span>课程类型</span>
            <select v-model="baseForm.course_type" @change="baseDirty = true">
              <option value="LIVE">直播课</option>
              <option value="RECORDED">录制课</option>
            </select>
          </label>
          <label>
            <span>封面 URL</span>
            <input v-model="baseForm.cover_url" @input="baseDirty = true" />
          </label>
          <label>
            <span>课程类目</span>
            <input v-model="baseForm.category_code" @input="baseDirty = true" />
          </label>
          <label class="full">
            <span>课程摘要</span>
            <textarea v-model="baseForm.summary" rows="2" @input="baseDirty = true"></textarea>
          </label>
          <label class="full">
            <span>课程详情</span>
            <textarea v-model="baseForm.detail" rows="4" @input="baseDirty = true"></textarea>
          </label>
          <label>
            <span>负责讲师 ID</span>
            <input v-model="baseForm.teacher_user_id" type="number" @input="baseDirty = true" />
          </label>
          <label>
            <span>默认税务规则 ID</span>
            <input v-model="baseForm.default_tax_rule_id" type="number" @input="baseDirty = true" />
          </label>
          <label class="full">
            <span>课程群入群二维码（文件号）</span>
            <input v-model="baseForm.course_group_qr" @input="baseDirty = true" />
          </label>
        </div>
        <footer class="edit-card-foot">
          <button class="primary" :disabled="!baseDirty || saving" @click="saveBase()">{{ saving ? "保存中…" : "保存基本信息" }}</button>
        </footer>
      </article>

      <!-- 售卖规格 -->
      <article class="edit-card">
        <header class="edit-card-head">
          <h3>售卖规格</h3>
          <button class="secondary" @click="openSpecModal()">新增规格</button>
        </header>
        <table class="mini-table">
          <thead><tr><th>规格名</th><th>单价</th><th>含实物</th><th>税务规则 ID</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="spec in specsList" :key="spec.spec_id">
              <td>{{ spec.spec_name ?? "-" }}</td>
              <td>{{ formatCent(spec.sale_price_cent ?? 0) }}</td>
              <td>{{ spec.contains_physical ? "是" : "否" }}</td>
              <td>{{ spec.tax_rule_id ?? "-" }}</td>
              <td><StatusTag v-if="spec.status" :value="spec.status" /></td>
              <td><button class="ghost" @click="openSpecModal(spec)">编辑</button></td>
            </tr>
            <tr v-if="specsList.length === 0"><td colspan="6" class="empty">暂无规格，点击"新增规格"添加</td></tr>
          </tbody>
        </table>
      </article>

      <!-- 章节小节 -->
      <article class="edit-card">
        <header class="edit-card-head">
          <h3>章节小节</h3>
          <button class="secondary" @click="openLessonModal()">新增章节/小节</button>
        </header>
        <table class="mini-table">
          <thead><tr><th>编号</th><th>标题</th><th>类型</th><th>状态</th><th>直播时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="node in lessonList" :key="node.lesson_id">
              <td>{{ node.node_no ?? "-" }}</td>
              <td>{{ node.node_title ?? "-" }}</td>
              <td>{{ node.node_type ?? "-" }}</td>
              <td><StatusTag v-if="node.status" :value="node.status" /></td>
              <td>{{ formatDateTime(node.live_start_at) }}</td>
              <td><button class="ghost" @click="openLessonModal(node)">编辑</button></td>
            </tr>
            <tr v-if="lessonList.length === 0"><td colspan="6" class="empty">暂无章节，点击"新增章节/小节"添加</td></tr>
          </tbody>
        </table>
      </article>
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
  padding: 16px;
}
.edit-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--line);
}
.edit-card-head h3 {
  margin: 0;
  font-size: 15px;
}
.edit-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}
.edit-form label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--muted);
}
.edit-form label.full {
  grid-column: span 2;
}
.edit-form input,
.edit-form select,
.edit-form textarea {
  padding: 6px 10px;
  border: 1px solid var(--line);
  font-size: 14px;
  color: var(--ink);
}
.edit-card-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.empty {
  text-align: center;
  color: var(--muted);
  padding: 16px;
}
</style>
