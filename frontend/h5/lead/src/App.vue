<script setup lang="ts">
import { healthEndpoint, surfaces } from "@wecom-bft/shared";

const surface = surfaces.lead;
</script>

<template>
  <main class="lead-shell">
    <section>
      <p class="tag">S1 H5 Skeleton</p>
      <h1>{{ surface.name }}</h1>
      <div class="rows">
        <div><span>路径</span><strong>{{ surface.routeBase }}</strong></div>
        <div><span>接口</span><strong>{{ surface.apiPrefix }}</strong></div>
        <div><span>健康</span><strong>{{ healthEndpoint }}</strong></div>
      </div>
      <p class="boundary">{{ surface.authBoundary }}</p>
    </section>
  </main>
</template>
