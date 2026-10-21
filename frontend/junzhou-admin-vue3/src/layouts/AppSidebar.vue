<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { primaryNavigation } from '@/router/navigation'
import logoUrl from '@/assets/brand/junzhou-logo.png'
defineProps<{ collapsed?: boolean }>()
const emit = defineEmits<{ navigate: [] }>()
const route = useRoute()
const activeMenu = computed(() => route.meta.activeMenu || route.path)
</script>

<template>
  <aside
    class="sidebar"
    :class="{ 'is-collapsed': collapsed }"
  >
    <RouterLink
      class="sidebar-brand"
      to="/dashboard"
      aria-label="均州鱼庄管理系统首页"
      @click="emit('navigate')"
    >
      <img
        :src="logoUrl"
        alt=""
        width="40"
        height="40"
      />
      <span
        v-if="!collapsed"
        class="brand-type"
      ><strong>均州鱼庄</strong><span>管理系统</span></span>
    </RouterLink>
    <nav aria-label="主导航">
      <RouterLink
        v-for="item in primaryNavigation"
        :key="item.path"
        :to="item.path"
        class="nav-link"
        :class="{ 'is-active': activeMenu === item.path }"
        :aria-label="item.label"
        :aria-current="activeMenu === item.path ? 'page' : undefined"
        :title="collapsed ? item.label : undefined"
        @click="emit('navigate')"
      >
        <AppIcon :name="item.icon" />
        <span v-if="!collapsed">{{ item.label }}</span>
      </RouterLink>
    </nav>
  </aside>
</template>

<style scoped lang="scss">
.sidebar { display: flex; flex-direction: column; width: 100%; min-height: 100%; color: var(--jz-nav-text); background: var(--jz-nav-background); }
.sidebar-brand { display: flex; flex: none; align-items: center; gap: var(--jz-space-3); min-height: 88px; padding: var(--jz-space-4) var(--jz-space-6); color: var(--jz-brand-gold); text-decoration: none; }
.sidebar-brand img { display: block; flex: none; object-fit: contain; }
.brand-type { display: flex; flex-direction: column; min-width: 0; white-space: nowrap; }
.brand-type strong { font-size: var(--jz-font-section); font-weight: 600; }
.brand-type > span { margin-top: var(--jz-space-1); font-size: var(--jz-font-caption); color: var(--jz-nav-text); }
nav { display: grid; align-content: start; gap: var(--jz-space-2); padding: var(--jz-space-4) var(--jz-space-3); }
.nav-link { display: flex; align-items: center; gap: var(--jz-space-3); min-height: 44px; padding: var(--jz-space-3) var(--jz-space-4); border: 1px solid transparent; border-radius: var(--jz-radius-control); color: inherit; font-size: var(--jz-font-control); text-decoration: none; white-space: nowrap; }
.nav-link:hover { color: var(--jz-surface); background: var(--jz-nav-hover); }
.nav-link.is-active { color: var(--jz-brand-gold); background: var(--jz-nav-selected); font-weight: 600; }
.sidebar a:focus-visible { outline-color: var(--jz-brand-gold); outline-offset: 1px; }
.is-collapsed .sidebar-brand { padding: var(--jz-space-4) var(--jz-space-3); }
.is-collapsed nav { padding-inline: var(--jz-space-2); }
.is-collapsed .nav-link { justify-content: center; padding-inline: var(--jz-space-2); }
</style>
