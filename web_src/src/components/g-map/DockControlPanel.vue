<template>
  <div v-drag-window class="dock-control-panel">
    <!-- title -->
    <div class="dock-control-panel-header drag-title fz16 pl5 pr5 flex-align-center flex-row flex-justify-between">
      <span>设置面板<span class="fz12 pl15">{{ props.sn }}</span></span>
      <span @mousedown.stop @pointerdown.stop @click="closeControlPanel" class="cursor-pointer">
        <CloseOutlined />
      </span>
    </div>
    <!-- setting -->
    <DeviceSettingBox :sn="props.sn" :deviceInfo="props.deviceInfo"></DeviceSettingBox>
    <!-- cmd -->
    <!-- 空调模式 -->
    <div class="control-cmd-wrapper">
      <div class="control-cmd-header">空调模式</div>
      <div class="control-cmd-box">
        <div class="control-cmd-item air-conditioner-item">
          <div class="control-cmd-item-left">
            <div class="item-label">当前模式</div>
            <div class="item-status">{{ airConditionerModeText }}</div>
          </div>
          <div class="control-cmd-item-right">
            <a-button class="text-white" size="small" type="primary" @click="showAirConditionerModal = true">
              调整
            </a-button>
          </div>
        </div>
      </div>
    </div>

    <!-- cmd -->
    <div class="control-cmd-wrapper">
      <div class="control-cmd-header">
        远程调试
        <a-switch class="debug-btn" checked-children="开" un-checked-children="关" v-model:checked="debugStatus"
          @change="onDeviceStatusChange" />
      </div>
      <div class="control-cmd-box">
        <div v-for="(cmdItem, index) in cmdList" :key="cmdItem.cmdKey" class="control-cmd-item">
          <div class="control-cmd-item-left">
            <div class="item-label">{{ cmdItem.label }}</div>
            <div class="item-status">{{ cmdItem.status }}</div>
          </div>
          <div class="control-cmd-item-right">
            <a-button class="text-white" :disabled="!debugStatus || cmdItem.disabled" :loading="cmdItem.loading"
              size="small" type="primary" @click="sendControlCmd(cmdItem, index)">
              {{ cmdItem.operateText }}
            </a-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 空调模式调整弹窗 - 自定义深色主题 -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="showAirConditionerModal" class="fixed inset-0 z-[10000] flex items-center justify-center">
          <!-- 遮罩层 -->
          <div class="absolute inset-0 bg-black/70 backdrop-blur-sm" @click="showAirConditionerModal = false"></div>

          <!-- 弹窗内容 -->
          <div
            class="relative bg-gradient-to-br from-gray-900 to-gray-800 rounded-lg shadow-2xl w-[380px] max-h-[70vh] overflow-hidden border border-gray-700">
            <!-- 标题栏 -->
            <div class="flex items-center justify-between px-4 py-2.5 border-b border-gray-700 bg-gray-900/50">
              <h3 class="text-base font-semibold text-white">空调模式调整</h3>
              <button @click="showAirConditionerModal = false"
                class="text-gray-400 hover:text-white transition-colors duration-200">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <!-- 内容区域 -->
            <div class="px-4 py-3 overflow-y-auto max-h-[50vh]">
              <div class="space-y-2">
                <!-- 空闲模式 -->
                <div @click="selectedAirConditionerMode = 0" :class="[
                  'group cursor-pointer rounded-md p-2.5 transition-all duration-200 border',
                  selectedAirConditionerMode === 0
                    ? 'bg-gradient-to-r from-emerald-500/20 to-teal-500/20 border-emerald-500 shadow-md shadow-emerald-500/20'
                    : 'bg-gray-800/50 border-gray-700 hover:border-gray-600 hover:bg-gray-800'
                ]">
                  <div class="flex items-start">
                    <div :class="[
                      'flex-shrink-0 w-4 h-4 rounded-full border-2 mt-0.5 transition-all duration-200',
                      selectedAirConditionerMode === 0
                        ? 'border-emerald-500 bg-emerald-500'
                        : 'border-gray-600 group-hover:border-gray-500'
                    ]">
                      <svg v-if="selectedAirConditionerMode === 0" class="w-full h-full text-white" fill="currentColor"
                        viewBox="0 0 20 20">
                        <path fill-rule="evenodd"
                          d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                          clip-rule="evenodd" />
                      </svg>
                    </div>
                    <div class="ml-2.5 flex-1">
                      <div class="text-white text-sm font-medium">空闲模式</div>
                      <div class="text-gray-400 text-xs mt-0.5">关闭制冷、制热或除湿</div>
                    </div>
                  </div>
                </div>

                <!-- 制冷模式 -->
                <div @click="selectedAirConditionerMode = 1" :class="[
                  'group cursor-pointer rounded-md p-2.5 transition-all duration-200 border',
                  selectedAirConditionerMode === 1
                    ? 'bg-gradient-to-r from-blue-500/20 to-cyan-500/20 border-blue-500 shadow-md shadow-blue-500/20'
                    : 'bg-gray-800/50 border-gray-700 hover:border-gray-600 hover:bg-gray-800'
                ]">
                  <div class="flex items-start">
                    <div :class="[
                      'flex-shrink-0 w-4 h-4 rounded-full border-2 mt-0.5 transition-all duration-200',
                      selectedAirConditionerMode === 1
                        ? 'border-blue-500 bg-blue-500'
                        : 'border-gray-600 group-hover:border-gray-500'
                    ]">
                      <svg v-if="selectedAirConditionerMode === 1" class="w-full h-full text-white" fill="currentColor"
                        viewBox="0 0 20 20">
                        <path fill-rule="evenodd"
                          d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                          clip-rule="evenodd" />
                      </svg>
                    </div>
                    <div class="ml-2.5 flex-1">
                      <div class="text-white text-sm font-medium">制冷模式</div>
                      <div class="text-gray-400 text-xs mt-0.5">启动空调制冷功能</div>
                    </div>
                  </div>
                </div>

                <!-- 制热模式 -->
                <div @click="selectedAirConditionerMode = 2" :class="[
                  'group cursor-pointer rounded-md p-2.5 transition-all duration-200 border',
                  selectedAirConditionerMode === 2
                    ? 'bg-gradient-to-r from-orange-500/20 to-red-500/20 border-orange-500 shadow-md shadow-orange-500/20'
                    : 'bg-gray-800/50 border-gray-700 hover:border-gray-600 hover:bg-gray-800'
                ]">
                  <div class="flex items-start">
                    <div :class="[
                      'flex-shrink-0 w-4 h-4 rounded-full border-2 mt-0.5 transition-all duration-200',
                      selectedAirConditionerMode === 2
                        ? 'border-orange-500 bg-orange-500'
                        : 'border-gray-600 group-hover:border-gray-500'
                    ]">
                      <svg v-if="selectedAirConditionerMode === 2" class="w-full h-full text-white" fill="currentColor"
                        viewBox="0 0 20 20">
                        <path fill-rule="evenodd"
                          d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                          clip-rule="evenodd" />
                      </svg>
                    </div>
                    <div class="ml-2.5 flex-1">
                      <div class="text-white text-sm font-medium">制热模式</div>
                      <div class="text-gray-400 text-xs mt-0.5">启动空调制热功能</div>
                    </div>
                  </div>
                </div>

                <!-- 除湿模式 -->
                <div @click="selectedAirConditionerMode = 3" :class="[
                  'group cursor-pointer rounded-md p-2.5 transition-all duration-200 border',
                  selectedAirConditionerMode === 3
                    ? 'bg-gradient-to-r from-purple-500/20 to-pink-500/20 border-purple-500 shadow-md shadow-purple-500/20'
                    : 'bg-gray-800/50 border-gray-700 hover:border-gray-600 hover:bg-gray-800'
                ]">
                  <div class="flex items-start">
                    <div :class="[
                      'flex-shrink-0 w-4 h-4 rounded-full border-2 mt-0.5 transition-all duration-200',
                      selectedAirConditionerMode === 3
                        ? 'border-purple-500 bg-purple-500'
                        : 'border-gray-600 group-hover:border-gray-500'
                    ]">
                      <svg v-if="selectedAirConditionerMode === 3" class="w-full h-full text-white" fill="currentColor"
                        viewBox="0 0 20 20">
                        <path fill-rule="evenodd"
                          d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                          clip-rule="evenodd" />
                      </svg>
                    </div>
                    <div class="ml-2.5 flex-1">
                      <div class="text-white text-sm font-medium">除湿模式</div>
                      <div class="text-gray-400 text-xs mt-0.5">自动处理除湿，包含制冷除湿及制热除湿</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 底部按钮 -->
            <div class="flex items-center justify-end gap-2 px-4 py-2.5 border-t border-gray-700 bg-gray-900/50">
              <button @click="showAirConditionerModal = false"
                class="px-3 py-1.5 text-sm rounded-md bg-gray-600 text-gray-300 hover:text-white hover:bg-gray-700 transition-all duration-200 font-medium">
                取消
              </button>
              <button @click="handleAirConditionerModeChange" :disabled="airConditionerLoading"
                class="px-4 py-1.5 text-sm rounded-md bg-gradient-to-r from-emerald-500 to-teal-500 text-white font-medium hover:from-emerald-600 hover:to-teal-600 transition-all duration-200 shadow-md shadow-emerald-500/30 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1.5">
                <svg v-if="airConditionerLoading" class="animate-spin h-3.5 w-3.5" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor"
                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z">
                  </path>
                </svg>
                <span>{{ airConditionerLoading ? '设置中...' : '确定' }}</span>
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>

