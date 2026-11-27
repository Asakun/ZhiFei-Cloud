<script setup>
import * as Cesium from 'cesium'
import { ref, computed, watch, onMounted, onUnmounted, markRaw, shallowRef } from 'vue'
import rawTrajectoryData from '@/assets/rawTrajectoryData.json'

const cesiumContainerRef = ref(null)
const viewerRef = shallowRef(null)

// =============== 数据解析与转换 ==================
const trajectoryData = rawTrajectoryData.map(d => {
  const osd = d.osd || {}
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
    time: d.create_time,
  }
}).filter(p => p.lat && p.lon)

// =============== 响应式变量 ==================
const viewerRef = ref(null)
const isPlaying = ref(false)
const currentFrame = ref(0)
const speed = ref(1)
const showDroneLabel = ref(true)
const showTrajectoryPoints = ref(true)
const showStats = ref(true)
const followDrone = ref(false)
const viewMode = ref('overview') // overview, follow, fpv

// =============== 计算属性 ==================
const totalFrames = computed(() => trajectoryData.length)
const currentData = computed(() => trajectoryData[currentFrame.value])
const progress = computed(() => (currentFrame.value / (totalFrames.value - 1)) * 100)

const dronePosition = computed(() => {
  if (!currentData.value) return null
  return [currentData.value.lon, currentData.value.lat, currentData.value.height]
})

const droneLabel = computed(() => {
  if (!currentData.value) return ''
  return `高度: ${currentData.value.height.toFixed(1)}m\n速度: ${currentData.value.speed.toFixed(1)}m/s\n电量: ${currentData.value.battery}%`
})

// =============== 轨迹线优化 ==================
const trajectoryPolyline = computed(() => ({
  positions: trajectoryData.map(p => [p.lon, p.lat, p.height]),
  material: {
    fabric: {
      type: 'PolylineGlow',
      uniforms: {
        glowPower: 0.2,
        color: { red: 0.4, green: 0.6, blue: 1.0, alpha: 0.6 }
      }
    }
  },
}))

const playedTrajectoryPolyline = computed(() => {
  const played = trajectoryData.slice(0, currentFrame.value + 1)
  if (played.length < 2) return null
  return {
    positions: played.map(p => [p.lon, p.lat, p.height]),
    material: {
      fabric: {
        type: 'PolylineArrow',
        uniforms: {
          color: { red: 0.0, green: 1.0, blue: 0.3, alpha: 1.0 }
        }
      }
    },
  }
})

const trajectoryPoints = computed(() =>
    trajectoryData.map((p, i) => ({
      position: [p.lon, p.lat, p.height],
      pixelSize: i <= currentFrame.value ? 8 : 5,
      color: i <= currentFrame.value
          ? [0, 255, 100, 255]
          : [150, 150, 150, 120],
      outlineColor: i <= currentFrame.value
          ? [0, 150, 50, 255]
          : [100, 100, 100, 150],
      outlineWidth: 2,
    }))
)

// =============== 统计数据 ==================
const startPosition = computed(() =>
    trajectoryData[0] ? [trajectoryData[0].lon, trajectoryData[0].lat, trajectoryData[0].height] : null
)

const endPosition = computed(() =>
    trajectoryData.at(-1) ? [trajectoryData.at(-1).lon, trajectoryData.at(-1).lat, trajectoryData.at(-1).height] : null
)

const flightDistance = computed(() => {
  let dist = 0
  for (let i = 1; i < trajectoryData.length; i++) {
    const a = trajectoryData[i - 1]
    const b = trajectoryData[i]
    const dx = (b.lon - a.lon) * 111000 * Math.cos((a.lat * Math.PI) / 180)
    const dy = (b.lat - a.lat) * 111000
    const dz = b.height - a.height
    dist += Math.sqrt(dx ** 2 + dy ** 2 + dz ** 2)
  }
  return dist
})

const maxHeight = computed(() => Math.max(...trajectoryData.map(p => p.height)))
const maxSpeed = computed(() => Math.max(...trajectoryData.map(p => p.speed)))
const flightDuration = computed(() => {
  if (trajectoryData.length < 2) return 0
  const start = new Date(trajectoryData[0].time).getTime()
  const end = new Date(trajectoryData.at(-1).time).getTime()
  return (end - start) / 1000 / 60 // 分钟
})

