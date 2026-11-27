<template>
  <a-drawer placement="right" :open="sVisible" @update:open="onVisibleChange"
    :destroyOnClose="true" :width="850" class="hms-drawer">
    <template #title>
      <div class="hms-drawer-title">
        <SafetyCertificateOutlined class="title-icon" />
        <span>HMS 告警与健康诊断</span>
      </div>
    </template>
    <div class="hms-drawer-container">
      <!-- 搜索与筛选工具栏 -->
      <div class="searchBox">
        <div style="width: 230px;">
          <a-range-picker size="default" v-model:value="time" format="YYYY-MM-DD"
            :placeholder="['开始时间', '结束时间']" @change="onTimeChange" />
        </div>
        <div>
          <a-select size="default" style="width: 140px" v-model:value="param.level" @select="onLevelSelect">
            <a-select-option v-for="item in levels" :key="item.label" :value="item.value">
              {{ item.label }}
            </a-select-option>
          </a-select>
        </div>
        <div>
          <a-select size="default" v-model:value="domainFilter" :disabled="!childrenSn || !param.device_sn"
            style="width: 130px" @select="onDeviceTypeSelect">
            <a-select-option v-for="item in deviceTypes" :key="item.label" :value="item.value">
              {{ item.label }}
            </a-select-option>
          </a-select>
        </div>
        <div>
          <a-input-search size="default" v-model:value="param.message" placeholder="搜索告警内容..."
            style="width: 200px" allow-clear @search="getHms" />
        </div>
      </div>

      <!-- 表格列表区 -->
      <div class="table-wrap">
        <a-table :columns="hmsColumns" :scroll="{ x: '100%', y: 580 }" :data-source="hmsData.data"
          :pagination="hmsPaginationProp" @change="refreshHmsData" row-key="hms_id" :rowClassName="rowClassName"
          :loading="loading">
          <template #time="{ record }">
            <div class="time-cell">
              <div class="time-create"><ClockCircleOutlined class="time-icon" /> {{ record.create_time }}</div>
              <div class="time-status" :class="getHmsStatusClass(record)">
                {{ record.update_time ? record.update_time : '发生中...' }}
              </div>
            </div>
          </template>
          <template #level="{ text }">
            <div class="hms-level-badge" :class="getHmsBadgeClass(text)">
              <span class="badge-dot"></span>
              <span>{{ getHmsLevelText(text) }}</span>
            </div>
          </template>
          <template #code="{ text }">
            <a-tooltip :title="text">
              <span class="code-badge" v-if="text">{{ text }}</span>
              <span v-else class="text-gray-400">--</span>
            </a-tooltip>
          </template>
          <template #message="{ record }">
            <div class="message-cell">
              <div class="message-zh" v-if="record.message_zh">{{ record.message_zh }}</div>
              <div class="message-en" v-if="record.message_en && record.message_en !== record.message_zh">{{ record.message_en }}</div>
            </div>
          </template>
          <template #domain="{ text }">
            <a-tag :color="text === EDeviceTypeName.Dock ? 'blue' : 'purple'" class="domain-tag">
              {{ text === EDeviceTypeName.Dock ? '机场' : text === EDeviceTypeName.Aircraft ? '飞行器' : (EDeviceTypeName[text] || '其他') }}
            </a-tag>
          </template>
        </a-table>
      </div>
    </div>
  </a-drawer>
</template>

<script lang="ts" setup>
import { watchEffect, reactive, ref, defineProps, defineEmits, watch } from 'vue'
import { getDeviceHms, HmsQueryBody } from '@/api/manage'
import dayjs, { Dayjs } from 'dayjs'
import { ColumnProps, TableState } from 'ant-design-vue/lib/table/interface'
import { Device, DeviceHms } from '@/types/device'
import { IPage } from '@/api/http/type'
import { EDeviceTypeName, EHmsLevel, ELocalStorageKey } from '@/types'
import { SafetyCertificateOutlined, ClockCircleOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  visible: boolean,
  device: null | Device,
}>()
const emit = defineEmits(['update:visible', 'ok', 'cancel'])