</template>

<script setup lang="ts">
import { defineProps, defineEmits, ref, watch, computed, createVNode } from 'vue'
import {
  CloseOutlined,
  ExclamationCircleOutlined
} from '@ant-design/icons-vue'
import { useDockControl } from './use-dock-control'
import { DeviceInfoType, EDockModeCode } from '@/types/device'
import { cmdList as baseCmdList, DeviceCmdItem, DeviceCmd } from '@/types/device-cmd'
import { useMyStore } from '@/store'
import { updateDeviceCmdInfoByOsd, updateDeviceCmdInfoByExecuteInfo } from '@/utils/device-cmd'
import DeviceSettingBox from './DeviceSettingBox.vue'
import { message, Modal } from 'ant-design-vue'

const props = defineProps<{
  sn: string,
  deviceInfo: DeviceInfoType,
}>()

const store = useMyStore()
const initCmdList = baseCmdList.map(cmdItem => Object.assign({}, cmdItem))
const cmdList = ref(initCmdList)

// 空调模式相关状态
const showAirConditionerModal = ref(false)
const airConditionerLoading = ref(false)
const selectedAirConditionerMode = ref(0)

// 空调模式枚举
enum AirConditionerModeEnum {
  IDLE = 0,
  COLD = 1,
  HOT = 2,
  DEHUMIDIFICATION = 3,
  COLD_EXIT = 4,
  HOT_EXIT = 5,
  DEHUMIDIFICATION_EXIT = 6,
  COLD_READY = 7,
  HOT_READY = 8,
  DEHUMIDIFICATION_READY = 9,
  WINDY_READY = 10,
  ON_WINDY = 11,
  WINDY_EXIT = 12,
  DEFOG_READY = 13,
  DEFOGGING = 14,
  DEFOG_EXIT = 15,
}

