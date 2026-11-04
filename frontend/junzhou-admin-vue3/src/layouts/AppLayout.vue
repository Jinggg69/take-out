<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElButton, ElAlert } from 'element-plus'
import { useRouter } from 'vue-router'
import { request } from '@/utils/request'
import { workspace } from '@/stores/workspace'
import { useUserStore } from '@/stores'
import PasswordDialog from './components/PasswordDialog.vue'
import StoreWorkspace from './StoreWorkspace.vue'
const loading=ref(true), error=ref(''), password=ref(false)
const user=useUserStore(), router=useRouter()
let active=true
async function load(){loading.value=true;error.value='';try{
 const {data}=await request.get('/rbac/context');if(!active)return
 if(String(data.code)!=='1')throw new Error(data.msg||'门店加载失败')
 workspace.load(data.data.stores)
}catch(e){if(active)error.value=e instanceof Error?e.message:'门店加载失败'}finally{if(active)loading.value=false}}
async function logout(){await user.logout();await router.replace('/login')}
onMounted(load);onUnmounted(()=>{active=false;workspace.reset()})
</script>
<template>
  <div
    v-if="loading"
    role="status"
    class="workspace-state"
  >
    正在加载门店权限…
  </div>
  <div
    v-else-if="error || !workspace.storeId.value"
    class="workspace-state"
  >
    <h1>{{ error ? '暂时无法加载门店' : '暂无可访问门店' }}</h1>
    <ElAlert
      :title="error || '请联系商户管理员配置角色和门店任职。'"
      :closable="false"
    />
    <ElButton @click="load">
      重新加载
    </ElButton><ElButton @click="password=true">
      修改密码
    </ElButton><ElButton @click="logout">
      退出登录
    </ElButton>
    <PasswordDialog v-model="password" />
  </div>
  <StoreWorkspace
    v-else
    :key="workspace.revision.value"
  />
</template>
<style scoped>.workspace-state{max-width:640px;margin:12vh auto;padding:24px;display:flex;flex-wrap:wrap;gap:16px;align-items:center}.workspace-state h1{font-size:24px}</style>
