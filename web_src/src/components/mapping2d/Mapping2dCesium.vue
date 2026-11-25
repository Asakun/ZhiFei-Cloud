<template>
  <div class="mapping2d-container select-none overflow-hidden" :class="{ 'is-preview': previewMode }">
    <!-- 顶部状态与工具栏 (非预览模式下展示完整导航栏) -->
    <header class="top-nav-bar" v-if="!previewMode">
      <div class="nav-left">
        <button class="action-icon-btn back-btn" @click="handleBack" title="返回上一级">
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          <span>返回</span>
        </button>
        <div class="divider-v"></div>
        <div class="wayline-info-badge">
          <span class="type-tag">面状航线</span>
          <span class="wayline-title" :title="waylineName">{{ waylineName || '未命名面状航线' }}</span>
        </div>
      </div>

      <!-- 步骤指引条 -->
      <div class="nav-center-guide">
        <div class="step-guide-pill" :class="{ active: drawMode === 'drawing' || polygonCoordinates.length < 3 }">
          <span class="step-num">1</span>
          <span>框选测区</span>
          <span v-if="polygonCoordinates.length > 0" class="step-badge">{{ polygonCoordinates.length }} 点</span>
        </div>
        <div class="guide-arrow">➜</div>
        <div class="step-guide-pill" :class="{ active: polygonCoordinates.length >= 3 && !hasPreview }">
          <span class="step-num">2</span>
          <span>参数配置 & 预览</span>
        </div>
        <div class="guide-arrow">➜</div>
        <div class="step-guide-pill" :class="{ active: hasPreview }">
          <span class="step-num">3</span>
          <span>确认保存</span>
        </div>
      </div>

      <div class="nav-right">
        <!-- 视角重置与俯视切换 -->
        <button class="action-icon-btn tool-btn" @click="switchToTopDownView" title="正俯视视角 (垂直向下)">
          <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 3v18M3 12h18" />
          </svg>
          <span>俯视</span>
        </button>
        <button class="action-icon-btn tool-btn" @click="flyToAreaOrDock" title="聚焦到测区或起飞点">
          <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 12l18-9-9 18-2-7-7-2z" />
          </svg>
          <span>聚焦</span>
        </button>
      </div>
    </header>

    <!-- 工作区主体：左侧参数面板 + 右侧地图区 (左右结构，避免图例遮挡面板) -->
    <div class="main-layout-body">
      <!-- 左侧参数与操作面板 (独立侧边栏结构，非浮动) -->
      <aside class="left-control-panel" v-if="!previewMode" :class="{ collapsed: isPanelCollapsed }">
      <div class="panel-header">
        <div class="panel-title-wrap">
          <div class="icon-radar">
            <span class="radar-sweep"></span>
          </div>
          <span class="panel-title">测绘参数配置</span>
        </div>
        <button class="collapse-toggle-btn" @click="isPanelCollapsed = !isPanelCollapsed" :title="isPanelCollapsed ? '展开面板' : '收起面板'">
          <svg class="w-4 h-4 transition-transform duration-200" :class="{ 'rotate-180': isPanelCollapsed }" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
      </div>

      <div class="panel-body" v-show="!isPanelCollapsed">
        <!-- 1. 测区多边形状态 -->
        <div class="config-section">
          <div class="section-title">
            <span>测区范围绘制</span>
            <span class="status-indicator" :class="polygonCoordinates.length >= 3 ? 'valid' : 'invalid'">
              {{ polygonCoordinates.length >= 3 ? '已闭合' : (drawMode === 'drawing' ? '绘制中' : '未绘制') }}
            </span>
          </div>

          <div class="polygon-stats-grid">
            <div class="stat-card">
              <span class="stat-lbl">顶点数</span>
              <span class="stat-val highlight">{{ polygonCoordinates.length }}</span>
            </div>
            <div class="stat-card">
              <span class="stat-lbl">测区估算面积</span>
              <span class="stat-val" :title="areaFormatted.detail">{{ areaFormatted.text }}</span>
            </div>
          </div>

          <!-- 测区操作按钮组 -->
          <div class="polygon-action-row">
            <button v-if="drawMode !== 'drawing'" class="btn-primary-ghost" @click="startDrawPolygon">
              <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 20h9M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>
              </svg>
              <span>{{ polygonCoordinates.length > 0 ? '重新框选' : '开始框选' }}</span>
            </button>
            <button v-else class="btn-success" @click="finishDrawPolygon" :disabled="polygonCoordinates.length < 3">
              <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              <span>完成闭合 ({{ polygonCoordinates.length }}/3)</span>
            </button>
            <button class="btn-danger-ghost" @click="clearPolygon" :disabled="polygonCoordinates.length === 0">
              <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
              <span>清除</span>
            </button>
          </div>
        </div>

        <!-- 2. 航摄重叠率与模式 -->
        <div class="config-section">
          <div class="section-title">
            <span>重叠率与拍照</span>
          </div>

          <!-- 采集方式（正射固定） -->
          <div class="form-item">
            <div class="form-label-row">
              <span class="item-label">采集方式</span>
              <span class="item-extra-tag">正射 (ortho)</span>
            </div>
            <div class="fixed-mode-bar">
              <span class="dot-live"></span>
              <span>正射摄影 (单相机下视垂直正射)</span>
            </div>
          </div>

          <!-- 航向重叠率 (overlap_h) -->
          <div class="form-item">
            <div class="form-label-row">
              <span class="item-label">航向重叠率 (H)</span>
              <span class="item-val-display">{{ formParams.overlap_h }}%</span>
            </div>
            <div class="slider-row">
              <a-slider
                v-model:value="formParams.overlap_h"
                :min="50"
                :max="90"
                :step="1"
                class="custom-slider flex-1"
              />
              <a-input-number
                v-model:value="formParams.overlap_h"
                :min="50"
                :max="90"
                size="small"
                class="num-input"
              />
            </div>
            <div class="param-hint">沿航线飞行方向的相邻照片重叠率 (建议 70%~85%)</div>
          </div>

          <!-- 旁向重叠率 (overlap_w) -->
          <div class="form-item">
            <div class="form-label-row">
              <span class="item-label">旁向重叠率 (W)</span>
              <span class="item-val-display">{{ formParams.overlap_w }}%</span>
            </div>
            <div class="slider-row">
              <a-slider
                v-model:value="formParams.overlap_w"
                :min="50"
                :max="85"
                :step="1"
                class="custom-slider flex-1"
              />
              <a-input-number
                v-model:value="formParams.overlap_w"
                :min="50"
                :max="85"
                size="small"
                class="num-input"
              />
            </div>
            <div class="param-hint">相邻飞行航带之间的横向照片重叠率 (建议 60%~80%)</div>
          </div>

          <!-- 拍照模式 (shoot_type) -->
          <div class="form-item">
            <div class="form-label-row">
              <span class="item-label">拍照模式</span>
            </div>
            <div class="seg-btn-group">
              <button
                class="seg-btn"
                :class="{ active: formParams.shoot_type === 'time' }"
                @click="formParams.shoot_type = 'time'"
              >
                <span>⏱️ 定时等时拍照</span>
              </button>
              <button
                class="seg-btn"
                :class="{ active: formParams.shoot_type === 'distance' }"
                @click="formParams.shoot_type = 'distance'"
              >
                <span>📏 等距等程拍照</span>
              </button>
            </div>
          </div>

          <!-- 高程优化开关 (is_elevation_optimize) -->
          <div class="form-item switch-item-row">
            <div class="switch-meta">
              <span class="item-label text-slate-100 font-medium">高程优化</span>
              <span class="switch-sub text-slate-300">飞行器在测区中央采高程校准照片</span>
            </div>
            <a-switch
              v-model:checked="formParams.is_elevation_optimize"
              checked-children="开"
              un-checked-children="关"
              class="custom-switch"
            />
          </div>
        </div>

        <!-- 3. 飞行基础参数 -->
        <div class="config-section">
          <div class="section-title">
            <span>飞行高度与速度</span>
          </div>

          <!-- 飞行相对高度 -->
          <div class="form-item">
            <div class="form-label-row">
              <span class="item-label">相对起飞点高度</span>
              <span class="item-val-display">{{ formParams.flightHeight }} m</span>
            </div>
            <div class="slider-row">
              <a-slider
                v-model:value="formParams.flightHeight"
                :min="30"
                :max="500"
                :step="5"
                class="custom-slider flex-1"
              />
              <a-input-number
                v-model:value="formParams.flightHeight"
                :min="30"
                :max="500"
                size="small"
                class="num-input"
              />
            </div>
          </div>

          <!-- 飞行速度 -->
          <div class="form-item">
            <div class="form-label-row">
              <span class="item-label">巡航飞行速度</span>
              <span class="item-val-display">{{ formParams.flightSpeed }} m/s</span>
            </div>
            <div class="slider-row">
              <a-slider
                v-model:value="formParams.flightSpeed"
                :min="1"
                :max="15"
                :step="0.5"
                class="custom-slider flex-1"
              />
              <a-input-number
                v-model:value="formParams.flightSpeed"
                :min="1"
                :max="15"
                size="small"
                class="num-input"
              />
            </div>
          </div>

          <!-- 航线主方向角 -->
          <div class="form-item">
            <div class="form-label-row">
              <span class="item-label">航带方向角度</span>
              <span class="item-val-display">{{ formParams.isAutoDirection ? '自动最优' : `${formParams.direction}°` }}</span>
            </div>
            <div class="direction-control-row">
              <button
                class="auto-btn"
                :class="{ active: formParams.isAutoDirection }"
                @click="formParams.isAutoDirection = !formParams.isAutoDirection"
              >
                {{ formParams.isAutoDirection ? '✓ 自动最优角' : '手动指定' }}
              </button>
              <div v-if="!formParams.isAutoDirection" class="slider-row flex-1">
                <a-slider
                  v-model:value="formParams.direction"
                  :min="0"
                  :max="360"
                  :step="1"
                  class="custom-slider flex-1"
                />
                <a-input-number
                  v-model:value="formParams.direction"
                  :min="0"
                  :max="360"
                  size="small"
                  class="num-input"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- 4. 预览与计算操作 -->
        <div class="panel-action-section">
          <button
            class="btn-generate-preview"
            :disabled="polygonCoordinates.length < 3 || isPreparing"
            @click="handlePrepareRoute"
          >
            <span v-if="isPreparing" class="spinner-inline"></span>
            <svg v-else class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="5 3 19 12 5 21 5 3"/>
            </svg>
            <span>{{ isPreparing ? '正在计算航线路径...' : (hasPreview ? '重新计算预览' : '生成航线预览') }}</span>
          </button>

          <button
            class="btn-save-wayline"
            :disabled="!hasPreview || isSaving"
            @click="handleSaveWayline"
          >
            <span v-if="isSaving" class="spinner-inline"></span>
            <svg v-else class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
              <polyline points="17 21 17 13 7 13 7 21"/>
              <polyline points="7 3 7 8 15 8"/>
            </svg>
            <span>{{ isSaving ? '保存中...' : (isEditMode ? '保存修改航线' : '保存此航线') }}</span>
          </button>
        </div>
      </div>
    </aside>

    <!-- 右侧地图区域 (三维视口与内部悬浮工具、图例) -->
    <main class="map-view-wrapper">
        <!-- Cesium 地图容器 -->
        <div ref="cesiumContainerRef" class="cesium-map-canvas"></div>

        <!-- 首页纯预览模式下的浮动快捷工具条 (右上角) -->
        <div class="preview-map-tools" v-if="previewMode">
          <button class="action-icon-btn tool-btn" @click="switchToTopDownView" title="正俯视视角 (垂直向下)">
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 3v18M3 12h18" />
            </svg>
            <span>俯视</span>
          </button>

          <button class="action-icon-btn tool-btn" @click="flyToAreaOrDock" title="聚焦到测区或起飞点">
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 12l18-9-9 18-2-7-7-2z" />
            </svg>
            <span>聚焦</span>
          </button>
        </div>

        <!-- 顶部状态提示横幅 -->
        <div class="top-tips-banner" v-if="!previewMode && tipsMessage">
          <div class="tips-pill">
            <span class="tips-icon">💡</span>
            <span>{{ tipsMessage }}</span>
          </div>
        </div>

        <!-- 右下角预览统计数据面板 -->
        <div class="route-stats-card" v-if="hasPreview">
          <div class="stats-header">
            <span class="stats-title">✈️ 航线计算结果</span>
            <span class="stats-status-tag">计算完成</span>
          </div>
          <div class="stats-grid">
            <div class="stat-box">
              <span class="k">预估总航程</span>
              <span class="v highlight">{{ statsInfo.totalDistance }}</span>
            </div>
            <div class="stat-box">
              <span class="k">预估飞行时间</span>
              <span class="v">{{ statsInfo.estimatedTime }}</span>
            </div>
            <div class="stat-box">
              <span class="k">计算航点数</span>
              <span class="v">{{ statsInfo.pointCount }} 个</span>
            </div>
            <div class="stat-box">
              <span class="k">测区规划面积</span>
              <span class="v">{{ areaFormatted.text }}</span>
            </div>
            <div class="stat-box">
              <span class="k">航带角度</span>
              <span class="v highlight">{{ statsInfo.directionText }}</span>
            </div>
            <div class="stat-box">
              <span class="k">高程优化采集</span>
              <span class="v" :class="{ 'text-amber': statsInfo.hasElevationOptimize }">
                {{ statsInfo.hasElevationOptimize ? '✓ 已包含' : '未开启' }}
              </span>
            </div>
          </div>
        </div>

        <!-- 航点详情弹窗卡片 (点击地图航点时展示) -->
        <div class="waypoint-detail-card" v-if="selectedWaypoint">
          <div class="wp-card-header">
            <div class="wp-badge-wrap">
              <span class="wp-badge" :class="selectedWaypoint.typeClass">#{{ selectedWaypoint.index }}</span>
              <span class="wp-title">{{ selectedWaypoint.typeTitle }}</span>
            </div>
            <button class="close-wp-btn" @click="selectedWaypoint = null" title="关闭详情">✕</button>
          </div>
          <div class="wp-card-body">
            <div class="wp-card-row">
              <span class="k">航点性质</span>
              <span class="v">{{ selectedWaypoint.typeDesc }}</span>
            </div>
            <div class="wp-card-row">
              <span class="k">经度 (Lon)</span>
              <span class="v font-mono">{{ Number(selectedWaypoint.longitude).toFixed(7) }}°</span>
            </div>
            <div class="wp-card-row">
              <span class="k">纬度 (Lat)</span>
              <span class="v font-mono">{{ Number(selectedWaypoint.latitude).toFixed(7) }}°</span>
            </div>
            <div class="wp-card-row">
              <span class="k">相对起飞点高度</span>
              <span class="v font-mono highlight">{{ selectedWaypoint.height }} m</span>
            </div>
            <div class="wp-card-row" v-if="getTakeoffCoord()?.height">
              <span class="k">绝对海拔高度 (AMSL)</span>
              <span class="v font-mono text-emerald-400 font-semibold">{{ ((Number(selectedWaypoint.height) || 0) + Number(getTakeoffCoord()?.height || 0)).toFixed(1) }} m</span>
            </div>
          </div>
          <div class="wp-card-footer">
            <button class="btn-focus-wp" @click="focusWaypoint(selectedWaypoint)">
              <span>🎯 视角聚焦到该点</span>
            </button>
          </div>
        </div>

        <!-- 绘制多边形时的悬浮快捷操作气泡 (优先级高，放在上方) -->
        <div 
          class="draw-floating-toolbar" 
          :class="{ 'with-takeoff-info': takeoffPointInfo }"
          v-if="!previewMode && (drawMode === 'drawing' || drawMode === 'done') && polygonCoordinates.length > 0"
        >
          <div class="toolbar-content">
            <span class="draw-status-text" v-if="drawMode === 'drawing'">
              已落顶点: <strong>{{ polygonCoordinates.length }}</strong> (双击闭合，支持拖拽顶点)
            </span>
            <span class="draw-status-text" v-else>
              测区已闭合。拖拽<span style="color:#ffd000">黄点</span>微调，拖拽<span style="color:#fff">白点</span>新增，右击黄点删除
            </span>
            <div class="toolbar-btns">
              <button class="t-btn complete" v-if="drawMode === 'drawing'" :disabled="polygonCoordinates.length < 3" @click="finishDrawPolygon">
                ✓ 闭合测区
              </button>
              <button class="t-btn cancel" @click="clearPolygon">
                ✕ 清空测区
              </button>
            </div>
          </div>
        </div>

        <!-- 底部起飞参考点信息浮条 -->
        <div class="bottom-ref-bar" v-if="takeoffPointInfo">
          <div class="ref-pill">
            <span class="ref-icon">📍</span>
            <span class="ref-title">参考起飞点:</span>
            <span class="ref-coords">{{ takeoffPointInfo }}</span>
          </div>
        </div>

        <!-- 地图图例组件 (置于地图内部左下角，完全脱离左侧面板，避免遮挡) -->
        <MapLegend ref="mapLegendRef" @change="handleLegendChange" @change-tileset="handleTilesetChange" />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, shallowRef, markRaw, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import * as Cesium from 'cesium'
