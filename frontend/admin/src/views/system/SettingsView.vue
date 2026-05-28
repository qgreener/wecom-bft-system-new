<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { request } from "@/services/http";
import { formatDateTime } from "@/utils/format";

type AdminUser = {
  id: number;
  user_no: string;
  display_name: string;
  mobile?: string;
  wecom_user_id?: string;
  status: string;
  last_login_at?: string;
  created_at?: string;
  role_codes?: string[];
};

type ConfigItem = {
  config_key: string;
  display_name: string;
  config_value?: string;
  masked_value?: string;
  sensitive_flag?: boolean;
  description?: string;
};

type RolePolicy = {
  role_code: string;
  data_scope_code?: string;
  data_scope_description?: string;
  permission_codes?: string[];
  menus?: { menu_code: string; menu_name: string }[];
};

type ConfigsPage = {
  records: ConfigItem[];
  total?: number;
};

type MockScene = {
  scene_code: string;
  capability: string;
  display_name: string;
  description: string;
  default_payload?: string;
  enabled: boolean;
};

const TAB_LIST = [
  { key: "users", label: "管理员账号" },
  { key: "roles", label: "角色权限矩阵" },
  { key: "params", label: "系统参数" },
  { key: "payment", label: "支付配置" },
  { key: "invoice", label: "开票配置" },
  { key: "tax", label: "税务规则" },
  { key: "logistics", label: "物流配置" },
  { key: "mock", label: "Mock 模式" }
] as const;

type TabKey = typeof TAB_LIST[number]["key"];

const CONFIG_GROUPS: Record<string, string> = {
  params: "PURCHASE",
  payment: "PAYMENT",
  invoice: "INVOICE",
  logistics: "LOGISTICS",
  mock: "MOCK"
};

const activeTab = ref<TabKey>("users");
const loading = ref(false);
const error = ref("");
const userKeyword = ref("");
const userStatus = ref("");
const adminUsers = ref<AdminUser[]>([]);
const rolePolicies = ref<RolePolicy[]>([]);
const configRecords = ref<ConfigItem[]>([]);
const mockScenes = ref<MockScene[]>([]);
const mockTriggerTarget = ref("");
const mockTriggerInFlight = ref<string>("");

async function loadUsers(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    adminUsers.value = await request<AdminUser[]>("/api/admin/system/admin-users", {
      query: {
        keyword: userKeyword.value || undefined,
        status: userStatus.value || undefined
      }
    });
  } catch (e) {
    error.value = e instanceof Error ? e.message : "管理员列表加载失败";
  } finally {
    loading.value = false;
  }
}

async function loadRoles(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    rolePolicies.value = await request<RolePolicy[]>("/api/admin/system/role-matrix");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "角色矩阵加载失败";
  } finally {
    loading.value = false;
  }
}

async function loadConfigs(group: string): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const data = await request<ConfigsPage | ConfigItem[]>("/api/admin/system/configs", {
      query: { config_group: group, page_no: "1", page_size: "100" }
    });
    if (Array.isArray(data)) {
      configRecords.value = data;
    } else {
      configRecords.value = data.records ?? [];
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : "配置加载失败";
  } finally {
    loading.value = false;
  }
}

async function loadMockScenes(): Promise<void> {
  try {
    const data = await request<{ scenes: MockScene[] }>("/api/admin/mock/scenes");
    mockScenes.value = data.scenes ?? [];
  } catch (e) {
    // 非阻塞
    mockScenes.value = [];
  }
}

async function triggerMockScene(scene: MockScene): Promise<void> {
  mockTriggerInFlight.value = scene.scene_code;
  try {
    await request(`/api/admin/mock/scenes/${scene.scene_code}/trigger`, {
      method: "POST",
      body: JSON.stringify({
        target_no: mockTriggerTarget.value || undefined,
        trigger_reason: "演示触发"
      }),
      idempotent: true,
      idempotencyScope: `mock-${scene.scene_code}`
    });
  } catch (e) {
    error.value = e instanceof Error ? e.message : "触发失败";
  } finally {
    mockTriggerInFlight.value = "";
  }
}

async function toggleUserStatus(user: AdminUser): Promise<void> {
  const next = user.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  try {
    await request(`/api/admin/system/admin-users/${user.id}/status`, {
      method: "POST",
      body: JSON.stringify({ target_status: next }),
      idempotent: true,
      idempotencyScope: `admin-user-${user.id}-status`
    });
    await loadUsers();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "状态切换失败";
  }
}

