<template>
  <a-row class="demo-viewer h-[100%] relative overflow-hidden">
    <div ref="viewerContainer" class="h-full w-full"></div>

    <!-- 顶部状态栏与 AI 事件开关区域 -->
    <div class="top-bar-wrapper">
      <div class="top-brand-bar">
        <span class="drone-icon">✈️</span>
        <span class="title-text">无人机飞行轨迹回放</span>
      </div>

      <!-- 轨迹图例胶囊 -->
      <div class="trajectory-legend-pill">
        <div class="legend-item">
          <span class="legend-line flown"></span>
          <span class="legend-text">已飞行</span>
        </div>
        <div class="legend-divider"></div>
        <div class="legend-item">
          <span class="legend-line upcoming"></span>
          <span class="legend-text">未飞行</span>
        </div>
      </div>

      <!-- 3D 倾斜摄影建模图层控制 (公共组件) -->
      <TilesetLayerControl
        :viewer="viewerRef"
        variant="pill"
        placement="bottomLeft"
      />
    </div>

    <!-- 自定义高颜值时间轴播放器组件 -->
    <CesiumTimelinePlayer
      v-if="isViewerReady && viewerRef && startJulianDate && stopJulianDate"
      :viewer="viewerRef"
      :Cesium="Cesium"
      :start-julian-date="startJulianDate"
      :stop-julian-date="stopJulianDate"
      :total-points="trajectoryData.length"
      :current-point-index="currentPointIndex"
      :default-speed="10"
      @change-view="handleViewChange"
    />

    <!-- 主飞行数据面板 (右侧悬浮) -->
    <a-card class="flight-data-panel" :bordered="false">
      <template #title>
        <div class="panel-title">
          <span>飞行数据</span>
          <a-badge :status="currentData.positionState?.is_fixed === 2 ? 'success' : 'warning'"
            :text="getGpsStatusText()" />
        </div>
      </template>

      <!-- 位置信息 -->
      <div class="section">
        <div class="section-title">位置信息</div>
        <a-row :gutter="[12, 8]">
          <a-col :span="12">
            <div class="data-item">
              <span class="label">经度:</span>
              <span class="value">{{ currentData.lon?.toFixed(6) || '--' }}</span>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="data-item">
              <span class="label">纬度:</span>
              <span class="value">{{ currentData.lat?.toFixed(6) || '--' }}</span>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="data-item">
              <span class="label">高度:</span>
              <span class="value">{{ currentData.height?.toFixed(2) || '--' }} m</span>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="data-item">
              <span class="label">返航距离:</span>
              <span class="value">{{ currentData.homeDistance?.toFixed(2) || '--' }} m</span>
            </div>
          </a-col>
        </a-row>
      </div>

      <!-- 终点坐标 -->
      <div class="section">
        <div class="section-title">终点坐标</div>
        <a-row :gutter="[12, 8]">
          <a-col :span="12">
            <div class="data-item">
              <span class="label">终点经度:</span>
              <span class="value">{{ currentData.endLon?.toFixed(6) || '--' }}</span>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="data-item">
              <span class="label">终点纬度:</span>
              <span class="value">{{ currentData.endLat?.toFixed(6) || '--' }}</span>
            </div>
          </a-col>
        </a-row>
      </div>

      <!-- 飞行统计 -->
      <div class="section">
        <div class="section-title">飞行统计</div>
        <a-row :gutter="[12, 8]">
          <a-col :span="12">
            <div class="data-item">
              <span class="label">时长:</span>
              <span class="value">{{ currentData.totalTime || '--' }}</span>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="data-item">
              <span class="label">总里程:</span>
              <span class="value">{{ currentData.currentTotalDistance?.toFixed(2) || '--' }} m</span>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="data-item">
              <span class="label">耗电量:</span>
              <span class="value">{{ currentData.totalBatteryUsed || '--' }} %</span>
            </div>
          </a-col>
        </a-row>
      </div>

      <!-- 速度信息 -->
      <div class="section">
        <div class="section-title">速度信息</div>
        <a-row :gutter="[12, 8]">
          <a-col :span="12">
            <div class="data-item">
              <span class="label">水平速度:</span>
              <span class="value">{{ currentData.speed?.toFixed(2) || '--' }} m/s</span>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="data-item">
              <span class="label">垂直速度:</span>
              <span class="value">{{ currentData.verticalSpeed?.toFixed(2) || '--' }} m/s</span>
            </div>
          </a-col>
        </a-row>
      </div>

      <!-- 姿态信息 -->
      <div class="section">
        <div class="section-title">姿态信息</div>
        <a-row :gutter="[12, 8]">
          <a-col :span="8">
            <div class="data-item">
              <span class="label">航向:</span>
              <span class="value">{{ currentData.yaw?.toFixed(1) || '--' }}°</span>
            </div>
          </a-col>
          <a-col :span="8">
            <div class="data-item">
              <span class="label">俯仰:</span>
              <span class="value">{{ currentData.pitch?.toFixed(1) || '--' }}°</span>
            </div>
          </a-col>
          <a-col :span="8">
            <div class="data-item">
              <span class="label">横滚:</span>
              <span class="value">{{ currentData.roll?.toFixed(1) || '--' }}°</span>
            </div>
          </a-col>
        </a-row>
      </div>

      <!-- 云台信息 -->
      <div class="section" v-if="currentData.gimbal">
        <div class="section-title">云台信息</div>
        <a-row :gutter="[12, 8]">
          <a-col :span="8">
            <div class="data-item">
              <span class="label">俯仰:</span>
              <span class="value">{{ currentData.gimbal.pitch?.toFixed(1) || '--' }}°</span>
            </div>
          </a-col>
          <a-col :span="8">
            <div class="data-item">
              <span class="label">偏航:</span>
              <span class="value">{{ currentData.gimbal.yaw?.toFixed(1) || '--' }}°</span>
            </div>
          </a-col>
          <a-col :span="8">
            <div class="data-item">
              <span class="label">变焦:</span>
              <span class="value">{{ currentData.gimbal.zoom?.toFixed(1) || '--' }}x</span>
            </div>
          </a-col>
        </a-row>
      </div>

      <!-- 电池信息 -->
      <div class="section">
        <div class="section-title">电池信息</div>
        <a-row :gutter="[12, 8]">
          <a-col :span="12">
            <div class="data-item">
              <span class="label">电量:</span>
              <span class="value" :class="getBatteryClass()">
                {{ currentData.battery?.toFixed(0) || '--' }}%
              </span>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="data-item">
              <span class="label">温度:</span>
              <span class="value">{{ currentData.batteryTemp?.toFixed(1) || '--' }}°C</span>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="data-item">
              <span class="label">电压:</span>
              <span class="value">{{ currentData.batteryVoltage?.toFixed(2) || '--' }} V</span>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="data-item">
              <span class="label">循环次数:</span>
              <span class="value">{{ currentData.batteryLoops || '--' }}</span>
            </div>
          </a-col>
        </a-row>
      </div>

      <!-- GPS/RTK状态 -->
      <div class="section">
        <div class="section-title">定位状态</div>
        <a-row :gutter="[12, 8]">
          <a-col :span="12">
            <div class="data-item">
              <span class="label">GPS卫星:</span>
              <span class="value">{{ currentData.positionState?.gps_number || '--' }}</span>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="data-item">
              <span class="label">RTK卫星:</span>
              <span class="value">{{ currentData.positionState?.rtk_number || '--' }}</span>
            </div>
          </a-col>
          <a-col :span="24">
            <div class="data-item">
              <span class="label">定位质量:</span>
              <span class="value">{{ getPositionQualityText() }}</span>
            </div>
          </a-col>
        </a-row>
      </div>
    </a-card>

  </a-row>
