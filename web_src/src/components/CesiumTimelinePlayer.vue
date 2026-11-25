<template>
  <div class="cesium-custom-player-wrapper">
    <!-- 悬浮播放器主容器 -->
    <div class="player-container">
      
      <!-- 顶部信息栏：播放状态、北京时间显示、时间进度与循环模式 -->
      <div class="player-header">
        <!-- 左侧：状态与点位信息 -->
        <div class="header-left">
          <div class="status-indicator" :class="{ 'is-playing': isPlaying }">
            <span class="status-dot"></span>
            <span class="status-text">{{ isPlaying ? '轨迹回放中' : '回放已暂停' }}</span>
          </div>
          <div v-if="totalPoints > 0" class="point-badge">
            <span class="point-label">航迹点</span>
            <span class="point-val font-mono">{{ currentPointIndex + 1 }} / {{ totalPoints }}</span>
          </div>
        </div>

        <!-- 中间：当前本地时间（UTC+8 北京时间） -->
        <div class="header-center">
          <div class="time-display-group">
            <svg class="clock-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <polyline points="12 6 12 12 16 14"></polyline>
            </svg>
            <span class="current-time-text font-mono">{{ currentTimeString || '--:--:--' }}</span>
            <span class="timezone-tag">UTC+8</span>
          </div>
        </div>

        <!-- 右侧：已播时长 / 总时长与循环切换 -->
        <div class="header-right">
          <div class="duration-display font-mono">
            <span class="elapsed-time">{{ elapsedTimeFormatted }}</span>
            <span class="duration-divider">/</span>
            <span class="total-time">{{ totalDurationFormatted }}</span>
          </div>
          <button 
            class="loop-toggle-btn" 
            :class="{ 'active': isLooping }"
            :title="isLooping ? '循环播放已开启' : '单次播放'"
            @click="toggleLoop"
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="17 1 21 5 17 9"></polyline>
              <path d="M3 11V9a4 4 0 0 1 4-4h14"></path>
              <polyline points="7 23 3 19 7 15"></polyline>
              <path d="M21 13v2a4 4 0 0 1-4 4H3"></path>
            </svg>
            <span>{{ isLooping ? '循环' : '单次' }}</span>
          </button>
        </div>
      </div>

      <!-- 中部：高交互性发光时间轴进度条 -->
      <div 
        class="timeline-scrubber-area"
        ref="scrubberTrackRef"
        @mousedown="onTrackMouseDown"
        @mousemove="onTrackMouseMove"
        @mouseleave="onTrackMouseLeave"
      >
        <!-- 起始时间提示 -->
        <div class="time-bound-label font-mono left-bound" :title="'起点: ' + startTimeString">
          {{ formatShortTime(startTimeString) }}
        </div>

        <!-- 轨道容器 -->
        <div class="scrubber-rail">
          <!-- 悬停指示线与悬浮气泡 -->
          <div 
            v-if="isHovering && hoverTimeString"
            class="hover-indicator"
            :style="{ left: hoverPercent + '%' }"
          >
            <div class="hover-tooltip">
              <span class="hover-time font-mono">{{ hoverTimeString }}</span>
              <span class="hover-pct font-mono">{{ hoverPercent.toFixed(1) }}%</span>
            </div>
          </div>

          <!-- 已播放进度条 -->
          <div class="scrubber-progress-fill" :style="{ width: progressPercent + '%' }">
            <div class="progress-glow"></div>
          </div>

          <!-- 拖拽游标 -->
          <div 
            class="scrubber-thumb" 
            :style="{ left: progressPercent + '%' }"
            :class="{ 'is-dragging': isDragging }"
          >
            <div class="thumb-inner"></div>
            <div class="thumb-halo"></div>
          </div>
        </div>

        <!-- 结束时间提示 -->
        <div class="time-bound-label font-mono right-bound" :title="'终点: ' + endTimeString">
          {{ formatShortTime(endTimeString) }}
        </div>
      </div>

      <!-- 底部控制栏：播放/步进、倍速调节、视角模式与快捷键 -->
      <div class="player-footer">
        
        <!-- 左侧：播放主控制组 -->
        <div class="playback-controls">
          <!-- 步退 10 秒 -->
          <button class="ctrl-btn sub-btn" @click="stepTime(-10)" title="后退 10 秒 (← 键)">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z"></path>
            </svg>
            <span class="step-label">-10s</span>
          </button>

          <!-- 回到起点 -->
          <button class="ctrl-btn icon-only-btn" @click="resetToStart" title="回到起点 (R 键)">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="19 20 9 12 19 4 19 20" fill="currentColor"></polygon>
              <line x1="5" y1="19" x2="5" y2="5"></line>
            </svg>
          </button>

          <!-- 主播放 / 暂停按钮 -->
          <button 
            class="ctrl-btn main-play-btn" 
            :class="{ 'playing': isPlaying }" 
            @click="togglePlay"
            :title="isPlaying ? '暂停 (空格键)' : '播放 (空格键)'"
          >
            <svg v-if="!isPlaying" viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
              <polygon points="5 3 19 12 5 21 5 3"></polygon>
            </svg>
            <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
              <rect x="6" y="4" width="4" height="16" rx="1"></rect>
              <rect x="14" y="4" width="4" height="16" rx="1"></rect>
            </svg>
          </button>

          <!-- 跳至终点 -->
          <button class="ctrl-btn icon-only-btn" @click="jumpToEnd" title="跳至终点">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="5 4 15 12 5 20 5 4" fill="currentColor"></polygon>
              <line x1="19" y1="5" x2="19" y2="19"></line>
            </svg>
          </button>

          <!-- 步进 10 秒 -->
          <button class="ctrl-btn sub-btn" @click="stepTime(10)" title="前进 10 秒 (→ 键)">
            <span class="step-label">+10s</span>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z"></path>
            </svg>
          </button>
        </div>

        <!-- 中间：倍速选择器 -->
        <div class="speed-selector-group">
          <span class="group-label">倍速</span>
          <div class="speed-pill-list">
            <button 
              v-for="rate in speedOptions" 
              :key="rate"
              class="speed-pill-btn font-mono"
              :class="{ 'active': currentSpeed === rate }"
              @click="changeSpeed(rate)"
            >
              {{ rate }}x
            </button>
          </div>
        </div>

        <!-- 右侧：视角切换与快捷栏 -->
        <div class="view-controls-group">
          <span class="group-label">视角</span>
          <div class="view-pill-list">
            <button 
              class="view-pill-btn" 
              :class="{ 'active': activeView === 'side' }"
              @click="triggerView('side')"
              title="侧方观察航线"
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
              </svg>
              <span>侧视</span>
            </button>
            <button 
              class="view-pill-btn" 
              :class="{ 'active': activeView === 'aircraft' }"
              @click="triggerView('aircraft')"
              title="相机锁定并跟随无人机"
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2L2 7l10 5 10-5-10-5z"></path>
                <path d="M2 17l10 5 10-5"></path>
                <path d="M2 12l10 5 10-5"></path>
              </svg>
              <span>跟随</span>
            </button>
            <button 
              class="view-pill-btn" 
              :class="{ 'active': activeView === 'top' }"
              @click="triggerView('top')"
              title="垂直正俯视"
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <circle cx="12" cy="12" r="3"></circle>
              </svg>
              <span>俯视</span>
            </button>
            <button 
              class="view-pill-btn" 
              :class="{ 'active': activeView === 'reset' }"
              @click="triggerView('reset')"
              title="恢复默认航线视角"
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path>
                <path d="M3 3v5h5"></path>
              </svg>
              <span>复位</span>
            </button>
          </div>
        </div>

      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps<{
  viewer: any
  Cesium: any
  startJulianDate?: any
  stopJulianDate?: any
  totalPoints?: number
  currentPointIndex?: number
  defaultSpeed?: number
}>()

