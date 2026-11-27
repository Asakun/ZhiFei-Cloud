<template>
  <div class="cockpit-waypoint-recorder-root">
    <!-- 顶部/悬浮 HUD 打点胶囊控制条 -->
    <div class="recorder-hud-pill" :class="{ 'has-points': totalCount > 0 }">
      <!-- 快捷打点主按钮 -->
      <button
        class="hud-btn-primary"
        @click="handleQuickRecord"
        title="记录当前无人机位置为航点 (快捷键: K)"
      >
        <span class="btn-icon">📍</span>
        <span class="btn-text">记录航点</span>
        <span class="btn-kbd font-mono">K</span>
      </button>

      <!-- 计数与航程胶囊 -->
      <div
        class="hud-stat-pill cursor-pointer"
        @click="drawerVisible = !drawerVisible"
        title="点击查看航点列表与详情"
      >
        <span class="stat-badge">{{ totalCount }}</span>
        <span class="stat-label">个航点</span>
        <span class="stat-divider" v-if="totalCount > 1"></span>
        <span class="stat-dist font-mono" v-if="totalCount > 1">
          {{ (totalDistance / 1000).toFixed(2) }} km
        </span>
      </div>

      <!-- 查看/管理航点抽屉切换按钮 -->
      <button
        class="hud-btn-ghost"
        @click="drawerVisible = !drawerVisible"
        :class="{ 'active': drawerVisible }"
        title="打开航点管理面板"
      >
        <span class="btn-icon">📋</span>
        <span class="btn-text">航点列表</span>
      </button>

      <!-- 快速生成航线按钮 (当有航点时高亮显示) -->
      <button
        v-if="totalCount > 0"
        class="hud-btn-generate"
        @click="openGenerateModal"
        title="根据当前记录的航点快速生成巡检航线"
      >
        <span class="btn-icon">🏁</span>
        <span class="btn-text">生成航线</span>
      </button>
    </div>

    <!-- 打点管理悬浮抽屉 (右侧弹出) -->
    <a-drawer
      v-model:visible="drawerVisible"
      title="📍 手动飞行航点管理"
      placement="right"
      :width="440"
      :closable="true"
      :mask="false"
      class="cockpit-waypoint-drawer"
    >
      <template #extra>
        <a-space size="small">
          <a-popconfirm
            title="确定要清空已记录的全部航点吗？"
            ok-text="清空"
            cancel-text="取消"
            @confirm="handleClearAll"
            v-if="totalCount > 0"
          >
            <a-button size="small" danger ghost>清空</a-button>
          </a-popconfirm>
          <a-button
            type="primary"
            size="small"
            :disabled="totalCount === 0"
            @click="openGenerateModal"
          >
            生成航线 ({{ totalCount }})
          </a-button>
        </a-space>
      </template>

      <!-- 航线概览统计卡片 -->
      <div class="route-summary-card mb-4" v-if="totalCount > 0">
        <div class="summary-grid">
          <div class="summary-item">
            <span class="summary-num">{{ totalCount }}</span>
            <span class="summary-lbl">航点总数</span>
          </div>
          <div class="summary-item">
            <span class="summary-num font-mono">{{ (totalDistance / 1000).toFixed(2) }} <span class="unit">km</span></span>
            <span class="summary-lbl">航线总长</span>
          </div>
          <div class="summary-item">
            <span class="summary-num font-mono">{{ formatDuration(estimatedDuration) }}</span>
            <span class="summary-lbl">预估耗时</span>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="totalCount === 0" class="empty-state py-12 flex flex-col items-center justify-center text-gray-400">
        <div class="text-4xl mb-3">📍</div>
        <p class="text-sm font-medium text-gray-300">暂未记录航点</p>
        <p class="text-xs text-gray-500 mt-1 max-w-[280px] text-center">
          在手动控制飞行时，按下键盘 <kbd class="px-1.5 py-0.5 bg-gray-800 border border-gray-700 rounded text-blue-400 font-mono text-xs">K</kbd> 键或点击顶部【记录航点】按钮即可保存当前点位。
        </p>
      </div>

      <!-- 航点列表 -->
      <div v-else class="waypoint-list space-y-3 pb-8">
        <div
          v-for="(wp, index) in waypoints"
          :key="wp.id"
          class="waypoint-card bg-gray-800/80 border border-gray-700/80 rounded-lg p-3 hover:border-blue-500/80 transition-all group"
        >
          <!-- 头部信息 -->
          <div class="flex items-center justify-between pb-2 border-b border-gray-700/60 mb-2">
            <div class="flex items-center gap-2">
              <span class="wp-badge">{{ index + 1 }}</span>
              <input
                v-model="wp.name"
                class="wp-name-input bg-transparent text-sm font-semibold text-white border-b border-transparent hover:border-gray-500 focus:border-blue-500 focus:outline-none px-1"
                placeholder="航点名称"
              />
            </div>
            <div class="flex items-center gap-1">
              <button
                class="card-act-btn"
                @click="flyToWaypoint(wp)"
                title="在3D地图中定位至该航点"
              >
                👁️ 定位
              </button>
              <button
                class="card-act-btn"
                :disabled="index === 0"
                @click="moveWaypoint(index, index - 1)"
                title="上移"
              >
                ↑
              </button>
              <button
                class="card-act-btn"
                :disabled="index === waypoints.length - 1"
                @click="moveWaypoint(index, index + 1)"
                title="下移"
              >
                ↓
              </button>
              <button
                class="card-act-btn text-red-400 hover:text-red-300"
                @click="removeWaypoint(index)"
                title="删除该航点"
              >
                ✕
              </button>
            </div>
          </div>

          <!-- 内容区域：左右布局 (缩略图 + 详细参数) -->
          <div class="flex gap-3">
            <!-- 抓拍缩略图 -->
            <div class="snapshot-wrapper w-28 h-18 bg-black/40 rounded border border-gray-700 overflow-hidden shrink-0 flex items-center justify-center relative cursor-pointer group/img" @click="previewImage(wp.snapshot)">
              <img
                v-if="wp.snapshot"
                :src="wp.snapshot"
                class="w-full h-full object-cover"
                alt="点位抓拍"
              />
              <div v-else class="text-xs text-gray-500 flex flex-col items-center">
                <span>📷</span>
                <span class="scale-90">无画面</span>
              </div>
              <div v-if="wp.snapshot" class="absolute inset-0 bg-black/40 opacity-0 group-hover/img:opacity-100 flex items-center justify-center transition-opacity text-xs text-white">
                🔍 放大
              </div>
            </div>

            <!-- 参数详情网格 -->
            <div class="flex-1 text-xs space-y-1 font-mono">
              <div class="flex justify-between text-gray-300">
                <span class="text-gray-400">经纬度:</span>
                <span>{{ wp.longitude.toFixed(5) }}, {{ wp.latitude.toFixed(5) }}</span>
              </div>
              <div class="flex justify-between text-gray-300">
                <span class="text-gray-400">相对起飞高:</span>
                <span class="text-green-400 font-bold">{{ wp.height }} m</span>
              </div>
              <div class="flex justify-between text-gray-300">
                <span class="text-gray-400">机头 / 云台:</span>
                <span>{{ wp.yaw }}° / {{ wp.gimbalPitch }}°</span>
              </div>
              <div class="flex justify-between text-gray-300" v-if="wp.zoomFactor">
                <span class="text-gray-400">变焦倍数:</span>
                <span class="text-blue-400">{{ wp.zoomFactor }}x</span>
              </div>
            </div>
          </div>

          <!-- 到达航点动作快速切换 -->
          <div class="mt-2 pt-2 border-t border-gray-700/40 flex items-center justify-between text-xs">
            <span class="text-gray-400">到达动作:</span>
            <div class="flex items-center gap-1.5">
              <button
                class="action-toggle-btn"
                :class="{ 'active': hasAction(wp, 'takePhoto') }"
                @click="toggleAction(wp, 'takePhoto')"
              >
                📷 拍照
              </button>
              <button
                class="action-toggle-btn"
                :class="{ 'active': hasAction(wp, 'hover') }"
                @click="toggleAction(wp, 'hover')"
              >
                ⏱️ 悬停
              </button>
              <button
                class="action-toggle-btn"
                :class="{ 'active': hasAction(wp, 'startRecord') }"
                @click="toggleAction(wp, 'startRecord')"
              >
                🎬 录像
              </button>
            </div>
          </div>
        </div>
      </div>
    </a-drawer>

    <!-- 生成航线参数配置与确认弹窗 -->
    <a-modal
      v-model:visible="generateModalVisible"
      title="🏁 快速生成巡检航线"
      :width="720"
      :footer="null"
      class="cockpit-generate-route-modal"
      :centered="true"
    >
      <div class="modal-body space-y-4 py-2 text-gray-200">
        <!-- 基础信息卡片 -->
        <div class="bg-gray-800/90 rounded-lg p-4 border border-gray-700 space-y-3">
          <div class="section-title text-sm font-bold text-blue-400 flex items-center gap-2">
            <span>📝</span> 航线基本信息
          </div>

          <!-- 航线名称 -->
          <div class="form-row">
            <label class="form-label text-xs text-gray-400 w-24 flex-shrink-0">航线名称:</label>
            <a-input
              v-model:value="routeForm.name"
              placeholder="请输入航线名称"
              class="flex-1 bg-gray-900 border-gray-700 text-white"
            />
          </div>

          <!-- 起飞机场/点位 -->
          <div class="form-row">
            <label class="form-label text-xs text-gray-400 w-24 flex-shrink-0">所属机场:</label>
            <div class="flex-1 text-xs text-gray-300 font-mono py-1.5 px-3 bg-gray-900 rounded border border-gray-700">
              {{ dockName || dockSn || '当前设备起飞点' }}
            </div>
          </div>

          <!-- 航点概览 -->
          <div class="form-row">
            <label class="form-label text-xs text-gray-400 w-24 flex-shrink-0">航线概况:</label>
            <div class="flex-1 text-xs text-gray-300 py-1 flex items-center gap-6 whitespace-nowrap">
              <span class="inline-flex items-center gap-1">航点数: <strong class="text-blue-400 font-mono text-sm">{{ totalCount }}</strong> 个</span>
              <span class="inline-flex items-center gap-1">总航程: <strong class="text-green-400 font-mono text-sm">{{ (totalDistance / 1000).toFixed(2) }}</strong> km</span>
              <span class="inline-flex items-center gap-1">预估耗时: <strong class="text-yellow-400 font-mono text-sm">{{ formatDuration(estimatedDuration) }}</strong></span>
            </div>
          </div>
        </div>

        <!-- 航线飞行全局参数 -->
        <div class="bg-gray-800/90 rounded-lg p-4 border border-gray-700 space-y-3">
          <div class="section-title text-sm font-bold text-blue-400 flex items-center gap-2">
            <span>⚙️</span> 全局飞行参数
          </div>

          <!-- 巡航速度 -->
          <div class="form-row items-center">
            <label class="form-label text-xs text-gray-400 w-24 flex-shrink-0">巡航速度 (m/s):</label>
            <div class="flex-1 flex items-center gap-3">
              <a-slider
                v-model:value="routeForm.globalSpeed"
                :min="1"
                :max="15"
                class="flex-1"
              />
              <span class="w-12 text-right font-mono text-sm font-bold text-blue-400">{{ routeForm.globalSpeed }}m/s</span>
            </div>
          </div>

          <!-- 安全返航高度 -->
          <div class="form-row items-center">
            <label class="form-label text-xs text-gray-400 w-24 flex-shrink-0">返航高度 (m):</label>
            <div class="flex-1 flex items-center gap-3">
              <a-slider
                v-model:value="routeForm.rthAltitude"
                :min="30"
                :max="300"
                :step="5"
                class="flex-1"
              />
              <span class="w-12 text-right font-mono text-sm font-bold text-green-400">{{ routeForm.rthAltitude }}m</span>
            </div>
          </div>

          <!-- 结束动作 -->
          <div class="form-row items-center">
            <label class="form-label text-xs text-gray-400 w-24 flex-shrink-0">航线结束动作:</label>
            <a-radio-group v-model:value="routeForm.finishAction" size="small" class="flex-1 flex flex-wrap gap-x-3 gap-y-1">
              <a-radio value="goHome">自动返航 (GoHome)</a-radio>
              <a-radio value="hover">原地悬停</a-radio>
              <a-radio value="land">原地降落</a-radio>
              <a-radio value="returnToStart">返回起点</a-radio>
            </a-radio-group>
          </div>

          <!-- 航向模式 -->
          <div class="form-row items-center">
            <label class="form-label text-xs text-gray-400 w-24 flex-shrink-0">机头航向模式:</label>
            <a-radio-group v-model:value="routeForm.yawMode" size="small" class="flex-1 flex flex-wrap gap-x-3 gap-y-1">
              <a-radio value="followWayline">沿航线方向</a-radio>
              <a-radio value="manual">手动控制</a-radio>
              <a-radio value="lockHeading">锁定起始朝向</a-radio>
            </a-radio-group>
          </div>
        </div>

        <!-- 底部生成操作按钮栏 -->
        <div class="modal-footer-bar flex items-center justify-between pt-4 mt-2 border-t border-gray-700/80">
          <button
            class="modal-btn modal-btn-secondary"
            @click="handleExportWPML"
            title="导出为本地标准 WPML JSON 文件"
          >
            <span>💾</span>
            <span>导出 WPML</span>
          </button>

          <div class="flex items-center gap-3">
            <button
              class="modal-btn modal-btn-cancel"
              @click="generateModalVisible = false"
            >
              取消
            </button>

            <!-- 跳转 3D 航线编辑器精修 -->
            <button
              class="modal-btn modal-btn-editor"
              @click="handleOpen3DEditor"
              title="在 3D 地图中直观拖拽调整航点与云台姿态"
            >
              <span>🛠️</span>
              <span>进入 3D 编辑器精修</span>
            </button>

            <!-- 一键立即保存至航线库 -->
            <button
              class="modal-btn modal-btn-primary"
              :disabled="saving"
              @click="handleDirectSave"
              title="直接将当前航线保存至工作空间航线库"
            >
              <span v-if="saving" class="animate-spin">⏳</span>
              <span v-else>🚀</span>
              <span>立即保存到航线库</span>
            </button>
          </div>
        </div>
      </div>
    </a-modal>

    <!-- 缩略图大图弹窗 -->
    <a-modal
      v-model:visible="previewVisible"
      :footer="null"
      :width="640"
      :centered="true"
      title="📷 航点抓拍视图"
    >
      <img :src="previewImageUrl" class="w-full h-auto rounded" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, onUnmounted } from 'vue';