</template>

<script setup lang="ts">
import * as Cesium from 'cesium'
import { onUnmounted, reactive, ref, watch, markRaw, shallowRef, nextTick, computed } from 'vue'
import CesiumTimelinePlayer from '@/components/CesiumTimelinePlayer.vue'
import TilesetLayerControl from '@/components/TilesetLayerControl.vue'
import { flightOsd } from "@/api/device-setting";
import { useRoute } from "vue-router";
import { useTitle } from "vue-hooks-plus";
import { CURRENT_CONFIG } from '@/api/http/config';

const viewerContainer = ref<HTMLDivElement | null>(null)
const position = ref({})
const positions = ref([])
const model = ref()
const availability = ref(null)
const orientation = ref(null)
const entity = ref<any>(null)
const startJulianDate = ref<any>(null)
const stopJulianDate = ref<any>(null)
const currentPointIndex = ref(0)
const viewerRef = shallowRef<any>(null)
const isViewerReady = ref(false)

const currentData = reactive<Record<string, any>>({
  height: null,
  yaw: null,
  pitch: null,
  roll: null,
  speed: null,
  verticalSpeed: null,
  battery: null,
  batteryTemp: null,
  batteryVoltage: null,
  batteryLoops: null,
  time: null,
  homeDistance: null,
  positionState: null,
  storage: null,
  gimbal: null,
  totalSorties: null,
  totalFlightTime: null,
  totalDistance: null,
  heightLimit: null,
  distanceLimit: null,
  totalTime: null, // 本次飞行总时长
  currentTotalDistance: null, // 本次飞行总里程
  totalBatteryUsed: null, //本次飞行耗电量
  endLon: null,
  endLat: null
})

let _viewer: any = undefined
let clockTickListener: any = null
const rawTrajectoryData = ref([])
const trajectoryData = ref([])

useTitle('飞行轨迹回放')