// =============== 控制逻辑 ==================
const togglePlay = () => (isPlaying.value = !isPlaying.value)

const reset = () => {
  isPlaying.value = false
  currentFrame.value = 0
}

const skipTo = (frame) => {
  currentFrame.value = Math.max(0, Math.min(frame, totalFrames.value - 1))
}

const adjustSpeed = (delta) => {
  speed.value = Math.max(0.25, Math.min(speed.value + delta, 10))
}

const changeViewMode = (mode) => {
  viewMode.value = mode
  if (mode === 'follow') followDrone.value = true
  else followDrone.value = false
  updateCamera()
}

// =============== Cesium 初始化 ==================
const droneModelUrl = '/glb/DJI.glb'

const onViewerReady = ({ Cesium, viewer }) => {
  viewerRef.value = viewer

  // 禁用默认双击事件
  viewer.screenSpaceEventHandler.removeInputAction(Cesium.ScreenSpaceEventType.LEFT_DOUBLE_CLICK)

  // 初始化视角
  if (trajectoryData.length > 0) {
    const mid = trajectoryData[Math.floor(trajectoryData.length / 2)]
    viewer.camera.flyTo({
      destination: Cesium.Cartesian3.fromDegrees(mid.lon, mid.lat, mid.height + 1500),
      orientation: {
        heading: Cesium.Math.toRadians(0),
        pitch: Cesium.Math.toRadians(-45),
        roll: 0
      },
      duration: 2,
    })
  }

  // 创建无人机 Entity
  const entity = viewer.entities.add({
    id: 'drone-model',
    position: new Cesium.CallbackProperty(() => {
      const p = trajectoryData[currentFrame.value]
      return p ? Cesium.Cartesian3.fromDegrees(p.lon, p.lat, p.height) : null
    }, false),
    orientation: new Cesium.CallbackProperty(() => {
      const p = trajectoryData[currentFrame.value]
      if (!p) return Cesium.Transforms.headingPitchRollQuaternion(
          Cesium.Cartesian3.fromDegrees(0, 0, 0),
          new Cesium.HeadingPitchRoll(0, 0, 0)
      )
      const hpr = new Cesium.HeadingPitchRoll(
          Cesium.Math.toRadians(p.yaw),
          Cesium.Math.toRadians(p.pitch),
          Cesium.Math.toRadians(p.roll)
      )
      return Cesium.Transforms.headingPitchRollQuaternion(
          Cesium.Cartesian3.fromDegrees(p.lon, p.lat, p.height),
          hpr
      )
    }, false),
    model: {
      uri: droneModelUrl,
      minimumPixelSize: 64,
      maximumScale: 0.05,
    },
    label: {
      text: new Cesium.CallbackProperty(() => {
        if (!showDroneLabel.value) return ''
        const p = trajectoryData[currentFrame.value]
        return p ? `高度: ${p.height.toFixed(1)}m\n速度: ${p.speed.toFixed(1)}m/s\n电量: ${p.battery}%` : ''
      }, false),
      font: '14px Microsoft YaHei',
      fillColor: Cesium.Color.WHITE,
      showBackground: true,
      backgroundColor: Cesium.Color.fromCssColorString('rgba(0, 0, 0, 0.7)'),
      backgroundPadding: new Cesium.Cartesian2(8, 4),
      pixelOffset: new Cesium.Cartesian2(0, -50),
      disableDepthTestDistance: Number.POSITIVE_INFINITY,
    },
  })

  viewer.droneEntity = entity

  // 加载 3D Tiles
  Cesium.Cesium3DTileset.fromUrl('/b3dms/terra_b3dms/tileset.json', {
    maximumScreenSpaceError: 1,
  }).then(tileset => {
    viewer.scene.primitives.add(tileset)
  }).catch(e => {
    console.error('加载 3D Tiles 失败:', e)
  })
}

