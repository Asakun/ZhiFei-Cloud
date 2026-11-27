<template>
  <div class="flighthub-map-card">
    <div class="map-view-wrapper">
      <div ref="mapContainer" class="map-instance"></div>
      
      <!-- 右上角地图类型切换按钮 (天地图卫星 / 天地图矢量) -->
      <div class="map-layer-control">
        <button
          class="layer-toggle-btn"
          :class="{ active: isSatellite }"
          @click="toggleMapType"
          title="切换天地图图层"
        >
          {{ isSatellite ? '天地图卫星' : '天地图矢量' }}
        </button>
      </div>

      <!-- 地图加载遮罩 -->
      <div v-if="loading" class="map-loading-overlay">
        <a-spin size="small" />
        <span>天地图加载中...</span>
      </div>

      <!-- 司空2 风格底部坐标与姿态浮层 -->
      <div v-if="formattedCoords" class="coords-overlay">
        <div class="coords-left">
          <span class="coords-text">{{ formattedCoords }}</span>
          <span v-if="altitude !== undefined && altitude !== null" class="altitude-text">
            H: {{ typeof altitude === 'number' ? altitude.toFixed(1) : altitude }}m
          </span>
        </div>
        <div class="posture-right">
          <span v-if="heading !== undefined && heading !== null" class="posture-item">
            Yaw: {{ Math.round(heading) }}°
          </span>
          <span v-if="pitch !== undefined && pitch !== null" class="posture-item">
            Pitch: {{ pitch.toFixed(1) }}°
          </span>
          <span v-if="roll !== undefined && roll !== null" class="posture-item">
            Roll: {{ roll.toFixed(1) }}°
          </span>
        </div>
        <button class="recenter-btn" @click="recenterMap" title="重置视角">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="8" />
            <line x1="12" y1="2" x2="12" y2="6" />
            <line x1="12" y1="18" x2="12" y2="22" />
            <line x1="2" y1="12" x2="6" y2="12" />
            <line x1="18" y1="12" x2="22" y2="12" />
            <circle cx="12" cy="12" r="2" fill="currentColor" />
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed, nextTick } from 'vue';
import { CURRENT_CONFIG } from '@/api/http/config';

// 天地图 Token (与全局配置统一)
const TDT_TOKEN = CURRENT_CONFIG.tiandituToken;

const props = defineProps<{
  latitude: number | string;
  longitude: number | string;
  altitude?: number | string;
  heading?: number; // 航向角 (0~360度)
  pitch?: number;   // 俯仰角
  roll?: number;    // 横滚角
  flightPath?: Array<[number, number]>; // 视频全量飞行轨迹线 [[lat, lng], ...]
}>();

const mapContainer = ref<HTMLDivElement | null>(null);
const loading = ref(true);
const isSatellite = ref(true);

let mapInstance: any = null;
let markerInstance: any = null;
let polylineInstance: any = null;
let satelliteGroup: any = null;
let vectorGroup: any = null;
let LeafletL: any = null;

// 经纬度数值计算 (WGS84 原始坐标)
const latNum = computed(() => {
  const val = typeof props.latitude === 'string' ? parseFloat(props.latitude) : props.latitude;
  return isNaN(val) ? null : val;
});

const lngNum = computed(() => {
  const val = typeof props.longitude === 'string' ? parseFloat(props.longitude) : props.longitude;
  return isNaN(val) ? null : val;
});

// 格式化为司空2样式坐标: 29.61378983° N  106.355069475° E
const formattedCoords = computed(() => {
  if (latNum.value === null || lngNum.value === null) return '';
  const latDir = latNum.value >= 0 ? 'N' : 'S';
  const lngDir = lngNum.value >= 0 ? 'E' : 'W';
  const latStr = Math.abs(latNum.value).toFixed(8);
  const lngStr = Math.abs(lngNum.value).toFixed(8);
  return `${latStr}° ${latDir}  ${lngStr}° ${lngDir}`;
});

