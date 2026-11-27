<template>
    <div class="layer-map-editor relative h-full w-full">
        <div ref="cesiumContainerRef" class="h-full w-full"></div>

        <!-- 绘制/编辑工具栏 -->
        <div class="draw-toolbar" v-if="isDrawing || isEditing">
            <div class="toolbar-title">{{ getDrawingTitle() }}</div>
            <div class="toolbar-hint">{{ getDrawingHint() }}</div>
            <div class="toolbar-actions">
                <a-button size="small" @click="cancelDrawing">取消</a-button>
                <a-button size="small" type="primary" @click="confirmDrawing" :disabled="!canConfirmDrawing">{{
                    isEditing ? '保存修改' : '确认' }}</a-button>
            </div>
        </div>

        <!-- 元素信息面板 -->
        <div class="element-info-panel" v-if="selectedElement && !isDrawing">
            <div class="panel-header">
                <span class="panel-title">{{ selectedElement.name }}</span>
                <CloseOutlined class="close-icon" @click="selectedElement = null" />
            </div>
            <div class="panel-content">
                <div class="info-row">
                    <span class="label">类型:</span>
                    <span class="value">{{ getElementTypeName(selectedElement.resource?.type) }}</span>
                </div>
                <div class="info-row" v-if="selectedElement.resource?.type === 0">
                    <span class="label">位置:</span>
                    <span class="value">
                        {{ getElementCoordinates(selectedElement) }}
                    </span>
                </div>
            </div>
            <div class="panel-actions">
                <a-button v-if="!readonly" size="small" type="primary" @click="$emit('edit', selectedElement)">
                    <EditOutlined /> 编辑
                </a-button>
                <a-button v-if="!readonly" size="small" danger @click="$emit('delete', selectedElement)">
                    <DeleteOutlined /> 删除
                </a-button>
                <span v-if="readonly" class="text-gray-400 text-xs">该分组已锁定，仅可查看</span>
            </div>
        </div>

        <!-- 加载提示 -->
        <div class="loading-overlay" v-if="!show">
            <a-spin tip="地图加载中..." />
        </div>

        <!-- 调试面板 - 显示鼠标经纬度 -->
        <div class="debug-panel" v-if="show">
            <div class="debug-title">🔍 调试信息</div>
            <div class="debug-row">
                <span class="label">经度 (Lon):</span>
                <span class="value">{{ mousePosition.longitude }}</span>
            </div>
            <div class="debug-row">
                <span class="label">纬度 (Lat):</span>
                <span class="value">{{ mousePosition.latitude }}</span>
            </div>
            <div class="debug-hint">
                提示: 国控点经度只有小数点后4位可能导致定位偏差
            </div>
        </div>
    </div>
</template>