// =============== 相机跟随 ==================
const updateCamera = () => {
  if (!viewerRef.value || !dronePosition.value) return

  const viewer = viewerRef.value
  viewer.trackedEntity = viewer.droneEntity.getCesiumObject()
  /*const Cesium = window.Cesium
  const pos = Cesium.Cartesian3.fromDegrees(...dronePosition.value)

  if (viewMode.value === 'follow') {
    viewer.camera.lookAt(
        pos,
        new Cesium.Cartesian3(0, -300, 150)
    )
  } else if (viewMode.value === 'fpv') {
    const heading = Cesium.Math.toRadians(currentData.value.yaw)
    viewer.camera.lookAt(
        pos,
        new Cesium.HeadingPitchRange(heading, Cesium.Math.toRadians(-10), 50)
    )
  }*/
}

// =============== 播放动画 ==================
let animationTimer = null

const startAnimation = () => {
  if (animationTimer) clearInterval(animationTimer)
  animationTimer = setInterval(() => {
    currentFrame.value++
    if (followDrone.value) updateCamera()
    if (currentFrame.value >= totalFrames.value - 1) {
      clearInterval(animationTimer)
      isPlaying.value = false
    }
  }, 100 / speed.value)
}

watch(isPlaying, val => {
  if (val) startAnimation()
  else if (animationTimer) clearInterval(animationTimer)
})

watch(speed, () => {
  if (isPlaying.value) startAnimation()
})

watch(currentFrame, () => {
  if (followDrone.value && !isPlaying.value) updateCamera()
})

const initNativeViewer = () => {
  if (!cesiumContainerRef.value) return

  const tdtToken = 'bc432cb6c95c860ae638a3c0573d1e7e'
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

  const viewer = new Cesium.Viewer(cesiumContainerRef.value, {
    animation: false,
    timeline: false,
    fullscreenButton: false,
    baseLayerPicker: false,
    geocoder: false,
    baseLayer: new Cesium.ImageryLayer(imgProvider)
  })

  viewer.imageryLayers.addImageryProvider(ciaProvider)
  const rawViewer = markRaw(viewer)
  viewerRef.value = rawViewer

  onViewerReady({ Cesium, viewer: rawViewer })
}

onMounted(() => {
  initNativeViewer()
  document.addEventListener('keydown', handleKeyPress)
})

onUnmounted(() => {
  if (animationTimer) clearInterval(animationTimer)
  document.removeEventListener('keydown', handleKeyPress)
})

