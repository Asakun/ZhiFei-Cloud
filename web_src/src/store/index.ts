import { InjectionKey } from 'vue'
import { ActionTree, createStore, GetterTree, MutationTree, Store, StoreOptions, useStore } from 'vuex'
import { EDeviceTypeName } from '../types'
import { Device, DeviceHms, DeviceOsd, DeviceStatus, DockOsd, GatewayOsd, OSDVisible } from '../types/device'
import { getLayers } from '@/api/layer'
import { LayerType } from '@/types/mapLayer'
import { WaylineFile } from '@/types/wayline'
import { DevicesCmdExecuteInfo } from '@/types/device-cmd'
import { FlightAreaStatus } from '../api/flight-area'

const MAX_HMS_COUNT_PER_DEVICE = 200
const MAX_CMD_EXECUTE_INFO_PER_DEVICE = 100

function getHmsKey(hms: DeviceHms): string {
  return hms.hms_id || [hms.sn, hms.key, hms.module, hms.create_time].join('|')
}

const initStateFunc = () => ({
  webTitle: '无人机智飞控制平台', // 平台标题
  Layers: [
    {
      name: 'default',
      id: '',
      is_distributed: true,
      elements: [],
      is_check: false,
      is_select: false,
      type: 1
    },
    {
      name: 'share',
      id: '',
      is_distributed: true,
      elements: [],
      is_check: false,
      is_select: false,
      type: 2
    }
  ],
  layerBaseInfo: {} as {
    [key: string]: string
  },
  drawVisible: false,
  livestreamOthersVisible: false,
  livestreamAgoraVisible: false,
  coverMap: {} as {
    [key: string]: any[]
  },
  wsEvent: {
    mapElementCreat: {},
    mapElementUpdate: {},
    mapElementDelete: {}
  },
  deviceStatusEvent: {
    deviceOnline: {} as DeviceStatus,
    deviceOffline: {} as DeviceStatus
  },
  markerInfo: {
    coverMap: {} as {
      [sn: string]: any
    },
    pathMap: {} as {
      [sn: string]: any[]
    }
  },
  deviceState: {
    // remote controller, dock
    gatewayInfo: {} as {
      [sn: string]: GatewayOsd
    },
    // drone
    deviceInfo: {} as {
      [sn: string]: DeviceOsd
    },
    dockInfo: {} as {
      [sn: string]: DockOsd
    },
    currentSn: '',
    currentType: -1,
    latestOsdUpdate: {
      sn: '',
      type: -1,
      revision: 0
    }
  },
  osdVisible: { // osd 显示设备相关信息
    sn: '',
    callsign: '',
    model: '',
    visible: false,
    gateway_sn: '',
    is_dock: false,
    payloads: null
  } as OSDVisible,
  waylineInfo: {

  } as WaylineFile,
  dockInfo: {

  } as Device,
  hmsInfo: {} as {
    [sn: string]: DeviceHms[]
  },
  // 机场指令执行状态信息
  devicesCmdExecuteInfo: {
  } as DevicesCmdExecuteInfo,
  mqttState: null as any, // mqtt 实例
  clientId: '', // mqtt 连接 唯一客户端id
  liveStreamSource: {
    key: '',
    value: ''
  }
})

export type RootStateType = ReturnType<typeof initStateFunc>

