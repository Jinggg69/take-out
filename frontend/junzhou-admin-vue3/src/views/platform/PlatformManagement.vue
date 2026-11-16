<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElMessageBox, ElPagination, ElTable, ElTableColumn } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import FormFeedback from '@/components/FormFeedback.vue'
import { createPlatformApi } from '@/api/platform'
import type { PlatformFields, PlatformRecord, PlatformResource } from '@/api/platform'
import { platformRequest } from '@/utils/platform-request'
import { errorMessage } from '@/utils/error-message'
import { createPlatformList } from './list-state'
import { validatePlatformForm } from './form'

const props = defineProps<{ resource: PlatformResource }>()
const title = computed(() => props.resource === 'tenants' ? '商户' : '门店')
const api = createPlatformApi(platformRequest, props.resource)
const list = createPlatformList(api), state = list.state
const editing = ref<PlatformRecord | null>(null), formOpen = ref(false), saving = ref(false), busy = ref('')
const errors = ref<Record<string, string>>({}), message = ref('')
const feedback = ref<InstanceType<typeof FormFeedback>>()
const form = reactive<PlatformFields>({ code: '', name: '' })
const isTenant = computed(() => props.resource === 'tenants')
const storeFields = computed(() => isTenant.value ? form.store! : form)
const storePrefix = computed(() => isTenant.value ? 'store.' : '')
function open(row?: PlatformRecord) {
  editing.value = row ?? null; errors.value = {}; message.value = ''
  for (const key of Object.keys(form)) delete (form as unknown as Record<string, unknown>)[key]
  Object.assign(form, row ? { code: row.code, name: row.name, ...(isTenant.value ? {} : { tenantId: row.tenantId, address: row.address ?? '', latitude: row.latitude ?? null, longitude: row.longitude ?? null }) } : {
    code: '', name: '', ...(isTenant.value ? { adminUsername: '', adminPassword: '', adminName: '', store: { code: '', name: '', address: '', latitude: null, longitude: null } } : { tenantId: '', address: '', latitude: null, longitude: null }),
  })
  formOpen.value = true
}
async function save() {
  if (saving.value) return
  errors.value = validatePlatformForm(form, props.resource, Boolean(editing.value)); message.value = ''
  if (Object.keys(errors.value).length) { message.value = '请检查下方标记的字段'; await nextTick(); feedback.value?.focus(); return }
  saving.value = true
  try {
    const payload = { ...form, code: form.code.trim(), name: form.name.trim() }
    const { data } = editing.value ? await api.update(editing.value.id, payload) : await api.create(payload)
    if (String(data.code) !== '1') throw new Error(data.msg || '保存失败，请重试')
    form.adminPassword = ''; formOpen.value = false; ElMessage.success(`${title.value}保存成功`); await list.load()
  } catch (cause) { message.value = errorMessage(cause, '保存失败，请检查网络后重试'); await nextTick(); feedback.value?.focus() }
  finally { saving.value = false }
}
async function action(row: PlatformRecord, remove: boolean) {
  if (busy.value) return
  const verb = remove ? '删除' : Number(row.status) === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`${verb}${title.value}“${row.name}”？${remove ? '删除后无法恢复；未完成订单、支付或退款会阻止删除。' : verb === '停用' ? isTenant.value ? '全部门店将一并停用，相关账号会话将失效。重新启用商户后需单独启用门店。' : '相关账号会话将失效。' : ''}`, `确认${verb}`, { confirmButtonText: verb, cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  busy.value = String(row.id)
  try {
    const { data } = remove ? await api.remove(row.id) : await api.setStatus(row.id, Number(row.status) === 1 ? 0 : 1)
    if (String(data.code) !== '1') throw new Error(data.msg || `${verb}失败`)
    ElMessage.success(`${verb}成功`); await list.load()
  } catch (cause) { ElMessage.error(errorMessage(cause, `${verb}失败，请重试`)) }
  finally { busy.value = '' }
}
function search() { state.page = 1; void list.load() }
function reset() { state.keyword = ''; state.tenantId = ''; search() }
onMounted(list.load)
onUnmounted(list.dispose)
onBeforeRouteLeave(to => to.path === '/login' || (!saving.value && !busy.value))
</script>

<template>
  <section>
    <PageHeader :title="formOpen ? `${editing ? '编辑' : '新建'}${title}` : `${title}管理`">
      <ElButton
        v-if="!formOpen"
        type="primary"
        @click="open()"
      >
        新建{{ title }}
      </ElButton>
      <ElButton
        v-else
        :disabled="saving"
        @click="formOpen = false; form.adminPassword = ''"
      >
        返回列表
      </ElButton>
    </PageHeader>
    <div
      v-if="formOpen"
      class="management-panel management-form-panel"
    >
      <FormFeedback
        ref="feedback"
        :message="message"
      />
      <ElForm
        class="management-form"
        label-position="top"
        :disabled="saving"
        @submit.prevent="save"
      >
        <ElFormItem
          v-if="!isTenant"
          label="所属商户 ID"
          for="platform-tenant"
          :error="errors.tenantId"
        >
          <ElInput
            id="platform-tenant"
            v-model="form.tenantId"
            :disabled="Boolean(editing)"
            placeholder="在商户管理中查看商户 ID"
          />
        </ElFormItem>
        <ElFormItem
          :label="`${title}编码`"
          for="platform-code"
          :error="errors.code"
        >
          <ElInput
            id="platform-code"
            v-model="form.code"
            maxlength="64"
          />
        </ElFormItem>
        <ElFormItem
          :label="`${title}名称`"
          for="platform-name"
          :error="errors.name"
        >
          <ElInput
            id="platform-name"
            v-model="form.name"
            maxlength="100"
          />
        </ElFormItem>
        <template v-if="isTenant && !editing">
          <h2>商户管理员</h2>
          <p class="management-form-hint">
            创建后请通过安全方式将初始密码交给管理员；账号不能以 platform_ 开头。
          </p>
          <ElFormItem
            label="管理员账号"
            for="platform-admin"
            :error="errors.adminUsername"
          >
            <ElInput
              id="platform-admin"
              v-model="form.adminUsername"
              autocomplete="off"
              maxlength="64"
            />
          </ElFormItem>
          <ElFormItem
            label="管理员姓名"
            for="platform-admin-name"
            :error="errors.adminName"
          >
            <ElInput
              id="platform-admin-name"
              v-model="form.adminName"
              maxlength="64"
            />
          </ElFormItem>
          <ElFormItem
            label="初始密码"
            for="platform-admin-password"
            :error="errors.adminPassword"
          >
            <ElInput
              id="platform-admin-password"
              v-model="form.adminPassword"
              type="password"
              show-password
              autocomplete="new-password"
            />
          </ElFormItem>
          <h2>首店资料</h2>
          <p class="management-form-hint">
            正式收款前需另行配置收款账户。
          </p>
          <ElFormItem
            label="首店编码"
            for="platform-store-code"
            :error="errors['store.code']"
          >
            <ElInput
              id="platform-store-code"
              v-model="form.store!.code"
              maxlength="64"
            />
          </ElFormItem>
          <ElFormItem
            label="首店名称"
            for="platform-store-name"
            :error="errors['store.name']"
          >
            <ElInput
              id="platform-store-name"
              v-model="form.store!.name"
              maxlength="100"
            />
          </ElFormItem>
        </template>
        <template v-if="!isTenant || !editing">
          <ElFormItem
            label="地址（选填）"
            for="platform-address"
          >
            <ElInput
              id="platform-address"
              v-model="storeFields.address"
              maxlength="255"
            />
          </ElFormItem>
          <div class="coordinate-fields">
            <ElFormItem
              label="纬度（选填）"
              for="platform-latitude"
              :error="errors[storePrefix + 'latitude']"
            >
              <ElInputNumber
                id="platform-latitude"
                v-model="storeFields.latitude"
                :min="-90"
                :max="90"
                :precision="6"
                :controls="false"
              />
            </ElFormItem>
            <ElFormItem
              label="经度（选填）"
              for="platform-longitude"
              :error="errors[storePrefix + 'longitude']"
            >
              <ElInputNumber
                id="platform-longitude"
                v-model="storeFields.longitude"
                :min="-180"
                :max="180"
                :precision="6"
                :controls="false"
              />
            </ElFormItem>
          </div>
        </template>
        <div class="management-form-actions">
          <ElButton
            :disabled="saving"
            @click="formOpen = false; form.adminPassword = ''"
          >
            取消
          </ElButton><ElButton
            type="primary"
            native-type="submit"
            :loading="saving"
          >
            {{ saving ? '保存中…' : '保存' }}
          </ElButton>
        </div>
      </ElForm>
    </div>
    <div
      v-else
      class="management-panel"
    >
      <form
        class="management-filter"
        @submit.prevent="search"
      >
        <label class="management-filter-field"><span>名称或编码</span><ElInput
          v-model="state.keyword"
          clearable
          placeholder="输入关键词"
        /></label>
        <label
          v-if="!isTenant"
          class="management-filter-field"
        ><span>商户 ID</span><ElInput
          v-model="state.tenantId"
          clearable
          placeholder="筛选所属商户"
        /></label>
        <ElButton
          native-type="submit"
          type="primary"
          :loading="state.loading"
        >
          查询
        </ElButton><ElButton @click="reset">
          重置
        </ElButton>
      </form>
      <p
        v-if="state.loading"
        role="status"
      >
        正在加载{{ title }}…
      </p>
      <template v-if="state.error">
        <ElAlert
          :title="state.error"
          type="error"
          :closable="false"
          show-icon
        /><ElButton
          class="retry"
          @click="list.load"
        >
          重新加载
        </ElButton>
      </template>
      <div
        v-else
        class="management-table-region"
        :aria-busy="state.loading"
      >
        <p class="table-hint">
          左右滑动表格查看完整资料和操作。
        </p>
        <ElTable
          :data="state.records"
          :empty-text="state.loading ? '正在加载…' : '暂无匹配结果，可调整筛选或新建'"
          row-key="id"
        >
          <ElTableColumn
            prop="id"
            label="ID"
            min-width="100"
          />
          <ElTableColumn
            prop="code"
            label="编码"
            min-width="140"
          />
          <ElTableColumn
            prop="name"
            :label="`${title}名称`"
            min-width="160"
          />
          <ElTableColumn
            v-if="!isTenant"
            prop="tenantName"
            label="所属商户"
            min-width="160"
          />
          <ElTableColumn
            v-if="!isTenant"
            prop="address"
            label="地址"
            min-width="180"
          />
          <ElTableColumn
            v-if="isTenant"
            prop="storeCount"
            label="门店数"
            width="100"
            align="right"
          />
          <ElTableColumn
            label="状态"
            width="100"
          >
            <template #default="{ row }">
              <span
                class="management-status"
                :data-enabled="Number(row.status) === 1"
              >{{ Number(row.status) === 1 ? '已启用' : '已停用' }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn
            label="操作"
            width="210"
          >
            <template #default="{ row }">
              <div class="management-row-actions">
                <ElButton
                  text
                  :disabled="Boolean(busy)"
                  @click="open(row as PlatformRecord)"
                >
                  编辑
                </ElButton><ElButton
                  text
                  :disabled="Boolean(busy)"
                  @click="action(row as PlatformRecord, false)"
                >
                  {{ Number(row.status) === 1 ? '停用' : '启用' }}
                </ElButton><ElButton
                  text
                  type="danger"
                  :disabled="Boolean(busy)"
                  @click="action(row as PlatformRecord, true)"
                >
                  删除
                </ElButton>
              </div>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
      <ElPagination
        v-model:current-page="state.page"
        v-model:page-size="state.pageSize"
        class="platform-pagination"
        :total="state.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        :disabled="state.loading"
        @current-change="list.load"
        @size-change="search"
      />
    </div>
  </section>
</template>

<style scoped lang="scss">
.table-hint { display: none; color: var(--jz-text-secondary); font-size: var(--jz-font-caption); }
h2 { margin: 32px 0 16px; font-size: var(--jz-font-section); font-weight: 600; }
.management-form-hint { margin-bottom: 24px; }
.coordinate-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.coordinate-fields .el-input-number { width: 100%; }
.platform-pagination { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 24px; font-variant-numeric: tabular-nums; }
.retry { margin-top: 16px; }
@media (max-width: 767px) { .coordinate-fields { grid-template-columns: 1fr; gap: 0; } .table-hint { display: block; margin: 0 0 12px; } }
</style>