import { message } from 'ant-design-vue';
import {
  useCockpitWaypointRecorder,
  CockpitRecordedWaypoint,
  RouteGlobalConfig
} from '@/hooks/useCockpitWaypointRecorder';
import { DRONE_TYPE, PAYLOAD_TYPE } from '@/types/device';

const props = defineProps<{
  osdInfo: any;
  dockMapLocation?: any;
  dockSn?: string;
  dockName?: string;
  droneType?: number;
  payloadType?: number;
  cameraPayloadIndex?: string;
  videoElement?: HTMLVideoElement | null;
}>();

const emit = defineEmits<{
  (e: 'update:waylinePoints', points: any[]): void;
  (e: 'fly-to-waypoint', point: CockpitRecordedWaypoint): void;
}>();

const {
  waypoints,
  totalCount,
  totalDistance,
  estimatedDuration,
  cesiumWaylinePoints,
  recordWaypoint,
  removeWaypoint,
  clearWaypoints,
  moveWaypoint,
  saveWaylineDirectly,
  openIn3DWaylineEditor,
  exportWPMLFile
} = useCockpitWaypointRecorder();

// 抽屉和弹窗显隐状态
const drawerVisible = ref(false);
const generateModalVisible = ref(false);
const saving = ref(false);

// 缩略图大图预览
const previewVisible = ref(false);
const previewImageUrl = ref('');
const previewImage = (url?: string) => {
  if (!url) return;
  previewImageUrl.value = url;
  previewVisible.value = true;
};