const initNativeViewer = () => {
  if (!viewerContainer.value || !trajectoryData.value.length || _viewer) return

  // 配置 Cesium Ion Token (与虚拟座舱保持一致，激活真实山体 3D DEM 地形)
  if (Cesium.Ion) {
    Cesium.Ion.defaultAccessToken = CURRENT_CONFIG.cesiumIonToken
  }

  const tdtToken = CURRENT_CONFIG.tiandituToken
  const imgProvider = new Cesium.UrlTemplateImageryProvider({
    url: `https://t{s}.tianditu.gov.cn/DataServer?T=img_w&X={x}&Y={y}&L={z}&tk=${tdtToken}`,
    subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
    maximumLevel: 18
  })
  const ciaProvider = new Cesium.UrlTemplateImageryProvider({
    url: `https://t{s}.tianditu.gov.cn/DataServer?T=cia_w&X={x}&Y={y}&L={z}&tk=${tdtToken}`,
    subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
    maximumLevel: 18
  })

  const viewer = new Cesium.Viewer(viewerContainer.value, {
    shouldAnimate: true,
    animation: false,
    timeline: false,
    baseLayerPicker: false,
    geocoder: false,
    targetFrameRate: 30,
    sceneModePicker: false,
    homeButton: false,
    navigationHelpButton: false,
    projectionPicker: false,
    infoBox: false,
    selectionIndicator: false,
    fullscreenButton: false,
    baseLayer: new Cesium.ImageryLayer(imgProvider)
  })

  viewer.imageryLayers.addImageryProvider(ciaProvider)
  _viewer = markRaw(viewer)
  viewerRef.value = markRaw(viewer)

  // 开启 3D 真实起伏山体地形 (与虚拟座舱保持一致)
  viewer.scene.globe.depthTestAgainstTerrain = true
  try {
    if (typeof Cesium.createWorldTerrainAsync === 'function') {
      Cesium.createWorldTerrainAsync({
        requestWaterMask: true,
        requestVertexNormals: true
      }).then(terrain => {
        if (_viewer && !_viewer.isDestroyed()) {
          _viewer.terrainProvider = terrain
        }
      }).catch(e => {
        console.warn('Cesium Ion 3D 地形加载异常:', e)
      })
    } else if (typeof (Cesium as any).createWorldTerrain === 'function') {
      viewer.terrainProvider = (Cesium as any).createWorldTerrain({
        requestWaterMask: true,
        requestVertexNormals: true
      })
    }
  } catch (e) {
    console.warn('初始化 3D 地形异常:', e)
  }

  if (_viewer.geocoder && _viewer.geocoder.container) {
    const setChinesePlaceholder = () => {
      const inputEl = _viewer.geocoder.container.querySelector('.cesium-geocoder-input') || _viewer.geocoder.container.querySelector('input')
      if (inputEl) {
        inputEl.placeholder = '搜索地址或地名...'
      }
    }
    setChinesePlaceholder()
    setTimeout(setChinesePlaceholder, 200)
  }

  onViewerReady({ Cesium, viewer: _viewer })

  // 1. 全程完整航线（底色轨迹：未飞行/规划路线，呈现半透明科技蓝虚线）
  if (positions.value && positions.value.length > 1) {
    _viewer.entities.add({
      name: '完整飞行轨迹(未飞行)',
      polyline: {
        positions: positions.value,
        width: 4,
        material: new Cesium.PolylineDashMaterialProperty({
          color: Cesium.Color.fromCssColorString('#00d4ff'),
          gapColor: Cesium.Color.fromCssColorString('rgba(0, 212, 255, 0.2)'),
          dashLength: 16.0
        }),
        clampToGround: false
      }
    })

    // 2. 起点地标 (绿)
    _viewer.entities.add({
      name: '起点',
      position: positions.value[0],
      point: {
        pixelSize: 8,
        color: Cesium.Color.fromCssColorString('#10b981'),
        outlineColor: Cesium.Color.WHITE,
        outlineWidth: 2,
        disableDepthTestDistance: Number.POSITIVE_INFINITY
      },
      label: {
        text: '起',
        font: 'bold 11px sans-serif',
        fillColor: Cesium.Color.WHITE,
        backgroundColor: Cesium.Color.fromCssColorString('rgba(16, 185, 129, 0.85)'),
        showBackground: true,
        backgroundPadding: new Cesium.Cartesian2(4, 2),
        verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
        pixelOffset: new Cesium.Cartesian2(0, -10),
        disableDepthTestDistance: Number.POSITIVE_INFINITY
      }
    })

    // 3. 终点地标 (红)
    _viewer.entities.add({
      name: '终点',
      position: positions.value[positions.value.length - 1],
      point: {
        pixelSize: 8,
        color: Cesium.Color.fromCssColorString('#ef4444'),
        outlineColor: Cesium.Color.WHITE,
        outlineWidth: 2,
        disableDepthTestDistance: Number.POSITIVE_INFINITY
      },
      label: {
        text: '终',
        font: 'bold 11px sans-serif',
        fillColor: Cesium.Color.WHITE,
        backgroundColor: Cesium.Color.fromCssColorString('rgba(239, 68, 68, 0.85)'),
        showBackground: true,
        backgroundPadding: new Cesium.Cartesian2(4, 2),
        verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
        pixelOffset: new Cesium.Cartesian2(0, -10),
        disableDepthTestDistance: Number.POSITIVE_INFINITY
      }
    })
  }

  // 4. 无人机实体：自带 path (已飞行轨迹，亮金黄高亮发光实线，trailTime: Number.POSITIVE_INFINITY)
  const droneEntity = _viewer.entities.add({
    availability: availability.value,
    position: position.value,
    orientation: orientation.value,
    description: '轨迹回放',
    path: {
      resolution: 1,
      material: new Cesium.PolylineGlowMaterialProperty({
        glowPower: 0.25,
        color: Cesium.Color.fromCssColorString('#ffb703')
      }),
      width: 7,
      leadTime: 0,
      trailTime: Number.POSITIVE_INFINITY
    },
    model: {
      uri: '/glb/DJI.glb',
      scale: 0.06,
      minimumPixelSize: 36,
      maximumScale: 0.08,
      silhouetteColor: Cesium.Color.fromCssColorString('#00d4ff'),
      silhouetteSize: 1.5
    }
  })
  entity.value = droneEntity
}

