<template>
  <div class="drone-properties-panel" :class="{ 'is-dark': darkTheme }">
    <!-- Header Bar -->
    <div class="panel-header" v-if="showHeader">
      <div class="header-left">
        <SlidersOutlined class="header-icon" />
        <span class="title-text">{{ title }}</span>
        <a-tag color="blue" class="sn-tag" v-if="deviceSn">SN: {{ deviceSn }}</a-tag>
        <a-tag v-if="lastReplyStatus" :color="lastReplyStatus.success ? 'green' : 'red'" style="margin-left: 4px;">
          {{ lastReplyStatus.text }}
        </a-tag>
      </div>

      <div class="header-actions">
        <a-tooltip title="下发属性的控制通道模式">
          <a-radio-group v-if="showChannelSwitch && !readonly" v-model:value="settingChannel" size="small" button-style="solid">
            <a-radio-button value="mqtt">
              <CloudUploadOutlined /> MQTT
            </a-radio-button>
            <!-- <a-radio-button value="http">
              <ApiOutlined /> HTTP
            </a-radio-button> -->
          </a-radio-group>
        </a-tooltip>

        <a-button type="primary" ghost size="small" :loading="loading" @click="fetchState(true)">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </div>
    </div>

    <!-- Loading State -->
    <a-spin :spinning="loading" tip="正在读取无人机状态数据...">
      <!-- Empty state -->
      <a-empty v-if="!loading && !hasData" description="暂未获取到无人机已设置属性数据" style="margin: 32px 0;" />

      <!-- Main Content Tabs -->
      <div v-else class="panel-body">
        <a-tabs v-model:activeKey="activeTab" type="card" size="small">
          <!-- 1. 照片存储设置 (photo_storage_settings) -->
          <a-tab-pane key="photo_storage" tab="拍照存储">
            <div class="storage-settings-wrapper">
              <div class="section-card photo-section-highlight">
                <div class="section-header">
                  <div class="flex items-center gap-2">
                    <span class="header-indicator" style="background: #38bdf8;"></span>
                    <span class="section-title">拍照存储设置 (photo_storage_settings)</span>
                    <span class="pill-badge-cyan">拍照存储</span>
                    <span v-if="isUserModified" class="pill-badge-unsaved">● 未保存修改</span>
                  </div>
                  <button
                    class="gradient-action-btn"
                    :disabled="readonly || savingKeys.has('photo_storage_settings')"
                    @click="savePhotoStorageConfig"
                  >
                    <span v-if="savingKeys.has('photo_storage_settings')" class="animate-spin mr-1">⏳</span>
                    <span v-else class="mr-1">⚡</span>
                    保存拍照存储
                  </button>
                </div>

                <!-- 当前照片存储状态条 -->
                <div class="status-banner mb-4">
                  <div class="banner-left">
                    <div class="banner-icon-badge">
                      <SlidersOutlined />
                    </div>
                    <div class="banner-info-col">
                      <div class="banner-title-row">
                        <span class="banner-title">当前拍照存储配置:</span>
                        <div class="tags-container" v-if="photoStorageSettings.length > 0">
                          <span
                            v-for="item in photoStorageSettings"
                            :key="item"
                            class="modern-pill-tag"
                            :style="{ borderColor: getPhotoStorageColor(item) + '80', color: getPhotoStorageColor(item), background: getPhotoStorageColor(item) + '20' }"
                          >
                            <span class="dot" :style="{ background: getPhotoStorageColor(item) }"></span>
                            {{ getPhotoStorageIcon(item) }} {{ getPhotoStorageLabel(item) }} ({{ item }})
                          </span>
                        </div>
                        <span v-else class="text-slate-300 text-xs">暂未配置 (默认)</span>
                      </div>
                      <span class="banner-subtitle">可选类型: {current (当前画面), vision (可见光), ir (红外)}，支持三者同时多选勾选</span>
                    </div>
                  </div>
                </div>

                <!-- 快捷预设模板 -->
                <div class="presets-row mb-4">
                  <span class="preset-label">快捷预设:</span>
                  <button
                    v-for="preset in photoStoragePresets"
                    :key="preset.label"
                    class="preset-chip"
                    :class="{ 'is-active': isPhotoPresetActive(preset.value) }"
                    :disabled="readonly"
                    @click="applyPhotoPreset(preset.value)"
                  >
                    <span class="preset-dot" v-if="isPhotoPresetActive(preset.value)">✓</span>
                    {{ preset.label }}
                  </button>
                </div>

                <!-- 镜头多选配置卡片 (3项: current, vision, ir) -->
                <div class="lens-grid">
                  <div
                    v-for="opt in photoStorageOptions"
                    :key="opt.value"
                    class="lens-card"
                    :class="{ 'is-selected': photoStorageSettings.includes(opt.value), 'is-disabled': readonly }"
                    :style="photoStorageSettings.includes(opt.value) ? { borderColor: opt.color, boxShadow: `0 0 18px ${opt.color}35`, background: `${opt.color}15` } : {}"
                    @click="!readonly && togglePhotoStorageOption(opt.value)"
                  >
                    <div class="lens-header-row">
                      <div class="lens-title-group">
                        <span class="lens-emoji">{{ opt.icon }}</span>
                        <span class="lens-name" :style="photoStorageSettings.includes(opt.value) ? { color: '#ffffff' } : {}">{{ opt.label }}</span>
                      </div>
                      <span class="lens-code-badge" :style="photoStorageSettings.includes(opt.value) ? { color: opt.color, borderColor: `${opt.color}60`, background: `${opt.color}25` } : {}">
                        {{ opt.value }}
                      </span>
                    </div>

                    <div class="lens-desc">{{ opt.desc }}</div>

                    <div class="lens-footer-row">
                      <span class="lens-status-text" :style="{ color: photoStorageSettings.includes(opt.value) ? opt.color : '#cbd5e1' }">
                        {{ photoStorageSettings.includes(opt.value) ? '● 已勾选存储' : '○ 未启用' }}
                      </span>
                      <div class="custom-check-circle" :class="{ 'is-checked': photoStorageSettings.includes(opt.value) }" :style="photoStorageSettings.includes(opt.value) ? { background: opt.color, borderColor: opt.color } : {}">
                        <svg v-if="photoStorageSettings.includes(opt.value)" viewBox="0 0 24 24" class="w-3.5 h-3.5 text-black stroke-current stroke-[3] fill-none">
                          <polyline points="20 6 9 17 4 12"></polyline>
                        </svg>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </a-tab-pane>

          <!-- 2. 相机水印设置 -->
          <a-tab-pane key="watermark" tab="相机水印">
            <div class="properties-grid">
              <!-- 水印全局开关 -->
              <div class="prop-card">
                <div class="prop-info">
                  <span class="prop-label">水印全局开关</span>
                  <span class="prop-desc">是否在生成的照片/视频中添加水印</span>
                </div>
                <div class="prop-action">
                  <a-switch
                    :checked="watermarkForm.global_enable === 1"
                    :disabled="readonly"
                    :loading="savingKeys.has('watermark')"
                    checked-children="开启"
                    un-checked-children="关闭"
                    @change="(val: boolean) => updateWatermarkField('global_enable', val ? 1 : 0)"
                  />
                </div>
              </div>

              <!-- 机型显示 -->
              <div class="prop-card">
                <div class="prop-info">
                  <span class="prop-label">机型名称水印</span>
                  <span class="prop-desc">在水印中显示飞行器型号</span>
                </div>
                <div class="prop-action">
                  <a-switch
                    :checked="watermarkForm.drone_type_enable === 1"
                    :disabled="readonly || watermarkForm.global_enable !== 1"
                    checked-children="显示"
                    un-checked-children="隐藏"
                    @change="(val: boolean) => updateWatermarkField('drone_type_enable', val ? 1 : 0)"
                  />
                </div>
              </div>

              <!-- SN 显示 -->
              <div class="prop-card">
                <div class="prop-info">
                  <span class="prop-label">序列号 (SN) 水印</span>
                  <span class="prop-desc">在水印中显示飞行器序列号</span>
                </div>
                <div class="prop-action">
                  <a-switch
                    :checked="watermarkForm.drone_sn_enable === 1"
                    :disabled="readonly || watermarkForm.global_enable !== 1"
                    checked-children="显示"
                    un-checked-children="隐藏"
                    @change="(val: boolean) => updateWatermarkField('drone_sn_enable', val ? 1 : 0)"
                  />
                </div>
              </div>

              <!-- 时间显示 -->
              <div class="prop-card">
                <div class="prop-info">
                  <span class="prop-label">日期时间水印</span>
                  <span class="prop-desc">在水印中显示拍摄日期时间</span>
                </div>
                <div class="prop-action">
                  <a-switch
                    :checked="watermarkForm.datetime_enable === 1"
                    :disabled="readonly || watermarkForm.global_enable !== 1"
                    checked-children="显示"
                    un-checked-children="隐藏"
                    @change="(val: boolean) => updateWatermarkField('datetime_enable', val ? 1 : 0)"
                  />
                </div>
              </div>

              <!-- GPS 显示 -->
              <div class="prop-card">
                <div class="prop-info">
                  <span class="prop-label">经纬度与海拔水印</span>
                  <span class="prop-desc">在水印中显示当前 GPS 经纬度和海拔高度</span>
                </div>
                <div class="prop-action">
                  <a-switch
                    :checked="watermarkForm.gps_enable === 1"
                    :disabled="readonly || watermarkForm.global_enable !== 1"
                    checked-children="显示"
                    un-checked-children="隐藏"
                    @change="(val: boolean) => updateWatermarkField('gps_enable', val ? 1 : 0)"
                  />
                </div>
              </div>

              <!-- 自定义文字水印 -->
              <div class="prop-card">
                <div class="prop-info">
                  <span class="prop-label">自定义文字水印</span>
                  <span class="prop-desc">在水印中添加用户自定义文案</span>
                </div>
                <div class="prop-action">
                  <a-switch
                    :checked="watermarkForm.user_custom_string_enable === 1"
                    :disabled="readonly || watermarkForm.global_enable !== 1"
                    checked-children="开启"
                    un-checked-children="关闭"
                    @change="(val: boolean) => updateWatermarkField('user_custom_string_enable', val ? 1 : 0)"
                  />
                </div>
              </div>

              <!-- 自定义文字输入 -->
              <div class="prop-card prop-card-wide" v-if="watermarkForm.user_custom_string_enable === 1">
                <div class="prop-info">
                  <span class="prop-label">自定义水印文本</span>
                  <span class="prop-desc">支持输入最多 31 个字符</span>
                </div>
                <div class="prop-action-inline">
                  <a-input
                    v-model:value="watermarkForm.user_custom_string"
                    placeholder="请输入自定义水印文本"
                    :maxlength="31"
                    style="width: 260px;"
                    size="small"
                    :disabled="readonly"
                  />
                </div>
              </div>

              <!-- 水印布局位置 -->
              <div class="prop-card prop-card-wide">
                <div class="prop-info">
                  <span class="prop-label">水印画面位置</span>
                  <span class="prop-desc">选择水印在生成画面中的对齐位置</span>
                </div>
                <div class="prop-action">
                  <a-radio-group
                    v-model:value="watermarkForm.layout"
                    :disabled="readonly || watermarkForm.global_enable !== 1"
                    size="small"
                    button-style="solid"
                  >
                    <a-radio-button :value="0">左下角</a-radio-button>
                    <a-radio-button :value="1">左上角</a-radio-button>
                    <a-radio-button :value="2">右上角</a-radio-button>
                    <a-radio-button :value="3">右下角</a-radio-button>
                  </a-radio-group>
                </div>
              </div>
            </div>

            <!-- 相机水印应用设置按钮 -->
            <div class="tab-footer">
              <a-button
                type="primary"
                :loading="savingKeys.has('camera_watermark_settings')"
                :disabled="readonly"
                @click="saveWatermarkConfig"
              >
                应用水印配置
              </a-button>
            </div>
          </a-tab-pane>

          <!-- 3. 飞行与返航设置 -->
          <a-tab-pane key="flight" tab="飞行与返航">
            <div class="properties-grid">
              <!-- 低电量返航阈值 -->
              <div class="prop-card">
                <div class="prop-info">
                  <span class="prop-label">低电量返航阈值</span>
                  <span class="prop-desc">触发自动返航的剩余电量百分比</span>
                </div>
                <div class="prop-action-inline">
                  <a-input-number
                    v-model:value="flightForm.remaining_power_for_return_home"
                    :min="10"
                    :max="50"
                    :step="1"
                    size="small"
                    style="width: 90px;"
                    :disabled="readonly"
                    addon-after="%"
                  />
                  <a-button
                    type="primary"
                    ghost
                    size="small"
                    :loading="savingKeys.has('remaining_power_for_return_home')"
                    :disabled="readonly"
                    @click="saveProperty('remaining_power_for_return_home', flightForm.remaining_power_for_return_home)"
                  >
                    设置
                  </a-button>
                </div>
              </div>

              <!-- 限高设置 -->
              <div class="prop-card">
                <div class="prop-info">
                  <span class="prop-label">飞行限高高度</span>
                  <span class="prop-desc">飞行器最大飞行海拔/高度 (20~1500m)</span>
                </div>
                <div class="prop-action-inline">
                  <a-input-number
                    v-model:value="flightForm.height_limit"
                    :min="20"
                    :max="1500"
                    :step="5"
                    size="small"
                    style="width: 90px;"
                    :disabled="readonly"
                    addon-after="m"
                  />
                  <a-button
                    type="primary"
                    ghost
                    size="small"
                    :loading="savingKeys.has('height_limit')"
                    :disabled="readonly"
                    @click="saveProperty('height_limit', flightForm.height_limit)"
                  >
                    设置
                  </a-button>
                </div>
              </div>

              <!-- 返航高度 -->
              <div class="prop-card">
                <div class="prop-info">
                  <span class="prop-label">安全返航高度</span>
                  <span class="prop-desc">自动返航时的飞行爬升高度 (20~500m)</span>
                </div>
                <div class="prop-action-inline">
                  <a-input-number
                    v-model:value="flightForm.rth_altitude"
                    :min="20"
                    :max="500"
                    :step="5"
                    size="small"
                    style="width: 90px;"
                    :disabled="readonly"
                    addon-after="m"
                  />
                  <a-button
                    type="primary"
                    ghost
                    size="small"
                    :loading="savingKeys.has('rth_altitude')"
                    :disabled="readonly"
                    @click="saveProperty('rth_altitude', flightForm.rth_altitude)"
                  >
                    设置
                  </a-button>
                </div>
              </div>

              <!-- 限远设置 -->
              <div class="prop-card">
                <div class="prop-info">
                  <span class="prop-label">飞行限远设置</span>
                  <span class="prop-desc">限制无人机距起飞点的最大水平距离</span>
                </div>
                <div class="prop-action-inline">
                  <a-switch
                    v-model:checked="flightForm.distance_limit_enable"
                    :disabled="readonly"
                    checked-children="开启"
                    un-checked-children="关闭"
                    style="margin-right: 6px;"
                  />
                  <a-input-number
                    v-if="flightForm.distance_limit_enable"
                    v-model:value="flightForm.distance_limit_val"
                    :min="15"
                    :max="8000"
                    :step="50"
                    size="small"
                    style="width: 90px;"
                    :disabled="readonly"
                    addon-after="m"
                  />
                  <a-button
                    type="primary"
                    ghost
                    size="small"
                    :loading="savingKeys.has('distance_limit_status')"
                    :disabled="readonly"
                    @click="saveDistanceLimit"
                  >
                    设置
                  </a-button>
                </div>
              </div>
            </div>
          </a-tab-pane>

          <!-- 4. 指点飞行配置 -->
          <a-tab-pane key="commander" tab="指点飞行">
            <div class="properties-grid">
              <!-- 指点高度 -->
              <div class="prop-card">
                <div class="prop-info">
                  <span class="prop-label">指点飞行默认高度</span>
                  <span class="prop-desc">指点飞行目标默认航行高度</span>
                </div>
                <div class="prop-action-inline">
                  <a-input-number
                    v-model:value="commanderForm.commander_flight_height"
                    :min="10"
                    :max="500"
                    :step="5"
                    size="small"
                    style="width: 90px;"
                    :disabled="readonly"
                    addon-after="m"
                  />
                  <a-button
                    type="primary"
                    ghost
                    size="small"
                    :loading="savingKeys.has('commander_flight_height')"
                    :disabled="readonly"
                    @click="saveProperty('commander_flight_height', commanderForm.commander_flight_height)"
                  >
                    保存
                  </a-button>
                </div>
              </div>
            </div>
          </a-tab-pane>
        </a-tabs>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  SlidersOutlined,
  ReloadOutlined,
  CloudUploadOutlined
} from '@ant-design/icons-vue'
import { useMyStore } from '@/store'
import { getDeviceState, putDeviceProps } from '@/api/device-setting'
import { publishMqttPropertySetWithReply, publishMqttServiceWithReply } from '@/utils/mqtt-property'
import type { CameraWatermarkSettings, DroneStateData } from '@/types/device-setting'

