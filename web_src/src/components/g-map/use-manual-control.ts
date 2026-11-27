import {
  ref,
  onUnmounted,
  watch,
  Ref,
} from 'vue'
import { message } from 'ant-design-vue'
import {
  DRC_METHOD,
  DroneControlProtocol,
  StickControlProtocol,
} from '@/types/drc'
import {
  useMqtt,
  DeviceTopicInfo
} from './use-mqtt'

let myInterval: any

export enum KeyCode {
  KEY_W = 'KeyW',
  KEY_A = 'KeyA',
  KEY_S = 'KeyS',
  KEY_D = 'KeyD',
  KEY_Q = 'KeyQ',
  KEY_E = 'KeyE',
  Key_C = 'KeyC',
  Key_Z = 'KeyZ',
}

// 速度配置 (0-100%)
export interface ControlSpeeds {
  horizontal: number
  vertical: number
  yaw: number
}

const DEFAULT_SPEEDS: ControlSpeeds = {
  horizontal: 60, // 60%
  vertical: 60,   // 60%
  yaw: 50         // 50%
}

const MIDDLE = 1024
const MAX_CHANNEL_DEVIATION = 660

export function useManualControl(
  deviceTopicInfo: DeviceTopicInfo,
  isCurrentFlightController: Ref<boolean>,
  controlSpeeds: Ref<ControlSpeeds> = ref(DEFAULT_SPEEDS)
) {
  // 改为 Set，支持多键
  const pressedKeys = ref(new Set<KeyCode>())
  const mqttHooks = useMqtt(deviceTopicInfo)
  let seq = 0

  // 计算偏移量
  function calculateDelta(percentage: number) {
    // 限制在 0-100 之间
    const validPercentage = Math.max(0, Math.min(100, percentage))
    return Math.floor((validPercentage / 100) * MAX_CHANNEL_DEVIATION)
  }

  // 合成当前控制量
  function computeControl(): StickControlProtocol {
    const control = {
      roll: MIDDLE,
      pitch: MIDDLE,
      yaw: MIDDLE,
      throttle: MIDDLE,
    }

    pressedKeys.value.forEach((key) => {
      // 动态获取当前速度配置 (百分比) 并转换为實際偏移量
      const horizontalDelta = calculateDelta(controlSpeeds.value.horizontal)
      const verticalDelta = calculateDelta(controlSpeeds.value.vertical)
      const yawDelta = calculateDelta(controlSpeeds.value.yaw)

      switch (key) {
        case KeyCode.KEY_A: // 左
          control.roll -= horizontalDelta
          break
        case KeyCode.KEY_D: // 右
          control.roll += horizontalDelta
          break
        case KeyCode.KEY_W: // 前
          control.pitch += horizontalDelta
          break
        case KeyCode.KEY_S: // 后
          control.pitch -= horizontalDelta
          break
        case KeyCode.KEY_Q: // 左转
          control.yaw -= yawDelta
          break
        case KeyCode.KEY_E: // 右转
          control.yaw += yawDelta
          break
        case KeyCode.Key_Z: // 下降
          control.throttle -= verticalDelta
          break
        case KeyCode.Key_C: // 上升
          control.throttle += verticalDelta
          break
      }
    })

    return control
  }

  // 启动定时发送
  function startPublishLoop() {
    handleClearInterval()
    myInterval = setInterval(() => {
      if (pressedKeys.value.size === 0 || !deviceTopicInfo.pubTopic) return

      const params = computeControl()
      const body = {
        method: DRC_METHOD.STICK_CONTROL,
        data: { ...params, seq: seq++ },
      }
      mqttHooks?.publishMqtt(deviceTopicInfo.pubTopic, body, { qos: 2 })
    }, 100)
  }

  // 按下按键
  function handleKeydown(keyCode: KeyCode) {
    if (!deviceTopicInfo.pubTopic) {
      message.error('请确保已经建立DRC链路')
      return
    }

    if (!pressedKeys.value.has(keyCode)) {
      pressedKeys.value.add(keyCode)
      startPublishLoop()
    }
  }

  // 松开按键
  function handleKeyup(keyCode: KeyCode) {
    pressedKeys.value.delete(keyCode)
    if (pressedKeys.value.size === 0) {
      handleClearInterval()
    } else {
      // 仍有按键按下，重新计算并继续发送
      startPublishLoop()
    }
  }

  function handleClearInterval() {
    if (myInterval) {
      clearInterval(myInterval)
      myInterval = undefined
    }
  }

  function resetControlState() {
    pressedKeys.value.clear()
    seq = 0
    handleClearInterval()
  }

  // 全局键盘事件处理
  function onKeydown(e: KeyboardEvent) {
    const code = e.code as KeyCode
    if (Object.values(KeyCode).includes(code)) {
      e.preventDefault()
      handleKeydown(code)
    }
  }

  function onKeyup(e: KeyboardEvent) {
    const code = e.code as KeyCode
    if (Object.values(KeyCode).includes(code)) {
      e.preventDefault()
      handleKeyup(code)
    }
  }

  // 处理窗口失去焦点的情况 - 当窗口失去焦点时，停止所有按键控制
  function onWindowBlur() {
    if (pressedKeys.value.size > 0) {
      console.log('Window lost focus, stopping all key controls')
      resetControlState()
    }
  }

  // 处理页面可见性变化 - 当页面变为不可见时，停止所有按键控制
  function onVisibilityChange() {
    if (document.hidden && pressedKeys.value.size > 0) {
      console.log('Page hidden, stopping all key controls')
      resetControlState()
    }
  }

  function startKeyboardManualControl() {
    window.addEventListener('keydown', onKeydown)
    window.addEventListener('keyup', onKeyup)
    // 添加窗口失去焦点和页面可见性变化的监听，以修复焦点丢失时按键指令持续执行的问题
    window.addEventListener('blur', onWindowBlur)
    document.addEventListener('visibilitychange', onVisibilityChange)
  }

  function closeKeyboardManualControl() {
    resetControlState()
    window.removeEventListener('keydown', onKeydown)
    window.removeEventListener('keyup', onKeyup)
    window.removeEventListener('blur', onWindowBlur)
    document.removeEventListener('visibilitychange', onVisibilityChange)
  }

  watch(
    () => isCurrentFlightController.value,
    (val) => {
      if (val && deviceTopicInfo.pubTopic) {
        startKeyboardManualControl()
      } else {
        closeKeyboardManualControl()
      }
    },
    { immediate: true }
  )

  onUnmounted(() => {
    closeKeyboardManualControl()
  })

  function handleEmergencyStop() {
    if (!deviceTopicInfo.pubTopic) {
      message.error('请确保已经建立DRC链路')
      return
    }
    const body = {
      method: DRC_METHOD.DRONE_EMERGENCY_STOP,
      data: {},
    }
    resetControlState()
    mqttHooks?.publishMqtt(deviceTopicInfo.pubTopic, body, { qos: 2 })
  }

  return {
    pressedKeys, // 可用于 UI 显示当前按键状态
    handleEmergencyStop,
    resetControlState,
    // 如果外部需要手动触发（一般不需要）
    handleKeydown: (key: KeyCode) => handleKeydown(key),
    handleKeyup: (key: KeyCode) => handleKeyup(key),
    closeKeyboardManualControl,
    startKeyboardManualControl
  }
}
