<script setup lang="ts">
import { useEventEmitter, useLocalStorageState, useTimeout } from "vue-hooks-plus";
import { onMounted, ref, reactive, computed, watch } from "vue";
import { message } from 'ant-design-vue';
import { PAYLOAD_TYPE, DRONE_TYPE } from '@/types/device';

import NewWayLineDjiCesium from "@/components/NewWayLineDjiCesium.vue";

const router = useRouter()

// --- 外部交互 ---
const eventBack = useEventEmitter({ global: true })
const back = () => {
  router.back()
  eventBack.emit('back')
}

// --- 状态管理 ---
const isModalVisible = ref(false)
const showCesiumMap = ref(false) // 控制 Cesium 组件是否加载
const currentStep = ref<'create' | 'settings'>('create') // 视图状态：新建 -> 设置

// --- 编辑模式状态 ---
const isEditMode = ref(false) // 是否为编辑模式
const editWaylineId = ref('') // 正在编辑的航线ID
const editWaylineData = ref<any>(null) // 编辑航线的原始数据

// --- STEP 1: 新建航线数据 ---
const createFormData = reactive({
  routeType: 'waypoint',
  aircraftSeries: DRONE_TYPE.M30,
  aircraftModel: PAYLOAD_TYPE.M30D,
  routeName: '',
  takeoffAirport: '' // 存储选中的机场 sn
})

// --- STEP 2: 全局设置数据 ---
const settingsData = reactive<Record<string, any>>({
  photoType: ['wide'], // wide, zoom, ir
  smartLowLight: false,
  takeoffMode: 'vertical', // vertical, oblique
  takeoffHeight: 50,
  altitudeMode: 'relative', // asl, relative, terrain
  flightHeight: 100,
  globalSpeed: 15,
  isAdvancedOpen: true, // 高级设置折叠状态
  // 高级选项
  takeoffSpeed: 15,
  waypointType: 'toPointAndStopWithDiscontinuityCurvature',
  yawMode: 'followWayline',
  gimbalMode: 'manual',
  finishAction: 'goHome',
  // 新增参数
  exitOnRCLost: 'goBack',
  gimbalPitchMode: 'usePointSetting'
})

// --- 最终的配置数据（传递给子组件） ---
const finalConfig = ref<any>(null)

// --- 配置项数据 (保持不变) ---
const routeGroups = [
  { title: '巡逻巡检航线', items: [{ id: 'waypoint', label: '航点航线', icon: 'waypoint' }] },

]
const aircrafts = [
  { id: DRONE_TYPE.M30, label: 'Matrice 30/Matrice 30T', disabled: false },
  { id: DRONE_TYPE.M3D, label: 'Matrice 3D/Matrice 3TD', disabled: false },
  { id: DRONE_TYPE.M4D, label: 'Matrice 4D/Matrice 4TD', disabled: false },
]

// 根据选择的飞行器系列动态返回型号
const models = computed(() => {
  const modelMap: Record<number, Array<{ id: number, label: string, payloadType: number }>> = {
    [DRONE_TYPE.M30]: [
      { id: DRONE_TYPE.M30, label: 'Matrice 30', payloadType: PAYLOAD_TYPE.M30D },
      { id: DRONE_TYPE.M30, label: 'Matrice 30T', payloadType: PAYLOAD_TYPE.M30T }
    ],
    [DRONE_TYPE.M3D]: [
      { id: DRONE_TYPE.M3D, label: 'Matrice 3D', payloadType: PAYLOAD_TYPE.M3D },
      { id: DRONE_TYPE.M3D, label: 'Matrice 3TD', payloadType: PAYLOAD_TYPE.M3TD }
    ],
    [DRONE_TYPE.M4D]: [
      { id: DRONE_TYPE.M4D, label: 'Matrice 4D', payloadType: PAYLOAD_TYPE.M4D },
      { id: DRONE_TYPE.M4D, label: 'Matrice 4TD', payloadType: PAYLOAD_TYPE.M4TD }
    ]
  }
  return modelMap[createFormData.aircraftSeries] || []
})

// 监听飞行器系列变化，重置型号为第一个选项
watch(() => createFormData.aircraftSeries, () => {
  if (models.value.length > 0) {
    createFormData.aircraftModel = models.value[0].payloadType
  }
})

// --- 逻辑方法 ---

