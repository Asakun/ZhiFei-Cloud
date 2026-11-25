<script setup lang="ts">
import DjiCesium from '@/components/DjiCesium.vue';
import noData from '@/assets/icons/no-data.png'
import rc from '@/assets/icons/rc.png'
import webRtcPlay from '@/components/webRtcPlay.vue';
import { message } from 'ant-design-vue';

import { getDeviceTopo, getUnreadDeviceHms, updateDeviceHms, getPayloadList } from '@/api/manage'
import { EDeviceTypeName, ELocalStorageKey } from '@/types'
import { OnlineDevice, EModeCode, EDockModeCode, EDockModeCodeZh, EModeWord, PAYLOAD_TYPE } from '@/types/device'
import { DeviceCmd } from '@/types/device-cmd'
import { postSendCmd } from '@/api/device-cmd'
import { getAuthoriyInfo } from '@/api/drone-control/authority'
import { postDrc } from '@/api/drc'
import { useMyStore } from '@/store'
import { RocketOutlined, EyeInvisibleOutlined, EyeOutlined, RobotOutlined, DoubleRightOutlined, PoweroffOutlined, AimOutlined, DashboardOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { EHmsLevel } from '@/types/enums'


import { ref, reactive, watch, computed, onMounted, onUnmounted, provide, watchEffect } from 'vue'
import { useEventEmitter, useEventListener, useLocalStorageState, useTimeout } from "vue-hooks-plus";
import { useDroneControl } from '@/components/g-map/use-drone-control';
import { ECommanderFlightMode, ECommanderModeLostAction, ERthMode, LostControlActionInCommandFLight, WaylineLostControlActionInCommandFlight } from '@/api/drone-control/drone';
import { useRouter } from 'vue-router';
import { useConnectMqtt1 } from '@/components/g-map/use-connect-mqtt';
import EventBus from '@/event-bus';


const router = useRouter()
const workspaceId = ref(localStorage.getItem(ELocalStorageKey.WorkspaceId)!)
const store = useMyStore()

const psdkWidgets = ref<Record<string, any[]>>({})
const fetchPsdkWidgets = () => {
  if (!workspaceId.value) return
  getPayloadList(workspaceId.value).then(res => {
    if (res) {
      psdkWidgets.value = res
    }
  }).catch(err => {
    console.error('获取无人机负载失败:', err)
  })
}

// 建立 MQTT 连接，获取 clientId
useConnectMqtt1()
const hmsInfo = computed({
  get: () => store.state.hmsInfo,
  set: (val) => {
    return val
  }
})
const onlineDevices = reactive({
  data: [] as OnlineDevice[]
})

const onlineDocks = reactive({
  data: [] as OnlineDevice[]
})

const activeTab = ref('docks')

// 机场在线/离线统计
const onlineDocksCount = computed(() => {
  return onlineDocks.data.filter(dock => {
    const sn = dock.gateway.sn
    const info = dockInfo.value?.[sn]
    return info && info.basic_osd?.mode_code !== EDockModeCode.Disconnected
  }).length
})

const offlineDocksList = computed(() => {
  return onlineDocks.data.filter(dock => {
    const sn = dock.gateway.sn
    const info = dockInfo.value?.[sn]
    return !info || info.basic_osd?.mode_code === EDockModeCode.Disconnected
  })
})

const offlineDocksCount = computed(() => offlineDocksList.value.length)

// 搜索过滤
const searchQuery = ref('')

const filteredDocks = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return onlineDocks.data
  }
  return onlineDocks.data.filter(dock => {
    const dockName = (dock.gateway.callsign || '').toLowerCase()
    const droneName = (dock.callsign || '').toLowerCase()
    const dockSn = (dock.gateway.sn || '').toLowerCase()
    const droneSn = (dock.sn || '').toLowerCase()
    return dockName.includes(query) || droneName.includes(query) || dockSn.includes(query) || droneSn.includes(query)
  })
})

const filteredDevices = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return onlineDevices.data
  }
  return onlineDevices.data.filter(device => {
    const deviceName = (device.callsign || '').toLowerCase()
    const modelName = (device.model || '').toLowerCase()
    const deviceSn = (device.sn || '').toLowerCase()
    const gwSn = (device.gateway.sn || '').toLowerCase()
    return deviceName.includes(query) || modelName.includes(query) || deviceSn.includes(query) || gwSn.includes(query)
  })
})

watch(searchQuery, () => {
  dockListPage.value = 1
  deviceListPage.value = 1
})

// 机场列表分页
const dockListPage = ref(1)
const dockListPageSize = ref(5)
const dockListTotal = computed(() => filteredDocks.value.length)
const paginatedDocks = computed(() => {
  const start = (dockListPage.value - 1) * dockListPageSize.value
  const end = start + dockListPageSize.value
  return filteredDocks.value.slice(start, end)
})
const onDockPageChange = (page: number, pageSize: number) => {
  dockListPage.value = page
  dockListPageSize.value = pageSize
}

// 无人机列表 (在线设备) 分页
const deviceListPage = ref(1)
const deviceListPageSize = ref(5)
const deviceListTotal = computed(() => filteredDevices.value.length)
const paginatedDevices = computed(() => {
  const start = (deviceListPage.value - 1) * deviceListPageSize.value
  const end = start + deviceListPageSize.value
  return filteredDevices.value.slice(start, end)
})
const onDevicePageChange = (page: number, pageSize: number) => {
  deviceListPage.value = page
  deviceListPageSize.value = pageSize
}

const hmsVisible = reactive<Record<string, boolean>>({})
const deviceInfo = computed(() => store?.state?.deviceState?.deviceInfo)
const dockInfo = computed(() => store?.state?.deviceState?.dockInfo)
const osdVisible = computed(() => store?.state?.osdVisible)

// 动态通知全局 OSD 节流器当前 UI 界面可显示的 SN 列表 (实现按需高敏捷刷新)
watch(
  [paginatedDocks, paginatedDevices, activeTab, () => osdVisible.value?.sn],
  () => {
    const visibleSns: string[] = []

    // 1. 当前选中/操作弹窗关联的设备 SN
    if (osdVisible.value?.sn) visibleSns.push(osdVisible.value.sn)
    if (osdVisible.value?.gateway_sn) visibleSns.push(osdVisible.value.gateway_sn)

    // 2. 当前 UI 分页列表中可视展示的卡片设备 SN
    if (activeTab.value === 'docks') {
      paginatedDocks.value.forEach((dock: any) => {
        if (dock.sn) visibleSns.push(dock.sn)
        if (dock.gateway?.sn) visibleSns.push(dock.gateway.sn)
      })
    } else {
      paginatedDevices.value.forEach((device: any) => {
        if (device.sn) visibleSns.push(device.sn)
        if (device.gateway?.sn) visibleSns.push(device.gateway.sn)
      })
    }

    EventBus.emit('updateVisibleSns', visibleSns)
  },
  { immediate: true }
)

const osdInfoData = computed(() => {
  const sn = osdVisible.value.sn
  if (sn && deviceInfo.value[sn] && (deviceInfo.value[sn] as any).elevation > 10) {
    return deviceInfo.value[sn]
  }
  return null
})

onMounted(() => {
  getOnlineTopo()
  fetchPsdkWidgets()
  setTimeout(() => {
    watch(() => store.state.deviceStatusEvent,
      data => {
        getOnlineTopo()
        fetchPsdkWidgets()
        if (data.deviceOnline.sn) {
          getUnreadHms(data.deviceOnline.sn)
        }
      },
      {
        deep: true
      }
    )
    getOnlineDeviceHms()
  }, 200)
})

const getUnreadHms = (sn: string) => {
  getUnreadDeviceHms(workspaceId.value, sn).then(res => {
    if (res.data.length !== 0) {
      hmsInfo.value[sn] = res.data
    }
  })
}
const getOnlineTopo = () => {
  getDeviceTopo(workspaceId.value).then((res) => {
    if (res.code !== 0) {
      return
    }
    onlineDevices.data = []
    onlineDocks.data = []
    res.data.forEach((gateway: any) => {
      const child = gateway.children
      const device: OnlineDevice = {
        model: child?.device_name,
        callsign: child?.nickname,
        sn: child?.device_sn,
        mode: EModeCode.Disconnected,
        gateway: {
          model: gateway?.device_name,
          callsign: gateway?.nickname,
          sn: gateway?.device_sn,
          domain: gateway?.domain
        },
        payload: []
      }
      child?.payloads_list.forEach((payload: any) => {
        device.payload.push({
          index: payload.index,
          model: payload.model,
          payload_name: payload.payload_name,
          payload_sn: payload.payload_sn,
          control_source: payload.control_source,
          payload_index: payload.payload_index
        })

      })
      if (EDeviceTypeName.Dock === gateway.domain) {
        hmsVisible[device.sn] = false
        hmsVisible[device.gateway.sn] = false
        onlineDocks.data.push(reactive(device))
      }
      if (gateway.status && EDeviceTypeName.Gateway === gateway.domain) {
        onlineDevices.data.push(reactive(device))
      }
    })
  })
}

const getOnlineDeviceHms = () => {
  const snList = Object.keys(dockInfo.value)
  if (snList.length === 0) {
    return
  }
  snList.forEach(sn => {
    getUnreadHms(sn)
  })
  const deviceSnList = Object.keys(deviceInfo.value)
  if (deviceSnList.length === 0) {
    return
  }
  deviceSnList.forEach(sn => {
    getUnreadHms(sn)
  })
}

const readHms = (visible: boolean, sn: string) => {
  if (!visible) {
    updateDeviceHms(workspaceId.value, sn).then(res => {
      if (res.code === 0) {
        delete hmsInfo.value[sn]
      }
    })
  }
}

const dockMapLocation = reactive({
  latitude: 29.699058,
  longitude: 106.488738,
  height: 100000
})
const [localDockMapLocation, setState] = useLocalStorageState('dockMapLocation', {
  defaultValue: dockMapLocation
})

const globalEvent = useEventEmitter({ global: true })

function switchVisible(e: any, device: OnlineDevice, isDock: boolean, isClick: boolean) {
  if (!isClick) {
    e.target.style.cursor = 'not-allowed'
    return
  }
  if (device.sn === osdVisible.value.sn) {
    osdVisible.value.visible = !osdVisible.value.visible
  } else {
    osdVisible.value.sn = device.sn
    osdVisible.value.callsign = device.callsign
    osdVisible.value.model = device.model
    osdVisible.value.visible = true
    osdVisible.value.gateway_sn = device.gateway.sn
    osdVisible.value.is_dock = isDock
    osdVisible.value.gateway_callsign = device.gateway.callsign
    osdVisible.value.payloads = device.payload
  }
  if (isDock) {
    const sn = device.gateway.sn
    if (dockInfo.value[sn]?.basic_osd) {
      dockMapLocation.latitude = dockInfo.value[sn].basic_osd.latitude
      dockMapLocation.longitude = dockInfo.value[sn].basic_osd.longitude
      dockMapLocation.height = dockInfo.value[sn].basic_osd.height
      setState(dockMapLocation)
      globalEvent.emit('globalEvent1', dockMapLocation)
    }
  } else {
    const sn = device.sn
    if (deviceInfo.value[sn]) {
      dockMapLocation.latitude = deviceInfo.value[sn].latitude
      dockMapLocation.longitude = deviceInfo.value[sn].longitude
      dockMapLocation.height = Number(deviceInfo.value[sn].height) || 100000
      setState(dockMapLocation)
      globalEvent.emit('globalEvent1', dockMapLocation)
    }
  }

  store.commit('SET_OSD_VISIBLE_INFO', osdVisible.value)
}




onUnmounted(() => {
  if (osdVisible.value && osdVisible.value.visible !== undefined) {
    osdVisible.value.visible = false
    store.commit('SET_OSD_VISIBLE_INFO', osdVisible)
  }
})

const dockMap = computed(() => {
  return onlineDocks.data.reduce((acc: any[], dock) => {
    const sn = dock.gateway.sn
    const basicOsd = dockInfo.value?.[sn]?.basic_osd
    const latitude = basicOsd?.latitude ?? dock.latitude ?? dock.gateway?.latitude
    const longitude = basicOsd?.longitude ?? dock.longitude ?? dock.gateway?.longitude
    const height = basicOsd?.height ?? dock.height ?? dock.gateway?.height ?? 0
    if (latitude != null && longitude != null && (Number(latitude) !== 0 || Number(longitude) !== 0)) {
      acc.push({
        callsign: dock.gateway.callsign,
        sn: dock.gateway.sn,
        domain: dock.gateway.domain,
        model: dock.model,
        latitude: latitude,
        longitude: longitude,
        height: height
      })
    }
    return acc
  }, [])
})

