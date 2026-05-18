<script setup lang="ts">
import { useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { surfaces } from "@wecom-bft/shared";
import { useAuthStore } from "@/stores/auth";

const surface = surfaces.admin;
const router = useRouter();
const auth = useAuthStore();
const { roleApp, roleAppError, roleAppLoading, roleAppSubmitted } = storeToRefs(auth);

async function submit(): Promise<void> {
  try {
    await auth.submitRoleApplication();
  } catch {
    // 错误已写入 roleAppError
  }
}

function logout(): void {
  auth.logout();
  router.push("/login");
}
</script>

<template>
  <main class="login-screen">
    <section class="login-card wide">
      <div class="brand-block">
        <div class="brand-mark">BFT</div>
        <span>{{ surface.name }}</span>
        <h1>未分配权限</h1>
      </div>
      <p v-if="roleAppSubmitted" class="success-line">角色申请已提交，请等待管理员审核。</p>
      <form v-else @submit.prevent="submit()">
        <label>申请角色
          <select v-model="roleApp.role_code">
            <option value="WAREHOUSE">仓管</option>
            <option value="OPS">运营</option>
            <option value="EDU_ADMIN">教务</option>
            <option value="TEACHER">讲师</option>
            <option value="CS">客服</option>
            <option value="ACCOUNTING">代账人员</option>
          </select>
        </label>
        <label>申请理由
          <textarea v-model="roleApp.submit_reason" rows="3"></textarea>
        </label>
        <button class="primary" type="submit" :disabled="roleAppLoading">
          {{ roleAppLoading ? "提交中..." : "提交申请" }}
        </button>
      </form>
      <p v-if="roleAppError" class="error-line">{{ roleAppError }}</p>
      <button class="ghost" @click="logout()">退出登录</button>
    </section>
  </main>
</template>