const currentTabLoad = computed(() => {
  return () => {
    if (activeTab.value === "users") return loadUsers();
    if (activeTab.value === "roles") return loadRoles();
    if (activeTab.value === "tax") return Promise.resolve();
    const group = CONFIG_GROUPS[activeTab.value];
    if (group) {
      const p = loadConfigs(group);
      if (activeTab.value === "mock") {
        void loadMockScenes();
      }
      return p;
    }
    return Promise.resolve();
  };
});

onMounted(() => { void currentTabLoad.value(); });
watch(activeTab, () => { void currentTabLoad.value(); });
watch([userKeyword, userStatus], () => {
  if (activeTab.value === "users") void loadUsers();
});

// 角色名称中文化
const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: "超级管理员",
  EDU_ADMIN: "教务",
  TEACHER: "讲师",
  OPS: "运营",
  WAREHOUSE: "仓管",
  SERVICE: "客服",
  ACCOUNTING: "代账人员"
};

function roleLabel(code: string): string {
  return ROLE_LABELS[code] ?? code;
}

const allMenus = computed<{ code: string; name: string }[]>(() => {
  const map = new Map<string, string>();
  for (const r of rolePolicies.value) {
    for (const m of r.menus ?? []) {
      map.set(m.menu_code, m.menu_name);
    }
  }
  return [...map.entries()].map(([code, name]) => ({ code, name }));
});

function roleHasMenu(role: RolePolicy, menuCode: string): boolean {
  return (role.menus ?? []).some((m) => m.menu_code === menuCode);
}
</script>

