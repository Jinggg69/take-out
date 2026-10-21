<script setup lang="ts">
import { ElButton } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <main class="not-found-page">
    <section aria-labelledby="not-found-title">
      <p
        class="error-code"
        aria-hidden="true"
      >
        404
      </p>
      <h1 id="not-found-title">
        页面不存在
      </h1>
      <p class="error-description">
        请检查访问地址，或返回首页。
      </p>
      <ElButton
        type="primary"
        @click="router.replace({ name: 'welcome' })"
      >
        返回首页
      </ElButton>
    </section>
  </main>
</template>

<style scoped lang="scss">
.not-found-page {
  display: grid;
  min-height: 100dvh;
  padding: var(--jz-space-6);
  text-align: center;
  place-items: center;
}

.error-code {
  margin: 0 0 var(--jz-space-3);
  color: var(--jz-brand-primary);
  font-size: var(--jz-font-metric);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

h1 {
  margin: 0;
  font-size: var(--jz-font-title);
  line-height: 1.4;
}

.error-description {
  margin: var(--jz-space-3) 0 var(--jz-space-6);
  color: var(--jz-text-secondary);
}
</style>