const onViewerReady = ({ Cesium, viewer }) => {
  console.log('viewer ready')
  _viewer = viewer

  viewer.scene.globe.enableLighting = true
  Cesium.Math.setRandomNumberSeed(3)

  const start = Cesium.JulianDate.fromDate(new Date(trajectoryData.value[0].time))
  let stop
  if (trajectoryData.value.length === 1) {
    stop = Cesium.JulianDate.addSeconds(start, 10, new Cesium.JulianDate())
  } else {
    stop = Cesium.JulianDate.fromDate(new Date(trajectoryData.value[trajectoryData.value.length - 1].time))
  }
  viewer.clock.startTime = start.clone()
  viewer.clock.stopTime = stop.clone()
  viewer.clock.currentTime = start.clone()
  viewer.clock.clockRange = Cesium.ClockRange.LOOP_STOP
  viewer.clock.multiplier = 10

  startJulianDate.value = start
  stopJulianDate.value = stop
  isViewerReady.value = true

  position.value = computeCirclularFlight()
  availability.value = new Cesium.TimeIntervalCollection([
    new Cesium.TimeInterval({
      start: start,
      stop: stop
    })
  ])
  orientation.value = new Cesium.VelocityOrientationProperty(position.value)

  clockTickListener = viewer.clock.onTick.addEventListener((clock) => {
    updateCurrentFlightData(clock.currentTime, Cesium)
  })
}

const computeCirclularFlight = () => {
  positions.value = []
  let property = new Cesium.SampledPositionProperty()
  for (let i = 0; i <= trajectoryData.value.length - 1; i += 1) {
    let time = Cesium.JulianDate.fromDate(new Date(trajectoryData.value[i].time))
    let pos = Cesium.Cartesian3.fromDegrees(
      trajectoryData.value[i].lon,
      trajectoryData.value[i].lat,
      trajectoryData.value[i].height
    )
    property.addSample(time, pos)
    positions.value.push(pos)
  }
  // 若只有1个点，增加一个在 10s 后的相同采样点，使 SampledPositionProperty 形成有效的时间范围
  if (trajectoryData.value.length === 1) {
    let time2 = Cesium.JulianDate.addSeconds(
      Cesium.JulianDate.fromDate(new Date(trajectoryData.value[0].time)),
      10,
      new Cesium.JulianDate()
    )
    let pos2 = Cesium.Cartesian3.fromDegrees(
      trajectoryData.value[0].lon,
      trajectoryData.value[0].lat,
      trajectoryData.value[0].height
    )
    property.addSample(time2, pos2)
  }
  return property
}

// 二分查找时间戳对应的最接近航点
const findClosestTrajectoryIndex = (currentTimeMs: number, dataList: any[]): number => {
  const len = dataList.length
  if (len <= 1) return 0
  if (currentTimeMs <= dataList[0]._timeMs) return 0
  if (currentTimeMs >= dataList[len - 1]._timeMs) return len - 1

  let low = 0
  let high = len - 1
  while (low <= high) {
    const mid = (low + high) >> 1
    const midTime = dataList[mid]._timeMs
    if (midTime === currentTimeMs) {
      return mid
    } else if (midTime < currentTimeMs) {
      low = mid + 1
    } else {
      high = mid - 1
    }
  }

  if (low >= len) return high
  if (high < 0) return low
  const diffLow = Math.abs(dataList[low]._timeMs - currentTimeMs)
  const diffHigh = Math.abs(dataList[high]._timeMs - currentTimeMs)
  return diffLow < diffHigh ? low : high
}

let lastClosestIndex = -1

