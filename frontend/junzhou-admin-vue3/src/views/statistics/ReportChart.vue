<script setup lang="ts">
import { computed, ref } from 'vue'
import { chartX, chartY, lineSegments, type ChartRow } from '@/utils/statistics'
const props = withDefaults(defineProps<{ title: string; rows: ChartRow[]; series: string[]; unit: string; money?: boolean; bars?: boolean }>(), { money: false, bars: false })
const active = ref('')
const maximum = computed(() => Math.max(1, ...props.rows.flatMap(row => row.values.filter((value): value is number => value !== null))))
const hasValues = computed(() => props.rows.some(row => row.values.some(value => value !== null)))
const ticks = computed(() => Array.from({ length: 5 }, (_, index) => maximum.value * index / 4))
function valueText(value: number | null | undefined) { return value === null || value === undefined ? '—' : props.money ? value.toFixed(2) : String(value) }
function description(row: ChartRow) { return `${row.label}：${props.series.map((name, i) => `${name} ${valueText(row.values[i])}${row.values[i] == null ? '' : props.unit}`).join('；')}` }
function showTick(index: number) { const step = Math.max(1, Math.ceil((props.rows.length - 1) / 4)); return index % step === 0 || index === props.rows.length - 1 }
</script>

<template>
  <div class="report-chart">
    <p
      v-if="!rows.length || !hasValues"
      class="chart-empty"
      role="status"
    >
      所选时间暂无统计数据
    </p>
    <template v-else>
      <p
        v-if="rows.every(row => row.values.every(value => value === 0))"
        class="zero-note"
      >
        所选时间统计值均为 0
      </p>
      <template v-if="bars">
        <p class="chart-unit">
          销量（{{ unit }}）
        </p>
        <ol
          class="rank-bars"
          :aria-label="title"
        >
          <li
            v-for="(row, index) in rows"
            :key="index"
            :title="description(row)"
          >
            <span class="rank-name">{{ index + 1 }}. {{ row.label }}</span>
            <span
              class="bar-track"
              aria-hidden="true"
            ><span
              class="bar-fill"
              :style="{ width: `${Math.max(0, row.values[0] ?? 0) / maximum * 100}%` }"
            /></span>
            <span class="rank-value">{{ valueText(row.values[0]) }} {{ unit }}</span>
          </li>
        </ol>
      </template>
      <template v-else>
        <ul
          class="chart-legend"
          :aria-label="`${title}图例`"
        >
          <li
            v-for="(name, index) in series"
            :key="name"
          >
            <span
              :class="['legend-line', { secondary: index === 1 }]"
              aria-hidden="true"
            />{{ name }}（{{ unit }}）
          </li>
        </ul>
        <div
          class="chart-scroll"
          tabindex="0"
          :aria-label="`${title}图表，可横向滚动`"
        >
          <svg
            viewBox="0 0 640 265"
            role="img"
            :aria-label="`${title}，单位${unit}；完整数值见下方数据表`"
          >
            <g
              v-for="tick in ticks"
              :key="tick"
            >
              <line
                x1="72"
                :y1="chartY(tick, maximum)"
                x2="592"
                :y2="chartY(tick, maximum)"
                class="chart-grid"
              />
              <text
                x="62"
                :y="chartY(tick, maximum) + 4"
                text-anchor="end"
              >{{ Number(tick.toFixed(money ? 2 : 1)).toLocaleString('zh-CN') }}</text>
            </g>
            <g
              v-for="(name, seriesIndex) in series"
              :key="name"
              :class="['chart-series', { secondary: seriesIndex === 1 }]"
            >
              <polyline
                v-for="(segment, index) in lineSegments(rows, seriesIndex, maximum)"
                :key="index"
                :points="segment"
              />
              <template
                v-for="(row, index) in rows"
                :key="index"
              >
                <circle
                  v-if="row.values[seriesIndex] != null"
                  :cx="chartX(index, rows.length)"
                  :cy="chartY(row.values[seriesIndex]!, maximum)"
                  r="4"
                  tabindex="0"
                  role="img"
                  :aria-label="description(row)"
                  @focus="active = description(row)"
                  @blur="active = ''"
                  @mouseenter="active = description(row)"
                  @mouseleave="active = ''"
                ><title>{{ description(row) }}</title></circle>
              </template>
            </g>
            <template
              v-for="(row, index) in rows"
              :key="index"
            ><text
              v-if="showTick(index)"
              :x="chartX(index, rows.length)"
              y="247"
              text-anchor="middle"
            >{{ row.label.slice(5) || row.label }}</text></template>
          </svg>
        </div>
        <p
          class="chart-tooltip"
          aria-live="polite"
        >
          {{ active || '悬停或聚焦数据点查看数值；完整日期与数据见下表。' }}
        </p>
      </template>
      <details class="chart-table">
        <summary>查看{{ title }}数据表</summary><div
          class="table-scroll"
          tabindex="0"
          :aria-label="`${title}完整数据`"
        >
          <table>
            <caption>{{ title }}（{{ unit }}）</caption><thead>
              <tr>
                <th scope="col">
                  {{ bars ? '菜品 / 套餐' : '日期' }}
                </th><th
                  v-for="name in series"
                  :key="name"
                  scope="col"
                >
                  {{ name }}（{{ unit }}）
                </th>
              </tr>
            </thead><tbody>
              <tr
                v-for="(row, index) in rows"
                :key="index"
              >
                <th scope="row">
                  {{ row.label }}
                </th><td
                  v-for="(_, i) in series"
                  :key="i"
                >
                  {{ valueText(row.values[i]) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </template>
  </div>
</template>

<style scoped lang="scss">
.chart-empty { padding: var(--jz-space-8) var(--jz-space-4); text-align: center; color: var(--jz-text-secondary); }
.chart-legend { display: flex; flex-wrap: wrap; gap: var(--jz-space-4); margin: var(--jz-space-4) 0 0; padding: 0; list-style: none; font-size: var(--jz-font-control); }
.chart-legend li { display: flex; align-items: center; gap: var(--jz-space-2); }
.legend-line { width: 24px; border-top: 3px solid var(--jz-brand-primary); }
.legend-line.secondary { border-top: 3px dashed var(--jz-info); }
.chart-scroll, .table-scroll { max-width: 100%; overflow: auto; }
svg { display: block; width: 100%; min-width: 500px; }
svg text { fill: var(--jz-text-secondary); font: 12px var(--jz-font-family); }
.chart-grid { stroke: var(--jz-border); }
.chart-series { color: var(--jz-brand-primary); }
.chart-series.secondary { color: var(--jz-info); }
.chart-series polyline { fill: none; stroke: currentColor; stroke-width: 2; }
.chart-series.secondary polyline { stroke-dasharray: 7 4; }
.chart-series circle { fill: currentColor; stroke: var(--jz-surface); stroke-width: 1; cursor: crosshair; }
.chart-series circle:focus { outline: 2px solid var(--jz-text-primary); outline-offset: 3px; }
.chart-tooltip, .chart-unit, .zero-note { color: var(--jz-text-secondary); font-size: var(--jz-font-control); }
.chart-tooltip { min-height: 3em; overflow-wrap: anywhere; margin: 0 0 var(--jz-space-3); }
.chart-table summary { cursor: pointer; color: var(--jz-brand-primary); padding: var(--jz-space-2) 0; }
table { border-collapse: collapse; width: 100%; font-size: var(--jz-font-control); }
caption { text-align: left; padding: var(--jz-space-3) 0; }
th, td { border-bottom: 1px solid var(--jz-border); padding: var(--jz-space-3); white-space: nowrap; text-align: right; font-variant-numeric: tabular-nums; }
th:first-child { text-align: left; font-weight: 400; }
.rank-bars { padding: 0; margin: var(--jz-space-4) 0; list-style: none; }
.rank-bars li { display: grid; grid-template-columns: minmax(80px, 1fr) minmax(30px, 2fr) auto; align-items: center; gap: var(--jz-space-3); min-height: 36px; font-size: var(--jz-font-control); }
.rank-name { overflow-wrap: anywhere; }
.rank-value { font-variant-numeric: tabular-nums; }
.bar-track { height: 16px; background: var(--jz-page-background); }
.bar-fill { display: block; height: 100%; background: var(--jz-brand-primary); }
</style>