const workspaceId: string = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''
// 健康状态
const sVisible = ref(false)

watch(props, () => {
  sVisible.value = props.visible
  if (props.visible) {
    showHms()
  }
})

function onVisibleChange(sVisible: boolean) {
  setVisible(sVisible)
}

function setVisible(v: boolean, e?: Event) {
  sVisible.value = v
  emit('update:visible', v, e)
}

const loading = ref(false)

const hmsColumns: ColumnProps[] = [
  { title: '时间 (开始 / 恢复)', dataIndex: 'create_time', width: '180px', className: 'titleStyle', slots: { customRender: 'time' } },
  { title: '级别', dataIndex: 'level', width: '100px', className: 'titleStyle', slots: { customRender: 'level' } },
  { title: '设备类型', dataIndex: 'domain', width: '100px', className: 'titleStyle', slots: { customRender: 'domain' } },
  { title: '错误码', dataIndex: 'key', width: '140px', className: 'titleStyle', ellipsis: true, slots: { customRender: 'code' } },
  { title: 'HMS 告警描述信息', dataIndex: 'message_zh', className: 'titleStyle', slots: { customRender: 'message' } },
]

interface DeviceHmsData {
  data: DeviceHms[]
}

const hmsData = reactive<DeviceHmsData>({
  data: []
})

type Pagination = TableState['pagination']

const hmsPaginationProp = reactive({
  pageSizeOptions: ['20', '50', '100'],
  showQuickJumper: true,
  showSizeChanger: true,
  buildOptionText: ({ value }) => `${value} 条/页`,
  locale: {
    jump_to: '跳至',
  },
  pageSize: 50,
  current: 1,
  total: 0
})

function getPaginationBody() {
  return {
    page: hmsPaginationProp.current,
    page_size: hmsPaginationProp.pageSize
  } as IPage
}

function showHms() {
  const dock = props.device
  if (!dock) return
  if (dock.domain === EDeviceTypeName.Dock) {
    getDeviceHmsBySn(dock.device_sn, dock.children?.[0]?.device_sn ?? '')
  }
  if (dock.domain === EDeviceTypeName.Aircraft) {
    domainFilter.value = EDeviceTypeName.Aircraft
    getDeviceHmsBySn('', dock.device_sn)
  }
}

function refreshHmsData(page: Pagination) {
  hmsPaginationProp.current = page?.current!
  hmsPaginationProp.pageSize = page?.pageSize!
  getHms()
}

const param = reactive<HmsQueryBody>({
  device_sn: '',
  language: 'en',
  begin_time: new Date(new Date().setDate(new Date().getDate() - 7)).setHours(0, 0, 0, 0),
  end_time: new Date().setHours(23, 59, 59, 999),
  level: '',
  message: ''
})

const domainFilter = ref(-1)
const childrenSn = ref('')

const levels = [
  {
    label: '全部级别',
    value: ''
  }, {
    label: '提示 (Notice)',
    value: EHmsLevel.NOTICE
  }, {
    label: '注意 (Caution)',
    value: EHmsLevel.CAUTION
  }, {
    label: '警告 (Warn)',
    value: EHmsLevel.WARN
  }
]

const deviceTypes = [
  {
    label: '全部设备',
    value: -1
  }, {
    label: '飞行器',
    value: EDeviceTypeName.Aircraft
  }, {
    label: '机场',
    value: EDeviceTypeName.Dock
  }
]

const rowClassName = (record: any, index: number) => {
  const className = []
  if ((index & 1) === 0) {
    className.push('table-striped')
  }
  if (record.domain !== EDeviceTypeName.Dock) {
    className.push('child-row')
  }
  return className.toString().replaceAll(',', ' ')
}

const time = ref<[Dayjs, Dayjs]>([dayjs(param.begin_time), dayjs(param.end_time)])

function getHmsLevelText(level: number) {
  switch (level) {
    case EHmsLevel.NOTICE: return '提示'
    case EHmsLevel.CAUTION: return '注意'
    case EHmsLevel.WARN: return '警告'
    default: return '未知'
  }
}

