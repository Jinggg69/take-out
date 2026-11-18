<script setup lang="ts">
import axios from 'axios'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElAlert, ElButton, ElInput, ElMessage, ElMessageBox, ElOption, ElSelect } from 'element-plus'
import { decorationApi, dishApi } from '@/api'
import { decorationModuleLabels, decorationPublishError, decorationTargets, decorationThemes, sameDecoration, type DecorationBanner, type DecorationConfig, type DecorationModule, type DecorationState } from '@/api/decoration'
import DataState from '@/components/DataState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { pendingWrites, workspace } from '@/stores/workspace'
import { imageUploadError } from '@/utils/dish'
import { errorMessage } from '@/utils/error-message'
import DecorationPreview from './DecorationPreview.vue'

const state = ref<DecorationState>()
const config = ref<DecorationConfig>()
const loading = ref(false)
const saving = ref<'save' | 'publish' | 'reset' | null>(null)
const uploading = ref<DecorationBanner>()
const error = ref('')
const conflict = ref(false)
const dirty = computed(() => Boolean(config.value && !sameDecoration(config.value, state.value?.draft)))
const busy = computed(() => loading.value || Boolean(saving.value || uploading.value))
const upToDate = computed(() => Boolean(state.value && sameDecoration(state.value.draft, state.value.published)))
const canUpload = computed(() => workspace.can('file:upload'))
let active = true
let uploadController: AbortController | undefined

function setState(result: Awaited<ReturnType<typeof decorationApi.load>>) {
  if (String(result.data.code) !== '1' || !result.data.data) throw new Error(result.data.msg || '首页装修请求失败')
  state.value = result.data.data
  config.value = structuredClone(result.data.data.draft)
  conflict.value = false
}

async function confirm(message: string, title: string, button: string) {
  try {
    await ElMessageBox.confirm(message, title, { confirmButtonText: button, cancelButtonText: '取消', type: 'warning' })
    return true
  } catch { return false }
}

async function load() {
  if (busy.value || (dirty.value && !await confirm('重新读取会丢弃本页未保存的修改。', '重新加载草稿', '重新加载'))) return
  loading.value = true
  error.value = ''
  try {
    const result = await decorationApi.load()
    if (active) setState(result)
  } catch (cause) {
    if (active) error.value = errorMessage(cause, '页面装修加载失败，请重试')
  } finally { loading.value = false }
}

async function submit(action: 'save' | 'publish' | 'reset') {
  if (busy.value || conflict.value || !config.value || !state.value) return
  if (action === 'publish') {
    if (dirty.value || upToDate.value) return
    const invalid = decorationPublishError(config.value)
    if (invalid) { error.value = invalid; return }
  }
  if (action === 'reset' && !await confirm('旗下所有门店将显示平台默认首页，已保存的装修草稿会保留。', '恢复默认首页', '恢复默认')) return
  if (!active || busy.value) return
  saving.value = action
  error.value = ''
  pendingWrites.value++
  const version = state.value.version
  try {
    const result = action === 'save'
      ? await decorationApi.save(version, config.value)
      : await decorationApi[action](version)
    if (!active) return
    // Reset updates the live configuration only. Preserve edits still in the browser.
    const edited = action === 'reset' && dirty.value ? config.value : undefined
    setState(result)
    if (edited) config.value = edited
    ElMessage.success({ save: '草稿已保存', publish: '已发布到商户首页', reset: '已恢复平台默认首页' }[action])
  } catch (cause) {
    if (!active) return
    conflict.value = axios.isAxiosError(cause) && cause.response?.status === 409
    error.value = conflict.value
      ? '这份装修已在其他窗口更新。请先核对当前修改，再重新加载最新草稿。'
      : errorMessage(cause, '操作失败，请重试')
  } finally {
    saving.value = null
    pendingWrites.value--
  }
}

function move<T>(items: T[], index: number, offset: number) {
  const item = items[index]
  const destination = index + offset
  if (!item || destination < 0 || destination >= items.length) return
  items.splice(index, 1)
  items.splice(destination, 0, item)
}

function addItem(module: Exclude<DecorationModule, {type: 'NOTICE'}>) {
  if (module.type === 'BANNER' && module.items.length < 5) module.items.push({ imageUrl: '', title: '', target: 'NONE' })
  if (module.type === 'ACTIVITY' && module.items.length < 4) module.items.push({ title: '', target: 'NONE' })
}