// 空调模式文本映射
const airConditionerModeMap = {
  [AirConditionerModeEnum.IDLE]: '空闲模式',
  [AirConditionerModeEnum.COLD]: '制冷模式',
  [AirConditionerModeEnum.HOT]: '制热模式',
  [AirConditionerModeEnum.DEHUMIDIFICATION]: '除湿模式',
  [AirConditionerModeEnum.COLD_EXIT]: '制冷退出',
  [AirConditionerModeEnum.HOT_EXIT]: '制热退出',
  [AirConditionerModeEnum.DEHUMIDIFICATION_EXIT]: '除湿退出',
  [AirConditionerModeEnum.COLD_READY]: '制冷准备模式',
  [AirConditionerModeEnum.HOT_READY]: '制热准备模式',
  [AirConditionerModeEnum.DEHUMIDIFICATION_READY]: '除湿准备模式',
  [AirConditionerModeEnum.WINDY_READY]: '风冷准备中',
  [AirConditionerModeEnum.ON_WINDY]: '风冷中',
  [AirConditionerModeEnum.WINDY_EXIT]: '风冷退出中',
  [AirConditionerModeEnum.DEFOG_READY]: '除雾准备中',
  [AirConditionerModeEnum.DEFOGGING]: '除雾中',
  [AirConditionerModeEnum.DEFOG_EXIT]: '除雾退出中',



}