/** 实时 OSD 无人机数据（用于地图多机展示，包括机场关联无人机和独立遥控器无人机） */
const droneMap = computed(() => {
  const list: any[] = []
  
  // 1. 机场关联的无人机
  onlineDocks.data.forEach(dock => {
    const droneSn = dock.sn
    const osd = deviceInfo.value?.[droneSn] as any
    if (osd?.longitude != null && osd?.latitude != null) {
      list.push({
        sn: droneSn,
        callsign: dock.callsign || droneSn,
        longitude: osd.longitude,
        latitude: osd.latitude,
        height: osd.height ?? 0,
        attitude_head: osd.attitude_head ?? 0
      })
    }
  })

  // 2. 独立遥控器接入的无人机
  onlineDevices.data.forEach(device => {
    const droneSn = device.sn
    const osd = deviceInfo.value?.[droneSn] as any
    if (osd?.longitude != null && osd?.latitude != null) {
      if (!list.some(d => d.sn === droneSn)) {
        list.push({
          sn: droneSn,
          callsign: device.callsign || droneSn,
          longitude: osd.longitude,
          latitude: osd.latitude,
          height: osd.height ?? 0,
          attitude_head: osd.attitude_head ?? 0
        })
      }
    }
  })

  return list
})

// 初始化本地存储状态
const [, setDockMapCache] = useLocalStorageState('dockMap')

// 初始延迟设置缓存（等待数据加载）
useTimeout(() => {
  if (dockMap.value.length > 0) {
    setDockMapCache(dockMap.value)
  }
}, 3000)

// 监听 dockMap 变化并更新缓存（移除昂贵的 deep 防止遥测移动产生不间断的高频同步阻塞 IO 回写 localStorage）
watch(() => dockMap.value.map(d => d.sn).join(','), () => {
  if (dockMap.value && dockMap.value.length > 0) {
    setDockMapCache(dockMap.value)
  }
})

interface FlyToPointPosition {
  alt_height: number;
  longitude: number,
  latitude: number,
  height: number
}
const flyToPointPosition = reactive({
  longitude: 0,
  latitude: 0,
  height: 0,
})
const { takeoffToPoint, flyToPoint } = useDroneControl()
const flyToPointMethod = (position: FlyToPointPosition, selectedDock: any) => {

  if (dockInfo.value[selectedDock.sn].basic_osd.sub_device?.device_online_status && !dockInfo.value[selectedDock.sn].basic_osd.drone_in_dock) {
    console.log('无人机未在机巢')
    flyToPoint(selectedDock.sn, {
      max_speed: 15,
      points: [{
        latitude: Number(position.latitude),
        longitude: Number(position.longitude),
        height: Number(position.alt_height) + Number(dockInfo.value[selectedDock.sn].basic_osd.height)
      }]
    })
  } else {
    console.log('flyToPointMethod被调用了', position.latitude, position.longitude, position.height, position.alt_height, selectedDock.sn)
    takeoffToPoint(selectedDock.sn, {
      target_latitude: position.latitude,
      target_longitude: position.longitude,
      target_height: Number(position.alt_height) + Number(dockInfo.value[selectedDock.sn].basic_osd.height),
      security_takeoff_height: position.alt_height,
      max_speed: 15,
      rc_lost_action: LostControlActionInCommandFLight.RETURN_HOME,
      rth_altitude: position.alt_height,
      exit_wayline_when_rc_lost: WaylineLostControlActionInCommandFlight.EXEC_LOST_ACTION,
      rth_mode: ERthMode.SETTING,
      commander_mode_lost_action: ECommanderModeLostAction.CONTINUE,
      commander_flight_mode: ECommanderFlightMode.SETTING,
      commander_flight_height: position.alt_height
    })
  }

}

// 根据电量百分比返回颜色样式
const getBatteryColor = (percent: number | undefined) => {
  if (percent === undefined || percent === null) {
    return 'color: #868e96;' // 灰色 - 无 data
  }
  if (percent > 50) {
    return 'color: #00ee8b;' // 绿色 - 电量充足
  } else if (percent > 20) {
    return 'color: #ff9f0a;' // 黄色 - 电量中等
  } else {
    return 'color: #f5222d;' // 红色 - 电量低
  }
}

// 获取电量渐变色
const getBatteryGradient = (percent: number | undefined) => {
  if (percent === undefined || percent === null) return 'linear-gradient(90deg, #4b5563, #374151)'
  if (percent > 50) return 'linear-gradient(90deg, #10b981, #059669)'
  if (percent > 20) return 'linear-gradient(90deg, #f59e0b, #d97706)'
  return 'linear-gradient(90deg, #ef4444, #dc2626)'
}

// 获取电量发光效果
const getBatteryGlow = (percent: number | undefined) => {
  if (percent === undefined || percent === null) return 'none'
  if (percent > 50) return '0 0 8px rgba(16, 185, 129, 0.4)'
  if (percent > 20) return '0 0 8px rgba(245, 158, 11, 0.4)'
  return '0 0 8px rgba(239, 68, 68, 0.6)'
}

// 获取 HMS 等级 Class
const getHmsClass = (level: number) => {
  if (level === EHmsLevel.CAUTION) return 'hms-caution'
  if (level === EHmsLevel.WARN) return 'hms-warn'
  return 'hms-notice'
}

// 获取充电状态文本和样式
const getChargeStateInfo = (state: boolean | undefined) => {
  if (state === true) {
    return { text: '充电中', color: 'color: #00ee8b;' } // 绿色
  }
  return { text: '空闲', color: 'color: #868e96;' } // 灰色
}

// ========== 分屏实况播放功能 ==========
// 分屏布局类型定义
type SplitLayoutType = '1x1' | '2x2' | '3x3' | '1x2' | '2x1' | '1+7'

// 分屏模式开关
const splitScreenMode = ref(false)
// 每个分屏的机场视频显示状态（小窗口打开/关闭）
const airportVideoVisible = reactive<Record<string, boolean>>({})
// 每个分屏的主视频类型（drone: 无人机为主，airport: 机场为主）
const mainVideoType = reactive<Record<string, 'drone' | 'airport'>>({})
// 分屏布局配置
const splitLayout = ref<SplitLayoutType>('2x2')

// 分屏当前页码
const splitPage = ref(1)

// 分屏是否仅展示在飞机场
const splitFilterFlying = ref(false)

// 分屏实际使用的机场数据源（支持过滤模式）
const splitDocks = computed(() => {
  if (!splitFilterFlying.value) return onlineDocks.data
  const flyingGwSns = new Set<string>()
  flyingDronesList.value.forEach(d => { if (d.dockSn) flyingGwSns.add(d.dockSn) })
  return onlineDocks.data.filter(d => flyingGwSns.has(d.gateway.sn))
})

// 计算分屏总页数
const totalSplitPages = computed(() => {
  const config = layoutConfigs[splitLayout.value]
  const total = splitDocks.value.length
  return Math.ceil(total / config.maxPanels) || 1
})

// 计算当前正在飞行中的无人机数量
const flyingDronesCount = computed(() => {
  let count = 0
  
  // 1. 机场关联的无人机
  onlineDocks.data.forEach(dock => {
    const droneSn = dock.sn
    const osd = deviceInfo.value?.[droneSn] as any
    if (osd) {
      // 检查飞行模式 (排除离线14、待机0、准备1、就绪2、升级13)
      const isFlyingMode = [3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 16, 17].includes(                                                              osd.mode_code)
      // 辅助检查高度 (高度大于 2.5 米也可判定为起飞)
      const hasAltitude = Number(osd.height || osd.elevation || 0) > 2.5
      if (isFlyingMode || (osd.mode_code !== 14 && osd.mode_code !== 0 && osd.mode_code !== 1 && osd.mode_code !== 2 && osd.mode_code !== 13 && hasAltitude)) {
        count++
      }
    }
  })

  // 2. 独立遥控器接入的无人机
  onlineDevices.data.forEach(device => {
    const droneSn = device.sn
    // 避免重复计算机场关联的无人机
    if (onlineDocks.data.some(d => d.sn === droneSn)) return
    
    const osd = deviceInfo.value?.[droneSn] as any
    if (osd) {
      const isFlyingMode = [3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 16, 17].includes(osd.mode_code)
      const hasAltitude = Number(osd.height || osd.elevation || 0) > 2.5
      if (isFlyingMode || (osd.mode_code !== 14 && osd.mode_code !== 0 && osd.mode_code !== 1 && osd.mode_code !== 2 && osd.mode_code !== 13 && hasAltitude)) {
        count++
      }
    }
  })

  return count
})

// 获取当前正在飞行中的无人机列表及详细遥测数据
const flyingDronesList = computed(() => {
  const list: any[] = []
  
  const checkAndAdd = (droneSn: string, callsign: string, gatewayCallsign: string, dockSn: string) => {
    const osd = deviceInfo.value?.[droneSn] as any
    if (osd) {
      // 检查飞行模式
      const isFlyingMode = [3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 16, 17].includes(osd.mode_code)
      // 辅助检查高度
      const hasAltitude = Number(osd.height || osd.elevation || 0) > 2.5
      if (isFlyingMode || (osd.mode_code !== 14 && osd.mode_code !== 0 && osd.mode_code !== 1 && osd.mode_code !== 2 && osd.mode_code !== 13 && hasAltitude)) {
        list.push({
          sn: droneSn,
          dockSn: dockSn,
          callsign: callsign || droneSn,
          gatewayCallsign: gatewayCallsign || '独立遥控器',
          height: Number(osd.height || osd.elevation || 0).toFixed(1),
          speed: Number(osd.horizontal_speed || 0).toFixed(1),
          modeText: EModeWord[EModeCode[osd.mode_code] as keyof typeof EModeWord] || '飞行中',
          homeDistance: osd.home_distance != null ? Number(osd.home_distance).toFixed(0) : '0',
          latitude: osd.latitude != null ? Number(osd.latitude).toFixed(6) : '0.000000',
          longitude: osd.longitude != null ? Number(osd.longitude).toFixed(6) : '0.000000'
        })
      }
    }
  }

  // 1. 机场关联的无人机
  onlineDocks.data.forEach(dock => {
    checkAndAdd(dock.sn, dock.callsign, dock.gateway.callsign, dock.gateway.sn)
  })

  // 2. 独立遥控器接入的无人机
  onlineDevices.data.forEach(device => {
    if (onlineDocks.data.some(d => d.sn === device.sn)) return
    checkAndAdd(device.sn, device.callsign, '独立遥控器', '')
  })

  return list
})

// 操作员缓存映射
const operatorsMap = reactive<Record<string, string>>({})

// 刷新在飞无人机操作员信息
const refreshOperators = () => {
  flyingDronesList.value.forEach(async (drone) => {
    const targetSn = drone.dockSn || drone.sn
    if (!targetSn) return
    try {
      const res = await getAuthoriyInfo(targetSn)
      if (res.code === 0 && res.data) {
        const owner = res.data.flight_authority?.authority_owner || ''
        operatorsMap[drone.sn] = owner || '系统自驾 / 无'
      } else {
        operatorsMap[drone.sn] = '系统自驾 / 无'
      }
    } catch (err) {
      console.error('获取操作人员失败:', err)
      operatorsMap[drone.sn] = '未知'
    }
  })
}

// 首次在飞时或在飞列表实际变更时，拉取操作员信息（按设备序列号浅显比对，避免飞行中 OSD 高密递归计算）
watch(
  () => flyingDronesList.value.map(d => d.sn).join(','),
  () => {
    flyingDronesList.value.forEach(async (drone) => {
      const targetSn = drone.dockSn || drone.sn
      if (!targetSn || operatorsMap[drone.sn]) return
      try {
        const res = await getAuthoriyInfo(targetSn)
        if (res.code === 0 && res.data) {
          const owner = res.data.flight_authority?.authority_owner || ''
          operatorsMap[drone.sn] = owner || '系统自驾 / 无'
        } else {
          operatorsMap[drone.sn] = '系统自驾 / 无'
        }
      } catch (err) {
        operatorsMap[drone.sn] = '未知'
      }
    })
  },
  { immediate: true }
)

// 监听布局变化，重置分屏页码
watch(splitLayout, () => {
  splitPage.value = 1
})

// 列表快速播放实况
const playDockLive = (dock: OnlineDevice) => {
  // 1. 开启分屏模式
  splitScreenMode.value = true
  
  // 2. 寻找该机场在所有在线机场中的全局索引
  const globalIndex = onlineDocks.data.findIndex(d => d.sn === dock.sn)
  if (globalIndex === -1) return

  // 3. 计算其在当前布局下应该处于分屏的第几页
  const config = layoutConfigs[splitLayout.value]
  const page = Math.floor(globalIndex / config.maxPanels) + 1
  splitPage.value = page

  // 4. 初始化主视频和显示状态
  airportVideoVisible[dock.gateway.sn] = false
  mainVideoType[dock.gateway.sn] = 'drone'

  // 5. 提示用户已切换
  message.success(`已在分屏中为您播放：${dock.gateway.callsign}`)
}