<template>
  <section class="business-workbench settings-page">
    <section class="business-command-bar">
      <div class="business-context">
        <span>系统设置</span>
        <small>管理员账号 / 权限矩阵 / 各类配置</small>
      </div>
    </section>

    <nav class="tab-nav">
      <button
        v-for="tab in TAB_LIST"
        :key="tab.key"
        type="button"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >{{ tab.label }}</button>
    </nav>

    <section v-if="error" class="state-panel warning">
      <h3>加载失败</h3>
      <p>{{ error }}</p>
    </section>

    <!-- 管理员账号 -->
    <section v-if="activeTab === 'users'" class="state-panel">
      <header class="tab-header">
        <h3>管理员账号</h3>
        <div class="filter-inline">
          <input v-model="userKeyword" placeholder="搜索账号/姓名/手机" />
          <select v-model="userStatus">
            <option value="">全部状态</option>
            <option value="ACTIVE">启用</option>
            <option value="DISABLED">禁用</option>
          </select>
        </div>
      </header>
      <table class="mini-table">
        <thead><tr><th>账号</th><th>姓名</th><th>手机</th><th>企微 user_id</th><th>角色</th><th>状态</th><th>最近登录</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="u in adminUsers" :key="u.id">
            <td>{{ u.user_no }}</td>
            <td>{{ u.display_name }}</td>
            <td>{{ u.mobile ?? "-" }}</td>
            <td>{{ u.wecom_user_id ?? "-" }}</td>
            <td>
              <span v-for="r in u.role_codes ?? []" :key="r" class="tag">{{ roleLabel(r) }}</span>
              <span v-if="(u.role_codes ?? []).length === 0" class="muted">未分配</span>
            </td>
            <td>
              <span :class="['status-dot', u.status === 'ACTIVE' ? 'ok' : 'off']"></span>
              {{ u.status === "ACTIVE" ? "启用" : "禁用" }}
            </td>
            <td>{{ formatDateTime(u.last_login_at) }}</td>
            <td>
              <button class="ghost" @click="toggleUserStatus(u)">{{ u.status === "ACTIVE" ? "禁用" : "启用" }}</button>
            </td>
          </tr>
          <tr v-if="adminUsers.length === 0"><td colspan="8" class="empty">暂无管理员账号</td></tr>
        </tbody>
      </table>
    </section>

    <!-- 角色权限矩阵 -->
    <section v-else-if="activeTab === 'roles'" class="state-panel">
      <h3>角色权限矩阵</h3>
      <p class="hint">列：角色 × 行：可见菜单。✓ 表示该角色可见。详细按钮/字段权限以 PermissionCatalog 为准。</p>
      <div class="matrix-wrap">
        <table class="mini-table matrix-table">
          <thead>
            <tr>
              <th>菜单</th>
              <th v-for="role in rolePolicies" :key="role.role_code">{{ roleLabel(role.role_code) }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="menu in allMenus" :key="menu.code">
              <td>{{ menu.name }}<small class="muted"> ({{ menu.code }})</small></td>
              <td v-for="role in rolePolicies" :key="role.role_code">
                <span v-if="roleHasMenu(role, menu.code)" class="check-mark">✓</span>
                <span v-else class="muted">·</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 配置类 Tab：参数 / 支付 / 开票 / 物流 / Mock -->
    <section v-else-if="activeTab !== 'tax'" class="state-panel">
      <h3>{{ TAB_LIST.find(t => t.key === activeTab)?.label }}</h3>
      <p class="hint">敏感配置以脱敏值展示，更新需具备超级管理员权限。</p>
      <table class="mini-table">
        <thead><tr><th>配置项</th><th>键</th><th>值</th><th>说明</th></tr></thead>
        <tbody>
          <tr v-for="row in configRecords" :key="row.config_key">
            <td>{{ row.display_name }}</td>
            <td><code>{{ row.config_key }}</code></td>
            <td>
              <span v-if="row.sensitive_flag">{{ row.masked_value ?? '********' }}</span>
              <span v-else>{{ row.config_value ?? '-' }}</span>
            </td>
            <td class="muted">{{ row.description }}</td>
          </tr>
          <tr v-if="configRecords.length === 0"><td colspan="4" class="empty">暂无配置项</td></tr>
        </tbody>
      </table>

      <!-- Mock Tab 额外渲染可触发场景列表 -->
      <div v-if="activeTab === 'mock'" class="mock-scenes-block">
        <header class="tab-header">
          <h4>Mock 场景控制</h4>
          <label class="inline-field">
            <span>关联业务编号（选填）</span>
            <input v-model="mockTriggerTarget" placeholder="如订单号 / 退款号 / 采购号" />
          </label>
        </header>
        <p class="hint">演示当天可触发 Mock 异常分支，回调走 /api/callbacks/** 与真实平台共用同一路径。</p>
        <table class="mini-table">
          <thead><tr><th>能力</th><th>场景名</th><th>说明</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="scene in mockScenes" :key="scene.scene_code">
              <td>{{ scene.capability }}</td>
              <td>{{ scene.display_name }}<small class="muted"> ({{ scene.scene_code }})</small></td>
              <td class="muted">{{ scene.description }}</td>
              <td>
                <span :class="['status-dot', scene.enabled ? 'ok' : 'off']"></span>
                {{ scene.enabled ? "启用" : "禁用" }}
              </td>
              <td>
                <button
                  class="ghost"
                  :disabled="!scene.enabled || mockTriggerInFlight === scene.scene_code"
                  @click="triggerMockScene(scene)"
                >{{ mockTriggerInFlight === scene.scene_code ? "触发中…" : "触发" }}</button>
              </td>
            </tr>
            <tr v-if="mockScenes.length === 0"><td colspan="5" class="empty">未注册任何 Mock 场景</td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 税务规则：链入现有 /tax-rules 路由 -->
    <section v-else-if="activeTab === 'tax'" class="state-panel">
      <h3>税务规则</h3>
      <p class="hint">税务规则在专门页面中维护，请前往 <router-link to="/tax-rules">税务规则</router-link> 页面进行新增/编辑/停用。</p>
    </section>
  </section>
</template>

<style scoped>
.tab-nav {
  display: flex;
  gap: 4px;
  margin: 16px 0 12px;
  border-bottom: 1px solid var(--line);
  flex-wrap: wrap;
}
.tab-nav button {
  background: transparent;
  border: none;
  padding: 10px 16px;
  font-size: 14px;
  color: var(--muted);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.tab-nav button.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
  font-weight: 600;
}
.tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.filter-inline {
  display: inline-flex;
  gap: 8px;
}
.filter-inline input,
.filter-inline select {
  padding: 6px 10px;
  border: 1px solid var(--line);
}
.tag {
  display: inline-block;
  padding: 2px 6px;
  margin-right: 4px;
  font-size: 12px;
  background: rgba(15, 118, 110, 0.1);
  color: var(--accent);
  border-radius: 2px;
}
.muted {
  color: var(--muted);
}
.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}
.status-dot.ok {
  background: var(--accent);
}
.status-dot.off {
  background: var(--danger);
}
.empty {
  text-align: center;
  color: var(--muted);
  padding: 16px;
}
.matrix-wrap {
  overflow-x: auto;
}
.matrix-table {
  min-width: 600px;
}
.matrix-table th, .matrix-table td {
  text-align: center;
  white-space: nowrap;
}
.matrix-table td:first-child, .matrix-table th:first-child {
  text-align: left;
}
.check-mark {
  color: var(--accent);
  font-weight: 700;
}
.hint {
  color: var(--muted);
  font-size: 12px;
  margin: 4px 0 12px;
}
code {
  font-family: ui-monospace, monospace;
  font-size: 12px;
  color: var(--muted);
}
</style>