// 动态加载 Leaflet 引擎 (纯净挂载天地图 WMTS 图层，100% 无高德地图依赖)
function loadLeafletEngine(): Promise<any> {
  return new Promise((resolve, reject) => {
    if ((window as any).L) {
      resolve((window as any).L);
      return;
    }

    if (!document.getElementById('leaflet-css')) {
      const link = document.createElement('link');
      link.id = 'leaflet-css';
      link.rel = 'stylesheet';
      link.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css';
      document.head.appendChild(link);
    }

    const existingScript = document.getElementById('leaflet-js');
    if (existingScript) {
      existingScript.addEventListener('load', () => resolve((window as any).L));
      return;
    }

    const script = document.createElement('script');
    script.id = 'leaflet-js';
    script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';
    script.onload = () => resolve((window as any).L);
    script.onerror = (err) => reject(err);
    document.head.appendChild(script);
  });
}

// 初始化天地图
async function initMap() {
  if (latNum.value === null || lngNum.value === null || !mapContainer.value) return;

  loading.value = true;
  try {
    const L = await loadLeafletEngine();
    LeafletL = L;

    const lat = latNum.value;
    const lng = lngNum.value;

    // 清理销毁旧地图实例
    if (mapInstance) {
      mapInstance.remove();
      mapInstance = null;
      markerInstance = null;
      polylineInstance = null;
    }

    // 创建 Leaflet 地图容器 (纯天地图)
    mapInstance = L.map(mapContainer.value, {
      center: [lat, lng],
      zoom: 16,
      zoomControl: false,
      attributionControl: false,
    });

    // 1. 天地图 影像底图 + 影像注记 (img_w + cia_w)
    const tdtImg = L.tileLayer(
      `https://t{s}.tianditu.gov.cn/DataServer?T=img_w&x={x}&y={y}&l={z}&tk=${TDT_TOKEN}`,
      { subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'], maxZoom: 18 }
    );
    const tdtCia = L.tileLayer(
      `https://t{s}.tianditu.gov.cn/DataServer?T=cia_w&x={x}&y={y}&l={z}&tk=${TDT_TOKEN}`,
      { subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'], maxZoom: 18 }
    );
    satelliteGroup = L.layerGroup([tdtImg, tdtCia]);

    // 2. 天地图 矢量底图 + 矢量注记 (vec_w + cva_w)
    const tdtVec = L.tileLayer(
      `https://t{s}.tianditu.gov.cn/DataServer?T=vec_w&x={x}&y={y}&l={z}&tk=${TDT_TOKEN}`,
      { subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'], maxZoom: 18 }
    );
    const tdtCva = L.tileLayer(
      `https://t{s}.tianditu.gov.cn/DataServer?T=cva_w&x={x}&y={y}&l={z}&tk=${TDT_TOKEN}`,
      { subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'], maxZoom: 18 }
    );
    vectorGroup = L.layerGroup([tdtVec, tdtCva]);

    // 挂载初始图层
    if (isSatellite.value) {
      satelliteGroup.addTo(mapInstance);
    } else {
      vectorGroup.addTo(mapInstance);
    }

    // 绘制视频航迹折线 (若存在)
    if (props.flightPath && props.flightPath.length > 0) {
      polylineInstance = L.polyline(props.flightPath, {
        color: '#00f2fe',
        weight: 3,
        opacity: 0.8,
        lineCap: 'round',
        lineJoin: 'round',
      }).addTo(mapInstance);

      // 如果有完整航迹，缩放地图适配航迹 bounds
      try {
        const bounds = polylineInstance.getBounds();
        if (bounds.isValid()) {
          mapInstance.fitBounds(bounds, { padding: [20, 20], maxZoom: 17 });
        }
      } catch (e) {
        console.warn('Fit bounds warning:', e);
      }
    }

    // 创建司空2 打点/无人机姿态 Marker 图标
    updateOrCreateMarker(L, lat, lng, props.heading);

  } catch (error) {
    console.error('初始化天地图失败:', error);
  } finally {
    loading.value = false;
  }
}

// 动态创建或更更新 Marker 姿态 (位置 + 旋转角)
function updateOrCreateMarker(L: any, lat: number, lng: number, heading?: number) {
  if (!mapInstance) return;

  const rotDeg = heading !== undefined && heading !== null ? heading : 0;
  const rotStyle = `transform: rotate(${rotDeg}deg); transition: transform 0.2s ease-out;`;

  if (!markerInstance) {
    const markerIcon = L.divIcon({
      className: 'flighthub-leaflet-marker-wrapper',
      html: `
        <div class="flighthub-map-marker">
          <div class="marker-pulse-outer"></div>
          <div class="marker-icon-box" style="${rotStyle}">
            <!-- 飞机/无人机俯视图 SVG (头部朝上) -->
            <svg viewBox="0 0 24 24" width="28" height="28" fill="#00f2fe" style="filter: drop-shadow(0px 2px 4px rgba(0,0,0,0.6));">
              <path d="M21,16V14L13,9V3.5A1.5,1.5 0 0,0 11.5,2A1.5,1.5 0 0,0 10,3.5V9L2,14V16L10,13.5V19L8,20.5V22L11.5,21L15,22V20.5L13,19V13.5L21,16Z" />
            </svg>
          </div>
        </div>
      `,
      iconSize: [28, 28],
      iconAnchor: [14, 14], // 居中锚点，确保旋转时中心不变
    });

    markerInstance = L.marker([lat, lng], { icon: markerIcon }).addTo(mapInstance);
  } else {
    // 平滑平移 Marker 位置
    markerInstance.setLatLng([lat, lng]);

    // 旋转 Marker 图标元素
    const el = markerInstance.getElement();
    if (el) {
      const box = el.querySelector('.marker-icon-box');
      if (box) {
        box.style.transform = `rotate(${rotDeg}deg)`;
      }
    }
  }
}

// 动态只刷新 Marker 或 航迹 (避免视频卡顿)
function updateRealtimePosition() {
  if (latNum.value === null || lngNum.value === null || !mapInstance || !LeafletL) return;

  const lat = latNum.value;
  const lng = lngNum.value;

  updateOrCreateMarker(LeafletL, lat, lng, props.heading);
  mapInstance.panTo([lat, lng], { animate: false });

  // 动态追加更新航迹线
  if (props.flightPath && props.flightPath.length > 0) {
    if (!polylineInstance) {
      polylineInstance = LeafletL.polyline(props.flightPath, {
        color: '#00f2fe',
        weight: 3,
        opacity: 0.8,
      }).addTo(mapInstance);
    } else {
      polylineInstance.setLatLngs(props.flightPath);
    }
  }
}

// 切换天地图图层类型
function toggleMapType() {
  isSatellite.value = !isSatellite.value;
  if (!mapInstance) return;

  if (isSatellite.value) {
    if (vectorGroup) mapInstance.removeLayer(vectorGroup);
    if (satelliteGroup) mapInstance.addLayer(satelliteGroup);
  } else {
    if (satelliteGroup) mapInstance.removeLayer(satelliteGroup);
    if (vectorGroup) mapInstance.addLayer(vectorGroup);
  }
}

// 重置视角居中
function recenterMap() {
  if (mapInstance && latNum.value !== null && lngNum.value !== null) {
    mapInstance.setView([latNum.value, lngNum.value], 16);
  }
}

watch(
  [() => props.latitude, () => props.longitude, () => props.heading],
  () => {
    nextTick(() => {
      if (mapInstance) {
        updateRealtimePosition();
      } else {
        initMap();
      }
    });
  },
  { immediate: false }
);

watch(
  () => props.flightPath,
  (newPath) => {
    if (mapInstance && LeafletL && newPath) {
      if (polylineInstance) {
        polylineInstance.setLatLngs(newPath);
      } else if (newPath.length > 0) {
        polylineInstance = LeafletL.polyline(newPath, {
          color: '#00f2fe',
          weight: 3,
          opacity: 0.8,
        }).addTo(mapInstance);
      }
    }
  },
  { deep: true }
);

onMounted(() => {
  nextTick(() => {
    initMap();
  });
});

onUnmounted(() => {
  if (mapInstance) {
    mapInstance.remove();
    mapInstance = null;
    markerInstance = null;
    polylineInstance = null;
  }
});
</script>

<style scoped lang="scss">
.flighthub-map-card {
  margin-top: 10px;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: #141416;

  .map-view-wrapper {
    position: relative;
    width: 100%;
    display: flex;
    flex-direction: column;

    .map-instance {
      width: 100%;
      height: 165px;
      background: #0d0d0e;
    }

    .map-layer-control {
      position: absolute;
      top: 6px;
      right: 6px;
      z-index: 1000;

      .layer-toggle-btn {
        background: rgba(20, 20, 24, 0.75);
        backdrop-filter: blur(4px);
        border: 1px solid rgba(255, 255, 255, 0.2);
        color: rgba(255, 255, 255, 0.85);
        border-radius: 3px;
        padding: 2px 7px;
        font-size: 11px;
        cursor: pointer;
        transition: all 0.2s ease;

        &:hover {
          background: rgba(30, 30, 36, 0.95);
          color: #fff;
        }

        &.active {
          border-color: #38bdf8;
          color: #38bdf8;
        }
      }
    }

    .map-loading-overlay {
      position: absolute;
      inset: 0;
      background: rgba(20, 20, 24, 0.9);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      color: rgba(255, 255, 255, 0.6);
      font-size: 11px;
      z-index: 2000;
    }

    .coords-overlay {
      width: 100%;
      background: rgba(16, 16, 20, 0.95);
      border-top: 1px solid rgba(255, 255, 255, 0.08);
      padding: 4px 8px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      z-index: 1000;

      .coords-left {
        display: flex;
        align-items: center;
        gap: 12px;
      }

      .posture-right {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-right: 12px;
      }

      .coords-text, .altitude-text, .posture-item {
        font-family: Consolas, 'SFMono-Regular', Menlo, Monaco, monospace;
        font-size: 11px;
        color: #ffffff;
        letter-spacing: 0.2px;
      }

      .altitude-text {
        color: #00f2fe;
      }

      .posture-item {
        color: #38bdf8;
      }

      .recenter-btn {
        background: transparent;
        border: none;
        color: rgba(255, 255, 255, 0.7);
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 2px;
        border-radius: 2px;
        transition: all 0.2s ease;

        &:hover {
          color: #38bdf8;
          background: rgba(255, 255, 255, 0.1);
        }
      }
    }
  }
}
</style>

<style lang="scss">
/* 司空2 Leaflet 打点 Marker 容器与脉冲动画 */
.flighthub-leaflet-marker-wrapper {
  background: transparent !important;
  border: none !important;
}

.flighthub-map-marker {
  position: relative;
  width: 22px;
  height: 22px;

  .marker-icon-box {
    position: relative;
    z-index: 2;
    filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.6));
    transform-origin: center center;
  }

  .marker-pulse-outer {
    position: absolute;
    top: 50%;
    left: 50%;
    width: 26px;
    height: 26px;
    margin-top: -13px;
    margin-left: -13px;
    border-radius: 50%;
    background-color: rgba(255, 59, 48, 0.35);
    animation: flightHubPulse 2s infinite ease-out;
    z-index: 1;
    pointer-events: none;
  }
}

@keyframes flightHubPulse {
  0% {
    transform: scale(0.6);
    opacity: 0.9;
  }
  100% {
    transform: scale(2.4);
    opacity: 0;
  }
}
</style>
