<script setup lang="ts">
import { ElSelect, ElOption, ElMessageBox } from 'element-plus'
import { useRouter, isNavigationFailure, NavigationFailureType } from 'vue-router'
import { workspace, pagePermissions, pendingWrites } from '@/stores/workspace'
const router=useRouter()
async function change(id:string){
 if(id===workspace.storeId.value || pendingWrites.value>0)return
 const expected=workspace.revision.value
 try{await ElMessageBox.confirm('切换后将清空当前页面未保存的内容，并刷新列表和订单提醒。','切换门店',{confirmButtonText:'切换门店',cancelButtonText:'留在当前门店'})}catch{return}
 if(expected!==workspace.revision.value || pendingWrites.value>0)return
 const permissions=workspace.stores.value.find(s=>s.id===id)?.permissions??[]
 const target=['/order','/access','/dish','/setmeal','/category','/employee','/dashboard','/statistics'].find(path=>permissions.includes(pagePermissions[path.slice(1)]!))??'/dashboard'
 const failure=await router.replace({path:target,query:{}})
 if(isNavigationFailure(failure,NavigationFailureType.aborted|NavigationFailureType.cancelled))return
 if(expected===workspace.revision.value && pendingWrites.value===0)workspace.select(id)
}
</script>
<template>
  <ElSelect
    :model-value="workspace.storeId.value"
    :disabled="pendingWrites>0"
    aria-label="当前门店"
    style="width:180px;max-width:35vw"
    @change="change"
  >
    <ElOption
      v-for="store in workspace.stores.value"
      :key="store.id"
      :label="store.name"
      :value="store.id"
    />
  </ElSelect>
</template>