function getHmsBadgeClass(level: number) {
  switch (level) {
    case EHmsLevel.NOTICE: return 'badge-notice'
    case EHmsLevel.CAUTION: return 'badge-caution'
    case EHmsLevel.WARN: return 'badge-warn'
    default: return 'badge-notice'
  }
}

function getHmsStatusClass(record: any) {
  if (record.update_time) return 'status-resolved'
  if (record.level === EHmsLevel.WARN) return 'status-active-warn'
  if (record.level === EHmsLevel.CAUTION) return 'status-active-caution'
  return 'status-active-notice'
}

function getHms() {
  loading.value = true
  const deviceSn = param.device_sn
  const childSn = childrenSn.value
  const queryDomain = domainFilter.value

  const promises = []

  if (queryDomain === -1 || queryDomain === EDeviceTypeName.Dock) {
    if (deviceSn) {
      const p = { ...param, device_sn: deviceSn }
      promises.push(getDeviceHms(p, workspaceId, getPaginationBody()).then(res => ({
        ...res,
        sn: deviceSn
      })))
    }
  }

  if (queryDomain === -1 || queryDomain === EDeviceTypeName.Aircraft) {
    if (childSn) {
      const p = { ...param, device_sn: childSn }
      promises.push(getDeviceHms(p, workspaceId, getPaginationBody()).then(res => ({
        ...res,
        sn: childSn
      })))
    }
  }

  Promise.all(promises)
    .then(results => {
      let total = 0
      let allList: any[] = []

      results.forEach((res: any) => {
        if (res.data && res.data.list) {
          const isChild = res.sn === childSn
          res.data.list.forEach((item: any) => {
            item.domain = isChild ? EDeviceTypeName.Aircraft : EDeviceTypeName.Dock
            item.sn = res.sn
          })
          allList = allList.concat(res.data.list)
          total += res.data.pagination.total
        }
      })

      allList.sort((a, b) => b.create_time - a.create_time)

      hmsPaginationProp.total = total
      hmsPaginationProp.current = hmsPaginationProp.current
      hmsData.data = allList
      loading.value = false
    }).catch(_err => {
      console.error(_err)
      loading.value = false
    })
}

function getDeviceHmsBySn(sn: string, childSn: string) {
  param.device_sn = sn
  childrenSn.value = childSn
  getHms()
}

function onTimeChange(newTime: null | [Dayjs, Dayjs]) {
  if (!newTime) return
  param.begin_time = newTime[0].valueOf()
  param.end_time = newTime[1].valueOf()
  getHms()
}

function onDeviceTypeSelect(val: number) {
  getHms()
}

function onLevelSelect(val: number) {
  param.level = val
  getHms()
}
</script>

<style lang="scss" scoped>
:deep(.ant-drawer-content) {
  background: #0b1120 !important;
  color: #f1f5f9;
}

:deep(.ant-drawer-header) {
  background: #111827 !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1) !important;

  .ant-drawer-title {
    color: #ffffff !important;
    font-weight: 600;
    font-size: 16px;
  }

  .ant-drawer-close {
    color: rgba(255, 255, 255, 0.65) !important;
    &:hover {
      color: #ffffff !important;
    }
  }
}

.hms-drawer-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #ffffff;

  .title-icon {
    color: #1890ff;
    font-size: 18px;
  }
}

:deep(.ant-drawer-body) {
  padding: 20px;
  background: #0b1120;
}

.hms-drawer-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.searchBox {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px 14px;

  :deep(.ant-picker) {
    background: #1e293b !important;
    border-color: rgba(255, 255, 255, 0.15) !important;
    color: #ffffff !important;

    .ant-picker-input > input {
      color: #ffffff !important;
    }
    .ant-picker-suffix, .ant-picker-separator {
      color: rgba(255, 255, 255, 0.45) !important;
    }
  }

  :deep(.ant-select:not(.ant-select-customize-input) .ant-select-selector) {
    background: #1e293b !important;
    border-color: rgba(255, 255, 255, 0.15) !important;
    color: #ffffff !important;
    border-radius: 6px;
  }

  :deep(.ant-select-arrow) {
    color: rgba(255, 255, 255, 0.45) !important;
  }

  :deep(.ant-input-affix-wrapper) {
    background: #1e293b !important;
    border-color: rgba(255, 255, 255, 0.15) !important;
    color: #ffffff !important;
    border-radius: 6px;

    .ant-input {
      background: transparent !important;
      color: #ffffff !important;
    }

    .ant-input-search-icon {
      color: rgba(255, 255, 255, 0.45) !important;
    }
  }
}