import { CURRENT_CONFIG } from '@/api/http/config'
import { ELocalStorageKey } from '@/types'
import { prepareWaylineEdit, saveWaylineEdit, modifyWaylineEdit, type Mapping2dCoordinate } from '@/api/wayline'
import MapLegend from '@/components/MapLegend.vue'
import { getFlightAreaList } from '@/api/flight-area'
import { EFlightAreaType, EGeometryType } from '@/types/flight-area'
import mapBjIcon from '@/assets/airControl/map/map-bj-icon.png'

const router = useRouter()

const props = defineProps<{
  config?: any
  takeoffPoint?: { longitude: number; latitude: number; height?: number } | null
  waylineName?: string
  isEditMode?: boolean
  editWaylineId?: string
  editWaylineData?: any
  dock_sn?: string
  previewMode?: boolean
  readOnly?: boolean
}>()

const isPreviewOrReadOnly = computed(() => Boolean(props.previewMode || props.readOnly))

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'update-takeoff-point', point: { longitude: number; latitude: number; height: number }): void
  (e: 'update-params', params: any): void
}>()

// --- 容器与 Cesium 状态 ---
const cesiumContainerRef = ref<HTMLDivElement | null>(null)
const viewerRef = shallowRef<Cesium.Viewer | null>(null)
let handler: Cesium.ScreenSpaceEventHandler | null = null

const isPanelCollapsed = ref(false)
const isPreparing = ref(false)
const isSaving = ref(false)
const hasPreview = ref(false)

// 绘制状态: idle 空闲, drawing 绘制中, done 完成
const drawMode = ref<'idle' | 'drawing' | 'done'>('idle')

// 用户框选的多边形顶点列表 (经纬度)
const polygonCoordinates = ref<Mapping2dCoordinate[]>([])

// 后端 prepare 返回的计算航线点位列表
const previewRoutePoints = ref<any[]>([])

// 参数配置表单
const formParams = reactive({
  overlap_h: 80,
  overlap_w: 70,
  shoot_type: 'time', // 'time' | 'distance'
  is_elevation_optimize: true,
  direction: 0,
  isAutoDirection: true,
  flightHeight: 100,
  flightSpeed: 15,
  collection_method: 'ortho'
})

// 同步全局设置 (props.config) 到当前面板参数表单
const syncFromConfig = (config: any) => {
  if (!config) return

  // 1. 飞行相对高度 (相对起飞点)
  const h = config.global_height ?? config.flightHeight
  if (h !== undefined && h !== null && !isNaN(Number(h))) {
    if (formParams.flightHeight !== Number(h)) {
      formParams.flightHeight = Number(h)
    }
  }

  // 2. 飞行巡航速度
  const s = config.auto_flight_speed ?? config.flightSpeed ?? config.globalSpeed
  if (s !== undefined && s !== null && !isNaN(Number(s))) {
    if (formParams.flightSpeed !== Number(s)) {
      formParams.flightSpeed = Number(s)
    }
  }

  // 3. 测绘特定参数
  const req = config.mapping_type_req
  if (req) {
    if (req.overlap_h !== undefined && !isNaN(Number(req.overlap_h))) {
      if (formParams.overlap_h !== Number(req.overlap_h)) {
        formParams.overlap_h = Number(req.overlap_h)
      }
    }
    if (req.overlap_w !== undefined && !isNaN(Number(req.overlap_w))) {
      if (formParams.overlap_w !== Number(req.overlap_w)) {
        formParams.overlap_w = Number(req.overlap_w)
      }
    }
    if (req.shoot_type && formParams.shoot_type !== req.shoot_type) {
      formParams.shoot_type = req.shoot_type
    }
    if (req.is_elevation_optimize !== undefined || req.elevation_optimize_enable !== undefined) {
      const isElev = Boolean(req.is_elevation_optimize ?? req.elevation_optimize_enable)
      if (formParams.is_elevation_optimize !== isElev) {
        formParams.is_elevation_optimize = isElev
      }
    }
    if (req.direction !== undefined && req.direction !== '') {
      if (formParams.direction !== Number(req.direction)) {
        formParams.isAutoDirection = false
        formParams.direction = Number(req.direction) || 0
      }
    }
  }
}

// 监听 props.config 深度变化，确保在全局设置中修改高度、速度等立即同步到当前面板
watch(() => props.config, (newConfig) => {
  if (newConfig) {
    syncFromConfig(newConfig)
  }
}, { deep: true, immediate: true })

// 监听本地表单参数变化，及时向外同步到父组件全局配置中
watch(
  () => [
    formParams.flightHeight,
    formParams.flightSpeed,
    formParams.overlap_h,
    formParams.overlap_w,
    formParams.shoot_type,
    formParams.is_elevation_optimize,
    formParams.direction,
    formParams.isAutoDirection
  ],
  () => {
    emit('update-params', {
      flightHeight: formParams.flightHeight,
      flightSpeed: formParams.flightSpeed,
      overlap_h: formParams.overlap_h,
      overlap_w: formParams.overlap_w,
      shoot_type: formParams.shoot_type,
      is_elevation_optimize: formParams.is_elevation_optimize,
      direction: formParams.isAutoDirection ? '' : formParams.direction
    })
  }
)

// 鼠标悬停动态虚线点
const activeMousePosition = ref<Cesium.Cartesian3 | null>(null)

// 实体集合记录 (用于精确清理)
let polygonEntity: Cesium.Entity | null = null
let outlineEntity: Cesium.Entity | null = null
let vertexEntities: Cesium.Entity[] = []
let mouseRubberLineEntity: Cesium.Entity | null = null
let routePolylineEntity: Cesium.Entity | null = null
let waypointEntities: Cesium.Entity[] = []
let verticalLineEntities: Cesium.Entity[] = []
let takeoffGuideLineEntity: Cesium.Entity | null = null
let returnGuideLineEntity: Cesium.Entity | null = null

// 选中的航点详情 (点击地图航点拾取)
const selectedWaypoint = ref<any | null>(null)

// 图例组件相关状态
const mapLegendRef = ref(null)
const legendShowData = ref([])
const layerEntitiesCache = new Map()

// 提取有效起飞点坐标辅助方法
const getTakeoffCoord = (): { longitude: number; latitude: number; height?: number } | null => {
  if (props.takeoffPoint?.longitude && props.takeoffPoint?.latitude) {
    return props.takeoffPoint
  }
  const rawRef = props.config?.take_off_ref_point || props.editWaylineData?.take_off_ref_point || props.editWaylineData?.data?.take_off_ref_point
  if (rawRef) {
    if (typeof rawRef === 'string') {
      const parts = rawRef.split(',').map(Number)
      if (parts.length >= 2 && !isNaN(parts[0]) && !isNaN(parts[1])) {
        return {
          latitude: parts[0],
          longitude: parts[1],
          height: !isNaN(parts[2]) ? parts[2] : 0
        }
      }
    } else if (typeof rawRef === 'object') {
      const lat = Number(rawRef.latitude ?? rawRef.lat)
      const lon = Number(rawRef.longitude ?? rawRef.lon ?? rawRef.lng)
      const h = Number(rawRef.height ?? rawRef.alt ?? 0)
      if (!isNaN(lat) && !isNaN(lon)) {
        return { latitude: lat, longitude: lon, height: isNaN(h) ? 0 : h }
      }
    }
  }
  return null
}

// 归一化航点数据结构
const normalizePoints = (points: any[]): any[] => {
  return points.map((p, idx) => {
    const lon = Number(p.wg_lon ?? p.longitude ?? p.lon ?? 0)
    const lat = Number(p.wg_lat ?? p.latitude ?? p.lat ?? 0)
    const h = Number(p.height ?? p.relative_height ?? formParams.flightHeight ?? 100)
    const type = p.type || (idx === 0 ? 'START_PHOTO' : (idx === points.length - 1 ? 'END_PHOTO' : 'NORMAL'))
    return {
      ...p,
      index: idx + 1,
      longitude: lon,
      latitude: lat,
      wg_lon: lon,
      wg_lat: lat,
      height: h,
      type
    }
  })
}

// 提取后端不同层次返回的真实规划航点
const extractRoutePoints = (data: any): any[] => {
  if (!data) return []
  // 1. DJI mapping2d 标准返回: data.mapping_type_req.sim_route_point_list
  if (Array.isArray(data.mapping_type_req?.sim_route_point_list) && data.mapping_type_req.sim_route_point_list.length > 0) {
    return data.mapping_type_req.sim_route_point_list
  }
  // 2. 根级 sim_route_point_list
  if (Array.isArray(data.sim_route_point_list) && data.sim_route_point_list.length > 0) {
    return data.sim_route_point_list
  }
  // 3. 根级 route_point_list
  if (Array.isArray(data.route_point_list) && data.route_point_list.length > 0) {
    return data.route_point_list
  }
  // 4. data 本身是数组
  if (Array.isArray(data) && data.length > 0) {
    return data
  }
  return []
}

