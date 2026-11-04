<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElAlert, ElButton, ElSelect, ElOption, ElTabs, ElTabPane, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { request } from '@/utils/request'
import { useUserStore } from '@/stores'
import PageHeader from '@/components/PageHeader.vue'
interface Role {code:string;name:string}
interface Grant {roleCode:string;dataScope:string;storeIds:string[]}
interface Config {
 roles:Role[];permissions:Role[];employees:{id:string;name:string;status:number}[];stores:{id:string;name:string}[]
 rolePermissions:{role_code:string;permission_code:string}[]
 assignments:{employee_id:string;role_code:string;data_scope:string}[]
 roleStores:{employee_id:string;role_code:string;store_id:string}[]
}
const config=ref<Config>(),loading=ref(true),saving=ref(false),error=ref(''),tab=ref('roles')
const role=ref(''),employee=ref(''),actions=ref<string[]>([]),grants=ref<Grant[]>([])
const router=useRouter(),user=useUserStore();let active=true
const scopes=[{value:'CURRENT_STORE',label:'本店（角色任职门店）'},{value:'SPECIFIED_STORES',label:'指定门店'},{value:'TENANT_STORES',label:'本商户全部门店'}]
const groups=computed(()=>[...new Set(config.value?.permissions.map(p=>p.code.split(':')[0]!))])
const resourceNames:Record<string,string>={dish:'菜品',category:'分类',setmeal:'套餐',order:'订单',report:'报表',employee:'员工与授权',shop:'营业设置',workspace:'工作台',file:'文件'}
const actionNames:Record<string,string>={read:'查看',create:'新增',update:'修改',delete:'删除',status:'启停',accept:'接单',reject:'拒单',cancel:'取消',deliver:'派送',complete:'完成',refund:'退款',export:'导出',manage:'管理角色与任职',upload:'上传'}
watch(role,()=>{actions.value=config.value?.rolePermissions.filter(p=>p.role_code===role.value).map(p=>p.permission_code)??[]})
watch(employee,()=>{grants.value=config.value?.assignments.filter(g=>String(g.employee_id)===employee.value).map(g=>({roleCode:g.role_code,dataScope:g.data_scope,storeIds:config.value!.roleStores.filter(s=>String(s.employee_id)===employee.value&&s.role_code===g.role_code).map(s=>String(s.store_id))}))??[]})
async function load(){loading.value=true;error.value='';try{
 const {data}=await request.get('/rbac/configuration');if(!active)return
 if(String(data.code)!=='1')throw new Error(data.msg||'授权信息加载失败')
 config.value=data.data;config.value!.employees=config.value!.employees.map(e=>({...e,id:String(e.id)}));config.value!.stores=config.value!.stores.map(s=>({...s,id:String(s.id)}))
 role.value=config.value!.roles[0]?.code??'';employee.value=config.value!.employees.find(e=>e.status===1)?.id??''
}catch(e){if(active)error.value=e instanceof Error?e.message:'加载失败'}finally{if(active)loading.value=false}}
async function save(kind:'roles'|'employees'){
 if(saving.value)return
 if(kind==='employees'&&(grants.value.some(g=>!g.roleCode)||new Set(grants.value.map(g=>g.roleCode)).size!==grants.value.length)){error.value='请选择角色，每个角色只能配置一次。';return}
 try{await ElMessageBox.confirm('保存后，本商户所有管理端会话将失效，您也需要重新登录。','保存授权变更',{confirmButtonText:'保存并重新登录',cancelButtonText:'继续编辑',type:'warning'})}catch{return}
 if(!active)return
 saving.value=true;error.value=''
 try{
  const url=kind==='roles'?`/rbac/roles/${encodeURIComponent(role.value)}/permissions`:`/rbac/employees/${employee.value}/assignments`
  const body=kind==='roles'?actions.value:grants.value.map(g=>({...g,storeIds:g.dataScope==='TENANT_STORES'?[]:g.storeIds}))
  const {data}=await request.put(url,body);if(!active)return
  if(String(data.code)!=='1')throw new Error(data.msg||'保存失败')
  user.clearSession();await router.replace('/login')
 }catch(e){if(active)error.value=e instanceof Error?e.message:'保存失败，请重试'}finally{if(active)saving.value=false}
}
onMounted(load);onUnmounted(()=>{active=false})
</script>
<template>
  <section class="access-page">
    <PageHeader title="角色与任职" />
    <p>配置员工可以执行的操作及负责的门店，每个角色单独设置范围。</p>
    <p
      v-if="loading"
      role="status"
    >
      正在加载授权信息…
    </p>
    <ElAlert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      role="alert"
    />
    <ElButton
      v-if="!config && !loading"
      @click="load"
    >
      重新加载
    </ElButton>
    <template v-if="config">
      <ElAlert
        title="请保留至少一名有效商户管理员。保存授权后，本商户所有管理端需要重新登录。"
        :closable="false"
      />
      <ElTabs v-model="tab">
        <ElTabPane
          label="角色权限"
          name="roles"
        >
          <div class="access-controls">
            <label for="role-select">选择角色</label><ElSelect
              id="role-select"
              v-model="role"
              :disabled="saving"
              aria-label="选择角色"
            >
              <ElOption
                v-for="r in config.roles"
                :key="r.code"
                :label="r.name"
                :value="r.code"
              />
            </ElSelect>
          </div>
          <fieldset
            :disabled="saving"
            class="permission-grid"
          >
            <legend>允许执行的操作</legend>
            <fieldset
              v-for="group in groups"
              :key="group"
              class="permission-group"
            >
              <legend>{{ resourceNames[group] || group }}</legend>
              <label
                v-for="p in config.permissions.filter(p=>p.code.split(':')[0]===group)"
                :key="p.code"
                class="permission-choice"
              >
                <input
                  v-model="actions"
                  type="checkbox"
                  :value="p.code"
                />{{ actionNames[p.code.split(':')[1]!] || p.name }}<small>{{ p.code }}</small>
              </label>
            </fieldset>
          </fieldset>
          <ElButton
            type="primary"
            :loading="saving"
            :disabled="!role"
            @click="save('roles')"
          >
            保存角色权限
          </ElButton>
        </ElTabPane>
        <ElTabPane
          label="员工门店任职"
          name="employees"
        >
          <div class="access-controls">
            <label for="employee-select">选择员工</label><ElSelect
              id="employee-select"
              v-model="employee"
              :disabled="saving"
              aria-label="选择员工"
            >
              <ElOption
                v-for="e in config.employees"
                :key="e.id"
                :label="e.name+(e.status===1?'':'（已停职）')"
                :value="e.id"
                :disabled="e.status!==1"
              />
            </ElSelect>
          </div>
          <p>空任职列表表示撤销该员工全部角色与门店权限。</p>
          <fieldset
            :disabled="saving"
            class="assignment-list"
          >
            <legend>角色任职</legend>
            <div
              v-for="(grant,index) in grants"
              :key="index"
              class="assignment-row"
            >
              <ElSelect
                v-model="grant.roleCode"
                :aria-label="'任职 '+(index+1)+' 的角色'"
                placeholder="选择角色"
              >
                <ElOption
                  v-for="r in config.roles"
                  :key="r.code"
                  :label="r.name"
                  :value="r.code"
                />
              </ElSelect>
              <ElSelect
                v-model="grant.dataScope"
                :aria-label="'任职 '+(index+1)+' 的范围'"
              >
                <ElOption
                  v-for="s in scopes"
                  :key="s.value"
                  :label="s.label"
                  :value="s.value"
                />
              </ElSelect>
              <ElSelect
                v-if="grant.dataScope!=='TENANT_STORES'"
                v-model="grant.storeIds"
                multiple
                :aria-label="'任职 '+(index+1)+' 的门店'"
                placeholder="选择任职门店"
              >
                <ElOption
                  v-for="s in config.stores"
                  :key="s.id"
                  :label="s.name"
                  :value="s.id"
                />
              </ElSelect>
              <p v-else>
                包含本商户以后新增的有效门店
              </p>
              <ElButton
                :aria-label="'移除任职 '+(index+1)"
                @click="grants.splice(index,1)"
              >
                移除
              </ElButton>
            </div>
          </fieldset>
          <div class="access-controls">
            <ElButton
              :disabled="saving || !employee"
              @click="grants.push({roleCode:'',dataScope:'CURRENT_STORE',storeIds:[]})"
            >
              添加任职
            </ElButton><ElButton
              type="primary"
              :loading="saving"
              :disabled="!employee"
              @click="save('employees')"
            >
              保存员工任职
            </ElButton>
          </div>
        </ElTabPane>
      </ElTabs>
    </template>
  </section>
</template>
<style scoped>
.access-page :deep(.page-header){margin-bottom:0}.access-page p{margin:0;line-height:1.6}.access-page{display:grid;gap:20px;max-width:1200px}.access-controls{display:flex;align-items:center;gap:12px;margin:20px 0;flex-wrap:wrap}.permission-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:20px;border:0;padding:12px 0 24px}.permission-group{border:1px solid var(--jz-border);border-radius:8px;padding:16px}.permission-group legend{padding:0 8px;font-weight:600}.permission-choice{display:flex;align-items:center;gap:8px;min-height:40px;flex-wrap:wrap}.permission-choice input{width:18px;height:18px;accent-color:var(--jz-brand-primary)}.permission-choice small{color:var(--jz-text-secondary);font-size:12px}.assignment-list{border:1px solid var(--jz-border);border-radius:8px;padding:16px}.assignment-row{display:grid;grid-template-columns:1fr 1.2fr 1.4fr auto;gap:12px;align-items:center;margin:12px 0}@media(max-width:900px){.assignment-row{grid-template-columns:1fr}.permission-grid{grid-template-columns:1fr}}
</style>