// Component Props Definition
const props = withDefaults(defineProps<{
  deviceSn: string
  gatewaySn?: string
  photoStorageSettings?: string[] // 接收外部传入的关键相机拍照存储设置
  deviceInfo?: any // 可选：全量设备信息兜底
  deviceModel?: string // 机型标识: M3D / M3TD / M4D / M4TD 等
  title?: string
  showHeader?: boolean
  readonly?: boolean
  darkTheme?: boolean
  autoRefresh?: boolean
  refreshInterval?: number
  showChannelSwitch?: boolean
}>(), {
  title: '无人机已设置属性配置',
  showHeader: true,
  readonly: false,
  darkTheme: true,
  autoRefresh: false,
  refreshInterval: 5000,
  showChannelSwitch: true
})

// Emits
const emit = defineEmits<{
  (e: 'refresh', state: DroneStateData): void
  (e: 'set-success', propertyKey: string, value: any, channel: string, replyData?: any): void
  (e: 'set-error', propertyKey: string, error: any): void
}>()

const store = useMyStore()

// State
const loading = ref<boolean>(false)
const hasData = ref<boolean>(false)
const activeTab = ref<string>('photo_storage')
const settingChannel = ref<'mqtt' | 'http'>('mqtt')
const savingKeys = reactive<Set<string>>(new Set())
const lastReplyStatus = ref<{ success: boolean; text: string } | null>(null)