// 更新配置数据
const updateConfig = () => {
  // 1. drone_type 直接使用 aircraftSeries (已经是 DRONE_TYPE 枚举值)
  const droneType = createFormData.aircraftSeries;

  // 2. 确定 sub_drone_type (根据 payload_type 判断是否为T系列)
  const payloadType = createFormData.aircraftModel;
  let subDroneType = 0;
  // T系列的 payload_type: M30T=53, M3TD=81, M4TD=99
  if (payloadType === PAYLOAD_TYPE.M30T || payloadType === PAYLOAD_TYPE.M3TD || payloadType === PAYLOAD_TYPE.M4TD) {
    subDroneType = 1;
  }

  // 获取选中机场的坐标作为起飞点
  let takeOffRefPoint = "";
  if (createFormData.takeoffAirport && dockMap.value) {
    const selectedDock = dockMap.value.find(d => d.sn === createFormData.takeoffAirport);
    if (selectedDock) {
      takeOffRefPoint = `${selectedDock.latitude},${selectedDock.longitude},${selectedDock.height}`;
    }
  }

  // 3. 构造符合接口要求的数据结构
  finalConfig.value = {
    name: createFormData.routeName, // 航线名称
    template_type: createFormData.routeType,
    take_off_ref_point: takeOffRefPoint, // 从选中的机场获取
    drone_type: droneType,
    sub_drone_type: subDroneType,
    payload_type: payloadType,
    payload_sub_type: 0,
    payload_position: 0,
    image_format: settingsData.photoType.join(','), // 转换为逗号分隔字符串
    finish_action: settingsData.finishAction, // 示例映射
    exit_on_rc_lost_action: settingsData.exitOnRCLost,
    global_height: settingsData.flightHeight,
    auto_flight_speed: settingsData.globalSpeed,
    waypoint_heading_req: {
      waypoint_heading_mode: settingsData.yawMode
    },
    waypoint_turn_req: {
      waypoint_turn_mode: settingsData.waypointType
    },
    gimbal_pitch_mode: settingsData.gimbalPitchMode,
    // start_action_list: [
    //   {
    //     action_index: 0,
    //     action_func: 'gimbalRotate',
    //     gimbal_yaw_rotate_angle: -90
    //   },
    //   {
    //     action_index: 1,
    //     action_func: 'hover',
    //     hover_time: 30
    //   }
    // ],
    route_point_list: [] // 将在地图中生成
  }
}

