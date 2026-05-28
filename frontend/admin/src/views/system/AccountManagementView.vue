<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { request } from "@/services/http";
import { formatDateTime } from "@/utils/format";

interface AdminUserView {
  id: number;
  user_no: string;
  display_name: string;
  mobile: string;
  wecom_user_id: string;
  status: string;
  last_login_at: string | null;
  created_at: string;
  role_codes: string[];
}

interface RolePolicy {
  role_code: string;
  data_scope_code: string;
  data_scope_description: string;
  permission_codes: string[];
  menus: Array<{ menu_code: string; menu_name: string }>;
}

const users = ref<AdminUserView[]>([]);
const roles = ref<RolePolicy[]>([]);
const loading = ref(false);
const error = ref("");
const focused = ref<AdminUserView | null>(null);
const draftRoles = ref<Set<string>>(new Set());
const saving = ref(false);
const saveError = ref("");

const roleLabel = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {};
  for (const r of roles.value) {
    map[r.role_code] = ({
      SUPER_ADMIN: "超级管理员", OPS: "运营", EDU_ADMIN: "教务",
      TEACHER: "讲师", SERVICE: "客服", WAREHOUSE: "仓管", ACCOUNTING: "代账人员"
    } as Record<string, string>)[r.role_code] ?? r.role_code;
  }
  return map;
});

async function reload(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const [u, r] = await Promise.all([
      request<AdminUserView[]>("/api/admin/system/admin-users"),
      request<RolePolicy[]>("/api/admin/system/role-matrix")
    ]);
    users.value = u ?? [];
    roles.value = r ?? [];
  } catch (e) {
    error.value = (e as { message?: string })?.message ?? "加载失败";
  } finally {
    loading.value = false;
  }
}

function openEdit(user: AdminUserView): void {
  focused.value = user;
  draftRoles.value = new Set(user.role_codes ?? []);
  saveError.value = "";
}

function toggleRole(code: string): void {
  const next = new Set(draftRoles.value);
  if (next.has(code)) next.delete(code);
  else next.add(code);
  draftRoles.value = next;
}

async function saveRoles(): Promise<void> {
  if (!focused.value) return;
  saving.value = true;
  saveError.value = "";
  try {
    const updated = await request<AdminUserView>(
      `/api/admin/system/admin-users/${focused.value.id}/roles`,
      {
        method: "POST",
        body: JSON.stringify({ role_codes: [...draftRoles.value] }),
        idempotent: true,
        idempotencyScope: "admin-user-roles"
      }
    );
    if (updated) {
      const idx = users.value.findIndex((u) => u.id === updated.id);
      if (idx >= 0) users.value[idx] = updated;
    }
    focused.value = null;
  } catch (e) {
    saveError.value = (e as { message?: string })?.message ?? "保存失败";
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(user: AdminUserView): Promise<void> {
  const next = user.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  if (!confirm(`确认将 ${user.display_name} 状态改为 ${next}？`)) return;
  try {
    const updated = await request<AdminUserView>(
      `/api/admin/system/admin-users/${user.id}/status`,
      {
        method: "POST",
        body: JSON.stringify({ target_status: next }),
        idempotent: true,
        idempotencyScope: "admin-user-status"
      }
    );
    if (updated) {
      const idx = users.value.findIndex((u) => u.id === updated.id);
      if (idx >= 0) users.value[idx] = updated;
    }
  } catch (e) {
    alert((e as { message?: string })?.message ?? "状态切换失败");
  }
}

function statusText(s: string): string {
  return s === "ACTIVE" ? "启用" : s === "DISABLED" ? "停用" : s;
}

onMounted(() => {
  void reload();
});
</script>

<template>
  <section class="account-page">
    <header class="account-head">
      <div>
        <h2>账号与角色</h2>
        <p>管理内部账号的启停与角色分配。角色变更后用户重新登录即可生效。</p>
      </div>
      <button class="secondary" :disabled="loading" @click="reload()">{{ loading ? "刷新中" : "刷新" }}</button>
    </header>

    <article v-if="error" class="state-panel warning">
      <p>{{ error }}</p>
    </article>

    <table class="account-table">
      <thead>
        <tr>
          <th>账号</th>
          <th>姓名</th>
          <th>手机号</th>
          <th>角色</th>
          <th>状态</th>
          <th>最后登录</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in users" :key="user.id">
          <td><code>{{ user.user_no }}</code></td>
          <td>{{ user.display_name }}</td>
          <td>{{ user.mobile ?? "-" }}</td>
          <td>
            <span v-if="(user.role_codes ?? []).length === 0" class="muted-line">未分配</span>
            <span v-for="code in user.role_codes ?? []" :key="code" class="role-chip">{{ roleLabel[code] ?? code }}</span>
          </td>
          <td>
            <span :class="user.status === 'ACTIVE' ? 'status-tag pass' : 'status-tag warn'">{{ statusText(user.status) }}</span>
          </td>
          <td>{{ formatDateTime(user.last_login_at) }}</td>
          <td>
            <button class="ghost" @click="openEdit(user)">修改角色</button>
            <button class="ghost" @click="toggleStatus(user)">
              {{ user.status === "ACTIVE" ? "停用" : "启用" }}
            </button>
          </td>
        </tr>
        <tr v-if="users.length === 0 && !loading">
          <td colspan="7" class="empty-line">暂无账号</td>
        </tr>
      </tbody>
    </table>

    <section v-if="focused" class="modal-layer" @click.self="focused = null">
      <article class="modal">
        <header>
          <h3>修改 {{ focused.display_name }} 的角色</h3>
          <button class="ghost" @click="focused = null">关闭</button>
        </header>
        <p class="muted-line">勾选目标角色，保存后该账号会被覆盖式更新为所选集合。</p>
        <div class="role-list">
          <label v-for="r in roles" :key="r.role_code" class="role-check">
            <input
              type="checkbox"
              :checked="draftRoles.has(r.role_code)"
              @change="toggleRole(r.role_code)"
            />
            <div>
              <strong>{{ roleLabel[r.role_code] ?? r.role_code }}</strong>
              <small>{{ r.data_scope_description }}</small>
            </div>
          </label>
        </div>
        <p v-if="saveError" class="error-line">{{ saveError }}</p>
        <footer>
          <button class="secondary" @click="focused = null">取消</button>
          <button class="primary" :disabled="saving" @click="saveRoles()">{{ saving ? "保存中" : "保存" }}</button>
        </footer>
      </article>
    </section>
  </section>
</template>

<style scoped>
.account-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.account-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.account-head h2 { margin: 0; }
.account-head p { margin: 4px 0 0; color: var(--muted); }
.account-table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border: 1px solid var(--line);
}
.account-table th,
.account-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}
.role-chip {
  display: inline-block;
  padding: 2px 8px;
  margin-right: 4px;
  border-radius: 12px;
  background: rgba(13, 148, 136, 0.12);
  color: #047857;
  font-size: 12px;
}
.role-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 12px 0;
}
.role-check {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  cursor: pointer;
}
.role-check small {
  display: block;
  color: var(--muted);
  font-size: 12px;
  margin-top: 2px;
}
.empty-line { text-align: center; color: var(--muted); padding: 24px 0; }
</style>