// 航线生成表单配置
const routeForm = reactive<RouteGlobalConfig>({
  name: '',
  dockSn: '',
  dockName: '',
  globalSpeed: 10,
  rthAltitude: 100,
  finishAction: 'goHome',
  exitOnRCLost: 'goBack',
  yawMode: 'followWayline',
  gimbalPitchMode: 'usePointSetting',
  photoType: ['visable'],
  templateType: 'waypoint'
});

// 监听航点变化同步给父组件（用于 Cesium 3D 地图渲染）
watch(
  () => cesiumWaylinePoints.value,
  (newPoints) => {
    emit('update:waylinePoints', newPoints);
  },
  { deep: true, immediate: true }
);

/**
 * 触发快速打点
 */
const handleQuickRecord = () => {
  const currentOsd = props.osdInfo;
  if (!currentOsd) {
    message.warning('尚未接收到无人机遥测 OSD 数据');
    return;
  }

  // 获取云台俯仰与变焦倍数
  const zoom = currentOsd.zoom_factor || currentOsd.zoomFactor || 1;
  const gimbalP = currentOsd.gimbal_pitch || currentOsd.gimbalPitch || -45;

  const recorded = recordWaypoint({
    osdInfo: currentOsd,
    dockLocation: props.dockMapLocation,
    gimbalPitch: gimbalP,
    zoomFactor: zoom,
    videoElement: props.videoElement,
    defaultAction: 'none'
  });

  if (recorded) {
    // 触发简短的震动或动效（如果有）
  }
};