// 起飞点显示文案
const takeoffPointInfo = computed(() => {
  const coord = getTakeoffCoord()
  if (coord) {
    return `${coord.latitude.toFixed(6)}°, ${coord.longitude.toFixed(6)}° (高 ${Number(coord.height || 0).toFixed(1)}m)`
  }
  return null
})

// 顶部提示信息
const tipsMessage = computed(() => {
  if (drawMode.value === 'drawing') {
    return '请在地图上点击添加测区顶点，至少3个点，完成后可点击【闭合测区】或双击/右键结束'
  }
  if (polygonCoordinates.value.length < 3) {
    return '请点击左侧【开始框选】按钮，在地图上绘制测区多边形'
  }
  if (!hasPreview.value) {
    return '测区已建立，调整参数后点击【生成航线预览】以计算航线轨迹'
  }
  return '航线预览已就绪，确认航带分布与拍照点位后可直接【保存航线】'
})

// --- 几何与面积计算 ---
// 计算球面上多边形面积 (平方米)
const calculatePolygonArea = (coords: Mapping2dCoordinate[]): number => {
  if (coords.length < 3) return 0
  const earthRadius = 6378137 // WGS84 赤道半径 (米)
  let total = 0
  const len = coords.length
  for (let i = 0; i < len; i++) {
    const p1 = coords[i]
    const p2 = coords[(i + 1) % len]
    const radLon1 = Cesium.Math.toRadians(p1.longitude)
    const radLat1 = Cesium.Math.toRadians(p1.latitude)
    const radLon2 = Cesium.Math.toRadians(p2.longitude)
    const radLat2 = Cesium.Math.toRadians(p2.latitude)
    total += (radLon2 - radLon1) * (2 + Math.sin(radLat1) + Math.sin(radLat2))
  }
  total = Math.abs((total * earthRadius * earthRadius) / 2)
  return total
}

const areaFormatted = computed(() => {
  const m2 = calculatePolygonArea(polygonCoordinates.value)
  if (m2 === 0) return { text: '0 ㎡', detail: '0 ㎡' }
  const mu = (m2 * 0.0015).toFixed(2)
  if (m2 < 10000) {
    return {
      text: `${m2.toFixed(1)} ㎡ (${mu} 亩)`,
      detail: `${m2.toFixed(2)} 平方米`
    }
  }
  const km2 = (m2 / 1000000).toFixed(3)
  return {
    text: `${km2} k㎡ (${mu} 亩)`,
    detail: `${km2} 平方公里 / ${mu} 亩`
  }
})

// 计算航线统计数据 (航程、时间、点数、方向角、高程优化)
const statsInfo = computed(() => {
  const points = previewRoutePoints.value
  if (!points || points.length === 0) {
    return {
      totalDistance: '0 m',
      estimatedTime: '0分0秒',
      pointCount: 0,
      photoCount: 0,
      hasElevationOptimize: false,
      directionText: formParams.isAutoDirection ? '自动最优' : `${formParams.direction}°`
    }
  }

  let totalMeters = 0
  let photoCount = 0
  let hasElevationOptimize = false

  for (let i = 0; i < points.length; i++) {
    const p = points[i]
    if (p.type === 'START_PHOTO' || p.type === 'END_PHOTO' || p.type === 'ELEVATION_OPTIMIZE') {
      photoCount++
    }
    if (p.type === 'ELEVATION_OPTIMIZE') {
      hasElevationOptimize = true
    }
    if (i < points.length - 1) {
      const takeoffH = Number(getTakeoffCoord()?.height || 0)
      const p1 = Cesium.Cartesian3.fromDegrees(p.longitude, p.latitude, (Number(p.height) || Number(formParams.flightHeight) || 100) + takeoffH)
      const p2 = Cesium.Cartesian3.fromDegrees(points[i + 1].longitude, points[i + 1].latitude, (Number(points[i + 1].height) || Number(formParams.flightHeight) || 100) + takeoffH)
      totalMeters += Cesium.Cartesian3.distance(p1, p2)
    }
  }

  const speed = Number(formParams.flightSpeed) || 15
  const totalSeconds = Math.round(totalMeters / speed)
  const mins = Math.floor(totalSeconds / 60)
  const secs = totalSeconds % 60

  return {
    totalDistance: totalMeters >= 1000 ? `${(totalMeters / 1000).toFixed(2)} km` : `${totalMeters.toFixed(0)} m`,
    estimatedTime: mins > 0 ? `${mins}分${secs}秒` : `${secs}秒`,
    pointCount: points.length,
    photoCount,
    hasElevationOptimize,
    directionText: `${Number(formParams.direction || 0).toFixed(1)}°`
  }
})

// --- 3D 起伏地形加载管理 (固定使用 3D 真实起伏地形) ---
const globalTerrainPromise = shallowRef<any>(null)

const getGlobalTerrainProvider = (Cesium: any) => {
  if (!Cesium) return Promise.resolve(null)
  if (Cesium.Ion) {
    Cesium.Ion.defaultAccessToken = (CURRENT_CONFIG as any).cesiumIonToken || (CURRENT_CONFIG as any).VITE_CESIUM_ION_TOKEN
  }
  if (globalTerrainPromise.value) {
    return globalTerrainPromise.value
  }

  globalTerrainPromise.value = (async () => {
    try {
      if (typeof Cesium.createWorldTerrainAsync === 'function') {
        const terrain = await Cesium.createWorldTerrainAsync({
          requestWaterMask: true,
          requestVertexNormals: true
        })
        return terrain
      } else if (typeof Cesium.createWorldTerrain === 'function') {
        return Cesium.createWorldTerrain({
          requestWaterMask: true,
          requestVertexNormals: true
        })
      } else if (typeof Cesium.CesiumTerrainProvider?.fromUrl === 'function') {
        const terrain = await Cesium.CesiumTerrainProvider.fromUrl(
          'https://assets.ion.cesium.com/1',
          { requestWaterMask: true, requestVertexNormals: true }
        )
        return terrain
      }
    } catch (e) {
      console.warn('3D 地形加载异常:', e)
    }
    return null
  })()

  return globalTerrainPromise.value
}

// --- 初始化 Cesium 地图 ---
const initCesium = () => {
  if (!cesiumContainerRef.value) return

  const tdtToken = CURRENT_CONFIG.tiandituToken || '4db514621ce57a692181a8bc4ddb9d72'
  if (Cesium.Ion) {
    Cesium.Ion.defaultAccessToken = (CURRENT_CONFIG as any).cesiumIonToken || (CURRENT_CONFIG as any).VITE_CESIUM_ION_TOKEN
  }

  // 天地图卫星影像与注记
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
    infoBox: false,
    selectionIndicator: false,
    skyAtmosphere: false,
    skyBox: false,
    sceneModePicker: false,
    navigationHelpButton: false,
    homeButton: false,
    geocoder: false,
    baseLayerPicker: false,
    baseLayer: new Cesium.ImageryLayer(imgProvider),
    targetFrameRate: 60,
    contextOptions: {
      webgl: {
        alpha: false,
        antialias: true,
        powerPreference: 'high-performance'
      }
    }
  })

  viewer.imageryLayers.addImageryProvider(ciaProvider)
  if (viewer._cesiumWidget?._creditContainer) {
    viewer._cesiumWidget._creditContainer.style.display = 'none'
  }

  viewer.scene.globe.enableLighting = false
  // 开启 3D 地形与深度检测 (固定使用 3D 真实起伏地形)
  viewer.scene.globe.depthTestAgainstTerrain = true
  getGlobalTerrainProvider(Cesium).then(terrain => {
    if (terrain && viewer && !viewer.isDestroyed()) {
      viewer.terrainProvider = terrain
    }
  }).catch(e => {
    console.warn('3D 地形加载异常:', e)
  })

  viewerRef.value = markRaw(viewer)

  // 绑定鼠标事件
  setupMouseEvents(viewer)

  // 尝试定位到初始位置
  flyToInitialLocation()

  // 检查是否有传入的编辑数据需要恢复
  restoreEditData()
}

// 恢复编辑数据
const restoreEditData = () => {
  const rawData = props.editWaylineData
  if (!rawData) return

  const data = rawData.data ? rawData.data : rawData

  // 恢复基础参数
  if (data.global_height) formParams.flightHeight = Number(data.global_height)
  if (data.auto_flight_speed) formParams.flightSpeed = Number(data.auto_flight_speed)

  const req = data.mapping_type_req
  if (req) {
    if (req.overlap_h !== undefined) formParams.overlap_h = Number(req.overlap_h)
    if (req.overlap_w !== undefined) formParams.overlap_w = Number(req.overlap_w)
    if (req.shoot_type) formParams.shoot_type = req.shoot_type
    if (req.is_elevation_optimize !== undefined || req.elevation_optimize_enable !== undefined) {
      formParams.is_elevation_optimize = Boolean(req.is_elevation_optimize ?? req.elevation_optimize_enable)
    }
    if (req.direction !== undefined && req.direction !== '') {
      formParams.isAutoDirection = false
      formParams.direction = Number(req.direction) || 0
    } else {
      formParams.isAutoDirection = true
    }

    // 恢复多边形顶点
    const coords = req.coordinates || data.coordinates
    if (Array.isArray(coords) && coords.length >= 3) {
      polygonCoordinates.value = coords.map((c: any) => ({
        latitude: Number(c.latitude ?? c.lat),
        longitude: Number(c.longitude ?? c.lon)
      }))
      drawMode.value = 'done'
      renderPolygonEntities()
    }
  }

  // 恢复航线路径预览 (提取 mapping_type_req.sim_route_point_list 或 route_point_list)
  const rawPoints = extractRoutePoints(data)
  if (rawPoints && rawPoints.length > 0) {
    previewRoutePoints.value = normalizePoints(rawPoints)
    hasPreview.value = true
    renderRouteEntities()
  }

  // 恢复起飞参考点 Marker
  renderTakeoffMarker()

  // 飞到测区中心
  if (polygonCoordinates.value.length >= 3 || previewRoutePoints.value.length > 0) {
    flyToPolygon()
  }
}

// 初始定位：优先使用测区，其次起飞参考点
const flyToInitialLocation = () => {
  const viewer = viewerRef.value
  if (!viewer) return

  const coord = getTakeoffCoord()
  if (coord) {
    viewer.camera.flyTo({
      destination: Cesium.Cartesian3.fromDegrees(
        coord.longitude,
        coord.latitude,
        (Number(coord.height) || 0) + 1200
      ),
      orientation: {
        heading: 0,
        pitch: Cesium.Math.toRadians(-60),
        roll: 0
      },
      duration: 1.5
    })
    renderTakeoffMarker()
  }
}

// 绘制起飞点 Marker
let takeoffEntity: Cesium.Entity | null = null
const renderTakeoffMarker = () => {
  const viewer = viewerRef.value
  if (!viewer) return

  const coord = getTakeoffCoord()

  if (takeoffEntity) {
    viewer.entities.remove(takeoffEntity)
    takeoffEntity = null
  }

  if (!coord) return

  const isClamp = !(coord.height && coord.height > 0)
  takeoffEntity = viewer.entities.add({
    position: Cesium.Cartesian3.fromDegrees(
      coord.longitude,
      coord.latitude,
      coord.height || 0
    ),
    point: {
      pixelSize: 12,
      color: Cesium.Color.fromCssColorString('#ff4d4f'),
      outlineColor: Cesium.Color.WHITE,
      outlineWidth: 2,
      heightReference: isClamp ? Cesium.HeightReference.CLAMP_TO_GROUND : Cesium.HeightReference.NONE,
      disableDepthTestDistance: Number.POSITIVE_INFINITY
    },
    label: {
      text: '起飞参考点',
      font: '12px sans-serif',
      fillColor: Cesium.Color.WHITE,
      showBackground: true,
      backgroundColor: Cesium.Color.fromCssColorString('rgba(255, 77, 79, 0.85)'),
      pixelOffset: new Cesium.Cartesian2(0, -24),
      verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
      heightReference: isClamp ? Cesium.HeightReference.CLAMP_TO_GROUND : Cesium.HeightReference.NONE,
      disableDepthTestDistance: Number.POSITIVE_INFINITY
    }
  })
}

// 聚焦到具体航点
const focusWaypoint = (wp: any) => {
  const viewer = viewerRef.value
  if (!viewer || !wp) return
  const takeoffH = Number(getTakeoffCoord()?.height || 0)
  const absHeight = (Number(wp.height) || Number(formParams.flightHeight) || 100) + takeoffH
  viewer.camera.flyTo({
    destination: Cesium.Cartesian3.fromDegrees(wp.longitude, wp.latitude, absHeight + 160),
    orientation: {
      heading: 0,
      pitch: Cesium.Math.toRadians(-60),
      roll: 0
    },
    duration: 1.0
  })
}