// 标记用户是否正在手动修改配置（防止被后台高频 OSD 遥测数据瞬间覆盖）
const isUserModified = ref<boolean>(false)

// Raw State Storage
const rawStateData = ref<DroneStateData>({})

// ==========================================
// 1. 照片存储设置
// ==========================================
const photoStorageSettings = ref<string[]>(['vision', 'ir'])

const photoStorageOptions = [
  { value: 'current', label: '当前画面', icon: '🎯', desc: '保存当前图传显示实时画面', code: 'current', color: '#38bdf8' },
  { value: 'vision', label: '可见光', icon: '👁️', desc: '保存可见光高清镜头原图', code: 'vision', color: '#60a5fa' },
  { value: 'ir', label: '红外镜头', icon: '🌡️', desc: '保存热成像红外测温图片', code: 'ir', color: '#fb923c' }
]

const photoStoragePresets = [
  { label: '当前画面 (current)', value: ['current'] },
  { label: '可见光 (vision)', value: ['vision'] },
  { label: '红外 (ir)', value: ['ir'] },
  { label: '可见光 + 红外 (vision + ir)', value: ['vision', 'ir'] },
  { label: '全选 (current + vision + ir)', value: ['current', 'vision', 'ir'] }
]

function getPhotoStorageLabel(val: string): string {
  const match = photoStorageOptions.find(opt => opt.value === val)
  return match ? match.label : val
}