.table-wrap {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px;

  :deep(.ant-table) {
    background: transparent !important;
    color: #f1f5f9 !important;

    .ant-table-thead > tr > th {
      background: #1e293b !important;
      color: rgba(255, 255, 255, 0.85) !important;
      font-weight: 600;
      border-bottom: 1px solid rgba(255, 255, 255, 0.1) !important;
    }

    .ant-table-tbody > tr > td {
      border-bottom: 1px solid rgba(255, 255, 255, 0.06) !important;
      background: transparent !important;
      color: rgba(255, 255, 255, 0.85) !important;
    }

    .ant-table-tbody > tr:hover > td {
      background: rgba(24, 144, 255, 0.08) !important;
    }
  }

  :deep(.ant-pagination) {
    .ant-pagination-item {
      background: rgba(255, 255, 255, 0.05) !important;
      border-color: rgba(255, 255, 255, 0.15) !important;
      a { color: rgba(255, 255, 255, 0.75) !important; }
      &-active {
        background: #1890ff !important;
        border-color: #1890ff !important;
        a { color: #ffffff !important; }
      }
    }
    .ant-pagination-prev, .ant-pagination-next {
      .ant-pagination-item-link {
        background: rgba(255, 255, 255, 0.05) !important;
        border-color: rgba(255, 255, 255, 0.15) !important;
        color: rgba(255, 255, 255, 0.75) !important;
      }
    }
    .ant-pagination-options {
      color: rgba(255, 255, 255, 0.65) !important;
    }
  }
}

.hms-level-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;

  .badge-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }

  &.badge-warn {
    background: rgba(239, 68, 68, 0.15);
    color: #fca5a5;
    border: 1px solid rgba(239, 68, 68, 0.3);
    .badge-dot {
      background: #ef4444;
      box-shadow: 0 0 8px #ef4444;
    }
  }

  &.badge-caution {
    background: rgba(245, 158, 11, 0.15);
    color: #fcd34d;
    border: 1px solid rgba(245, 158, 11, 0.3);
    .badge-dot {
      background: #f59e0b;
      box-shadow: 0 0 8px #f59e0b;
    }
  }

  &.badge-notice {
    background: rgba(34, 197, 94, 0.15);
    color: #86efac;
    border: 1px solid rgba(34, 197, 94, 0.3);
    .badge-dot {
      background: #22c55e;
      box-shadow: 0 0 8px #22c55e;
    }
  }
}

.domain-tag {
  font-size: 11.5px;
  font-weight: 500;
  border-radius: 4px;
  padding: 1px 8px;
}

.code-badge {
  font-family: 'JetBrains Mono', Consolas, Monaco, monospace;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: #94a3b8;
  padding: 2px 6px;
  border-radius: 4px;
}

.time-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;

  .time-create {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #e2e8f0;
    font-weight: 500;

    .time-icon {
      font-size: 11px;
      color: #94a3b8;
    }
  }

  .time-status {
    font-size: 11px;

    &.status-resolved {
      color: #94a3b8;
    }
    &.status-active-warn {
      color: #f87171;
      font-weight: 600;
    }
    &.status-active-caution {
      color: #fbbf24;
      font-weight: 600;
    }
    &.status-active-notice {
      color: #4ade80;
    }
  }
}

.message-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .message-zh {
    font-size: 13px;
    font-weight: 500;
    color: #f8fafc;
    line-height: 1.45;
  }

  .message-en {
    font-size: 11.5px;
    color: #64748b;
    line-height: 1.35;
  }
}
</style>