// 当前空调模式文本
const airConditionerModeText = computed(() => {
  const mode = props.deviceInfo?.dock?.basic_osd.air_conditioner?.air_conditioner_state
  if (mode !== undefined && mode !== null) {
    return airConditionerModeMap[mode] || '未知'
  }
  return '--'
})

// 监听设备信息变化，更新选中的空调模式
watch(() => props.deviceInfo?.dock?.basic_osd.air_conditioner?.air_conditioner_state, (mode) => {
  if (mode !== undefined && mode !== null) {
    selectedAirConditionerMode.value = mode
  }
}, { immediate: true })

// 根据机场指令执行状态更新信息
watch(() => store.state.devicesCmdExecuteInfo, (devicesCmdExecuteInfo) => {
  if (props.sn && devicesCmdExecuteInfo[props.sn]) {
    updateDeviceCmdInfoByExecuteInfo(cmdList.value, devicesCmdExecuteInfo[props.sn])
  }
}, {
  immediate: true,
  deep: true,
})

// 根据设备osd信息更新信息
watch(() => props.deviceInfo, (value) => {
  updateDeviceCmdInfoByOsd(cmdList.value, value)
  // console.log('deviceInfo', value)
}, {
  immediate: true,
  deep: true
})

// dock 控制指令
const debugStatus = ref(props.deviceInfo.dock?.basic_osd?.mode_code === EDockModeCode.Remote_Debugging)
const emit = defineEmits(['close-control-panel'])

function closeControlPanel() {
  emit('close-control-panel', props.sn, debugStatus.value)
}

async function onDeviceStatusChange(status: boolean) {
  let result = false
  if (status) {
    result = await dockDebugOnOff(props.sn, true)
  } else {
    result = await dockDebugOnOff(props.sn, false)
  }
  if (!result) {
    if (status) {
      debugStatus.value = false
    } else {
      debugStatus.value = true
    }
  }
}

// 处理空调模式变更
async function handleAirConditionerModeChange() {
  airConditionerLoading.value = true
  try {
    const success = await sendDockControlCmd({
      sn: props.sn,
      cmd: DeviceCmd.AirConditionerModeSwitch,
      action: selectedAirConditionerMode.value as any
    }, false)

    if (success) {
      message.success('空调模式设置成功')
      showAirConditionerModal.value = false
    } else {
      message.error('空调模式设置失败')
    }
  } catch (error) {
    message.error('空调模式设置失败')
  } finally {
    airConditionerLoading.value = false
  }
}

const {
  sendDockControlCmd,
  dockDebugOnOff
} = useDockControl()

