<template>
  <div v-if="visible"
    class="absolute top-2 left-4 z-[900] bg-gray-900/95 backdrop-blur-xl rounded-2xl p-4 shadow-[0_10px_30px_rgba(0,0,0,0.5)] border border-gray-700/80 w-80 text-white transition-all duration-300">
    <!-- Header -->
    <div class="flex justify-between items-center mb-3 pb-2 border-b border-gray-800">
      <span class="text-white text-sm font-bold flex items-center gap-2">
        <span class="text-cyan-400 text-base">✈️</span>
        <span>{{ progress?.type === 'wayline' ? '航线任务进度' : '指点飞行进度' }}</span>
        <span v-if="isRth"
          class="px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-400 text-[10px] font-semibold border border-amber-500/30 flex items-center gap-1">
          <span class="w-1.5 h-1.5 rounded-full bg-amber-400 animate-ping"></span>
          返航中
        </span>
        <span v-else-if="progress?.status === 'wayline_progress' || progress?.status === 'in_progress'"
          class="px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 text-[10px] font-semibold border border-emerald-500/30 flex items-center gap-1">
          <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping"></span>
          飞行中
        </span>
        <span v-else-if="progress?.status === 'task_ready'"
          class="px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-400 text-[10px] font-semibold border border-amber-500/30">
          任务准备
        </span>
        <span v-else-if="progress?.status === 'wayline_ok' || progress?.status === 'ok'"
          class="px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-400 text-[10px] font-semibold border border-blue-500/30">
          ✓ 已完成
        </span>
      </span>
      <div class="cursor-pointer text-gray-400 hover:text-white hover:bg-gray-800 rounded-lg p-1 transition-colors"
        @click="handleClose">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
        </svg>
      </div>
    </div>

    <!-- 地铁站点线路图模式 (Wayline) -->
    <template v-if="progress?.type === 'wayline'">
      <!-- 1. 地铁区间聚焦卡片 (当前站 ➔ 下一站/返航机场) -->
      <div class="bg-gray-800/80 rounded-xl p-3 border border-gray-700/60 mb-3 shadow-inner">
        <div class="text-[11px] text-gray-400 mb-2 flex justify-between items-center">
          <span class="font-medium text-cyan-400 flex items-center gap-1">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"></path>
            </svg>
            {{ isRth ? '返航阶段区间' : '当前航段区间' }}
          </span>
          <span v-if="totalWaypointsCount > 0" class="text-[10px] text-gray-400 bg-gray-700/60 px-1.5 py-0.5 rounded">
            共 {{ totalWaypointsCount }} 个航点
          </span>
        </div>

        <!-- 站点导轨双站图 -->
        <div class="relative flex items-center justify-between px-2 py-2">
          <!-- 起点站 (当前航点) -->
          <div class="flex flex-col items-center z-10 min-w-[55px]">
            <div class="relative">
              <div class="w-7 h-7 rounded-full bg-blue-600 border-2 border-cyan-300 flex items-center justify-center font-bold text-xs shadow-[0_0_10px_rgba(6,182,212,0.6)] text-white">
                {{ currentPointIndex }}
              </div>
              <span class="absolute -top-5 left-1/2 -translate-x-1/2 whitespace-nowrap bg-blue-500/20 text-cyan-300 text-[9px] px-1 rounded border border-blue-500/30 font-medium">
                当前站
              </span>
            </div>
            <span class="text-[11px] font-semibold text-white mt-1">航点 {{ currentPointIndex }}</span>
          </div>

          <!-- 动态地铁线路轨道 + 无人机指示器 -->
          <div class="flex-1 mx-2 relative flex items-center h-8">
            <!-- 背景导轨 -->
            <div class="w-full h-2 bg-gray-700/80 rounded-full overflow-hidden relative border border-gray-600/40">
              <div class="h-full bg-gradient-to-r from-blue-500 via-cyan-400 to-teal-300 rounded-full animate-pulse"
                style="width: 100%"></div>
            </div>
            <!-- 沿导轨移动的飞行图标 -->
            <div class="drone-anim-container pointer-events-none">
              <span class="text-sm transform rotate-45 inline-block text-cyan-200 drop-shadow-[0_0_8px_rgba(6,182,212,0.9)]">✈️</span>
            </div>
          </div>

          <!-- 终点站 (下一航点 或 返航机场) -->
          <div class="flex flex-col items-center z-10 min-w-[70px]">
            <div class="relative">
              <div :class="[
                'w-7 h-7 rounded-full border-2 flex items-center justify-center font-bold text-xs animate-pulse shadow-md',
                isRth ? 'bg-amber-500 border-amber-300 text-white shadow-[0_0_12px_rgba(245,158,11,0.8)]' : 'bg-amber-500/20 border-amber-400 text-amber-300 shadow-[0_0_10px_rgba(245,158,11,0.5)]'
              ]">
                <template v-if="isRth">🏠</template>
                <template v-else>{{ nextPointIndex }}</template>
              </div>
              <span :class="[
                'absolute -top-5 left-1/2 -translate-x-1/2 whitespace-nowrap text-[9px] px-1 rounded border font-medium',
                isRth ? 'bg-amber-500/30 text-amber-200 border-amber-400/50' : 'bg-amber-500/20 text-amber-300 border-amber-500/30'
              ]">
                {{ isRth ? '返航机场' : '下一站' }}
              </span>
            </div>
            <span class="text-[11px] font-semibold text-amber-300 mt-1 truncate max-w-[85px] text-center">
              {{ isRth ? targetDockName : `航点 ${nextPointIndex}` }}
            </span>
          </div>
        </div>

        <!-- 航段信息 (剩余距离/预计时间) -->
        <div v-if="progress?.remainingDistance || progress?.remainingTime"
          class="mt-2 pt-2 border-t border-gray-700/40 grid grid-cols-2 gap-2 text-[11px]">
          <div v-if="progress?.remainingDistance !== undefined" class="flex items-center gap-1 text-gray-400">
            <span>剩余距离:</span>
            <span class="text-cyan-300 font-mono font-medium">{{ (progress?.remainingDistance ?? 0).toFixed(1) }} m</span>
          </div>
          <div v-if="progress?.remainingTime !== undefined" class="flex items-center gap-1 text-gray-400">
            <span>预计时间:</span>
            <span class="text-cyan-300 font-mono font-medium">{{ progress?.remainingTime ?? 0 }} 秒</span>
          </div>
        </div>
      </div>


    </template>

    <!-- 指点飞行模式 -->
    <template v-else>
      <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs my-2 bg-gray-800/60 rounded-xl p-2.5 border border-gray-700/50">
        <div class="text-gray-400">剩余距离:</div>
        <div class="text-cyan-300 font-medium font-mono">{{ (progress?.remainingDistance ?? 0).toFixed(1) }} m</div>
        <div class="text-gray-400">预计时间:</div>
        <div class="text-cyan-300 font-medium font-mono">{{ progress?.remainingTime ?? 0 }} 秒</div>
        <div class="text-gray-400">航点索引:</div>
        <div class="text-white font-medium font-mono">{{ progress?.wayPointIndex ?? 0 }}</div>
      </div>
      <!-- 进度条 -->
      <div v-if="progress?.status === 'wayline_progress' || progress?.status === 'in_progress'" class="mt-2">
        <div class="h-1.5 bg-gray-800 rounded-full overflow-hidden border border-gray-700/50">
          <div class="h-full bg-gradient-to-r from-blue-500 to-cyan-400 transition-all duration-300"
            :style="{ width: progressWidth }">
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