// 鼠标事件绑定：多边形绘制与航点点击拾取
const setupMouseEvents = (viewer: Cesium.Viewer) => {
  handler = new Cesium.ScreenSpaceEventHandler(viewer.canvas)

  // 拾取地面世界坐标通用函数
  const pickGroundPosition = (windowPos: Cesium.Cartesian2): Cesium.Cartesian3 | null => {
    const ray = viewer.camera.getPickRay(windowPos)
    if (!ray) return null
    let cartesian = viewer.scene.globe.pick(ray, viewer.scene)
    if (!cartesian) {
      cartesian = viewer.camera.pickEllipsoid(windowPos, viewer.scene.globe.ellipsoid)
    }
    return cartesian
  }

  // 左键点击：
  // 左键点击：
  // 1. 若处于绘制状态：添加测区顶点 (非预览只读)
  // 2. 若处于正常状态：拾取点击的航点并展示详情
  handler.setInputAction((event: any) => {
    if (!isPreviewOrReadOnly.value && drawMode.value === 'drawing') {
      const cartesian = pickGroundPosition(event.position)
      if (!cartesian) return

      const carto = Cesium.Cartographic.fromCartesian(cartesian)
      const longitude = Cesium.Math.toDegrees(carto.longitude)
      const latitude = Cesium.Math.toDegrees(carto.latitude)

      polygonCoordinates.value.push({
        latitude: Number(latitude.toFixed(7)),
        longitude: Number(longitude.toFixed(7))
      })

      // 有新点添加，需要重新生成预览
      hasPreview.value = false
      clearRouteEntities()

      renderPolygonEntities()
      return
    }

    // 非绘制模式下：拾取点击实体
    const picked = viewer.scene.pick(event.position)
    if (Cesium.defined(picked) && picked.id && (picked.id as any)._waypointData) {
      const wp = (picked.id as any)._waypointData
      const typeDescMap: Record<string, { title: string; desc: string; cls: string }> = {
        START_PHOTO: { title: '拍照起始点', desc: '相机在此点开始触发曝光采集照片', cls: 'type-start' },
        END_PHOTO: { title: '拍照结束点', desc: '相机完成该段/航带照片采集', cls: 'type-end' },
        ELEVATION_OPTIMIZE: { title: '高程优化采集点', desc: '在测区中央垂直拍摄以校正地形高程', cls: 'type-elevation' },
        NORMAL: { title: '航线转向航点', desc: 'S形航带转弯与过渡飞行航点', cls: 'type-normal' }
      }
      const meta = typeDescMap[wp.type] || { title: '飞行航点', desc: '规划的飞行路径点', cls: 'type-normal' }
      selectedWaypoint.value = {
        ...wp,
        typeTitle: meta.title,
        typeDesc: meta.desc,
        typeClass: meta.cls
      }
    } else {
      selectedWaypoint.value = null
    }
  }, Cesium.ScreenSpaceEventType.LEFT_CLICK)

  let draggedVertexIdx = -1

  handler.setInputAction((event: any) => {
    // 预览/只读模式下，禁止拖拽顶点或插入中点调整范围
    if (isPreviewOrReadOnly.value) return

    if (drawMode.value === 'done' || drawMode.value === 'drawing') {
      const picked = viewer.scene.pick(event.position)
      if (Cesium.defined(picked) && picked.id) {
        if ((picked.id as any)._vertexIdx !== undefined) {
          draggedVertexIdx = (picked.id as any)._vertexIdx
          viewer.scene.screenSpaceCameraController.enableRotate = false
          viewer.scene.screenSpaceCameraController.enableTranslate = false
          return
        } else if ((picked.id as any)._isMidpoint) {
          const insertIdx = (picked.id as any)._insertIdx
          const c1 = polygonCoordinates.value[(insertIdx - 1 + polygonCoordinates.value.length) % polygonCoordinates.value.length]
          const c2 = polygonCoordinates.value[insertIdx % polygonCoordinates.value.length]
          
          // 🔥 关键修复：直接在原数组上 splice，让 Vue 自动追踪响应式变化
          polygonCoordinates.value.splice(insertIdx, 0, {
            longitude: Number(((c1.longitude + c2.longitude) / 2).toFixed(7)),
            latitude: Number(((c1.latitude + c2.latitude) / 2).toFixed(7))
          })
          
          // 立即刷新实体，确保新顶点可见
          nextTick(() => {
            renderPolygonEntities()
            // 设置新插入顶点为拖拽目标
            draggedVertexIdx = insertIdx
            viewer.scene.screenSpaceCameraController.enableRotate = false
            viewer.scene.screenSpaceCameraController.enableTranslate = false
          })
          return
        }
      }
    }
  }, Cesium.ScreenSpaceEventType.LEFT_DOWN)

  handler.setInputAction((event: any) => {
    if (isPreviewOrReadOnly.value) return

    if (draggedVertexIdx >= 0) {
      viewer.scene.screenSpaceCameraController.enableRotate = true
      viewer.scene.screenSpaceCameraController.enableTranslate = true
      
      // 触发重新生成航线预览
      if (drawMode.value === 'done') {
        hasPreview.value = false
        clearRouteEntities()
        message.info('测区已更新，需重新计算航线。')
      }
      draggedVertexIdx = -1
    }
  }, Cesium.ScreenSpaceEventType.LEFT_UP)

  // 鼠标移动：橡皮筋连线或拖拽顶点
  handler.setInputAction((event: any) => {
    if (isPreviewOrReadOnly.value) return

    const cartesian = pickGroundPosition(event.endPosition)
    
    // 处理顶点拖拽
    if (draggedVertexIdx >= 0 && cartesian) {
      const carto = Cesium.Cartographic.fromCartesian(cartesian)
      polygonCoordinates.value[draggedVertexIdx] = {
        longitude: Cesium.Math.toDegrees(carto.longitude),
        latitude: Cesium.Math.toDegrees(carto.latitude)
      }
      return
    }

    // 处理橡皮筋
    if (drawMode.value !== 'drawing' || polygonCoordinates.value.length === 0) {
      activeMousePosition.value = null
      return
    }
    
    if (cartesian) {
      activeMousePosition.value = cartesian
      updateRubberLine(cartesian)
    }
  }, Cesium.ScreenSpaceEventType.MOUSE_MOVE)

  // 双击或右键：闭合测区
  handler.setInputAction(() => {
    if (isPreviewOrReadOnly.value) return
    if (drawMode.value === 'drawing') {
      finishDrawPolygon()
    }
  }, Cesium.ScreenSpaceEventType.LEFT_DOUBLE_CLICK)

  handler.setInputAction((event: any) => {
    if (isPreviewOrReadOnly.value) return

    if (drawMode.value === 'drawing') {
      finishDrawPolygon()
    } else if (drawMode.value === 'done') {
      const picked = viewer.scene.pick(event.position)
      if (Cesium.defined(picked) && picked.id && (picked.id as any)._vertexIdx !== undefined) {
        if (polygonCoordinates.value.length > 3) {
          const idx = (picked.id as any)._vertexIdx
          polygonCoordinates.value.splice(idx, 1)
          renderPolygonEntities()
          hasPreview.value = false
          clearRouteEntities()
          message.info('顶点已删除，需重新计算航线。')
        } else {
          message.warning('测区至少需要3个顶点！')
        }
      }
    }
  }, Cesium.ScreenSpaceEventType.RIGHT_CLICK)
}

// 橡皮筋辅助线实体更新
const updateRubberLine = (mousePos: Cesium.Cartesian3) => {
  const viewer = viewerRef.value
  if (!viewer || polygonCoordinates.value.length === 0) return

  const lastCoord = polygonCoordinates.value[polygonCoordinates.value.length - 1]
  const lastPos = Cesium.Cartesian3.fromDegrees(lastCoord.longitude, lastCoord.latitude, 0)

  if (!mouseRubberLineEntity) {
    mouseRubberLineEntity = viewer.entities.add({
      polyline: {
        positions: new Cesium.CallbackProperty(() => {
          if (!activeMousePosition.value) return []
          return [lastPos, activeMousePosition.value]
        }, false),
        width: 2,
        material: new Cesium.PolylineDashMaterialProperty({
          color: Cesium.Color.fromCssColorString('#00f0ff'),
          dashLength: 12
        }),
        clampToGround: true
      }
    })
  }
}

const clearRubberLine = () => {
  if (mouseRubberLineEntity && viewerRef.value) {
    viewerRef.value.entities.remove(mouseRubberLineEntity)
    mouseRubberLineEntity = null
  }
  activeMousePosition.value = null
}

// --- 测区绘制与渲染 ---
const startDrawPolygon = () => {
  drawMode.value = 'drawing'
  polygonCoordinates.value = []
  hasPreview.value = false
  clearPolygonEntities()
  clearRouteEntities()
  message.info('已开启绘制模式，请在地图上点击添加多边形顶点')
}

const finishDrawPolygon = () => {
  if (polygonCoordinates.value.length < 3) {
    message.warning('多边形测区至少需要 3 个顶点')
    return
  }
  drawMode.value = 'done'
  clearRubberLine()
  renderPolygonEntities()
  message.success(`测区绘制完成，共 ${polygonCoordinates.value.length} 个顶点`)
}

const cancelDrawPolygon = () => {
  drawMode.value = 'idle'
  polygonCoordinates.value = []
  clearRubberLine()
  clearPolygonEntities()
}

const clearPolygon = () => {
  polygonCoordinates.value = []
  drawMode.value = 'idle'
  hasPreview.value = false
  clearRubberLine()
  clearPolygonEntities()
  clearRouteEntities()
}

// 渲染多边形面、边线与顶点小标
const renderPolygonEntities = () => {
  const viewer = viewerRef.value
  if (!viewer) return

  clearPolygonEntities()

  const coords = polygonCoordinates.value
  if (coords.length === 0) return

  // 1. 顶点标记
  coords.forEach((c, idx) => {
    const entity = viewer.entities.add({
      position: new Cesium.CallbackProperty(() => {
        const cCoord = polygonCoordinates.value[idx]
        return cCoord ? Cesium.Cartesian3.fromDegrees(cCoord.longitude, cCoord.latitude, 0) : Cesium.Cartesian3.ZERO
      }, false),
      point: {
        pixelSize: isPreviewOrReadOnly.value ? 7 : 11,
        color: Cesium.Color.fromCssColorString('#ffd000'),
        outlineColor: Cesium.Color.fromCssColorString('#000000'),
        outlineWidth: 1.5,
        heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
        disableDepthTestDistance: Number.POSITIVE_INFINITY
      }
    });
    // 仅在非预览/编辑模式下绑定顶点索引，以供鼠标拖拽调整
    if (!isPreviewOrReadOnly.value) {
      (entity as any)._vertexIdx = idx;
    }
    vertexEntities.push(entity)
  })

  // 1.5 中间点标记 (仅在非预览只读模式 且 drawMode === 'done' 且顶点数 >= 3 时显示供插入顶点)
  if (!isPreviewOrReadOnly.value && drawMode.value === 'done' && coords.length >= 3) {
    coords.forEach((c, idx) => {
      const nextIdx = (idx + 1) % coords.length
      const entity = viewer.entities.add({
        position: new Cesium.CallbackProperty(() => {
          const c1 = polygonCoordinates.value[idx]
          const c2 = polygonCoordinates.value[nextIdx]
          if (!c1 || !c2) return Cesium.Cartesian3.ZERO
          // 近似球面中点（对于较小测区直接用经纬度平均值也足够）
          const lat = (c1.latitude + c2.latitude) / 2
          const lon = (c1.longitude + c2.longitude) / 2
          return Cesium.Cartesian3.fromDegrees(lon, lat, 0)
        }, false),
        point: {
          pixelSize: 8,
          color: Cesium.Color.fromCssColorString('#ffffff').withAlpha(0.7),
          outlineColor: Cesium.Color.fromCssColorString('#000000').withAlpha(0.5),
          outlineWidth: 1,
          heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
          disableDepthTestDistance: Number.POSITIVE_INFINITY
        }
      });
      (entity as any)._isMidpoint = true;
      (entity as any)._insertIdx = nextIdx; // 点击此点后，新顶点插入在 nextIdx 位置
      vertexEntities.push(entity)
    })
  }

  // 2. 边框折线 (当点数 >= 2 时)
  if (coords.length >= 2) {
    outlineEntity = viewer.entities.add({
      polyline: {
        positions: new Cesium.CallbackProperty(() => {
          const currentCoords = polygonCoordinates.value;
          if (currentCoords.length < 2) return [];
          const boundaryPositions = currentCoords.map(c => Cesium.Cartesian3.fromDegrees(c.longitude, c.latitude, 0));
          if (drawMode.value === 'done' || currentCoords.length >= 3) {
             boundaryPositions.push(Cesium.Cartesian3.fromDegrees(currentCoords[0].longitude, currentCoords[0].latitude, 0));
          }
          return boundaryPositions;
        }, false),
        width: 3,
        material: Cesium.Color.fromCssColorString('#00e5ff'),
        clampToGround: true
      }
    })
  }

  // 3. 多边形半透明填充 (仅当 >= 3 个点且已闭合或有一定形状时)
  if (coords.length >= 3) {
    polygonEntity = viewer.entities.add({
      polygon: {
        hierarchy: new Cesium.CallbackProperty(() => {
           const currentCoords = polygonCoordinates.value;
           const positions = currentCoords.map(c => Cesium.Cartesian3.fromDegrees(c.longitude, c.latitude, 0));
           return new Cesium.PolygonHierarchy(positions);
        }, false),
        material: Cesium.Color.fromCssColorString('#00b4d8').withAlpha(0.28),
        heightReference: Cesium.HeightReference.CLAMP_TO_GROUND
      }
    })
  }
}