const emit = defineEmits<{
  (e: 'changeView', mode: 'side' | 'aircraft' | 'top' | 'reset'): void
  (e: 'timeChange', currentTime: any): void
}>()

// 状态变量
const isPlaying = ref(true)
const isLooping = ref(true)
const currentSpeed = ref(props.defaultSpeed || 10)
const speedOptions = [1, 2, 5, 10, 20, 50]
const activeView = ref<'side' | 'aircraft' | 'top' | 'reset'>('side')

// 进度与时间
const progressPercent = ref(0)
const currentTimeString = ref('')
const startTimeString = ref('')
const endTimeString = ref('')
const elapsedTimeFormatted = ref('00:00')
const totalDurationFormatted = ref('00:00')
const totalDurationSeconds = ref(0)

// 拖拽与悬停交互
const scrubberTrackRef = ref<HTMLElement | null>(null)
const isDragging = ref(false)
const isHovering = ref(false)
const hoverPercent = ref(0)
const hoverTimeString = ref('')

let clockTickRemover: (() => void) | null = null

// 格式化本地时间（北京时间 UTC+8）
const formatToLocalDate = (julianDate: any): Date | null => {
  if (!julianDate || !props.Cesium) return null
  try {
    return props.Cesium.JulianDate.toDate(julianDate)
  } catch (e) {
    return null
  }
}

