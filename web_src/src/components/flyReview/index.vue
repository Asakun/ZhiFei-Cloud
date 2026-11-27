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
        <div class="flex justify-between items-center py-2.5 text-sm">
          <span class="text-white/70">记录时间:</span>
          <span class="font-semibold font-mono text-xs">{{ currentData?.time?.slice(11, 19) }}</span>
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

<script setup>
import * as Cesium from 'cesium'
import { ref, computed, watch, onMounted, onUnmounted, markRaw, shallowRef } from 'vue'

const cesiumContainerRef = ref(null)
const viewerRef = shallowRef(null)

// Cesium配置
const cesiumPath = '/cesium/'
const accessToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...' // 替换为你的token

// 真实无人机数据
const rawTrajectoryData = [
  { lat: 29.601622, lon: 106.33121, height: 278.26, yaw: 17.4, pitch: -2.1, roll: -0.1, speed: 0.0, vspeed: 0.0, battery: 87, time: 429885.66 },
  { lat: 29.601622, lon: 106.33121, height: 278.25, yaw: 17.4, pitch: -2.1, roll: -0.1, speed: 0.0, vspeed: 0.0, battery: 87, time: 429885.66 },
  { lat: 29.601622, lon: 106.33121, height: 278.24, yaw: 17.4, pitch: -2.1, roll: -0.1, speed: 0.0, vspeed: 0.0, battery: 87, time: 429885.66 },
  { lat: 29.601662, lon: 106.33121, height: 281.54, yaw: 25.4, pitch: -1.0, roll: -0.5, speed: 0.0, vspeed: 2.4, battery: 87, time: 429889.84 },
  { lat: 29.601624, lon: 106.3312, height: 292.24, yaw: 25.5, pitch: -0.3, roll: -1.2, speed: 0.0, vspeed: 5.9, battery: 87, time: 429889.84 },
  { lat: 29.601624, lon: 106.3312, height: 304.34, yaw: 25.5, pitch: -0.1, roll: -0.7, speed: 0.0, vspeed: 6.0, battery: 87, time: 429893.72 },
  { lat: 29.601624, lon: 106.3312, height: 316.14, yaw: 25.5, pitch: -0.3, roll: -0.4, speed: 0.0, vspeed: 5.9, battery: 86, time: 429895.66 },
  { lat: 29.601622, lon: 106.3312, height: 324.44, yaw: 25.5, pitch: -1.6, roll: -0.4, speed: 0.0, vspeed: 2.1, battery: 86, time: 429897.6 },
  { lat: 29.601622, lon: 106.33121, height: 326.94, yaw: 25.5, pitch: -1.0, roll: -1.4, speed: 0.0, vspeed: 0.7, battery: 86, time: 429899.53 },
  { lat: 29.601624, lon: 106.33121, height: 327.84, yaw: 25.5, pitch: -1.0, roll: -1.1, speed: 0.0, vspeed: 0.2, battery: 86, time: 429901.5 },
  { lat: 29.601624, lon: 106.33121, height: 328.04, yaw: 25.5, pitch: -1.3, roll: -1.5, speed: 0.0, vspeed: 0.0, battery: 86, time: 429901.5 },
]

// 数据转换
const trajectoryData = rawTrajectoryData.map(d => ({
  lat: d.lat,
  lon: d.lon,
  height: d.height,
  yaw: d.yaw,
  pitch: d.pitch,
  roll: d.roll,
  speed: d.speed,
  verticalSpeed: d.vspeed,
  battery: d.battery,
  time: d.time,
}))

// 响应式变量
const viewerRef = ref(null)
const isPlaying = ref(false)
const currentFrame = ref(0)
const speed = ref(1)
const showDroneLabel = ref(true)
const alpha = ref(1)
const brightness = ref(1)
const contrast = ref(1)

// 计算属性
const totalFrames = computed(() => trajectoryData.length)
const currentData = computed(() => trajectoryData[currentFrame.value])

const dronePosition = computed(() => {
  if (!currentData.value) return null
  return [currentData.value.lon, currentData.value.lat, currentData.value.height]
})

const droneHeading = computed(() => currentData.value?.yaw || 0)
const dronePitch = computed(() => currentData.value?.pitch || 0)
const droneRoll = computed(() => currentData.value?.roll || 0)

const droneLabel = computed(() => {
  if (!currentData.value) return ''
  return `H: ${currentData.value.height.toFixed(1)}m\nY: ${currentData.value.yaw.toFixed(1)}° P: ${currentData.value.pitch.toFixed(1)}° R: ${currentData.value.roll.toFixed(1)}°`
})

// 轨迹线数据
const trajectoryPolyline = computed(() => {
  if (trajectoryData.length === 0) return null
  return {
    positions: trajectoryData.map(p => [p.lon, p.lat, p.height]),
    material: { color: [128, 128, 128, 100] },
  }
})