const clearPolygonEntities = () => {
  const viewer = viewerRef.value
  if (!viewer) return
  if (polygonEntity) {
    viewer.entities.remove(polygonEntity)
    polygonEntity = null
  }
  if (outlineEntity) {
    viewer.entities.remove(outlineEntity)
    outlineEntity = null
  }
  vertexEntities.forEach(ent => viewer.entities.remove(ent))
  vertexEntities = []
}

// 聚焦到多边形与航线整体视角
const flyToPolygon = () => {
  const viewer = viewerRef.value
  if (!viewer) return
  const takeoffH = Number(getTakeoffCoord()?.height || 0)
  const positions: Cesium.Cartesian3[] = []
  polygonCoordinates.value.forEach(c => {
    positions.push(Cesium.Cartesian3.fromDegrees(c.longitude, c.latitude, 0))
  })
  if (previewRoutePoints.value.length > 0) {
    previewRoutePoints.value.forEach(p => {
      const absHeight = (Number(p.height) || Number(formParams.flightHeight) || 100) + takeoffH
      positions.push(Cesium.Cartesian3.fromDegrees(p.longitude, p.latitude, absHeight))
    })
  }
  if (positions.length === 0) return
  const boundingSphere = Cesium.BoundingSphere.fromPoints(positions)
  viewer.camera.flyToBoundingSphere(boundingSphere, {
    duration: 1.2,
    offset: new Cesium.HeadingPitchRange(0, Cesium.Math.toRadians(-55), Math.max(500, boundingSphere.radius * 2.2))
  })
}

// 聚焦到起飞点或测区
const flyToAreaOrDock = () => {
  if (polygonCoordinates.value.length >= 3 || previewRoutePoints.value.length > 0) {
    flyToPolygon()
  } else {
    flyToInitialLocation()
  }
}

// 正俯视视角
const switchToTopDownView = () => {
  const viewer = viewerRef.value
  if (!viewer) return
  const camera = viewer.camera
  const ray = new Cesium.Ray(camera.position, camera.direction)
  let target = viewer.scene.globe.pick(ray, viewer.scene)
  if (!target) {
    target = camera.pickEllipsoid(new Cesium.Cartesian2(viewer.canvas.width / 2, viewer.canvas.height / 2))
  }
  if (!target) return
  const distance = Cesium.Cartesian3.distance(camera.position, target)
  const carto = Cesium.Cartographic.fromCartesian(target)
  const dest = Cesium.Cartesian3.fromDegrees(
    Cesium.Math.toDegrees(carto.longitude),
    Cesium.Math.toDegrees(carto.latitude),
    carto.height + Math.max(150, distance)
  )

  camera.flyTo({
    destination: dest,
    orientation: {
      heading: camera.heading,
      pitch: Cesium.Math.toRadians(-90),
      roll: 0
    },
    duration: 1.0
  })
}

// --- 航线真实渲染逻辑 ---
const renderRouteEntities = () => {
  const viewer = viewerRef.value
  if (!viewer) return

  clearRouteEntities()

  const points = previewRoutePoints.value
  if (!points || points.length === 0) return

  const takeoffH = Number(getTakeoffCoord()?.height || 0)
  const positions: Cesium.Cartesian3[] = []
  const groundPositions: Cesium.Cartesian3[] = []

  points.forEach((p, idx) => {
    const relH = Number(p.height) || Number(formParams.flightHeight) || 100
    const absH = relH + takeoffH
    const pos = Cesium.Cartesian3.fromDegrees(p.longitude, p.latitude, absH)

    let groundHeight = takeoffH
    const carto = Cesium.Cartographic.fromDegrees(p.longitude, p.latitude)
    const sampledHeight = viewer.scene.globe.getHeight(carto)
    if (sampledHeight !== undefined && !Number.isNaN(sampledHeight) && sampledHeight !== null) {
      groundHeight = sampledHeight
    } else {
      console.warn(`航点${idx + 1} 地形数据暂未加载，使用参考起飞高度 ${takeoffH}m`)
    }
    const posGround = Cesium.Cartesian3.fromDegrees(p.longitude, p.latitude, groundHeight)
    positions.push(pos)
    groundPositions.push(posGround)

    // 统一的航点样式（不区分特殊类型）
    let pointColor = Cesium.Color.fromCssColorString('#38bdf8') // 统一天蓝色
    let outlineColor = Cesium.Color.fromCssColorString('#0284c7')
    let pointSize = 7
    let labelText = `${idx + 1}`
    let labelBg = Cesium.Color.fromCssColorString('rgba(15, 23, 42, 0.75)')
    let labelPixelOffset = new Cesium.Cartesian2(0, -16)
    let isSpecial = false

    // 航点实体标记
    const ent = viewer.entities.add({
      name: `waypoint_${idx + 1}`,
      position: pos,
      point: {
        pixelSize: pointSize,
        color: pointColor,
        outlineColor: outlineColor,
        outlineWidth: 2,
        disableDepthTestDistance: Number.POSITIVE_INFINITY
      },
      label: {
        text: labelText,
        font: isSpecial ? 'bold 12px sans-serif' : '10px monospace',
        fillColor: Cesium.Color.WHITE,
        showBackground: true,
        backgroundColor: labelBg,
        pixelOffset: labelPixelOffset,
        verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
        distanceDisplayCondition: isSpecial ? undefined : new Cesium.DistanceDisplayCondition(0, 5000)
      }
    })
    ;(ent as any)._waypointData = p
    waypointEntities.push(ent)

    // 为关键特殊点 (拍照起点、拍照终点、高程优化点) 添加垂直地面投影引线，强化 3D 悬浮感
    if (isSpecial) {
      const dropLine = viewer.entities.add({
        polyline: {
          positions: [posGround, pos],
          width: 1.5,
          material: new Cesium.PolylineDashMaterialProperty({
            color: pointColor.withAlpha(0.6),
            dashLength: 8
          }),
          clampToGround: false
        },
        point: {
          position: posGround,
          pixelSize: 5,
          color: pointColor.withAlpha(0.5),
          disableDepthTestDistance: Number.POSITIVE_INFINITY
        }
      })
      verticalLineEntities.push(dropLine)
    }
  })

  // 航线连续折线 (带方向箭头的青蓝色航线)
  if (positions.length >= 2) {
    routePolylineEntity = viewer.entities.add({
      name: 'mapping2d_flight_path',
      polyline: {
        positions: positions,
        width: 5,
        material: new Cesium.PolylineArrowMaterialProperty(
          Cesium.Color.fromCssColorString('#00f0ff')
        ),
        clampToGround: false
      }
    })
  }

  // 起飞点至航线首航点导引线 (黄色虚线)
  const takeoffCoord = getTakeoffCoord()
  if (takeoffCoord && positions.length > 0) {
    const takeoffGround = Cesium.Cartesian3.fromDegrees(takeoffCoord.longitude, takeoffCoord.latitude, takeoffCoord.height || 0)
    const firstAbsHeight = (Number(points[0].height) || Number(formParams.flightHeight) || 100) + takeoffH
    const takeoffAir = Cesium.Cartesian3.fromDegrees(takeoffCoord.longitude, takeoffCoord.latitude, firstAbsHeight)
    const startPoint = positions[0]

    takeoffGuideLineEntity = viewer.entities.add({
      name: 'takeoff_guide_line',
      polyline: {
        positions: [takeoffGround, takeoffAir, startPoint],
        width: 2.5,
        material: new Cesium.PolylineDashMaterialProperty({
          color: Cesium.Color.fromCssColorString('#ffb703'),
          dashLength: 14
        }),
        clampToGround: false
      }
    })

    // 航线末尾返航引导线 (橙色虚线，代表 finish_action: goHome)
    const lastPoint = positions[positions.length - 1]
    returnGuideLineEntity = viewer.entities.add({
      name: 'return_guide_line',
      polyline: {
        positions: [lastPoint, takeoffAir, takeoffGround],
        width: 2,
        material: new Cesium.PolylineDashMaterialProperty({
          color: Cesium.Color.fromCssColorString('#ff7043'),
          dashLength: 12
        }),
        clampToGround: false
      }
    })
  }

  // 🔥 关键优化：渲染完成后，异步批量更新地形高度
  setTimeout(() => {
    batchUpdateTerrainHeightsForWaypoints()
  }, 1000) // 延迟1秒，等待地形数据加载
}

// 批量更新航点的地形高度（面状航线专用）
const batchUpdateTerrainHeightsForWaypoints = async () => {
  const viewer = viewerRef.value
  if (!viewer || !viewer.terrainProvider || waypointEntities.length === 0) return

  try {
    const points = previewRoutePoints.value
    if (!points || points.length === 0) return

    // 准备所有航点的地理坐标
    const positions = points.map(p => {
      return Cesium.Cartographic.fromDegrees(p.longitude, p.latitude)
    })

    // 批量采样地形高度（使用最高精度）
    const sampledPositions = await Cesium.sampleTerrainMostDetailed(
      viewer.terrainProvider,
      positions
    )

    const takeoffH = Number(getTakeoffCoord()?.height || 0)

    // 更新每个航点的标签和垂直线
    sampledPositions.forEach((sampledPos, idx) => {
      if (idx >= waypointEntities.length) return

      const point = points[idx]
      const entity = waypointEntities[idx]
      const terrainHeight = sampledPos.height

      // 验证地形高度有效性
      if (terrainHeight === undefined || Number.isNaN(terrainHeight) || terrainHeight === null) {
        console.warn(`面状航线航点${idx + 1} 批量采样失败`)
        return
      }

      // 计算距地高度（AGL）
      const relH = Number(point.height) || Number(formParams.flightHeight) || 100
      const absH = relH + takeoffH
      const agl = absH - terrainHeight

      // 更新标签文本，添加距地高度信息
      let currentLabel = entity.label?.text?.getValue() || `${idx + 1}`
      
      // 如果是特殊航点（拍照起点、结束点等），保持原有标签，只追加距地信息
      if (point.type === 'START_PHOTO' || point.type === 'END_PHOTO' || point.type === 'ELEVATION_OPTIMIZE') {
        // 特殊航点：只在距地过低时添加警告
        if (agl < 30) {
          currentLabel = currentLabel.replace(/\n⚠️.*$/, '') // 移除旧警告
          currentLabel += `\n⚠️距地:${agl.toFixed(0)}m`
          if (entity.point) {
            entity.point.color = Cesium.Color.fromCssColorString('#ff3d71') // 红色警告
          }
        }
      } else {
        // 普通航点：更新完整信息
        if (agl < 30) {
          currentLabel = `${idx + 1}\n⚠️距地:${agl.toFixed(0)}m`
          if (agl < 0) {
            currentLabel += ` (撞地!)`
          }
          if (entity.point) {
            entity.point.color = Cesium.Color.fromCssColorString('#ff1744') // 红色
            entity.point.outlineColor = Cesium.Color.WHITE
          }
        } else {
          currentLabel = `${idx + 1}\n距地:${agl.toFixed(0)}m`
        }
      }

      if (entity.label) {
        entity.label.text = currentLabel
      }

      // 更新垂直线的地面端点
      if (idx < verticalLineEntities.length) {
        const vertLineEntity = verticalLineEntities[idx]
        const posGround = Cesium.Cartesian3.fromDegrees(
          point.longitude, 
          point.latitude, 
          terrainHeight
        )
        const posAir = Cesium.Cartesian3.fromDegrees(
          point.longitude, 
          point.latitude, 
          absH
        )
        if (vertLineEntity.polyline) {
          vertLineEntity.polyline.positions = [posAir, posGround]
        }
      }
    })

    console.log(`✅ 面状航线批量地形采样完成：${sampledPositions.length} 个航点`)

  } catch (error) {
    console.warn('面状航线批量地形采样失败:', error)
  }
}

const clearRouteEntities = () => {
  const viewer = viewerRef.value
  if (!viewer) return
  if (routePolylineEntity) {
    viewer.entities.remove(routePolylineEntity)
    routePolylineEntity = null
  }
  if (takeoffGuideLineEntity) {
    viewer.entities.remove(takeoffGuideLineEntity)
    takeoffGuideLineEntity = null
  }
  if (returnGuideLineEntity) {
    viewer.entities.remove(returnGuideLineEntity)
    returnGuideLineEntity = null
  }
  waypointEntities.forEach(ent => viewer.entities.remove(ent))
  waypointEntities = []
  verticalLineEntities.forEach(ent => viewer.entities.remove(ent))
  verticalLineEntities = []
  selectedWaypoint.value = null
}

