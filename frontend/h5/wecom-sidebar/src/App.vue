<script setup lang="ts">
import { healthEndpoint, surfaces } from "@wecom-bft/shared";

const surface = surfaces.wecomSidebar;
</script>

<template>
  <main class="phone-shell">
    <section class="panel">
      <p class="tag">S1 H5 Skeleton</p>
      <h1>{{ surface.name }}</h1>
      <dl>
        <div><dt>路径</dt><dd>{{ surface.routeBase }}</dd></div>
        <div><dt>接口</dt><dd>{{ surface.apiPrefix }}</dd></div>
        <div><dt>健康</dt><dd>{{ healthEndpoint }}</dd></div>
      </dl>
      <p class="boundary">{{ surface.authBoundary }}</p>
    </section>
  </main>
</template>