// 一键展示所有在飞机场的实况
const showFlyingLive = () => {
  // 收集所有有在飞无人机的机场 gateway SN（仅机场关联，排除独立遥控器）
  const flyingDockGwSns = new Set<string>()
  flyingDronesList.value.forEach(drone => {
    if (drone.dockSn) flyingDockGwSns.add(drone.dockSn)
  })

  if (flyingDockGwSns.size === 0) {
    message.info('当前没有机场在执行飞行作业')
    return
  }

  const count = flyingDockGwSns.size

  // 开启在飞过滤模式
  splitFilterFlying.value = true

  // 根据在飞数量自动选择最合适的布局
  if (count === 1) splitLayout.value = '1x1'
  else if (count === 2) splitLayout.value = '1x2'
  else if (count <= 4) splitLayout.value = '2x2'
  else splitLayout.value = '3x3'

  // 开启分屏模式，重置页码
  splitScreenMode.value = true
  splitPage.value = 1

  // 初始化所有在飞机场的视频状态
  flyingDockGwSns.forEach(gwSn => {
    airportVideoVisible[gwSn] = false
    mainVideoType[gwSn] = 'drone'
  })

  message.success(`已展示 ${count} 个在飞机场实况`)
}

// 退出在飞过滤，显示全部机场
const clearFlyingFilter = () => {
  splitFilterFlying.value = false
  splitPage.value = 1
}

// 获取机场视频流ID（机场内部摄像头）
const getAirportVideoId = (dockSn: string) => {
  // 165-0-7 是机场内部摄像头的payload index，normal-0 表示普通码流
  return `${dockSn}/165-0-7/normal-0`
}

// 获取无人机视频流ID（需要根据无人机的payload信息来获取）
const getDroneVideoId = (droneSn: string, dock: any) => {
  // 根据 model 获取默认的 payloadIndex
  const m = String(dock.model || '').toUpperCase().trim()
  let fallbackPayloadIndex = '52-0-0' // 默认值为 M30D (52-0-0)
  if (m === 'M30T') fallbackPayloadIndex = `${PAYLOAD_TYPE.M30T}-0-0`
  else if (m === 'M30D') fallbackPayloadIndex = `${PAYLOAD_TYPE.M30D}-0-0`
  else if (m === 'M30') fallbackPayloadIndex = `${PAYLOAD_TYPE.M30T}-0-0`
  else if (m === 'M3TD') fallbackPayloadIndex = `${PAYLOAD_TYPE.M3TD}-0-0`
  else if (m === 'M3E') fallbackPayloadIndex = `${PAYLOAD_TYPE.M3E}-0-0`
  else if (m === 'M3T') fallbackPayloadIndex = `${PAYLOAD_TYPE.M3T}-0-0`
  else if (m === 'M3D') fallbackPayloadIndex = `${PAYLOAD_TYPE.M3D}-0-0`
  else if (m === 'M4TD') fallbackPayloadIndex = `${PAYLOAD_TYPE.M4TD}-0-0`
  else if (m === 'M4D') fallbackPayloadIndex = `${PAYLOAD_TYPE.M4D}-0-0`
  else if (m === 'H20T') fallbackPayloadIndex = `${PAYLOAD_TYPE.H20T}-0-0`
  else if (m === 'H20N') fallbackPayloadIndex = `${PAYLOAD_TYPE.H20N}-0-0`
  else if (m === 'H20') fallbackPayloadIndex = `${PAYLOAD_TYPE.H20}-0-0`
  else if (m === 'FPV') fallbackPayloadIndex = `${PAYLOAD_TYPE.FPV}-0-0`

  // 获取无人机的负载信息，通常是 52-0-0（广角）或 53-0-0（变焦）
  const payload = dock.payload?.[0]
  if (payload) {
    const payloadIndex = payload.payload_index || fallbackPayloadIndex
    return `${droneSn}/${payloadIndex}/normal-0`
  }
  return `${droneSn}/${fallbackPayloadIndex}/normal-0`
}

// 切换分屏模式
const toggleSplitScreenMode = () => {
  splitScreenMode.value = !splitScreenMode.value
  // 切换时清除在飞过滤
  splitFilterFlying.value = false
  // 初始化所有机场视频为显示状态，默认无人机为主视频
  if (splitScreenMode.value) {
    onlineDocks.data.forEach(dock => {
      airportVideoVisible[dock.gateway.sn] = false
      mainVideoType[dock.gateway.sn] = 'drone' // 默认无人机为主视频
    })
  }
}

// 切换单个机场视频的显示/隐藏
const toggleAirportVideo = (dockSn: string) => {
  airportVideoVisible[dockSn] = !airportVideoVisible[dockSn]
}

// 切换主视频（无人机 <-> 机场）
const swapMainVideo = (dockSn: string) => {
  if (!mainVideoType[dockSn]) {
    mainVideoType[dockSn] = 'drone'
  }
  mainVideoType[dockSn] = mainVideoType[dockSn] === 'drone' ? 'airport' : 'drone'
}

// 切换分屏布局
const changeSplitLayout = (layout: SplitLayoutType) => {
  splitLayout.value = layout
}

// 布局配置信息
const layoutConfigs: Record<SplitLayoutType, { maxPanels: number; isIrregular: boolean }> = {
  '1x1': { maxPanels: 1, isIrregular: false },
  '2x2': { maxPanels: 4, isIrregular: false },
  '3x3': { maxPanels: 9, isIrregular: false },
  '1x2': { maxPanels: 2, isIrregular: false },
  '2x1': { maxPanels: 2, isIrregular: false },
  '1+7': { maxPanels: 9, isIrregular: true }
}

// 根据布局和索引计算每个分屏的大小样式
const getPanelStyle = (index: number) => {
  const layout = splitLayout.value

  // 规则布局
  if (!layoutConfigs[layout].isIrregular) {
    const regularLayouts: Record<string, { width: string; height: string }> = {
      '1x1': { width: '100%', height: '100%' },
      '2x2': { width: '50%', height: '50%' },
      '3x3': { width: '33.333%', height: '33.333%' },
      '1x2': { width: '100%', height: '50%' },
      '2x1': { width: '50%', height: '100%' }
    }
    return regularLayouts[layout]
  }



  // 不规则布局 - 1+7 布局说明：
  // - Panel 0: 大面板，左侧 50% 宽度，占据 2/3 高度
  // - Panel 1-2: 右上角 2 个小面板
  // - Panel 3-4: 右侧中间 2 个小面板  
  // - Panel 5-8: 底部 4 个小面板
  if (layout === '1+7') {
    if (index === 0) {
      return { width: '50%', height: '66.666%' }
    }
    if (index <= 2) {
      // 右侧上方两个
      return { width: '25%', height: '33.333%' }
    }
    if (index <= 4) {
      // 右侧中间两个
      return { width: '25%', height: '33.333%' }
    }
    // 底部四个 (索引 5-8)
    return { width: '25%', height: '33.333%' }
  }

  return { width: '50%', height: '50%' }
}

// 面板排序顺序（用于拖拽交换）
const panelOrder = ref<number[]>([])

// 拖拽状态
const dragState = reactive({
  draggingIndex: -1,  // 正在拖拽的面板索引
  dragOverIndex: -1,  // 拖拽悬停的目标索引
})

// 初始化面板顺序
const initPanelOrder = () => {
  const config = layoutConfigs[splitLayout.value]
  panelOrder.value = Array.from({ length: config.maxPanels }, (_, i) => i)
}

// 布局变化时重置顺序
watch(splitLayout, () => {
  initPanelOrder()
}, { immediate: true })

// 获取要显示的分屏列表（根据布局限制数量，不足时用占位符填充）
const visibleSplitPanels = computed(() => {
  const config = layoutConfigs[splitLayout.value]
  
  // 根据分屏当前页码切片
  const start = (splitPage.value - 1) * config.maxPanels
  const end = start + config.maxPanels
  const actualDocks = splitDocks.value.slice(start, end)

  // 确保 panelOrder 已初始化
  if (panelOrder.value.length !== config.maxPanels) {
    panelOrder.value = Array.from({ length: config.maxPanels }, (_, i) => i)
  }

  // 创建包含实际数据和占位符的数组，按 panelOrder 排序
  const panels: Array<{ dock: OnlineDevice | null; isPlaceholder: boolean; index: number; dataIndex: number }> = []

  for (let i = 0; i < config.maxPanels; i++) {
    const dataIndex = panelOrder.value[i] // 实际数据索引
    if (dataIndex < actualDocks.length) {
      panels.push({
        dock: actualDocks[dataIndex],
        isPlaceholder: false,
        index: i,           // 显示位置索引
        dataIndex: dataIndex // 数据索引
      })
    } else {
      panels.push({
        dock: null,
        isPlaceholder: true,
        index: i,
        dataIndex: dataIndex
      })
    }
  }

  return panels
})

// 拖拽开始
const onDragStart = (index: number, event: DragEvent) => {
  dragState.draggingIndex = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', index.toString())
  }
}

// 拖拽悬停
const onDragOver = (index: number, event: DragEvent) => {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
  dragState.dragOverIndex = index
}

// 拖拽离开
const onDragLeave = () => {
  dragState.dragOverIndex = -1
}

// 拖拽结束
const onDragEnd = () => {
  dragState.draggingIndex = -1
  dragState.dragOverIndex = -1
}

// 放置（交换位置）
const onDrop = (targetIndex: number, event: DragEvent) => {
  event.preventDefault()
  const sourceIndex = dragState.draggingIndex

  if (sourceIndex !== -1 && sourceIndex !== targetIndex) {
    // 交换 panelOrder 中的位置
    const newOrder = [...panelOrder.value]
    const temp = newOrder[sourceIndex]
    newOrder[sourceIndex] = newOrder[targetIndex]
    newOrder[targetIndex] = temp
    panelOrder.value = newOrder
  }

  dragState.draggingIndex = -1
  dragState.dragOverIndex = -1
}

// 获取或兜底请求有效 clientId
const getValidClientId = async (): Promise<string> => {
  let cid = store.state.clientId
  if (!cid) {
    try {
      const result = await postDrc({})
      if (result?.code === 0 && result.data?.client_id) {
        cid = result.data.client_id
        store.commit('SET_CLIENT_ID', cid)
      }
    } catch (e) {
      console.error('Failed to get clientId:', e)
    }
  }
  return cid
}

// 打开虚拟座舱页面 (Docks list -> virtualCockpit.vue)
const openVirtualCockpit = async (dock: OnlineDevice) => {
  const cid = await getValidClientId()
  const url = router.resolve({
    name: 'virtualCockpit',
    query: {
      dockSn: dock.gateway.sn,
      dockName: dock.gateway.callsign,
      deviceSn: dock.sn,
      clientId: cid,
      model: dock.model
    }
  }).href
  window.open(url, '_blank', 'noopener,noreferrer')
}

// 打开在线设备虚拟座舱页面 (Devices list -> virtualCockpit1.vue)
const openVirtualCockpitDevice = async (device: OnlineDevice) => {
  const cid = await getValidClientId()
  const url = router.resolve({
    name: 'virtualCockpit1',
    query: {
      dockSn: device.gateway.sn,
      dockName: device.gateway.callsign,
      deviceSn: device.sn,
      clientId: cid,
      model: device.model
    }
  }).href
  window.open(url, '_blank', 'noopener,noreferrer')
}

// 无人机开机状态
const dronePoweringOn = reactive<Record<string, boolean>>({})

// 无人机开机
const powerOnDrone = async (dockSn: string) => {
  if (dronePoweringOn[dockSn]) return

  dronePoweringOn[dockSn] = true
  try {
    // 1. 检查是否在远程调试模式
    const currentMode = dockInfo.value[dockSn]?.basic_osd?.mode_code
    if (currentMode !== EDockModeCode.Remote_Debugging) {
      console.log('当前非远程调试模式，正在切换...')
      const debugRes = await postSendCmd({
        dock_sn: dockSn,
        device_cmd: DeviceCmd.DebugModeOpen
      })
      if (debugRes.code !== 0) {
        throw new Error(`切换远程调试模式失败: ${debugRes.message}`)
      }
      // 稍微等待一下状态切换
      await new Promise(resolve => setTimeout(resolve, 1000))
    }

    // 2. 发送开机指令
    const res = await postSendCmd({
      dock_sn: dockSn,
      device_cmd: DeviceCmd.DroneOpen
    })

    if (res.code === 0) {
      // 开机指令发送成功
      console.log('无人机开机指令已发送')

      // 启动轮询更新拓扑，以便及时获取上线后的无人机信息
      let pollCount = 0
      const pollTimer = setInterval(() => {
        getOnlineTopo()
        pollCount++
        // 轮询 20 次（约 1 分钟），或者检测到该机场已有无人机 SN 时停止（这里简单处理只做次数限制）
        if (pollCount >= 20) {
          clearInterval(pollTimer)
        }
      }, 3000)
    } else {
      console.error('无人机开机失败:', res.message)
    }
  } catch (error: any) {
    console.error('无人机开机操作失败:', error?.message || error)
  } finally {
    // 延迟重置状态，因为开机需要时间
    setTimeout(() => {
      dronePoweringOn[dockSn] = false
    }, 10000)
  }
}

provide('flyToPointMethod', flyToPointMethod)
</script>