const updateCurrentFlightData = (currentTime: any, Cesium: any) => {
  const dataList = trajectoryData.value as any[]
  if (!dataList || !dataList.length) return

  const currentDate = Cesium.JulianDate.toDate(currentTime)
  const currentTimeMs = currentDate.getTime()

  const closestIndex = findClosestTrajectoryIndex(currentTimeMs, dataList)
  if (closestIndex === lastClosestIndex) {
    return // 索引未变化，跳过重复赋值与姿态计算
  }
  lastClosestIndex = closestIndex
  currentPointIndex.value = closestIndex

  const data = dataList[closestIndex]
  if (data) {
    Object.assign(currentData, data)
  }

  // 🧭 动态调整姿态逻辑
  if (Cesium && data && data.lon && data.lat && data.height !== undefined) {
    const pos = Cesium.Cartesian3.fromDegrees(data.lon, data.lat, data.height)

    // 从速度判断方向
    const horizontalSpeed = Math.abs(data.speed || 0)
    const verticalSpeed = Math.abs(data.verticalSpeed || 0)
    const totalSpeed = Math.sqrt(horizontalSpeed ** 2 + verticalSpeed ** 2)

    // 定义阈值
    const verticalRatio = totalSpeed > 0 ? verticalSpeed / totalSpeed : 0

    if (verticalRatio < 0.7 && horizontalSpeed > 0.5) {
      // 🛫 水平飞行阶段：沿速度方向
      orientation.value = new Cesium.VelocityOrientationProperty(position.value)
    } else {
      // 🚁 垂直飞行阶段：保持水平
      const heading = Cesium.Math.toRadians(data.yaw || 0)
      const hpr = new Cesium.HeadingPitchRoll(heading, 0, 0)
      orientation.value = Cesium.Transforms.headingPitchRollQuaternion(pos, hpr)
    }
  }
}

// 辅助函数
const getGpsStatusText = () => {
  const state = currentData.positionState
  if (!state) return '未知'
  if (state.is_fixed === 2) return 'RTK固定解'
  if (state.is_fixed === 1) return 'RTK浮动解'
  return 'GPS定位'
}

const getPositionQualityText = () => {
  const quality = currentData.positionState?.quality
  if (!quality) return '--'
  const qualityMap = {
    0: '无效',
    1: '差',
    2: '一般',
    3: '良好',
    4: '优秀',
    5: '极好'
  }
  return qualityMap[quality] || '未知'
}

const getBatteryClass = () => {
  const battery = currentData.battery
  if (!battery) return ''
  if (battery < 20) return 'battery-low'
  if (battery < 40) return 'battery-medium'
  return ''
}

const formatFlightTime = (seconds) => {
  if (!seconds && seconds !== 0) return '--'
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = Math.floor(seconds % 60)
  return `${minutes}m ${secs}s`
}

const viewTopDown = () => {
  if (!_viewer) return
  _viewer.trackedEntity = undefined
  _viewer.zoomTo(_viewer.entities, new Cesium.HeadingPitchRange(0, Cesium.Math.toRadians(-89.9), 1200))
}

const viewSide = () => {
  if (!_viewer) return
  _viewer.trackedEntity = undefined
  _viewer.zoomTo(_viewer.entities, new Cesium.HeadingPitchRange(Cesium.Math.toRadians(-90), Cesium.Math.toRadians(-20), 800))
}

const viewAircraft = () => {
  if (!_viewer || !entity.value) return
  _viewer.trackedEntity = entity.value
}

const viewReset = () => {
  if (!_viewer) return
  _viewer.trackedEntity = undefined
  _viewer.zoomTo(_viewer.entities, new Cesium.HeadingPitchRange(Cesium.Math.toRadians(-90), Cesium.Math.toRadians(-45), 1000))
}

const handleViewChange = (mode: 'side' | 'aircraft' | 'top' | 'reset') => {
  switch (mode) {
    case 'side':
      viewSide()
      break
    case 'aircraft':
      viewAircraft()
      break
    case 'top':
      viewTopDown()
      break
    case 'reset':
      viewReset()
      break
  }
}

const route = useRoute()
watch(() => route.query.jobId, async val => {
  if (!val) return

  // 1. 查询航线OSD遥测数据
  rawTrajectoryData.value = await flightOsd(val)
  trajectoryData.value = rawTrajectoryData.value.map(d => {
    const osd = d.osd || {}
    const battery = osd.battery?.batteries?.[0]
    const payload = osd.payload?.[0]
    const timeMs = typeof d.create_time === 'number' ? d.create_time : new Date(d.create_time).getTime()

    return {
      lat: osd.latitude,
      lon: osd.longitude,
      height: osd.height,
      yaw: osd.attitude_head,
      pitch: osd.attitude_pitch,
      roll: osd.attitude_roll,
      speed: osd.horizontal_speed,
      verticalSpeed: osd.vertical_speed,
      battery: osd.battery?.capacity_percent,
      batteryTemp: battery?.temperature,
      batteryVoltage: battery?.voltage ? battery.voltage / 1000 : null,
      batteryLoops: battery?.loop_times,
      time: d.create_time,
      _timeMs: timeMs,
      homeDistance: osd.home_distance,
      positionState: osd.position_state,
      storage: osd.storage,
      gimbal: payload ? {
        pitch: payload.gimbal_pitch,
        yaw: payload.gimbal_yaw,
        roll: payload.gimbal_roll,
        zoom: payload.zoom_factor !== undefined ? payload.zoom_factor : 1
      } : null,
      totalSorties: osd.total_flight_sorties,
      totalFlightTime: osd.total_flight_time,
      totalDistance: osd.total_flight_distance,
      heightLimit: osd.height_limit,
      distanceLimit: osd.distance_limit_status?.distance_limit
    }
  }).filter(p => p.lat && p.lon)

  calcFlightStats()
  nextTick(() => {
    initNativeViewer()
    if (_viewer) {
      _viewer.zoomTo(_viewer.entities, new Cesium.HeadingPitchRange(Cesium.Math.toRadians(-90),
        Cesium.Math.toRadians(-45), 1000))
    }
  })

}, {
  immediate: true
})

