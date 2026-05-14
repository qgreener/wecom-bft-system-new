<script setup lang="ts">
import { healthEndpoint, surfaces } from "@wecom-bft/shared";

const surface = surfaces.supplier;
</script>

<template>
  <main class="supplier-shell">
    <section>
      <p>S1 H5 Skeleton</p>
      <h1>{{ surface.name }}</h1>
      <ul>
        <li><span>路径</span><strong>{{ surface.routeBase }}</strong></li>
        <li><span>接口</span><strong>{{ surface.apiPrefix }}</strong></li>
        <li><span>健康</span><strong>{{ healthEndpoint }}</strong></li>
      </ul>
      <small>{{ surface.authBoundary }}</small>
    </section>
  </main>
</template>