<template>
  <div class="container">

    <!-- 左侧区域 -->
    <div class="left-section">
      <!-- 分屏控制按钮 -->
      <div class="flex flex-wrap items-center justify-between px-3 py-2 border-b border-gray-700/50 gap-y-2">
        <div class="flex bg-gray-800/60 backdrop-blur-md rounded-lg p-0.5 border border-gray-700/40 shadow-inner flex-shrink-0">
          <button @click="activeTab = 'docks'" 
            class="px-2.5 py-1 text-xs font-semibold rounded-md transition-all duration-300 cursor-pointer"
            :class="activeTab === 'docks' ? 'bg-blue-600 text-white shadow-md' : 'text-gray-400 hover:text-gray-200'">
            机场列表
          </button>
          <button @click="activeTab = 'devices'" 
            class="px-2.5 py-1 text-xs font-semibold rounded-md transition-all duration-300 cursor-pointer"
            :class="activeTab === 'devices' ? 'bg-blue-600 text-white shadow-md' : 'text-gray-400 hover:text-gray-200'">
            手持设备
          </button>
        </div>
        <div class="flex flex-wrap items-center justify-end gap-2 max-w-full">
          <!-- 飞行中无人机状态栏 -->
          <a-tooltip v-if="flyingDronesCount > 0" placement="bottomLeft" color="#0f172a" :overlay-style="{ maxWidth: '340px' }">
            <template #title>
              <div class="p-2 text-[13px] min-w-[300px] leading-normal flex flex-col">
                <div class="font-bold border-b border-gray-700/80 pb-1.5 mb-1.5 text-green-400 flex items-center gap-1.5 flex-shrink-0">
                  <span class="w-2 h-2 bg-green-500 rounded-full shadow-[0_0_4px_#22c55e]"></span>
                  <span>在飞飞行器详情 ({{ flyingDronesCount }}台)</span>
                </div>
                <!-- 可滚动列表区，防止多机时超出页面高度 -->
                <div class="max-h-[300px] overflow-y-auto pr-1.5 space-y-2 select-text" 
                     style="scrollbar-width: thin; scrollbar-color: rgba(156, 163, 175, 0.35) transparent;">
                  <div v-for="drone in flyingDronesList" :key="drone.sn" class="py-1 border-b border-gray-800/60 last:border-0 last:pb-0">
                    <div class="flex justify-between items-center text-white font-semibold">
                      <span>🛸 {{ drone.callsign }}</span>
                      <span class="text-[11px] px-1 py-0.2 bg-blue-600/30 text-blue-300 rounded border border-blue-500/20 font-normal">{{ drone.modeText }}</span>
                    </div>
                    <div class="text-[11px] text-gray-400 mt-1 flex flex-col gap-0.5 pl-3.5">
                      <div>所属源: {{ drone.gatewayCallsign }}</div>
                      <div>操作员: <span class="text-green-400 font-semibold">{{ operatorsMap[drone.sn] || '正在获取...' }}</span></div>
                      <div v-if="drone.gatewayCallsign !== '独立遥控器'">离机场距离: <span class="text-white font-medium">{{ drone.homeDistance }}m</span></div>
                      <div>当前经纬度: <span class="text-gray-300 select-all font-mono">{{ drone.longitude }}, {{ drone.latitude }}</span></div>
                      <div class="flex gap-3">
                        <span>高度: {{ drone.height }}m</span>
                        <span>速度: {{ drone.speed }}m/s</span>
                      </div>
                    </div>
                  </div>
                </div>
                <!-- 一键查看实况按钮 -->
                <button @click="showFlyingLive"
                  class="mt-2 w-full py-1.5 flex items-center justify-center gap-1.5 bg-green-600/80 hover:bg-green-500 text-white text-xs font-semibold rounded-md transition-all duration-200 border border-green-500/40 shadow-[0_0_10px_rgba(34,197,94,0.3)] cursor-pointer">
                  <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="2" y="3" width="20" height="14" rx="2" />
                    <path d="M8 21h8M12 17v4" stroke-linecap="round" />
                  </svg>
                  <span>一键查看在飞实况</span>
                </button>
              </div>
            </template>
            <div
              @mouseenter="refreshOperators"
              @click="showFlyingLive"
              class="flex items-center gap-1.5 px-2 py-0.5 bg-green-500/10 border border-green-500/30 rounded-md text-[10px] text-green-400 font-semibold select-none shadow-[0_0_8px_rgba(34,197,94,0.25)] animate-pulse cursor-pointer hover:bg-green-500/20 transition-colors flex-shrink-0">
              <span class="w-1.5 h-1.5 bg-green-500 rounded-full shadow-[0_0_6px_#22c55e]"></span>
              <span>{{ flyingDronesCount }} 机在飞</span>
            </div>
          </a-tooltip>
          <div v-else
            class="flex items-center gap-1.5 px-2 py-0.5 bg-gray-800/80 border border-gray-700/60 rounded-md text-[10px] text-gray-400 font-medium select-none flex-shrink-0">
            <span class="w-1.5 h-1.5 bg-gray-500 rounded-full"></span>
            <span>无在飞</span>
          </div>

          <!-- 分屏模式切换按钮 -->
          <a-tooltip :title="splitScreenMode ? '关闭分屏实况' : '开启分屏实况'">
            <button @click="toggleSplitScreenMode"
              class="flex items-center justify-center w-7 h-7 rounded-lg transition-all duration-300 flex-shrink-0"
              :class="splitScreenMode ? 'bg-green-600 text-white shadow-lg shadow-green-500/30' : 'bg-gray-700/80 text-gray-300 hover:bg-gray-600 hover:text-white'">
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="7" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
                <rect x="14" y="14" width="7" height="7" rx="1" />
              </svg>
            </button>
          </a-tooltip>

          <!-- 分屏布局选择器 -->
          <div v-if="splitScreenMode" class="flex items-center gap-1 bg-gray-700/80 rounded-lg p-0.5 flex-wrap flex-shrink-0">
            <a-tooltip title="1x1布局">
              <button @click="changeSplitLayout('1x1')"
                class="w-5 h-5 rounded flex items-center justify-center transition-colors"
                :class="splitLayout === '1x1' ? 'bg-blue-600 text-white' : 'text-gray-400 hover:text-white hover:bg-gray-600'">
                <svg class="w-3 h-3" viewBox="0 0 16 16" fill="currentColor">
                  <rect x="2" y="2" width="12" height="12" rx="1" />
                </svg>
              </button>
            </a-tooltip>
            <a-tooltip title="2x2布局">
              <button @click="changeSplitLayout('2x2')"
                class="w-5 h-5 rounded flex items-center justify-center transition-colors"
                :class="splitLayout === '2x2' ? 'bg-blue-600 text-white' : 'text-gray-400 hover:text-white hover:bg-gray-600'">
                <svg class="w-3 h-3" viewBox="0 0 16 16" fill="currentColor">
                  <rect x="2" y="2" width="5" height="5" rx="0.5" />
                  <rect x="9" y="2" width="5" height="5" rx="0.5" />
                  <rect x="2" y="9" width="5" height="5" rx="0.5" />
                  <rect x="9" y="9" width="5" height="5" rx="0.5" />
                </svg>
              </button>
            </a-tooltip>
            <a-tooltip title="3x3九宫格">
              <button @click="changeSplitLayout('3x3')"
                class="w-5 h-5 rounded flex items-center justify-center transition-colors"
                :class="splitLayout === '3x3' ? 'bg-blue-600 text-white' : 'text-gray-400 hover:text-white hover:bg-gray-600'">
                <svg class="w-3 h-3" viewBox="0 0 16 16" fill="currentColor">
                  <rect x="1" y="1" width="4" height="4" rx="0.3" />
                  <rect x="6" y="1" width="4" height="4" rx="0.3" />
                  <rect x="11" y="1" width="4" height="4" rx="0.3" />
                  <rect x="1" y="6" width="4" height="4" rx="0.3" />
                  <rect x="6" y="6" width="4" height="4" rx="0.3" />
                  <rect x="11" y="6" width="4" height="4" rx="0.3" />
                  <rect x="1" y="11" width="4" height="4" rx="0.3" />
                  <rect x="6" y="11" width="4" height="4" rx="0.3" />
                  <rect x="11" y="11" width="4" height="4" rx="0.3" />
                </svg>
              </button>
            </a-tooltip>
            <a-tooltip title="1x2布局">
              <button @click="changeSplitLayout('1x2')"
                class="w-5 h-5 rounded flex items-center justify-center transition-colors"
                :class="splitLayout === '1x2' ? 'bg-blue-600 text-white' : 'text-gray-400 hover:text-white hover:bg-gray-600'">
                <svg class="w-3 h-3" viewBox="0 0 16 16" fill="currentColor">
                  <rect x="2" y="2" width="12" height="5" rx="0.5" />
                  <rect x="2" y="9" width="12" height="5" rx="0.5" />
                </svg>
              </button>
            </a-tooltip>
            <a-tooltip title="2x1布局">
              <button @click="changeSplitLayout('2x1')"
                class="w-5 h-5 rounded flex items-center justify-center transition-colors"
                :class="splitLayout === '2x1' ? 'bg-blue-600 text-white' : 'text-gray-400 hover:text-white hover:bg-gray-600'">
                <svg class="w-3 h-3" viewBox="0 0 16 16" fill="currentColor">
                  <rect x="2" y="2" width="5" height="12" rx="0.5" />
                  <rect x="9" y="2" width="5" height="12" rx="0.5" />
                </svg>
              </button>
            </a-tooltip>
            <a-tooltip title="1+8不规则布局">
              <button @click="changeSplitLayout('1+7')"
                class="w-5 h-5 rounded flex items-center justify-center transition-colors"
                :class="splitLayout === '1+7' ? 'bg-blue-600 text-white' : 'text-gray-400 hover:text-white hover:bg-gray-600'">
                <svg class="w-3 h-3" viewBox="0 0 16 16" fill="currentColor">
                  <!-- 左上大块 -->
                  <rect x="1" y="1" width="7" height="9" rx="0.5" />
                  <!-- 右侧4个小块 -->
                  <rect x="9" y="1" width="3" height="4" rx="0.3" />
                  <rect x="12.5" y="1" width="3" height="4" rx="0.3" />
                  <rect x="9" y="5.5" width="3" height="4" rx="0.3" />
                  <rect x="12.5" y="5.5" width="3" height="4" rx="0.3" />
                  <!-- 底部4个小块 -->
                  <rect x="1" y="11" width="3.5" height="4" rx="0.3" />
                  <rect x="5" y="11" width="3.5" height="4" rx="0.3" />
                  <rect x="9" y="11" width="3" height="4" rx="0.3" />
                  <rect x="12.5" y="11" width="3" height="4" rx="0.3" />
                </svg>
              </button>
            </a-tooltip>
          </div>
        </div>
      </div>

      <!-- 分屏控制和切页栏 -->
      <div v-if="splitScreenMode && (totalSplitPages > 1 || splitFilterFlying)" 
        class="flex items-center justify-between px-3 py-2 bg-[#0c1b3e]/60 backdrop-blur-sm border-b border-gray-700/40 select-none flex-shrink-0 transition-all duration-300">
        <!-- 左侧：仅在飞过滤状态 -->
        <div class="flex items-center gap-1.5">
          <div v-if="splitFilterFlying" class="flex items-center gap-1 px-1.5 py-0.5 bg-green-500/15 border border-green-500/30 rounded text-[10px] text-green-400 font-semibold whitespace-nowrap">
            <span class="w-1.5 h-1.5 bg-green-500 rounded-full shadow-[0_0_4px_#22c55e] animate-pulse"></span>
            <span>仅在飞</span>
            <button @click="clearFlyingFilter" 
              class="ml-1 w-4 h-4 flex items-center justify-center rounded bg-green-500/20 hover:bg-red-500/40 text-green-300 hover:text-white transition-colors cursor-pointer" title="显示全部机场">
              <svg class="w-2 h-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                <path d="M6 18L18 6M6 6l12 12" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </div>
          <span v-else class="text-[16px] text-[#7898E3]/90 font-medium">监控源: {{ splitDocks.length }} 个机场</span>
        </div>

        <!-- 右侧：翻页控件 -->
        <div class="flex items-center gap-2">
          <button v-if="totalSplitPages > 1"
            @click="splitPage > 1 ? splitPage-- : null"
            :disabled="splitPage <= 1"
            class="flex items-center justify-center w-5 h-5 rounded bg-gray-800/80 text-gray-300 hover:bg-gray-700/90 hover:text-white disabled:opacity-20 disabled:cursor-not-allowed transition-all cursor-pointer border border-[#7898E3]/20"
            title="上一页">
            <svg class="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          
          <span class="text-[10px] font-semibold text-gray-300 min-w-[70px] text-center">
            第 {{ splitPage }} / {{ totalSplitPages }} 页
          </span>
          
          <button v-if="totalSplitPages > 1"
            @click="splitPage < totalSplitPages ? splitPage++ : null"
            :disabled="splitPage >= totalSplitPages"
            class="flex items-center justify-center w-5 h-5 rounded bg-gray-800/80 text-gray-300 hover:bg-gray-700/90 hover:text-white disabled:opacity-20 disabled:cursor-not-allowed transition-all cursor-pointer border border-[#7898E3]/20"
            title="下一页">
            <svg class="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>

      <div class="equipment">
        <!-- 搜索过滤栏 -->
        <div class="search-filter-bar">
          <a-input v-model:value="searchQuery" placeholder="搜索机场或无人机名称/SN..." allow-clear>
            <template #prefix>
              <SearchOutlined style="color: rgba(120, 152, 227, 0.6)" />
            </template>
          </a-input>
        </div>

        <!-- 机场列表选项卡下内容 -->
        <template v-if="activeTab === 'docks'">
          <!-- 机场在线/离线统计 -->
          <div class="dock-stats-bar">
            <div class="stat-item online">
              <span class="stat-dot bg-green-500 shadow-[0_0_4px_#22c55e]"></span>
              <span class="stat-label">在线:</span>
              <span class="stat-value text-green-400">{{ onlineDocksCount }}</span>
            </div>
            <a-tooltip placement="bottomLeft" color="#0f172a" :overlay-style="{ maxWidth: '340px' }">
              <template #title>
                <div class="p-2 text-[12px] min-w-[200px] leading-normal flex flex-col">
                  <div class="font-bold border-b border-gray-700/80 pb-1.5 mb-1.5 text-red-400 flex items-center gap-1.5">
                    <span class="w-1.5 h-1.5 bg-red-500 rounded-full shadow-[0_0_4px_#ef4444]"></span>
                    <span>离线机场详情 ({{ offlineDocksCount }}台)</span>
                  </div>
                  <div v-if="offlineDocksList.length > 0" class="max-h-[200px] overflow-y-auto pr-1 space-y-1"
                       style="scrollbar-width: thin; scrollbar-color: rgba(156, 163, 175, 0.35) transparent;">
                    <div v-for="dock in offlineDocksList" :key="dock.gateway.sn" class="py-1 border-b border-gray-800/40 last:border-0 last:pb-0 text-gray-300">
                      <div class="flex justify-between items-center">
                        <span class="font-medium">❌ {{ dock.gateway.callsign }}</span>
                      </div>
                      <div class="text-[10px] text-gray-500 pl-4 font-mono select-all">SN: {{ dock.gateway.sn }}</div>
                    </div>
                  </div>
                  <div v-else class="text-gray-400 text-xs py-1">暂无离线机场</div>
                </div>
              </template>
              <div class="stat-item offline cursor-pointer">
                <span class="stat-dot bg-red-500 shadow-[0_0_4px_#ef4444]"></span>
                <span class="stat-label">离线:</span>
                <span class="stat-value text-red-400">{{ offlineDocksCount }}</span>
              </div>
            </a-tooltip>
            <div class="stat-divider"></div>
            <div class="stat-item total">
              <span class="stat-label">总数:</span>
              <span class="stat-value text-white">{{ onlineDocks.data.length }}</span>
            </div>
          </div>

          <div v-if="filteredDocks.length === 0">
            <a-empty :image="noData"
              :image-style="{ height: '80px', display: 'flex', justifyContent: 'center', alignItems: 'center' }" />
          </div>
          <div v-else class="equipment-all">
            <div v-for="dock in paginatedDocks" :key="dock.gateway.sn" class="equipment-card">
              <!-- Card Header -->
              <div class="card-header">
                <div class="title-section">
                  <div class="main-title text-hidden" :title="dock.gateway.callsign">
                    {{ dock.gateway.callsign }}
                  </div>
                  <div class="sub-title text-hidden" :title="dock.callsign ?? '无设备'">
                    🛸 {{ dock.callsign ?? '无设备' }}
                  </div>
                </div>
                
                <div class="card-actions">
                  <!-- 负载显示 -->
                  <template v-if="dock.sn && psdkWidgets[dock.sn] && psdkWidgets[dock.sn].length > 0">
                    <a-tooltip>
                      <template #title>
                        <div class="text-[11px] p-1">
                          <div class="font-bold border-b border-gray-700/80 pb-1 mb-1">负载信息</div>
                          <div v-for="p in psdkWidgets[dock.sn]" :key="p.payload_sn">
                            <div>名称: {{ p.psdk_name }}</div>
                            <div>SN: {{ p.payload_sn }}</div>
                            <div>版本: {{ p.psdk_version }}</div>
                          </div>
                        </div>
                      </template>
                      <div class="payload-badge">
                        <span class="payload-badge-dot"></span>
                        <span class="payload-badge-text text-hidden">
                          {{ psdkWidgets[dock.sn][0].psdk_name }}{{ psdkWidgets[dock.sn].length > 1 ? ' +' + (psdkWidgets[dock.sn].length - 1) : '' }}
                        </span>
                      </div>
                    </a-tooltip>
                  </template>

                  <!-- 进入虚拟座舱 -->
                  <a-tooltip title="进入虚拟座舱">
                    <button class="action-btn-glass" @click="openVirtualCockpit(dock)">
                      <AimOutlined class="text-blue-400" />
                    </button>
                  </a-tooltip>

                  <!-- 显隐眼睛按钮 -->
                  <a-tooltip :title="osdVisible.gateway_sn === dock.gateway.sn && osdVisible.visible ? '隐藏调试信息' : ' 显示调试信息'">
                    <button class="action-btn-glass" @click="switchVisible($event, dock, true, dockInfo[dock.gateway.sn] &&
                      dockInfo[dock.gateway.sn].basic_osd?.mode_code !== EDockModeCode.Disconnected)">
                      <EyeOutlined v-if="osdVisible.gateway_sn === dock.gateway.sn && osdVisible.visible" class="text-blue-400" />
                      <EyeInvisibleOutlined v-else class="text-gray-400" />
                    </button>
                  </a-tooltip>
                </div>
              </div>

              <!-- Card Body -->
              <div class="card-body">
                <!-- Status Row -->
                <div class="status-grid">
                  <!-- 机场状态 -->
                  <div class="status-item">
                    <div class="status-label">
                      <span class="dot" :class="dockInfo[dock.gateway.sn] && dockInfo[dock.gateway.sn].basic_osd?.mode_code !== EDockModeCode.Disconnected ? 'bg-green-500 shadow-[0_0_6px_#22c55e]' : 'bg-red-500 shadow-[0_0_6px_#ef4444]'"></span>
                      <span>机场：</span>
                    </div>
                    <div class="status-value text-hidden" :style="dockInfo[dock.gateway.sn] && dockInfo[dock.gateway.sn].basic_osd?.mode_code !== EDockModeCode.Disconnected ? 'color: #00ee8b' : 'color: #f87171'">
                      {{ dockInfo[dock.gateway.sn] ?
                        (EDockModeCodeZh as any)[EDockModeCode[dockInfo[dock.gateway.sn].basic_osd?.mode_code]] :
                        (EDockModeCodeZh as any)[EDockModeCode[EDockModeCode.Disconnected]] }}
                    </div>

                    <!-- HMS Info for Dock -->
                    <div v-if="hmsInfo[dock.gateway.sn]" class="hms-badge-container">
                      <a-popover trigger="click" placement="bottom" overlay-class-name="hms-popover-custom"
                        v-model:open="hmsVisible[dock.gateway.sn]"
                        @openChange="readHms(hmsVisible[dock.gateway.sn], dock.gateway.sn)"
                        :overlayStyle="{ width: '280px' }">
                        <template #content>
                          <div class="hms-collapse-container">
                            <a-collapse :bordered="false" expand-icon-position="right" :accordion="true" style="background: transparent;">
                              <a-collapse-panel v-for="(hms, idx) in hmsInfo[dock.gateway.sn]" :key="hms.hms_id + '_' + idx"
                                :showArrow="false"
                                class="hms-panel-card"
                                :class="hms.level === EHmsLevel.CAUTION ? 'hms-caution' : hms.level === EHmsLevel.WARN ? 'hms-warn' : 'hms-notice'">
                                <template #header="slotProps">
                                  <div class="hms-panel-header">
                                    <div class="hms-header-main">
                                      <span class="hms-level-dot"></span>
                                      <span class="hms-message-text" :title="hms.message_zh">{{ hms.message_zh }}</span>
                                    </div>
                                    <div class="hms-header-arrow">
                                      <DoubleRightOutlined :rotate="slotProps?.isActive ? 90 : 0" />
                                    </div>
                                  </div>
                                </template>
                                <div class="hms-panel-detail">
                                  <div class="detail-row">
                                    <span class="detail-label">时间：</span>
                                    <span class="detail-value">{{ hms.create_time }}</span>
                                  </div>
                                  <div v-if="hms.hms_id" class="detail-row">
                                    <span class="detail-label">代码：</span>
                                    <span class="detail-value code-font">{{ hms.hms_id }}</span>
                                  </div>
                                </div>
                              </a-collapse-panel>
                            </a-collapse>
                          </div>
                        </template>
                        <div class="hms-glow-badge" :class="getHmsClass(hmsInfo[dock.gateway.sn][0].level)">
                          <span>{{ hmsInfo[dock.gateway.sn].length }}</span>
                        </div>
                      </a-popover>
                    </div>
                  </div>

                  <!-- 无人机状态 -->
                  <div class="status-item">
                    <div class="status-label">
                      <span class="dot" :class="deviceInfo[dock.sn] && deviceInfo[dock.sn].mode_code !== EModeCode.Disconnected ? 'bg-green-500 shadow-[0_0_6px_#22c55e]' : 'bg-gray-500 shadow-[0_0_6px_#6b7280]'"></span>
                      <span>飞机：</span>
                    </div>
                    <div class="status-value text-hidden" :style="deviceInfo[dock.sn] && deviceInfo[dock.sn].mode_code !== EModeCode.Disconnected ? 'color: #00ee8b' : 'color: #9ca3af'">
                      {{ deviceInfo[dock.sn] ? EModeWord[EModeCode[deviceInfo[dock.sn].mode_code] as keyof typeof EModeWord] :
                        EModeWord[EModeCode[EModeCode.Disconnected] as keyof typeof EModeWord] }}
                    </div>

                    <!-- HMS Info for Drone -->
                    <div v-if="hmsInfo[dock.sn]" class="hms-badge-container">
                      <a-popover trigger="click" placement="bottom" overlay-class-name="hms-popover-custom" v-model:open="hmsVisible[dock.sn]"
                        @openChange="readHms(hmsVisible[dock.sn], dock.sn)"
                        :overlayStyle="{ width: '280px' }">
                        <template #content>
                          <div class="hms-collapse-container">
                            <a-collapse :bordered="false" expand-icon-position="right" :accordion="true" style="background: transparent;">
                              <a-collapse-panel v-for="(hms, idx) in hmsInfo[dock.sn]" :key="hms.hms_id + '_' + idx" :showArrow="false"
                                class="hms-panel-card"
                                :class="hms.level === EHmsLevel.CAUTION ? 'hms-caution' : hms.level === EHmsLevel.WARN ? 'hms-warn' : 'hms-notice'">
                                <template #header="slotProps">
                                  <div class="hms-panel-header">
                                    <div class="hms-header-main">
                                      <span class="hms-level-dot"></span>
                                      <span class="hms-message-text" :title="hms.message_zh">{{ hms.message_zh }}</span>
                                    </div>
                                    <div class="hms-header-arrow">
                                      <DoubleRightOutlined :rotate="slotProps?.isActive ? 90 : 0" />
                                    </div>
                                  </div>
                                </template>
                                <div class="hms-panel-detail">
                                  <div class="detail-row">
                                    <span class="detail-label">时间：</span>
                                    <span class="detail-value">{{ hms.create_time }}</span>
                                  </div>
                                  <div v-if="hms.hms_id" class="detail-row">
                                    <span class="detail-label">代码：</span>
                                    <span class="detail-value code-font">{{ hms.hms_id }}</span>
                                  </div>
                                </div>
                              </a-collapse-panel>
                            </a-collapse>
                          </div>
                        </template>
                        <div class="hms-glow-badge" :class="getHmsClass(hmsInfo[dock.sn][0].level)">
                          <span>{{ hmsInfo[dock.sn].length }}</span>
                        </div>
                      </a-popover>
                    </div>
                  </div>
                </div>

                <!-- Battery level row -->
                <div class="battery-section">
                  <div class="battery-header">
                    <span class="battery-title">🔋 剩余电量</span>
                    <div class="battery-details">
                      <span class="charge-status-tag" :class="{ 'charging-pulse': Boolean(dockInfo[dock.gateway.sn]?.basic_osd?.drone_charge_state?.state) }">
                        {{ getChargeStateInfo(Boolean(dockInfo[dock.gateway.sn]?.basic_osd?.drone_charge_state?.state)).text }}
                      </span>
                      <span class="battery-number" :style="getBatteryColor(dockInfo[dock.gateway.sn]?.basic_osd?.drone_charge_state?.capacity_percent ?? deviceInfo[dock.sn]?.battery?.capacity_percent)">
                        {{ dockInfo[dock.gateway.sn]?.basic_osd?.drone_charge_state?.capacity_percent ?? deviceInfo[dock.sn]?.battery?.capacity_percent ?? '--' }}%
                      </span>
                    </div>
                  </div>
                  
                  <!-- Glowing battery progress bar -->
                  <div class="battery-progress-wrapper">
                    <div class="battery-progress-bar"
                      :style="{
                        width: `${dockInfo[dock.gateway.sn]?.basic_osd?.drone_charge_state?.capacity_percent ?? deviceInfo[dock.sn]?.battery?.capacity_percent ?? 0}%`,
                        background: getBatteryGradient(dockInfo[dock.gateway.sn]?.basic_osd?.drone_charge_state?.capacity_percent ?? deviceInfo[dock.sn]?.battery?.capacity_percent),
                        boxShadow: getBatteryGlow(dockInfo[dock.gateway.sn]?.basic_osd?.drone_charge_state?.capacity_percent ?? deviceInfo[dock.sn]?.battery?.capacity_percent)
                      }">
                    </div>
                  </div>
                </div>
              </div>


            </div>
          </div>
          <!-- 分页组件 -->
          <div class="dock-pagination">
            <a-pagination v-model:current="dockListPage" v-model:pageSize="dockListPageSize" :total="dockListTotal"
              :showSizeChanger="false" :showQuickJumper="false" size="small" @change="onDockPageChange" />
          </div>
        </template>

        <!-- 在线设备选项卡下内容 -->
        <template v-else-if="activeTab === 'devices'">
          <div v-if="filteredDevices.length === 0">
            <a-empty :image="noData"
              :image-style="{ height: '80px', display: 'flex', justifyContent: 'center', alignItems: 'center' }" />
          </div>
          <div v-else class="equipment-all">
            <div v-for="device in paginatedDevices" :key="device.sn" class="equipment-card">
              <!-- Card Header -->
              <div class="card-header">
                <div class="title-section">
                  <div class="main-title text-hidden" :title="device.callsign ?? '未知设备'">
                    {{ device.callsign ?? '未知设备' }}
                  </div>
                  <div class="sub-title text-hidden" :title="device.model ? device.model : '未知型号'">
                    🛸 {{ device.model ? device.model : '未知型号' }}
                  </div>
                </div>
                
                <div class="card-actions">
                  <!-- 负载显示 -->
                  <template v-if="device.sn && psdkWidgets[device.sn] && psdkWidgets[device.sn].length > 0">
                    <a-tooltip>
                      <template #title>
                        <div class="text-[11px] p-1">
                          <div class="font-bold border-b border-gray-700/80 pb-1 mb-1">负载信息</div>
                          <div v-for="p in psdkWidgets[device.sn]" :key="p.payload_sn">
                            <div>名称: {{ p.psdk_name }}</div>
                            <div>SN: {{ p.payload_sn }}</div>
                            <div>版本: {{ p.psdk_version }}</div>
                          </div>
                        </div>
                      </template>
                      <div class="payload-badge">
                        <span class="payload-badge-dot"></span>
                        <span class="payload-badge-text text-hidden">
                          {{ psdkWidgets[device.sn][0].psdk_name }}{{ psdkWidgets[device.sn].length > 1 ? ' +' + (psdkWidgets[device.sn].length - 1) : '' }}
                        </span>
                      </div>
                    </a-tooltip>
                  </template>

                  <!-- 进入虚拟座舱 -->
                  <a-tooltip title="进入虚拟座舱">
                    <button class="action-btn-glass" @click="openVirtualCockpitDevice(device)">
                      <AimOutlined class="text-blue-400" />
                    </button>
                  </a-tooltip>

                  <!-- 显隐眼睛按钮 -->
                  <a-tooltip :title="osdVisible.sn === device.sn && osdVisible.visible ? '隐藏定位' : '显示定位'">
                    <button class="action-btn-glass" @click="switchVisible($event, device, false, deviceInfo[device.sn] &&
                      deviceInfo[device.sn].mode_code !== EModeCode.Disconnected)">
                      <EyeOutlined v-if="osdVisible.sn === device.sn && osdVisible.visible" class="text-blue-400" />
                      <EyeInvisibleOutlined v-else class="text-gray-400" />
                    </button>
                  </a-tooltip>
                </div>
              </div>

              <!-- Card Body -->
              <div class="card-body">
                <!-- Status Row -->
                <div class="status-grid single-col">
                  <!-- 无人机状态 -->
                  <div class="status-item">
                    <div class="status-label">
                      <span class="dot" :class="deviceInfo[device.sn] && deviceInfo[device.sn].mode_code !== EModeCode.Disconnected ? 'bg-green-500 shadow-[0_0_6px_#22c55e]' : 'bg-gray-500 shadow-[0_0_6px_#6b7280]'"></span>
                      <span>飞机状态：</span>
                    </div>
                    <div class="status-value text-hidden" :style="deviceInfo[device.sn] && deviceInfo[device.sn].mode_code !== EModeCode.Disconnected ? 'color: #00ee8b' : 'color: #9ca3af'">
                      {{ deviceInfo[device.sn] ? EModeWord[EModeCode[deviceInfo[device.sn].mode_code] as keyof typeof EModeWord] : EModeWord[EModeCode[EModeCode.Disconnected] as keyof typeof EModeWord] }}
                    </div>

                    <!-- HMS Info for Drone -->
                    <div v-if="hmsInfo[device.sn]" class="hms-badge-container">
                      <a-popover trigger="click" placement="bottom" overlay-class-name="hms-popover-custom" v-model:open="hmsVisible[device.sn]"
                        @openChange="readHms(hmsVisible[device.sn], device.sn)"
                        :overlayStyle="{ width: '280px' }">
                        <template #content>
                          <div class="hms-collapse-container">
                            <a-collapse :bordered="false" expand-icon-position="right" :accordion="true" style="background: transparent;">
                              <a-collapse-panel v-for="(hms, idx) in hmsInfo[device.sn]" :key="hms.hms_id + '_' + idx" :showArrow="false"
                                class="hms-panel-card"
                                :class="hms.level === EHmsLevel.CAUTION ? 'hms-caution' : hms.level === EHmsLevel.WARN ? 'hms-warn' : 'hms-notice'">
                                <template #header="slotProps">
                                  <div class="hms-panel-header">
                                    <div class="hms-header-main">
                                      <span class="hms-level-dot"></span>
                                      <span class="hms-message-text" :title="hms.message_zh">{{ hms.message_zh }}</span>
                                    </div>
                                    <div class="hms-header-arrow">
                                      <DoubleRightOutlined :rotate="slotProps?.isActive ? 90 : 0" />
                                    </div>
                                  </div>
                                </template>
                                <div class="hms-panel-detail">
                                  <div class="detail-row">
                                    <span class="detail-label">时间：</span>
                                    <span class="detail-value">{{ hms.create_time }}</span>
                                  </div>
                                  <div v-if="hms.hms_id" class="detail-row">
                                    <span class="detail-label">代码：</span>
                                    <span class="detail-value code-font">{{ hms.hms_id }}</span>
                                  </div>
                                </div>
                              </a-collapse-panel>
                            </a-collapse>
                          </div>
                        </template>
                        <div class="hms-glow-badge" :class="getHmsClass(hmsInfo[device.sn][0].level)">
                          <span>{{ hmsInfo[device.sn].length }}</span>
                        </div>
                      </a-popover>
                    </div>
                  </div>

                  <!-- 遥控器配对状态 -->
                  <div class="status-item">
                    <div class="status-label">
                      <span class="dot bg-blue-500 shadow-[0_0_6px_#3b82f6]"></span>
                      <span>关联遥控：</span>
                    </div>
                    <div class="status-value text-hidden text-blue-300 font-semibold" style="max-width: 120px;">
                      {{ device.gateway.model }}
                    </div>
                  </div>
                </div>

                <!-- Battery level row -->
                <div class="battery-section">
                  <div class="battery-header">
                    <span class="battery-title">🔋 剩余电量</span>
                    <div class="battery-details">
                      <span class="battery-number" :style="getBatteryColor(deviceInfo[device.sn]?.battery?.capacity_percent)">
                        {{ deviceInfo[device.sn]?.battery?.capacity_percent ?? '--' }}%
                      </span>
                    </div>
                  </div>
                  
                  <!-- Glowing battery progress bar -->
                  <div class="battery-progress-wrapper">
                    <div class="battery-progress-bar"
                      :style="{
                        width: `${deviceInfo[device.sn]?.battery?.capacity_percent ?? 0}%`,
                        background: getBatteryGradient(deviceInfo[device.sn]?.battery?.capacity_percent),
                        boxShadow: getBatteryGlow(deviceInfo[device.sn]?.battery?.capacity_percent)
                      }">
                    </div>
                  </div>
                </div>
              </div>


            </div>
          </div>
          <!-- 分页组件 -->
          <div class="dock-pagination">
            <a-pagination v-model:current="deviceListPage" v-model:pageSize="deviceListPageSize" :total="deviceListTotal"
              :showSizeChanger="false" :showQuickJumper="false" size="small" @change="onDevicePageChange" />
          </div>
        </template>
      </div>
    </div>

    <!-- 右侧区域 -->
    <div class="right-section">
      <!-- 普通地图模式 -->
      <div v-if="!splitScreenMode" class="h-full">
        <DjiCesium :dockMapLocation="dockMapLocation" :dockMap="dockMap" :droneMap="droneMap" :sceneMode="2" :loadTerrain="false"></DjiCesium>
      </div>

      <!-- 分屏实况模式 -->
      <div v-else class="h-full w-full overflow-hidden bg-gray-900 relative"
        :class="splitLayout === '1+7' ? 'split-grid-1-7' : 'flex flex-wrap'">

        <div v-for="panel in visibleSplitPanels" :key="panel.dock ? panel.dock.gateway.sn : `placeholder-${panel.dataIndex}`"
          class="split-panel relative overflow-hidden border border-gray-700 cursor-move"
          :style="splitLayout !== '1+7' ? getPanelStyle(panel.index) : undefined" :class="[
            splitLayout === '1+7' ? `grid-item-${panel.index}` : '',
            dragState.draggingIndex === panel.index ? 'opacity-50 scale-95' : '',
            dragState.dragOverIndex === panel.index && dragState.draggingIndex !== panel.index ? 'ring-2 ring-blue-500 ring-inset' : ''
          ]" draggable="true" @dragstart="onDragStart(panel.index, $event)" @dragover="onDragOver(panel.index, $event)"
          @dragleave="onDragLeave" @dragend="onDragEnd" @drop="onDrop(panel.index, $event)">

          <!-- 占位符面板 -->
          <template v-if="panel.isPlaceholder">
            <div class="w-full h-full bg-gray-800/50 flex flex-col items-center justify-center">
              <svg class="w-12 h-12 text-gray-600 mb-2" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                stroke-width="1">
                <rect x="3" y="3" width="18" height="18" rx="2" stroke-dasharray="4 2" />
                <path d="M12 8v4m0 4h.01" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" />
              </svg>
              <span class="text-gray-500 text-sm">空闲分屏</span>
              <span class="text-gray-600 text-xs mt-1">等待机场上线</span>
            </div>
          </template>

          <!-- 实际机场面板 -->
          <template v-else>
            <!-- 分屏头部信息 -->
            <div
              class="absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-2 py-1 bg-gradient-to-b from-black/80 to-transparent">
              <div class="flex items-center gap-2">
                <span class="text-white text-xs font-medium truncate max-w-[120px]">
                  {{ panel.dock?.gateway.callsign }}
                </span>
                <span class="text-gray-400 text-[10px]">
                  {{ panel.dock?.callsign || '无人机' }}
                </span>
              </div>
              <div class="flex items-center gap-1.5">
                <!-- 虚拟座舱按钮 -->
                <a-tooltip title="进入虚拟座舱">
                  <button @click="openVirtualCockpit(panel.dock)"
                    class="text-white/80 hover:text-white text-[10px] px-2 py-0.5 bg-blue-600/60 hover:bg-blue-600 rounded transition-colors">
                    虚拟座舱
                  </button>
                </a-tooltip>
                <!-- 无人机状态指示 -->
                <span class="text-[10px] px-1 py-0.5 rounded"
                  :class="panel.dock?.sn && deviceInfo[panel.dock.sn] && deviceInfo[panel.dock.sn].mode_code !== 0 ? 'bg-green-600/80 text-white' : 'bg-gray-600/80 text-gray-300'">
                  {{ panel.dock?.sn && deviceInfo[panel.dock.sn] ? '在线' : '离线' }}
                </span>
              </div>
            </div>

            <!-- 视频容器 - 使用绝对定位切换主/副视频位置 -->
            <div class="w-full h-full relative">
              <!-- 无人机视频 -->
              <div class="absolute transition-all duration-300 ease-in-out bg-black overflow-hidden"
                :class="(!panel.dock?.gateway.sn || !mainVideoType[panel.dock.gateway.sn] || mainVideoType[panel.dock.gateway.sn] === 'drone')
                  ? 'inset-0 z-0 rounded-none'
                  : 'bottom-2 right-2 w-[180px] h-[120px] z-10 rounded-lg border border-gray-600 shadow-xl cursor-pointer hover:border-blue-500'"
                @click="panel.dock?.gateway.sn && mainVideoType[panel.dock.gateway.sn] === 'airport' && swapMainVideo(panel.dock.gateway.sn)">
                <webRtcPlay v-if="panel.dock?.sn && deviceInfo[panel.dock.sn]"
                  :key="panel.dock.sn"
                  :droneId="getDroneVideoId(panel.dock.sn, panel.dock)" class="w-full h-full" />
                <div v-else class="w-full h-full flex flex-col items-center justify-center text-gray-500 text-sm">
                  <svg class="w-12 h-12 text-gray-600" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                    stroke-width="1">
                    <path d="M12 12m-3 0a3 3 0 1 0 6 0 a3 3 0 1 0-6 0" />
                    <path d="M4.5 4.5L7.5 7.5M19.5 4.5L16.5 7.5M4.5 19.5L7.5 16.5M19.5 19.5L16.5 16.5" stroke-width="2"
                      stroke-linecap="round" />
                    <circle cx="4.5" cy="4.5" r="2" fill="currentColor" />
                    <circle cx="19.5" cy="4.5" r="2" fill="currentColor" />
                    <circle cx="4.5" cy="19.5" r="2" fill="currentColor" />
                    <circle cx="19.5" cy="19.5" r="2" fill="currentColor" />
                  </svg>
                  <span class="mt-2">无人机未上线</span>
                  <!-- 无人机开机按钮 -->
                  <a-tooltip title="无人机开机">
                    <button v-if="panel.dock?.gateway.sn" @click.stop="powerOnDrone(panel.dock.gateway.sn)"
                      :disabled="dronePoweringOn[panel.dock.gateway.sn]"
                      class="absolute top-2 right-2 w-7 h-7 bg-green-600 hover:bg-green-500 disabled:bg-gray-600 disabled:cursor-not-allowed text-white rounded flex items-center justify-center transition-all shadow-md hover:scale-105 z-20">
                      <svg v-if="dronePoweringOn[panel.dock.gateway.sn]" class="w-3.5 h-3.5 animate-spin"
                        viewBox="0 0 24 24" fill="none">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4">
                        </circle>
                        <path class="opacity-75" fill="currentColor"
                          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z">
                        </path>
                      </svg>
                      <PoweroffOutlined v-else class="text-sm" />
                    </button>
                  </a-tooltip>
                </div>
                <!-- 无人机视频标签（小窗口模式时显示） -->
                <div v-if="panel.dock?.gateway.sn && mainVideoType[panel.dock.gateway.sn] === 'airport'"
                  class="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/80 to-transparent py-1 px-2">
                  <span class="text-white text-[10px]">🚁 无人机</span>
                </div>
                <!-- 切换提示（小窗口模式） -->
                <div v-if="panel.dock?.gateway.sn && mainVideoType[panel.dock.gateway.sn] === 'airport'"
                  class="absolute inset-0 bg-black/0 hover:bg-black/40 transition-colors flex items-center justify-center opacity-0 hover:opacity-100">
                  <div class="flex items-center gap-1 text-white text-xs bg-blue-600/80 px-2 py-1 rounded">
                    <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M7 16V4m0 0L3 8m4-4l4 4M17 8v12m0 0l4-4m-4 4l-4-4" stroke-linecap="round"
                        stroke-linejoin="round" />
                    </svg>
                    <span>切换</span>
                  </div>
                </div>
              </div>

              <!-- 机场视频 -->
              <div v-if="panel.dock && airportVideoVisible[panel.dock.gateway.sn]"
                class="absolute transition-all duration-300 ease-in-out bg-black overflow-hidden"
                :class="mainVideoType[panel.dock.gateway.sn] === 'airport'
                  ? 'inset-0 z-0 rounded-none'
                  : 'bottom-2 right-2 w-[180px] h-[120px] z-10 rounded-lg border border-gray-600 shadow-xl cursor-pointer hover:border-blue-500'"
                @click="(!mainVideoType[panel.dock.gateway.sn] || mainVideoType[panel.dock.gateway.sn] === 'drone') && swapMainVideo(panel.dock.gateway.sn)">
                <webRtcPlay :key="panel.dock.gateway.sn" :dockId="getAirportVideoId(panel.dock.gateway.sn)" class="w-full h-full" />
                <!-- 机场视频标签（小窗口模式时显示） -->
                <div v-if="!mainVideoType[panel.dock.gateway.sn] || mainVideoType[panel.dock.gateway.sn] === 'drone'"
                  class="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/80 to-transparent py-1 px-2">
                  <span class="text-white text-[10px]">机场监控</span>
                </div>
                <!-- 切换提示（小窗口模式） -->
                <div v-if="!mainVideoType[panel.dock.gateway.sn] || mainVideoType[panel.dock.gateway.sn] === 'drone'"
                  class="absolute inset-0 bg-black/0 hover:bg-black/40 transition-colors flex items-center justify-center opacity-0 hover:opacity-100">
                  <div class="flex items-center gap-1 text-white text-xs bg-blue-600/80 px-2 py-1 rounded">
                    <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M7 16V4m0 0L3 8m4-4l4 4M17 8v12m0 0l4-4m-4 4l-4-4" stroke-linecap="round"
                        stroke-linejoin="round" />
                    </svg>
                    <span>切换</span>
                  </div>
                </div>
                <!-- 关闭小窗口按钮（小窗口模式时显示） -->
                <button v-if="!mainVideoType[panel.dock.gateway.sn] || mainVideoType[panel.dock.gateway.sn] === 'drone'"
                  @click.stop="toggleAirportVideo(panel.dock.gateway.sn)"
                  class="absolute top-1 right-1 z-20 w-5 h-5 rounded bg-gray-800/80 hover:bg-red-600 flex items-center justify-center transition-colors">
                  <svg class="w-3 h-3 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                    stroke-width="2">
                    <path d="M6 18L18 6M6 6l12 12" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </button>
                <!-- 关闭主视频按钮（主视频模式时显示在头部） -->
                <button v-if="mainVideoType[panel.dock.gateway.sn] === 'airport'"
                  @click.stop="toggleAirportVideo(panel.dock.gateway.sn)"
                  class="absolute top-2 right-2 z-20 px-2 py-1 rounded bg-gray-800/80 hover:bg-red-600 flex items-center gap-1 transition-colors">
                  <svg class="w-3 h-3 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                    stroke-width="2">
                    <path d="M6 18L18 6M6 6l12 12" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                  <span class="text-white text-[10px]">关闭机场</span>
                </button>
              </div>

              <!-- 小窗口收起状态按钮 -->
              <div v-if="panel.dock && !airportVideoVisible[panel.dock.gateway.sn]"
                class="absolute z-10 bottom-2 right-2 w-8 h-8 rounded-lg overflow-hidden shadow-xl border border-gray-600 bg-gray-800 flex items-center justify-center cursor-pointer hover:border-blue-500"
                @click="toggleAirportVideo(panel.dock.gateway.sn)">
                <svg class="w-5 h-5 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                  stroke-width="2">
                  <rect x="3" y="3" width="18" height="18" rx="2" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
              </div>
            </div>

          </template>
        </div>
      </div>
    </div>

  </div>

