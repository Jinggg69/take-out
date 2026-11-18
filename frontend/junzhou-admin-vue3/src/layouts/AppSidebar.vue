<script setup lang="ts">
import { canVisit, workspace } from '@/stores/workspace'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { sidebarNavigation, type SidebarItem } from '@/router/navigation'
import { useAppStore } from '@/stores'
const props = defineProps<{ collapsed?: boolean }>()
const emit = defineEmits<{ navigate: [] }>()
const route = useRoute()
const activeMenu = computed(() => route.meta.activeMenu || route.path)
const app = useAppStore()
const navigation = computed(() => sidebarNavigation(canVisit))
const openGroups = ref<string[]>([])
const groupActive = (item: SidebarItem) => item.children?.some(child => child.path === activeMenu.value) || false
watch([activeMenu, navigation], () => {
  for (const item of navigation.value) if (groupActive(item) && !openGroups.value.includes(item.path)) openGroups.value.push(item.path)
}, { immediate: true })
function toggleGroup(item: SidebarItem) {
  if (props.collapsed) { app.setSidebarOpen(true); if (!openGroups.value.includes(item.path)) openGroups.value.push(item.path) }
  else if (openGroups.value.includes(item.path)) openGroups.value = openGroups.value.filter(path => path !== item.path)
  else openGroups.value.push(item.path)
}

</script>

<template>
  <aside
    class="sidebar"
    :class="{ 'is-collapsed': collapsed }"
  >
    <RouterLink
      class="sidebar-brand"
      to="/dashboard"
      aria-label="均州餐饮点餐营销管理系统首页"
      @click="emit('navigate')"
    >
      <svg
        class="platform-mark"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linecap="round"
        aria-hidden="true"
      >
        <path d="m4 3 5 5M2 5l5 5M6 1l5 5c2 2-2 6-4 4L2 5m7 5 11 11M21 2c-5 1-8 6-5 9L3 22m17-20-5 9" />
      </svg>
      <span
        v-if="!collapsed"
        class="brand-type"
        title="均州餐饮点餐营销管理系统"
      >均州餐饮点餐营销管理系统</span>
    </RouterLink>
    <section
      v-if="!collapsed"
      class="merchant-identity"
      aria-label="当前商户与身份"
    >
      <strong :title="workspace.identity.value.tenantName">{{ workspace.identity.value.tenantName || '商户信息未提供' }}</strong>
      <span class="identity-roles">（{{ workspace.identity.value.roleNames.join('、') || '商户员工' }}）</span>
    </section>
    <nav aria-label="主导航">
      <template
        v-for="item in navigation"
        :key="item.path"
      >
        <div
          v-if="item.children"
          class="nav-group"
        >
          <button
            type="button"
            class="nav-link group-trigger"
            :class="{ 'is-active': groupActive(item) }"
            :aria-expanded="!collapsed && openGroups.includes(item.path)"
            :aria-controls="!collapsed ? `${item.path}-navigation` : undefined"
            :title="collapsed ? item.label : undefined"
            :aria-label="item.label"
            @click="toggleGroup(item)"
          >
            <AppIcon :name="item.icon" />
            <span v-if="!collapsed">{{ item.label }}</span>
            <AppIcon
              v-if="!collapsed"
              name="chevron"
              class="group-arrow"
              :class="{ 'is-open': openGroups.includes(item.path) }"
            />
          </button>
          <div
            v-if="!collapsed"
            v-show="openGroups.includes(item.path)"
            :id="`${item.path}-navigation`"
            class="group-children"
          >
            <RouterLink
              v-for="child in item.children"
              :key="child.path"
              :to="child.path"
              class="nav-link child-link"
              :class="{ 'is-active': activeMenu === child.path }"
              :aria-current="activeMenu === child.path ? 'page' : undefined"
              @click="emit('navigate')"
            >
              <AppIcon :name="child.icon" /><span>{{ child.label }}</span>
            </RouterLink>
          </div>
        </div>
        <RouterLink
          v-else
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
      </template>
    </nav>
  </aside>
</template>

<style scoped lang="scss">
.sidebar { display: flex; flex-direction: column; width: 100%; min-height: 100%; color: var(--jz-nav-text); background: var(--jz-nav-background); }
.sidebar-brand { display: flex; flex: none; align-items: center; gap: 7px; height: 50px; min-width: 0; padding: 0 12px; color: #e3e6ea; text-decoration: none; }
.platform-mark { display: block; width: 18px; height: 18px; flex: none; color: #bbc2cb; }
.brand-type { display: block; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; font-weight: 500; }
.merchant-identity { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; margin: 0 10px 10px; padding: 9px 10px; min-height: 56px; box-sizing: border-box; border: 1px solid #665055; border-radius: 2px; background: #493438; color: #f0e8ea; text-align: center; }
.merchant-identity strong { max-width: 100%; overflow-wrap: anywhere; color: #f0e8ea; font-size: 14px; line-height: 1.5; font-weight: 500; }
.identity-roles { color: #cdc0c4; font-size: 12px; line-height: 1.5; font-weight: 400; }
nav { display: grid; align-content: start; gap: var(--jz-space-2); padding: var(--jz-space-4) var(--jz-space-3); }
.nav-link { display: flex; align-items: center; gap: var(--jz-space-3); min-height: 44px; padding: var(--jz-space-3) var(--jz-space-4); border: 1px solid transparent; border-radius: var(--jz-radius-control); color: inherit; font-size: var(--jz-font-control); text-decoration: none; white-space: nowrap; }
.group-trigger { width: 100%; background: none; cursor: pointer; font-family: inherit; text-align: left; }
.group-arrow { margin-left: auto; width: 14px; height: 14px; transform: rotate(-90deg); }
.group-arrow.is-open { transform: rotate(0); }
.group-children { display: grid; gap: 4px; padding-top: 4px; }
.child-link { padding-left: 32px; font-size: 13px; }
.sidebar button:focus-visible { outline: 2px solid var(--jz-brand-gold); outline-offset: 1px; }
.nav-link:hover { color: var(--jz-surface); background: var(--jz-nav-hover); }
.nav-link.is-active { color: var(--jz-brand-gold); background: var(--jz-nav-selected); font-weight: 600; }
.sidebar a:focus-visible { outline-color: var(--jz-brand-gold); outline-offset: 1px; }
.is-collapsed .sidebar-brand { justify-content: center; padding: 0 8px; }
.is-collapsed nav { padding-inline: var(--jz-space-2); }
.is-collapsed .nav-link { justify-content: center; padding-inline: var(--jz-space-2); }
</style>