onUnmounted(() => {
  if (clockTickListener) {
    clockTickListener()
    clockTickListener = null
  }
  if (_viewer) {
    if (!_viewer.isDestroyed()) {
      _viewer.destroy()
    }
    _viewer = undefined
  }
})

const calcFlightStats = () => {
  const data = trajectoryData.value
  if (data && data.length > 0) {
    Object.assign(currentData, data[0])
  }
  if (!data || data.length < 2) {
    currentData.totalTime = formatFlightTime(0)
    currentData.currentTotalDistance = 0
    currentData.totalBatteryUsed = 0
    if (data && data.length === 1) {
      currentData.endLon = data[0].lon
      currentData.endLat = data[0].lat
    }
    return
  }

  // 飞行时长
  const startTime = data[0].time
  const endTime = data[data.length - 1].time
  currentData.totalTime = formatFlightTime(Math.max(0, (endTime - startTime) / 1000))

  // 飞行距离
  const firstTotalDistance = data[0].totalDistance || 0;
  const lastTotalDistance = data[data.length - 1].totalDistance || 0;
  let flightDist = Math.max(0, lastTotalDistance - firstTotalDistance);
  if (flightDist === 0) {
    let tempDist = 0;
    for (let i = 1; i < data.length; i++) {
      tempDist += calcDistance(data[i - 1].lon, data[i - 1].lat, data[i].lon, data[i].lat);
    }
    flightDist = tempDist;
  }
  currentData.currentTotalDistance = flightDist;

  // 耗电量
  const firstBattery = data[0].battery;
  const lastBattery = data[data.length - 1].battery;
  currentData.totalBatteryUsed = Math.max(0, firstBattery - lastBattery);

  // 终点坐标
  const lastPoint = data[data.length - 1]
  currentData.endLon = lastPoint.lon
  currentData.endLat = lastPoint.lat
}

