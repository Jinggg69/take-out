<script setup lang="ts">
import logoUrl from '@/assets/brand/junzhou-logo.png'
</script>

<template>
  <main class="welcome-page">
    <div class="welcome-identity">
      <img
        :src="logoUrl"
        alt="均州鱼庄"
        class="welcome-logo"
        width="144"
        height="144"
      />
      <h1>均州鱼庄管理系统</h1>
      <p>门店运营管理</p>
    </div>
  </main>
</template>

<style scoped lang="scss">
.welcome-page {
  display: grid;
  min-height: 100dvh;
  padding: var(--jz-space-6);
  place-items: center;
}

.welcome-identity {
  text-align: center;
}

.welcome-logo {
  display: block;
  width: 144px;
  height: 144px;
  margin: 0 auto var(--jz-space-6);
  object-fit: contain;
}

h1 {
  margin: 0;
  color: var(--jz-text-primary);
  font-size: var(--jz-font-title);
  font-weight: 600;
  line-height: 1.4;
}

p {
  margin: var(--jz-space-3) 0 0;
  color: var(--jz-text-secondary);
  font-size: var(--jz-font-body);
}
</style>
