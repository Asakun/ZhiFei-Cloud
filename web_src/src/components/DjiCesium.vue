<template>
  <div class="dji-cesium-container relative h-full w-full">
    <div ref="cesiumContainerRef" class="h-full w-full"></div>
    <!-- 非阻塞页面框架的轻量级地图引擎加载层 -->
    <div v-if="loading" class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm z-[1000] flex flex-col items-center justify-center transition-opacity duration-500">
      <a-spin size="large" tip="地图三维引擎加载中..." />
    </div>

    <!-- 右键弹出表单 -->
    <div v-if="contextMenu.visible"
      class="absolute bg-gray-900 border border-gray-700 text-white rounded-lg p-3 shadow-xl z-[999] min-w-[260px]"
      :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }">
      <div class="space-y-2 mb-3 text-xs">
        <div class="grid grid-cols-[60px_1fr] gap-2 items-center">
          <span class="text-gray-400">机场:</span>
          <select v-model="contextMenu.selectedDock"
            class="px-2 py-1 bg-gray-800 rounded border border-gray-600 outline-none focus:border-blue-500 text-white">
            <option :value="null" disabled>请选择机场</option>
            <option v-for="dock in dockMap" :key="dock.sn" :value="dock">
              {{ dock.callsign }}
            </option>
          </select>
        </div>
        <div class="grid grid-cols-[60px_1fr] gap-2 items-center">
          <span class="text-gray-400">经度:</span>
          <input v-model="contextMenu.position.longitude"
            class="px-2 py-1 bg-gray-800 rounded border border-gray-600 outline-none focus:border-blue-500" />
        </div>
        <div class="grid grid-cols-[60px_1fr] gap-2 items-center">
          <span class="text-gray-400">纬度:</span>
          <input v-model="contextMenu.position.latitude"
            class="px-2 py-1 bg-gray-800 rounded border border-gray-600 outline-none focus:border-blue-500" />
        </div>
        <div class="grid grid-cols-[60px_1fr_30px] gap-2 items-center">
          <span class="text-gray-400">参考地面高度:</span>
          <input disabled v-model="contextMenu.position.height"
            class="px-2 py-1 bg-gray-800 rounded border border-gray-600 outline-none focus:border-blue-500" />
          <span class="text-gray-400">米</span>
        </div>
        <div class="grid grid-cols-[60px_1fr_30px] gap-2 items-center" v-if="contextMenu.selectedDock">
          <span class="text-gray-400">距离机场:</span>
          <span class="text-white px-2">{{ Number(distanceToDock / 1000).toFixed(2) }}</span>
          <span class="text-gray-400">公里</span>
        </div>
        <div class="grid grid-cols-[60px_1fr_30px] gap-2 items-center">
          <span class="text-gray-400">相对于机场高度:</span>
          <input type="number" min="50" v-model="contextMenu.position.alt_height"
            class="px-2 py-1 bg-gray-800 rounded border border-gray-600 outline-none focus:border-blue-500" />
          <span class="text-gray-400">米</span>
        </div>
      </div>

      <div class="flex gap-2">
        <button @click="contextMenu.visible = false"
          class="flex-1 px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-xs rounded transition-colors">
          关闭
        </button>
        <button @click="flyToPoint"
          class="flex-1 px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-xs rounded transition-colors">
          指点飞行
        </button>
      </div>
    </div>
      <!-- <div class="ctrl" v-if="map.Cesium">
        <a-tooltip title="标记点">
          <button @click="startDraw('Point')">
            <img src="@/assets/airControl/map/icon-bj.png" alt="">
          </button>
        </a-tooltip>
        <a-tooltip title="画线">
          <button @click="startDraw('LineString')">
            <img src="@/assets/airControl/map/icon-hx.png" alt="">
          </button>
        </a-tooltip>
        <a-tooltip title="画面">
          <button @click="startDraw('Polygon')">
            <img src="@/assets/airControl/map/icon-hm.png" alt="">
          </button>
        </a-tooltip>

      </div> -->
    <div class="map-legend">
      <div class="map-legend-item">
        <div class="item" v-for="(item, index) in legendList" :key="'l_' + index">
          <div class="name">
            <img :src="item.url" alt="">
            {{ item.name }}
          </div>
          <a-switch size="small" v-model:checked="item.checked" @change="layerShowChange" />
        </div>
        <div class="item" v-for="(item, index) in legendList1" :key="'l1_' + index">
          <div class="name">
            <span :style="{ backgroundColor: item.color }"></span>
            {{ item.name }}
          </div>
          <a-switch size="small" v-model:checked="item.checked" @change="layerShowChange" />
        </div>
        <div class="item">
          <div class="name">
            <span style="background-color: rgb(255, 255, 0);"></span>
            黄圈(米)
          </div>
          <a-input-number v-model:value="yellowRadiusModel" :min="500" :step="100" size="small" style="width: 70px;" />
        </div>
      </div>
      <!-- 3D 建模独立控制模块 -->
      <div class="map-legend-item tileset-legend-module" v-if="totalTilesetCount > 0 || searchTilesetKeyword || loadingTilesets || (tilesetsList && tilesetsList.length > 0)">
        <div class="tileset-header">
          <span class="title">3D 建模</span>
          <span class="count-tag" v-if="totalTilesetCount > 0">{{ totalTilesetCount }}</span>
        </div>

        <!-- 关键词搜索框 -->
        <div class="tileset-search">
          <a-input
            v-model:value="searchTilesetKeyword"
            placeholder="搜索模型"
            size="small"
            allow-clear
            @change="onTilesetKeywordChange"
            @pressEnter="handleTilesetSearch"
          >
            <template #prefix>
              <SearchOutlined style="color: #999; font-size: 11px;" />
            </template>
          </a-input>
        </div>

        <!-- 模型列表 -->
        <div class="tileset-list" v-if="tilesetsList && tilesetsList.length > 0">
          <div class="tileset-item" v-for="(item, index) in tilesetsList" :key="'tileset_' + (item.file_id || item.id || index)">
            <div class="tileset-name" :title="item.name">
              <span class="tileset-dot"></span>
              <span class="tileset-label">{{ item.name }}</span>
            </div>
            <a-switch size="small" v-model:checked="item.checked" @change="(val) => handleTilesetToggle(val, item)" />
          </div>
        </div>
        <div class="tileset-empty" v-else>
          <span>{{ loadingTilesets ? '加载中...' : '暂无 3D 建模数据' }}</span>
        </div>

        <!-- 分页控制 -->
        <div class="tileset-pagination" v-if="totalTilesetCount > tilesetPageSize || totalTilesetPages > 1">
          <a-button
            type="text"
            size="small"
            class="pager-btn"
            :disabled="currentTilesetPage <= 1 || loadingTilesets"
            @click="handleTilesetPageChange(currentTilesetPage - 1)"
          >
            <LeftOutlined style="font-size: 10px;" />
          </a-button>
          <span class="pager-text">{{ currentTilesetPage }} / {{ totalTilesetPages }}</span>
          <a-button
            type="text"
            size="small"
            class="pager-btn"
            :disabled="currentTilesetPage >= totalTilesetPages || loadingTilesets"
            @click="handleTilesetPageChange(currentTilesetPage + 1)"
          >
            <RightOutlined style="font-size: 10px;" />
          </a-button>
        </div>
      </div>
    </div>
    <a-drawer :title="addMapName" :visible="visible" :mask="false" :maskClosable="false" placement="right" width="400"
      @close="handleClose">
      <div class="form-box">
        <a-form :model="formState" :label-col="{ span: 4 }">
          <a-form-item label="名称">
            <a-input v-model:value="formState.name" style="width: 88%;" @blur="hzNameBlur" />
            <a-tooltip title="删除">
              <DeleteOutlined class="icon-del" @click="toolClear" />
            </a-tooltip>
          </a-form-item>
          <a-form-item label="颜色" v-if="drawType !== 'Point'">
            <div class="color-box">
              <span v-for="(item, index) in 5" :key="index" @click="colorClick(index)"><i
                  v-if="formState.colorIndex == index">√</i></span>
            </div>
          </a-form-item>
        </a-form>
      </div>
    </a-drawer>
  </div>
</template>