// --- 调用 Prepare 接口生成航线预览 ---
const handlePrepareRoute = async () => {
  if (polygonCoordinates.value.length < 3) {
    message.warning('请先在地图上框选有效的多边形测区（至少3个顶点）')
    return
  }

  const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId)
  if (!workspaceId) {
    message.error('未找到工作空间ID，请重新登录')
    return
  }

  isPreparing.value = true

  // 组装完整的 prepare 请求体
  let takeOffStr = ''
  const takeoffCoord = getTakeoffCoord()
  if (takeoffCoord) {
    takeOffStr = `${takeoffCoord.latitude},${takeoffCoord.longitude},${takeoffCoord.height || 0}`
  } else if (props.config?.take_off_ref_point) {
    takeOffStr = props.config.take_off_ref_point
  }

  const payload: any = {
    auto_flight_speed: Number(formParams.flightSpeed),
    drone_type: props.config?.drone_type || 100,
    sub_drone_type: props.config?.sub_drone_type || 1,
    payload_type: props.config?.payload_type || 99,
    payload_sub_type: props.config?.payload_sub_type || 0,
    payload_position: props.config?.payload_position || 0,
    image_format: props.config?.image_format || 'visable',
    finish_action: props.config?.finish_action || 'goHome',
    exit_on_rc_lost_action: props.config?.exit_on_rc_lost_action || 'goBack',
    global_height: Number(formParams.flightHeight),
    height_mode: 'relativeToStartPoint',
    take_off_ref_point: takeOffStr,
    take_off_ref_point_agl_height: props.config?.take_off_ref_point_agl_height ?? -10,
    take_off_ref_point_aglheight: props.config?.take_off_ref_point_aglheight ?? 0,
    template_type: 'mapping2d',
    mapping_type_req: {
      collection_method: 'ortho',
      overlap_h: Number(formParams.overlap_h),
      overlap_w: Number(formParams.overlap_w),
      is_elevation_optimize: formParams.is_elevation_optimize ? 1 : 0,
      elevation_optimize_enable: formParams.is_elevation_optimize ? 1 : 0,
      shoot_type: formParams.shoot_type,
      direction: formParams.isAutoDirection ? undefined : String(formParams.direction || 0),
      coordinates: polygonCoordinates.value.map(c => ({
        latitude: Number(c.latitude),
        longitude: Number(c.longitude)
      }))
    }
  }

  try {
    const res = await prepareWaylineEdit(workspaceId, payload)
    if (res && (res.code === 0 || res.code === 200) && res.data) {
      const data = res.data

      // 1. 同步后端计算的最优航向角
      if (data.mapping_type_req?.direction !== undefined && data.mapping_type_req.direction !== '') {
        const d = Number(data.mapping_type_req.direction)
        if (!isNaN(d)) {
          formParams.direction = Number(d.toFixed(1))
        }
      }

      // 2. 提取并归一化航线点位 (支持 sim_route_point_list 和 route_point_list)
      const rawPoints = extractRoutePoints(data)
      if (rawPoints && rawPoints.length > 0) {
        const points = normalizePoints(rawPoints)
        previewRoutePoints.value = points
        hasPreview.value = true
        renderRouteEntities()
        message.success(`航线生成成功，共规划 ${points.length} 个航点！`)
        // 自动缩放到全航线与测区整体视角
        flyToPolygon()
      } else {
        message.warning(res.message || '后端已计算但未返回有效航点列表')
      }
    } else {
      message.error(res?.message || '航线计算生成失败，请检查参数与测区形状')
    }
  } catch (err: any) {
    console.error('prepareWaylineEdit 异常:', err)
    message.error(`航线生成失败: ${err.message || '网络连接或接口异常'}`)
  } finally {
    isPreparing.value = false
  }
}

// --- 保存航线 ---
const handleSaveWayline = async () => {
  if (!hasPreview.value || previewRoutePoints.value.length === 0) {
    message.warning('请先点击【生成航线预览】计算并验证航线轨迹')
    return
  }

  const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId)
  if (!workspaceId) {
    message.error('未找到工作空间ID，请重新登录')
    return
  }

  const routeName = props.waylineName || props.config?.name || `面状航线_${new Date().toISOString().slice(0, 19).replace(/[:-]/g, '')}`

  const takeoffCoord = getTakeoffCoord()
  let takeOffStr = ''
  if (takeoffCoord) {
    takeOffStr = `${takeoffCoord.latitude},${takeoffCoord.longitude},${takeoffCoord.height || 0}`
  } else if (props.config?.take_off_ref_point) {
    takeOffStr = props.config.take_off_ref_point
  }

  const finalPayload: any = {
    ...props.config,
    name: routeName,
    template_type: 'mapping2d',
    auto_flight_speed: Number(formParams.flightSpeed),
    drone_type: props.config?.drone_type || 100,
    sub_drone_type: props.config?.sub_drone_type || 1,
    payload_type: props.config?.payload_type || 99,
    payload_sub_type: props.config?.payload_sub_type || 0,
    payload_position: props.config?.payload_position || 0,
    image_format: props.config?.image_format || 'visable',
    finish_action: props.config?.finish_action || 'goHome',
    exit_on_rc_lost_action: props.config?.exit_on_rc_lost_action || 'goBack',
    global_height: Number(formParams.flightHeight),
    height_mode: 'relativeToStartPoint',
    take_off_ref_point: takeOffStr,
    take_off_ref_point_agl_height: props.config?.take_off_ref_point_agl_height ?? -10,
    take_off_ref_point_aglheight: props.config?.take_off_ref_point_aglheight ?? 0,
    mapping_type_req: {
      collection_method: 'ortho',
      overlap_h: Number(formParams.overlap_h),
      overlap_w: Number(formParams.overlap_w),
      is_elevation_optimize: formParams.is_elevation_optimize ? 1 : 0,
      elevation_optimize_enable: formParams.is_elevation_optimize ? 1 : 0,
      shoot_type: formParams.shoot_type,
      direction: formParams.isAutoDirection ? undefined : String(formParams.direction || 0),
      coordinates: polygonCoordinates.value.map(c => ({
        latitude: Number(c.latitude),
        longitude: Number(c.longitude)
      })),
      sim_route_point_list: previewRoutePoints.value.map(p => ({
        wg_lon: p.longitude,
        wg_lat: p.latitude,
        height: p.height,
        type: p.type
      }))
    },
    route_point_list: previewRoutePoints.value
  }

  isSaving.value = true

  try {
    let res: any
    if (props.isEditMode && props.editWaylineId) {
      res = await modifyWaylineEdit(workspaceId, props.editWaylineId, finalPayload, props.dock_sn || '')
      if (res && (res.code === 0 || res.code === 200)) {
        message.success(`航线 "${routeName}" 修改成功！`)
        if (props.editWaylineId) {
          sessionStorage.removeItem(`editWaylineData_${props.editWaylineId}`)
          sessionStorage.removeItem(`wayline_draft_${props.editWaylineId}`)
        }
        sessionStorage.removeItem('editWaylineData')
        localStorage.removeItem('editWaylineData')
        router.back()
      } else {
        message.error(res?.message || '航线修改保存失败')
      }
    } else {
      res = await saveWaylineEdit(workspaceId, routeName, finalPayload)
      if (res && (res.code === 0 || res.code === 200)) {
        message.success(`面状航线 "${routeName}" 保存成功！`)
        sessionStorage.removeItem('editWaylineData')
        localStorage.removeItem('editWaylineData')
        router.back()
      } else {
        message.error(res?.message || '航线保存失败')
      }
    }
  } catch (err: any) {
    console.error('保存面状航线异常:', err)
    message.error(`保存航线失败: ${err.message || '未知错误'}`)
  } finally {
    isSaving.value = false
  }
}

// 返回
const handleBack = () => {
  emit('close')
  router.back()
}

// 监听高度变化，更新航点高度并刷新实体
watch(() => formParams.flightHeight, (newH) => {
  if (hasPreview.value && previewRoutePoints.value.length > 0) {
    previewRoutePoints.value.forEach(p => {
      p.height = Number(newH)
    })
    renderRouteEntities()
  }
})

// 监听传入的编辑航线数据异步更新
watch(() => props.editWaylineData, (newVal) => {
  if (newVal) {
    restoreEditData()
  }
}, { deep: true })

// 监听左侧面板折叠展开，平滑通知 Cesium 更新视口尺寸
watch(isPanelCollapsed, () => {
  nextTick(() => {
    setTimeout(() => {
      viewerRef.value?.resize?.()
    }, 300)
  })
})

// --- 图例相关功能 (同步 NewWayLineDjiCesium.vue) ---

// 动态生成 SVG 图标模板
const svgTemplate = `
<svg width="64" height="64" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <filter id="shadow" x="0" y="0" width="64" height="64" filterUnits="userSpaceOnUse">
      <feGaussianBlur in="SourceAlpha" stdDeviation="2"/>
      <feOffset dx="0" dy="2" result="offsetblur"/>
      <feComponentTransfer>
        <feFuncA type="linear" slope="0.5"/>
      </feComponentTransfer>
      <feMerge> 
        <feMergeNode/>
        <feMergeNode in="SourceGraphic"/> 
      </feMerge>
    </filter>
  </defs>
  <ellipse cx="32" cy="58" rx="10" ry="3" fill="#000000" fill-opacity="0.3" />
  <path d="M32 4 C18.745 4 8 14.745 8 28 C8 42 32 60 32 60 C32 60 56 42 56 28 C56 14.745 45.255 4 32 4Z" fill="{COLOR}" filter="url(#shadow)" stroke="#ffffff" stroke-width="1.5"/>
  <circle cx="32" cy="28" r="10" fill="white" fill-opacity="0.9"/>
</svg>`

const iconCache = new Map<string, string>()

function getMarkerIcon(color: string) {
  if (!color) return mapBjIcon
  if (iconCache.has(color)) {
    return iconCache.get(color)!
  }

  // 将 SVG 转换为 Data URI (base64)
  const svgContent = svgTemplate.replace('{COLOR}', color)
  const base64 = btoa(unescape(encodeURIComponent(svgContent)))
  const dataUri = `data:image/svg+xml;base64,${base64}`

  iconCache.set(color, dataUri)
  return dataUri
}

// 图例开关变化处理 (接收 MapLegend 发出的 { legendList, legendList1 })
const handleLegendChange = async (data: any) => {
  const viewer = viewerRef.value
  if (!viewer || !data) return

  const allLayers = [...(data.legendList || []), ...(data.legendList1 || [])]

  for (const layer of allLayers) {
    const cacheKey = layer.name
    if (!layerEntitiesCache.has(cacheKey)) {
      layerEntitiesCache.set(cacheKey, [])
    }
    const cachedEntities = layerEntitiesCache.get(cacheKey)

    if (layer.checked) {
      if (layer.isFlightArea) {
        if (cachedEntities.length === 0) {
          await loadFlightAreas(layer.flightAreaType, layer)
        }
      } else if (layer.elements && layer.elements.length > 0) {
        if (cachedEntities.length < layer.elements.length) {
          const createdIds = new Set(cachedEntities.map((e: any) => e.id))
          const elementsToCreate = layer.elements.filter((ite: any) => !createdIds.has(ite.id))

          const chunkSize = 30
          const addChunk = () => {
            if (elementsToCreate.length === 0 || !layer.checked) return
            const chunk = elementsToCreate.splice(0, chunkSize)
            chunk.forEach((ite: any) => {
              const entity = addLayerElement(ite, layer)
              if (entity) {
                cachedEntities.push(entity)
              }
            })
            if (elementsToCreate.length > 0) {
              requestAnimationFrame(addChunk)
            }
          }
          addChunk()
        }
      }

      // 将已存在的实体设为可见
      cachedEntities.forEach((entity: any) => {
        if (entity && !entity.isDestroyed) {
          entity.show = true
        }
      })
    } else {
      if (cachedEntities.length > 0) {
        cachedEntities.forEach((entity: any) => {
          if (entity && !entity.isDestroyed) {
            entity.show = false
          }
        })
      }
    }
  }
}