export interface FlightProgressInfo {
  visible?: boolean
  type?: 'wayline' | 'fly_to_point' | string
  status?: string
  flightId?: string
  wayPointIndex?: number
  remainingDistance?: number
  remainingTime?: number
  initialDistance?: number
  percent?: number
  plannedPathPoints?: any[]
  totalWaypoints?: number
  dockName?: string
  isRth?: boolean
  modeCode?: number
}

const props = defineProps<{
  visible: boolean
  progress?: FlightProgressInfo
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'close'): void
}>()

const route = useRoute()

const handleClose = () => {
  emit('update:visible', false)
  emit('close')
}

const currentPointIndex = computed(() => props.progress?.wayPointIndex ?? 0)
const nextPointIndex = computed(() => currentPointIndex.value + 1)

const totalWaypointsCount = computed(() => {
  if (props.progress?.plannedPathPoints && Array.isArray(props.progress.plannedPathPoints) && props.progress.plannedPathPoints.length > 0) {
    return props.progress.plannedPathPoints.length
  }
  if (props.progress?.totalWaypoints && props.progress.totalWaypoints > 0) {
    return props.progress.totalWaypoints
  }
  return 0
})

const isRth = computed(() => {
  // mode_code === 9 或者 10  表示无人机自动返航
  if (props.progress?.modeCode === 9 || props.progress?.modeCode === 10) return true
  if (props.progress?.isRth) return true
  return false
})

const targetDockName = computed(() => {
  return props.progress?.dockName || (route?.query?.dockName as string) || '返航机场'
})

const visibleStations = computed(() => {
  const curr = currentPointIndex.value
  const total = totalWaypointsCount.value
  const rth = isRth.value
  
  let maxCount = total > 0 ? total : Math.max(5, curr + 2)
  
  const list: Array<{ index: number; label: string; status: string; isHome?: boolean }> = []
  for (let i = 0; i < maxCount; i++) {
    let status: 'passed' | 'current' | 'next' | 'future' = 'future'
    if (rth) {
      status = 'passed'
    } else if (i < curr) {
      status = 'passed'
    } else if (i === curr) {
      status = 'current'
    } else if (i === curr + 1) {
      status = 'next'
    } else {
      status = 'future'
    }
    list.push({
      index: i,
      label: `#${i}`,
      status
    })
  }

  // 始终追加返航机场终端节点
  list.push({
    index: 999,
    label: targetDockName.value,
    isHome: true,
    status: rth ? 'next' : 'future'
  })

  return list
})

const progressWidth = computed(() => {
  if (!props.progress) return '0%'
  if (props.progress.type === 'wayline') {
    return `${props.progress.percent ?? 0}%`
  }
  const initial = props.progress.initialDistance ?? 0
  const remaining = props.progress.remainingDistance ?? 0
  if (initial > 0) {
    const calculated = ((1 - remaining / initial) * 100).toFixed(1)
    return `${calculated}%`
  }
  return '0%'
})
</script>

<style scoped>
@keyframes droneGlide {
  0% {
    left: 0%;
    transform: translate(-50%, -50%) scale(0.9);
    opacity: 0.6;
  }
  50% {
    left: 50%;
    transform: translate(-50%, -50%) scale(1.1);
    opacity: 1;
  }
  100% {
    left: 100%;
    transform: translate(-50%, -50%) scale(0.9);
    opacity: 0.6;
  }
}

.drone-anim-container {
  position: absolute;
  top: 50%;
  left: 0%;
  width: 100%;
  animation: droneGlide 2.5s ease-in-out infinite;
}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