async function sendControlCmd(cmdItem: DeviceCmdItem, index: number) {
  if (cmdItem.cmdKey === DeviceCmd.CoverForceClose) {
    Modal.confirm({
      title: createVNode('span', { style: 'color: #ffffff; font-weight: 600; font-size: 16px;' }, '强制关舱盖安全警告'),
      icon: createVNode(ExclamationCircleOutlined, { style: 'color: #ff4d4f; font-size: 20px;' }),
      content: createVNode('div', { style: 'color: rgba(255, 255, 255, 0.85); font-size: 14px; line-height: 1.6; margin-top: 10px;' }, '机场“设备属性”推送的 drone_in_dock 字段值为 0 时，且通过机场摄像头观看确认飞行器不在舱内后，才可调用该指令强制关闭舱盖。否则可能导致桨叶被夹！是否确认强制关闭舱盖？'),
      okText: '确认强制关闭',
      okType: 'danger',
      cancelText: '取消',
      wrapClassName: 'dark-confirm-modal',
      async onOk() {
        await sendDockControlCmd({
          sn: props.sn,
          cmd: cmdItem.cmdKey,
          action: cmdItem.action
        }, true)
      }
    })
    return
  }

  const success = await sendDockControlCmd({
    sn: props.sn,
    cmd: cmdItem.cmdKey,
    action: cmdItem.action
  }, true)
  if (success) {
    // updateDeviceSingleCmdInfo(cmdList.value[index])
  }
}

</script>

<style lang='scss' scoped>
.dock-control-panel {
  position: absolute;
  right: 536px;
  top: 80px;
  width: 460px;
  padding: 0 !important;

  // Pro-Max Theme
  background: rgba(4, 22, 58, 0.85);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.3);
  color: #fff;
  border-radius: 12px;
  transition: opacity 0.3s ease;
  z-index: 60;
  overflow: hidden;
  font-family: system-ui, -apple-system, sans-serif;

  .dock-control-panel-header {
    height: 44px;
    padding: 0 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: rgba(0, 0, 0, 0.2);
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    font-size: 14px;
    font-weight: 600;
    cursor: move;
  }

  .control-cmd-wrapper {
    .control-cmd-header {
      font-size: 14px;
      font-weight: 600;
      padding: 12px 16px 8px;
      color: rgba(255, 255, 255, 0.9);
      display: flex;
      align-items: center;

      .debug-btn {
        margin-left: 10px;
      }
    }

    .control-cmd-box {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      padding: 0 12px 12px;
      gap: 8px;

      .control-cmd-item {
        width: 48%;
        height: auto;
        min-height: 56px;
        display: flex;
        align-items: center;
        justify-content: space-between;

        // Item Style
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid rgba(255, 255, 255, 0.1);
        border-radius: 8px;
        margin: 0;
        padding: 8px 12px;
        transition: all 0.2s;

        &:hover {
          background: rgba(255, 255, 255, 0.1);
          border-color: rgba(255, 255, 255, 0.2);
        }

        .control-cmd-item-left {
          display: flex;
          flex-direction: column;

          .item-label {
            font-size: 13px;
            font-weight: 500;
            margin-bottom: 2px;
          }

          .item-status {
            font-size: 12px;
            color: rgba(255, 255, 255, 0.6);
          }
        }
      }

      .air-conditioner-item {
        width: 100%;
      }
    }
  }

  // 远程调试关闭/禁用状态按钮高对比度文字处理
  :deep(.ant-btn[disabled]),
  :deep(.ant-btn-primary[disabled]),
  :deep(.ant-btn:disabled) {
    color: rgba(255, 255, 255, 0.7) !important;
    background: rgba(255, 255, 255, 0.08) !important;
    border-color: rgba(255, 255, 255, 0.2) !important;
    cursor: not-allowed;
  }
}
</style>

<style lang="scss">
// 弹窗过渡动画
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.3s ease;

  >div:last-child {
    transition: transform 0.3s ease, opacity 0.3s ease;
  }
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;

  >div:last-child {
    transform: scale(0.95);
    opacity: 0;
  }
}

.modal-fade-enter-to,
.modal-fade-leave-from {
  opacity: 1;

  >div:last-child {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
```