// 加载并绘制官方飞行区域 (分批 chunk 加载)
const loadFlightAreas = async (flightAreaType: string, layer: any) => {
  const viewer = viewerRef.value
  if (!viewer) return
  const cacheKey = layer.name
  if (!layerEntitiesCache.has(cacheKey)) {
    layerEntitiesCache.set(cacheKey, [])
  }
  const cachedEntities = layerEntitiesCache.get(cacheKey)

  try {
    const res = await getFlightAreaList()
    if (res.code !== 0 || !res.data) {
      console.warn('获取飞行区域列表失败:', res.message)
      return
    }

    const filteredAreas = res.data.filter((area: any) => area.type === flightAreaType)
    const chunkSize = 30

    const addChunk = () => {
      if (filteredAreas.length === 0 || !layer.checked) return
      const chunk = filteredAreas.splice(0, chunkSize)
      chunk.forEach((area: any) => {
        const content = area.content
        if (!content || !content.geometry) return

        const geometryType = content.geometry.type
        const coordinates = content.geometry.coordinates

        const areaColor = flightAreaType === EFlightAreaType.NFZ
          ? Cesium.Color.RED.withAlpha(0.3)
          : Cesium.Color.GREEN.withAlpha(0.3)
        const outlineColor = flightAreaType === EFlightAreaType.NFZ
          ? Cesium.Color.RED
          : Cesium.Color.GREEN

        let entity = null
        if (geometryType === EGeometryType.CIRCLE) {
          const radius = content.geometry.radius
          const center = coordinates

          entity = viewer.entities.add({
            id: `flight-area-${area.area_id}`,
            name: `[${flightAreaType === EFlightAreaType.NFZ ? '禁飞区' : '可飞区'}] ${area.name}`,
            position: Cesium.Cartesian3.fromDegrees(center[0], center[1]),
            ellipse: {
              semiMajorAxis: radius,
              semiMinorAxis: radius,
              material: areaColor,
              outline: true,
              outlineColor: outlineColor,
              outlineWidth: 2,
              heightReference: Cesium.HeightReference.CLAMP_TO_GROUND
            }
          })
        } else if (geometryType === EGeometryType.POLYGON) {
          const polygonCoords = coordinates[0]
          const positions = polygonCoords.map((coord: number[]) =>
            Cesium.Cartesian3.fromDegrees(coord[0], coord[1])
          )

          entity = viewer.entities.add({
            id: `flight-area-${area.area_id}`,
            name: `[${flightAreaType === EFlightAreaType.NFZ ? '禁飞区' : '可飞区'}] ${area.name}`,
            polygon: {
              hierarchy: new Cesium.PolygonHierarchy(positions),
              material: areaColor,
              outline: true,
              outlineColor: outlineColor,
              outlineWidth: 2,
              heightReference: Cesium.HeightReference.CLAMP_TO_GROUND
            }
          })
        }
        if (entity) {
          cachedEntities.push(entity)
          legendShowData.value.push(entity)
        }
      })
      if (filteredAreas.length > 0) {
        requestAnimationFrame(addChunk)
      }
    }
    addChunk()

    console.log(`已启动分批加载 ${filteredAreas.length} 个${flightAreaType === EFlightAreaType.NFZ ? '禁飞' : '可飞'}区域`)
  } catch (error) {
    console.error('加载飞行区域失败:', error)
    message.error('加载飞行区域失败')
  }
}

// 添加单个图层元素
const addLayerElement = (element: any, layer: any) => {
  const viewer = viewerRef.value
  if (!viewer) return null

  const elementColor = element.resource?.content?.properties?.color || layer.color || '#2D8CF0'

  if (element.type === 'Point') {
    let finalIcon = layer.url
    if (elementColor && (layer.url?.includes('map-icon-marker') || layer.url?.includes('svg'))) {
      finalIcon = getMarkerIcon(elementColor)
    }

    const entity = viewer.entities.add({
      id: element.id,
      position: Cesium.Cartesian3.fromDegrees(element.lonLat[0], element.lonLat[1]),
      billboard: {
        image: finalIcon,
        width: 28,
        height: 34,
        verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
        heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
        disableDepthTestDistance: Number.POSITIVE_INFINITY
      },
      label: {
        text: element.name,
        font: '14px sans-serif',
        fillColor: Cesium.Color.WHITE,
        showBackground: true,
        backgroundColor: Cesium.Color.BLACK.withAlpha(0.6),
        pixelOffset: new Cesium.Cartesian2(0, -40),
        verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
        heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
        disableDepthTestDistance: Number.POSITIVE_INFINITY
      }
    })
    legendShowData.value.push(entity)
    return entity
  } else if (element.type === 'LineString') {
    if (!element.lonLat || element.lonLat.length === 0) return null
    const positions = Cesium.Cartesian3.fromDegreesArray(element.lonLat.flat())
    const entity = viewer.entities.add({
      id: element.id,
      polyline: {
        positions: positions,
        width: 3,
        material: Cesium.Color.fromCssColorString(elementColor),
        depthFailMaterial: Cesium.Color.fromCssColorString(elementColor),
        clampToGround: true
      }
    })
    legendShowData.value.push(entity)
    return entity
  } else if (element.type === 'Polygon') {
    if (!element.lonLat || element.lonLat.length === 0 || element.lonLat[0].length === 0) return null
    const pts = Cesium.Cartesian3.fromDegreesArray(element.lonLat[0].flat())
    
    const sphere = Cesium.BoundingSphere.fromPoints(pts)
    const carto = Cesium.Cartographic.fromCartesian(sphere.center)
    const centerLon = Cesium.Math.toDegrees(carto.longitude)
    const centerLat = Cesium.Math.toDegrees(carto.latitude)

    const entity = viewer.entities.add({
      id: element.id,
      polygon: {
        hierarchy: new Cesium.PolygonHierarchy(pts),
        material: new Cesium.ColorMaterialProperty(Cesium.Color.fromCssColorString(elementColor).withAlpha(0.4)),
        outline: true,
        outlineColor: Cesium.Color.BLACK,
        outlineWidth: 10,
        heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
        perPositionHeight: false
      },
      label: {
        text: element.name,
        font: '14px sans-serif',
        fillColor: Cesium.Color.WHITE,
        showBackground: true,
        backgroundColor: Cesium.Color.BLACK.withAlpha(0.6),
        verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
        pixelOffset: new Cesium.Cartesian2(0, -20),
        heightReference: Cesium.HeightReference.CLAMP_TO_GROUND
      },
      position: Cesium.Cartesian3.fromDegrees(centerLon, centerLat)
    })
    legendShowData.value.push(entity)
    return entity
  }
  return null
}

// 3D 建模图层控制状态与方法
const viewerTilesetPrimitives = new Map<string, any>() // url -> Cesium3DTileset primitive

const handleTilesetChange = ({ checked, name, url }: { checked: boolean; name: string; url: string }) => {
  const viewer = viewerRef.value
  if (!viewer || (viewer as any).isDestroyed?.()) return

  if (checked) {
    if (viewerTilesetPrimitives.has(url)) return
    try {
      // 加载 3D 建模时关闭地形深度检测，防止模型被地形瓦片遮挡
      viewer.scene.globe.depthTestAgainstTerrain = false

      Cesium.Cesium3DTileset.fromUrl(url, {
        maximumScreenSpaceError: 0.5,
      }).then((tileset: any) => {
        if (!viewer || (viewer as any).isDestroyed?.()) return
        if (viewerTilesetPrimitives.has(url)) return
        const addedTileset = viewer.scene.primitives.add(tileset)
        viewerTilesetPrimitives.set(url, markRaw(addedTileset))
        console.log(`3D Tileset 图层 "${name || url}" 加载成功`)
      }).catch((e: any) => {
        console.error(`加载 3D 建模 primitives 失败: ${url}`, e)
        message.error(`图层加载失败: ${name || url}`)
      })
    } catch (e) {
      console.error(`加载 3D 建模外层异常: ${url}`, e)
    }
  } else {
    const tileset = viewerTilesetPrimitives.get(url)
    if (tileset) {
      try {
        if (!(viewer as any).isDestroyed?.()) {
          viewer.scene.primitives.remove(tileset)
        }
      } catch (e) {
        console.error(`卸载 3D 建模 primitives 失败: ${url}`, e)
      }
      viewerTilesetPrimitives.delete(url)
    }

    // 若所有 3D 建模已卸载，恢复地形深度检测
    if (viewerTilesetPrimitives.size === 0 && !(viewer as any).isDestroyed?.()) {
      viewer.scene.globe.depthTestAgainstTerrain = true
    }
  }
}

onMounted(() => {
  nextTick(() => {
    initCesium()
  })
})

onBeforeUnmount(() => {
  if (handler) {
    handler.destroy()
    handler = null
  }
  clearRubberLine()
  clearPolygonEntities()
  clearRouteEntities()

  // 卸载所有加载的 3D 模型与图例实体
  const viewer = viewerRef.value
  if (viewer && !viewer.isDestroyed()) {
    viewerTilesetPrimitives.forEach((tileset) => {
      try {
        viewer.scene.primitives.remove(tileset)
      } catch (e) {}
    })
  }
  viewerTilesetPrimitives.clear()
  layerEntitiesCache.clear()

  if (viewerRef.value && !viewerRef.value.isDestroyed()) {
    viewerRef.value.destroy()
    viewerRef.value = null
  }
})
</script>

<style scoped lang="scss">
.mapping2d-container {
  background-color: #0b0f19;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  position: relative;
  overflow: hidden;
}

/* 主体左右布局工作区 */
.main-layout-body {
  flex: 1;
  display: flex;
  flex-direction: row;
  width: 100%;
  height: calc(100% - 52px);
  overflow: hidden;
  position: relative;
}

.mapping2d-container.is-preview .main-layout-body {
  height: 100%;
}

/* 右侧地图容器区域 */
.map-view-wrapper {
  flex: 1;
  height: 100%;
  position: relative;
  overflow: hidden;
  background: #000;

  .cesium-map-canvas {
    width: 100%;
    height: 100%;
  }
}

/* 纯预览模式下的右上角地图控制工具浮条 */
.preview-map-tools {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(15, 23, 42, 0.85);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  padding: 6px 10px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.45);
}

/* 顶部导航与状态条 (独立固定在页面顶端) */
.top-nav-bar {
  position: relative;
  height: 52px;
  width: 100%;
  background: rgba(15, 23, 42, 0.95);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
  flex-shrink: 0;

  .nav-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .divider-v {
    width: 1px;
    height: 20px;
    background: rgba(255, 255, 255, 0.15);
  }

  .wayline-info-badge {
    display: flex;
    align-items: center;
    gap: 8px;

    .type-tag {
      background: linear-gradient(135deg, #0284c7, #0369a1);
      color: #fff;
      font-size: 11px;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 4px;
      letter-spacing: 0.5px;
    }

    .wayline-title {
      color: #f1f5f9;
      font-size: 14px;
      font-weight: 600;
      max-width: 260px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .nav-center-guide {
    display: flex;
    align-items: center;
    gap: 10px;

    .step-guide-pill {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 4px 12px;
      border-radius: 20px;
      font-size: 12px;
      color: #94a3b8;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid transparent;
      transition: all 0.2s;

      .step-num {
        width: 16px;
        height: 16px;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.1);
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 10px;
        font-weight: bold;
      }

      .step-badge {
        background: #0284c7;
        color: #fff;
        padding: 0 5px;
        border-radius: 10px;
        font-size: 10px;
      }

      &.active {
        color: #38bdf8;
        background: rgba(2, 132, 199, 0.15);
        border-color: rgba(56, 189, 248, 0.4);

        .step-num {
          background: #38bdf8;
          color: #0f172a;
        }
      }
    }

    .guide-arrow {
      color: #475569;
      font-size: 11px;
    }
  }

  .nav-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.action-icon-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid rgba(255, 255, 255, 0.12);
  cursor: pointer;
  transition: all 0.18s ease;

  &.back-btn {
    background: rgba(255, 255, 255, 0.06);
    color: #e2e8f0;

    &:hover {
      background: rgba(255, 255, 255, 0.12);
      border-color: rgba(255, 255, 255, 0.25);
    }
  }

  &.tool-btn {
    background: rgba(30, 41, 59, 0.7);
    color: #94a3b8;

    &:hover {
      background: rgba(51, 65, 85, 0.9);
      color: #38bdf8;
      border-color: rgba(56, 189, 248, 0.3);
    }

    &.terrain-active {
      background: rgba(2, 132, 199, 0.22);
      color: #38bdf8;
      border-color: rgba(56, 189, 248, 0.55);
      box-shadow: 0 0 12px rgba(56, 189, 248, 0.2);
      font-weight: 600;

      svg {
        stroke: #38bdf8;
        filter: drop-shadow(0 0 4px rgba(56, 189, 248, 0.4));
      }
    }
  }
}

/* 顶部状态提示条 (在地图区域顶部中央显示) */
.top-tips-banner {
  position: absolute;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 90;
  pointer-events: none;

  .tips-pill {
    background: rgba(15, 23, 42, 0.88);
    backdrop-filter: blur(12px);
    border: 1px solid rgba(56, 189, 248, 0.3);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
    padding: 6px 16px;
    border-radius: 20px;
    color: #e2e8f0;
    font-size: 12px;
    display: flex;
    align-items: center;
    gap: 8px;

    .tips-icon {
      font-size: 14px;
    }
  }
}

/* 左侧参数配置面板 (独立侧边栏结构，左右并排) */
.left-control-panel {
  position: relative;
  width: 360px;
  height: 100%;
  background: rgba(15, 23, 42, 0.96);
  border-right: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  z-index: 50;
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.45);
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);

  &.collapsed {
    width: 48px;

    .panel-header {
      padding: 12px 6px;
      justify-content: center;

      .panel-title-wrap {
        display: none;
      }
    }
  }

  .panel-header {
    height: 48px;
    padding: 0 16px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-shrink: 0;

    .panel-title-wrap {
      display: flex;
      align-items: center;
      gap: 10px;

      .icon-radar {
        width: 18px;
        height: 18px;
        border-radius: 50%;
        border: 1.5px solid #00f0ff;
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;

        .radar-sweep {
          width: 5px;
          height: 5px;
          background: #00f0ff;
          border-radius: 50%;
          box-shadow: 0 0 8px #00f0ff;
        }
      }

      .panel-title {
        color: #f8fafc;
        font-size: 14px;
        font-weight: 600;
        letter-spacing: 0.3px;
      }
    }

    .collapse-toggle-btn {
      background: transparent;
      border: none;
      color: #94a3b8;
      cursor: pointer;
      padding: 4px;
      border-radius: 4px;

      &:hover {
        color: #fff;
        background: rgba(255, 255, 255, 0.08);
      }
    }
  }

  .panel-body {
    flex: 1;
    overflow-y: auto;
    padding: 14px 16px 20px;
    display: flex;
    flex-direction: column;
    gap: 16px;

    &::-webkit-scrollbar {
      width: 5px;
    }
    &::-webkit-scrollbar-thumb {
      background: rgba(255, 255, 255, 0.15);
      border-radius: 3px;
    }
  }
}