const playedTrajectoryPolyline = computed(() => {
  if (currentFrame.value === 0) return null
  const playedData = trajectoryData.slice(0, currentFrame.value + 1)
  return {
    positions: playedData.map(p => [p.lon, p.lat, p.height]),
    material: { color: [0, 255, 0, 255] },
  }
})

const dronePathPolyline = computed(() => {
  if (currentFrame.value === 0) return null
  const pathData = trajectoryData.slice(0, currentFrame.value + 1)
  return {
    positions: pathData.map(p => [p.lon, p.lat, p.height]),
    material: { color: [255, 200, 0, 150] },
  }
})

// 轨迹点
const trajectoryPoints = computed(() => {
  return trajectoryData.map((p, index) => {
    if (index === 0) {
      return {
        position: [p.lon, p.lat, p.height],
        pixelSize: 10,
        color: [34, 221, 136, 255],
        outlineColor: [255, 255, 255, 255],
      }
    } else if (index === trajectoryData.length - 1) {
      return {
        position: [p.lon, p.lat, p.height],
        pixelSize: 10,
        color: [255, 170, 34, 255],
        outlineColor: [255, 255, 255, 255],
      }
    } else if (index <= currentFrame.value) {
      return {
        position: [p.lon, p.lat, p.height],
        pixelSize: 6,
        color: [0, 255, 0, 200],
        outlineColor: [255, 255, 255, 100],
      }
    } else {
      return {
        position: [p.lon, p.lat, p.height],
        pixelSize: 4,
        color: [200, 200, 200, 100],
        outlineColor: [255, 255, 255, 50],
      }
    }
  })
})

// 起点和终点
const startPosition = computed(() => {
  if (trajectoryData.length === 0) return null
  const p = trajectoryData[0]
  return [p.lon, p.lat, p.height]
})

const endPosition = computed(() => {
  if (trajectoryData.length === 0) return null
  const p = trajectoryData[trajectoryData.length - 1]
  return [p.lon, p.lat, p.height]
})

// 统计数据
const startHeight = computed(() => trajectoryData[0]?.height)
const maxHeight = computed(() => Math.max(...trajectoryData.map(p => p.height)))
const flightDistance = computed(() => {
  let distance = 0
  for (let i = 1; i < trajectoryData.length; i++) {
    const prev = trajectoryData[i - 1]
    const curr = trajectoryData[i]
    const dx = (curr.lon - prev.lon) * 111000 * Math.cos((curr.lat * Math.PI) / 180)
    const dy = (curr.lat - prev.lat) * 111000
    const dz = curr.height - prev.height
    distance += Math.sqrt(dx * dx + dy * dy + dz * dz)
  }
  return distance
})

// 无人机模型URL（使用简单的glTF模型）
const droneModelUrl = computed(() => {
  return 'https://example.com/drone-model.glb' // 替换为实际模型URL
})

// 方法
const togglePlay = () => {
  isPlaying.value = !isPlaying.value
}

const reset = () => {
  currentFrame.value = 0
  isPlaying.value = false
}

const increaseSpeed = () => {
  speed.value = Math.min(speed.value + 0.5, 3)
}

const decreaseSpeed = () => {
  speed.value = Math.max(speed.value - 0.5, 0.5)
}

const onFrameChange = () => {
  isPlaying.value = false
}

const onViewerReady = ({ Cesium, viewer }) => {
  if (trajectoryData.length > 0) {
    const p = trajectoryData[Math.floor(trajectoryData.length / 2)]
    viewer.camera.flyTo({
      destination: Cesium.Cartesian3.fromDegrees(p.lon, p.lat, p.height + 500),
      duration: 1,
    })
  }
}

const initNativeViewer = () => {
  if (!cesiumContainerRef.value) return

  const tdtToken = '4db514621ce57a692181a8bc4ddb9d72'
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
})

// 播放动画
const animationInterval = ref(null)

watch(isPlaying, (newVal) => {
  if (newVal) {
    animationInterval.value = setInterval(() => {
      currentFrame.value++
      if (currentFrame.value >= totalFrames.value) {
        currentFrame.value = totalFrames.value - 1
        isPlaying.value = false
      }
    }, 100 / speed.value)
  } else {
    if (animationInterval.value) {
      clearInterval(animationInterval.value)
    }
  }
})

watch(speed, () => {
  if (isPlaying.value) {
    if (animationInterval.value) clearInterval(animationInterval.value)
    animationInterval.value = setInterval(() => {
      currentFrame.value++
      if (currentFrame.value >= totalFrames.value) {
        currentFrame.value = totalFrames.value - 1
        isPlaying.value = false
      }
    }, 100 / speed.value)
  }
})

onMounted(() => {
  return () => {
    if (animationInterval.value) {
      clearInterval(animationInterval.value)
    }
  }
})
</script>

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