</template>

<style scoped lang="scss">
.container {
  display: flex;
  width: 100vw;
  max-width: 100vw;
  height: 100%;
  gap: 0.5rem;
}

// 分屏面板拖拽样式
.split-panel {
  transition: opacity 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;

  &.cursor-move {
    cursor: grab;

    &:active {
      cursor: grabbing;
    }
  }
}

// 1+7 布局使用 CSS Grid
.split-grid-1-7 {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: repeat(3, 1fr);
  gap: 1px;

  // 大面板占据左上 2x2 区域
  .grid-item-0 {
    grid-column: 1 / 3;
    grid-row: 1 / 3;
  }

  // 右上两个
  .grid-item-1 {
    grid-column: 3;
    grid-row: 1;
  }

  .grid-item-2 {
    grid-column: 4;
    grid-row: 1;
  }

  // 右中两个
  .grid-item-3 {
    grid-column: 3;
    grid-row: 2;
  }

  .grid-item-4 {
    grid-column: 4;
    grid-row: 2;
  }

  // 底部四个
  .grid-item-5 {
    grid-column: 1;
    grid-row: 3;
  }

  .grid-item-6 {
    grid-column: 2;
    grid-row: 3;
  }

  .grid-item-7 {
    grid-column: 3;
    grid-row: 3;
  }

  .grid-item-8 {
    grid-column: 4;
    grid-row: 3;
  }
}