// =============== 键盘控制 ==================
const handleKeyPress = (e) => {
  switch(e.key) {
    case ' ':
      e.preventDefault()
      togglePlay()
      break
    case 'ArrowLeft':
      skipTo(currentFrame.value - 10)
      break
    case 'ArrowRight':
      skipTo(currentFrame.value + 10)
      break
    case 'r':
    case 'R':
      reset()
      break
  }
}
</script>
<template>
  <div class="relative w-screen h-screen overflow-hidden font-sans">
    <div ref="cesiumContainerRef" class="h-full w-full"></div>

    <!-- 顶部标题栏 -->
    <div class="absolute top-0 left-0 right-0 h-16 bg-gradient-to-b from-black/85 to-black/60 backdrop-blur-md flex items-center justify-between px-6 z-50 shadow-2xl">
      <div class="flex items-center gap-3 text-white text-xl font-semibold">
        <span class="text-3xl">✈️</span>
        <span>无人机飞行轨迹回放系统</span>
      </div>
      <div class="flex gap-2">
        <button
            :class="['px-4 py-2 rounded-lg text-sm text-white border transition-all',
                   viewMode === 'overview' ? 'bg-gradient-to-r from-purple-500 to-indigo-600 border-transparent shadow-lg shadow-purple-500/50' : 'bg-white/10 border-white/20 hover:bg-white/20']"
            @click="changeViewMode('overview')"
        >
          🌍 总览
        </button>
        <button
            :class="['px-4 py-2 rounded-lg text-sm text-white border transition-all',
                   viewMode === 'follow' ? 'bg-gradient-to-r from-purple-500 to-indigo-600 border-transparent shadow-lg shadow-purple-500/50' : 'bg-white/10 border-white/20 hover:bg-white/20']"
            @click="changeViewMode('follow')"
        >
          📹 跟随
        </button>
        <button
            :class="['px-4 py-2 rounded-lg text-sm text-white border transition-all',
                   viewMode === 'fpv' ? 'bg-gradient-to-r from-purple-500 to-indigo-600 border-transparent shadow-lg shadow-purple-500/50' : 'bg-white/10 border-white/20 hover:bg-white/20']"
            @click="changeViewMode('fpv')"
        >
          👁️ FPV
        </button>
      </div>
    </div>

    <!-- 左侧信息面板 -->
    <div class="absolute left-4 top-20 w-72 max-h-[calc(100vh-10rem)] overflow-y-auto bg-gradient-to-br from-black/85 to-slate-900/85 backdrop-blur-xl rounded-xl border border-white/10 shadow-2xl text-white">
      <div class="flex items-center justify-between p-4 text-base font-semibold border-b border-white/10 bg-white/5 rounded-t-xl">
        <span>📊 飞行数据</span>
        <button
            class="w-6 h-6 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center text-lg transition-all"
            @click="showStats = !showStats"
        >
          {{ showStats ? '−' : '+' }}
        </button>
      </div>
      <div v-if="showStats" class="p-4">
        <div class="flex justify-between items-center py-2.5 text-sm border-b border-white/5">
          <span class="text-white/70">当前帧:</span>
          <span class="font-semibold font-mono text-cyan-400">{{ currentFrame + 1 }} / {{ totalFrames }}</span>
        </div>
        <div class="flex justify-between items-center py-2.5 text-sm border-b border-white/5">
          <span class="text-white/70">飞行高度:</span>
          <span class="font-semibold font-mono">{{ currentData?.height?.toFixed(1) }} m</span>
        </div>
        <div class="flex justify-between items-center py-2.5 text-sm border-b border-white/5">
          <span class="text-white/70">水平速度:</span>
          <span class="font-semibold font-mono">{{ currentData?.speed?.toFixed(1) }} m/s</span>
        </div>
        <div class="flex justify-between items-center py-2.5 text-sm border-b border-white/5">
          <span class="text-white/70">垂直速度:</span>
          <span class="font-semibold font-mono">{{ currentData?.verticalSpeed?.toFixed(1) }} m/s</span>
        </div>
        <div class="flex justify-between items-center py-2.5 text-sm border-b border-white/5">
          <span class="text-white/70">电池电量:</span>
          <span
              :class="['font-semibold font-mono',
                     currentData?.battery < 20 ? 'text-red-400' :
                     currentData?.battery < 50 ? 'text-yellow-400' : 'text-green-400']"
          >
            {{ currentData?.battery }}%
          </span>
        </div>

        <div class="h-px bg-gradient-to-r from-transparent via-white/20 to-transparent my-3"></div>

        <div class="flex justify-between items-center py-2.5 text-sm border-b border-white/5">
          <span class="text-white/70">航线总长:</span>
          <span class="font-semibold font-mono">{{ (flightDistance / 1000).toFixed(2) }} km</span>
        </div>
        <div class="flex justify-between items-center py-2.5 text-sm border-b border-white/5">
          <span class="text-white/70">最大高度:</span>
          <span class="font-semibold font-mono">{{ maxHeight.toFixed(1) }} m</span>
        </div>
        <div class="flex justify-between items-center py-2.5 text-sm border-b border-white/5">
          <span class="text-white/70">最大速度:</span>
          <span class="font-semibold font-mono">{{ maxSpeed.toFixed(1) }} m/s</span>
        </div>
        <div class="flex justify-between items-center py-2.5 text-sm border-b border-white/5">
          <span class="text-white/70">飞行时长:</span>
          <span class="font-semibold font-mono">{{ flightDuration.toFixed(1) }} 分钟</span>
        </div>

      </div>
    </div>

    <!-- 右侧控制面板 -->
    <div class="absolute right-4 top-20 w-80 bg-gradient-to-br from-black/85 to-slate-900/85 backdrop-blur-xl rounded-xl border border-white/10 shadow-2xl text-white">
      <div class="p-4 text-base font-semibold border-b border-white/10 bg-white/5 rounded-t-xl">
        <span>🎮 播放控制</span>
      </div>
      <div class="p-4">
        <!-- 播放控制按钮 -->
        <div class="flex gap-2 mb-4">
          <button
              class="flex-1 py-2.5 bg-gradient-to-r from-purple-500 to-indigo-600 hover:from-purple-600 hover:to-indigo-700 rounded-lg text-sm font-medium transition-all shadow-lg shadow-purple-500/30 hover:shadow-purple-500/50 hover:-translate-y-0.5"
              @click="reset"
          >
            ⏮️ 重置
          </button>
          <button
              class="flex-[1.5] py-3 bg-gradient-to-r from-purple-500 to-indigo-600 hover:from-purple-600 hover:to-indigo-700 rounded-lg text-base font-medium transition-all shadow-lg shadow-purple-500/30 hover:shadow-purple-500/50 hover:-translate-y-0.5"
              @click="togglePlay"
          >
            {{ isPlaying ? '⏸️ 暂停' : '▶️ 播放' }}
          </button>
          <button
              class="flex-1 py-2.5 bg-gradient-to-r from-purple-500 to-indigo-600 hover:from-purple-600 hover:to-indigo-700 rounded-lg text-sm font-medium transition-all shadow-lg shadow-purple-500/30 hover:shadow-purple-500/50 hover:-translate-y-0.5"
              @click="skipTo(currentFrame + 10)"
          >
            ⏭️ 快进
          </button>
        </div>

        <!-- 进度条 -->
        <div class="mb-4">
          <div class="relative h-2 bg-white/10 rounded-full overflow-hidden mb-2">
            <div
                class="h-full bg-gradient-to-r from-cyan-400 to-green-400 transition-all duration-100 rounded-full"
                :style="{ width: progress + '%' }"
            ></div>
            <input
                type="range"
                class="absolute top-0 left-0 w-full h-full opacity-0 cursor-pointer"
                :min="0"
                :max="totalFrames - 1"
                v-model.number="currentFrame"
                @input="isPlaying = false"
            />
          </div>
          <div class="text-right text-xs text-white/60 font-mono">{{ progress.toFixed(1) }}%</div>
        </div>

        <!-- 速度控制 -->
        <div class="flex items-center justify-between mb-4">
          <span class="text-sm text-white/80">播放速度</span>
          <div class="flex items-center gap-3 bg-white/10 px-3 py-1.5 rounded-lg">
            <button
                class="w-7 h-7 rounded-md bg-white/15 hover:bg-white/25 transition-all text-base"
                @click="adjustSpeed(-0.25)"
            >
              −
            </button>
            <span class="font-mono font-semibold min-w-[50px] text-center text-cyan-400">{{ speed.toFixed(2) }}x</span>
            <button
                class="w-7 h-7 rounded-md bg-white/15 hover:bg-white/25 transition-all text-base"
                @click="adjustSpeed(0.25)"
            >
              +
            </button>
          </div>
        </div>

        <div class="h-px bg-gradient-to-r from-transparent via-white/20 to-transparent my-3"></div>

        <!-- 显示选项 -->
        <div class="flex flex-col gap-3">
          <label class="flex items-center gap-2.5 cursor-pointer text-sm text-white/90 hover:text-white transition-colors">
            <input type="checkbox" v-model="showDroneLabel" class="w-4 h-4 accent-purple-500 cursor-pointer" />
            <span>显示无人机标签</span>
          </label>
          <label class="flex items-center gap-2.5 cursor-pointer text-sm text-white/90 hover:text-white transition-colors">
            <input type="checkbox" v-model="showTrajectoryPoints" class="w-4 h-4 accent-purple-500 cursor-pointer" />
            <span>显示轨迹点</span>
          </label>
          <label class="flex items-center gap-2.5 cursor-pointer text-sm text-white/90 hover:text-white transition-colors">
            <input type="checkbox" v-model="followDrone" class="w-4 h-4 accent-purple-500 cursor-pointer" />
            <span>相机跟随</span>
          </label>
        </div>
      </div>
    </div>

    <!-- 底部提示 -->
    <div class="absolute bottom-4 left-1/2 -translate-x-1/2 bg-black/70 backdrop-blur-md px-6 py-2.5 rounded-full text-xs text-white/80 border border-white/10 whitespace-nowrap">
      <span>💡 快捷键: 空格=播放/暂停 | R=重置 | ←/→=快退/快进</span>
    </div>
  </div>
</template>

<style scoped>
/* 滚动条样式 */
.overflow-y-auto::-webkit-scrollbar {
  width: 6px;
}

.overflow-y-auto::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 3px;
}

.overflow-y-auto::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 3px;
}

.overflow-y-auto::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.3);
}
</style>