async function upload(event: Event, item: DecorationBanner) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || busy.value || !canUpload.value) return
  error.value = imageUploadError(file)
  if (error.value) return
  uploading.value = item
  uploadController = new AbortController()
  pendingWrites.value++
  try {
    const { data } = await dishApi.upload(file, uploadController.signal)
    if (!active) return
    if (String(data.code) !== '1' || typeof data.data !== 'string' || !data.data) throw new Error(data.msg || '图片上传失败')
    item.imageUrl = data.data
  } catch (cause) {
    if (active) error.value = errorMessage(cause, '图片上传失败，请重试')
  } finally {
    uploading.value = undefined
    uploadController = undefined
    pendingWrites.value--
  }
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value && !saving.value && !uploading.value) return
  event.preventDefault()
  event.returnValue = ''
}
onBeforeRouteLeave(() => saving.value || uploading.value
  ? false
  : !dirty.value || confirm('离开页面会丢弃未保存的修改。', '离开页面装修', '离开'))
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  void load()
})
onBeforeUnmount(() => {
  active = false
  uploadController?.abort()
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<template>
  <div class="decoration-page">
    <PageHeader title="页面装修">
      <div
        v-if="state"
        class="actions page-actions"
      >
        <ElButton
          :disabled="busy || !state.published || conflict"
          :loading="saving === 'reset'"
          @click="submit('reset')"
        >
          恢复默认
        </ElButton>
        <ElButton
          :disabled="busy || !dirty || conflict"
          :loading="saving === 'save'"
          @click="submit('save')"
        >
          保存草稿
        </ElButton>
        <ElButton
          type="primary"
          :disabled="busy || dirty || upToDate || state.version === 0 || conflict"
          :loading="saving === 'publish'"
          @click="submit('publish')"
        >
          发布
        </ElButton>
      </div>
    </PageHeader>
    <DataState
      v-if="!config"
      :kind="loading ? 'loading' : 'error'"
      :title="loading ? '正在读取首页装修' : '首页装修未加载'"
      :description="error"
    >
      <ElButton
        v-if="!loading"
        @click="load"
      >
        重试
      </ElButton>
    </DataState>
    <template v-else>
      <div class="editor-status">
        <span>装修发布后适用于本商户旗下所有门店。</span>
        <strong aria-live="polite">{{ dirty ? '有未保存的修改' : upToDate ? '已发布' : state?.version ? '已保存草稿' : '当前使用默认首页' }}</strong>
      </div>
      <ElAlert
        v-if="error"
        class="command-feedback"
        :title="error"
        type="error"
        :closable="false"
      >
        <ElButton
          v-if="conflict"
          :disabled="busy"
          @click="load"
        >
          重新加载最新草稿
        </ElButton>
      </ElAlert>
      <div class="decoration-grid">
        <section class="management-panel editor">
          <fieldset :disabled="busy">
            <legend>首页强调色</legend>
            <div class="theme-list">
              <label
                v-for="(theme, value) in decorationThemes"
                :key="value"
              >
                <input
                  v-model="config.theme"
                  type="radio"
                  name="decoration-theme"
                  :value="value"
                />
                <span
                  class="swatch"
                  :style="{ backgroundColor: theme.color }"
                  aria-hidden="true"
                />
                {{ theme.label }}
              </label>
            </div>
          </fieldset>
          <section
            v-for="(module, index) in config.modules"
            :key="module.type"
            class="editable-module"
          >
            <header class="module-heading">
              <h2>{{ decorationModuleLabels[module.type] }}</h2>
              <div class="actions">
                <label class="enable-module"><input
                  v-model="module.enabled"
                  type="checkbox"
                  :disabled="busy"
                /> 启用</label>
                <ElButton
                  size="small"
                  :aria-label="`上移${decorationModuleLabels[module.type]}`"
                  :disabled="busy || index === 0"
                  @click="move(config.modules, index, -1)"
                >
                  上移
                </ElButton>
                <ElButton
                  size="small"
                  :aria-label="`下移${decorationModuleLabels[module.type]}`"
                  :disabled="busy || index === config.modules.length - 1"
                  @click="move(config.modules, index, 1)"
                >
                  下移
                </ElButton>
              </div>
            </header>
            <ElInput
              v-if="module.type === 'NOTICE'"
              v-model="module.text"
              type="textarea"
              aria-label="公告内容"
              :rows="3"
              :maxlength="100"
              :disabled="busy"
              show-word-limit
              placeholder="填写顾客会在首页看到的公告"
            />
            <template v-else>
              <div
                v-for="(item, position) in module.items"
                :key="position"
                class="editable-item"
              >
                <div class="item-fields">
                  <ElInput
                    v-model="item.title"
                    :maxlength="30"
                    :aria-label="`${decorationModuleLabels[module.type]}标题 ${position + 1}`"
                    placeholder="填写标题"
                    :disabled="busy"
                  />
                  <ElSelect
                    v-model="item.target"
                    :aria-label="`${decorationModuleLabels[module.type]}跳转 ${position + 1}`"
                    :disabled="busy"
                  >
                    <ElOption
                      v-for="(label, target) in decorationTargets"
                      :key="target"
                      :label="label"
                      :value="target"
                    />
                  </ElSelect>
                </div>
                <div
                  v-if="'imageUrl' in item"
                  class="image-row"
                >
                  <span>{{ item.imageUrl ? '已上传图片' : '请选择 PNG 或 JPEG 图片，小于 2 MB' }}</span>
                  <label class="upload-label">
                    {{ uploading === item ? '正在上传' : item.imageUrl ? '更换图片' : '上传图片' }}
                    <input
                      type="file"
                      accept=".png,.jpg,.jpeg"
                      :aria-label="`上传第 ${position + 1} 张轮播图`"
                      :disabled="busy || !canUpload"
                      @change="upload($event, item)"
                    />
                  </label>
                </div>
                <div class="actions item-actions">
                  <ElButton
                    size="small"
                    :aria-label="`上移第 ${position + 1} 项`"
                    :disabled="busy || position === 0"
                    @click="move(module.items, position, -1)"
                  >
                    上移
                  </ElButton>
                  <ElButton
                    size="small"
                    :aria-label="`下移第 ${position + 1} 项`"
                    :disabled="busy || position === module.items.length - 1"
                    @click="move(module.items, position, 1)"
                  >
                    下移
                  </ElButton>
                  <ElButton
                    size="small"
                    :aria-label="`移除第 ${position + 1} 项`"
                    :disabled="busy"
                    @click="module.items.splice(position, 1)"
                  >
                    移除
                  </ElButton>
                </div>
              </div>
              <p
                v-if="module.type === 'BANNER' && !canUpload"
                class="module-note"
              >
                当前账号没有图片上传权限，可请管理员添加权限。
              </p>
              <ElButton
                :disabled="busy || module.items.length >= (module.type === 'BANNER' ? 5 : 4)"
                @click="addItem(module)"
              >
                {{ module.type === 'BANNER' ? '添加轮播图' : '添加活动入口' }}
              </ElButton>
            </template>
          </section>
        </section>
        <DecorationPreview
          :config="config"
          :store-name="workspace.current.value?.name || '当前门店'"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.decoration-page { max-width: 1320px; }
.decoration-page :deep(.page-header) { margin-bottom: 12px; }
.actions, .editor-status, .module-heading { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; }
.actions :deep(.el-button) { margin-left: 0; }
.editor-status { justify-content: space-between; color: var(--jz-text-secondary); font-size: var(--jz-font-control); margin-bottom: 16px; }
.editor-status strong { color: var(--jz-text-primary); font-weight: 600; }
.command-feedback { margin-bottom: 16px; }
.command-feedback :deep(.el-button) { margin-top: 8px; }
.decoration-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(290px, 365px); align-items: start; gap: 16px; }
fieldset { min-width: 0; border: 0; padding: 0; margin: 0; }
legend, h2 { font-size: var(--jz-font-body); font-weight: 600; }
h2 { margin: 0; }
.theme-list { display: flex; flex-wrap: wrap; gap: 8px 16px; margin-top: 8px; }
.theme-list label, .enable-module { display: inline-flex; align-items: center; gap: 6px; min-height: 32px; font-size: var(--jz-font-control); }
input[type='radio'], input[type='checkbox'] { accent-color: var(--jz-brand-primary); }
.swatch { width: 14px; height: 14px; border-radius: 50%; }
.editable-module { border-top: 1px solid var(--jz-border); padding-top: 16px; margin-top: 16px; }
.module-heading { justify-content: space-between; margin-bottom: 12px; }
.editable-item { background: var(--jz-page-background); padding: 12px; border-radius: var(--jz-radius-control); margin-bottom: 8px; }
.item-fields { display: grid; grid-template-columns: minmax(0, 1fr) minmax(124px, 0.65fr); gap: 8px; }
.image-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin-top: 8px; }
.image-row span, .module-note { font-size: var(--jz-font-meta); color: var(--jz-text-secondary); }
.upload-label { position: relative; display: inline-flex; min-height: 32px; align-items: center; color: var(--jz-brand-primary); text-decoration: underline; font-size: var(--jz-font-control); cursor: pointer; }
.upload-label:has(:disabled) { color: var(--jz-text-secondary); cursor: not-allowed; }
.upload-label:focus-within { outline: 2px solid var(--jz-brand-primary); outline-offset: 2px; }
.upload-label input { position: absolute; inset: 0; width: 100%; opacity: 0; cursor: inherit; }
.item-actions { margin-top: 8px; justify-content: flex-end; }
@media (max-width: 900px) { .decoration-grid { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 480px) { .item-fields { grid-template-columns: minmax(0, 1fr); } }
</style>