function calcDistance(lon1: number, lat1: number, lon2: number, lat2: number) {
  const R = 6371000;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const A1 = toRad(lat1);
  const A2 = toRad(lat2);
  const A3 = toRad(lat2 - lat1);
  const A4 = toRad(lon2 - lon1);
  const a =
    Math.sin(A3 / 2) ** 2 +
    Math.cos(A1) * Math.cos(A2) * Math.sin(A4 / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
</script>

<style scoped lang="scss">
/* 顶部状态栏与开关区域 */
.top-bar-wrapper {
  position: absolute;
  top: 20px;
  left: 20px;
  z-index: 1000;
  display: flex;
  align-items: center;
  gap: 12px;
}

.top-brand-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(11, 15, 25, 0.85);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.5px;

  .drone-icon {
    font-size: 18px;
  }

  .title-text {
    background: linear-gradient(135deg, #ffffff 0%, #00d4ff 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}

.trajectory-legend-pill {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: rgba(11, 15, 25, 0.85);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);

  .legend-item {
    display: flex;
    align-items: center;
    gap: 6px;

    .legend-line {
      width: 18px;
      height: 3px;
      border-radius: 2px;

      &.flown {
        background: #ffb703;
        box-shadow: 0 0 8px rgba(255, 183, 3, 0.8);
      }

      &.upcoming {
        background: repeating-linear-gradient(90deg, #00d4ff, #00d4ff 4px, transparent 4px, transparent 8px);
      }
    }

    .legend-text {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.85);
      font-weight: 500;
    }
  }

  .legend-divider {
    width: 1px;
    height: 12px;
    background: rgba(255, 255, 255, 0.15);
  }
}

.ai-event-toggle-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: rgba(11, 15, 25, 0.85);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border: 1px solid rgba(168, 85, 247, 0.35);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  transition: all 0.25s ease;

  .pill-left {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
  }

  .ai-badge-icon {
    font-size: 14px;
  }

  .ai-label {
    font-size: 13px;
    color: #e2e8f0;
    font-weight: 500;
  }

  .ai-count {
    font-size: 11px;
    color: #ffffff;
    background: #7c3aed;
    padding: 1px 6px;
    border-radius: 10px;
    font-weight: 600;
  }

  &.active {
    border-color: rgba(168, 85, 247, 0.7);
    box-shadow: 0 0 16px rgba(168, 85, 247, 0.25);
  }
}

/* 主飞行数据面板 */
.flight-data-panel {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 400px;
  max-height: calc(100vh - 140px);
  overflow-y: auto;
  background: rgba(11, 15, 25, 0.88);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 14px;
  z-index: 1000;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.6);
}

.flight-data-panel :deep(.ant-card-head) {
  background: rgba(255, 255, 255, 0.04);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding: 12px 16px;
}

.flight-data-panel :deep(.ant-card-head-title) {
  padding: 0;
  color: #fff;
  font-weight: 600;
}

.flight-data-panel :deep(.ant-card-body) {
  padding: 16px;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-title :deep(.ant-badge-status-text) {
  color: #fff;
  font-size: 12px;
}

.section {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.section:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.section-title {
  color: #00d4ff;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.data-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 3px 0;
}

.data-item .label {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
}

.data-item .value {
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.battery-low {
  color: #ff4d4f !important;
  animation: blink 1s infinite;
}

.battery-medium {
  color: #faad14 !important;
}

@keyframes blink {
  0%, 50%, 100% { opacity: 1; }
  25%, 75% { opacity: 0.5; }
}

/* AI 事件详情卡片 (左侧悬浮) */
.ai-event-detail-popup {
  position: absolute;
  top: 76px;
  left: 20px;
  width: 360px;
  z-index: 1000;
  pointer-events: auto;
}

.ai-event-card {
  background: rgba(11, 15, 25, 0.92);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(168, 85, 247, 0.4);
  border-radius: 14px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.7), 0 0 20px rgba(168, 85, 247, 0.15);
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  gap: 8px;
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #c084fc;
  font-weight: 600;
  font-size: 14px;
  flex: 1;
  min-width: 0;

  .detail-name {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.detail-nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-btn {
  background: rgba(168, 85, 247, 0.15);
  border: 1px solid rgba(168, 85, 247, 0.4);
  color: #c084fc;
  border-radius: 6px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 14px;

  &:hover:not(:disabled) {
    background: rgba(168, 85, 247, 0.3);
  }

  &:disabled {
    opacity: 0.3;
    cursor: not-allowed;
  }
}

.nav-indicator {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.7);
}

.detail-close {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.7);
  border-radius: 6px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 12px;

  &:hover {
    background: rgba(255, 77, 79, 0.2);
    color: #ff4d4f;
    border-color: rgba(255, 77, 79, 0.5);
  }
}

.detail-img-section {
  padding: 10px 14px;
  text-align: center;
  position: relative;

  .detail-img {
    width: 100%;
    max-height: 180px;
    object-fit: cover;
    border-radius: 8px;
    border: 1px solid rgba(255, 255, 255, 0.1);
    cursor: pointer;
    transition: transform 0.2s ease;

    &:hover {
      transform: scale(1.02);
    }
  }

  .detail-img-hint {
    font-size: 10px;
    color: rgba(255, 255, 255, 0.4);
    margin-top: 4px;
  }
}

.detail-info-grid {
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.detail-info-item {
  display: flex;
  justify-content: space-between;
  font-size: 12px;

  .info-label {
    color: rgba(255, 255, 255, 0.6);
  }

  .info-value {
    color: #ffffff;
    font-weight: 500;

    &.highlight {
      color: #38bdf8;
      font-weight: 600;
    }
  }
}

.detail-video-section {
  padding: 10px 14px;

  .section-title {
    font-size: 12px;
    color: #c084fc;
    margin-bottom: 6px;
  }

  .detail-video {
    width: 100%;
    border-radius: 8px;
  }
}

.detail-actions {
  padding: 10px 14px 14px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

/* Cluster Picker */
.cluster-picker {
  position: absolute;
  bottom: 120px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  background: rgba(11, 15, 25, 0.95);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(168, 85, 247, 0.4);
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.6);
  min-width: 320px;
  max-width: 80vw;
  overflow: hidden;
}

.cluster-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(168, 85, 247, 0.2);

  .cluster-picker-title {
    font-size: 13px;
    color: #c4b5fd;

    strong {
      color: #a855f7;
    }
  }

  .cluster-picker-close {
    background: transparent;
    border: none;
    color: rgba(255, 255, 255, 0.6);
    cursor: pointer;
    font-size: 12px;

    &:hover {
      color: #ff4d4f;
    }
  }
}

.cluster-picker-list {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  overflow-x: auto;
}

.cluster-picker-card {
  flex-shrink: 0;
  width: 100px;
  border-radius: 8px;
  border: 1px solid rgba(168, 85, 247, 0.2);
  background: rgba(168, 85, 247, 0.08);
  cursor: pointer;
  transition: all 0.2s ease;
  overflow: hidden;

  &:hover {
    border-color: rgba(168, 85, 247, 0.6);
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(168, 85, 247, 0.3);
  }

  .picker-card-img-wrap {
    width: 100px;
    height: 70px;
    overflow: hidden;
    background: rgba(0, 0, 0, 0.4);

    .picker-card-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .picker-card-no-img {
    width: 100%;
    height: 70px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #a855f7;
    font-size: 18px;
    font-weight: 700;
  }

  .picker-card-info {
    padding: 6px;

    .picker-card-cls {
      font-size: 11px;
      font-weight: 600;
      color: #ffffff;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .picker-card-time {
      font-size: 10px;
      color: rgba(255, 255, 255, 0.5);
      margin-top: 2px;
    }

    .picker-card-conf {
      font-size: 10px;
      margin-top: 2px;
    }
  }
}

.cluster-fade-enter-active,
.cluster-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.cluster-fade-enter-from,
.cluster-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(12px);
}

/* 全屏图片预览 */
.image-fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.9);
  backdrop-filter: blur(10px);
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  .fullscreen-img {
    max-width: 90vw;
    max-height: 85vh;
    border-radius: 8px;
    box-shadow: 0 0 40px rgba(0, 0, 0, 0.8);
  }

  .fullscreen-hint {
    color: rgba(255, 255, 255, 0.6);
    font-size: 13px;
    margin-top: 14px;
  }
}

.conf-high {
  color: #52c41a !important;
}

.conf-medium {
  color: #faad14 !important;
}

.conf-low {
  color: #ff4d4f !important;
}

.font-mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
}