.config-section {
  background: rgba(30, 41, 59, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;

  .section-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 12px;
    font-weight: 600;
    color: #94a3b8;
    letter-spacing: 0.5px;
    text-transform: uppercase;

    .status-indicator {
      font-size: 11px;
      font-weight: normal;
      padding: 2px 8px;
      border-radius: 10px;

      &.valid {
        background: rgba(34, 197, 94, 0.15);
        color: #4ade80;
        border: 1px solid rgba(74, 222, 128, 0.3);
      }

      &.invalid {
        background: rgba(239, 68, 68, 0.15);
        color: #f87171;
        border: 1px solid rgba(248, 113, 113, 0.3);
      }
    }
  }
}

.polygon-stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;

  .stat-card {
    background: rgba(15, 23, 42, 0.6);
    border: 1px solid rgba(255, 255, 255, 0.05);
    border-radius: 8px;
    padding: 8px 10px;
    display: flex;
    flex-direction: column;
    gap: 3px;

    .stat-lbl {
      font-size: 10px;
      color: #64748b;
    }

    .stat-val {
      font-size: 13px;
      font-weight: 600;
      color: #e2e8f0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;

      &.highlight {
        color: #38bdf8;
      }
    }
  }
}

.polygon-action-row {
  display: flex;
  gap: 8px;

  button {
    flex: 1;
    height: 32px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 500;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    cursor: pointer;
    transition: all 0.18s;
  }

  .btn-primary-ghost {
    background: rgba(2, 132, 199, 0.2);
    border: 1px solid rgba(56, 189, 248, 0.4);
    color: #38bdf8;

    &:hover {
      background: rgba(2, 132, 199, 0.35);
      border-color: #38bdf8;
    }
  }

  .btn-success {
    background: #16a34a;
    border: 1px solid #22c55e;
    color: #fff;

    &:hover:not(:disabled) {
      background: #15803d;
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  .btn-danger-ghost {
    background: rgba(239, 68, 68, 0.12);
    border: 1px solid rgba(239, 68, 68, 0.3);
    color: #f87171;
    flex: 0 0 70px;

    &:hover:not(:disabled) {
      background: rgba(239, 68, 68, 0.25);
    }

    &:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
  }
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;

  .item-label {
    font-size: 12px;
    font-weight: 500;
    color: #f1f5f9;
  }

  .form-label-row {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .item-label {
      font-size: 12px;
      font-weight: 500;
      color: #f1f5f9;
    }

    .item-extra-tag {
      font-size: 10px;
      background: rgba(56, 189, 248, 0.15);
      color: #38bdf8;
      padding: 1px 6px;
      border-radius: 4px;
    }

    .item-val-display {
      font-size: 12px;
      font-weight: 600;
      color: #38bdf8;
      font-family: monospace;
    }
  }

  .param-hint {
    font-size: 10px;
    color: #94a3b8;
    line-height: 1.3;
  }
}

.fixed-mode-bar {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  padding: 6px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #94a3b8;

  .dot-live {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #10b981;
    box-shadow: 0 0 6px #10b981;
  }
}

.slider-row {
  display: flex;
  align-items: center;
  gap: 10px;

  .custom-slider {
    margin: 6px 0;
  }

  .num-input {
    width: 60px;
    background: #0f172a;
    border-color: rgba(255, 255, 255, 0.15);
    color: #38bdf8;
  }
}

.seg-btn-group {
  display: flex;
  background: rgba(15, 23, 42, 0.7);
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  padding: 2px;
  gap: 2px;

  .seg-btn {
    flex: 1;
    height: 28px;
    border: none;
    border-radius: 4px;
    background: transparent;
    color: #94a3b8;
    font-size: 11px;
    cursor: pointer;
    transition: all 0.16s;

    &:hover {
      color: #e2e8f0;
    }

    &.active {
      background: #0284c7;
      color: #fff;
      font-weight: 500;
      box-shadow: 0 2px 8px rgba(2, 132, 199, 0.4);
    }
  }
}

.switch-item-row {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 2px 0;

  .switch-meta {
    display: flex;
    flex-direction: column;
    gap: 3px;

    .item-label {
      font-size: 12px;
      font-weight: 500;
      color: #f1f5f9 !important;
    }

    .switch-sub {
      font-size: 11px;
      color: #94a3b8 !important;
      line-height: 1.4;
    }
  }
}

.direction-control-row {
  display: flex;
  align-items: center;
  gap: 8px;

  .auto-btn {
    height: 30px;
    padding: 0 10px;
    border-radius: 6px;
    font-size: 11px;
    border: 1px solid rgba(255, 255, 255, 0.12);
    background: rgba(15, 23, 42, 0.6);
    color: #94a3b8;
    cursor: pointer;
    white-space: nowrap;

    &.active {
      background: rgba(2, 132, 199, 0.2);
      border-color: #38bdf8;
      color: #38bdf8;
      font-weight: 500;
    }
  }
}

/* 底部操作区按钮 */
.panel-action-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;

  button {
    height: 38px;
    border-radius: 8px;
    font-size: 13px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    cursor: pointer;
    transition: all 0.2s;
  }

  .btn-generate-preview {
    background: linear-gradient(135deg, #0284c7, #2563eb);
    border: 1px solid rgba(56, 189, 248, 0.4);
    color: #fff;
    box-shadow: 0 4px 16px rgba(2, 132, 199, 0.35);

    &:hover:not(:disabled) {
      filter: brightness(1.1);
      box-shadow: 0 6px 20px rgba(2, 132, 199, 0.5);
    }

    &:disabled {
      opacity: 0.45;
      cursor: not-allowed;
      filter: grayscale(0.5);
      box-shadow: none;
    }
  }

  .btn-save-wayline {
    background: linear-gradient(135deg, #10b981, #059669);
    border: 1px solid rgba(52, 211, 153, 0.4);
    color: #fff;
    box-shadow: 0 4px 16px rgba(16, 185, 129, 0.35);

    &:hover:not(:disabled) {
      filter: brightness(1.1);
      box-shadow: 0 6px 20px rgba(16, 185, 129, 0.5);
    }

    &:disabled {
      opacity: 0.4;
      cursor: not-allowed;
      box-shadow: none;
    }
  }
}

/* 右下角航线计算结果卡片 */
.route-stats-card {
  position: absolute;
  bottom: 24px;
  right: 20px;
  width: 280px;
  background: rgba(15, 23, 42, 0.9);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(56, 189, 248, 0.3);
  border-radius: 12px;
  z-index: 95;
  padding: 12px 14px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.4);

  .stats-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
    padding-bottom: 6px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);

    .stats-title {
      color: #f1f5f9;
      font-size: 12px;
      font-weight: 600;
    }

    .stats-status-tag {
      background: rgba(34, 197, 94, 0.15);
      color: #4ade80;
      font-size: 10px;
      padding: 1px 6px;
      border-radius: 4px;
    }
  }

  .stats-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;

    .stat-box {
      background: rgba(30, 41, 59, 0.5);
      border-radius: 6px;
      padding: 6px 8px;
      display: flex;
      flex-direction: column;
      gap: 2px;

      .k {
        font-size: 10px;
        color: #94a3b8;
      }

      .v {
        font-size: 12px;
        font-weight: 600;
        color: #e2e8f0;

        &.highlight {
          color: #38bdf8;
        }

        &.text-amber {
          color: #ffb703;
        }
      }
    }
  }
}

/* 航点详情弹窗卡片 (点击地图航点时展示) */
.waypoint-detail-card {
  position: absolute;
  top: 80px;
  right: 20px;
  width: 290px;
  background: rgba(15, 23, 42, 0.94);
  backdrop-filter: blur(18px);
  border: 1px solid rgba(56, 189, 248, 0.4);
  border-radius: 12px;
  z-index: 100;
  padding: 14px;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.55);

  .wp-card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
    padding-bottom: 8px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);

    .wp-badge-wrap {
      display: flex;
      align-items: center;
      gap: 8px;

      .wp-badge {
        font-size: 11px;
        font-weight: 700;
        padding: 2px 7px;
        border-radius: 4px;

        &.type-start {
          background: rgba(0, 230, 118, 0.2);
          color: #00e676;
          border: 1px solid rgba(0, 230, 118, 0.4);
        }

        &.type-end {
          background: rgba(255, 61, 113, 0.2);
          color: #ff3d71;
          border: 1px solid rgba(255, 61, 113, 0.4);
        }

        &.type-elevation {
          background: rgba(255, 183, 3, 0.2);
          color: #ffb703;
          border: 1px solid rgba(255, 183, 3, 0.4);
        }

        &.type-normal {
          background: rgba(56, 189, 248, 0.2);
          color: #38bdf8;
          border: 1px solid rgba(56, 189, 248, 0.4);
        }
      }

      .wp-title {
        color: #f1f5f9;
        font-size: 13px;
        font-weight: 600;
      }
    }

    .close-wp-btn {
      background: transparent;
      border: none;
      color: #94a3b8;
      cursor: pointer;
      font-size: 14px;
      padding: 2px 6px;
      border-radius: 4px;

      &:hover {
        color: #fff;
        background: rgba(255, 255, 255, 0.1);
      }
    }
  }

  .wp-card-body {
    display: flex;
    flex-direction: column;
    gap: 7px;
    margin-bottom: 12px;

    .wp-card-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 11px;

      .k {
        color: #94a3b8;
      }

      .v {
        color: #e2e8f0;

        &.highlight {
          color: #38bdf8;
          font-weight: 600;
        }
      }
    }
  }

  .wp-card-footer {
    .btn-focus-wp {
      width: 100%;
      padding: 6px 12px;
      border-radius: 6px;
      background: rgba(56, 189, 248, 0.15);
      border: 1px solid rgba(56, 189, 248, 0.35);
      color: #38bdf8;
      font-size: 12px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      transition: all 0.2s;

      &:hover {
        background: rgba(56, 189, 248, 0.28);
        color: #fff;
      }
    }
  }
}

/* 底部起飞参考点横条 */
.bottom-ref-bar {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 90;
  pointer-events: none;

  .ref-pill {
    background: rgba(15, 23, 42, 0.82);
    backdrop-filter: blur(12px);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 20px;
    padding: 4px 14px;
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    color: #94a3b8;

    .ref-title {
      color: #cbd5e1;
    }

    .ref-coords {
      color: #38bdf8;
      font-family: monospace;
    }
  }
}

/* 绘制时的快捷操作工具条 */
.draw-floating-toolbar {
  position: absolute;
  bottom: 30px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 105;
  transition: bottom 0.3s ease;

  /* 当有起飞点信息显示时，上移避免重叠 */
  &.with-takeoff-info {
    bottom: 80px;
  }

  .toolbar-content {
    background: rgba(15, 23, 42, 0.95);
    backdrop-filter: blur(16px);
    border: 1.5px solid #00f0ff;
    box-shadow: 0 8px 32px rgba(0, 240, 255, 0.25);
    border-radius: 30px;
    padding: 6px 12px 6px 18px;
    display: flex;
    align-items: center;
    gap: 16px;

    .draw-status-text {
      color: #e2e8f0;
      font-size: 12px;

      strong {
        color: #00f0ff;
        font-size: 14px;
      }
    }

    .toolbar-btns {
      display: flex;
      gap: 8px;

      .t-btn {
        height: 28px;
        padding: 0 12px;
        border-radius: 14px;
        font-size: 11px;
        font-weight: 600;
        cursor: pointer;
        border: none;
        transition: all 0.16s;

        &.complete {
          background: #10b981;
          color: #fff;

          &:hover:not(:disabled) {
            background: #059669;
          }

          &:disabled {
            opacity: 0.4;
            cursor: not-allowed;
          }
        }

        &.cancel {
          background: rgba(255, 255, 255, 0.1);
          color: #cbd5e1;

          &:hover {
            background: rgba(255, 255, 255, 0.2);
            color: #fff;
          }
        }
      }
    }
  }
}

.spinner-inline {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
