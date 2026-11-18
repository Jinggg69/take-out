<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDialog, ElForm, ElFormItem, ElSelect, ElOption, ElButton, ElAlert } from 'element-plus'
import { memberApi } from '@/api'
import type { MemberBody, MemberRow } from '@/api/member'
import { workspace } from '@/stores/workspace'
import FormFields from './FormFields.vue'
import { memberFields } from './schema'
import { memberPayload } from './forms'
import { unwrap } from './shared'
import { errorMessage } from '@/utils/error-message'
const open = defineModel<boolean>({ required: true })
const props = defineProps<{ record?: MemberRow }>()
const emit = defineEmits<{ saved: [] }>()
const draft = ref<MemberBody>({}), levels = ref<MemberRow[]>([]), tags = ref<MemberRow[]>([]), busy = ref(false), error = ref('')
watch(workspace.revision, () => { open.value = false })
watch(open, async visible => {
  if (!visible) return
  draft.value = props.record ? { ...props.record, tagIds: [...(props.record.tagIds as string[] || [])] } : { name: '', phone: '', birthday: '', remark: '', tagIds: [] }
  error.value = ''; levels.value = []; tags.value = []
  try { const responses = await Promise.all([memberApi.list('levels'), memberApi.list('tags')]); levels.value = unwrap(responses[0]); tags.value = unwrap(responses[1]) }
  catch (e) { error.value = errorMessage(e, '等级或标签加载失败，请关闭后重试') }
})
async function save() {
  if (busy.value || !workspace.can(props.record ? 'member:update' : 'member:create')) return
  try { const body = memberPayload(draft.value); busy.value = true; error.value = ''; unwrap(await memberApi.saveMember(props.record?.id, body)); open.value = false; emit('saved') }
  catch (e) { error.value = errorMessage(e, '会员保存失败，请重试') }
  finally { busy.value = false }
}
</script>
<template>
  <ElDialog
    v-model="open"
    :title="record ? '编辑会员' : '新增线下会员'"
    width="min(600px, 94vw)"
    :close-on-click-modal="false"
    :show-close="!busy"
    :close-on-press-escape="!busy"
  >
    <ElAlert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    />
    <ElForm
      label-position="top"
      class="member-form"
      :disabled="busy"
      @submit.prevent="save"
    >
      <FormFields
        v-model="draft"
        :fields="memberFields"
      />
      <ElFormItem label="会员等级">
        <ElSelect
          :model-value="draft.levelId as string"
          clearable
          placeholder="默认等级"
          @update:model-value="draft.levelId = $event"
        >
          <ElOption
            v-for="row in levels"
            :key="row.id"
            :label="String(row.name)"
            :value="row.id"
            :disabled="row.status === 0"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="会员标签">
        <ElSelect
          :model-value="draft.tagIds as string[]"
          multiple
          clearable
          @update:model-value="draft.tagIds = $event"
        >
          <ElOption
            v-for="row in tags"
            :key="row.id"
            :label="String(row.name)"
            :value="row.id"
            :disabled="row.status === 0"
          />
        </ElSelect>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton
        :disabled="busy"
        @click="open = false"
      >
        取消
      </ElButton><ElButton
        type="primary"
        :loading="busy"
        @click="save"
      >
        保存会员
      </ElButton>
    </template>
  </ElDialog>
</template>