function getPhotoStorageIcon(val: string): string {
  const match = photoStorageOptions.find(opt => opt.value === val)
  return match ? match.icon : '📷'
}

function getPhotoStorageColor(val: string): string {
  const match = photoStorageOptions.find(opt => opt.value === val)
  return match ? match.color : '#38bdf8'
}

function applyPhotoPreset(presetVal: string[]) {
  isUserModified.value = true
  photoStorageSettings.value = [...presetVal]
}

// 支持 current (当前画面), vision (可见光), ir (红外) 三者同时勾选多选
function togglePhotoStorageOption(val: string) {
  isUserModified.value = true
  if (photoStorageSettings.value.includes(val)) {
    photoStorageSettings.value = photoStorageSettings.value.filter(item => item !== val)
  } else {
    photoStorageSettings.value = [...photoStorageSettings.value, val]
  }
}

function isPhotoPresetActive(presetVal: string[]): boolean {
  if (photoStorageSettings.value.length !== presetVal.length) return false
  return presetVal.every(item => photoStorageSettings.value.includes(item))
}

/**
 * 从 props / deviceInfo 或 store 中同步提取当前拍照存储配置
 * @param targetInfo 目标设备信息
 * @param force 是否强制覆盖用户本地修改
 */
function syncPhotoStorageFromDeviceInfo(targetInfo?: any, force = false): boolean {
  // 如果用户手动修改了勾选项且未强制刷新，则不被遥测覆盖
  if (isUserModified.value && !force) {
    return false
  }

  // 1. 优先使用外部直接显式传递的 photoStorageSettings
  if (props.photoStorageSettings && Array.isArray(props.photoStorageSettings) && props.photoStorageSettings.length > 0) {
    photoStorageSettings.value = [...props.photoStorageSettings]
    return true
  }

  const info = targetInfo || props.deviceInfo || store.state.deviceInfo
  if (!info) return false

  // 2. deviceInfo?.device?.cameras?.[0]?.photo_storage_settings
  const devCam = info?.device?.cameras?.[0]
  if (devCam?.photo_storage_settings && Array.isArray(devCam.photo_storage_settings) && devCam.photo_storage_settings.length > 0) {
    photoStorageSettings.value = [...devCam.photo_storage_settings]
    return true
  }

  // 3. deviceInfo?.dock?.basic_osd?.sub_device?.cameras?.[0]?.photo_storage_settings
  const dockSubCam = info?.dock?.basic_osd?.sub_device?.cameras?.[0]
  if (dockSubCam?.photo_storage_settings && Array.isArray(dockSubCam.photo_storage_settings) && dockSubCam.photo_storage_settings.length > 0) {
    photoStorageSettings.value = [...dockSubCam.photo_storage_settings]
    return true
  }

  // 4. deviceInfo?.cameras?.[0]?.photo_storage_settings
  const directCam = info?.cameras?.[0]
  if (directCam?.photo_storage_settings && Array.isArray(directCam.photo_storage_settings) && directCam.photo_storage_settings.length > 0) {
    photoStorageSettings.value = [...directCam.photo_storage_settings]
    return true
  }

  // 5. deviceInfo?.photo_storage_settings
  if (info?.photo_storage_settings && Array.isArray(info.photo_storage_settings) && info.photo_storage_settings.length > 0) {
    photoStorageSettings.value = [...info.photo_storage_settings]
    return true
  }

  return false
}