/**
 * 键盘快捷键监听器 (按 K 键快速打点)
 */
const handleKeyDown = (e: KeyboardEvent) => {
  const target = e.target as HTMLElement;
  if (
    target &&
    (target.tagName === 'INPUT' ||
      target.tagName === 'TEXTAREA' ||
      target.isContentEditable)
  ) {
    return;
  }

  if (e.key === 'k' || e.key === 'K') {
    e.preventDefault();
    handleQuickRecord();
  }
};

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown);
});

/**
 * 飞至指定航点
 */
const flyToWaypoint = (wp: CockpitRecordedWaypoint) => {
  emit('fly-to-waypoint', wp);
};

/**
 * 动作标签切换
 */
const hasAction = (wp: CockpitRecordedWaypoint, actionFunc: string) => {
  return wp.actions.some((a) => a.action_func === actionFunc);
};

const toggleAction = (wp: CockpitRecordedWaypoint, actionFunc: any) => {
  const existingIdx = wp.actions.findIndex((a) => a.action_func === actionFunc);
  if (existingIdx >= 0) {
    wp.actions.splice(existingIdx, 1);
  } else {
    wp.actions.push({
      action_func: actionFunc,
      hover_time: actionFunc === 'hover' ? 5 : undefined,
      use_global_image_format: actionFunc === 'takePhoto' ? 1 : undefined,
      action_index: wp.actions.length
    });
  }
};

