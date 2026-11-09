<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElButton, ElDialog, ElInput, ElMessageBox, ElTable, ElTableColumn, ElTag } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { request } from '@/utils/request'
import { workspace } from '@/stores/workspace'
import { diningQr } from '@/utils/dining-qr'

interface Table { id: string; name: string; enabled: number; currentSessionId?: string }
const rows=ref<Table[]>([]), name=ref(''), busy=ref(false), error=ref(''), loading=ref(false)
const qr=ref(''), qrName=ref(''), qrVisible=ref(false)
let alive=true, serial=0
async function load(){
  const current=++serial; loading.value=true; error.value=''
  try { const {data}=await request.get('/dining'); if(!alive || serial!==current)return
    if(String(data.code)!=='1')throw new Error(data.msg || '桌台加载失败')
    rows.value=data.data
  } catch(e){if(alive)error.value=e instanceof Error?e.message:'桌台加载失败'}
  finally{if(alive && current===serial)loading.value=false}
}
async function save(row?:Table){
  if(busy.value)return
  const tableName=(row?.name || name.value).trim()
  if(!tableName || tableName.length>64){error.value='请填写1至64字桌号';return}
  busy.value=true;error.value=''
  try {const {data}=await request.post('/dining',{id:row?.id,name:tableName,enabled:row?.enabled ?? 1})
    if(String(data.code)!=='1')throw new Error(data.msg || '保存失败')
    if(alive){name.value='';await load()}
  }catch(e){if(alive)error.value=e instanceof Error?e.message:'保存失败'}finally{if(alive)busy.value=false}
}
async function code(row:Table,rotate=false){
  if(busy.value)return
  if(rotate){try{await ElMessageBox.confirm(`轮换 ${row.name} 的桌码后，旧桌牌立即失效，请重新打印。`,'轮换桌码',{confirmButtonText:'轮换',cancelButtonText:'取消'})}catch{return}}
  busy.value=true;error.value=''
  try {const {data}=await (rotate?request.put(`/dining/${row.id}/code`):request.get(`/dining/${row.id}/code`))
    if(String(data.code)!=='1')throw new Error(data.msg || '桌码读取失败')
    if(alive){qr.value=diningQr(data.data);qrName.value=row.name;qrVisible.value=true}
  }catch(e){if(alive)error.value=e instanceof Error?e.message:'桌码读取失败'}finally{if(alive)busy.value=false}
}
async function close(row:Table){
  if(busy.value)return
  try{await ElMessageBox.confirm(`结束 ${row.name} 当前桌次？本桌次须先完成或取消全部订单。`,'结束桌次',{confirmButtonText:'结束桌次',cancelButtonText:'返回'})}catch{return}
  busy.value=true;error.value=''
  try{const {data}=await request.put(`/dining/${row.id}/close`);if(String(data.code)!=='1')throw new Error(data.msg || '结束失败');if(alive)await load()}
  catch(e){if(alive)error.value=e instanceof Error?e.message:'结束失败'}finally{if(alive)busy.value=false}
}
onBeforeRouteLeave(()=>!busy.value)
onBeforeUnmount(()=>{alive=false;serial++})
void load()
</script>

<template>
  <section>
    <PageHeader title="堂食桌台">
      <ElButton
        :disabled="busy"
        @click="load"
      >
        刷新
      </ElButton>
    </PageHeader>
    <p>当前门店：{{ workspace.current.value?.name }}。顾客在小程序内扫描桌码，确认门店和桌号后点餐。</p>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <form
      v-if="workspace.can('dining:manage')"
      class="dining-create"
      @submit.prevent="save()"
    >
      <label for="dining-name">新桌号</label><ElInput
        id="dining-name"
        v-model="name"
        maxlength="64"
        :disabled="busy"
        placeholder="例如 A01"
      />
      <ElButton
        native-type="submit"
        type="primary"
        :disabled="busy"
      >
        新增桌台
      </ElButton>
    </form>
    <ElTable
      v-loading="loading"
      :data="rows"
      empty-text="暂无桌台，请新增桌台后下载桌码"
    >
      <ElTableColumn
        label="桌号"
        min-width="140"
      >
        <template #default="{row}">
          <ElInput
            v-if="workspace.can('dining:manage')"
            v-model="row.name"
            :aria-label="'桌号 '+row.name"
            :disabled="busy"
            maxlength="64"
          /><span v-else>{{ row.name }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn
        label="状态"
        width="100"
      >
        <template #default="{row}">
          <ElTag>{{ row.enabled===1?'启用':'停用' }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn
        label="当前桌次"
        min-width="160"
      >
        <template #default="{row}">
          {{ row.currentSessionId || '空闲，等待顾客扫码' }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        label="操作"
        min-width="360"
      >
        <template #default="{row}">
          <template v-if="workspace.can('dining:manage')">
            <ElButton
              :disabled="busy"
              @click="save(row as Table)"
            >
              保存桌号
            </ElButton>
            <ElButton
              :disabled="busy"
              @click="save({...row as Table,enabled:row.enabled===1?0:1})"
            >
              {{ row.enabled===1?'停用':'启用' }}
            </ElButton>
            <ElButton
              :disabled="busy"
              @click="code(row as Table)"
            >
              桌码
            </ElButton><ElButton
              :disabled="busy"
              @click="code(row as Table,true)"
            >
              轮换桌码
            </ElButton>
          </template>
          <ElButton
            v-if="workspace.can('dining:close') && row.currentSessionId"
            :disabled="busy"
            @click="close(row as Table)"
          >
            结束桌次
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
    <ElDialog
      v-model="qrVisible"
      :title="qrName+' · 堂食桌码'"
      width="420px"
    >
      <img
        :src="qr"
        :alt="qrName+'堂食二维码'"
        width="280"
        height="280"
        class="dining-qr"
      />
      <p>在均州鱼庄小程序内点击“扫码堂食”。轮换后请重新下载并替换桌牌。</p>
      <a
        :href="qr"
        :download="qrName+'-堂食桌码.svg'"
      >下载桌码 SVG</a>
    </ElDialog>
  </section>
</template>

<style scoped>
.dining-create { display:flex; flex-wrap:wrap; align-items:center; gap:16px; margin:24px 0; }
.dining-create .el-input { width:min(240px,100%); }
.dining-qr { display:block; max-width:100%; height:auto; margin:0 auto; }
p { overflow-wrap:anywhere; }
</style>