// 监听关键拍照存储设置或 deviceInfo 变化，在非用户编辑状态下实时回显
watch(
  () => [props.photoStorageSettings, props.deviceInfo, store.state.deviceInfo],
  () => {
    if (!isUserModified.value) {
      syncPhotoStorageFromDeviceInfo()
    }
  },
  { deep: true, immediate: true }
)

// 获取当前相机负载 payload_index (如 53-0-0)
const payloadIndex = computed(() => {
  if (rawStateData.value?.cameras?.[0]?.payload_index) {
    return rawStateData.value.cameras[0].payload_index
  }
  const info = props.deviceInfo || store.state.deviceInfo
  if (info?.device?.cameras?.[0]?.payload_index) {
    return info.device.cameras[0].payload_index
  }
  if (info?.dock?.basic_osd?.sub_device?.cameras?.[0]?.payload_index) {
    return info.dock.basic_osd.sub_device.cameras[0].payload_index
  }
  return '53-0-0'
})

// 保存照片存储设置 (photo_storage_set / photo_storage_settings)
async function savePhotoStorageConfig() {
  if (!photoStorageSettings.value || photoStorageSettings.value.length === 0) {
    message.warning('请至少选择一种拍照存储镜头类型')
    return
  }

  const targetGatewaySn = props.gatewaySn || props.deviceSn
  if (settingChannel.value === 'mqtt' && store.state.mqttState) {
    savingKeys.add('photo_storage_settings')
    try {
      const res = await publishMqttServiceWithReply(
        targetGatewaySn,
        'photo_storage_set',
        {
          payload_index: payloadIndex.value,
          photo_storage_settings: [...photoStorageSettings.value]
        },
        store.state.mqttState,
        8000
      )
      if (res.success) {
        message.success('[MQTT] 拍照存储设置成功')
        isUserModified.value = false
        lastReplyStatus.value = { success: true, text: 'MQTT: 拍照存储设置成功' }
        emit('set-success', 'photo_storage_settings', photoStorageSettings.value, 'mqtt', res)
        setTimeout(() => { fetchState(true) }, 500)
        return
      }
    } catch (e) {
      console.warn('[MQTT] photo_storage_set 服务调用异常，回退至 property/set...', e)
    } finally {
      savingKeys.delete('photo_storage_settings')
    }
  }

  isUserModified.value = false
  dispatchPropertySet('photo_storage_settings', [...photoStorageSettings.value])
}

// 自动检测并提取当前无人机机型 (M3D, M3TD, M4D, M4TD 等)
const detectedModel = computed(() => {
  if (props.deviceModel) return props.deviceModel.toUpperCase()
  if (rawStateData.value) {
    const rawAny = rawStateData.value as any
    const name = rawAny?.device_name || rawAny?.sub_device?.device_name || rawAny?.type || ''
    if (name) return String(name).toUpperCase()
  }
  return ''
})

// Forms Reactive Data
const watermarkForm = reactive<CameraWatermarkSettings>({
  global_enable: 0,
  drone_type_enable: 0,
  drone_sn_enable: 0,
  datetime_enable: 0,
  gps_enable: 0,
  user_custom_string_enable: 0,
  user_custom_string: '',
  layout: 0
})

const flightForm = reactive({
  remaining_power_for_return_home: 15,
  height_limit: 120,
  rth_altitude: 100,
  distance_limit_enable: false,
  distance_limit_val: 2000
})

const commanderForm = reactive({
  commander_flight_height: 100,
  commander_flight_mode: 0,
  commander_mode_lost_action: 0
})

let timer: any = null

/**
 * 获取无人机状态
 * @param force 是否强制覆盖本地修改
 */
async function fetchState(force = false) {
  if (!props.deviceSn) return
  if (force) {
    isUserModified.value = false
  }
  loading.value = true
  try {
    const res = await getDeviceState(props.deviceSn)
    if (res && res.code === 0 && res.data) {
      hasData.value = true
      rawStateData.value = res.data

      // 仅在非用户编辑中或强制刷新时更新配置
      if (!isUserModified.value || force) {
        if (res.data.photo_storage_settings && Array.isArray(res.data.photo_storage_settings) && res.data.photo_storage_settings.length > 0) {
          photoStorageSettings.value = [...res.data.photo_storage_settings]
        } else if (res.data.cameras && Array.isArray(res.data.cameras) && res.data.cameras.length > 0) {
          const cam = res.data.cameras[0]
          if (cam && Array.isArray(cam.photo_storage_settings) && cam.photo_storage_settings.length > 0) {
            photoStorageSettings.value = [...cam.photo_storage_settings]
          }
        } else {
          syncPhotoStorageFromDeviceInfo(undefined, force)
        }
      }

      // Map Watermark Settings
      if (res.data.camera_watermark_settings) {
        Object.assign(watermarkForm, res.data.camera_watermark_settings)
      }

      // Map Flight Settings
      if (res.data.remaining_power_for_return_home !== undefined) {
        flightForm.remaining_power_for_return_home = res.data.remaining_power_for_return_home
      }
      if (res.data.height_limit !== undefined) {
        flightForm.height_limit = res.data.height_limit
      }
      if (res.data.rth_altitude !== undefined) {
        flightForm.rth_altitude = res.data.rth_altitude
      }
      if (res.data.distance_limit_status) {
        flightForm.distance_limit_enable = res.data.distance_limit_status.state === 1
        flightForm.distance_limit_val = res.data.distance_limit_status.distance_limit || 2000
      }

      // Map Commander Settings
      if (res.data.commander_flight_height !== undefined) {
        commanderForm.commander_flight_height = res.data.commander_flight_height
      }
      if (res.data.commander_flight_mode !== undefined) {
        commanderForm.commander_flight_mode = res.data.commander_flight_mode
      }
      if (res.data.commander_mode_lost_action !== undefined) {
        commanderForm.commander_mode_lost_action = res.data.commander_mode_lost_action
      }

      emit('refresh', res.data)
    } else {
      hasData.value = false
    }
  } catch (err) {
    console.error('获取无人机属性失败:', err)
  } finally {
    loading.value = false
  }
}