<script setup>
import { watch, ref, reactive, computed, onBeforeUnmount, onMounted, nextTick, markRaw, shallowRef } from 'vue'
import * as Cesium from 'cesium'
import { CloseOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import mapBjIcon from '@/assets/airControl/map/map-icon-marker.svg'
import { hexToRgba } from '@/utils/doubleLayerIcon'
import TiandituGeocoderService from '@/services/TiandituGeocoderService'
import { CURRENT_CONFIG } from '@/api/http/config'

const props = defineProps({
    elements: {
        type: Array,
        default: () => []
    },
    drawMode: {
        type: String,
        default: null
    },
    editingElement: {
        type: Object,
        default: null
    },
    defaultColor: {
        type: String,
        default: '#3366FF'
    },
    readonly: {
        type: Boolean,
        default: false
    },
    draftCoordinates: {
        type: Array,
        default: null
    }
})

const emit = defineEmits([
    'element-created',
    'element-updated',
    'element-clicked',
    'edit',
    'delete',
    'draw-complete',
    'draw-cancel',
    'edit-confirmed'
])
        // 地图相关
        const cesiumContainerRef = ref(null)
        const viewerContainer = ref(null)
        const vcViewer = ref(null)
        const show = ref(false)
        const accessToken = ref(CURRENT_CONFIG.cesiumIonToken)
        const markerIcon = mapBjIcon

        const map = reactive({
            viewer: null,
            Cesium: null,
            handler: null,
            isDestroyed: false
        })

        // 绘制状态
        const drawingCoords = ref([])
        const drawingPoints = ref([])
        const drawingLine = ref(null)
        const drawingPolygon = ref(null)
        const mousePreviewLine = ref(null)
        const selectedElement = ref(null)

        // 调试 - 鼠标位置
        const mousePosition = reactive({
            longitude: '-- 移动鼠标至地图 --',
            latitude: '-- 移动鼠标至地图 --'
        })

        // 编辑模式状态
        const isEditing = ref(false)
        const editingCoords = ref([])
        const editingType = ref(null) // 'point', 'line', 'polygon'
        const draggingPointIndex = ref(-1)
        const editControlPoints = ref([])
        const editPreviewLine = ref(null)
        const editPreviewPolygon = ref(null)

        // 临时编辑状态 - 保存修改后但未提交的元素数据
        const pendingEditElement = ref(null) // { id, coordinates, type }
        const originalEditCoords = ref(null) // 保存原始坐标用于恢复

        // 用于强制刷新地图实体的 key，当需要强制 Vue-Cesium 重新渲染时递增
        const elementRefreshKey = ref(0)

        // 计算属性
        const isDrawing = computed(() => props.drawMode !== null)

        const currentColorWithAlpha = computed(() => hexToRgba(props.defaultColor, 0.4))

        const pointElements = computed(() => {
            // 编辑模式时隐藏正在编辑的元素
            const filtered = props.elements.filter(e => {
                if (isEditing.value && props.editingElement?.id === e.id) return false
                // 如果有待确认的编辑元素，也隐藏原始元素
                if (pendingEditElement.value && pendingEditElement.value.id === e.id) return false
                return e.resource?.type === 0
            })
            // 如果有待确认的点元素编辑，添加临时显示
            if (pendingEditElement.value && pendingEditElement.value.type === 'point' && !isEditing.value) {
                const pendingEl = props.elements.find(e => e.id === pendingEditElement.value.id)
                if (pendingEl) {
                    // 创建临时元素副本，使用新坐标
                    const tempElement = JSON.parse(JSON.stringify(pendingEl))
                    tempElement.resource.content.geometry.coordinates = pendingEditElement.value.coordinates
                    filtered.push(tempElement)
                }
            }
            return filtered
        })

        const lineElements = computed(() => {
            let filtered
            if (isEditing.value && props.editingElement?.id) {
                filtered = props.elements.filter(e => e.resource?.type === 1 && e.id !== props.editingElement.id)
            } else {
                filtered = props.elements.filter(e => {
                    if (pendingEditElement.value && pendingEditElement.value.id === e.id) return false
                    return e.resource?.type === 1
                })
            }
            // 如果有待确认的线元素编辑，添加临时显示
            if (pendingEditElement.value && pendingEditElement.value.type === 'line' && !isEditing.value) {
                const pendingEl = props.elements.find(e => e.id === pendingEditElement.value.id)
                if (pendingEl) {
                    const tempElement = JSON.parse(JSON.stringify(pendingEl))
                    tempElement.resource.content.geometry.coordinates = pendingEditElement.value.coordinates
                    filtered.push(tempElement)
                }
            }
            return filtered
        })

        const polygonElements = computed(() => {
            let filtered
            if (isEditing.value && props.editingElement?.id) {
                filtered = props.elements.filter(e => e.resource?.type === 2 && e.id !== props.editingElement.id)
            } else {
                filtered = props.elements.filter(e => {
                    if (pendingEditElement.value && pendingEditElement.value.id === e.id) return false
                    return e.resource?.type === 2
                })
            }
            // 如果有待确认的面元素编辑，添加临时显示
            if (pendingEditElement.value && pendingEditElement.value.type === 'polygon' && !isEditing.value) {
                const pendingEl = props.elements.find(e => e.id === pendingEditElement.value.id)
                if (pendingEl) {
                    const tempElement = JSON.parse(JSON.stringify(pendingEl))
                    tempElement.resource.content.geometry.coordinates = pendingEditElement.value.coordinates
                    filtered.push(tempElement)
                }
            }
            return filtered
        })

        const canConfirmDrawing = computed(() => {
            if (isEditing.value) {
                if (editingType.value === 'point') return editingCoords.value.length === 1
                if (editingType.value === 'line') return editingCoords.value.length >= 2
                if (editingType.value === 'polygon') return editingCoords.value.length >= 3
                return false
            }
            if (props.drawMode === 'point') {
                return drawingCoords.value.length === 1
            } else if (props.drawMode === 'line') {
                return drawingCoords.value.length >= 2
            } else if (props.drawMode === 'polygon') {
                return drawingCoords.value.length >= 3
            }
            return false
        })

        // 编辑预览线是否可见
        const editPreviewLineVisible = computed(() => {
            return isEditing.value && editingType.value === 'line' && editingCoords.value.length >= 2
        })

        // 编辑预览面是否可见
        const editPreviewPolygonVisible = computed(() => {
            return isEditing.value && editingType.value === 'polygon' && editingCoords.value.length >= 3
        })

        // 获取编辑控制点位置（返回缓存的 CallbackProperty）
        function getEditPointPosition(index) {
            const point = editControlPoints.value[index]
            if (point && point.positionCallback) {
                return point.positionCallback
            }
            return [0, 0]
        }

        // 编辑线的动态位置回调
        const editLinePositionsCallback = computed(() => {
            if (!map.Cesium) return []
            return new map.Cesium.CallbackProperty(() => {
                if (editingCoords.value.length >= 2) {
                    return map.Cesium.Cartesian3.fromDegreesArray(editingCoords.value.flat())
                }
                return []
            }, false)
        })

        // 编辑面的动态层级回调
        const editPolygonHierarchyCallback = computed(() => {
            if (!map.Cesium) return { positions: [] }
            return new map.Cesium.CallbackProperty(() => {
                if (editingCoords.value.length >= 3) {
                    return {
                        positions: map.Cesium.Cartesian3.fromDegreesArray(editingCoords.value.flat())
                    }
                }
                return { positions: [] }
            }, false)
        })

        // 监听绘制模式变化
        watch(() => props.drawMode, (newMode, oldMode) => {
            if (newMode && !oldMode) {
                startDrawingMode()
            } else if (!newMode && oldMode) {
                endDrawingMode()
            }
        })

        // 监听编辑元素变化
        watch(() => props.editingElement, (newElement) => {
            if (newElement) {
                prepareEditElement(newElement)
            }
        })

        const initNativeCesium = () => {
            if (!cesiumContainerRef.value || map.isDestroyed) return

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

            const _viewer = new Cesium.Viewer(cesiumContainerRef.value, {
                animation: false,
                timeline: false,
                fullscreenButton: false,
                infoBox: false,
                selectionIndicator: false,
                skyAtmosphere: false,
                skyBox: false,
                sceneModePicker: false,
                showCredit: false,
                targetFrameRate: 60,
                geocoder: true,
                baseLayerPicker: false,
                homeButton: false,
                navigationHelpButton: false,
                projectionPicker: false,
                baseLayer: new Cesium.ImageryLayer(imgProvider)
            })

            _viewer.imageryLayers.addImageryProvider(ciaProvider)

            // 配置天地图 Geocoder 搜索服务
            const geocoderService = new TiandituGeocoderService({
                key: CURRENT_CONFIG.tiandituToken,
                getLocalPoints: () => {
                    if (!Array.isArray(props.elements)) return []
                    return props.elements.map(el => {
                        const coords = el.resource?.content?.geometry?.coordinates
                        if (coords && coords.length >= 2) {
                            return {
                                name: el.name || '标注点',
                                longitude: Number(coords[0]),
                                latitude: Number(coords[1]),
                                address: '图层标注'
                            }
                        }
                        return null
                    }).filter(Boolean)
                }
            })

            const originGeocode = geocoderService.geocode.bind(geocoderService)
            let debounceTimeout = null
            geocoderService.geocode = (input, options) => {
                if (debounceTimeout) {
                    clearTimeout(debounceTimeout)
                }
                return new Promise((resolve, reject) => {
                    debounceTimeout = setTimeout(() => {
                        originGeocode(input, options)
                            .then(resolve)
                            .catch(reject)
                    }, 400)
                })
            }

            if (_viewer.geocoder && _viewer.geocoder.viewModel) {
                _viewer.geocoder.viewModel._geocoderServices = [geocoderService]
                if (_viewer.geocoder.container) {
                    const setChinesePlaceholder = () => {
                        const inputEl = _viewer.geocoder.container.querySelector('.cesium-geocoder-input') || _viewer.geocoder.container.querySelector('input')
                        if (inputEl) {
                            inputEl.placeholder = '搜索地址或地名...'
                        }
                    }
                    setChinesePlaceholder()
                    setTimeout(setChinesePlaceholder, 200)
                }
            }

            const rawViewer = markRaw(_viewer)
            map.viewer = rawViewer
            map.Cesium = Cesium

            onViewerReady({ Cesium, viewer: rawViewer })
        }

        // 延迟初始化地图
        onMounted(() => {
            nextTick(() => {
                setTimeout(() => {
                    if (!map.isDestroyed) {
                        show.value = true
                        initNativeCesium()
                    }
                }, 100)
            })
        })

        onBeforeUnmount(() => {
            map.isDestroyed = true
            if (map.handler) {
                map.handler.destroy()
                map.handler = null
            }
        })

        // 缓存原生 Cesium 实体 Map
        const elementEntitiesMap = new Map()
        const tempDrawingEntities = []

        // 同步图层元素至 Cesium 地图实体
        function syncElementsToMap() {
            if (!map.viewer || !map.Cesium) return

            const currentIds = new Set(props.elements.map(e => e.id))

            // 清理已删除元素的实体
            elementEntitiesMap.forEach((entity, id) => {
                if (!currentIds.has(id)) {
                    map.viewer.entities.remove(entity)
                    elementEntitiesMap.delete(id)
                }
            })

            props.elements.forEach(element => {
                const coords = element.resource?.content?.geometry?.coordinates
                if (!coords) return

                const elementColor = element.resource?.content?.properties?.color || props.defaultColor || '#3366FF'

                let entity = elementEntitiesMap.get(element.id)

                if (isEditing.value && props.editingElement?.id === element.id) {
                    if (entity) entity.show = false
                    return
                }

                if (!entity) {
                    if (element.resource?.type === 0) { // 点 (Point)
                        let finalIcon = mapBjIcon
                        if (elementColor) {
                            const svgContent = `
<svg width="64" height="64" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <filter id="shadow" x="0" y="0" width="64" height="64" filterUnits="userSpaceOnUse">
      <feGaussianBlur in="SourceAlpha" stdDeviation="2"/>
      <feOffset dx="0" dy="2" result="offsetblur"/>
      <feComponentTransfer><feFuncA type="linear" slope="0.5"/></feComponentTransfer>
      <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
  </defs>
  <ellipse cx="32" cy="58" rx="10" ry="3" fill="#000000" fill-opacity="0.3" />
  <path d="M32 4 C18.745 4 8 14.745 8 28 C8 42 32 60 32 60 C32 60 56 42 56 28 C56 14.745 45.255 4 32 4Z" fill="${elementColor}" filter="url(#shadow)" stroke="#ffffff" stroke-width="1.5"/>
  <circle cx="32" cy="28" r="10" fill="white" fill-opacity="0.9"/>
</svg>`
                            const base64 = btoa(unescape(encodeURIComponent(svgContent)))
                            finalIcon = `data:image/svg+xml;base64,${base64}`
                        }

                        entity = map.viewer.entities.add({
                            id: element.id,
                            name: element.name,
                            position: map.Cesium.Cartesian3.fromDegrees(coords[0], coords[1]),
                            billboard: {
                                image: finalIcon,
                                width: 28,
                                height: 34,
                                verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
                                heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
                                disableDepthTestDistance: Number.POSITIVE_INFINITY
                            },
                            label: {
                                text: element.name,
                                font: '14px sans-serif',
                                fillColor: map.Cesium.Color.WHITE,
                                showBackground: true,
                                backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.6),
                                pixelOffset: new map.Cesium.Cartesian2(0, -40),
                                verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
                                heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
                                disableDepthTestDistance: Number.POSITIVE_INFINITY
                            }
                        })
                    } else if (element.resource?.type === 1) { // 折线 (LineString)
                        if (coords.length > 0) {
                            const flatCoords = coords.flat()
                            entity = map.viewer.entities.add({
                                id: element.id,
                                name: element.name,
                                polyline: {
                                    positions: map.Cesium.Cartesian3.fromDegreesArray(flatCoords),
                                    width: 3,
                                    material: map.Cesium.Color.fromCssColorString(elementColor),
                                    clampToGround: true
                                }
                            })
                        }
                    } else if (element.resource?.type === 2) { // 多边形 (Polygon)
                        const polyCoords = coords[0] || []
                        if (polyCoords.length > 0) {
                            const flatCoords = polyCoords.flat()
                            const pts = map.Cesium.Cartesian3.fromDegreesArray(flatCoords)
                            const sphere = map.Cesium.BoundingSphere.fromPoints(pts)
                            const carto = map.Cesium.Cartographic.fromCartesian(sphere.center)
                            const centerLon = map.Cesium.Math.toDegrees(carto.longitude)
                            const centerLat = map.Cesium.Math.toDegrees(carto.latitude)

                            entity = map.viewer.entities.add({
                                id: element.id,
                                name: element.name,
                                polygon: {
                                    hierarchy: new map.Cesium.PolygonHierarchy(pts),
                                    material: new map.Cesium.ColorMaterialProperty(map.Cesium.Color.fromCssColorString(elementColor).withAlpha(0.4)),
                                    outline: true,
                                    outlineColor: map.Cesium.Color.BLACK,
                                    outlineWidth: 2,
                                    heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND
                                },
                                label: {
                                    text: element.name,
                                    font: '14px sans-serif',
                                    fillColor: map.Cesium.Color.WHITE,
                                    showBackground: true,
                                    backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.6),
                                    verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
                                    pixelOffset: new map.Cesium.Cartesian2(0, -20),
                                    heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND
                                },
                                position: map.Cesium.Cartesian3.fromDegrees(centerLon, centerLat)
                            })
                        }
                    }
                    if (entity) {
                        elementEntitiesMap.set(element.id, entity)
                    }
                } else {
                    entity.show = true
                }
            })
        }

        const draftEntity = shallowRef(null)

        // 刷新草稿/待确认元素的预览实体 (用于新增/修改坐标时的即时地图回显)
        function updateDraftEntity() {
            if (!map.viewer || !map.Cesium) return

            if (draftEntity.value) {
                try {
                    map.viewer.entities.remove(draftEntity.value)
                } catch (e) {}
                draftEntity.value = null
            }

            const coords = props.draftCoordinates
            if (!coords || !Array.isArray(coords) || coords.length === 0) return

            const currentColor = props.defaultColor || '#3366FF'

            // 点类型草稿 [lon, lat]
            if (typeof coords[0] === 'number' && typeof coords[1] === 'number' && coords[0] !== 0 && coords[1] !== 0) {
                let finalIcon = mapBjIcon
                if (currentColor) {
                    const svgContent = `
<svg width="64" height="64" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <filter id="shadow" x="0" y="0" width="64" height="64" filterUnits="userSpaceOnUse">
      <feGaussianBlur in="SourceAlpha" stdDeviation="2"/>
      <feOffset dx="0" dy="2" result="offsetblur"/>
      <feComponentTransfer><feFuncA type="linear" slope="0.5"/></feComponentTransfer>
      <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
  </defs>
  <ellipse cx="32" cy="58" rx="10" ry="3" fill="#000000" fill-opacity="0.3" />
  <path d="M32 4 C18.745 4 8 14.745 8 28 C8 42 32 60 32 60 C32 60 56 42 56 28 C56 14.745 45.255 4 32 4Z" fill="${currentColor}" filter="url(#shadow)" stroke="#ffffff" stroke-width="1.5"/>
  <circle cx="32" cy="28" r="10" fill="white" fill-opacity="0.9"/>
</svg>`
                    const base64 = btoa(unescape(encodeURIComponent(svgContent)))
                    finalIcon = `data:image/svg+xml;base64,${base64}`
                }

                draftEntity.value = map.viewer.entities.add({
                    position: map.Cesium.Cartesian3.fromDegrees(coords[0], coords[1]),
                    billboard: {
                        image: finalIcon,
                        width: 28,
                        height: 34,
                        verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
                        heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
                        disableDepthTestDistance: Number.POSITIVE_INFINITY
                    }
                })
            }
        }

        // 监听 draftCoordinates 变化，即时同步点坐标回显
        watch([() => props.draftCoordinates, () => props.defaultColor], () => {
            updateDraftEntity()
        }, { deep: true, immediate: true })

        // 监听 elements 数据自动渲染
        watch(() => props.elements, () => {
            syncElementsToMap()
        }, { deep: true, immediate: true })

        // 地图就绪回调
        function onViewerReady({ Cesium, viewer }) {
            console.log('LayerMapEditor viewer is loaded.')
            map.Cesium = Cesium
            map.viewer = viewer

            // 配置地图
            viewer.scene.pickTranslucentDepth = true
            viewer.terrainProvider = new Cesium.EllipsoidTerrainProvider()
            viewer.scene.globe.depthTestAgainstTerrain = false

            // 初始化 ScreenSpaceEventHandler 处理鼠标点击与拖拽
            map.handler = new Cesium.ScreenSpaceEventHandler(viewer.canvas)

            // 1. 左键点击事件注册
            map.handler.setInputAction((e) => {
                onLeftClick(e)
            }, Cesium.ScreenSpaceEventType.LEFT_CLICK)

            // 2. 右键点击事件注册
            map.handler.setInputAction(() => {
                onRightClick()
            }, Cesium.ScreenSpaceEventType.RIGHT_CLICK)

            // 3. 鼠标移动事件注册
            map.handler.setInputAction((movement) => {
                const ray = viewer.camera.getPickRay(movement.endPosition)
                let cartesian = viewer.scene.globe.pick(ray, viewer.scene)

                if (!cartesian) {
                    cartesian = viewer.scene.camera.pickEllipsoid(movement.endPosition, viewer.scene.globe.ellipsoid)
                }

                if (cartesian) {
                    const carto = Cesium.Cartographic.fromCartesian(cartesian)
                    mousePosition.longitude = Cesium.Math.toDegrees(carto.longitude).toFixed(10)
                    mousePosition.latitude = Cesium.Math.toDegrees(carto.latitude).toFixed(10)
                }
            }, Cesium.ScreenSpaceEventType.MOUSE_MOVE)

            // 同步渲染图层元素实体与草稿实体
            syncElementsToMap()
            updateDraftEntity()

            // 如果有元素，飞到第一个元素位置
            if (props.elements.length > 0) {
                const firstElement = props.elements[0]
                flyToElement(firstElement)
            } else {
                // 默认飞到重庆区域
                viewer.camera.flyTo({
                    destination: Cesium.Cartesian3.fromDegrees(106.11, 29.80, 100000),
                    duration: 1
                })
            }
        }

        // 飞到元素位置
        function flyToElement(element) {
            console.log('element', element)
            if (!map.viewer || !map.Cesium) return

            const coords = element.resource?.content?.geometry?.coordinates
            if (!coords) return

            let lon, lat
            let cameraHeight = 10000 // 默认相机高度（米）

            if (element.resource?.type === 0) {
                // 点类型：直接使用坐标
                lon = coords[0]
                lat = coords[1]
                cameraHeight = 10000
            } else if (element.resource?.type === 1 || element.resource?.type === 2) {
                // 线/面类型：计算中心点和范围
                const flatCoords = element.resource?.type === 1 ? coords : coords[0]
                if (flatCoords && flatCoords.length > 0) {
                    // 计算中心点
                    lon = flatCoords.reduce((sum, c) => sum + c[0], 0) / flatCoords.length
                    lat = flatCoords.reduce((sum, c) => sum + c[1], 0) / flatCoords.length

                    // 计算元素的地理范围，用于确定合适的相机高度
                    const minLon = Math.min(...flatCoords.map(c => c[0]))
                    const maxLon = Math.max(...flatCoords.map(c => c[0]))
                    const minLat = Math.min(...flatCoords.map(c => c[1]))
                    const maxLat = Math.max(...flatCoords.map(c => c[1]))

                    // 计算范围（度数）
                    const lonRange = maxLon - minLon
                    const latRange = maxLat - minLat
                    const maxRange = Math.max(lonRange, latRange)

                    // 根据范围动态计算相机高度
                    // 1度约等于111公里，相机高度取范围的1.5倍左右，最小5000米，最大50000米
                    cameraHeight = Math.max(5000, Math.min(50000, maxRange * 111000 * 1.5))
                } else {
                    return
                }
            } else {
                return
            }

            // 飞到目标位置，设置相机朝向：垂直向下俯视目标点，确保目标居中
            map.viewer.camera.flyTo({
                destination: map.Cesium.Cartesian3.fromDegrees(lon, lat, cameraHeight),
                orientation: {
                    heading: map.Cesium.Math.toRadians(0), // 朝向北方
                    pitch: map.Cesium.Math.toRadians(-90), // 垂直向下看
                    roll: 0
                },
                duration: 1.5
            })
        }

        // 飞到指定 Cesium Cartesian3 目标位置
        function flyToDestination(destination, duration = 1.5) {
            if (!map.viewer || !map.Cesium || !destination) return
            map.viewer.camera.flyTo({
                destination,
                orientation: {
                    heading: map.Cesium.Math.toRadians(0),
                    pitch: map.Cesium.Math.toRadians(-90),
                    roll: 0
                },
                duration
            })
        }

        // 飞到指定经纬度
        function flyToLocation(lon, lat, cameraHeight = 5000, duration = 1.5) {
            if (!map.viewer || !map.Cesium) return
            map.viewer.camera.flyTo({
                destination: map.Cesium.Cartesian3.fromDegrees(lon, lat, cameraHeight),
                orientation: {
                    heading: map.Cesium.Math.toRadians(0),
                    pitch: map.Cesium.Math.toRadians(-90),
                    roll: 0
                },
                duration
            })
        }

        // 左键点击
        function onLeftClick(e) {
            if (!map.Cesium || !map.viewer) return

            const position = getClickPosition(e)
            if (!position) return

            if (isDrawing.value) {
                handleDrawingClick(position)
            } else {
                const pickedElement = pickElement(e)
                if (pickedElement) {
                    selectedElement.value = pickedElement
                    emit('element-clicked', pickedElement)
                } else {
                    selectedElement.value = null
                }
            }
        }

        // 右键点击
        function onRightClick() {
            if (isDrawing.value && props.drawMode !== 'point') {
                confirmDrawing()
            }
        }

        // 获取点击位置
        function getClickPosition(e) {
            if (!map.Cesium || !map.viewer) return null

            const ray = map.viewer.camera.getPickRay(e.position)
            let cartesian = map.viewer.scene.globe.pick(ray, map.viewer.scene)

            if (!cartesian) {
                cartesian = map.viewer.scene.camera.pickEllipsoid(e.position, map.viewer.scene.globe.ellipsoid)
            }

            if (!cartesian) return null

            const carto = map.Cesium.Cartographic.fromCartesian(cartesian)
            return {
                longitude: map.Cesium.Math.toDegrees(carto.longitude),
                latitude: map.Cesium.Math.toDegrees(carto.latitude)
            }
        }

        // 拾取元素
        function pickElement(e) {
            const clickPos = getClickPosition(e)
            if (!clickPos) return null

            const threshold = 0.001

            for (const element of props.elements) {
                const coords = element.resource?.content?.geometry?.coordinates
                if (!coords) continue

                if (element.resource?.type === 0) {
                    const dist = Math.sqrt(
                        Math.pow(coords[0] - clickPos.longitude, 2) +
                        Math.pow(coords[1] - clickPos.latitude, 2)
                    )
                    if (dist < threshold) {
                        return element
                    }
                }
            }

            return null
        }

        // 开始绘制模式
        function startDrawingMode() {
            drawingCoords.value = []
            drawingPoints.value = []
            drawingLine.value = null
            drawingPolygon.value = null
            mousePreviewLine.value = null

            if (props.editingElement) {
                prepareEditElement(props.editingElement)
            }

            if (map.Cesium && map.viewer) {
                map.handler = new map.Cesium.ScreenSpaceEventHandler(map.viewer.canvas)

                map.handler.setInputAction((movement) => {
                    if (drawingCoords.value.length === 0) return

                    const ray = map.viewer.camera.getPickRay(movement.endPosition)
                    let cartesian = map.viewer.scene.globe.pick(ray, map.viewer.scene)
                    if (!cartesian) {
                        cartesian = map.viewer.scene.camera.pickEllipsoid(movement.endPosition, map.viewer.scene.globe.ellipsoid)
                    }
                    if (!cartesian) return

                    const carto = map.Cesium.Cartographic.fromCartesian(cartesian)
                    const lon = map.Cesium.Math.toDegrees(carto.longitude)
                    const lat = map.Cesium.Math.toDegrees(carto.latitude)

                    const lastCoord = drawingCoords.value[drawingCoords.value.length - 1]

                    mousePreviewLine.value = {
                        positions: map.Cesium.Cartesian3.fromDegreesArray([lastCoord[0], lastCoord[1], lon, lat]),
                        material: new map.Cesium.PolylineDashMaterialProperty({
                            color: map.Cesium.Color.YELLOW
                        })
                    }
                }, map.Cesium.ScreenSpaceEventType.MOUSE_MOVE)
            }
        }

        // 结束绘制模式
        function endDrawingMode() {
            if (map.handler) {
                map.handler.destroy()
                map.handler = null
            }
            clearTempDrawingEntities()
            drawingCoords.value = []
            drawingPoints.value = []
            drawingLine.value = null
            drawingPolygon.value = null
            mousePreviewLine.value = null
        }

        // 准备编辑元素 - 启动编辑模式
        function prepareEditElement(element) {
            const coords = element.resource?.content?.geometry?.coordinates
            if (!coords) return

            isEditing.value = true
            selectedElement.value = null

            // 保存原始坐标用于恢复
            originalEditCoords.value = JSON.parse(JSON.stringify(coords))

            // 确定编辑类型
            if (element.resource?.type === 0) {
                editingType.value = 'point'
                editingCoords.value = [[...coords]]
            } else if (element.resource?.type === 1) {
                editingType.value = 'line'
                editingCoords.value = coords.map(c => [...c])
            } else if (element.resource?.type === 2) {
                editingType.value = 'polygon'
                editingCoords.value = (coords[0] || []).map(c => [...c])
            }

            updateEditEntities()
            flyToElement(element)
            startEditEventHandlers()
        }

        // 启动编辑事件处理
        function startEditEventHandlers() {
            if (!map.Cesium || !map.viewer) return

            if (map.handler) {
                map.handler.destroy()
            }

            map.handler = new map.Cesium.ScreenSpaceEventHandler(map.viewer.canvas)

            // 鼠标按下 - 开始拖动 或 新增点
            map.handler.setInputAction((click) => {
                const clickPos = getClickPositionFromMovement(click.position)
                if (!clickPos) return

                // 检查是否点击了控制点
                const pointIndex = findNearestControlPoint(clickPos)
                if (pointIndex >= 0) {
                    draggingPointIndex.value = pointIndex
                    map.viewer.scene.screenSpaceCameraController.enableRotate = false
                    map.viewer.scene.screenSpaceCameraController.enableTranslate = false
                    map.viewer.scene.screenSpaceCameraController.enableZoom = false
                } else {
                    // 检查是否点击了线段（新增点）
                    const segmentInfo = findNearestSegment(clickPos)
                    if (segmentInfo) {
                        editingCoords.value.splice(segmentInfo.index, 0, segmentInfo.position)
                        updateEditEntities()

                        // 立即开始拖动新点
                        draggingPointIndex.value = segmentInfo.index
                        map.viewer.scene.screenSpaceCameraController.enableRotate = false
                        map.viewer.scene.screenSpaceCameraController.enableTranslate = false
                        map.viewer.scene.screenSpaceCameraController.enableZoom = false
                    } else if (editingType.value === 'point') {
                        // 点类型：点击地图直接移动到新位置
                        editingCoords.value = [[clickPos.longitude, clickPos.latitude]]
                        updateEditEntities()
                    }
                }
            }, map.Cesium.ScreenSpaceEventType.LEFT_DOWN)

            // 右键点击 - 删除点
            map.handler.setInputAction((click) => {
                const clickPos = getClickPositionFromMovement(click.position)
                if (!clickPos) return

                const pointIndex = findNearestControlPoint(clickPos)
                if (pointIndex >= 0) {
                    // 校验最小点数
                    if (editingType.value === 'polygon' && editingCoords.value.length <= 3) return
                    if (editingType.value === 'line' && editingCoords.value.length <= 2) return

                    editingCoords.value.splice(pointIndex, 1)
                    updateEditEntities()
                }
            }, map.Cesium.ScreenSpaceEventType.RIGHT_CLICK)

            // 鼠标移动 - 拖动控制点
            map.handler.setInputAction((movement) => {
                if (draggingPointIndex.value < 0) return

                const movePos = getClickPositionFromMovement(movement.endPosition)
                if (!movePos) return

                editingCoords.value[draggingPointIndex.value] = [movePos.longitude, movePos.latitude]
                updateEditEntities()
            }, map.Cesium.ScreenSpaceEventType.MOUSE_MOVE)

            // 鼠标抬起 - 结束拖动
            map.handler.setInputAction(() => {
                if (draggingPointIndex.value >= 0) {
                    draggingPointIndex.value = -1
                    map.viewer.scene.screenSpaceCameraController.enableRotate = true
                    map.viewer.scene.screenSpaceCameraController.enableTranslate = true
                    map.viewer.scene.screenSpaceCameraController.enableZoom = true
                }
            }, map.Cesium.ScreenSpaceEventType.LEFT_UP)
        }

        // 从屏幕坐标获取经纬度
        function getClickPositionFromMovement(position) {
            if (!map.Cesium || !map.viewer) return null

            const ray = map.viewer.camera.getPickRay(position)
            let cartesian = map.viewer.scene.globe.pick(ray, map.viewer.scene)

            if (!cartesian) {
                cartesian = map.viewer.scene.camera.pickEllipsoid(position, map.viewer.scene.globe.ellipsoid)
            }

            if (!cartesian) return null

            const carto = map.Cesium.Cartographic.fromCartesian(cartesian)
            return {
                longitude: map.Cesium.Math.toDegrees(carto.longitude),
                latitude: map.Cesium.Math.toDegrees(carto.latitude)
            }
        }

        // 查找最近的控制点
        function findNearestControlPoint(clickPos) {
            const threshold = 0.001 // 约100米

            for (let i = 0; i < editingCoords.value.length; i++) {
                const coord = editingCoords.value[i]
                const dist = Math.sqrt(
                    Math.pow(coord[0] - clickPos.longitude, 2) +
                    Math.pow(coord[1] - clickPos.latitude, 2)
                )
                if (dist < threshold) {
                    return i
                }
            }
            return -1
        }

        // 查找最近的线段
        function findNearestSegment(clickPos) {
            if (!editingCoords.value || editingCoords.value.length < 2) return null
            if (editingType.value === 'point') return null

            const threshold = 0.0005 // 约50米，与控制点保持一致

            let bestDist = threshold
            let result = null

            const points = editingCoords.value
            const isPolygon = editingType.value === 'polygon'
            const len = points.length

            for (let i = 0; i < (isPolygon ? len : len - 1); i++) {
                const p1 = points[i]
                const p2 = points[(i + 1) % len]

                // 计算点到线段的距离和投影点
                const info = getDistanceToSegment(clickPos, p1, p2)
                if (info.distance < bestDist) {
                    bestDist = info.distance
                    result = {
                        index: i + 1, // 插入到 p1 之后
                        position: info.position
                    }
                }
            }

            return result
        }

        // 计算点到线段的距离
        function getDistanceToSegment(p, a, b) {
            const x = p.longitude
            const y = p.latitude
            const x1 = a[0]
            const y1 = a[1]
            const x2 = b[0]
            const y2 = b[1]

            const A = x - x1
            const B = y - y1
            const C = x2 - x1
            const D = y2 - y1

            const dot = A * C + B * D
            const len_sq = C * C + D * D

            let param = -1
            if (len_sq !== 0) // in case of 0 length line
                param = dot / len_sq

            let xx, yy

            if (param < 0) {
                xx = x1
                yy = y1
            } else if (param > 1) {
                xx = x2
                yy = y2
            } else {
                xx = x1 + param * C
                yy = y1 + param * D
            }

            const dx = x - xx
            const dy = y - yy
            return {
                distance: Math.sqrt(dx * dx + dy * dy),
                position: [xx, yy]
            }
        }

        function clearTempDrawingEntities() {
            if (!map.viewer) return
            tempDrawingEntities.forEach(entity => {
                try {
                    map.viewer.entities.remove(entity)
                } catch (e) {}
            })
            tempDrawingEntities.length = 0
        }



        // 更新编辑模式的实体显示
        // 注意：由于使用了 CallbackProperty，这里需要创建并缓存它们
        // 实际的位置更新是通过 CallbackProperty 在每一帧自动完成的
        function updateEditEntities() {
            if (!map.Cesium) return

            // 只在点数量变化时更新控制点数组（用于渲染控制点图标）
            // 为每个控制点创建并缓存 CallbackProperty
            if (editControlPoints.value.length !== editingCoords.value.length) {
                editControlPoints.value = editingCoords.value.map((coord, index) => {
                    // 创建一个 CallbackProperty，它会在每一帧动态获取坐标
                    const positionCallback = new map.Cesium.CallbackProperty(() => {
                        const c = editingCoords.value[index]
                        if (c) {
                            return map.Cesium.Cartesian3.fromDegrees(c[0], c[1])
                        }
                        return map.Cesium.Cartesian3.ZERO
                    }, false)
                    return {
                        index,
                        positionCallback
                    }
                })
            }
            // 预览线和面的更新通过 editPreviewLineVisible/editPreviewPolygonVisible 计算属性
            // 以及 editLinePositionsCallback/editPolygonHierarchyCallback 的 CallbackProperty 自动完成
        }

        // 结束编辑模式
        function endEditingMode() {
            isEditing.value = false
            editingCoords.value = []
            editingType.value = null
            draggingPointIndex.value = -1
            editControlPoints.value = []
            editPreviewLine.value = null
            editPreviewPolygon.value = null

            if (map.handler) {
                map.handler.destroy()
                map.handler = null
            }

            // 恢复相机控制
            if (map.viewer) {
                map.viewer.scene.screenSpaceCameraController.enableRotate = true
                map.viewer.scene.screenSpaceCameraController.enableTranslate = true
                map.viewer.scene.screenSpaceCameraController.enableZoom = true
            }
        }

        // 处理绘制点击
        function handleDrawingClick(position) {
            const coord = [position.longitude, position.latitude]

            if (props.drawMode === 'point') {
                drawingCoords.value = [coord]
                updateDrawingEntities()
                confirmDrawing()
            } else {
                drawingCoords.value.push(coord)
                updateDrawingEntities()
            }
        }

        // 更新绘制实体 (原生 Cesium 实体)
        function updateDrawingEntities() {
            if (!map.viewer || !map.Cesium) return
            clearTempDrawingEntities()

            const currentColor = props.defaultColor || '#3366FF'

            // 1. 绘制已确定的顶点标点
            drawingCoords.value.forEach((coord) => {
                const pointEntity = map.viewer.entities.add({
                    position: map.Cesium.Cartesian3.fromDegrees(coord[0], coord[1]),
                    point: {
                        pixelSize: 10,
                        color: map.Cesium.Color.fromCssColorString(currentColor),
                        outlineColor: map.Cesium.Color.WHITE,
                        outlineWidth: 2,
                        disableDepthTestDistance: Number.POSITIVE_INFINITY
                    }
                })
                tempDrawingEntities.push(pointEntity)
            })

            // 2. 绘制折线
            if (props.drawMode === 'line' && drawingCoords.value.length >= 2) {
                const lineEntity = map.viewer.entities.add({
                    polyline: {
                        positions: map.Cesium.Cartesian3.fromDegreesArray(drawingCoords.value.flat()),
                        width: 3,
                        material: map.Cesium.Color.fromCssColorString(currentColor),
                        clampToGround: true
                    }
                })
                tempDrawingEntities.push(lineEntity)
            }

            // 3. 绘制多边形
            if (props.drawMode === 'polygon' && drawingCoords.value.length >= 3) {
                const polyEntity = map.viewer.entities.add({
                    polygon: {
                        hierarchy: new map.Cesium.PolygonHierarchy(map.Cesium.Cartesian3.fromDegreesArray(drawingCoords.value.flat())),
                        material: new map.Cesium.ColorMaterialProperty(map.Cesium.Color.fromCssColorString(currentColor).withAlpha(0.4)),
                        outline: true,
                        outlineColor: map.Cesium.Color.fromCssColorString(currentColor),
                        outlineWidth: 2,
                        heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND
                    }
                })
                tempDrawingEntities.push(polyEntity)
            }
        }

        // 取消绘制/编辑
        function cancelDrawing() {
            if (isEditing.value) {
                endEditingMode()
            } else {
                endDrawingMode()
            }
            emit('draw-cancel')
        }

        // 确认绘制/编辑
        function confirmDrawing() {
            if (!canConfirmDrawing.value) return

            let coordinates
            let type

            if (isEditing.value) {
                // 编辑模式 - 保存到临时状态，不直接结束
                type = editingType.value
                if (editingType.value === 'point') {
                    coordinates = editingCoords.value[0]
                } else if (editingType.value === 'line') {
                    coordinates = [...editingCoords.value]
                } else if (editingType.value === 'polygon') {
                    coordinates = [[...editingCoords.value]]
                }

                // 保存到待确认状态
                pendingEditElement.value = {
                    id: props.editingElement?.id,
                    type,
                    coordinates
                }
                clearTempDrawingEntities()
                emit('edit-confirmed', {
                    id: props.editingElement?.id,
                    type,
                    coordinates
                })
            } else {
                type = props.drawMode
                if (props.drawMode === 'point') {
                    coordinates = drawingCoords.value[0]
                } else if (props.drawMode === 'line') {
                    coordinates = [...drawingCoords.value]
                } else if (props.drawMode === 'polygon') {
                    coordinates = [[...drawingCoords.value]]
                }
                clearTempDrawingEntities()
                endDrawingMode()
                emit('draw-complete', { type, coordinates })
            }
        }

        // 恢复原始编辑 - 用于用户取消时
        function revertEdit() {
            if (pendingEditElement.value) {
                pendingEditElement.value = null
            }
            originalEditCoords.value = null
            // 递增刷新 key 强制 Vue-Cesium 重新渲染所有元素实体
            elementRefreshKey.value++
        }

        // 清除待确认的编辑 - 用于用户保存成功后
        function clearPendingEdit() {
            pendingEditElement.value = null
            originalEditCoords.value = null
        }

        // 获取绘制/编辑标题
        function getDrawingTitle() {
            if (isEditing.value) {
                const titles = {
                    point: '编辑点位置',
                    line: '编辑线',
                    polygon: '编辑面'
                }
                return titles[editingType.value] || '编辑'
            }
            const titles = {
                point: '标记点',
                line: '绘制线',
                polygon: '绘制面'
            }
            return titles[props.drawMode] || '绘制'
        }

        // 获取绘制/编辑提示
        function getDrawingHint() {
            if (isEditing.value) {
                if (editingType.value === 'point') {
                    return '点击地图移动位置，或拖动控制点'
                } else if (editingType.value === 'line') {
                    return `共 ${editingCoords.value.length} 个点，拖动调整，点击线段增加点，右键点删除`
                } else if (editingType.value === 'polygon') {
                    return `共 ${editingCoords.value.length} 个点，拖动调整，点击边线增加点，右键点删除`
                }
                return ''
            }
            if (props.drawMode === 'point') {
                return '点击地图选择位置'
            } else if (props.drawMode === 'line') {
                return `已添加 ${drawingCoords.value.length} 个点，右键结束`
            } else if (props.drawMode === 'polygon') {
                return `已添加 ${drawingCoords.value.length} 个点，右键结束`
            }
            return ''
        }

        // 辅助函数：获取点位置
        function getPointPosition(element) {
            const coords = element.resource?.content?.geometry?.coordinates
            if (coords && coords.length >= 2) {
                return [coords[0], coords[1]]
            }
            return [0, 0]
        }

        // 辅助函数：获取线位置
        function getLinePositions(element) {
            const coords = element.resource?.content?.geometry?.coordinates
            console.log('coords', coords)
            if (coords && map.Cesium) {
                const positions = coords.map((p) => map.Cesium.Cartesian3.fromDegrees(p[0], p[1]))
                return new map.Cesium.CallbackProperty(() => positions, false)
            }
            return []
        }

        // 辅助函数：获取面层级
        function getPolygonHierarchy(element) {
            const coords = element.resource?.content?.geometry?.coordinates
            if (coords && coords[0] && map.Cesium) {
                return {
                    positions: map.Cesium.Cartesian3.fromDegreesArray(coords[0].flat().map(Number))
                }
            }
            return { positions: [] }
        }

        // 辅助函数：获取元素颜色
        function getElementColor(element) {
            return element.resource?.content?.properties?.color || '#3366FF'
        }

        // 辅助函数：获取带透明度的元素颜色
        function getElementColorWithAlpha(element, alpha) {
            const color = getElementColor(element)
            return hexToRgba(color, alpha)
        }

        // 辅助函数：获取元素类型名称
        function getElementTypeName(type) {
            const names = {
                0: '点',
                1: '线',
                2: '面'
            }
            return names[type] || '未知'
        }

        // 辅助函数：获取元素坐标
        function getElementCoordinates(element) {
            const coords = element.resource?.content?.geometry?.coordinates
            if (coords && coords.length >= 2) {
                return `${coords[0].toFixed(6)}, ${coords[1].toFixed(6)}`
            }
            return '--'
        }

        // 动态生成 SVG 图标
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

        // 图标缓存
        const iconCache = new Map()

        function getMarkerIcon(element) {
            const color = getElementColor(element)
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

defineExpose({
    flyToElement,
    flyToDestination,
    flyToLocation,
    clearPendingEdit,
    revertEdit
})
</script>

<style lang="scss" scoped>
.layer-map-editor {
    position: relative;
    width: 100%;
    height: 100%;
    min-height: 400px;
    background: #1a1a2e;
    border-radius: 8px;
    overflow: hidden;

    :deep(.cesium-viewer-geocoderContainer) {
        position: absolute;
        top: 16px;
        right: 16px;
        z-index: 100;

        .cesium-geocoder-input {
            width: 240px;
            background: rgba(15, 23, 42, 0.85);
            color: #ffffff;
            border: 1px solid rgba(255, 255, 255, 0.2);
            border-radius: 6px;
            padding: 6px 10px;
            font-size: 13px;
            backdrop-filter: blur(10px);
            transition: all 0.2s ease;

            &:focus {
                width: 300px;
                border-color: #3b82f6;
                box-shadow: 0 0 10px rgba(59, 130, 246, 0.4);
                outline: none;
            }
        }

        .cesium-geocoder-searchButton {
            fill: #9ca3af;
            &:hover {
                fill: #ffffff;
            }
        }

        .search-results {
            background: rgba(15, 23, 42, 0.95);
            border: 1px solid rgba(255, 255, 255, 0.2);
            border-radius: 6px;
            color: #ffffff;
            backdrop-filter: blur(10px);
            margin-top: 4px;

            li {
                padding: 8px 12px;
                border-bottom: 1px solid rgba(255, 255, 255, 0.08);

                &:hover {
                    background: rgba(59, 130, 246, 0.3);
                }
            }
        }
    }
}

.draw-toolbar {
    position: absolute;
    top: 16px;
    left: 50%;
    transform: translateX(-50%);
    background: rgba(0, 0, 0, 0.85);
    border: 1px solid rgba(255, 255, 255, 0.2);
    border-radius: 8px;
    padding: 12px 20px;
    z-index: 100;
    color: #fff;
    text-align: center;
    min-width: 200px;
    backdrop-filter: blur(10px);

    .toolbar-title {
        font-size: 16px;
        font-weight: 600;
        margin-bottom: 8px;
        color: #1890ff;
    }

    .toolbar-hint {
        font-size: 12px;
        color: #aaa;
        margin-bottom: 12px;
    }

    .toolbar-actions {
        display: flex;
        gap: 12px;
        justify-content: center;

        :deep(.ant-btn) {
            background-color: transparent;
            border-color: rgba(255, 255, 255, 0.4);
            color: rgba(255, 255, 255, 0.9);

            &:hover {
                border-color: #1890ff;
                color: #1890ff;
            }
        }

        :deep(.ant-btn-primary) {
            background-color: #1890ff;
            border-color: #1890ff;
            color: #fff;

            &:hover {
                background-color: #40a9ff;
                border-color: #40a9ff;
            }

            &[disabled] {
                color: rgba(255, 255, 255, 0.3);
                border-color: rgba(255, 255, 255, 0.2);
                background-color: rgba(255, 255, 255, 0.08);
            }
        }
    }
}

.element-info-panel {
    position: absolute;
    bottom: 16px;
    left: 16px;
    background: rgba(0, 0, 0, 0.85);
    border: 1px solid rgba(255, 255, 255, 0.2);
    border-radius: 8px;
    padding: 16px;
    z-index: 100;
    color: #fff;
    min-width: 240px;
    backdrop-filter: blur(10px);

    .panel-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 12px;
        padding-bottom: 8px;
        border-bottom: 1px solid rgba(255, 255, 255, 0.1);

        .panel-title {
            font-size: 14px;
            font-weight: 600;
        }

        .close-icon {
            cursor: pointer;
            color: #999;

            &:hover {
                color: #fff;
            }
        }
    }

    .panel-content {
        margin-bottom: 12px;

        .info-row {
            display: flex;
            margin-bottom: 8px;
            font-size: 12px;

            .label {
                color: #999;
                width: 50px;
            }

            .value {
                color: #fff;
            }
        }
    }

    .panel-actions {
        display: flex;
        gap: 8px;
    }
}