/**
 * 打开生成航线弹窗
 */
const openGenerateModal = () => {
  if (totalCount.value === 0) {
    message.warning('请先记录航点再生成航线');
    return;
  }

  const now = new Date();
  const dateStr = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}_${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}`;
  const defaultName = `手动打点_${props.dockName || '航线'}_${dateStr}`;

  routeForm.name = defaultName;
  routeForm.dockSn = props.dockSn || '';
  routeForm.dockName = props.dockName || '';
  routeForm.takeoffPoint = props.dockMapLocation || {
    longitude: waypoints.value[0]?.longitude || 0,
    latitude: waypoints.value[0]?.latitude || 0,
    height: 0
  };
  routeForm.droneType = props.droneType ?? DRONE_TYPE.M30;
  routeForm.payloadType = props.payloadType ?? PAYLOAD_TYPE.M30D;

  generateModalVisible.value = true;
};

/**
 * 一键保存航线至库
 */
const handleDirectSave = async () => {
  if (!routeForm.name || routeForm.name.trim() === '') {
    message.warning('请输入航线名称');
    return;
  }
  saving.value = true;
  try {
    const success = await saveWaylineDirectly(routeForm);
    if (success) {
      generateModalVisible.value = false;
      drawerVisible.value = false;
    }
  } finally {
    saving.value = false;
  }
};

/**
 * 跳转至 3D 航线编辑器 (新窗口打开)
 */
const handleOpen3DEditor = () => {
  openIn3DWaylineEditor(routeForm);
  generateModalVisible.value = false;
};

/**
 * 导出 WPML 文件
 */
const handleExportWPML = () => {
  exportWPMLFile(routeForm);
};

/**
 * 清空全部
 */
const handleClearAll = () => {
  clearWaypoints();
};

/**
 * 格式化耗时
 */
const formatDuration = (seconds: number): string => {
  if (seconds < 60) return `${seconds}秒`;
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}分${s > 0 ? s + '秒' : ''}`;
};