/* ====== DJI OSD 飞行姿态与云台 FOV 样式 ====== */
.detail-osd-section {
  padding: 12px 14px;
  border-top: 1px solid rgba(56, 189, 248, 0.15);
  background: rgba(56, 189, 248, 0.03);

  .section-title {
    font-size: 13px;
    font-weight: 700;
    color: #38bdf8;
    margin-bottom: 10px;
    display: flex;
    align-items: center;
    gap: 6px;
  }
}

.osd-card-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.osd-sub-card {
  background: rgba(4, 20, 40, 0.65);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 8px;
  padding: 8px 10px;

  .sub-card-title {
    font-size: 11px;
    font-weight: 600;
    color: #94a3b8;
    margin-bottom: 6px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
}

.osd-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  padding: 2.5px 0;

  .osd-k {
    color: #94a3b8;
  }

  .osd-v {
    color: #f1f5f9;
    font-weight: 500;
    font-family: 'JetBrains Mono', Consolas, monospace;

    &.highlight {
      color: #38bdf8;
      font-weight: 600;
    }

    &.text-cyan {
      color: #00d4ff;
      font-weight: 600;
    }
  }
}

.flight-data-panel::-webkit-scrollbar,
.ai-event-card::-webkit-scrollbar,
.cluster-picker-list::-webkit-scrollbar {
  width: 4px;
  height: 4px;
}

.flight-data-panel::-webkit-scrollbar-thumb,
.ai-event-card::-webkit-scrollbar-thumb,
.cluster-picker-list::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
}

/* ====== 溯源多次复核样式 ====== */
.detail-suyuan-section {
  padding: 10px 14px;
  border-top: 1px solid rgba(251, 191, 36, 0.15);
  background: rgba(251, 191, 36, 0.03);
}

.suyuan-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
  margin-bottom: 6px;
}

.suyuan-title {
  font-size: 13px;
  font-weight: 700;
  color: #fbbf24;
  display: flex;
  align-items: center;
  gap: 6px;
}

.suyuan-count {
  font-size: 11px;
  background: rgba(251, 191, 36, 0.18);
  color: #fbbf24;
  padding: 1px 7px;
  border-radius: 10px;
}

.suyuan-toggle {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.4);
}

.suyuan-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
  padding: 8px 0;

  &--error {
    color: #f87171;
  }

  &--empty {
    color: rgba(255, 255, 255, 0.3);
    justify-content: center;
    padding: 12px 0;
  }
}

.suyuan-spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid rgba(251, 191, 36, 0.2);
  border-top-color: #fbbf24;
  border-radius: 50%;
  animation: su-spin 0.7s linear infinite;
}

@keyframes su-spin {
  to { transform: rotate(360deg); }
}

.suyuan-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.suyuan-item {
  background: rgba(4, 20, 40, 0.55);
  border: 1px solid rgba(251, 191, 36, 0.15);
  border-radius: 8px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.suyuan-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.suyuan-badge {
  font-size: 11px;
  font-weight: 700;
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.15);
  padding: 2px 8px;
  border-radius: 6px;
  white-space: nowrap;
}

.suyuan-item-time {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.45);
  flex: 1;
  min-width: 0;
}

.suyuan-result {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
  white-space: nowrap;

  &--ok {
    background: rgba(52, 211, 153, 0.15);
    color: #34d399;
  }

  &--bad {
    background: rgba(248, 113, 113, 0.15);
    color: #f87171;
  }
}

.suyuan-row {
  display: flex;
  font-size: 12px;
  gap: 6px;
  align-items: flex-start;
}

.suyuan-label {
  color: #94a3b8;
  white-space: nowrap;
  flex-shrink: 0;
}

.suyuan-value {
  color: #e2e8f0;
  word-break: break-all;
}

.suyuan-img-wrap {
  margin-top: 4px;
}

.suyuan-img {
  width: 100%;
  max-height: 120px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  cursor: pointer;
  transition: transform 0.2s ease;

  &:hover {
    transform: scale(1.02);
  }
}
</style>
