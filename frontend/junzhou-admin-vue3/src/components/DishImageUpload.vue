<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { ElButton } from 'element-plus'
import { dishApi } from '@/api'
import { imageUploadError, dishResultMessage } from '@/utils/dish'
import { errorMessage } from '@/utils/error-message'

const image = defineModel<string>({ required: true })
defineProps<{ disabled: boolean; invalid?: boolean }>()
const emit = defineEmits<{ busy: [value: boolean]; changed: [] }>()
const input = ref<HTMLInputElement>()
const uploading = ref(false)
const error = ref('')
const broken = ref(false)
let active = true
let controller: AbortController | undefined
onBeforeUnmount(() => { active = false; controller?.abort(); emit('busy', false) })
async function upload(event: Event) {
  const fileInput = event.target as HTMLInputElement
  const file = fileInput.files?.[0]; fileInput.value = ''
  if (!file || uploading.value) return
  error.value = imageUploadError(file)
  if (error.value) return
  uploading.value = true; emit('busy', true); controller = new AbortController()
  try {
    const { data } = await dishApi.upload(file, controller.signal)
    if (!active) return
    if (String(data.code) !== '1' || typeof data.data !== 'string' || !data.data) throw new Error(dishResultMessage(data, '图片上传失败，请重新上传'))
    image.value = data.data; broken.value = false; emit('changed')
  } catch (cause) { if (active) error.value = errorMessage(cause, '图片上传失败，请检查网络后重新上传') }
  finally { if (active) { uploading.value = false; emit('busy', false) } }
}
function remove() { image.value = ''; error.value = ''; broken.value = false; emit('changed') }
</script>

<template>
  <div
    class="dish-image-upload"
    :aria-busy="uploading"
  >
    <div class="dish-image-preview">
      <img
        v-if="image && !broken"
        :src="image"
        alt="菜品图片预览"
        width="160"
        height="160"
        @error="broken = true"
      />
      <span v-else>{{ image ? '图片暂时无法显示' : '尚未上传图片' }}</span>
    </div>
    <div class="dish-image-controls">
      <input
        ref="input"
        class="file-input"
        type="file"
        accept=".jpg,.jpeg,.png"
        aria-label="选择菜品图片"
        tabindex="-1"
        :disabled="disabled || uploading"
        @change="upload"
      />
      <div class="dish-image-buttons">
        <ElButton
          id="dish-image"
          :disabled="disabled || uploading"
          :loading="uploading"
          :aria-invalid="invalid"
          aria-describedby="dish-image-hint dish-image-error"
          @click="input?.click()"
        >
          {{ uploading ? '正在上传' : image ? '重新上传' : '上传图片' }}
        </ElButton>
        <ElButton
          v-if="image"
          :disabled="disabled || uploading"
          @click="remove"
        >
          删除图片
        </ElButton>
      </div>
      <p
        id="dish-image-hint"
        class="management-form-hint"
      >
        支持 PNG、JPEG、JPG，小于 2MB。建议尺寸 200 × 200 或 300 × 300 像素。
      </p>
      <p
        v-if="error"
        id="dish-image-error"
        class="upload-error"
        role="alert"
      >
        {{ error }}
      </p>
      <p
        v-if="uploading"
        class="management-form-hint"
        role="status"
      >
        图片上传完成后即可保存菜品。
      </p>
    </div>
  </div>
</template>

<style scoped>
.dish-image-upload { display: flex; align-items: center; flex-wrap: wrap; gap: var(--jz-space-4); width: 100%; }
.dish-image-preview { display: grid; place-items: center; flex: 0 0 160px; height: 160px; border: 1px solid var(--jz-border); border-radius: var(--jz-radius-control); overflow: hidden; color: var(--jz-text-secondary); background: var(--jz-page-background); font-size: var(--jz-font-caption); }
img { display: block; object-fit: contain; }
.dish-image-controls { flex: 1; min-width: min(220px, 100%); }
.dish-image-buttons { display: flex; flex-wrap: wrap; gap: var(--jz-space-3); }
.dish-image-buttons .el-button { margin-left: 0; }
.file-input { position: absolute; width: 1px; height: 1px; clip-path: inset(50%); overflow: hidden; }
.upload-error { color: var(--jz-danger); font-size: var(--jz-font-control); margin: var(--jz-space-2) 0 0; }
</style>