// 暴露方法给外部父组件
defineExpose({
  recordCurrentPoint: handleQuickRecord,
  openDrawer: () => { drawerVisible.value = true; },
  openGenerateModal
});
</script>

<style scoped>
.cockpit-waypoint-recorder-root {
  display: contents;
}

/* ====== 顶部悬浮 HUD 胶囊控制条 ====== */
.recorder-hud-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(17, 24, 39, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(75, 85, 99, 0.5);
  padding: 3px 6px;
  border-radius: 9999px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.1);
  transition: all 0.25s ease;
}

.recorder-hud-pill.has-points {
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4), 0 0 12px rgba(59, 130, 246, 0.25);
}

.hud-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 9999px;
  border: 1px solid rgba(96, 165, 250, 0.4);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.35);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.hud-btn-primary:hover {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.5);
}

.hud-btn-primary:active {
  transform: scale(0.96);
}

.btn-kbd {
  font-size: 10px;
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 4px;
  padding: 1px 4px;
  line-height: 1;
}

.hud-stat-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 9999px;
  background: rgba(31, 41, 55, 0.7);
  font-size: 11px;
  color: #e5e7eb;
  border: 1px solid rgba(75, 85, 99, 0.3);
  transition: all 0.2s;
}

.hud-stat-pill:hover {
  background: rgba(55, 65, 81, 0.8);
  border-color: rgba(96, 165, 250, 0.4);
}

.stat-badge {
  background: #3b82f6;
  color: #fff;
  font-weight: bold;
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 9999px;
}

.stat-divider {
  width: 1px;
  height: 10px;
  background: rgba(156, 163, 175, 0.4);
}

.stat-dist {
  color: #10b981;
  font-weight: 600;
}

.hud-btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: transparent;
  color: #9ca3af;
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 9999px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.hud-btn-ghost:hover, .hud-btn-ghost.active {
  color: #fff;
  background: rgba(55, 65, 81, 0.6);
  border-color: rgba(107, 114, 128, 0.4);
}