.loading-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(26, 26, 46, 0.9);
    z-index: 200;
}

// 调试面板样式
.debug-panel {
    position: absolute;
    top: 16px;
    left: 16px;
    background: rgba(0, 100, 0, 0.95);
    border: 2px solid #00ff00;
    border-radius: 8px;
    padding: 12px 16px;
    z-index: 999;
    color: #00ff00;
    font-family: 'Consolas', 'Monaco', monospace;
    min-width: 300px;
    backdrop-filter: blur(10px);
    box-shadow: 0 0 20px rgba(0, 255, 0, 0.3);
    pointer-events: none;

    .debug-title {
        font-size: 14px;
        font-weight: 600;
        margin-bottom: 10px;
        padding-bottom: 6px;
        border-bottom: 1px solid rgba(0, 255, 0, 0.4);
        color: #90EE90;
    }

    .debug-row {
        display: flex;
        margin-bottom: 6px;
        font-size: 13px;

        .label {
            color: #98FB98;
            width: 100px;
        }

        .value {
            color: #00ff00;
            font-weight: bold;
            font-size: 14px;
        }
    }

    .debug-hint {
        margin-top: 10px;
        padding-top: 8px;
        border-top: 1px dashed rgba(0, 255, 0, 0.3);
        font-size: 11px;
        color: #FFD700;
        font-style: italic;
    }
}
</style>