const formatDateTime = (d: Date | null): string => {
  if (!d || isNaN(d.getTime())) return '--'
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${day} ${hh}:${mm}:${ss}`
}

const formatShortTime = (timeStr: string): string => {
  if (!timeStr || timeStr === '--') return '--:--'
  const parts = timeStr.split(' ')
  return parts[1] ? parts[1].substring(0, 8) : timeStr
}

const formatSeconds = (sec: number): string => {
  if (isNaN(sec) || sec < 0) return '00:00'
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

// 绑定与同步 Cesium Clock
const setupClockSync = () => {
  if (!props.viewer || !props.Cesium) return

  const clock = props.viewer.clock
  if (!clock) return

  // 初始化倍速与状态
  clock.multiplier = currentSpeed.value
  clock.clockRange = isLooping.value ? props.Cesium.ClockRange.LOOP_STOP : props.Cesium.ClockRange.CLAMPED
  isPlaying.value = clock.shouldAnimate

  if (props.startJulianDate && props.stopJulianDate) {
    const totalSec = Math.max(0.1, props.Cesium.JulianDate.secondsDifference(props.stopJulianDate, props.startJulianDate))
    totalDurationSeconds.value = totalSec
    totalDurationFormatted.value = formatSeconds(totalSec)
    
    const startD = formatToLocalDate(props.startJulianDate)
    const stopD = formatToLocalDate(props.stopJulianDate)
    startTimeString.value = formatDateTime(startD)
    endTimeString.value = formatDateTime(stopD)
  }

  // 监听时钟跳动 (Tick)
  if (clockTickRemover) {
    clockTickRemover()
  }

  clockTickRemover = clock.onTick.addEventListener((c: any) => {
    if (!props.startJulianDate || !props.stopJulianDate || isDragging.value) return

    const cur = c.currentTime
    const totalSec = totalDurationSeconds.value || 1
    const diffSec = Math.max(0, props.Cesium.JulianDate.secondsDifference(cur, props.startJulianDate))
    
    // 计算百分比
    const pct = Math.min(100, Math.max(0, (diffSec / totalSec) * 100))
    progressPercent.value = pct
    elapsedTimeFormatted.value = formatSeconds(diffSec)

    // 更新时间文本（本地时间）
    const curDate = formatToLocalDate(cur)
    if (curDate) {
      currentTimeString.value = formatDateTime(curDate)
    }

    isPlaying.value = c.shouldAnimate
  })
}

// 控制动作
const togglePlay = () => {
  if (!props.viewer) return
  props.viewer.clock.shouldAnimate = !props.viewer.clock.shouldAnimate
  isPlaying.value = props.viewer.clock.shouldAnimate
}

const toggleLoop = () => {
  if (!props.viewer || !props.Cesium) return
  isLooping.value = !isLooping.value
  props.viewer.clock.clockRange = isLooping.value 
    ? props.Cesium.ClockRange.LOOP_STOP 
    : props.Cesium.ClockRange.CLAMPED
}

const changeSpeed = (speed: number) => {
  currentSpeed.value = speed
  if (props.viewer) {
    props.viewer.clock.multiplier = speed
  }
}

const resetToStart = () => {
  if (!props.viewer || !props.startJulianDate) return
  props.viewer.clock.currentTime = props.startJulianDate.clone()
}

const jumpToEnd = () => {
  if (!props.viewer || !props.stopJulianDate) return
  props.viewer.clock.currentTime = props.stopJulianDate.clone()
}

const stepTime = (seconds: number) => {
  if (!props.viewer || !props.Cesium) return
  const clock = props.viewer.clock
  const targetTime = props.Cesium.JulianDate.addSeconds(clock.currentTime, seconds, new props.Cesium.JulianDate())
  
  if (props.startJulianDate && props.Cesium.JulianDate.lessThan(targetTime, props.startJulianDate)) {
    clock.currentTime = props.startJulianDate.clone()
  } else if (props.stopJulianDate && props.Cesium.JulianDate.greaterThan(targetTime, props.stopJulianDate)) {
    clock.currentTime = props.stopJulianDate.clone()
  } else {
    clock.currentTime = targetTime
  }
}

const seekToPercent = (pct: number) => {
  if (!props.viewer || !props.Cesium || !props.startJulianDate || !props.stopJulianDate) return
  const totalSec = totalDurationSeconds.value
  const targetSec = totalSec * (pct / 100)
  const targetTime = props.Cesium.JulianDate.addSeconds(
    props.startJulianDate,
    targetSec,
    new props.Cesium.JulianDate()
  )
  props.viewer.clock.currentTime = targetTime
  progressPercent.value = pct
  elapsedTimeFormatted.value = formatSeconds(targetSec)
  currentTimeString.value = formatDateTime(formatToLocalDate(targetTime))
}

const triggerView = (view: 'side' | 'aircraft' | 'top' | 'reset') => {
  activeView.value = view
  emit('changeView', view)
}

// 进度条交互事件
const getTrackPercentFromEvent = (e: MouseEvent): number => {
  if (!scrubberTrackRef.value) return 0
  const rect = scrubberTrackRef.value.getBoundingClientRect()
  // 轨道两端有 60px 的时间标签内边距
  const railLeft = rect.left + 65
  const railWidth = Math.max(1, rect.width - 130)
  const offsetX = e.clientX - railLeft
  const rawPct = (offsetX / railWidth) * 100
  return Math.min(100, Math.max(0, rawPct))
}

const onTrackMouseDown = (e: MouseEvent) => {
  isDragging.value = true
  const pct = getTrackPercentFromEvent(e)
  seekToPercent(pct)

  const onMouseMove = (moveEvent: MouseEvent) => {
    if (!isDragging.value) return
    const movePct = getTrackPercentFromEvent(moveEvent)
    seekToPercent(movePct)
  }

  const onMouseUp = () => {
    isDragging.value = false
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  }

  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

const onTrackMouseMove = (e: MouseEvent) => {
  if (!props.startJulianDate || !props.Cesium) return
  isHovering.value = true
  const pct = getTrackPercentFromEvent(e)
  hoverPercent.value = pct
  
  const targetSec = totalDurationSeconds.value * (pct / 100)
  const targetTime = props.Cesium.JulianDate.addSeconds(
    props.startJulianDate,
    targetSec,
    new props.Cesium.JulianDate()
  )
  const hoverDate = formatToLocalDate(targetTime)
  hoverTimeString.value = formatDateTime(hoverDate)
}

const onTrackMouseLeave = () => {
  isHovering.value = false
}

// 快捷键支持
const handleKeyDown = (e: KeyboardEvent) => {
  // 如果焦点在 input/textarea 则不捕获
  if (['INPUT', 'TEXTAREA'].includes((e.target as HTMLElement)?.tagName)) return

  if (e.code === 'Space') {
    e.preventDefault()
    togglePlay()
  } else if (e.code === 'ArrowLeft') {
    e.preventDefault()
    stepTime(-5)
  } else if (e.code === 'ArrowRight') {
    e.preventDefault()
    stepTime(5)
  } else if (e.code === 'KeyR') {
    e.preventDefault()
    resetToStart()
  }
}

watch(() => [props.viewer, props.startJulianDate, props.stopJulianDate], () => {
  setupClockSync()
}, { immediate: true })

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  if (clockTickRemover) {
    clockTickRemover()
  }
  window.removeEventListener('keydown', handleKeyDown)
})
</script>

<style scoped lang="scss">
.cesium-custom-player-wrapper {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  width: clamp(680px, 72vw, 1080px);
  z-index: 1000;
  pointer-events: auto;
  user-select: none;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

.player-container {
  background: rgba(11, 15, 25, 0.88);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 16px;
  padding: 14px 20px 12px 20px;
  box-shadow: 
    0 20px 50px rgba(0, 0, 0, 0.6),
    0 0 0 1px rgba(255, 255, 255, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.15);
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    border-color: rgba(0, 212, 255, 0.3);
    box-shadow: 
      0 24px 60px rgba(0, 0, 0, 0.7),
      0 0 20px rgba(0, 212, 255, 0.15),
      inset 0 1px 0 rgba(255, 255, 255, 0.2);
  }
}

/* 顶部栏 */
.player-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 2px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);

  .status-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #faad14;
    box-shadow: 0 0 8px #faad14;
    transition: all 0.3s ease;
  }

  &.is-playing {
    color: #ffffff;
    border-color: rgba(0, 238, 139, 0.3);
    background: rgba(0, 238, 139, 0.08);

    .status-dot {
      background: #00ee8b;
      box-shadow: 0 0 10px #00ee8b;
      animation: pulse-dot 1.8s infinite;
    }
  }
}

@keyframes pulse-dot {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.4;
    transform: scale(1.3);
  }
}

.point-badge {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  padding: 3px 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);

  .point-val {
    color: #00d4ff;
    font-weight: 600;
  }
}

.header-center {
  display: flex;
  align-items: center;
}

.time-display-group {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 14px;
  background: rgba(0, 0, 0, 0.35);
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.08);

  .clock-icon {
    color: #00d4ff;
  }

  .current-time-text {
    font-size: 15px;
    font-weight: 600;
    color: #ffffff;
    letter-spacing: 0.5px;
    text-shadow: 0 0 10px rgba(0, 212, 255, 0.4);
  }

  .timezone-tag {
    font-size: 11px;
    color: #00d4ff;
    background: rgba(0, 212, 255, 0.15);
    padding: 1px 6px;
    border-radius: 4px;
    font-weight: 500;
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.duration-display {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);

  .elapsed-time {
    color: #ffffff;
    font-weight: 600;
  }
  .duration-divider {
    margin: 0 4px;
    color: rgba(255, 255, 255, 0.3);
  }
  .total-time {
    color: rgba(255, 255, 255, 0.5);
  }
}

.loop-toggle-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.12);
    color: #ffffff;
  }

  &.active {
    background: rgba(0, 212, 255, 0.15);
    border-color: rgba(0, 212, 255, 0.4);
    color: #00d4ff;
  }
}

/* 时间轴进度条区域 */
.timeline-scrubber-area {
  position: relative;
  display: flex;
  align-items: center;
  padding: 10px 0;
  cursor: pointer;
}

.time-bound-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
  width: 60px;
  flex-shrink: 0;

  &.left-bound {
    text-align: left;
  }
  &.right-bound {
    text-align: right;
  }
}

.scrubber-rail {
  position: relative;
  flex: 1;
  height: 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  margin: 0 8px;
  transition: height 0.2s ease;

  .timeline-scrubber-area:hover & {
    height: 8px;
  }
}

.scrubber-progress-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, #00d4ff, #00ee8b);
  border-radius: 3px;
  pointer-events: none;

  .progress-glow {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    width: 20px;
    box-shadow: 0 0 12px rgba(0, 238, 139, 0.8);
  }
}

.scrubber-thumb {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 16px;
  height: 16px;
  border-radius: 50%;
  pointer-events: none;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s ease;

  .thumb-inner {
    width: 12px;
    height: 12px;
    background: #ffffff;
    border-radius: 50%;
    box-shadow: 0 0 8px rgba(0, 238, 139, 0.9), 0 2px 4px rgba(0, 0, 0, 0.4);
    border: 2px solid #00ee8b;
    z-index: 2;
  }

  .thumb-halo {
    position: absolute;
    width: 26px;
    height: 26px;
    border-radius: 50%;
    background: rgba(0, 238, 139, 0.25);
    transform: scale(0);
    transition: transform 0.2s ease;
  }

  .timeline-scrubber-area:hover &,
  &.is-dragging {
    transform: translate(-50%, -50%) scale(1.15);

    .thumb-halo {
      transform: scale(1);
    }
  }
}

.hover-indicator {
  position: absolute;
  top: -8px;
  bottom: -8px;
  width: 2px;
  background: rgba(255, 255, 255, 0.6);
  pointer-events: none;
  transform: translateX(-50%);

  .hover-tooltip {
    position: absolute;
    bottom: 24px;
    left: 50%;
    transform: translateX(-50%);
    background: rgba(15, 23, 42, 0.95);
    border: 1px solid rgba(0, 212, 255, 0.4);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.6);
    border-radius: 8px;
    padding: 6px 10px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    white-space: nowrap;
    z-index: 10;

    .hover-time {
      font-size: 12px;
      color: #ffffff;
      font-weight: 500;
    }
    .hover-pct {
      font-size: 10px;
      color: #00d4ff;
    }

    &::after {
      content: '';
      position: absolute;
      bottom: -4px;
      left: 50%;
      transform: translateX(-50%) rotate(45deg);
      width: 8px;
      height: 8px;
      background: rgba(15, 23, 42, 0.95);
      border-right: 1px solid rgba(0, 212, 255, 0.4);
      border-bottom: 1px solid rgba(0, 212, 255, 0.4);
    }
  }
}

/* 底部控制器栏 */
.player-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 4px;
}

.playback-controls {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ctrl-btn {
  border: none;
  outline: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #ffffff;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    background: rgba(255, 255, 255, 0.16);
    border-color: rgba(255, 255, 255, 0.25);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(1px) scale(0.96);
  }

  &.icon-only-btn {
    width: 34px;
    height: 34px;
  }

  &.sub-btn {
    height: 34px;
    padding: 0 10px;
    gap: 4px;

    .step-label {
      font-size: 11px;
      font-weight: 500;
    }
  }

  &.main-play-btn {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    background: linear-gradient(135deg, #00d4ff 0%, #0088ff 100%);
    border: none;
    color: #ffffff;
    box-shadow: 0 4px 16px rgba(0, 140, 255, 0.4);

    &:hover {
      background: linear-gradient(135deg, #26dcff 0%, #1a94ff 100%);
      box-shadow: 0 6px 20px rgba(0, 140, 255, 0.6);
      transform: translateY(-2px);
    }

    &.playing {
      background: linear-gradient(135deg, #00ee8b 0%, #00b368 100%);
      box-shadow: 0 4px 16px rgba(0, 238, 139, 0.4);

      &:hover {
        background: linear-gradient(135deg, #26f29d 0%, #00c774 100%);
        box-shadow: 0 6px 20px rgba(0, 238, 139, 0.6);
      }
    }
  }
}

.speed-selector-group,
.view-controls-group {
  display: flex;
  align-items: center;
  gap: 8px;

  .group-label {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.5);
    font-weight: 500;
  }
}

.speed-pill-list,
.view-pill-list {
  display: flex;
  align-items: center;
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 3px;
  gap: 2px;
}

.speed-pill-btn,
.view-pill-btn {
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.65);
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.2s ease;

  &:hover {
    color: #ffffff;
    background: rgba(255, 255, 255, 0.08);
  }

  &.active {
    color: #00d4ff;
    font-weight: 600;
    background: rgba(0, 212, 255, 0.16);
    border: 1px solid rgba(0, 212, 255, 0.3);
    box-shadow: 0 0 10px rgba(0, 212, 255, 0.2);
  }
}

.font-mono {
  font-family: 'JetBrains Mono', 'Fira Code', 'Roboto Mono', Consolas, Monaco, monospace;
}
</style>