.hud-btn-generate {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: linear-gradient(135deg, #059669, #047857);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 9999px;
  border: 1px solid rgba(52, 211, 153, 0.4);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(5, 150, 105, 0.3);
  animation: pulse-border 2s infinite;
  transition: all 0.2s;
}

.hud-btn-generate:hover {
  background: linear-gradient(135deg, #10b981, #059669);
  transform: translateY(-1px);
}

@keyframes pulse-border {
  0%, 100% { box-shadow: 0 2px 8px rgba(5, 150, 105, 0.3); }
  50% { box-shadow: 0 2px 14px rgba(16, 185, 129, 0.6); }
}

/* ====== 抽屉样式 ====== */
:deep(.ant-drawer-content) {
  background: #111827 !important;
  color: #f3f4f6 !important;
}

:deep(.ant-drawer-header) {
  background: #1f2937 !important;
  border-bottom: 1px solid #374151 !important;
}

:deep(.ant-drawer-title) {
  color: #f9fafb !important;
  font-weight: 600 !important;
}

:deep(.ant-drawer-close) {
  color: #9ca3af !important;
}

:deep(.ant-drawer-close:hover) {
  color: #fff !important;
}

.route-summary-card {
  background: linear-gradient(135deg, rgba(30, 41, 59, 0.9), rgba(15, 23, 42, 0.9));
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 8px;
  padding: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  text-align: center;
  gap: 8px;
}

.summary-num {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: #60a5fa;
}

.summary-lbl {
  font-size: 10px;
  color: #9ca3af;
}

.wp-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: #2563eb;
  color: #fff;
  border-radius: 9999px;
  font-size: 11px;
  font-weight: bold;
}

.card-act-btn {
  background: rgba(55, 65, 81, 0.6);
  border: 1px solid rgba(75, 85, 99, 0.4);
  color: #d1d5db;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
}

.card-act-btn:hover:not(:disabled) {
  background: rgba(75, 85, 99, 0.9);
  color: #fff;
}

.card-act-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.action-toggle-btn {
  background: rgba(31, 41, 55, 0.8);
  border: 1px solid rgba(75, 85, 99, 0.5);
  color: #9ca3af;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
}

.action-toggle-btn.active {
  background: rgba(59, 130, 246, 0.2);
  border-color: #3b82f6;
  color: #60a5fa;
  font-weight: 600;
}

/* ====== 弹窗表单与底部按钮样式 ====== */
.form-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.modal-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.modal-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid transparent;
  line-height: 1.3;
}

.modal-btn-secondary {
  background: rgba(55, 65, 81, 0.85);
  border-color: rgba(75, 85, 99, 0.6);
  color: #d1d5db;
}

.modal-btn-secondary:hover {
  background: rgba(75, 85, 99, 0.95);
  color: #ffffff;
}

.modal-btn-cancel {
  background: transparent;
  border-color: rgba(75, 85, 99, 0.5);
  color: #9ca3af;
  padding: 8px 14px;
}

.modal-btn-cancel:hover {
  background: rgba(55, 65, 81, 0.5);
  color: #e5e7eb;
}

.modal-btn-editor {
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #ffffff;
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.3);
}

.modal-btn-editor:hover {
  background: linear-gradient(135deg, #818cf8, #6366f1);
  box-shadow: 0 4px 14px rgba(79, 70, 229, 0.45);
  transform: translateY(-1px);
}

.modal-btn-primary {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 2px 10px rgba(37, 99, 235, 0.4);
}

.modal-btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.55);
  transform: translateY(-1px);
}

.modal-btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

:deep(.cockpit-generate-route-modal .ant-modal-content) {
  background: #111827 !important;
  border: 1px solid #374151 !important;
  border-radius: 12px !important;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.6) !important;
}

:deep(.cockpit-generate-route-modal .ant-modal-header) {
  background: #1f2937 !important;
  border-bottom: 1px solid #374151 !important;
  border-radius: 12px 12px 0 0 !important;
}

:deep(.cockpit-generate-route-modal .ant-modal-title) {
  color: #f9fafb !important;
}

:deep(.ant-radio-wrapper) {
  color: #d1d5db !important;
  font-size: 12px !important;
}
</style>