/**
 * 通用设置属性下发逻辑 (支持 MQTT / HTTP 双通道)
 * @param propertyKey 属性标识
 * @param value 属性值 payload
 */
async function dispatchPropertySet(propertyKey: string, value: any) {
  const payloadData: Record<string, any> = { [propertyKey]: value }

  savingKeys.add(propertyKey)

  try {
    if (settingChannel.value === 'mqtt') {
      const targetGatewaySn = props.gatewaySn || props.deviceSn
      const result = await publishMqttPropertySetWithReply(targetGatewaySn, payloadData, store.state.mqttState, 8000)

      if (result.success) {
        const msgText = `[MQTT set_reply] 【${propertyKey}】设置成功 (${result.resultMsg || '0: 成功'})`
        message.success(msgText)
        lastReplyStatus.value = { success: true, text: `MQTT: 设置成功` }
        emit('set-success', propertyKey, value, 'mqtt', result)
        // 成功响应后，自动延迟刷新全量属性（确认 Redis 保持最新）
        setTimeout(() => { fetchState() }, 500)
      } else {
        if (result.error === '未连接 MQTT 客户端服务') {
          message.warning(`[MQTT] 客户端未连通，自动回退至 HTTP 接口进行设置...`)
          const httpRes = await putDeviceProps(props.deviceSn, payloadData)
          if (httpRes.code === 0) {
            message.success(`[HTTP] 属性【${propertyKey}】设置成功`)
            lastReplyStatus.value = { success: true, text: `HTTP: 设置成功` }
            emit('set-success', propertyKey, value, 'http', httpRes)
            setTimeout(() => { fetchState() }, 500)
          } else {
            throw new Error(httpRes.message || 'HTTP 设置接口错误')
          }
        } else {
          // 设备真正返回了 set_reply 错误码或超时
          const errDetail = result.resultMsg || result.error || '设备设置失败'
          const errMsg = `[MQTT set_reply] 属性【${propertyKey}】设置失败: ${errDetail}`
          message.error(errMsg)
          lastReplyStatus.value = { success: false, text: `MQTT: ${errDetail}` }
          emit('set-error', propertyKey, result)
        }
      }
    } else {
      const httpRes = await putDeviceProps(props.deviceSn, payloadData)
      if (httpRes.code === 0) {
        message.success(`[HTTP] 属性【${propertyKey}】设置成功`)
        lastReplyStatus.value = { success: true, text: `HTTP: 设置成功` }
        emit('set-success', propertyKey, value, 'http', httpRes)
        setTimeout(() => { fetchState() }, 500)
      } else {
        throw new Error(httpRes.message || 'HTTP 设置接口错误')
      }
    }
  } catch (err: any) {
    console.error(`设置属性 [${propertyKey}] 失败:`, err)
    message.error(`设置【${propertyKey}】失败: ${err.message || err}`)
    lastReplyStatus.value = { success: false, text: `错误: ${err.message || err}` }
    emit('set-error', propertyKey, err)
  } finally {
    savingKeys.delete(propertyKey)
  }
}

// 帮助设置单项属性
function saveProperty(key: string, val: any) {
  dispatchPropertySet(key, val)
}

// 保存相机水印配置
function updateWatermarkField(field: keyof CameraWatermarkSettings, val: any) {
  (watermarkForm as any)[field] = val
  saveWatermarkConfig()
}

function saveWatermarkConfig() {
  dispatchPropertySet('camera_watermark_settings', { ...watermarkForm })
}

// 保存限远
function saveDistanceLimit() {
  const payload = {
    state: flightForm.distance_limit_enable ? 1 : 0,
    distance_limit: flightForm.distance_limit_val
  }
  dispatchPropertySet('distance_limit_status', payload)
}

// 保存避障
function saveObstacleAvoidance() {
  const payload = {
    horizon: safetyForm.obstacle_horizon,
    upside: safetyForm.obstacle_upside,
    downside: safetyForm.obstacle_downside
  }
  dispatchPropertySet('obstacle_avoidance', payload)
}

// Lifecycle Hooks & Watchers
watch(() => props.deviceSn, () => {
  fetchState()
}, { immediate: true })