.left-section {
  // flex: 1;
  width: 19.9375rem;
  background: linear-gradient(178deg, #041428 0%, #002658 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.equipment {
  padding: 10px 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .search-filter-bar {
    padding: 0 10px 10px;
    width: 100%;
    
    :deep(.ant-input-affix-wrapper) {
      background-color: rgba(8, 20, 48, 0.45) !important;
      backdrop-filter: blur(10px) !important;
      border: 1px solid rgba(120, 152, 227, 0.2) !important;
      border-radius: 8px !important;
      color: #e0efff !important;
      transition: all 0.3s ease !important;
      box-shadow: none !important;
      
      &:hover, &.ant-input-affix-wrapper-focused {
        border-color: rgba(120, 152, 227, 0.6) !important;
        box-shadow: 0 0 8px rgba(120, 152, 227, 0.2) !important;
        background-color: rgba(10, 26, 62, 0.6) !important;
      }
      
      .ant-input {
        background-color: transparent !important;
        color: #e0efff !important;
        font-size: 0.75rem !important;
        border: none !important;
        box-shadow: none !important;
        
        &::placeholder {
          color: rgba(224, 239, 255, 0.45) !important;
        }
      }
      
      .ant-input-prefix {
        margin-right: 6px;
      }

      .ant-input-clear-icon {
        color: rgba(224, 239, 255, 0.45) !important;
        &:hover {
          color: #fff !important;
        }
      }
    }
  }

  .equipment-all {
    margin: 0 auto;
    flex: 1;
    overflow-y: auto;
    width: 100%;
  }

  .dock-pagination {
    padding: 8px 10px;
    display: flex;
    justify-content: center;
    background: rgba(33, 69, 101, 0.3);
    border-top: 1px solid #3d6a94;

    :deep(.ant-pagination) {
      .ant-pagination-item {
        background: rgba(61, 106, 148, 0.4);
        border-color: #5a8fc4;

        a {
          color: #e0efff;
        }

        &:hover {
          border-color: #40a9ff;
          background: rgba(64, 169, 255, 0.3);

          a {
            color: #40a9ff;
          }
        }

        &-active {
          background: #1890ff;
          border-color: #1890ff;

          a {
            color: #fff;
          }
        }
      }

      .ant-pagination-prev,
      .ant-pagination-next {
        .ant-pagination-item-link {
          background: rgba(61, 106, 148, 0.4);
          border-color: #5a8fc4;
          color: #e0efff;
        }

        &:hover .ant-pagination-item-link {
          border-color: #40a9ff;
          color: #40a9ff;
          background: rgba(64, 169, 255, 0.3);
        }

        &.ant-pagination-disabled .ant-pagination-item-link {
          color: #6a8aad;
          border-color: #4a6a8a;
          background: rgba(45, 55, 72, 0.3);
        }
      }
    }
  }

  .equipment-card {
    background: rgba(8, 20, 48, 0.45);
    backdrop-filter: blur(10px);
    -webkit-backdrop-filter: blur(10px);
    border: 1px solid rgba(120, 152, 227, 0.2);
    border-radius: 12px;
    padding: 12px 14px;
    margin: 0 10px 14px;
    width: calc(100% - 20px);
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2), inset 0 1px 0 rgba(255, 255, 255, 0.05);
    display: flex;
    flex-direction: column;
    gap: 8px;

    &:hover {
      border-color: rgba(120, 152, 227, 0.55);
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.35), 0 0 12px rgba(120, 152, 227, 0.15);
      transform: translateY(-2px);
      background: rgba(10, 26, 62, 0.6);
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid rgba(120, 152, 227, 0.15);
      padding-bottom: 8px;

      .title-section {
        display: flex;
        flex-direction: column;
        gap: 2px;
        width: 80%;

        .main-title {
          font-size: 0.85rem;
          font-weight: 700;
          color: #f3f4f6;
          letter-spacing: 0.5px;
          text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
        }

        .sub-title {
          font-size: 0.72rem;
          color: rgba(224, 239, 255, 0.65);
        }
      }

      .card-actions {
        display: flex;
        align-items: center;
        gap: 6px;

        .payload-badge {
          display: flex;
          align-items: center;
          gap: 4px;
          background: rgba(59, 130, 246, 0.12);
          border: 1px solid rgba(59, 130, 246, 0.3);
          border-radius: 6px;
          padding: 2px 6px;
          font-size: 0.65rem;
          color: #93c5fd;
          max-width: 90px;
          height: 22px;
          box-shadow: 0 0 4px rgba(59, 130, 246, 0.15);
          cursor: default;
          
          .payload-badge-dot {
            width: 4px;
            height: 4px;
            border-radius: 50%;
            background-color: #3b82f6;
            box-shadow: 0 0 6px #3b82f6;
            flex-shrink: 0;
          }

          .payload-badge-text {
            flex: 1;
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            font-weight: 500;
          }
        }

        .action-btn-glass {
          background: rgba(120, 152, 227, 0.1);
          border: 1px solid rgba(120, 152, 227, 0.25);
          width: 28px;
          height: 28px;
          border-radius: 8px;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          transition: all 0.2s ease;
          color: #fff;

          &:hover {
            background: rgba(120, 152, 227, 0.25);
            border-color: rgba(120, 152, 227, 0.5);
            transform: scale(1.05);
          }

          &:active {
            transform: scale(0.95);
          }
        }
      }
    }

    .card-body {
      display: flex;
      flex-direction: column;
      gap: 10px;

      .status-grid {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 8px;

        &.single-col {
          grid-template-columns: 1fr;
          gap: 6px;
        }

        .status-item {
          display: flex;
          align-items: center;
          justify-content: space-between;
          background: rgba(15, 23, 42, 0.4);
          border: 1px solid rgba(120, 152, 227, 0.1);
          border-radius: 8px;
          padding: 5px 8px;
          font-size: 0.75rem;
          color: #d1d5db;
          position: relative;
          min-width: 0;

          .status-label {
            display: flex;
            align-items: center;
            gap: 4px;
            color: rgba(255, 255, 255, 0.6);
            white-space: nowrap;

            .dot {
              width: 6px;
              height: 6px;
              border-radius: 50%;
              flex-shrink: 0;
            }
          }

          .status-value {
            font-weight: 600;
            text-align: right;
            padding-left: 4px;
            flex: 1;
            min-width: 0;
          }
        }
      }

      .battery-section {
        display: flex;
        flex-direction: column;
        gap: 6px;
        background: rgba(15, 23, 42, 0.45);
        border: 1px solid rgba(120, 152, 227, 0.1);
        border-radius: 8px;
        padding: 8px 10px;

        .battery-header {
          display: flex;
          justify-content: space-between;
          align-items: center;

          .battery-title {
            font-size: 0.72rem;
            color: rgba(255, 255, 255, 0.6);
            font-weight: 500;
          }

          .battery-details {
            display: flex;
            align-items: center;
            gap: 6px;

            .charge-status-tag {
              font-size: 0.65rem;
              color: rgba(255, 255, 255, 0.4);
              background: rgba(255, 255, 255, 0.05);
              padding: 1px 4px;
              border-radius: 4px;

              &.charging-pulse {
                color: #10b981;
                background: rgba(16, 185, 129, 0.15);
                animation: pulse-glow 1.5s infinite;
              }
            }

            .battery-number {
              font-size: 0.8rem;
              font-weight: 700;
            }
          }
        }

        .battery-progress-wrapper {
          background: rgba(255, 255, 255, 0.08);
          border-radius: 4px;
          height: 5px;
          overflow: hidden;
          width: 100%;

          .battery-progress-bar {
            height: 100%;
            border-radius: 4px;
            transition: width 0.5s ease-out;
          }
        }
      }
    }

    .card-footer {
      display: flex;
      justify-content: center;
      margin-top: 2px;

      .cockpit-btn {
        width: 100%;
        background: linear-gradient(135deg, rgba(59, 130, 246, 0.8), rgba(29, 78, 216, 0.8));
        border: 1px solid rgba(120, 152, 227, 0.4);
        padding: 7px 12px;
        border-radius: 8px;
        color: #fff;
        font-size: 0.75rem;
        font-weight: 600;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
        cursor: pointer;
        transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
        box-shadow: 0 2px 6px rgba(59, 130, 246, 0.25);

        &:hover {
          background: linear-gradient(135deg, rgba(59, 130, 246, 0.95), rgba(29, 78, 216, 0.95));
          box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4), 0 0 8px rgba(120, 152, 227, 0.3);
          transform: scale(1.02);
        }

        &:active {
          transform: scale(0.98);
        }

        .btn-icon {
          font-size: 0.85rem;
        }
      }
    }
  }

  .hms-badge-container {
    position: absolute;
    right: -4px;
    top: -4px;
    z-index: 10;
  }

  .hms-glow-badge {
    width: 15px;
    height: 15px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 9px;
    font-weight: 700;
    color: #fff;
    cursor: pointer;
    box-shadow: 0 0 6px rgba(0,0,0,0.3);

    &.hms-caution {
      background: #f59e0b;
      box-shadow: 0 0 8px rgba(245, 158, 11, 0.6);
      animation: blink 1.2s infinite;
    }

    &.hms-warn {
      background: #ef4444;
      box-shadow: 0 0 8px rgba(239, 68, 68, 0.7);
      animation: blink 0.8s infinite;
    }

    &.hms-notice {
      background: #10b981;
      box-shadow: 0 0 8px rgba(16, 185, 129, 0.6);
    }
  }

  @keyframes pulse-glow {
    0% { opacity: 0.8; }
    50% { opacity: 1; text-shadow: 0 0 4px rgba(16, 185, 129, 0.6); }
    100% { opacity: 0.8; }
  }
}

