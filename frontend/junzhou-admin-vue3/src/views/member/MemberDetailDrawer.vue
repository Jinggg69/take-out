<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDrawer, ElTabs, ElTabPane, ElButton } from 'element-plus'
import { memberApi } from '@/api'
import type { MemberDetail } from '@/api/member'
import { workspace } from '@/stores/workspace'
import DataState from '@/components/DataState.vue'
import MemberTable from './MemberTable.vue'
import { unwrap, useMemberLoad, display, money, pointColumns, walletColumns } from './shared'
const open = defineModel<boolean>({ required: true })
const props = defineProps<{ memberId: string }>()
const tab = ref('profile')
const { value: detail, loading, error, load } = useMemberLoad<MemberDetail | null>(() => null, async () => open.value ? unwrap(await memberApi.detail(props.memberId)) : null)
watch(open, value => { if (value) { detail.value = null; tab.value = 'profile'; void load() } })
watch(workspace.revision, () => { open.value = false; detail.value = null })
const profile = [{ key: 'memberNo', label: '会员号' }, { key: 'name', label: '姓名' }, { key: 'phone', label: '手机号' }, { key: 'birthday', label: '生日' }, { key: 'levelName', label: '等级' }, { key: 'tagNames', label: '标签' }, { key: 'points', label: '可用积分' }, { key: 'balance', label: '可用余额', money: true }, { key: 'principalBalance', label: '本金余额', money: true }, { key: 'giftBalance', label: '赠送余额', money: true }, { key: 'totalSpent', label: '累计消费', money: true }, { key: 'orderCount', label: '消费次数' }, { key: 'storeName', label: '开卡门店' }, { key: 'createTime', label: '开卡时间' }, { key: 'remark', label: '备注' }]
</script>
<template>
  <ElDrawer
    v-model="open"
    title="会员详情"
    size="min(1000px, 96vw)"
  >
    <DataState
      v-if="loading"
      kind="loading"
      title="正在加载会员详情"
    />
    <DataState
      v-else-if="error"
      kind="error"
      title="详情加载失败"
      :description="error"
    >
      <ElButton @click="load">
        重试
      </ElButton>
    </DataState>
    <ElTabs
      v-else-if="detail"
      v-model="tab"
    >
      <ElTabPane
        label="会员资料"
        name="profile"
      >
        <dl class="member-detail-grid">
          <template
            v-for="field in profile"
            :key="field.key"
          >
            <div v-if="detail.member[field.key] !== undefined">
              <dt>{{ field.label }}</dt><dd>{{ field.money ? money(detail.member[field.key]) : display(detail.member[field.key]) }}</dd>
            </div>
          </template>
        </dl><p class="member-hint">
          手机号由后台登记时为未验证。资产和记录范围按当前账号权限展示。
        </p>
      </ElTabPane>
      <ElTabPane
        label="消费订单"
        name="orders"
      >
        <MemberTable
          v-if="detail.orders.length"
          :rows="detail.orders"
          :columns="[{ key: 'number', label: '订单号' }, { key: 'amount', label: '订单金额', money: true }, { key: 'status', label: '状态', labels: { '1': '待付款', '2': '待接单', '3': '制作 / 待履约', '4': '派送中', '5': '已完成', '6': '已取消' } }, { key: 'storeName', label: '消费门店' }, { key: 'orderTime', label: '下单时间', width: 180 }]"
        /><DataState
          v-else
          title="暂无消费订单"
        />
      </ElTabPane>
      <ElTabPane
        label="积分流水"
        name="points"
      >
        <MemberTable
          v-if="detail.points.length"
          :rows="detail.points"
          :columns="pointColumns"
        /><DataState
          v-else
          title="暂无可查看的积分流水"
        />
      </ElTabPane>
      <ElTabPane
        label="余额流水"
        name="wallet"
      >
        <MemberTable
          v-if="detail.wallet.length"
          :rows="detail.wallet"
          :columns="walletColumns"
        /><DataState
          v-else
          title="暂无可查看的余额流水"
        />
      </ElTabPane>
      <ElTabPane
        label="优惠券"
        name="coupons"
      >
        <MemberTable
          v-if="detail.coupons.length"
          :rows="detail.coupons"
          :columns="[{ key: 'name', label: '券名称' }, { key: 'status', label: '状态' }, { key: 'minSpend', label: '使用门槛', money: true }, { key: 'endTime', label: '有效期至', width: 180 }]"
        /><DataState
          v-else
          title="暂无可查看的优惠券"
        />
      </ElTabPane>
    </ElTabs>
  </ElDrawer>
</template>