onMounted(() => {
  if (props.autoRefresh && props.refreshInterval > 0) {
    timer = setInterval(fetchState, props.refreshInterval)
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

// Expose Ref Methods for parent component usage
defineExpose({
  fetchState,
  saveProperty,
  dispatchPropertySet,
  rawStateData
})
</script>

<style lang="scss" scoped>
.drone-properties-panel {
  background: #111622;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  padding: 16px;
  color: #fff;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.36);

  &.is-dark {
    color: #f0f5ff;

    /* Override Ant Design components in Dark Mode */
    :deep(.ant-input-number) {
      background: #1d2636 !important;
      border-color: rgba(255, 255, 255, 0.2) !important;
      color: #ffffff !important;
      border-radius: 4px;

      .ant-input-number-input {
        color: #ffffff !important;
        background: transparent !important;
        text-align: center;
      }

      .ant-input-number-handler-wrap {
        background: rgba(255, 255, 255, 0.1) !important;
        border-left-color: rgba(255, 255, 255, 0.15) !important;
      }
    }

    :deep(.ant-input-number-group-addon) {
      background: #263347 !important;
      border-color: rgba(255, 255, 255, 0.2) !important;
      color: rgba(255, 255, 255, 0.85) !important;
    }

    :deep(.ant-input) {
      background: #1d2636 !important;
      border-color: rgba(255, 255, 255, 0.2) !important;
      color: #ffffff !important;

      &::placeholder {
        color: rgba(255, 255, 255, 0.35) !important;
      }
    }

    :deep(.ant-input-disabled) {
      background: rgba(255, 255, 255, 0.05) !important;
      border-color: rgba(255, 255, 255, 0.1) !important;
      color: rgba(255, 255, 255, 0.35) !important;
    }

    :deep(.ant-input-affix-wrapper) {
      background: #1d2636 !important;
      border-color: rgba(255, 255, 255, 0.2) !important;
      color: #ffffff !important;
      border-radius: 4px;

      .ant-input {
        background: transparent !important;
        border: none !important;
        box-shadow: none !important;
        color: #ffffff !important;
        padding: 0 !important;
      }

      &::placeholder {
        color: rgba(255, 255, 255, 0.35) !important;
      }

      &:hover {
        border-color: rgba(24, 144, 255, 0.6) !important;
      }

      &:focus {
        border-color: #1890ff !important;
        box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2) !important;
      }
    }

    :deep(.ant-input-affix-wrapper-focused) {
      border-color: #1890ff !important;
      box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2) !important;
    }

    :deep(.ant-input-affix-wrapper-disabled) {
      background: rgba(255, 255, 255, 0.05) !important;
      border-color: rgba(255, 255, 255, 0.1) !important;
      color: rgba(255, 255, 255, 0.35) !important;

      .ant-input {
        color: rgba(255, 255, 255, 0.35) !important;
      }

      .ant-btn {
        color: rgba(255, 255, 255, 0.25) !important;
        pointer-events: none;
      }
    }

    :deep(.ant-select:not(.ant-select-customize-input) .ant-select-selector) {
      background: #1d2636 !important;
      border-color: rgba(255, 255, 255, 0.2) !important;
      color: #ffffff !important;
    }

    :deep(.ant-select-arrow) {
      color: rgba(255, 255, 255, 0.6) !important;
    }

    :deep(.ant-radio-button-wrapper) {
      background: rgba(30, 41, 59, 0.7) !important;
      border-color: rgba(255, 255, 255, 0.12) !important;
      color: #94a3b8 !important;
      border-radius: 4px;

      &.ant-radio-button-wrapper-checked {
        background: linear-gradient(135deg, #1d4ed8 0%, #0284c7 100%) !important;
        border-color: #38bdf8 !important;
        color: #ffffff !important;
        font-weight: 600;
        box-shadow: 0 0 10px rgba(56, 189, 248, 0.3);
      }
    }
  }

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);

    .header-left {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;

      .header-icon {
        color: #38bdf8;
        font-size: 18px;
      }

      .title-text {
        font-size: 15px;
        font-weight: 600;
        white-space: nowrap;
        color: #ffffff;
      }

      .sn-tag {
        font-family: monospace;
        font-size: 11.5px;
        font-weight: 500;
        background: rgba(56, 189, 248, 0.12) !important;
        border: 1px solid rgba(56, 189, 248, 0.3) !important;
        color: #38bdf8 !important;
        border-radius: 4px;
        padding: 1px 8px;
        margin: 0;
      }
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 10px;
    }
  }

  .properties-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    padding: 8px 0;

    .prop-card {
      width: calc(50% - 6px);
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 8px;
      padding: 12px 14px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);

      &:hover {
        background: rgba(255, 255, 255, 0.07);
        border-color: rgba(24, 144, 255, 0.4);
      }

      &.prop-card-wide {
        width: 100%;
      }

      .prop-info {
        display: flex;
        flex-direction: column;
        gap: 3px;
        max-width: 65%;

        .prop-label {
          font-size: 13.5px;
          font-weight: 600;
          color: rgba(255, 255, 255, 0.95);
        }

        .prop-desc {
          font-size: 11px;
          color: rgba(255, 255, 255, 0.5);
          line-height: 1.3;
        }
      }

      .prop-action {
        display: flex;
        align-items: center;
      }

      .prop-action-inline {
        display: flex;
        align-items: center;
        gap: 8px;
      }

      .sub-label {
        font-size: 12px;
        font-weight: 500;
        color: rgba(255, 255, 255, 0.85);
      }

      .prop-action-column {
        display: flex;
        flex-direction: column;
        width: 100%;

        .switch-row, .inline-input-row {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-top: 8px;
        }
      }
    }
  }

  .storage-settings-wrapper {
    display: flex;
    flex-direction: column;
    gap: 14px;

    .section-card {
      background: rgba(15, 23, 42, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 12px;
      padding: 16px 18px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.25);

      .section-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 14px;

        .header-indicator {
          width: 4px;
          height: 16px;
          border-radius: 2px;
          background: #38bdf8;
        }

        .section-title {
          font-size: 14px;
          font-weight: 600;
          color: #ffffff;
          letter-spacing: 0.2px;
        }

        .pill-badge-cyan {
          font-size: 11px;
          padding: 2px 8px;
          border-radius: 12px;
          background: rgba(56, 189, 248, 0.18);
          border: 1px solid rgba(56, 189, 248, 0.5);
          color: #38bdf8;
          font-weight: 500;
        }

        .pill-badge-unsaved {
          font-size: 11px;
          padding: 2px 8px;
          border-radius: 12px;
          background: rgba(245, 158, 11, 0.2);
          border: 1px solid #f59e0b;
          color: #fbbf24;
          font-weight: 600;
        }
      }

      .gradient-action-btn {
        background: linear-gradient(135deg, #0284c7 0%, #2563eb 100%) !important;
        color: #ffffff !important;
        border: 1px solid rgba(255, 255, 255, 0.35) !important;
        border-radius: 6px;
        padding: 6px 16px;
        font-size: 13px;
        font-weight: 600;
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
        box-shadow: 0 3px 12px rgba(2, 132, 199, 0.45);

        &:hover:not(:disabled) {
          background: linear-gradient(135deg, #0ea5e9 0%, #3b82f6 100%) !important;
          transform: translateY(-1px);
          box-shadow: 0 5px 18px rgba(14, 165, 233, 0.6);
        }

        &:disabled {
          opacity: 0.5;
          cursor: not-allowed;
          box-shadow: none;
        }
      }

      .status-banner {
        display: flex;
        align-items: center;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: 12px;
        padding: 12px 16px;
        background: rgba(30, 41, 59, 0.55);
        border: 1px solid rgba(56, 189, 248, 0.25);
        border-radius: 10px;

        .banner-left {
          display: flex;
          align-items: center;
          gap: 12px;

          .banner-icon-badge {
            width: 36px;
            height: 36px;
            border-radius: 8px;
            background: rgba(56, 189, 248, 0.15);
            border: 1px solid rgba(56, 189, 248, 0.35);
            color: #38bdf8;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 16px;
            flex-shrink: 0;
          }

          .banner-info-col {
            display: flex;
            flex-direction: column;
            gap: 4px;

            .banner-title-row {
              display: flex;
              align-items: center;
              flex-wrap: wrap;
              gap: 8px;

              .banner-title {
                font-size: 13.5px;
                font-weight: 600;
                color: #ffffff;
              }

              .modern-pill-tag {
                display: inline-flex;
                align-items: center;
                gap: 5px;
                padding: 2px 10px;
                border-radius: 20px;
                font-size: 12px;
                font-weight: 600;
                border: 1px solid;
                line-height: 1.4;

                .dot {
                  width: 6px;
                  height: 6px;
                  border-radius: 50%;
                }
              }
            }

            .banner-subtitle {
              font-size: 12px;
              color: #cbd5e1;
            }
          }
        }
      }

      .presets-row {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 10px;

        .preset-label {
          font-size: 13px;
          font-weight: 600;
          color: #f1f5f9;
          margin-right: 4px;
        }

        .preset-chip {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          background: rgba(255, 255, 255, 0.08);
          border: 1px solid rgba(255, 255, 255, 0.18);
          border-radius: 6px;
          padding: 6px 14px;
          font-size: 12.5px;
          color: #f1f5f9;
          font-weight: 500;
          cursor: pointer;
          transition: all 0.2s;

          &:hover:not(:disabled) {
            background: rgba(56, 189, 248, 0.18);
            border-color: #38bdf8;
            color: #38bdf8;
          }

          &.is-active {
            background: linear-gradient(135deg, rgba(2, 132, 199, 0.35) 0%, rgba(37, 99, 235, 0.25) 100%);
            border-color: #38bdf8;
            color: #ffffff;
            font-weight: 600;
            box-shadow: 0 0 12px rgba(56, 189, 248, 0.35);
          }

          .preset-dot {
            color: #38bdf8;
            font-weight: bold;
          }
        }
      }

      .lens-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
        gap: 12px;

        .lens-card {
          background: rgba(255, 255, 255, 0.04);
          border: 1px solid rgba(255, 255, 255, 0.14);
          border-radius: 10px;
          padding: 14px;
          display: flex;
          flex-direction: column;
          gap: 10px;
          cursor: pointer;
          transition: all 0.22s cubic-bezier(0.4, 0, 0.2, 1);
          user-select: none;

          &:hover:not(.is-disabled) {
            background: rgba(255, 255, 255, 0.08);
            border-color: rgba(56, 189, 248, 0.5);
            transform: translateY(-2px);
            box-shadow: 0 6px 18px rgba(0, 0, 0, 0.35);
          }

          &.is-selected {
            background: rgba(56, 189, 248, 0.12);
            border-color: #38bdf8;
          }

          &.is-disabled {
            cursor: not-allowed;
            opacity: 0.5;
          }

          .lens-header-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 6px;

            .lens-title-group {
              display: flex;
              align-items: center;
              gap: 8px;

              .lens-emoji {
                font-size: 16px;
              }

              .lens-name {
                font-size: 14px;
                font-weight: 600;
                color: #ffffff;
                transition: color 0.2s;
              }
            }

            .lens-code-badge {
              font-size: 11px;
              font-family: monospace;
              padding: 2px 7px;
              background: rgba(255, 255, 255, 0.08);
              border: 1px solid rgba(255, 255, 255, 0.18);
              border-radius: 4px;
              color: #cbd5e1;
              transition: all 0.2s;
            }
          }

          .lens-desc {
            font-size: 12px;
            color: #cbd5e1;
            line-height: 1.45;
            min-height: 32px;
          }

          .lens-footer-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding-top: 8px;
            border-top: 1px solid rgba(255, 255, 255, 0.09);

            .lens-status-text {
              font-size: 11.5px;
              font-weight: 500;
              color: #cbd5e1;
            }

            .custom-check-circle {
              width: 18px;
              height: 18px;
              border-radius: 50%;
              border: 1.5px solid rgba(255, 255, 255, 0.35);
              display: flex;
              align-items: center;
              justify-content: center;
              transition: all 0.2s;

              &.is-checked {
                box-shadow: 0 0 8px rgba(56, 189, 248, 0.45);
              }
            }
          }
        }
      }
    }
  }

  .tab-footer {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
    padding-top: 12px;
    border-top: 1px solid rgba(255, 255, 255, 0.08);
  }
}

:deep(.ant-tabs-nav) {
  margin-bottom: 14px !important;
}

:deep(.ant-tabs-card) {
  .ant-tabs-nav-wrap {
    background: rgba(15, 23, 42, 0.6);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 8px;
    padding: 4px;
  }

  .ant-tabs-tab {
    background: transparent !important;
    border: none !important;
    border-radius: 6px !important;
    color: #94a3b8 !important;
    font-size: 13px !important;
    padding: 6px 16px !important;
    margin-right: 4px !important;
    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1) !important;

    &:hover {
      color: #f8fafc !important;
      background: rgba(255, 255, 255, 0.05) !important;
    }

    &.ant-tabs-tab-active {
      background: linear-gradient(135deg, #1d4ed8 0%, #0284c7 100%) !important;
      color: #ffffff !important;
      box-shadow: 0 2px 10px rgba(2, 132, 199, 0.4) !important;

      .ant-tabs-tab-btn {
        color: #ffffff !important;
        font-weight: 600 !important;
      }
    }
  }
}
</style>