.middle-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.right-section {
  flex: 1;
  background-color: #f0f0f0;
  position: relative;
}

.middle-top {
  flex: 2;
  background-color: #e0e0e0;
}

.middle-bottom {
  flex: 1;
  display: flex;
  gap: 0.5rem;
}

.bottom-left {
  flex: 1;
  background-color: #d0d0d0;
}

.bottom-right {
  flex: 1;
  background-color: #c0c0c0;
}

.project-tsa-wrapper> :first-child {
  height: 50px;
  line-height: 50px;
  align-items: center;
  border-bottom: 1px solid #4f4f4f;
}

.project-tsa-wrapper {
  height: 100%;

  .scrollbar {
    overflow: auto;
  }

  ::-webkit-scrollbar {
    display: none;
  }
}

.ant-collapse>.ant-collapse-item>.ant-collapse-header {
  color: white;
  border: 0;
  padding-left: 14px;
}

.text-hidden {
  overflow: hidden !important;
  text-overflow: ellipsis !important;
  white-space: nowrap;
  -o-text-overflow: ellipsis;
}

.font-bold {
  font-weight: 700;
}

.battery-slide {
  width: 100%;

  .capacity-percent {
    background: #00ee8b;
  }

  .return-home {
    background: #ff9f0a;
  }

  .landing {
    background: #f5222d;
  }

  .battery {
    background: white;
    border-radius: 1px;
    width: 8px;
    height: 4px;
    margin-top: -3px;
  }
}