<script setup>
import { watch, ref, reactive, computed, inject, onBeforeUnmount, onMounted, nextTick, shallowRef, markRaw, toRaw, shallowReactive } from 'vue'
import * as Cesium from 'cesium'
import { ELocalStorageKey } from '@/types'
import SvgIcon from "@/components/svgIcon.vue";
import { message } from 'ant-design-vue';
import { DeleteOutlined, SearchOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import { debounce } from "lodash-es";

import mapBjIcon from '../assets/airControl/map/map-icon-marker.svg'
import df from '../assets/airControl/map/map-icon-df.png'
import { makeDoubleLayerIcon, hexToRgba } from '@/utils/doubleLayerIcon'
import { uuidv4 } from '@/utils/uuid'
import { postElementsGroupsReq, postElementsReq, getElementGroupsReq, deleteElementsGroupsReq, deleteLayerEleReq } from '@/api/layer'
import { useEventEmitter, useLocalStorageState, useRequest, useTimeout } from "vue-hooks-plus";
import TiandituGeocoderService from "@/services/TiandituGeocoderService";
import { ColorGeometryInstanceAttribute, PerInstanceColorAppearance } from "cesium";
import { getFlightAreaList } from '@/api/flight-area';
import { EFlightAreaType, EGeometryType } from '@/types/flight-area';
import { CURRENT_CONFIG } from '@/api/http/config';
import { getTilesets } from '@/api/manage';

// 防护补丁：拦截 Cesium 在 Clustering 聚类、瓦片图层加载与视角缩放时由已销毁句柄抛出的 DeveloperError 终止渲染
try {
  if (Cesium.VertexArray && Cesium.VertexArray.prototype) {
    Cesium.VertexArray.prototype.throwOnDestroyed = function () {}
  }
  if (Cesium.Imagery && Cesium.Imagery.prototype) {
    Cesium.Imagery.prototype.throwOnDestroyed = function () {}
  }
  if (Cesium.TileImagery && Cesium.TileImagery.prototype) {
    Cesium.TileImagery.prototype.throwOnDestroyed = function () {}
  }

  if (Cesium.Context && Cesium.Context.prototype && !Cesium.Context.prototype.__safeDrawPatched) {
    Cesium.Context.prototype.__safeDrawPatched = true
    const originalDraw = Cesium.Context.prototype.draw
    Cesium.Context.prototype.draw = function (drawCommand, passState, shaderProgram, uniformMap) {
      try {
        if (drawCommand && drawCommand.vertexArray && typeof drawCommand.vertexArray.isDestroyed === 'function' && drawCommand.vertexArray.isDestroyed()) {
          return // 如果顶点数组已被聚类引擎销毁，安全跳过本帧该命令的绘制
        }
        return originalDraw.call(this, drawCommand, passState, shaderProgram, uniformMap)
      } catch (err) {
        console.warn('捕获并安全规避了 Cesium WebGL 帧间渲染竞态异常:', err)
      }
    }
  }
} catch (e) {
  console.warn('初始化 Cesium 安全补丁:', e)
}

// 自动为 2d Canvas 补全 willReadFrequently: true 标记，提升读取性能并消除 Canvas2D 控制台警告
if (typeof HTMLCanvasElement !== 'undefined' && !HTMLCanvasElement.prototype.__willReadFrequentlyPatched) {
  HTMLCanvasElement.prototype.__willReadFrequentlyPatched = true
  const origGetContext = HTMLCanvasElement.prototype.getContext
  HTMLCanvasElement.prototype.getContext = function (contextType, attributes) {
    if (contextType === '2d') {
      attributes = attributes || {}
      if (attributes.willReadFrequently === undefined) {
        attributes.willReadFrequently = true
      }
    }
    return origGetContext.call(this, contextType, attributes)
  }
}

const emit = defineEmits(["viewer-ready"])
const props = defineProps(['osdInfo', 'dockMapLocation', 'dockMap', 'droneMap', 'waylinePoints', 'markedPoint', 'reviewList', 'flightPathPoints', 'sceneMode', 'loadTerrain'])

const cesiumContainerRef = ref(null)
const viewerRef = shallowRef(null)

    const map = shallowReactive({
      viewer: null,
      drawEntity: null,
      positions: null,
      handler: null,
      Cesium: null,
      isDestroyed: false
    })
    const contextMenu = reactive({
      visible: false,
      x: 0,
      y: 0,
      position: {
        longitude: 0,
        latitude: 0,
        height: 0,
        alt_height: 20
      },
      selectedDock: null
    })

    const distanceToDock = computed(() => {
      // 检查组件是否已销毁
      if (map.isDestroyed) return 0
      if (contextMenu.selectedDock && contextMenu.position.longitude && contextMenu.position.latitude && map.Cesium) {
        const dockPos = map.Cesium.Cartesian3.fromDegrees(
          Number(contextMenu.selectedDock.longitude),
          Number(contextMenu.selectedDock.latitude),
          Number(contextMenu.selectedDock.height)
        )
        const targetPos = map.Cesium.Cartesian3.fromDegrees(
          Number(contextMenu.position.longitude),
          Number(contextMenu.position.latitude),
          Number(contextMenu.position.height)
        )
        return map.Cesium.Cartesian3.distance(dockPos, targetPos).toFixed(2)
      }
      return 0
    })

    /** 首页等多机场景以及虚拟座舱单机场景：由 droneMap 或 osdInfo 计算每架无人机的位置与朝向 */
    const droneEntities = computed(() => {
      if (map.isDestroyed || !map.Cesium) return []
      const Cesium = map.Cesium
      if (props.droneMap && props.droneMap.length > 0) {
        return props.droneMap.map((drone) => {
          const lon = Number(drone.longitude)
          const lat = Number(drone.latitude)
          const h = Number(drone.height + 20) || 0
          const position = [lon, lat, h]
          const heading = Number(drone.attitude_head) || 0
          const hpr = new Cesium.HeadingPitchRoll(
            Cesium.Math.toRadians(heading - 90),
            Cesium.Math.toRadians(0),
            Cesium.Math.toRadians(0)
          )
          const cartesian = Cesium.Cartesian3.fromDegrees(lon, lat, h)
          const orientation = Cesium.Transforms.headingPitchRollQuaternion(cartesian, hpr)
          return {
            sn: drone.sn,
            callsign: drone.callsign || drone.sn,
            position,
            orientation
          }
        })
      } else if (props.osdInfo && props.osdInfo.longitude !== undefined && Number(props.osdInfo.longitude) !== 0 && Number(props.osdInfo.latitude) !== 0) {
        const lon = Number(props.osdInfo.longitude)
        const lat = Number(props.osdInfo.latitude)
        const h = Number(props.osdInfo.height || props.osdInfo.elevation || 0) + 20
        const position = [lon, lat, h]
        const heading = Number(props.osdInfo.attitude_head) || 0
        const hpr = new Cesium.HeadingPitchRoll(
          Cesium.Math.toRadians(heading - 90),
          Cesium.Math.toRadians(0),
          Cesium.Math.toRadians(0)
        )
        const cartesian = Cesium.Cartesian3.fromDegrees(lon, lat, h)
        const orientation = Cesium.Transforms.headingPitchRollQuaternion(cartesian, hpr)
        return [{
          sn: props.osdInfo.sn || 'virtual_cockpit_drone',
          callsign: props.osdInfo.callsign || props.osdInfo.device_name || '执行机',
          position,
          orientation
        }]
      }
      return []
    })

    let mapClickIndex = 0
    let positions = []           // Cartesian3[]
    let lonLats = []   // 目标 WGS84 数组
    let totalM = 0 // 累计距离（米）
    let colorList = ['#2D8CF0', '#19BE6B', '#FFBB00', '#E23C39', '#B620E0']
    const mapShowData = ref([])//地图图层所有数据
    const allLayerData = ref(null)//地图图层所有数据
    const formType = ref(null)
    const addMapName = ref('标注信息')
    const visible = ref(false)
    const drawType = ref(null)
    const coordsText = ref(null)
    const pointEntities = ref([])     // 已落点实体
    const lineEntity = ref(null)      // 实时折线
    const polygonEntity = ref(null)   // 实时多边形
    const mouseLabel = ref(null) //鼠标跟随距离标签
    const mouseLineEntity = ref(null)
    const distLabels = ref([]) //距离
    const reviewMarker = ref(null) // 单个复核任务标记点（选中）
    const reviewMarkers = ref([])  // 所有复核任务标记点列表


    // 航线绘制相关
    const waylinePolyline = ref(null)
    const waylinePointEntities = ref([])
    const takeoffLine = ref(null)
    const flightPathLine = ref(null)
    const verticalLines = ref([]) // 垂直辅助线


    let waylineNativeEntities = []

    const updateWaylineEntities = () => {
      if (!map.viewer || map.viewer.isDestroyed() || !map.Cesium) return
      const targetEntities = map.legendDataSource ? map.legendDataSource.entities : map.viewer.entities

      // 1. 清理上一批航线实体
      if (waylineNativeEntities.length > 0) {
        waylineNativeEntities.forEach(entity => {
          try {
            targetEntities.remove(entity)
          } catch (e) {}
        })
        waylineNativeEntities = []
      }

      const points = props.waylinePoints
      if (!points || points.length === 0) return

      try {
        const positions = points.map(p => map.Cesium.Cartesian3.fromDegrees(Number(p.longitude), Number(p.latitude), Number(p.height || 100)))

        // 2. 主航线折线
        if (positions.length >= 2) {
          const polylineEntity = targetEntities.add({
            polyline: {
              positions: positions,
              width: 4,
              material: map.Cesium.Color.CYAN.withAlpha(0.9),
              clampToGround: false
            }
          })
          waylineNativeEntities.push(polylineEntity)
        }

        // 3. 起飞阶段导引线 (机场 -> 机场上方航点1高度 -> 航点1)
        const dock = props.dockMapLocation || (dockMap.value && dockMap.value[0])
        const wp1 = points[0]
        if (dock && dock.longitude && dock.latitude && wp1) {
          const p1 = map.Cesium.Cartesian3.fromDegrees(Number(dock.longitude), Number(dock.latitude), Number(dock.height || 0))
          const p2 = map.Cesium.Cartesian3.fromDegrees(Number(dock.longitude), Number(dock.latitude), Number(wp1.height || 100))
          const p3 = map.Cesium.Cartesian3.fromDegrees(Number(wp1.longitude), Number(wp1.latitude), Number(wp1.height || 100))

          const takeoffEntity = targetEntities.add({
            polyline: {
              positions: [p1, p2, p3],
              width: 3,
              material: new map.Cesium.PolylineDashMaterialProperty({
                color: map.Cesium.Color.LIME,
                dashLength: 16.0
              }),
              clampToGround: false
            }
          })
          waylineNativeEntities.push(takeoffEntity)
        }

        // 4. 航点图标、编号与垂直连线
        points.forEach((p, index) => {
          const topPos = map.Cesium.Cartesian3.fromDegrees(Number(p.longitude), Number(p.latitude), Number(p.height || 100))
          const bottomPos = map.Cesium.Cartesian3.fromDegrees(Number(p.longitude), Number(p.latitude), 0)

          // 航点 Entity
          const ptEntity = targetEntities.add({
            position: topPos,
            billboard: new map.Cesium.BillboardGraphics({
              image: mapBjIcon,
              width: 26,
              height: 32,
              verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
              disableDepthTestDistance: Number.POSITIVE_INFINITY
            }),
            label: new map.Cesium.LabelGraphics({
              text: `航点${index + 1} H:${Number(p.height || 0).toFixed(0)}m`,
              font: '13px sans-serif',
              fillColor: map.Cesium.Color.WHITE,
              showBackground: true,
              backgroundColor: map.Cesium.Color.fromCssColorString('rgba(0, 0, 0, 0.75)'),
              pixelOffset: new map.Cesium.Cartesian2(0, -36),
              verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
              disableDepthTestDistance: Number.POSITIVE_INFINITY
            })
          })
          waylineNativeEntities.push(ptEntity)

          // 垂直辅助虚线
          const vlineEntity = targetEntities.add({
            polyline: {
              positions: [topPos, bottomPos],
              width: 1,
              material: new map.Cesium.PolylineDashMaterialProperty({
                color: map.Cesium.Color.YELLOW.withAlpha(0.5),
                dashLength: 4.0
              }),
              clampToGround: false
            }
          })
          waylineNativeEntities.push(vlineEntity)
        })

        // 5. 自动缩放视角飞往航线区域
        if (positions.length > 0) {
          const boundingSphere = map.Cesium.BoundingSphere.fromPoints(positions)
          map.viewer.camera.flyToBoundingSphere(boundingSphere, {
            duration: 1.5,
            offset: new map.Cesium.HeadingPitchRange(0, map.Cesium.Math.toRadians(-45), Math.max(300, boundingSphere.radius * 2.5))
          })
        }
      } catch (e) {
        console.error('绘制航线实体失败:', e)
      }
    }

    // 监听航线点位变化，生成绘制实体
    watch(
      () => props.waylinePoints,
      (points) => {
        if (map.isDestroyed || !map.Cesium) return

        if (points && points.length > 0) {
          const positions = points.map((p) => map.Cesium.Cartesian3.fromDegrees(p.longitude, p.latitude, p.height))

          waylinePolyline.value = {
            positions: new map.Cesium.CallbackProperty(() => positions, false),
            material: new map.Cesium.ColorMaterialProperty(map.Cesium.Color.CYAN.withAlpha(0.8))
          }

          if (props.dockMapLocation) {
            const dock = props.dockMapLocation
            const wp1 = points[0]

            if (dock.longitude && dock.latitude && wp1) {
              const p1 = map.Cesium.Cartesian3.fromDegrees(dock.longitude, dock.latitude, dock.height || 0)
              const p2 = map.Cesium.Cartesian3.fromDegrees(dock.longitude, dock.latitude, wp1.height)
              const p3 = map.Cesium.Cartesian3.fromDegrees(wp1.longitude, wp1.latitude, wp1.height)

              takeoffLine.value = {
                positions: new map.Cesium.CallbackProperty(() => [p1, p2, p3], false),
                material: new map.Cesium.PolylineDashMaterialProperty({
                  color: map.Cesium.Color.LIME,
                  dashLength: 16.0
                })
              }
            }
          }

          waylinePointEntities.value = points.map((p, index) => ({
            position: [p.longitude, p.latitude, p.height],
            billboard: {
              image: mapBjIcon,
              width: 28,
              height: 34,
              verticalOrigin: 1,
              heightReference: 0
            },
            label: {
              text: `航点${index + 1} H:${p.height.toFixed(0)}m`,
              font: '14px sans-serif',
              fillColor: [255, 255, 255, 255],
              showBackground: true,
              backgroundColor: [0, 0, 0, 153],
              pixelOffset: [0, -40],
              verticalOrigin: 1,
              heightReference: 0
            }
          }))

          const lineMaterial = new map.Cesium.PolylineDashMaterialProperty({
            color: new map.Cesium.PolylineDashMaterialProperty({
              color: map.Cesium.Color.YELLOW.withAlpha(0.4),
              dashLength: 2
            }),
            dashLength: 2
          })

          verticalLines.value = points.map((p, index) => {
            const topPos = map.Cesium.Cartesian3.fromDegrees(p.longitude, p.latitude, p.height)
            const bottomPos = map.Cesium.Cartesian3.fromDegrees(p.longitude, p.latitude, 0)
            return {
              id: `vline-${index}`,
              positions: new map.Cesium.CallbackProperty(() => [topPos, bottomPos], false),
              material: lineMaterial
            }
          })
        } else {
          waylinePolyline.value = null
          waylinePointEntities.value = []
          takeoffLine.value = null
          verticalLines.value = []
        }

        updateWaylineEntities()
      },
      { immediate: true }
    )

    // 监听复核任务标记点
    watch(
      () => props.markedPoint,
      (val) => {
        if (!val || !val.lat || !val.lon) {
          reviewMarker.value = null
          return
        }

        // 1. 设置标记点实体
        reviewMarker.value = {
          position: [Number(val.lon), Number(val.lat)],
          billboard: {
            image: mapBjIcon,
            width: 32,
            height: 38,
            verticalOrigin: 1, // BOTTOM
            heightReference: 1, // CLAMP_TO_GROUND
            disableDepthTestDistance: Number.POSITIVE_INFINITY
          },
          label: {
            text: val.name || '复核点',
            font: '14px sans-serif',
            fillColor: [255, 255, 255, 255],
            showBackground: true,
            backgroundColor: [0, 0, 255, 180], // 蓝色背景
            pixelOffset: [0, -45],
            verticalOrigin: 1, // BOTTOM
            heightReference: 1, // CLAMP_TO_GROUND
            disableDepthTestDistance: Number.POSITIVE_INFINITY
          }
        }

        // 2. 飞行到目标点
        nextTick(() => {
          if (map.viewer) {
            map.viewer.camera.flyTo({
              destination: map.Cesium.Cartesian3.fromDegrees(Number(val.lon), Number(val.lat), 2000), // 高度2000米
              duration: 1.5
            })
          }
        })
      }
    )

    let reviewMarkerEntities = []
    const updateReviewMarkerEntities = () => {
      if (!map.viewer || map.viewer.isDestroyed() || !map.Cesium) return
      const targetEntities = map.legendDataSource ? map.legendDataSource.entities : map.viewer.entities

      // 清理现有复核点实体
      if (reviewMarkerEntities.length > 0) {
        reviewMarkerEntities.forEach(entity => {
          try {
            targetEntities.remove(entity)
          } catch (e) {}
        })
        reviewMarkerEntities = []
      }

      if (Array.isArray(reviewMarkers.value) && reviewMarkers.value.length > 0) {
        reviewMarkers.value.forEach(item => {
          if (item.position && item.position.length >= 2) {
            const lon = Number(item.position[0])
            const lat = Number(item.position[1])
            const alt = Number(item.position[2] || 0)
            if (!isNaN(lon) && !isNaN(lat)) {
              const entity = targetEntities.add({
                position: map.Cesium.Cartesian3.fromDegrees(lon, lat, alt),
                billboard: new map.Cesium.BillboardGraphics({
                  image: mapBjIcon,
                  width: 26,
                  height: 32,
                  verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
                  disableDepthTestDistance: Number.POSITIVE_INFINITY
                }),
                label: new map.Cesium.LabelGraphics({
                  text: item.label || '复核任务点',
                  font: '12px sans-serif',
                  fillColor: map.Cesium.Color.WHITE,
                  showBackground: true,
                  backgroundColor: map.Cesium.Color.fromCssColorString('rgba(0, 128, 0, 0.85)'),
                  pixelOffset: new map.Cesium.Cartesian2(0, -38),
                  verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
                  disableDepthTestDistance: Number.POSITIVE_INFINITY
                })
              })
              reviewMarkerEntities.push(entity)
            }
          }
        })
      }
    }

    // 监听复核任务列表
    watch(
      () => props.reviewList,
      (list) => {
        if (!list || list.length === 0) {
          reviewMarkers.value = []
          updateReviewMarkerEntities()
          return
        }

        reviewMarkers.value = list
          .filter(item => (item.lat || item.latitude) && (item.lon || item.longitude))
          .map(item => ({
            position: [Number(item.lon || item.longitude), Number(item.lat || item.latitude), 0],
            label: item.wenti_code || '任务点'
          }))
        updateReviewMarkerEntities()
      },
      { immediate: true }
    )

    let flightPathLineEntity = null
    const updateFlightPathLineEntity = (points) => {
      if (!map.viewer || map.viewer.isDestroyed() || !map.Cesium) return
      if (flightPathLineEntity) {
        try {
          map.viewer.entities.remove(flightPathLineEntity)
        } catch (e) {}
        flightPathLineEntity = null
      }
      if (points && points.length > 0) {
        const positions = points.map((p) => {
          return map.Cesium.Cartesian3.fromDegrees(
            Number(p.longitude),
            Number(p.latitude),
            Number(p.height || p.altitude || 100)
          )
        })
        flightPathLineEntity = map.viewer.entities.add({
          polyline: {
            positions: positions,
            width: 3,
            material: new map.Cesium.PolylineDashMaterialProperty({
              color: map.Cesium.Color.RED,
              dashLength: 16.0
            }),
            clampToGround: false
          }
        })
      }
    }

    // 监听指点飞行路线点位
    watch(
      () => props.flightPathPoints,
      (points) => {
        updateFlightPathLineEntity(points)
      },
      { immediate: true }
    )

    const iconArr = ref([
      new URL(`../assets/airControl/map/${'map-icon-df.png'}`, import.meta.url).href,
      new URL(`../assets/airControl/map/${'map-icon-pf.png'}`, import.meta.url).href,
      new URL(`../assets/airControl/map/${'map-icon-qt.png'}`, import.meta.url).href,
      new URL(`../assets/airControl/map/${'map-icon-sk.png'}`, import.meta.url).href,
      new URL(`../assets/airControl/map/${'map-icon-swzrs.png'}`, import.meta.url).href,
      new URL(`../assets/airControl/map/${'map-icon-yc.png'}`, import.meta.url).href,
      new URL(`../assets/airControl/map/${'map-icon-ydy.png'}`, import.meta.url).href,
    ])

    const formState = ref({
      id: null,
      name: '标记点',
      type: null,
      content: {
        properties: {
          color: "#2D8CF0",
          clampToGround: true
        },
        geometry: {
          type: "string",
          coordinates: [
            113.943109,
            22.577378
          ],
          radius: 0
        }
      },
      colorIndex: 0,
      ImgIndex: 0,
      latitude: null,
      longitude: ''
    })

    const legendList = ref([
      {
        url: new URL(`../assets/airControl/map/${'map-icon-marker.svg'}`, import.meta.url).href,
        name: '标记点',
        checked: false,
        elements: []
      }
    ])
    const legendList1 = ref([
      {
        color: '#E60012',
        name: '禁飞区',
        checked: false,
        elements: []
      },
      {
        color: '#FFFF00',
        name: '电子围栏',
        checked: false,
        elements: []
      },
      {
        color: '#FFA500',
        name: '巡查管理',
        checked: false,
        elements: []
      },
      {
        color: '#13FFFF',
        url: new URL(`../assets/airControl/map/${'map-icon-gkd.png'}`, import.meta.url).href,
        name: '国控点',
        checked: false,
        elements: []
      },
      {
        color: '#4DC993',
        name: '航线',
        checked: false,
        elements: []
      },
      {
        color: '#00FF00',
        name: '识别区',
        checked: false,
        elements: []
      },
      {
        color: '#FF0000',
        name: '官方禁飞区',
        checked: false,
        elements: [],
        isFlightArea: true,
        flightAreaType: 'nfz'
      },
      {
        color: '#00FF00',
        name: '官方可飞区',
        checked: false,
        elements: [],
        isFlightArea: true,
        flightAreaType: 'dfence'
      },
    ])
    const loading = ref(false)
    const animation = ref(false)
    const timeline = ref(false)
    const baseLayerPicker = ref(true)
    const fullscreenButton = ref(true)
    const infoBox = ref(false)
    const showCredit = ref(false)
    const fullscreenElement = document.body
    const vcViewer = ref(null)
    const currentViewer = ref(null)
    const point = ref({
      pixelSize: 28,
      color: 'red'
    })
    const label = ref({
      text: 'Hello World',
      pixelOffset: [0, 150]
    })
    const offset = ref([50, 25])
    const otherOpts = ref({
      offset: [0, 32],
      position: 'bottom-right'
    })
    const show = ref(false) // 默认为 false，等组件挂载后延迟激活，给 WebGL 上下文初始化时间

    const initNativeCesium = () => {
      console.log("initNativeCesium")

      if (!cesiumContainerRef.value || map.isDestroyed) return
      
      console.log("initNativeCesium1")
      // 配置 Cesium Ion Token 激活 3D 真实高程山体地形
      if (Cesium.Ion) {
        Cesium.Ion.defaultAccessToken = CURRENT_CONFIG.cesiumIonToken
      }

      const tdtToken = CURRENT_CONFIG.tiandituToken
      const imgProvider = new Cesium.UrlTemplateImageryProvider({
        url: `https://t{s}.tianditu.gov.cn/DataServer?T=img_w&X={x}&Y={y}&L={z}&tk=${tdtToken}`,
        subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
        maximumLevel: 17
      })
      const ciaProvider = new Cesium.UrlTemplateImageryProvider({
        url: `https://t{s}.tianditu.gov.cn/DataServer?T=cia_w&X={x}&Y={y}&L={z}&tk=${tdtToken}`,
        subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
        maximumLevel: 17
      })

      // 天地图地形图 (ter_w 官方最高等级为 14) 及地形注记图层 (cta_w)
      const terProvider = new Cesium.UrlTemplateImageryProvider({
        url: `https://t{s}.tianditu.gov.cn/DataServer?T=ter_w&X={x}&Y={y}&L={z}&tk=${tdtToken}`,
        subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
        maximumLevel: 14
      })
      const ctaProvider = new Cesium.UrlTemplateImageryProvider({
        url: `https://t{s}.tianditu.gov.cn/DataServer?T=cta_w&X={x}&Y={y}&L={z}&tk=${tdtToken}`,
        subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
        maximumLevel: 14
      })

      const _viewer = new Cesium.Viewer(cesiumContainerRef.value, {
        animation: props.animation || false,
        timeline: props.timeline || false,
        fullscreenButton: props.fullscreenButton || false,
        infoBox: props.infoBox || false,
        selectionIndicator: false,
        skyAtmosphere: false,
        skyBox: false,
        sceneModePicker: false,
        sceneMode: props.sceneMode !== undefined ? props.sceneMode : Cesium.SceneMode.SCENE2D,
        navigationHelpButton: false,
        homeButton: false,
        geocoder: true,
        baseLayerPicker: false,
        baseLayer: new Cesium.ImageryLayer(imgProvider),
        targetFrameRate: 30,
        contextOptions: {
          webgl: {
            alpha: false,
            antialias: false,
            powerPreference: 'high-performance'
          }
        }
      })
      _viewer.imageryLayers.addImageryProvider(ciaProvider)

      // 开启 3D 真实起伏山体地形 (仅在 loadTerrain 为 true 时加载)
      if (props.loadTerrain) {
        _viewer.scene.globe.depthTestAgainstTerrain = true
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
              // 静默捕获 Cesium Ion 401 (INVALID_TOKEN) 报错，防止报错弹框
              console.log('Cesium Ion 服务未授权或无 Token，降级为标准椭球高程')
            })
          } else if (typeof Cesium.createWorldTerrain === 'function') {
            _viewer.terrainProvider = Cesium.createWorldTerrain({
              requestWaterMask: true,
              requestVertexNormals: true
            })
          }
        } catch (e) {
          console.warn('初始化 3D 地形异常:', e)
        }
      } else {
        _viewer.scene.globe.depthTestAgainstTerrain = false
      }


      _viewer.camera.setView({
        destination: Cesium.Cartesian3.fromDegrees(106.11, 29.8000, 100000)
      })

      const rawViewer = markRaw(_viewer)
      viewerRef.value = rawViewer
      map.viewer = rawViewer
      map.Cesium = Cesium

      onViewerReady({ Cesium, viewer: rawViewer, vm: null })
      emit('viewer-ready', { Cesium, viewer: rawViewer, vm: null })
      syncNativeEntities()
    }

    const syncDockEntities = () => {
      if (!map.viewer || map.isDestroyed || !map.Cesium) return
      const viewer = map.viewer
      const Cesium = map.Cesium
      viewer.entities.suspendEvents()
      try {
        const dockDistDisplayCond = new Cesium.DistanceDisplayCondition(0, 60000)
        const labelDistDisplayCond = new Cesium.DistanceDisplayCondition(0, 35000)
        const billboardNearFar = new Cesium.NearFarScalar(1.0e3, 1.5, 1.0e5, 0.6)
        const dockLabelBgColor = new Cesium.Color(0, 0.47, 0.84, 0.8)
        const kmLabelBgColor = new Cesium.Color(0, 0, 0, 0.7)

        const currentDocks = dockMap.value || []
        const activeDockIds = new Set()

        if (Array.isArray(currentDocks)) {
          currentDocks.forEach((dock, index) => {
            if (dock.longitude == null || dock.latitude == null) return
            const dockId = 'dock_' + (dock.sn || index)
            const labelId = 'dock_label_' + (dock.sn || index)
            activeDockIds.add(dockId)
            activeDockIds.add(labelId)

            const callsignText = dock.callsign || '机场'
            const radiusVal = yellowRadiusModel.value
            const lon = Number(dock.longitude)
            const lat = Number(dock.latitude)
            const h = Number(dock.height || 0)
            const cacheKey = `${lon}_${lat}_${h}_${radiusVal}_${callsignText}`

            let dockEntity = viewer.entities.getById(dockId)
            if (dockEntity) {
              if (dockEntity._cacheKey !== cacheKey) {
                dockEntity._cacheKey = cacheKey
                dockEntity.position = Cesium.Cartesian3.fromDegrees(lon, lat, h + 10)
                if (dockEntity.label && dockEntity.label.text !== callsignText) {
                  dockEntity.label.text = callsignText
                }
                if (dockEntity.label) {
                  dockEntity.label.backgroundColor = dockLabelBgColor
                }
                if (dockEntity.ellipse) {
                  dockEntity.ellipse.semiMajorAxis = radiusVal
                  dockEntity.ellipse.semiMinorAxis = radiusVal
                  dockEntity.ellipse.height = h
                }
              }
            } else {
              const pos = Cesium.Cartesian3.fromDegrees(lon, lat, h + 10)
              dockEntity = viewer.entities.add({
                id: dockId,
                position: pos,
                description: `<h2>${callsignText}</h2>`,
                billboard: {
                  image: '/map_png/dockBilibord.png',
                  scale: 1.5,
                  show: true,
                  color: Cesium.Color.RED,
                  disableDepthTestDistance: Number.POSITIVE_INFINITY,
                  scaleByDistance: billboardNearFar
                },
                label: {
                  text: callsignText,
                  font: '16px Microsoft YaHei',
                  fillColor: Cesium.Color.WHITE,
                  showBackground: true,
                  backgroundColor: dockLabelBgColor,
                  pixelOffset: new Cesium.Cartesian2(0, -35),
                  disableDepthTestDistance: Number.POSITIVE_INFINITY
                },
                ellipse: {
                  semiMajorAxis: radiusVal,
                  semiMinorAxis: radiusVal,
                  fill: false,
                  height: h,
                  outline: true,
                  outlineColor: Cesium.Color.YELLOW,
                  outlineWidth: 2,
                  granularity: Cesium.Math.RADIANS_PER_DEGREE * 2,
                  distanceDisplayCondition: dockDistDisplayCond
                }
              })
              dockEntity._isDock = true
              dockEntity._cacheKey = cacheKey
            }

            let labelEntity = viewer.entities.getById(labelId)
            const kmText = (radiusVal / 1000).toFixed(1) + 'KM'
            if (labelEntity) {
              if (labelEntity._cacheKey !== cacheKey) {
                labelEntity._cacheKey = cacheKey
                labelEntity.position = Cesium.Cartesian3.fromDegrees(lon, lat + (radiusVal / 111000), h)
                if (labelEntity.label && labelEntity.label.text !== kmText) {
                  labelEntity.label.text = kmText
                }
              }
            } else {
              const labelPos = Cesium.Cartesian3.fromDegrees(lon, lat + (radiusVal / 111000), h)
              labelEntity = viewer.entities.add({
                id: labelId,
                position: labelPos,
                label: {
                  text: kmText,
                  font: 'bold 18px Microsoft YaHei',
                  fillColor: Cesium.Color.YELLOW,
                  showBackground: true,
                  backgroundColor: kmLabelBgColor,
                  pixelOffset: new Cesium.Cartesian2(0, 0),
                  disableDepthTestDistance: Number.POSITIVE_INFINITY,
                  distanceDisplayCondition: labelDistDisplayCond
                }
              })
              labelEntity._isDock = true
              labelEntity._cacheKey = cacheKey
            }
          })
        }

        const oldDocks = viewer.entities.values.filter(e => e._isDock)
        oldDocks.forEach(e => {
          if (!activeDockIds.has(e.id)) {
            viewer.entities.remove(e)
          }
        })
      } finally {
        viewer.entities.resumeEvents()
      }
    }

    const syncDroneEntities = () => {
      if (!map.viewer || map.isDestroyed || !map.Cesium) return
      const viewer = map.viewer
      const Cesium = map.Cesium
      viewer.entities.suspendEvents()
      try {
        const droneDistDisplayCond = new Cesium.DistanceDisplayCondition(0, 30000)
        const droneLabelBgColor = new Cesium.Color(0, 0.47, 0.84, 0.8)
        const activeDroneIds = new Set()

        if (droneEntities.value && Array.isArray(droneEntities.value)) {
          droneEntities.value.forEach((drone, index) => {
            if (!drone.position) return
            const droneId = 'drone_' + (drone.sn || index)
            activeDroneIds.add(droneId)

            const pos = Array.isArray(drone.position) ? Cesium.Cartesian3.fromDegrees(drone.position[0], drone.position[1], drone.position[2]) : drone.position
            const callsignText = drone.callsign || '无人机'
            const coordStr = Array.isArray(drone.position) ? `${drone.position[0]}_${drone.position[1]}_${drone.position[2]}` : ''

            let droneEntity = viewer.entities.getById(droneId)
            if (droneEntity) {
              if (droneEntity._lastPos !== coordStr || !coordStr) {
                droneEntity._lastPos = coordStr
                droneEntity.position = pos
                if (drone.orientation) droneEntity.orientation = drone.orientation
              }
              if (droneEntity.model) {
                droneEntity.model.scale = droneModelScale.value
              }
              if (droneEntity.label && droneEntity.label.text !== callsignText) {
                droneEntity.label.text = callsignText
              }
            } else {
              droneEntity = viewer.entities.add({
                id: droneId,
                position: pos,
                orientation: drone.orientation,
                model: {
                  uri: '/glb/DJI.glb',
                  scale: droneModelScale.value,
                  minimumPixelSize: 8,
                  maximumScale: 0.2
                },
                label: {
                  text: callsignText,
                  font: '16px Microsoft YaHei',
                  fillColor: Cesium.Color.WHITE,
                  showBackground: true,
                  backgroundColor: droneLabelBgColor,
                  pixelOffset: new Cesium.Cartesian2(0, -65),
                  distanceDisplayCondition: droneDistDisplayCond
                }
              })
              droneEntity._isDrone = true
              droneEntity._lastPos = coordStr
            }
          })
        }

        const oldDrones = viewer.entities.values.filter(e => e._isDrone)
        oldDrones.forEach(e => {
          if (!activeDroneIds.has(e.id)) {
            viewer.entities.remove(e)
          }
        })
      } finally {
        viewer.entities.resumeEvents()
      }
    }

    const syncNativeEntities = () => {
      syncDockEntities()
      syncDroneEntities()
    }

    onMounted(() => {
      fetchTilesetsList()
      nextTick(() => {
        setTimeout(() => {
          console.log('测试', map, map.isDestroyed)
          if (!map.isDestroyed) {
            show.value = true
            initNativeCesium()
          }
        }, 100)
      })
    })

    const accessToken = ref('eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiIzMTdmZTIxOS05MjVkLTQ3MGEtYWUzNy00YWQ4MDZiMjJhZGEiLCJpZCI6MjEwODIxLCJpYXQiOjE3MTM5Mjc0ODd9.tKCOP9jjOYKTT69b7wgnhlp20JeM_7ZlS9vAuTdsxBA')

    watch(timeline, val => {
      otherOpts.value.offset = val ? [0, 30] : fullscreenButton.value ? [30, 5] : [0, 5]
    })

    watch(fullscreenButton, val => {
      if (!timeline.value && !val) {
        otherOpts.value.offset = [0, 5]
      } else if (!timeline.value && val) {
        otherOpts.value.offset = [30, 5]
      }
    })

    const [dockMap1] = useLocalStorageState('dockMap')
    const [yellowRadius, setYellowRadius] = useLocalStorageState('flightCircleRadius', { defaultValue: 5000 })
    const yellowRadiusModel = computed({
      get: () => yellowRadius.value || 5000,
      set: (val) => setYellowRadius(val)
    })

    // 使用 shallowRef 缓存 dockMap，避免每次返回新数组导致闪烁
    const cachedDockMap = ref([])

    // 比较两个 dockMap 数组是否实质相同（基于 sn 和位置）
    const isDockMapEqual = (arr1, arr2) => {
      if (!arr1 || !arr2 || arr1.length !== arr2.length) return false
      return arr1.every((dock, i) => {
        const other = arr2[i]
        return dock.sn === other.sn &&
          dock.longitude === other.longitude &&
          dock.latitude === other.latitude &&
          dock.height === other.height
      })
    }

    // 监听 props.dockMap 变化，只在实质变化时更新缓存（移除昂贵的 deep 响应式递归追踪）
    watch(
      () => props.dockMap,
      (newVal) => {
        if (Array.isArray(newVal)) {
          if (newVal.length > 0) {
            if (!isDockMapEqual(newVal, cachedDockMap.value)) {
              cachedDockMap.value = [...newVal]
            }
          } else if (dockMap1.value && dockMap1.value.length > 0) {
            if (!isDockMapEqual(dockMap1.value, cachedDockMap.value)) {
              cachedDockMap.value = [...dockMap1.value]
            }
          } else {
            cachedDockMap.value = []
          }
        }
      },
      { immediate: true }
    )

    // 监听 localStorage 的 dockMap1 变化
    watch(
      dockMap1,
      (newVal) => {
        // 只有当 props.dockMap 为空时才使用 localStorage 的数据
        if ((!props.dockMap || props.dockMap.length === 0) && newVal && newVal.length > 0) {
          if (!isDockMapEqual(newVal, cachedDockMap.value)) {
            cachedDockMap.value = [...newVal]
          }
        }
      },
      { immediate: true }
    )

    // dockMap 直接返回缓存的值，避免每次都创建新数组
    const dockMap = computed(() => cachedDockMap.value)

    // 独立监听机场数据与飞行员半径，避开遥测更新风暴
    watch([cachedDockMap, yellowRadiusModel], () => { syncDockEntities() }, { immediate: true })
    // 独立监听飞机 OSD 变化，只更新飞机几何
    watch(droneEntities, () => { syncDroneEntities() }, { immediate: true })
    const globalEvent = useEventEmitter({ global: true })
    globalEvent.subscribe('globalEvent1', (message) => {
      // 检查组件是否已销毁
      if (map.isDestroyed || !map.Cesium) return

      if (currentViewer.value && !currentViewer.value.isDestroyed()) {
        const dockMapLocation = message.params
        const { longitude, latitude } = dockMapLocation ?? { longitude: 106.331200, latitude: 29.601615 }
        currentViewer.value.camera.flyTo({
          destination: map.Cesium.Cartesian3.fromDegrees(longitude, latitude, 1500),
          duration: 1
        })
      } else {
        useTimeout(() => {
          // 再次检查组件是否已销毁
          if (map.isDestroyed || !map.Cesium || !currentViewer.value || currentViewer.value.isDestroyed()) return

          const dockMapLocation = message.params
          const { longitude, latitude } = dockMapLocation ?? { longitude: 106.331200, latitude: 29.601615 }
          currentViewer.value.camera.flyTo({
            destination: map.Cesium.Cartesian3.fromDegrees(longitude, latitude, 1500),
            duration: 1
          })
        }, 3000)
      }
    })
    const flyToPointMethod = inject('flyToPointMethod', null)
    const virtualFlyToPointMethod = inject('virtualFlyToPointMethod', null)
    const droneColor = ref(null)
    const silhouetteColor = ref(null)
    const droneModelScale = ref(0.2)

    const tryFlyToDock = (location) => {
      if (map.isDestroyed || !map.Cesium || !map.viewer) return

      let lon = location?.longitude ?? props.dockMapLocation?.longitude
      let lat = location?.latitude ?? props.dockMapLocation?.latitude
      let h = location?.height ?? props.dockMapLocation?.height

      console.log('tryFlyToDock coordinates:', lon, '-', lat, '-', h)

      if (lon && lat && lon !== 0 && lat !== 0) {
        setTimeout(() => {
          if (map.viewer && map.Cesium && !map.isDestroyed) {
            map.viewer.camera.flyTo({
              destination: map.Cesium.Cartesian3.fromDegrees(Number(lon), Number(lat), (Number(h) || 0) + 3500),
              duration: 1
            })
          }
        }, 500)
      }
    }

    const flyToCoordinate = (lon, lat, height = 100, range = 300) => {
      if (map.isDestroyed || !map.Cesium || !map.viewer) return
      if (!lon || !lat) return
      try {
        const target = map.Cesium.Cartesian3.fromDegrees(Number(lon), Number(lat), Number(height || 0))
        map.viewer.camera.flyToBoundingSphere(new map.Cesium.BoundingSphere(target, range), {
          duration: 1.2,
          offset: new map.Cesium.HeadingPitchRange(0, map.Cesium.Math.toRadians(-45), range)
        })
      } catch (e) {
        console.warn('flyToCoordinate failed:', e)
      }
    }

    const tilesetsList = ref([])
    const searchTilesetKeyword = ref('')
    const currentTilesetPage = ref(1)
    const tilesetPageSize = ref(4)
    const totalTilesetCount = ref(0)
    const loadingTilesets = ref(false)
    const checkedTilesetMap = ref({})
    const totalTilesetPages = computed(() => Math.ceil(totalTilesetCount.value / tilesetPageSize.value) || 1)
    const tilesetPrimitivesMap = new Map() // url -> raw Cesium3DTileset primitive

    const loadTilesets = () => {
      if (!map.viewer || !map.Cesium || map.isDestroyed) return
      tilesetsList.value.forEach(item => {
        if (item.checked && !tilesetPrimitivesMap.has(item.url)) {
          try {
            map.Cesium.Cesium3DTileset.fromUrl(item.url, {
              maximumScreenSpaceError: 0.5,
              cacheBytes: 512 * 1024 * 1024,
              preferLeaves: false,
              skipLevelOfDetail: true
            }).then(tileset => {
              if (item.checked) { // Double check in case it was toggled off while loading
                if (tilesetPrimitivesMap.has(item.url)) return
                if (map.viewer && map.viewer.scene && !map.viewer.isDestroyed?.()) {
                  const addedTileset = map.viewer.scene.primitives.add(tileset)
                  const rawTileset = markRaw(addedTileset)
                  tilesetPrimitivesMap.set(item.url, rawTileset)
                  item.primitive = rawTileset
                  map.viewer.scene.requestRender?.()
                }
              } else {
                if (!tileset.isDestroyed?.()) {
                  tileset.destroy()
                }
              }
            }).catch(e => {
              console.error(`加载3D建模失败: ${item.name}`, e)
            })
          } catch (e) {
            console.error(`加载3D建模失败: ${item.name}`, e)
          }
        }
      })
    }

    const fetchTilesetsList = async () => {
      loadingTilesets.value = true
      try {
        console.log('是否执行')
        const res = await getTilesets({
          page: currentTilesetPage.value,
          page_size: tilesetPageSize.value,
          keyword: searchTilesetKeyword.value.trim() || undefined
        })
        let listData = []
        if (res && (res.code === 200 || res.code === 0) && res.data) {
          if (Array.isArray(res.data)) {
            listData = res.data
            totalTilesetCount.value = listData.length
          } else if (Array.isArray(res.data.list)) {
            listData = res.data.list
            totalTilesetCount.value = res.data.pagination?.total ?? listData.length
          }
        } else {
          listData = []
          totalTilesetCount.value = 0
        }
        tilesetsList.value = listData.map(item => {
          const key = item.file_id || item.object_key || item.name
          const isChecked = checkedTilesetMap.value[key] ?? false
          return {
            ...item,
            url: item.url || (item.object_key ? (item.object_key.startsWith('/') ? item.object_key : `/${item.object_key}`) : ''),
            checked: isChecked,
            primitive: null
          }
        })
        if (map.viewer && map.Cesium) {
          loadTilesets()
        }
      } catch (err) {
        console.error('获取3d建模列表失败:', err)
        tilesetsList.value = []
        totalTilesetCount.value = 0
      } finally {
        loadingTilesets.value = false
      }
    }

    let tilesetSearchTimer = null

    watch(searchTilesetKeyword, () => {
      clearTimeout(tilesetSearchTimer)
      tilesetSearchTimer = setTimeout(() => {
        currentTilesetPage.value = 1
        fetchTilesetsList()
      }, 300)
    })

    const onTilesetKeywordChange = (e) => {
      if (e && e.target && typeof e.target.value === 'string') {
        searchTilesetKeyword.value = e.target.value
      }
    }

    const handleTilesetSearch = () => {
      clearTimeout(tilesetSearchTimer)
      currentTilesetPage.value = 1
      fetchTilesetsList()
    }

    const handleTilesetPageChange = (newPage) => {
      if (newPage < 1 || newPage > totalTilesetPages.value) return
      currentTilesetPage.value = newPage
      fetchTilesetsList()
    }

    const handleTilesetToggle = (checked, item) => {
      if (!map.viewer || !map.Cesium) return
      const key = item.file_id || item.object_key || item.name
      checkedTilesetMap.value[key] = checked
      if (checked) {
        if (!tilesetPrimitivesMap.has(item.url)) {
          try {
            map.Cesium.Cesium3DTileset.fromUrl(item.url, {
              maximumScreenSpaceError: 0.5,
              cacheBytes: 512 * 1024 * 1024,
              preferLeaves: false,
              skipLevelOfDetail: true
            }).then(tileset => {
              if (item.checked) {
                if (tilesetPrimitivesMap.has(item.url)) return
                if (map.viewer && map.viewer.scene && !map.viewer.isDestroyed?.()) {
                  const addedTileset = map.viewer.scene.primitives.add(tileset)
                  const rawTileset = markRaw(addedTileset)
                  tilesetPrimitivesMap.set(item.url, rawTileset)
                  item.primitive = rawTileset
                  map.viewer.scene.requestRender?.()
                }
              } else {
                if (!tileset.isDestroyed?.()) {
                  tileset.destroy()
                }
              }
            }).catch(e => {
              console.error(`加载3D建模失败: ${item.name}`, e)
            })
          } catch (e) {
            console.error(`加载3D建模失败: ${item.name}`, e)
          }
        }
      } else {
        const rawTileset = tilesetPrimitivesMap.get(item.url) || (item.primitive ? toRaw(item.primitive) : null)
        if (rawTileset) {
          try {
            if (map.viewer && map.viewer.scene && !map.viewer.isDestroyed?.()) {
              map.viewer.scene.primitives.remove(rawTileset)
              map.viewer.scene.requestRender?.()
            }
          } catch (e) {
            console.error(`卸载3D建模失败: ${item.name}`, e)
          }
          tilesetPrimitivesMap.delete(item.url)
          item.primitive = null
        }
      }
    }

    //地图初始化
    const onViewerReady = ({ Cesium, viewer, vm }) => {
      console.log('viewer is loaded.', vm)
      // vm.vcMitt.on('destroyed', e => {
      //   console.log('viewer is destroyed', e)
      // })
      loading.value = false
      // handler = new Cesium.ScreenSpaceEventHandler(viewer.canvas)
      currentViewer.value = viewer
      droneColor.value = Cesium.Color.SKYBLUE
      silhouetteColor.value = Cesium.Color.BLACK
      viewer.scene.pickTranslucentDepth = true
      // 2. （可选）给地球加个地形，让拾取更直观
      // viewer.terrainProvider = Cesium.createWorldTerrain?.()
      // 动态加载 3D 建模
      loadTilesets()

      // 容错保护：监听 WebGL 渲染管线微延迟异常，自动恢复渲染循环，防止 Rendering has stopped
      if (viewer.scene.renderError) {
        viewer.scene.renderError.addEventListener((scene, error) => {
          console.warn('捕获到 Cesium WebGL 渲染管线微异常，自动恢复渲染:', error)
          viewer.useDefaultRenderLoop = true
        })
      }

      map.handler = new Cesium.ScreenSpaceEventHandler(viewer.canvas)
      map.viewer = viewer
      map.Cesium = Cesium

      // 初始化图层 CustomDataSource 并开启点位区域聚类 (Clustering)
      const legendDataSource = new Cesium.CustomDataSource('legendDataSource')
      legendDataSource.clustering.enabled = true
      legendDataSource.clustering.pixelRange = 65
      legendDataSource.clustering.minimumClusterSize = 2

      legendDataSource.clustering.clusterEvent.addEventListener((clusteredEntities, cluster) => {
        cluster.label.show = false
        cluster.billboard.show = true
        cluster.billboard.id = clusteredEntities
        cluster.billboard.verticalOrigin = Cesium.VerticalOrigin.CENTER
        cluster.billboard.image = getClusterIcon(clusteredEntities.length)
        cluster.billboard.width = 68
        cluster.billboard.height = 68
        cluster.billboard.disableDepthTestDistance = Number.POSITIVE_INFINITY
      })

      viewer.dataSources.add(legendDataSource)
      map.legendDataSource = legendDataSource

      // 监听地图实体及聚合点 (Cluster) 点击，实现点击自动平滑下钻
      map.handler.setInputAction((click) => {
        if (contextMenu.visible) {
          contextMenu.visible = false
        }
        if (!viewer || viewer.isDestroyed()) return
        const pickedObject = viewer.scene.pick(click.position)
        if (!Cesium.defined(pickedObject)) return

        // 1. 判断是否点击了聚合点 (Cluster Icon)
        let clusterEntities = null
        if (Array.isArray(pickedObject.id)) {
          clusterEntities = pickedObject.id
        } else if (pickedObject.primitive && pickedObject.primitive._clusteredEntities) {
          clusterEntities = pickedObject.primitive._clusteredEntities
        } else if (pickedObject.primitive && Array.isArray(pickedObject.primitive.id)) {
          clusterEntities = pickedObject.primitive.id
        }

        if (clusterEntities && clusterEntities.length > 0) {
          console.log('点击了聚合图标，触发下钻展开, 点位数量:', clusterEntities.length)
          const time = viewer.clock ? viewer.clock.currentTime : new Cesium.JulianDate()
          const positions = clusterEntities
            .map(e => typeof e.position?.getValue === 'function' ? e.position.getValue(time) : e.position)
            .filter(Boolean)

          if (positions.length > 0) {
            const boundingSphere = Cesium.BoundingSphere.fromPoints(positions)
            const carto = Cesium.Cartographic.fromCartesian(boundingSphere.center)
            const centerLon = Cesium.Math.toDegrees(carto.longitude)
            const centerLat = Cesium.Math.toDegrees(carto.latitude)
            const currentH = viewer.camera.positionCartographic.height

            // 1. 如果视角已拉到近空 (<=250m) 且点位在极小范围内 (radius < 18m，物理坐标重合/相近)
            if (currentH <= 250 && boundingSphere.radius < 18) {
              console.log('触发重合点位 Spiderfy 环形散开')
              const count = clusterEntities.length
              // 按弧度在半径 ~10 米 (约 0.00010°) 的圆周上散开
              const spreadRadius = 0.00010
              clusterEntities.forEach((entity, idx) => {
                const angle = (idx / count) * 2 * Math.PI
                const offsetLon = centerLon + spreadRadius * Math.cos(angle)
                const offsetLat = centerLat + spreadRadius * Math.sin(angle)
                const h = carto.height || 0
                entity.position = Cesium.Cartesian3.fromDegrees(offsetLon, offsetLat, h)
              })

              viewer.camera.flyTo({
                destination: Cesium.Cartesian3.fromDegrees(centerLon, centerLat, Math.max(30, currentH * 0.7)),
                duration: 0.8
              })
              return
            }

            // 2. 正常高度下的下钻
            let targetH = Math.min(currentH * 0.35, Math.max(45, boundingSphere.radius * 3.2))
            if (currentH < 250) {
              targetH = Math.max(25, currentH * 0.3)
            }

            viewer.camera.flyTo({
              destination: Cesium.Cartesian3.fromDegrees(centerLon, centerLat, targetH),
              duration: 1.0
            })
            return
          }
        }

        // 2. 点击单个实体 (Entity) 平滑下钻
        const entity = pickedObject.id
        if (entity && !Array.isArray(entity) && (entity._isDock || entity._isDrone || entity.myData || entity.position)) {
          const time = viewer.clock ? viewer.clock.currentTime : new Cesium.JulianDate()
          const pos = typeof entity.position?.getValue === 'function'
            ? entity.position.getValue(time)
            : entity.position
          if (pos) {
            const carto = Cesium.Cartographic.fromCartesian(pos)
            const lon = Cesium.Math.toDegrees(carto.longitude)
            const lat = Cesium.Math.toDegrees(carto.latitude)
            const currentH = viewer.camera.positionCartographic.height

            if (currentH > 1500) {
              viewer.camera.flyTo({
                destination: Cesium.Cartesian3.fromDegrees(lon, lat, 1200),
                duration: 1.2
              })
            }
          }
        }
      }, Cesium.ScreenSpaceEventType.LEFT_CLICK)

      // 监听鼠标右键弹出指点飞行菜单
      map.handler.setInputAction((e) => {
        onRightClick(e)
      }, Cesium.ScreenSpaceEventType.RIGHT_CLICK)

      // 限制 Web Worker 线程池上限为 2，防止在 4 核/双核 CPU 上开启 8 个 Worker 导致 CPU 撕裂及 DJI.glb 解析挂起 51 秒
      if (Cesium.TaskProcessor) {
        Cesium.TaskProcessor.maximumRecommendedWorkers = 2
      }
      // 优化网络并发与数据加载解压调度，避免在无独显/弱 CPU 设备上造成主线程和内存总线卡死
      if (Cesium.RequestScheduler) {
        Cesium.RequestScheduler.requestsByServer = 4
        Cesium.RequestScheduler.maximumRequests = 8
        Cesium.RequestScheduler.maximumRequestsPerServer = 4
      }
      if (viewer.scene && viewer.scene.globe) {
        viewer.scene.globe.loadingQueueThreshold = 2
        viewer.scene.globe.tileCacheSize = 100
      }

      emit("viewer-ready", viewer);

      const geocoderService = new TiandituGeocoderService({
        key: "4db514621ce57a692181a8bc4ddb9d72",
        specify: "156500000", // 优先限定搜重庆（瀨溪河流域）以提升检索准确度
        getLocalPoints: () => {
          const list = [];
          // 1. 收集已加载图层中的项目标注点 (Point 类型)
          if (Array.isArray(legendList.value)) {
            legendList.value.forEach(ite => {
              if (Array.isArray(ite.elements)) {
                ite.elements.forEach(el => {
                  if (el && el.type === 'Point' && Array.isArray(el.lonLat) && el.lonLat.length >= 2) {
                    list.push({
                      name: el.name || '未命名标注点',
                      longitude: Number(el.lonLat[0]),
                      latitude: Number(el.lonLat[1]),
                      address: ite.name ? `图层: ${ite.name}` : '项目标注'
                    });
                  }
                });
              }
            });
          }
          if (Array.isArray(legendList1.value)) {
            legendList1.value.forEach(ite => {
              if (Array.isArray(ite.elements)) {
                ite.elements.forEach(el => {
                  if (el && el.type === 'Point' && Array.isArray(el.lonLat) && el.lonLat.length >= 2) {
                    list.push({
                      name: el.name || '未命名标注点',
                      longitude: Number(el.lonLat[0]),
                      latitude: Number(el.lonLat[1]),
                      address: ite.name ? `图层: ${ite.name}` : '项目标注'
                    });
                  }
                });
              }
            });
          }
          // 2. 收集机场/基站标注点 (dockMap)
          if (Array.isArray(props.dockMap)) {
            props.dockMap.forEach(dock => {
              if (dock && dock.longitude && dock.latitude) {
                list.push({
                  name: dock.callsign || '未命名机场',
                  longitude: Number(dock.longitude),
                  latitude: Number(dock.latitude),
                  height: dock.height !== undefined ? Number(dock.height) + 150 : 150,
                  address: `机场 (SN: ${dock.sn || '未知'})`
                });
              }
            });
          }
          return list;
        }
      })

      const originGeocode = geocoderService.geocode.bind(geocoderService);

      // 3. 替换 geocode 方法，使用自定义防抖以返回 Promise，修复 lodash debounce 返回 undefined 导致无法检索的问题
      let debounceTimeout = null;
      geocoderService.geocode = (input, options) => {
        if (debounceTimeout) {
          clearTimeout(debounceTimeout);
        }
        return new Promise((resolve, reject) => {
          debounceTimeout = setTimeout(() => {
            originGeocode(input, options)
              .then(resolve)
              .catch(reject);
          }, 500); // 500ms 检索更顺畅
        });
      };
      viewer.geocoder.viewModel._geocoderServices = [geocoderService];
      if (viewer.geocoder && viewer.geocoder.container) {
        const setChinesePlaceholder = () => {
          const inputEl = viewer.geocoder.container.querySelector('.cesium-geocoder-input') || viewer.geocoder.container.querySelector('input');
          if (inputEl) {
            inputEl.placeholder = '搜索地址或地名...';
          }
        };
        setChinesePlaceholder();
        setTimeout(setChinesePlaceholder, 200);
      }

      // 每帧按需更新无人机模型缩放（避免无休止微步触发 Vue ref 响应式刷新）
      viewer.scene.preRender.addEventListener(() => {
        if (map.isDestroyed || !viewer || viewer.isDestroyed()) return
        const cameraHeight = viewer.camera.positionCartographic.height
        const target = computeDroneScale(cameraHeight)
        if (Math.abs(target - droneModelScale.value) > 0.05) {
          droneModelScale.value = target
        }
      })
      droneModelScale.value = computeDroneScale(viewer.camera.positionCartographic.height)
    }

    // 连续幂函数：scale = 0.0005 * height^0.75，无离散跳跃
    //   200m → 0.027 | 500m → 0.053 | 1km → 0.089 | 5km → 0.30
    //   20km → 0.84  | 100km → 2.8  | clamp [0.025, 3.5]
    function computeDroneScale(cameraHeight) {
      if (cameraHeight <= 0) return 0.2
      return Math.max(0.2, Math.min(50, 0.005 * Math.pow(cameraHeight, 0.75)))
    }

    const onNavigationEvt = e => {
      console.log(e, 'onNavigationEvt')
    }
    const onEntityClick = e => {
      console.log(e, 'onEntityClick')
    }
    const onLeftClick = e => {
      console.log(e, 'onLeftClick')
    }

    const flyToPoint = () => {
      if (!contextMenu.selectedDock) {
        message.warning('请选择机场')
        return
      }
      if (!contextMenu.position.alt_height || Number(contextMenu.position.alt_height) < 20) {
        message.warning('相对高度必须大于等于20米')
        return
      }

      // 绘制无人机到目标点的路线
      if (dronePosition.value && map.Cesium && contextMenu.selectedDock) {
        try {
          const startPos = map.Cesium.Cartesian3.fromDegrees(dronePosition.value[0], dronePosition.value[1], dronePosition.value[2])

          const dockHeight = Number(contextMenu.selectedDock.height || 0)
          const relativeHeight = Number(contextMenu.position.alt_height)
          const targetHeight = dockHeight + relativeHeight

          const endPos = map.Cesium.Cartesian3.fromDegrees(
            Number(contextMenu.position.longitude),
            Number(contextMenu.position.latitude),
            targetHeight
          )

          flightPathLine.value = {
            positions: new map.Cesium.CallbackProperty(() => [startPos, endPos], false),
            material: new map.Cesium.PolylineDashMaterialProperty({
              color: map.Cesium.Color.CYAN,
              dashLength: 16.0
            })
          }
        } catch (e) {
          console.error('绘制指点飞行路线失败', e)
        }
      }

      contextMenu.visible = false
      flyToPointMethod && flyToPointMethod(contextMenu.position, contextMenu.selectedDock)
      virtualFlyToPointMethod && virtualFlyToPointMethod(contextMenu.position, contextMenu.selectedDock)
    }
    const onRightClick = e => {
      // 检查组件是否已销毁
      if (map.isDestroyed || !map.Cesium || !currentViewer.value) return

      console.log(e, currentViewer)
      const ray = currentViewer.value.scene.camera.getPickRay(e.position)
      let cartesian = currentViewer.value.scene.globe.pick(ray, currentViewer.value.scene)
      if (!cartesian) {
        cartesian = currentViewer.value.scene.camera.pickEllipsoid(e.position, currentViewer.value.scene.globe.ellipsoid)
      }

      const windowPosition = e.position
      contextMenu.visible = true
      if (!contextMenu.selectedDock && dockMap.value && dockMap.value.length > 0) {
        contextMenu.selectedDock = dockMap.value[0]
      }
      const menuWidth = 420 // Approximate width of the menu
      // 根据分辨率动态调整高度预估，或者直接给一个较大值
      const menuHeight = 380
      const mapWidth = currentViewer.value.canvas.clientWidth
      console.log('windowPosition.x ', windowPosition.x, ' mapWidth ', mapWidth)

      // 更加智能的边界检测
      if (windowPosition.x + menuWidth > mapWidth) {
        contextMenu.x = Math.max(0, mapWidth - menuWidth - 10) // 确保不小于0
      } else {
        contextMenu.x = windowPosition.x + 10
      }

      const mapHeight = currentViewer.value.canvas.clientHeight
      console.log('windowPosition.y ', windowPosition.y, ' mapHeight ', mapHeight)

      if (windowPosition.y + menuHeight > mapHeight) {
        // 如果底部放不下，就尝试往上放
        contextMenu.y = Math.max(0, windowPosition.y - menuHeight)
      } else {
        contextMenu.y = windowPosition.y + 10
      }
      if (cartesian) {
        const cartographic = map.Cesium.Ellipsoid.WGS84.cartesianToCartographic(cartesian)

        const lon = map.Cesium.Math.toDegrees(cartographic.longitude)
        const lat = map.Cesium.Math.toDegrees(cartographic.latitude)
        const height = cartographic.height

        console.log(`经纬度: ${lat.toFixed(6)}, ${lon.toFixed(6)}, 高度: ${height.toFixed(2)} 米`)
        contextMenu.position.latitude = lat.toFixed(6)
        contextMenu.position.longitude = lon.toFixed(6)
        contextMenu.position.height = height.toFixed(2)
        // flyToPointMethod && flyToPointMethod(contextMenu.position)
        // virtualFlyToPointMethod && virtualFlyToPointMethod(contextMenu.position)
      }
    }
    const onTouchEnd = e => {
      console.log(e)
    }
    const onDestroyed = e => {
      console.log('onDestroyed', e)
    }
    const load = () => {
      // vcViewer.value.load().then(e => {
      //   console.log(e)
      //   loading.value = false
      // })
      show.value = true
    }
    const unload = () => {
      // this.$refs.vcViewer.unload().then(e => {
      //   console.log(e)
      //   this.loading = true
      // })
      show.value = false
    }
    const reload = () => {
      loading.value = true
      vcViewer.value.reload().then(e => {
        console.log(e)
        loading.value = false
      })
    }
    function addMarkers(Cesium, viewer) {
      if (viewer) {
        // // 创建一个标记点
        viewer.entities.add({
          position: Cesium.Cartesian3.fromDegrees(106.532117, 29.802762),
          billboard: new Cesium.BillboardGraphics({
            image: mapBjIcon, // 标记点图片的路径
            // position: point,
            width: 28,
            height: 34,
            verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
            heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
            disableDepthTestDistance: Number.POSITIVE_INFINITY
          }),
          label: {
            text: '标记点',
            font: '14px sans-serif',
            fillColor: Cesium.Color.WHITE,
            showBackground: true,
            backgroundColor: Cesium.Color.BLACK.withAlpha(0.6),
            pixelOffset: new Cesium.Cartesian2(0, -40),
            verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
            heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
            disableDepthTestDistance: Number.POSITIVE_INFINITY   // 永不被遮挡
          },
        });
        // 线
        viewer.entities.add({
          polyline: {
            positions: new Cesium.CallbackProperty(() => Cesium.Cartesian3.fromDegreesArray([106.532117, 29.802762, 106.53328, 29.801695].flat()), false),
            width: 4, // 线宽
            material: '#2D8CF0' // 线颜色
          },
          // label: {
          //   text: '名字',
          //   font: '14px sans-serif',
          //   fillColor: Cesium.Color.WHITE,
          //   showBackground: true,
          //   backgroundColor: Cesium.Color.BLACK.withAlpha(0.6),
          //   verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
          //   pixelOffset: new Cesium.Cartesian2(0, -20), // 在点上方 20px
          //   heightReference: Cesium.HeightReference.CLAMP_TO_GROUND
          // },
          // position: Cesium.Cartesian3.fromDegrees(106.532117, 29.802762),
        });
        //面
        let lt = [
          [
            106.52998423068581,
            29.80348816352653
          ],
          [
            106.53060040023978,
            29.801652521729586
          ],
          [
            106.53613704016485,
            29.8029038746203
          ],
          [
            106.53557924875493,
            29.8043120253775
          ]
        ]
        const pts = Cesium.Cartesian3.fromDegreesArray(lt.flat())

        // 2. 计算中心（BoundingSphere）
        const sphere = Cesium.BoundingSphere.fromPoints(pts)
        const carto = Cesium.Cartographic.fromCartesian(sphere.center)
        const centerLon = Cesium.Math.toDegrees(carto.longitude)
        const centerLat = Cesium.Math.toDegrees(carto.latitude)
        // return;
        viewer.entities.add({
          polygon: {
            hierarchy: new Cesium.CallbackProperty(() => ({
              positions: Cesium.Cartesian3.fromDegreesArray(lt.flat())
            }), false),
            // material: 'rgba(220,20,60,0.3)',
            // material: hexToRgba(colorList[0],0.4),
            material: new Cesium.ColorMaterialProperty(Cesium.Color.ORANGE.withAlpha(0.5)), // 橙色半透明
            outline: true,
            outlineColor: Cesium.Color.BLACK,
            outlineWidth: 10,
            heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
            perPositionHeight: false
          },
          label: {
            text: '面',
            font: '14px sans-serif',
            fillColor: Cesium.Color.WHITE,
            showBackground: true,
            backgroundColor: Cesium.Color.BLACK.withAlpha(0.6),
            verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
            pixelOffset: new Cesium.Cartesian2(0, -20), // 在点上方 20px
            heightReference: Cesium.HeightReference.CLAMP_TO_GROUND
          },
          position: Cesium.Cartesian3.fromDegrees(centerLon, centerLat),
        });
      }
    }


    /* -------- 统一入口：开始绘制 -------- */
    async function startDraw(type) {
      const id = uuidv4()
      if (formState.value.id && formState.value.id !== id) {
        handleClose()
      } else {
        formState.value.id = id
      }
      clear()
      drawType.value = type
      formType.value = 'add'
      // const iconUrl = await makeDoubleLayerIcon(mapBjIcon, df)//双重图标
      const iconUrl = mapBjIcon
      map.handler = new map.Cesium.ScreenSpaceEventHandler(map.viewer.canvas)
      /* 左键拾取 */
      map.handler.setInputAction(movement => {
        mapClickIndex++
        console.log('点击地图', mapClickIndex, 'type:', type)
        // const cartesian = map.viewer.scene.pickPosition(movement.position)
        // const cartesian3 = map.viewer.scene.camera.pickEllipsoid( movement.position, map.viewer.scene.globe.ellipsoid );
        // console.log(cartesian,'cartesian3cartesian3cartesian3')
        // if (!cartesian3) return
        //   // 2. 世界坐标转弧度坐标
        //   const cartographic = Cesium.Cartographic.fromCartesian(cartesian3);

        //   // 3. 弧度转经纬度
        //   const longitude = Cesium.Math.toDegrees(cartographic.longitude);
        //   const latitude = Cesium.Math.toDegrees(cartographic.latitude);
        //   const height = cartographic.height;
        const ray = map.viewer.camera.getPickRay(movement.position);
        let cartesian = map.viewer.scene.globe.pick(ray, map.viewer.scene);

        // 2. 俯视时 glob.pick 可能 null → 退到椭球面
        if (!cartesian) {
          cartesian = map.viewer.scene.camera.pickEllipsoid(movement.position, map.viewer.scene.globe.ellipsoid);
        }

        if (!cartesian) return; // 真的点在天空外
        positions.push(cartesian.clone())
        // 3. 转经纬度
        const carto = map.Cesium.Cartographic.fromCartesian(cartesian);
        const longitude = map.Cesium.Math.toDegrees(carto.longitude);
        const latitude = map.Cesium.Math.toDegrees(carto.latitude);
        const height = carto.height;
        console.log('经度:', longitude.toFixed(6), '纬度:', latitude.toFixed(6), '高度:', height, positions);


        // 1) 存 Cartesian3
        // positions.push(cartesian3)
        // 2) 转经纬度
        lonLats.push([longitude.toFixed(6), latitude.toFixed(6)])
        // 3) 画点
        if (type == 'Point') {
          formState.value.name = '标记点'
          formState.value.longitude = longitude.toFixed(6)
          formState.value.latitude = latitude.toFixed(6)
          pointEntities.value.push({
            position: [longitude, latitude],
            // point: { pixelSize: 8, color: type === 'Point' ? 'red' : 'orange' },
            billboard: {
              image: iconUrl,   // 支持相对路径、网络 url、base64
              width: 28,
              height: 34,
              verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
              heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
              disableDepthTestDistance: Number.POSITIVE_INFINITY
            },
            label: {
              text: formState.value.name,
              font: '12px sans-serif',
              fillColor: map.Cesium.Color.WHITE,
              showBackground: true,
              backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.6),
              pixelOffset: new Cesium.Cartesian2(0, -40),
              verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
              heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
              disableDepthTestDistance: Number.POSITIVE_INFINITY   // 永不被遮挡
            }
          })
          visible.value = true
        } else {
          pointEntities.value.push({
            position: [longitude, latitude],
            point: {
              pixelSize: 8,
              color: '#fff',
              outlineWidth: 2,
              outlineColor: 'orange',
              scale: 1,
              scaleByDistance: undefined,     // ② 去掉距离缩放
              translucencyByDistance: undefined,
              pixelOffsetScaleByDistance: undefined,
              heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND, // ← 贴地
              disableDepthTestDistance: Number.POSITIVE_INFINITY      // 可选：永不被遮挡
            },
          })
          if (positions.length > 1) {
            const seg = positions[positions.length - 2]
            totalM += map.Cesium.Cartesian3.distance(seg, cartesian)
          }
          distLabels.value.push({
            position: [longitude, latitude],
            label: {
              text: totalM.toFixed(1) + ' m',
              font: '14px sans-serif',
              fillColor: map.Cesium.Color.WHITE,
              showBackground: true,
              backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.6),
              verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
              pixelOffset: new Cesium.Cartesian2(0, -20), // 在点上方 20px
              heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND
            }
          })
        }


        // 4) 实时图形
        if (type === 'LineString' && !lineEntity.value) {

          addMapName.value = '路线信息'
          formState.value.name = '路线'
          lineEntity.value = {
            polyline: {
              positions: new Cesium.CallbackProperty(() => map.Cesium.Cartesian3.fromDegreesArray(lonLats.flat()), false),
              width: 3,
              // material : map.Cesium.Color.RED,
              material: colorList[formState.value.colorIndex],
              clampToGround: true //贴地
            },

          }
        }
        if (type === 'Polygon' && !polygonEntity.value) {
          addMapName.value = '范围信息'
          formState.value.name = '范围'
          polygonEntity.value = {
            polygon: {
              hierarchy: new Cesium.CallbackProperty(() => ({
                positions: map.Cesium.Cartesian3.fromDegreesArray(flatten(lonLats))
              }), false),
              // material: 'rgba(0,255,0,.3)',
              material: hexToRgba(colorList[formState.value.colorIndex], 0.4),
              outline: true,
              outlineColor: 'green',
              // 🔽 关键：贴地
              heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
              // 🔽 若线也想贴地，同步给 outline
              perPositionHeight: false      // 必须 false 才生效
            },
          }
        }

        // 5) 单点模式立即结束
        if (type === 'Point') {
          addMapName.value = '标注信息'
          map.handler.destroy()
          map.handler = null
          coordsText.value = JSON.stringify(lonLats, null, 2)
          setTimeout(() => {
            visible.value = true
          }, 500)
        }
        if (mapClickIndex == 1) {
          //新增标记分组名称

        }
      }, map.Cesium.ScreenSpaceEventType.LEFT_CLICK)

      /* 右键结束 */
      map.handler.setInputAction(() => {
        map.handler.destroy()
        map.handler = null
        coordsText.value = JSON.stringify(lonLats, null, 2)
        mouseLineEntity.value = null;
        mouseLabel.value = null;


        setTimeout(() => {
          visible.value = true
        }, 500)
      }, map.Cesium.ScreenSpaceEventType.RIGHT_CLICK)
      /* 鼠标移动：预览线段 + 实时长度 */
      map.handler.setInputAction(movement => {
        if (positions.length === 0 && type == 'Point') return
        const ray = map.viewer.camera.getPickRay(movement.endPosition);
        let cartesian = map.viewer.scene.globe.pick(ray, map.viewer.scene);

        // 2. 俯视时 glob.pick 可能 null → 退到椭球面
        if (!cartesian) {
          cartesian = map.viewer.scene.camera.pickEllipsoid(movement.endPosition, map.viewer.scene.globe.ellipsoid);
        }

        if (!cartesian) return; // 真的点在天空外
        const carto = map.Cesium.Cartographic.fromCartesian(cartesian);
        const longitude = map.Cesium.Math.toDegrees(carto.longitude);
        const latitude = map.Cesium.Math.toDegrees(carto.latitude);

        const lenM = map.Cesium.Cartesian3.distance(positions[positions.length - 1], cartesian)
        // 预览虚线
        mouseLineEntity.value = {
          polyline: {
            width: 3,
            material: new Cesium.PolylineDashMaterialProperty({
              color: map.Cesium.Color.YELLOW
            }),
            clampToGround: true,
            positions: new Cesium.CallbackProperty(() => map.Cesium.Cartesian3.fromDegreesArray([lonLats[lonLats.length - 1], [longitude, latitude]].flat()), false),
          }
        }

        // 鼠标跟随长度标签
        mouseLabel.value = {
          position: [map.Cesium.Math.toDegrees(map.Cesium.Cartographic.fromCartesian(cartesian).longitude),
          map.Cesium.Math.toDegrees(map.Cesium.Cartographic.fromCartesian(cartesian).latitude)],
          label: {
            text: lenM.toFixed(1) + ' m',
            font: '14px sans-serif',
            fillColor: map.Cesium.Color.WHITE,
            showBackground: true,
            backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.6),
            verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
            pixelOffset: new Cesium.Cartesian2(0, -25),
            heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
            disableDepthTestDistance: Number.POSITIVE_INFINITY
          }
        }
      }, map.Cesium.ScreenSpaceEventType.MOUSE_MOVE)
    }

    /* -------- 清空 -------- */
    function clear() {
      if (map.handler) { map.handler.destroy(); map.handler = null }
      positions = []; lonLats = [], totalM = 0,
        distLabels.value = []
      pointEntities.value = []
      lineEntity.value = null
      polygonEntity.value = null
      mouseLineEntity.value = null;
      mouseLabel.value = null;
      drawType.value = '';
      coordsText.value = '';

    }
    //图标切换
    function tbClick(i) {
      formState.value.ImgIndex = i
      hzMap()
    }
    //名称替换
    function hzNameBlur() {
      if (drawType.value == 'Point') {
        hzMap()
      }
    }
    //经纬度修改
    function lonLatBlur() {
      if (drawType.value == 'Point') {
        lonLats[0] = [formState.value.longitude, formState.value.latitude]
        hzMap()
      }
    }

    //绘线、面颜色切换
    function colorClick(i) {
      formState.value.colorIndex = i
      if (drawType.value != 'Point') {
        hzMap()
      }
    }
    //弹窗删除
    function toolClear() {
      //删除标记分组名称
      // let delReq = deleteElementsGroupsReq(formState.value.id)
      // let delReq1 = deleteLayerEleReq({id: formState.value.id})
      positions = []; lonLats = [];
      pointEntities.value = []
      lineEntity.value = null
      polygonEntity.value = null
      drawType.value = '';
      coordsText.value = ''
      visible.value = false
      distLabels.value = []
      formState.value = {
        name: '标记点',
        colorIndex: 0,
        ImgIndex: 0,
        latitude: null,
        longitude: '',
        id: ''
      }

    }
    //弹窗关闭
    async function handleClose() {
      const username = localStorage.getItem(ELocalStorageKey.Username)
      const pid = '7e9027b5-2311-43a3-8ada-36060857abd1'
      await Promise.all([
        //新增经纬度 信息
        postElementsReq(
          pid,
          {
            id: formState.value.id,
            name: formState.value.name,
            resource: {
              type: 0,
              content: {
                type: "Feature",
                properties: {
                  color: colorList[formState.value.colorIndex],
                  clampToGround: true
                },
                geometry: {
                  type: drawType.value,
                  coordinates: drawType.value == 'Point' ? lonLats[0] : drawType.value == 'LineString' ? lonLats : [lonLats],
                  longitude: drawType.value == 'Point' ? Number(lonLats[0][0]) : '',
                  latitude: drawType.value == 'Point' ? Number(lonLats[0][1]) : '',
                  radius: 0
                }
              },
              user_name: username
            }
          }
        )
      ]).then(res => {
        if (res[0].code == 0) {
          message.success(
            '新增成功！',
            10,
          );
          visible.value = false
          formState.value = {
            name: '标记点',
            colorIndex: 0,
            ImgIndex: 0,
            latitude: null,
            longitude: '',
            id: ''
          }
          getMapLayer()
        }
      })

    }
    //地图数据处理
    function flatten(arr) {
      return arr.reduce((acc, cur) => acc.concat(cur), [])
    }
    //重新绘制
    async function hzMap() {
      pointEntities.value = []
      // const iconUrl = await makeDoubleLayerIcon(mapBjIcon, iconArr.value[formState.value.ImgIndex])//双重图标
      const iconUrl = mapBjIcon
      if (drawType.value == 'Point') {
        pointEntities.value.push({
          position: lonLats[0],
          // point: { pixelSize: 8, color: type === 'point' ? 'red' : 'orange' },
          billboard: {
            image: iconUrl,   // 支持相对路径、网络 url、base64
            width: 28,
            height: 34,
            verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
            heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
            disableDepthTestDistance: Number.POSITIVE_INFINITY
          },
          label: {
            text: formState.value.name,
            font: '12px sans-serif',
            fillColor: map.Cesium.Color.WHITE,
            showBackground: true,
            backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.6),
            pixelOffset: new Cesium.Cartesian2(0, -40),
            verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
            heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
            disableDepthTestDistance: Number.POSITIVE_INFINITY   // 永不被遮挡
          }
        })
      } else {
        lonLats.map(val => {
          pointEntities.value.push({
            position: val,
            point: {
              pixelSize: 8,
              outlineWidth: 4,
              outlineColor: '#fff',
              color: 'orange',
              heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND, // ← 贴地
              disableDepthTestDistance: Number.POSITIVE_INFINITY      // 可选：永不被遮挡
            },
          })
        })

      }


      // 4) 实时图形
      if (drawType.value === 'LineString' && lineEntity.value) {
        lineEntity.value = {
          polyline: {
            positions: new Cesium.CallbackProperty(() => map.Cesium.Cartesian3.fromDegreesArray(lonLats.flat()), false),
            width: 3,
            // material : map.Cesium.Color.RED,
            material: colorList[formState.value.colorIndex],
            clampToGround: true
          },

        }
      }
      if (drawType.value === 'Polygon' && polygonEntity.value) {
        polygonEntity.value = {
          polygon: {
            hierarchy: new Cesium.CallbackProperty(() => ({
              positions: map.Cesium.Cartesian3.fromDegreesArray(flatten(lonLats))
            }), false),
            // material: 'rgba(0,255,0,.3)',
            material: hexToRgba(colorList[formState.value.colorIndex], 0.4),
            outline: true,
            outlineColor: 'green',
            // 🔽 关键：贴地
            heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
            // 🔽 若线也想贴地，同步给 outline
            perPositionHeight: false      // 必须 false 才生效
          }
        }
      }

    }
    //图层显示
    async function layerShowChange() {
      let layerList = [...legendList.value, ...legendList1.value]

      // 挂起地图和聚类数据源的实体事件，防止在批量添加新图例点位时触发高频聚类 Primitive 重建销毁 WebGL 顶点数组
      if (map.legendDataSource) {
        map.legendDataSource.entities.suspendEvents()
      }
      if (map.viewer) {
        map.viewer.entities.suspendEvents()
      }

      try {
        // 处理每个图层的显示/隐藏
        for (const val of layerList) {
          if (!val.entities) {
            val.entities = []
          }

          if (val.isFlightArea) {
            if (val.checked && (!val.entities || val.entities.length === 0)) {
              loadFlightAreas(val.flightAreaType, val.color, val)
            }
          }

          if (val.checked) {
            // 检查是否已完全创建。同步一次性完成缺失实体的创建
            if (val.entities.length < val.elements.length) {
              const createdIds = new Set(val.entities.map(e => e.id))
              const elementsToCreate = val.elements.filter(ite => !createdIds.has(ite.id))

              elementsToCreate.forEach(ite => {
                let entity = null
                if (ite.type === 'Point') {
                  entity = setMaker(ite.name, ite.lonLat, val.url, ite)
                } else if (ite.type === 'LineString') {
                  entity = setLine(ite.name, ite.lonLat, ite)
                } else if (ite.type === 'Polygon') {
                  entity = setArea(ite.name, ite.lonLat, ite)
                }
                if (entity && !val.entities.includes(entity)) {
                  val.entities.push(entity)
                }
              })
            }

            // 设为可见
            val.entities.forEach(entity => {
              if (entity) entity.show = true
            })
          } else {
            // 未选中状态，设置 show = false
            if (val.entities.length > 0) {
              val.entities.forEach(entity => {
                if (entity) entity.show = false
              })
            }
          }
        }
      } finally {
        // 恢复实体事件，触发单次高效聚类计算，避免 WebGL 渲染管线中断
        if (map.legendDataSource) {
          map.legendDataSource.entities.resumeEvents()
        }
        if (map.viewer) {
          map.viewer.entities.resumeEvents()
        }
      }
    }

    // 判断当前是否处于 2D 模式
    const is2DMode = () => {
      if (props.sceneMode === 2 || (Cesium?.SceneMode && props.sceneMode === Cesium.SceneMode.SCENE2D)) return true
      if (map.viewer && !map.viewer.isDestroyed() && map.viewer.scene && Cesium?.SceneMode && map.viewer.scene.mode === Cesium.SceneMode.SCENE2D) return true
      return false
    }

    // 加载并绘制官方飞行区域 (2D/3D自适应分批加载)
    async function loadFlightAreas(flightAreaType, color, val) {
      if (!map.viewer || map.isDestroyed) return
      try {
        const res = await getFlightAreaList()
        if (res.code !== 0 || !res.data) {
          console.warn('获取飞行区域列表失败:', res.message)
          return
        }

        const filteredAreas = res.data.filter(area => area.type === flightAreaType)
        const is2D = is2DMode()
        const chunkSize = 30

        const addChunk = () => {
          if (filteredAreas.length === 0 || !val.checked) return
          const chunk = filteredAreas.splice(0, chunkSize)
          chunk.forEach((area) => {
            const content = area.content
            if (!content || !content.geometry) return
            const geometryType = content.geometry.type
            const coordinates = content.geometry.coordinates

            const areaColor = flightAreaType === EFlightAreaType.NFZ
              ? map.Cesium.Color.RED.withAlpha(0.3)
              : map.Cesium.Color.GREEN.withAlpha(0.3)
            const outlineColor = flightAreaType === EFlightAreaType.NFZ
              ? map.Cesium.Color.RED
              : map.Cesium.Color.GREEN

            if (geometryType === EGeometryType.CIRCLE) {
              const radius = content.geometry.radius
              const center = coordinates
              const entity = map.viewer.entities.add({
                id: `flight-area-${area.area_id}`,
                name: `[${flightAreaType === EFlightAreaType.NFZ ? '禁飞区' : '可飞区'}] ${area.name}`,
                description: `
                  <div style="padding: 10px; font-family: Microsoft YaHei;">
                    <h3 style="margin: 0 0 10px 0; color: ${flightAreaType === EFlightAreaType.NFZ ? '#ff4444' : '#44ff44'};">
                      ${flightAreaType === EFlightAreaType.NFZ ? '🚫 禁飞区域' : '✅ 可飞区域'}
                    </h3>
                    <table style="width: 100%; border-collapse: collapse;">
                      <tr><td style="padding: 4px 8px; color: #888;">区域名称</td><td style="padding: 4px 8px;">${area.name}</td></tr>
                      <tr><td style="padding: 4px 8px; color: #888;">区域类型</td><td style="padding: 4px 8px;">圆形区域</td></tr>
                      <tr><td style="padding: 4px 8px; color: #888;">半径</td><td style="padding: 4px 8px;">${radius} 米</td></tr>
                      <tr><td style="padding: 4px 8px; color: #888;">中心经度</td><td style="padding: 4px 8px;">${center[0].toFixed(6)}</td></tr>
                      <tr><td style="padding: 4px 8px; color: #888;">中心纬度</td><td style="padding: 4px 8px;">${center[1].toFixed(6)}</td></tr>
                      <tr><td style="padding: 4px 8px; color: #888;">状态</td><td style="padding: 4px 8px;">${area.status ? '启用' : '禁用'}</td></tr>
                    </table>
                  </div>
                `,
                position: map.Cesium.Cartesian3.fromDegrees(center[0], center[1]),
                ellipse: {
                  semiMajorAxis: radius,
                  semiMinorAxis: radius,
                  material: areaColor,
                  outline: true,
                  outlineColor: outlineColor,
                  outlineWidth: 2,
                  heightReference: is2D ? Cesium.HeightReference.NONE : map.Cesium.HeightReference.CLAMP_TO_GROUND,
                  height: is2D ? 0 : undefined
                }
              })
              val.entities.push(entity)
              mapShowData.value.push(entity)
            } else if (geometryType === EGeometryType.POLYGON) {
              const polygonCoords = coordinates[0]
              const positions = polygonCoords.map(coord =>
                map.Cesium.Cartesian3.fromDegrees(coord[0], coord[1])
              )
              let centerLon = 0, centerLat = 0
              polygonCoords.forEach(coord => {
                centerLon += coord[0]
                centerLat += coord[1]
              })
              centerLon /= polygonCoords.length
              centerLat /= polygonCoords.length

              const entity = map.viewer.entities.add({
                id: `flight-area-${area.area_id}`,
                name: `[${flightAreaType === EFlightAreaType.NFZ ? '禁飞区' : '可飞区'}] ${area.name}`,
                description: `
                  <div style="padding: 10px; font-family: Microsoft YaHei;">
                    <h3 style="margin: 0 0 10px 0; color: ${flightAreaType === EFlightAreaType.NFZ ? '#ff4444' : '#44ff44'};">
                      ${flightAreaType === EFlightAreaType.NFZ ? '🚫 禁飞区域' : '✅ 可飞区域'}
                    </h3>
                    <table style="width: 100%; border-collapse: collapse;">
                      <tr><td style="padding: 4px 8px; color: #888;">区域名称</td><td style="padding: 4px 8px;">${area.name}</td></tr>
                      <tr><td style="padding: 4px 8px; color: #888;">区域类型</td><td style="padding: 4px 8px;">多边形区域</td></tr>
                      <tr><td style="padding: 4px 8px; color: #888;">顶点数</td><td style="padding: 4px 8px;">${polygonCoords.length} 个</td></tr>
                      <tr><td style="padding: 4px 8px; color: #888;">中心经度</td><td style="padding: 4px 8px;">${centerLon.toFixed(6)}</td></tr>
                      <tr><td style="padding: 4px 8px; color: #888;">中心纬度</td><td style="padding: 4px 8px;">${centerLat.toFixed(6)}</td></tr>
                      <tr><td style="padding: 4px 8px; color: #888;">状态</td><td style="padding: 4px 8px;">${area.status ? '启用' : '禁用'}</td></tr>
                    </table>
                  </div>
                `,
                polygon: {
                  hierarchy: new map.Cesium.PolygonHierarchy(positions),
                  material: areaColor,
                  outline: true,
                  outlineColor: outlineColor,
                  outlineWidth: 2,
                  heightReference: is2D ? Cesium.HeightReference.NONE : map.Cesium.HeightReference.CLAMP_TO_GROUND,
                  height: is2D ? 0 : undefined
                },
                position: map.Cesium.Cartesian3.fromDegrees(centerLon, centerLat)
              })
              val.entities.push(entity)
              mapShowData.value.push(entity)
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
        message.error('加载飞行区域失败，请检查网络连接')
      }
    }

    //获取地图展示数据
    async function getMapLayer() {
      // 1. 清理原有的图层缓存实体，防止更新数据时出现残留
      const layerList = [...legendList.value, ...legendList1.value]
      layerList.forEach(ite => {
        if (ite.entities && ite.entities.length > 0) {
          ite.entities.forEach(entity => {
            if (map.legendDataSource) {
              map.legendDataSource.entities.remove(entity)
            }
            if (map.viewer) {
              map.viewer.entities.remove(entity)
            }
          })
          ite.entities = []
        }
      })

      try {
        let allLayerData = await getElementGroupsReq({})
        if (allLayerData && allLayerData.data) {
          const list = Array.isArray(allLayerData.data) ? allLayerData.data : (Array.isArray(allLayerData.data.list) ? allLayerData.data.list : [])
          legendList.value.forEach(ite => {
            let elements = []
            list.forEach(val => {
              if (ite.name == val.name && val.elements && val.elements.length > 0) {
                val.elements.forEach(element => {
                  elements.push({
                    name: element?.name,
                    id: element?.id,
                    type: element?.resource?.content?.geometry?.type,
                    lonLat: element?.resource?.content?.geometry?.coordinates,
                    resource: element?.resource,
                  })
                })
              }
            })
            ite.elements = elements
          })
          legendList1.value.forEach(ite => {
            if (ite.isFlightArea) return
            let elements = []
            list.forEach(val => {
              if (ite.name == val.name && val.elements && val.elements.length > 0) {
                val.elements.forEach(element => {
                  elements.push({
                    name: element?.name,
                    id: element?.id,
                    type: element?.resource?.content?.geometry?.type,
                    lonLat: element?.resource?.content?.geometry?.coordinates,
                    resource: element?.resource,
                  })
                })
              }
            })
            ite.elements = elements
          })
        }

        const flightRes = await getFlightAreaList()
        if (flightRes && flightRes.code === 0 && flightRes.data) {
          legendList1.value.forEach(ite => {
            if (ite.isFlightArea) {
              const filteredAreas = flightRes.data.filter(area => area.type === ite.flightAreaType)
              ite.elements = filteredAreas.map(area => ({
                name: area.name,
                id: area.area_id,
                type: area.content?.geometry?.type,
                lonLat: area.content?.geometry?.coordinates,
                radius: area.content?.geometry?.radius,
                resource: {
                  content: {
                    geometry: area.content?.geometry,
                    properties: {
                      color: ite.color,
                      isFlightArea: true
                    }
                  }
                },
                raw: area
              }))
            }
          })
        }
      } catch (error) {
        console.error('获取图层数据失败:', error)
      }

      // 2. 重新应用当前选中的图层显示
      await layerShowChange()
    }

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

    function getLabelPixelOffset(idStr) {
      let hash = 0
      if (idStr) {
        const s = String(idStr)
        for (let i = 0; i < s.length; i++) {
          hash = (hash << 5) - hash + s.charCodeAt(i)
          hash |= 0
        }
      }
      const absHash = Math.abs(hash)
      // 3档纵向高度交错 (-40px, -62px, -84px)，防止相邻近点位的文字贴合在一起重叠
      const yIndex = absHash % 3
      const yOffset = -40 - yIndex * 22
      // 微小横向交错 (-10px, 0px, 10px)
      const xOffset = ((absHash % 3) - 1) * 10
      return new Cesium.Cartesian2(xOffset, yOffset)
    }

    const iconCache = new Map()

    function getMarkerIcon(color) {
      if (!color) return mapBjIcon
      if (iconCache.has(color)) {
        return iconCache.get(color)
      }

      // 将 SVG 转换为 Data URI (base64)
      const svgContent = svgTemplate.replace('{COLOR}', color)
      const base64 = btoa(unescape(encodeURIComponent(svgContent)))
      const dataUri = `data:image/svg+xml;base64,${base64}`

      iconCache.set(color, dataUri)
      return dataUri
    }

    const clusterCanvasCache = new Map()
    function getClusterIcon(count) {
      if (clusterCanvasCache.has(count)) return clusterCanvasCache.get(count)

      const canvas = document.createElement('canvas')
      canvas.width = 96
      canvas.height = 96
      const ctx = canvas.getContext('2d')

      // 1. 外部科技感半透明圆盘底盘
      ctx.beginPath()
      ctx.arc(48, 48, 38, 0, Math.PI * 2)
      const grad = ctx.createRadialGradient(48, 48, 6, 48, 48, 38)
      grad.addColorStop(0, 'rgba(10, 30, 52, 0.95)')
      grad.addColorStop(1, 'rgba(4, 14, 26, 0.92)')
      ctx.fillStyle = grad
      ctx.shadowColor = '#00f0ff'
      ctx.shadowBlur = 8
      ctx.fill()
      ctx.shadowBlur = 0

      // 外边界边框
      ctx.beginPath()
      ctx.arc(48, 48, 38, 0, Math.PI * 2)
      ctx.strokeStyle = count >= 5 ? '#00f0ff' : '#ffcc00'
      ctx.lineWidth = 2.5
      ctx.stroke()

      // 2. 左侧/中央绘制 Map Pin 标记点图标
      ctx.save()
      ctx.translate(34, 48)
      // 绘制水滴状 Pin 图标外壳
      ctx.beginPath()
      ctx.arc(0, -9, 11, Math.PI * 0.8, Math.PI * 0.2, false)
      ctx.lineTo(0, 11)
      ctx.closePath()
      ctx.fillStyle = '#ff4d4f'
      ctx.fill()
      ctx.strokeStyle = '#ffffff'
      ctx.lineWidth = 1.8
      ctx.stroke()
      // Pin 图标中心白点
      ctx.beginPath()
      ctx.arc(0, -9, 4.5, 0, Math.PI * 2)
      ctx.fillStyle = '#ffffff'
      ctx.fill()
      ctx.restore()

      // 3. 右上角绘制数量统计数字 Badge 圈
      ctx.beginPath()
      ctx.arc(63, 30, 16, 0, Math.PI * 2)
      ctx.fillStyle = '#1890ff'
      ctx.shadowColor = '#1890ff'
      ctx.shadowBlur = 6
      ctx.fill()
      ctx.strokeStyle = '#ffffff'
      ctx.lineWidth = 2
      ctx.stroke()
      ctx.shadowBlur = 0

      // 4. 在 Badge 气泡中绘制具体的统计数量数字
      ctx.font = 'bold 15px "Microsoft YaHei", sans-serif'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillStyle = '#ffffff'
      ctx.fillText(String(count), 63, 30)

      const dataUrl = canvas.toDataURL('image/png')
      clusterCanvasCache.set(count, dataUrl)
      return dataUrl
    }

    //标记点增加 (2D/3D自适应)
    function setMaker(name, lonLat, mapIcon, item) {
      if (!map.viewer || map.isDestroyed || !map.Cesium) return null

      formState.value = item
      formState.value.longitude = lonLat[0]
      formState.value.latitude = lonLat[1]

      const is2D = is2DMode()
      // 2D 模式下不放入聚类数据源，防止 2D 坐标聚合引擎隐藏点位
      const targetEntities = (!is2D && map.legendDataSource) ? map.legendDataSource.entities : map.viewer.entities
      const existing = targetEntities.getById(item.id)
      if (existing) {
        existing.show = true
        return existing
      }

      // 尝试获取动态图标
      let finalIcon = mapIcon || mapBjIcon
      const elementColor = item?.resource?.content?.properties?.color
      if (elementColor && typeof mapIcon === 'string' && (mapIcon.includes('map-icon-marker') || mapIcon.includes('svg'))) {
        finalIcon = getMarkerIcon(elementColor)
      } else if (!mapIcon) {
        finalIcon = elementColor ? getMarkerIcon(elementColor) : mapBjIcon
      }

      let maker = targetEntities.add({
        id: item.id,
        myData: item,
        description: `
          <h2>${item.name}</h2>
          <div>${item.longitude}</div>
          <div>${item.latitude}</div>
        `,
        position: Cesium.Cartesian3.fromDegrees(Number(lonLat[0]), Number(lonLat[1])),
        billboard: {
          image: finalIcon,
          width: 28,
          height: 34,
          verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
          // 2D 模式下必须使用 NONE，否则高程投影会导致点位消失
          heightReference: is2D ? Cesium.HeightReference.NONE : Cesium.HeightReference.CLAMP_TO_GROUND,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
          scaleByDistance: is2D ? undefined : new Cesium.NearFarScalar(1.0e3, 1.0, 1.0e5, 0.5)
        },
        label: {
          text: name || '',
          font: '14px sans-serif',
          fillColor: Cesium.Color.WHITE,
          showBackground: true,
          backgroundColor: Cesium.Color.BLACK.withAlpha(0.75),
          pixelOffset: getLabelPixelOffset(item.id || item.name || name),
          verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
          // 2D 模式下必须使用 NONE
          heightReference: is2D ? Cesium.HeightReference.NONE : Cesium.HeightReference.CLAMP_TO_GROUND,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
          // 2D 模式下不能限制在 30000 米，否则 2D 视角全被隐藏
          distanceDisplayCondition: is2D ? undefined : new Cesium.DistanceDisplayCondition(0, 30000)
        },
      })
      mapShowData.value.push(maker)
      return maker
    }
    //绘制折线 (2D/3D自适应)
    function setLine(name, lonLat, item) {
      if (!map.viewer || map.isDestroyed || !map.Cesium) return null

      // 折线始终直接挂载到主视图实体，避免污染聚类数据源
      const targetEntities = map.viewer.entities
      const existing = targetEntities.getById(item.id)
      if (existing) {
        existing.show = true
        return existing
      }

      const is2D = is2DMode()
      const lineColor = item?.resource?.content?.properties?.color || '#2D8CF0'

      let line = targetEntities.add({
        id: item.id,
        myData: item,
        polyline: {
          positions: map.Cesium.Cartesian3.fromDegreesArray(lonLat.flat()),
          width: 3,
          material: map.Cesium.Color.fromCssColorString(lineColor),
          // 2D 模式下 GroundPolyline 不支持，必须设为 false 才能在 2D 显示！
          clampToGround: !is2D
        },
      })
      mapShowData.value.push(line)
      return line
    }
    //绘制范围 (2D/3D自适应)
    function setArea(name, lonLat, item) {
      if (!map.viewer || map.isDestroyed || !map.Cesium) return null
      if (!lonLat || !lonLat[0] || lonLat[0].length <= 0) return null

      // 多边形始终直接挂载到主视图实体，避免污染聚类数据源
      const targetEntities = map.viewer.entities
      const existing = targetEntities.getById(item.id)
      if (existing) {
        existing.show = true
        return existing
      }

      const is2D = is2DMode()
      const areaColor = item?.resource?.content?.properties?.color || '#2D8CF0'
      const pts = map.Cesium.Cartesian3.fromDegreesArray(lonLat[0].flat())

      // 计算中心（BoundingSphere）
      const sphere = map.Cesium.BoundingSphere.fromPoints(pts)
      const carto = map.Cesium.Cartographic.fromCartesian(sphere.center)
      const centerLon = map.Cesium.Math.toDegrees(carto.longitude)
      const centerLat = map.Cesium.Math.toDegrees(carto.latitude)

      let area = targetEntities.add({
        id: item.id,
        myData: item,
        polygon: {
          hierarchy: new map.Cesium.PolygonHierarchy(pts),
          material: new map.Cesium.ColorMaterialProperty(map.Cesium.Color.fromCssColorString(areaColor).withAlpha(0.4)),
          outline: true,
          outlineColor: map.Cesium.Color.BLACK,
          outlineWidth: 10,
          // 2D 模式下 GroundPrimitive 官方不支持！2D 必须设为 NONE 并指定 height: 0，才能走通用几何管线正常绘制！
          heightReference: is2D ? Cesium.HeightReference.NONE : map.Cesium.HeightReference.CLAMP_TO_GROUND,
          height: is2D ? 0 : undefined,
          perPositionHeight: false
        },
        label: {
          text: name || '',
          font: '14px sans-serif',
          fillColor: map.Cesium.Color.WHITE,
          showBackground: true,
          backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.75),
          verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
          pixelOffset: getLabelPixelOffset(item.id || item.name || name),
          heightReference: is2D ? Cesium.HeightReference.NONE : map.Cesium.HeightReference.CLAMP_TO_GROUND,
          distanceDisplayCondition: is2D ? undefined : new Cesium.DistanceDisplayCondition(0, 40000)
        },
        position: map.Cesium.Cartesian3.fromDegrees(centerLon, centerLat),
      })
      mapShowData.value.push(area)
      return area
    }
    function onLineClick(e) {
      console.log(e, '惦记上hi见')
    }

    const dronePosition = ref()
    const initHeight = ref(0)
    const droneOrientation = ref()

    watch(() => props.osdInfo, val => {
      // 检查组件是否已销毁
      if (map.isDestroyed || !map.Cesium) return

      // 更新位置：绕圈飞行
      if (props.osdInfo?.longitude !== undefined) {
        if (initHeight.value === 0) {
          initHeight.value = props.osdInfo?.height
        }
        dronePosition.value = [
          props.osdInfo?.longitude,
          props.osdInfo?.latitude,
          props.osdInfo?.height,
        ]

        const heading = props.osdInfo?.attitude_head
        const pitch = 0
        const roll = 0

        const hpr = new map.Cesium.HeadingPitchRoll(map.Cesium.Math.toRadians(heading - 90), map.Cesium.Math.toRadians(pitch),
          map.Cesium.Math.toRadians(roll))

        // 将经纬高转为笛卡尔坐标
        const position = map.Cesium.Cartesian3.fromDegrees(...dronePosition.value)

        // 计算朝向四元数
        droneOrientation.value = map.Cesium.Transforms.headingPitchRollQuaternion(position, hpr)

      }
    }, {
      immediate: true,
      // deep: true,
    })

    // 使用 shallowRef 缓存材质，避免每次重新计算导致闪烁
    const triColorGradientMaterial = shallowRef(null)
    let triColorGradientMaterialInitialized = false

    // 创建三色渐变材质的函数（只执行一次）
    function initTriColorGradientMaterial() {
      if (triColorGradientMaterialInitialized || map.isDestroyed || !map.Cesium) return

      triColorGradientMaterialInitialized = true
      console.log('[triColorGradientMaterial] 初始化材质，只执行一次')

      const canvas = document.createElement('canvas')
      canvas.width = 512
      canvas.height = 512
      const ctx = canvas.getContext('2d')

      const centerX = 256
      const centerY = 256
      const maxRadius = 256

      // 创建径向渐变
      const gradient = ctx.createRadialGradient(centerX, centerY, 0, centerX, centerY, maxRadius)

      // 0-30% 区域（0-3km）：绿色
      gradient.addColorStop(0, 'rgba(0, 255, 0, 0.2)')
      gradient.addColorStop(0.3, 'rgba(0, 255, 0, 0.2)')

      // 30-50% 区域（3-5km）：黄色
      gradient.addColorStop(0.3, 'rgba(255, 255, 0, 0.2)')
      gradient.addColorStop(0.5, 'rgba(255, 255, 0, 0.2)')

      // 50-100% 区域（5-10km）：红色
      gradient.addColorStop(0.5, 'rgba(255, 0, 0, 0.2)')
      gradient.addColorStop(1, 'rgba(255, 0, 0, 0.2)')

      ctx.fillStyle = gradient
      ctx.fillRect(0, 0, 512, 512)

      triColorGradientMaterial.value = new map.Cesium.ImageMaterialProperty({
        image: canvas,
        transparent: true
      })
    }

    // 监听 Cesium 就绪状态，初始化材质
    watch(
      () => map.Cesium,
      (cesium) => {
        if (cesium && !triColorGradientMaterialInitialized) {
          initTriColorGradientMaterial()
        }
      },
      { immediate: true }
    )


    // 组件卸载时清理资源，防止异步操作访问已销毁的 Cesium 对象
    onBeforeUnmount(() => {
      console.log('DjiCesium 组件正在卸载，开始清理资源...')

      // 清理 3D 建模加载的原生 primitive
      tilesetPrimitivesMap.forEach((tileset) => {
        if (tileset && map.viewer && map.viewer.scene && !map.viewer.isDestroyed?.()) {
          try {
            map.viewer.scene.primitives.remove(tileset)
          } catch (e) {
            console.warn('清理 3D 建模 primitive 时出错:', e)
          }
        }
      })
      tilesetPrimitivesMap.clear()

      // 设置销毁标志位
      map.isDestroyed = true

      // 先隐藏 viewer，防止闪烁
      show.value = false

      // 清理 ScreenSpaceEventHandler
      if (map.handler) {
        try {
          map.handler.destroy()
        } catch (e) {
          console.warn('清理 handler 时出错:', e)
        }
        map.handler = null
      }

      // 清空引用，防止后续访问
      // 不要在这里销毁 viewer，让 vue-cesium 的 vc-viewer 组件自己处理
      map.viewer = null
      map.Cesium = null

      console.log('DjiCesium 组件资源清理完成')
    })

    // 组件初始化时拉取图层数据
    getMapLayer()

    const clearFlightPathLine = () => {
      flightPathLine.value = null
    }

    const getTerrainHeight = async (lon, lat) => {
      if (!map.viewer || !map.Cesium) return 0
      try {
        const cartographic = map.Cesium.Cartographic.fromDegrees(lon, lat)
        const provider = map.viewer.terrainProvider

        // 无地形数据 (椭球体)，直接尝试获取 Globe 高度（可能为0）
        if (!provider || provider instanceof map.Cesium.EllipsoidTerrainProvider) {
          console.log('无地形数据 (椭球体)，直接尝试获取 Globe 高度')
          const h = map.viewer.scene.globe.getHeight(cartographic)
          return h || 0
        }

        // 确保 provider 已就绪
        if (provider.readyPromise) {
          await provider.readyPromise
        }

        // 方案A: 尝试采样最详细地形
        try {
          const updatedPositions = await map.Cesium.sampleTerrainMostDetailed(provider, [cartographic])
          if (updatedPositions?.[0]?.height !== undefined) {
            return updatedPositions[0].height
          }
        } catch (e) {
          // console.warn('方案A (MostDetailed) 获取失败 (预期内):', e.message || e)
        }

        // 方案B: 降级尝试指定级别 (Level 11, ~30m Res) 采样
        try {
          const updatedPositions = await map.Cesium.sampleTerrain(provider, 11, [cartographic])
          if (updatedPositions?.[0]?.height !== undefined) {
            return updatedPositions[0].height
          }
        } catch (e) {
          // console.warn('方案B (Level 11) 获取失败:', e.message || e)
        }

        // 方案C: 最后的兜底，使用此时 Globe 加载的高度
        const globeHeight = map.viewer.scene.globe.getHeight(cartographic)
        return globeHeight || 0

      } catch (e) {
        console.error('获取地面高度失败:', e)
        return 0
      }
    }

    defineExpose({
      cesiumContainerRef,
      viewerRef,
      map,
      tryFlyToDock,
      flyToCoordinate,
      getTerrainHeight,
      clearFlightPathLine,
      legendList,
      legendList1,
      tilesetsList,
      totalTilesetCount,
      searchTilesetKeyword,
      currentTilesetPage,
      tilesetPageSize,
      totalTilesetPages,
      fetchTilesetsList,
      handleTilesetToggle,
      drawType,
      startDraw,
      syncNativeEntities,
      syncDockEntities,
      syncDroneEntities
    })
</script>

<style lang="scss" scoped>
.ctrl {
  position: absolute;
  left: 1.55rem;
  width: 1.5rem;
  top: 3rem;
  z-index: 999;
  border-radius: 2px 2px 2px 2px;
  background-color: #fff;
  box-shadow: 0 0 4px rgba(0, 0, 0, .6);

  button {
    display: inline-block;
    width: 1.625rem;
    height: 1.625rem;
    cursor: pointer;
    border-bottom: 1px solid #ccc;
    display: flex;
    justify-content: center;
    align-items: center;
    border-radius: 2px 2px 2px 2px;

    &:hover {
      border: 2px solid #2D8CF0;
    }

    &:active {
      border: 2px solid #2D8CF0;
    }

    img {
      width: 1rem;
      height: 1rem;
      margin-left: -2px;
    }
  }
}

pre {
  max-height: 200px;
  overflow: auto;
}

.icon-del {
  font-size: 20px;
  color: #D6E5FB;
  cursor: pointer;
  transition: color 0.3s;
  margin-left: 10px;
  margin-top: -4px;

  &:hover {
    color: #5babcb;
  }
}

.color-box {
  display: flex;
  align-items: center;

  span {
    display: inline-block;
    width: 20px;
    height: 20px;
    margin-right: 6px;
    border-radius: 2px;
    cursor: pointer;
    border: 1px solid transparent;
    color: #fff;
    text-align: center;
    line-height: 18px;
    font-size: 16px;
    font-weight: bold;

    &:hover {
      border-color: #fff;
    }

    &:nth-of-type(1) {
      background-color: #2D8CF0;
    }

    &:nth-of-type(2) {
      background-color: #19BE6B;
    }

    &:nth-of-type(3) {
      background-color: #FFBB00;
    }

    &:nth-of-type(4) {
      background-color: #E23C39;
    }

    &:nth-of-type(5) {
      background-color: #B620E0;
    }
  }
}

.img-box {
  display: flex;
  align-items: center;

  >div {
    width: 24px;
    height: 24px;
    margin-right: 6px;
    border-radius: 2px;
    cursor: pointer;
    border: 2px solid transparent;
    display: flex;
    align-items: center;
    justify-content: center;

    &:hover {
      border-color: #408ece;
    }

    img {
      width: 18px;
      height: 18px;



    }
  }

  .action {
    background-color: #fff;
  }

}

.map-legend {
  position: absolute;
  left: .25rem;
  bottom: .25rem;
  display: flex;
  align-items: flex-end;

  .map-legend-item {
    background-color: #fff;
    border-radius: 2px;
    box-shadow: 0 0 4px rgba(0, 0, 0, .6);
    padding: 6px;
    margin-right: 4px;
  }

  .tileset-legend-module {
    min-width: 145px;
    max-width: 185px;

    .tileset-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: bold;
      font-size: 0.6rem;
      border-bottom: 1px solid #eee;
      margin-bottom: 6px;
      padding-bottom: 2px;
      color: #333;

      .count-tag {
        background: #e6f7ff;
        color: #1890ff;
        font-size: 10px;
        border-radius: 10px;
        padding: 0 5px;
        font-weight: normal;
      }
    }

    .tileset-search {
      margin-bottom: 6px;

      :deep(.ant-input-affix-wrapper) {
        background: #f8fafc !important;
        border: 1px solid #d1d5db !important;
        border-radius: 4px !important;
        padding: 2px 6px !important;
        box-shadow: none !important;
        display: flex !important;
        align-items: center !important;

        &:hover, &:focus, &.ant-input-affix-wrapper-focused {
          border-color: #1890ff !important;
          background: #fff !important;
        }

        .ant-input {
          background: transparent !important;
          color: #1e293b !important;
          border: none !important;
          padding: 0 !important;
          font-size: 11px !important;
          line-height: 1.5 !important;
          box-shadow: none !important;

          &::placeholder {
            color: #94a3b8 !important;
          }
        }

        .ant-input-prefix {
          margin-right: 4px;
          display: flex;
          align-items: center;
        }

        .ant-input-clear-icon {
          color: #94a3b8 !important;
          font-size: 11px;
        }
      }
    }

    .tileset-list {
      max-height: 140px;
      overflow-y: auto;

      .tileset-item {
        display: flex;
        align-items: center;
        justify-content: space-between;
        height: 1.5rem;
        padding: 0 2px;
        color: #000;
        font-size: 0.55rem;

        .tileset-name {
          display: flex;
          align-items: center;
          overflow: hidden;
          margin-right: 6px;
          flex: 1;

          .tileset-dot {
            display: inline-block;
            width: 6px;
            height: 6px;
            background-color: #1890ff;
            border-radius: 50%;
            margin-right: 6px;
            flex-shrink: 0;
          }

          .tileset-label {
            display: inline-block;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            font-size: 0.55rem;
            color: #1e293b;
            flex: 1;
            max-width: 100px;
          }
        }
      }
    }

    .tileset-empty {
      font-size: 11px;
      color: #999;
      text-align: center;
      padding: 8px 0;
    }

    .tileset-pagination {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-top: 1px solid #eee;
      margin-top: 6px;
      padding-top: 4px;

      .pager-text {
        font-size: 11px;
        color: #666;
      }

      .pager-btn {
        padding: 0 4px;
        height: 20px;
        line-height: 20px;
        color: #1890ff;

        &:disabled {
          color: #ccc;
        }
      }
    }
  }

  .item {
    color: #000;
    font-size: .55rem;
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 1.4rem;

    img {
      width: 1rem;
      height: 1rem;
      margin-right: 4px;
    }

    .name {
      display: flex;
      align-items: center;
      margin-right: .375rem;

      span {
        display: inline-block;
        width: 1rem;
        height: 1rem;
        margin-right: 4px
      }
    }
  }
}

.context-menu {
  position: absolute;
  background-color: black;
  border: 1px solid #ccc;
  color: white;
  border-radius: 8px;
  padding: 10px 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
  font-size: 13px;
  line-height: 1.5;
  z-index: 999;
}

.context-menu button {
  margin-top: 6px;
  background: #1677ff;
  color: white;
  border: none;
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
}

.context-menu button:hover {
  background: #4096ff;
}

.event-bubble-popup {
  position: absolute;
  transform: translate(-50%, -100%);
  margin-top: -45px;
  z-index: 1000;
  pointer-events: auto;
}
</style>
