<script setup lang="ts">
import { healthEndpoint, surfaces } from "@wecom-bft/shared";

const surface = surfaces.admin;
const cards = [
  ["入口边界", surface.routeBase],
  ["接口前缀", surface.apiPrefix],
  ["健康检查", healthEndpoint],
  ["本地端口", String(surface.port)]
];
</script>

<template>
  <main class="shell">
    <aside class="rail">
      <div class="mark">BFT</div>
      <span>admin</span>
    </aside>
    <section class="workspace">
      <header>
        <p class="eyebrow">S1 Engineering Skeleton</p>
        <h1>{{ surface.name }}</h1>
        <p>{{ surface.authBoundary }}</p>
      </header>
      <div class="grid" aria-label="admin skeleton facts">
        <article v-for="[label, value] in cards" :key="label">
          <span>{{ label }}</span>
          <strong>{{ value }}</strong>
        </article>
      </div>
    </section>
  </main>
</template>