const getters: GetterTree<RootStateType, RootStateType> = {
}
const mutations: MutationTree<RootStateType> = {
  SET_WEB_TITLE(state, title: string) {
    state.webTitle = title
  },
  SET_LAYER_INFO(state, info) {
    state.Layers = info
  },
  ADD_LAYER_ELEMENT(state, info) {
    const layer = state.Layers.find(item => item.id.includes(info.layerId))
    if (layer?.elements) {
      layer.elements.push(info.element as never)
    }
  },
  SET_COVER_MAP_ITEM(state, info) {
    state.coverMap[info.id] = info.value
  },
  SET_DEVICE_INFO(state, info) {
    state.deviceState.deviceInfo[info.sn] = info.host
    state.deviceState.latestOsdUpdate = {
      sn: info.sn,
      type: EDeviceTypeName.Aircraft,
      revision: state.deviceState.latestOsdUpdate.revision + 1
    }
  },
  SET_GATEWAY_INFO(state, info) {
    state.deviceState.gatewayInfo[info.sn] = info.host
    state.deviceState.latestOsdUpdate = {
      sn: info.sn,
      type: EDeviceTypeName.Gateway,
      revision: state.deviceState.latestOsdUpdate.revision + 1
    }
  },
  SET_DOCK_INFO(state, info) {
    if (!info || !info.sn || !info.host || Object.keys(info.host).length === 0) {
      return
    }
    if (!state.deviceState.dockInfo[info.sn]) {
      state.deviceState.dockInfo[info.sn] = {} as DockOsd
    }
    const dock = { ...state.deviceState.dockInfo[info.sn] }
    if (info.host.mode_code !== undefined) {
      dock.basic_osd = info.host
    } else if (info.host.wireless_link) {
      dock.link_osd = info.host
    } else if (info.host.job_number !== undefined) {
      dock.work_osd = info.host
    } else {
      dock.basic_osd = { ...(dock.basic_osd || {}), ...info.host }
    }
    state.deviceState.dockInfo[info.sn] = dock
    state.deviceState.latestOsdUpdate = {
      sn: info.sn,
      type: EDeviceTypeName.Dock,
      revision: state.deviceState.latestOsdUpdate.revision + 1
    }
  },
  SET_DRAW_VISIBLE_INFO(state, bool) {
    state.drawVisible = bool
  },
  SET_LIVESTREAM_OTHERS_VISIBLE(state, bool) {
    state.livestreamOthersVisible = bool
  },
  SET_LIVESTREAM_AGORA_VISIBLE(state, bool) {
    state.livestreamAgoraVisible = bool
  },
  SET_MAP_ELEMENT_CREATE(state, info) {
    state.wsEvent.mapElementCreat = info
  },
  SET_MAP_ELEMENT_UPDATE(state, info) {
    state.wsEvent.mapElementUpdate = info
  },
  SET_MAP_ELEMENT_DELETE(state, info) {
    state.wsEvent.mapElementDelete = info
  },
  CLEAR_MAP_ELEMENT_CREATE(state) {
    state.wsEvent.mapElementCreat = {}
  },
  CLEAR_MAP_ELEMENT_UPDATE(state) {
    state.wsEvent.mapElementUpdate = {}
  },
  CLEAR_MAP_ELEMENT_DELETE(state) {
    state.wsEvent.mapElementDelete = {}
  },
  SET_DEVICE_ONLINE(state, info) {
    state.deviceStatusEvent.deviceOnline = info
  },
  CLEAR_DEVICE_ONLINE(state) {
    state.deviceStatusEvent.deviceOnline = {} as DeviceStatus
  },
  CLEAR_DEVICE_OFFLINE(state) {
    state.deviceStatusEvent.deviceOffline = {} as DeviceStatus
  },
  SET_DEVICE_OFFLINE(state, info) {
    state.deviceStatusEvent.deviceOffline = info
    delete state.deviceState.gatewayInfo[info.sn]
    delete state.deviceState.deviceInfo[info.sn]
    delete state.deviceState.dockInfo[info.sn]
    delete state.hmsInfo[info.sn]
    delete state.devicesCmdExecuteInfo[info.sn]
    // delete state.markerInfo.coverMap[info.sn]
    // delete state.markerInfo.pathMap[info.sn]
  },
  SET_OSD_VISIBLE_INFO(state, info) {
    state.osdVisible = info
  },
  SET_SELECT_WAYLINE_INFO(state, info) {
    state.waylineInfo = info
  },
  SET_SELECT_DOCK_INFO(state, info) {
    state.dockInfo = info
  },
  SET_DEVICE_HMS_INFO(state, info) {
    if (!info?.sn || !info?.host) {
      return
    }

    const incomingList: DeviceHms[] = Array.isArray(info.host) ? info.host : [info.host]
    const hmsByKey = new Map<string, DeviceHms>()
    const hmsList = [...incomingList, ...(state.hmsInfo[info.sn] ?? [])]
    hmsList.forEach((hms: DeviceHms) => {
      if (!hms) return
      if (!hms.sn) hms.sn = info.sn
      const key = getHmsKey(hms)
      const current = hmsByKey.get(key)
      if (!current || String(hms.update_time ?? '') >= String(current.update_time ?? '')) {
        hmsByKey.set(key, hms)
      }
    })
    state.hmsInfo[info.sn] = Array.from(hmsByKey.values())
      .sort((a, b) => String(b.update_time || b.create_time || '').localeCompare(String(a.update_time || a.create_time || '')))
      .slice(0, MAX_HMS_COUNT_PER_DEVICE)
  },
  SET_DEVICES_CMD_EXECUTE_INFO(state, info) { // 保存设备指令ws消息推送
    if (!info.sn) {
      return
    }
    const commandList = state.devicesCmdExecuteInfo[info.sn] ?? []
    const index = commandList.findIndex(cmdExecuteInfo => cmdExecuteInfo.biz_code === info.biz_code)
    if (index >= 0) {
      if (commandList[index].timestamp > info.timestamp) {
        return
      }
      commandList[index] = info
    } else {
      commandList.push(info)
    }
    commandList.sort((a, b) => Number(b.timestamp ?? 0) - Number(a.timestamp ?? 0))
    state.devicesCmdExecuteInfo[info.sn] = commandList.slice(0, MAX_CMD_EXECUTE_INFO_PER_DEVICE)
  },
  SET_MQTT_STATE(state, mqttState) {
    state.mqttState = mqttState
  },
  SET_CLIENT_ID(state, clientId) {
    state.clientId = clientId
  },
  SET_LIVE_STREAM_SOURCE(state, source) {
    state.liveStreamSource = {
      key: source.key,
      value: source.value
    }
  }
}