.battery-slide>div {
  position: relative;
  margin-top: -2px;
  min-height: 2px;
  border-radius: 2px;
  white-space: nowrap;
}

.disable {
  cursor: not-allowed;
}

.notice-blink {
  background: green;
  animation: blink 500ms infinite;
}

.caution-blink {
  background: orange;
  animation: blink 500ms infinite;
}

.warn-blink {
  background: red;
  animation: blink 500ms infinite;
}

.notice {
  background: green;
  overflow: hidden;
  cursor: pointer;
}

.caution {
  background: orange;
  cursor: pointer;
  overflow: hidden;
}

.warn {
  background: red;
  cursor: pointer;
  overflow: hidden;
}

.word-loop {
  white-space: nowrap;
  display: inline-block;
  animation: 10s loop linear infinite normal;
}

@keyframes blink {
  from {
    opacity: 1;
  }

  50% {
    opacity: 0.35;
  }

  to {
    opacity: 1;
  }
}

@keyframes loop {
  0% {
    transform: translateX(20px);
    -webkit-transform: translateX(20px);
  }

  100% {
    transform: translateX(-100%);
    -webkit-transform: translateX(-100%);
  }
}

// 无人机电量显示样式
.drone-battery {
  font-size: 0.85rem;
  white-space: nowrap;
  padding: 0 8px;
}

// 充电状态样式
.charge-state {
  font-size: 0.75rem;
  white-space: nowrap;
  padding: 0 4px;
}

// 充电中闪烁动画
.charging-blink {
  animation: blink 1s infinite;
}

// 分隔符样式
.battery-separator {
  color: #4f5f6f;
  margin: 0 2px;
  font-size: 0.75rem;
}

// 电量行样式
.battery-row {
  .icon {
    font-size: 0.85rem;
    line-height: 1.875rem;
  }
}

// ========== 分屏面板样式 ==========
.split-panel {
  box-sizing: border-box;
  transition: all 0.3s ease;

  &:hover {
    border-color: #3b82f6;
  }

  // 视频容器
  video {
    object-fit: contain;
    background: #000;
  }
}

// 分屏布局容器
.split-screen-container {
  display: flex;
  flex-wrap: wrap;
  width: 100%;
  height: 100%;
  background: #111827;
}

// 画中画视频窗口
.pip-video {
  position: absolute;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
  transition: all 0.3s ease;
  cursor: move;

  &:hover {
    transform: scale(1.02);
    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.6);
  }
}

// 分屏播放控制条动画
.split-panel-controls {
  opacity: 0;
  transition: opacity 0.3s ease;
}

.split-panel:hover .split-panel-controls {
  opacity: 1;
}

// ========== HMS Cyber-Tech Popover & Cards ==========
.hms-popover-custom {
  .ant-popover-inner {
    background: rgba(15, 23, 42, 0.95) !important;
    backdrop-filter: blur(16px) !important;
    -webkit-backdrop-filter: blur(16px) !important;
    border: 1px solid rgba(255, 255, 255, 0.12) !important;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.6) !important;
    border-radius: 10px !important;
    padding: 8px !important;
  }
  .ant-popover-inner-content {
    padding: 0 !important;
    color: #fff !important;
    background: transparent !important;
  }
  .ant-popover-arrow {
    display: none !important;
  }
}

.hms-collapse-container {
  max-height: 290px;
  overflow-y: auto;
  padding: 4px;
  
  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-track {
    background: rgba(255, 255, 255, 0.03);
    border-radius: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.15);
    border-radius: 4px;
    &:hover {
      background: rgba(255, 255, 255, 0.3);
    }
  }
}

.hms-panel-card {
  margin-bottom: 8px !important;
  border-radius: 6px !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
  background: rgba(30, 41, 59, 0.4) !important;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1) !important;
  overflow: hidden !important;

  &:hover {
    background: rgba(30, 41, 59, 0.7) !important;
    border-color: rgba(255, 255, 255, 0.18) !important;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35);
  }

  .ant-collapse-header {
    padding: 8px 12px !important;
    color: #fff !important;
    align-items: center !important;
  }

  .ant-collapse-content {
    background: rgba(15, 23, 42, 0.6) !important;
    border-top: 1px solid rgba(255, 255, 255, 0.05) !important;
    color: rgba(255, 255, 255, 0.85) !important;
  }
  
  .ant-collapse-content-box {
    padding: 8px 12px !important;
  }

  &.hms-notice {
    border-left: 3px solid #10b981 !important;
    .hms-level-dot {
      background: #10b981;
      box-shadow: 0 0 6px #10b981;
    }
    .hms-message-text {
      color: rgba(255, 255, 255, 0.95);
    }
  }

  &.hms-caution {
    border-left: 3px solid #f59e0b !important;
    .hms-level-dot {
      background: #f59e0b;
      box-shadow: 0 0 6px #f59e0b;
    }
    .hms-message-text {
      color: #f59e0b;
      font-weight: 500;
    }
  }

  &.hms-warn {
    border-left: 3px solid #ef4444 !important;
    .hms-level-dot {
      background: #ef4444;
      box-shadow: 0 0 6px #ef4444;
      animation: hms-pulse 1.5s infinite;
    }
    .hms-message-text {
      color: #ef4444;
      font-weight: 600;
    }
  }
}

.hms-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 8px;
}

.hms-header-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.hms-level-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.hms-message-text {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  text-align: left;
}

.hms-header-arrow {
  color: rgba(255, 255, 255, 0.45);
  font-size: 10px;
  display: flex;
  align-items: center;
  transition: transform 0.2s ease;
}

.hms-panel-detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  text-align: left;

  .detail-row {
    display: flex;
    align-items: flex-start;
    
    .detail-label {
      color: rgba(255, 255, 255, 0.45);
      flex-shrink: 0;
      width: 42px;
    }

    .detail-value {
      color: rgba(255, 255, 255, 0.85);
      word-break: break-all;
      
      &.code-font {
        font-family: monospace;
        color: #60a5fa;
        background: rgba(96, 165, 250, 0.1);
        padding: 1px 4px;
        border-radius: 3px;
      }
    }
  }
}

@keyframes hms-pulse {
  0% { opacity: 0.6; }
  50% { opacity: 1; }
  100% { opacity: 0.6; }
}

// 机场在线/离线统计栏
.dock-stats-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(8, 20, 48, 0.45);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(120, 152, 227, 0.2);
  border-radius: 8px;
  padding: 6px 12px;
  margin: 0 10px 10px;
  width: calc(100% - 20px);
  font-size: 0.75rem;
  color: rgba(224, 239, 255, 0.85);
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.15), inset 0 1px 0 rgba(255, 255, 255, 0.05);

  .stat-item {
    display: flex;
    align-items: center;
    gap: 4px;

    .stat-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }

    .stat-label {
      color: rgba(255, 255, 255, 0.55);
    }

    .stat-value {
      font-weight: 700;
    }
  }

  .stat-divider {
    width: 1px;
    height: 12px;
    background: rgba(120, 152, 227, 0.2);
  }
}
</style>