// 航线名称验证正则：不允许包含特殊字符 <>:"/|?*._\
const waylineNameRegex = /^[^<>:"/|?*._\\]+$/;
const isWaylineNameValid = computed(() => {
  if (!createFormData.routeName || createFormData.routeName.trim() === '') {
    return false;
  }
  return waylineNameRegex.test(createFormData.routeName);
});

// 进入第二步 (修改为：关闭弹窗，显示地图，等待设置起飞点)
const handleCreateConfirm = () => {
  // 验证起飞机场必填
  if (!createFormData.takeoffAirport) {
    message.error('请选择起飞机场');
    return;
  }
  // 验证航线名称必填且格式正确
  if (!createFormData.routeName || createFormData.routeName.trim() === '') {
    message.error('请输入航线名称');
    return;
  }
  if (!isWaylineNameValid.value) {
    message.error('航线名称不能包含特殊字符 <>:"/|?*._\\');
    return;
  }
  updateConfig();
  showCesiumMap.value = true;
  currentStep.value = 'settings';
}

// 起飞点设置完成回调
const onTakeoffPointSet = () => {
  currentStep.value = 'settings';
  isModalVisible.value = true;
}

// 最终完成
const handleFinalConfirm = () => {
  console.log('配置完成')
  updateConfig();

  // 关闭弹窗
  isModalVisible.value = false

  // 重置步骤状态
  setTimeout(() => { currentStep.value = 'create' }, 300)
}

const handleCancel = () => {
  isModalVisible.value = false
  router.back()
  setTimeout(() => { currentStep.value = 'create' }, 300)
}

// 简单的数值调节函数
const adjustValue = (key: any, amount: number) => {
  // @ts-ignore
  let val = settingsData[key] + amount
  if (key === 'globalSpeed') {
    val = Math.max(1, Math.min(15, val))
  } else {
    val = Math.max(0, val)
  }
  // @ts-ignore
  settingsData[key] = val
}

const togglePhotoType = (type: string) => {
  const index = settingsData.photoType.indexOf(type);
  if (index > -1) {
    // 至少保留一个
    if (settingsData.photoType.length > 1) {
      settingsData.photoType.splice(index, 1);
    }
  } else {
    settingsData.photoType.push(type);
  }
}

// 监听设置数据变化，自动更新配置
watch([() => settingsData, () => createFormData], () => {
  if (finalConfig.value) {
    updateConfig();
  }
}, { deep: true })

const [dockMap] = useLocalStorageState<Array<{ sn: string, callsign: string, model: string, latitude: number, longitude: number, height: number }>>('dockMap')

// 计算起飞点对象
const takeoffPoint = computed(() => {
  if (createFormData.takeoffAirport && dockMap.value) {
    const selectedDock = dockMap.value.find(d => d.sn === createFormData.takeoffAirport);
    if (selectedDock) {
      return {
        longitude: selectedDock.longitude,
        latitude: selectedDock.latitude,
        height: selectedDock.height
      };
    }
  }
  return null;
})

onMounted(() => {
  // 检查是否有编辑数据
  const savedEditData = localStorage.getItem('editWaylineData')
  if (savedEditData) {
    try {
      const editData = JSON.parse(savedEditData)
      if (editData.isEditMode && editData.waylineData) {
        // 设置编辑模式
        isEditMode.value = true
        editWaylineId.value = editData.waylineId
        editWaylineData.value = editData.waylineData
        createFormData.routeName = editData.waylineName

        // 从编辑数据中恢复配置
        const waylineData = editData.waylineData
        if (waylineData.drone_type !== undefined) {
          createFormData.aircraftSeries = waylineData.drone_type
        }
        if (waylineData.payload_type !== undefined) {
          createFormData.aircraftModel = waylineData.payload_type
        }

        // 恢复全局设置
        if (waylineData.global_height !== undefined) {
          settingsData.flightHeight = waylineData.global_height
        }
        if (waylineData.auto_flight_speed !== undefined) {
          settingsData.globalSpeed = waylineData.auto_flight_speed
        }
        if (waylineData.finish_action) {
          settingsData.finishAction = waylineData.finish_action
        }
        if (waylineData.exit_on_rc_lost_action) {
          settingsData.exitOnRCLost = waylineData.exit_on_rc_lost_action
        }
        if (waylineData.waypoint_heading_req?.waypoint_heading_mode) {
          settingsData.yawMode = waylineData.waypoint_heading_req.waypoint_heading_mode
        }
        if (waylineData.waypoint_turn_req?.waypoint_turn_mode) {
          settingsData.waypointType = waylineData.waypoint_turn_req.waypoint_turn_mode
        }
        if (waylineData.gimbal_pitch_mode) {
          settingsData.gimbalPitchMode = waylineData.gimbal_pitch_mode
        }

        // 从起飞点坐标中尝试匹配机场
        if (waylineData.take_off_ref_point && dockMap.value) {
          const [takeoffLat, takeoffLng] = waylineData.take_off_ref_point.split(',').map(Number)
          const matchedDock = dockMap.value.find(d =>
            Math.abs(d.latitude - takeoffLat) < 0.0001 &&
            Math.abs(d.longitude - takeoffLng) < 0.0001
          )
          if (matchedDock) {
            createFormData.takeoffAirport = matchedDock.sn
          }
        }

        // 清除 localStorage 中的编辑数据
        localStorage.removeItem('editWaylineData')

        // 更新配置并直接跳转到地图编辑界面
        updateConfig()
        isModalVisible.value = false // 不显示弹窗，直接进入地图编辑

        useTimeout(() => {
          showCesiumMap.value = true
        }, 1000)
        currentStep.value = 'settings'

        return
      }
    } catch (e) {
      console.error('解析编辑数据失败:', e)
      localStorage.removeItem('editWaylineData')
    }
  }

  // 正常新建模式
  isModalVisible.value = true
})

const SvgIcon = (pathInfo: string) => ({ render: () => h('svg', { xmlns: 'http://www.w3.org/2000/svg', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.5, strokeLinecap: 'round', strokeLinejoin: 'round' }, [h('path', { d: pathInfo })]) })
const icons = { waypoint: 'M3 12h18M3 6h18M3 18h18 M8 6l4 6-4 6' } // 简写
function getIcon(name: string) { return SvgIcon(icons.waypoint) }
</script>

<template>
  <!-- <div class="p-4 relative z-10">
    <a-button @click="back">返回上一级</a-button>
    <a-button class="ml-2" type="primary" @click="isModalVisible = true">打开航线弹窗</a-button>
  </div> -->
  <NewWayLineDjiCesium class="fixed inset-0 z-0" v-if="showCesiumMap" :config="finalConfig" :takeoffPoint="takeoffPoint"
    :waylineName="createFormData.routeName" :isEditMode="isEditMode" :editWaylineId="editWaylineId"
    :editWaylineData="editWaylineData" :dock_sn="createFormData.takeoffAirport" @close="showCesiumMap = false" />

  <!-- 悬浮编辑按钮 -->
  <div v-if="showCesiumMap && !isModalVisible" class="fixed top-4 left-1/2 -translate-x-1/2 z-20 flex gap-2">
    <button @click="isModalVisible = true"
      class="bg-blue-600 text-white px-4 py-2 rounded shadow-lg hover:bg-blue-500 transition-colors flex items-center gap-2">
      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none"
        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 20h9" />
        <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
      </svg>
      编辑航线设置
    </button>
  </div>
  <!-- 遮罩层 -->
  <div v-if="isModalVisible"
    class="fixed inset-0 bg-black/70 backdrop-blur-[2px] z-50 flex items-center justify-center font-sans select-none">

    <!-- 弹窗容器 -->
    <div
      class="bg-[#1e1e1e] rounded-lg shadow-2xl border border-gray-700 flex flex-col text-gray-200 overflow-hidden transition-all duration-300"
      :class="currentStep === 'create' ? 'w-[900px] h-[800px]' :
        'w-[500px] h-[85vh] absolute left-4 top-1/2 -translate-y-1/2'">

      <!-- HEADER: 动态标题 -->
      <div class="h-12 shrink-0 flex items-center justify-between px-4 border-b border-gray-700 bg-[#252525]">
        <div class="font-medium text-lg flex items-center gap-4">
          <span v-if="!showCesiumMap">{{ currentStep === 'create' ? '创建新航线' : '全局设置' }}</span>
          <div v-else class="flex bg-[#121212] rounded p-1">
            <button @click="currentStep = 'create'" class="px-3 py-1 rounded text-sm transition-colors"
              :class="currentStep === 'create' ? 'bg-[#333] text-white shadow' : 'text-gray-400 hover:text-gray-200'">基础信息</button>
            <button @click="currentStep = 'settings'" class="px-3 py-1 rounded text-sm transition-colors"
              :class="currentStep === 'settings' ? 'bg-[#333] text-white shadow' : 'text-gray-400 hover:text-gray-200'">全局设置</button>
          </div>
        </div>
        <!-- 步骤2的右上角重设按钮 -->
        <div v-if="currentStep === 'settings'"
          class="text-xs text-blue-500 cursor-pointer hover:text-blue-400 flex items-center gap-1">
          <svg class="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          重设起飞点
        </div>
      </div>

      <!-- CONTENT: 滚动区域 -->
      <div class="flex-1 overflow-y-auto custom-scrollbar relative">

        <!-- STEP 1: 新建航线 (保持原有布局) -->
        <div v-if="currentStep === 'create'" class="p-6 space-y-5">
          <!-- 航线类型 -->
          <div class="flex gap-4">
            <div v-for="(group, gIndex) in routeGroups" :key="gIndex" class="flex flex-col gap-2">
              <span class="text-xs text-gray-400 pl-1">{{ group.title }}</span>
              <div class="flex gap-3">
                <div v-for="item in group.items" :key="item.id" @click="createFormData.routeType = item.id"
                  class="w-24 h-20 rounded flex flex-col items-center justify-center gap-2 cursor-pointer border transition-all duration-200 hover:bg-[#333]"
                  :class="createFormData.routeType === item.id ? 'border-blue-500 bg-[#2d68aa]/20 text-blue-100' : 'border-[#3a3a3a] bg-[#2d2d2d] text-gray-300'">
                  <component :is="getIcon(item.icon)" class="w-6 h-6 opacity-80"
                    :class="createFormData.routeType === item.id ? 'text-blue-400' : 'text-gray-400'" />
                  <span class="text-xs font-medium">{{ item.label }}</span>
                </div>
              </div>
            </div>
          </div>
          <!-- 飞行器选择 (简化) -->
          <div>
            <div class="text-xs text-gray-400 mb-2 pl-1">选择飞行器</div>
            <div class="grid grid-cols-4 gap-3">
              <button v-for="craft in aircrafts" :key="craft.id" :disabled="craft.disabled"
                @click="createFormData.aircraftSeries = craft.id"
                class="h-12 rounded border text-sm font-medium transition-colors"
                :class="[createFormData.aircraftSeries === craft.id && !craft.disabled ? 'border-blue-500 bg-[#2d68aa]/30 text-white' : 'border-[#3a3a3a] bg-[#333] text-gray-300 hover:border-gray-500', craft.disabled ? 'opacity-30 cursor-not-allowed' : '']">
                {{ craft.label }}
              </button>
            </div>
          </div>
          <!-- 型号选择 -->
          <div v-if="models.length > 0">
            <div class="text-xs text-gray-400 mb-2 pl-1">选择型号</div>
            <div class="grid grid-cols-4 gap-3">
              <button v-for="model in models" :key="model.payloadType"
                @click="createFormData.aircraftModel = model.payloadType"
                class="h-12 rounded border text-sm font-medium transition-colors"
                :class="createFormData.aircraftModel === model.payloadType ? 'border-blue-500 bg-[#2d68aa]/30 text-white' : 'border-[#3a3a3a] bg-[#333] text-gray-300 hover:border-gray-500'">
                {{ model.label }}
              </button>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <div class="text-xs text-gray-400 mb-2 pl-1">选择起飞机场 <span class="text-red-500">*</span></div>
              <div class="relative">
                <select v-model="createFormData.takeoffAirport"
                  class="w-full h-10 bg-[#121212] border rounded px-3 text-sm text-white focus:border-blue-500 focus:outline-none appearance-none"
                  :class="!createFormData.takeoffAirport ? 'border-gray-600' : 'border-green-500'">
                  <option value="" disabled selected>请选择起飞机场</option>
                  <option v-for="dock in dockMap" :key="dock.sn" :value="dock.sn">
                    {{ dock.callsign }} - {{ dock.model }}
                  </option>
                </select>
                <div class="pointer-events-none absolute inset-y-0 right-0 flex items-center px-2 text-gray-400">
                  <svg class="fill-current h-4 w-4" viewBox="0 0 20 20">
                    <path d="M9.293 12.95l.707.707L15.657 8l-1.414-1.414L10 10.828 5.757 6.586 4.343 8z" />
                  </svg>
                </div>
              </div>
            </div>
            <!-- 名称输入 -->
            <div>
              <div class="text-xs text-gray-400 mb-2 pl-1">
                航线名称 <span class="text-red-500">*</span>
                <span v-if="isEditMode" class="text-yellow-500 ml-2">(编辑模式下不可修改)</span>
              </div>
              <input v-model="createFormData.routeName" type="text"
                class="w-full h-10 bg-[#121212] border rounded px-3 text-sm text-white focus:border-blue-500 focus:outline-none"
                :class="[
                  !createFormData.routeName ? 'border-gray-600' : (isWaylineNameValid ? 'border-green-500' : 'border-red-500'),
                  isEditMode ? 'cursor-not-allowed opacity-60' : ''
                ]" :disabled="isEditMode" placeholder="请输入航线名称" />
              <div v-if="createFormData.routeName && !isWaylineNameValid && !isEditMode"
                class="text-red-400 text-xs mt-1 pl-1">
                航线名称不能包含特殊字符 &lt;&gt;:"/|?*._\
              </div>
            </div>
          </div>
        </div>

        <!-- STEP 2: 全局设置 (新界面) -->
        <div v-else class="p-4 space-y-4 text-sm">

          <!-- 1. 拍照设置 -->
          <div class="bg-[#2d2d2d] p-3 rounded border border-[#3a3a3a]">
            <div class="text-xs text-gray-400 mb-2">拍照设置 (多选)</div>
            <div class="flex bg-[#1e1e1e] p-1 rounded mb-3">
              <button v-for="type in ['wide', 'zoom', 'ir']" :key="type" @click="togglePhotoType(type)"
                class="flex-1 py-1 rounded text-xs transition-colors"
                :class="settingsData.photoType.includes(type) ? 'bg-blue-500 text-white shadow' : 'text-gray-400 hover:text-gray-200'">
                {{ type === 'wide' ? '广角照片' : type === 'zoom' ? '变焦照片' : '红外照片' }}
              </button>
            </div>
            <div class="flex justify-between items-center">
              <span class="text-gray-300">智能低光</span>
              <!-- Switch 组件 -->
              <div @click="settingsData.smartLowLight = !settingsData.smartLowLight"
                class="w-10 h-5 rounded-full relative cursor-pointer transition-colors duration-200"
                :class="settingsData.smartLowLight ? 'bg-gray-200' : 'bg-gray-600'">
                <div class="absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-200"
                  :class="settingsData.smartLowLight ? 'translate-x-5' : 'translate-x-0'"></div>
              </div>
            </div>
          </div>

          <!-- 2. 起飞模式 (SVG 图解) -->
          <div class="bg-[#2d2d2d] p-3 rounded border border-[#3a3a3a]">
            <div class="flex bg-[#3a3a3a] rounded mb-3 overflow-hidden">
              <button v-for="mode in ['vertical', 'oblique']" :key="mode" @click="settingsData.takeoffMode = mode"
                class="flex-1 py-1.5 text-xs transition-colors border-r border-[#4a4a4a] last:border-0"
                :class="settingsData.takeoffMode === mode ? 'bg-blue-600 text-white' : 'text-gray-400 hover:bg-[#444]'">
                {{ mode === 'vertical' ? '垂直爬升' : '倾斜爬升' }}
              </button>
            </div>

            <div class="flex gap-2">
              <!-- SVG 示意图区域 -->
              <div
                class="flex-1 relative h-32 border border-[#3a3a3a] rounded bg-[#1e1e1e] flex items-end justify-center pb-2 overflow-hidden">
                <!-- SVG 图形 -->
                <svg viewBox="0 0 200 120" class="w-full h-full absolute inset-0">
                  <!-- 地面 -->
                  <line x1="10" y1="100" x2="190" y2="100" stroke="#666" stroke-width="2" stroke-dasharray="4 2" />
                  <!-- 树 -->
                  <path d="M60 100 V70 M60 70 Q45 80 45 60 Q45 40 60 40 Q75 40 75 60 Q75 80 60 70" stroke="white"
                    fill="none" stroke-width="1.5" />
                  <!-- Home点 -->
                  <circle cx="90" cy="100" r="5" stroke="white" stroke-width="1.5" />
                  <text x="86" y="103" font-size="8" fill="white">H</text>
                  <!-- 垂直爬升线 (黄色) -->
                  <path d="M90 100 V40" stroke="#fbbf24" stroke-width="1.5" stroke-dasharray="3 3"
                    v-if="settingsData.takeoffMode === 'vertical'" />
                  <path d="M90 40 L30 40" stroke="#fbbf24" stroke-width="1.5" stroke-dasharray="3 3"
                    v-if="settingsData.takeoffMode === 'vertical'" />
                  <!-- 倾斜爬升线 (绿色) -->
                  <line x1="90" y1="100" x2="30" y2="40" stroke="#4ade80" stroke-width="1.5" stroke-dasharray="3 3"
                    v-if="settingsData.takeoffMode === 'oblique'" />
                  <!-- 无人机 -->
                  <path d="M25 40 L35 40 M30 35 L30 45" stroke="white" stroke-width="1.5" transform="translate(0,0)" />
                  <path d="M25 35 L35 45 M25 45 L35 35" stroke="white" stroke-width="1" />
                  <!-- 标注线 -->
                  <line x1="100" y1="100" x2="100" y2="60" stroke="#4ade80" stroke-width="1" />
                  <line x1="97" y1="100" x2="103" y2="100" stroke="#4ade80" stroke-width="1" />
                  <line x1="97" y1="60" x2="103" y2="60" stroke="#4ade80" stroke-width="1" />
                  <text x="105" y="80" font-size="8" fill="#aaa">安全起飞高</text>
                  <text x="105" y="90" font-size="8" fill="#aaa">(ALT)</text>
                </svg>
              </div>

              <!-- 数值调节器 (Stack) -->
              <div
                class="w-16 flex flex-col items-center justify-between py-1 bg-[#1e1e1e] rounded border border-[#3a3a3a]">
                <div class="flex flex-col gap-1 w-full px-1">
                  <button @click="adjustValue('takeoffHeight', 100)"
                    class="bg-[#2d2d2d] text-gray-400 text-[10px] py-0.5 rounded hover:bg-[#444]">+100</button>
                  <button @click="adjustValue('takeoffHeight', 10)"
                    class="bg-[#2d2d2d] text-gray-400 text-[10px] py-0.5 rounded hover:bg-[#444]">+10</button>
                </div>
                <div class="text-blue-500 font-bold text-lg leading-none my-1">{{ settingsData.takeoffHeight }} <span
                    class="text-xs font-normal text-gray-400">m</span></div>
                <div class="flex flex-col gap-1 w-full px-1">
                  <button @click="adjustValue('takeoffHeight', -10)"
                    class="bg-[#2d2d2d] text-gray-400 text-[10px] py-0.5 rounded hover:bg-[#444]">-10</button>
                  <button @click="adjustValue('takeoffHeight', -100)"
                    class="bg-[#2d2d2d] text-gray-400 text-[10px] py-0.5 rounded hover:bg-[#444]">-100</button>
                </div>
              </div>
            </div>
          </div>

          <!-- 3. 航点高度模式 -->
          <div class="bg-[#2d2d2d] p-3 rounded border border-[#3a3a3a]">
            <div class="text-xs text-gray-400 mb-2">航点高度模式</div>
            <div class="flex bg-[#3a3a3a] rounded mb-3 overflow-hidden">
              <button v-for="mode in ['asl', 'relative', 'terrain']" :key="mode"
                @click="settingsData.altitudeMode = mode"
                class="flex-1 py-1.5 text-[10px] transition-colors border-r border-[#4a4a4a] last:border-0 px-1 whitespace-nowrap"
                :class="settingsData.altitudeMode === mode ? 'bg-blue-600 text-white' : 'text-gray-400 hover:bg-[#444]'">
                {{ mode === 'asl' ? '海拔高度' : mode === 'relative' ? '相对起飞点高度' : '相对地形高度' }}
              </button>
            </div>

            <div class="flex gap-2">
              <!-- SVG 地形示意图 -->
              <div
                class="flex-1 relative h-28 border border-[#3a3a3a] rounded bg-[#1e1e1e] flex items-end justify-center pb-2 overflow-hidden">
                <svg viewBox="0 0 200 100" class="w-full h-full absolute inset-0">
                  <!-- 1. 基础环境 -->
                  <!-- 地形波浪 -->
                  <path d="M10 90 Q40 90 60 80 Q90 65 110 85 T190 90" fill="none" stroke="#666" stroke-width="1.5" />
                  <!-- 海平面 -->
                  <line x1="10" y1="95" x2="190" y2="95" stroke="#444" stroke-dasharray="2 2" />
                  <text x="15" y="98" font-size="8" fill="#3b82f6">海平面</text>

                  <!-- Home点 -->
                  <rect x="48" y="78" width="4" height="4" fill="#eab308" />
                  <text x="45" y="90" font-size="8" fill="#eab308">H</text>

                  <!-- 目标无人机位置 (x=120, y=30) -->
                  <path d="M115 30 L125 30 M120 25 L120 35" stroke="white" stroke-width="1.5" />
                  <circle cx="120" cy="30" r="2" fill="white" />

                  <!-- 2. 动态测量标注 -->

                  <!-- A. 海拔高度 (ASL) -->
                  <g v-if="settingsData.altitudeMode === 'asl'">
                    <!-- 测量线: 从海平面(95)到无人机(30) -->
                    <line x1="140" y1="95" x2="140" y2="30" stroke="#3b82f6" stroke-width="1" />
                    <line x1="137" y1="95" x2="143" y2="95" stroke="#3b82f6" stroke-width="1" /> <!-- 底部横杠 -->
                    <line x1="137" y1="30" x2="143" y2="30" stroke="#3b82f6" stroke-width="1" /> <!-- 顶部横杠 -->
                    <line x1="120" y1="30" x2="140" y2="30" stroke="#666" stroke-dasharray="2 2" /> <!-- 辅助水平线 -->

                    <text x="145" y="60" font-size="8" fill="#3b82f6">海拔高度</text>
                    <text x="145" y="70" font-size="8" fill="#3b82f6">(ASL)</text>
                  </g>

                  <!-- B. 相对起飞点高度 (Relative) -->
                  <g v-if="settingsData.altitudeMode === 'relative'">
                    <!-- Home点水平参考线 (y=80 approx) -->
                    <line x1="52" y1="80" x2="140" y2="80" stroke="#eab308" stroke-dasharray="2 2" opacity="0.5" />

                    <!-- 测量线: 从Home水平面(80)到无人机(30) -->
                    <line x1="140" y1="80" x2="140" y2="30" stroke="#eab308" stroke-width="1" />
                    <line x1="137" y1="80" x2="143" y2="80" stroke="#eab308" stroke-width="1" />
                    <line x1="137" y1="30" x2="143" y2="30" stroke="#eab308" stroke-width="1" />
                    <line x1="120" y1="30" x2="140" y2="30" stroke="#666" stroke-dasharray="2 2" />

                    <text x="145" y="50" font-size="8" fill="#eab308">相对起飞点</text>
                    <text x="145" y="60" font-size="8" fill="#eab308">(ALT)</text>
                  </g>

                  <!-- C. 相对地形高度 (Terrain/AGL) -->
                  <g v-if="settingsData.altitudeMode === 'terrain'">
                    <!-- 地面投影点 (x=120, y=85 approx on curve) -->
                    <line x1="120" y1="30" x2="120" y2="85" stroke="#4ade80" stroke-dasharray="2 2" />

                    <!-- 测量线 -->
                    <line x1="140" y1="85" x2="140" y2="30" stroke="#4ade80" stroke-width="1" />
                    <line x1="137" y1="85" x2="143" y2="85" stroke="#4ade80" stroke-width="1" />
                    <line x1="137" y1="30" x2="143" y2="30" stroke="#4ade80" stroke-width="1" />
                    <line x1="120" y1="30" x2="140" y2="30" stroke="#666" stroke-dasharray="2 2" />
                    <line x1="120" y1="85" x2="140" y2="85" stroke="#666" stroke-dasharray="2 2" />

                    <text x="145" y="55" font-size="8" fill="#4ade80">相对地面</text>
                    <text x="145" y="65" font-size="8" fill="#4ade80">(AGL)</text>
                  </g>
                </svg>
              </div>
              <!-- 数值调节器 (复用样式) -->
              <div
                class="w-16 flex flex-col items-center justify-between py-1 bg-[#1e1e1e] rounded border border-[#3a3a3a]">
                <div class="flex flex-col gap-1 w-full px-1">
                  <button @click="adjustValue('flightHeight', 100)"
                    class="bg-[#2d2d2d] text-gray-400 text-[10px] py-0.5 rounded hover:bg-[#444]">+100</button>
                  <button @click="adjustValue('flightHeight', 10)"
                    class="bg-[#2d2d2d] text-gray-400 text-[10px] py-0.5 rounded hover:bg-[#444]">+10</button>
                </div>
                <div class="text-blue-500 font-bold text-lg leading-none my-1">{{ settingsData.flightHeight }} <span
                    class="text-xs font-normal text-gray-400">m</span></div>
                <div class="flex flex-col gap-1 w-full px-1">
                  <button @click="adjustValue('flightHeight', -10)"
                    class="bg-[#2d2d2d] text-gray-400 text-[10px] py-0.5 rounded hover:bg-[#444]">-10</button>
                  <button @click="adjustValue('flightHeight', -100)"
                    class="bg-[#2d2d2d] text-gray-400 text-[10px] py-0.5 rounded hover:bg-[#444]">-100</button>
                </div>
              </div>
            </div>
          </div>

          <!-- 4. 全局速度 -->
          <div class="bg-[#2d2d2d] p-3 rounded border border-[#3a3a3a]">
            <div class="text-xs text-gray-400 mb-2">全局航线速度</div>
            <div class="flex items-center gap-4">
              <button @click="adjustValue('globalSpeed', -1)"
                class="w-10 h-10 bg-[#3a3a3a] rounded flex items-center justify-center text-gray-400 hover:text-white text-xl">-</button>
              <div class="text-blue-500 font-bold text-2xl flex-1 text-center">{{ settingsData.globalSpeed }} <span
                  class="text-sm font-normal text-gray-400">m/s</span></div>
              <button @click="adjustValue('globalSpeed', 1)"
                class="w-10 h-10 bg-[#3a3a3a] rounded flex items-center justify-center text-gray-400 hover:text-white text-xl">+</button>
            </div>
          </div>

          <!-- 5. 高级设置 (折叠面板) -->
          <div class="border-t border-[#3a3a3a] pt-2">
            <div @click="settingsData.isAdvancedOpen = !settingsData.isAdvancedOpen"
              class="flex items-center justify-center text-gray-300 text-xs cursor-pointer hover:text-white py-2 select-none">
              高级设置
              <svg class="w-3 h-3 ml-1 transition-transform duration-200"
                :class="settingsData.isAdvancedOpen ? 'rotate-180' : ''" viewBox="0 0 24 24" fill="none"
                stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
              </svg>
            </div>

            <div v-show="settingsData.isAdvancedOpen" class="space-y-3 pt-2 pb-4">
              <!-- 起飞速度 -->
              <div>
                <div class="text-xs text-gray-400 mb-1">起飞速度</div>
                <div class="flex items-center gap-2 bg-[#1e1e1e] p-1 rounded">
                  <button @click="adjustValue('takeoffSpeed', -1)"
                    class="w-8 h-8 bg-[#333] rounded text-gray-400 hover:text-white">-</button>
                  <div class="flex-1 text-center text-blue-500 font-bold">{{ settingsData.takeoffSpeed }} <span
                      class="text-xs text-gray-500">m/s</span></div>
                  <button @click="adjustValue('takeoffSpeed', 1)"
                    class="w-8 h-8 bg-[#333] rounded text-gray-400 hover:text-white">+</button>
                </div>
              </div>

              <!-- 下拉选框组 -->
              <div class="space-y-3">
                <div v-for="item in [
                  { l: '航点类型', k: 'waypointType', opts: [{ v: 'toPointAndStopWithDiscontinuityCurvature', l: '直线飞行，飞行器到点停' }, { v: 'coordinateTurn', l: '协调转弯，不过点，提前转弯' }, { v: 'toPointAndStopWithContinuityCurvature', l: '曲线飞行，飞行器到点停' }, { v: 'toPointAndPassWithContinuityCurvature', l: '曲线飞行，飞行器过点不停' }] },
                  { l: '飞行器偏航角模式 ⓘ', k: 'yawMode', opts: [{ v: 'followWayline', l: '沿航线方向' }, { v: 'manually', l: '手动控制' }, { v: 'fixed', l: '锁定当前偏航角' }, { v: 'smoothTransition', l: '自定义' }, { v: 'towardPOI', l: '朝向兴趣点' }] },
                  { l: '航点间云台俯仰角控制模式 ⓘ', k: 'gimbalMode', opts: [{ v: 'manual', l: '手动控制' }, { v: 'usePointSetting', l: '依照每个航点设置' }] },
                  { l: '完成动作 ⓘ', k: 'finishAction', opts: [{ v: 'goHome', l: '自动返航' }, { v: 'noAction', l: '退出航线模式' }, { v: 'autoLand', l: '原地降落' }, { v: 'gotoFirstWaypoint', l: '飞向起始点退出航线模式' }] },
                ]" :key="item.k">
                  <div class="text-xs text-gray-400 mb-1">{{ item.l }}</div>
                  <div class="relative">
                    <select v-model="settingsData[item.k]"
                      class="w-full bg-[#3a3a3a] text-xs text-white p-2 pr-8 rounded appearance-none focus:outline-none border border-[#444]">
                      <option v-for="opt in item.opts" :key="opt.v" :value="opt.v">{{ opt.l }}</option>
                    </select>
                    <div class="pointer-events-none absolute inset-y-0 right-0 flex items-center px-2 text-gray-400">
                      <svg class="fill-current h-4 w-4" viewBox="0 0 20 20">
                        <path d="M9.293 12.95l.707.707L15.657 8l-1.414-1.414L10 10.828 5.757 6.586 4.343 8z" />
                      </svg>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

        </div>

      </div>

      <!-- FOOTER: 底部按钮 -->
      <div class="p-4 border-t border-gray-700 flex justify-end gap-3 bg-[#252525]">
        <button @click="handleCancel"
          class="px-6 py-1.5 rounded bg-[#3a3a3a] text-sm text-gray-200 hover:bg-[#4a4a4a] transition-colors">
          取消
        </button>
        <button v-if="currentStep === 'create'" @click="handleCreateConfirm"
          class="px-6 py-1.5 rounded bg-blue-600 text-sm text-white hover:bg-blue-500 transition-colors shadow-lg">
          确定
        </button>
        <button v-else @click="handleFinalConfirm"
          class="px-6 py-1.5 rounded bg-blue-600 text-sm text-white hover:bg-blue-500 transition-colors shadow-lg">
          确定
        </button>
      </div>

    </div>
  </div>
</template>

<script lang="ts">
import { h } from 'vue';
import { useRouter } from "vue-router";
const SvgIcon = (pathInfo: string) => ({ render: () => h('svg', { xmlns: 'http://www.w3.org/2000/svg', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.5, strokeLinecap: 'round', strokeLinejoin: 'round' }, [h('path', { d: pathInfo })]) })
const icons = {
  waypoint: 'M3 12h18M3 6h18M3 18h18 M8 6l4 6-4 6',
  scan: 'M4 4h16v16H4z M9 9h6v6H9z',
  mapping: 'M3 3h18v18H3z M8 12h8',
  strip: 'M12 3v18 M8 8l4-4 4 4 M8 16l4 4 4-4',
  oblique: 'M4 20h16L12 4z',
  geometry: 'M12 2l10 6-10 6-10-6z M12 14l10-6M12 14v8M2 8v8l10 8 10-8V8',
  closeup: 'M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7'
}
function getIcon(name: string) { return SvgIcon(icons[name as keyof typeof icons] || 'M12 12m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0') }
</script>

<style scoped lang="scss">
/* 滚动条美化 */
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #444;
  border-radius: 2px;
}

.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: #555;
}
</style>