const actions: ActionTree<RootStateType, RootStateType> = {
  async getAllElement({ commit }) {
    const result = await getLayers({
      groupId: '',
      isDistributed: true
    })
    commit('SET_LAYER_INFO', result.data?.list)
    console.log(result)
  },
  updateElement({ state }, content: { type: 'is_check' | 'is_select', id: string, bool: boolean }) {
    const key = content.id.replaceAll('resource__', '')
    const type = content.type
    const layers = state.Layers
    const layer = layers.find(item => item.id === key)
    if (layer) {
      layer[type] = content.bool
    }
  },
  setLayerInfo({ state }, layers) {
    // const layers = state.Layers
    const obj: {
      [key: string]: string
    } = {}
    layers.forEach(layer => {
      if (layer.type === LayerType.Default) {
        obj.default = layer.id
      } else {
        if (layer.type === LayerType.Share) {
          obj.share = layer.id
        }
      }
    })
    state.layerBaseInfo = obj
    console.log('state.layerBaseInfo', state.layerBaseInfo)
  },
  getLayerInfo({ state }, id: string) {
    return state.layerBaseInfo[id]
  }
}

const storeOptions: StoreOptions<RootStateType> = {
  state: initStateFunc,
  getters,
  mutations,
  actions
}

const rootStore = createStore(storeOptions)

export default rootStore

export const storeKey: InjectionKey<Store<RootStateType>> = Symbol('')

type AllStateStoreTypes = RootStateType & {
  // moduleName: moduleType
}

export function useMyStore<T = AllStateStoreTypes>() {
  return useStore<T>(storeKey)
}
