/**
 * 航点拖拽优化 - 使用 CallbackProperty 实现流畅拖拽
 * 
 * 核心原理:
 * 1. 使用纯 JS 对象存储航点数据,避免 Vue 响应式开销
 * 2. 使用 Cesium CallbackProperty 动态计算位置,避免重绘
 * 3. 直接操作数据对象,Cesium 自动更新视图
 */

import { ref } from 'vue'

/**
 * 创建优化的航点管理系统
 */
export function useOptimizedWaypoints(map) {
    // 航点数据存储 - 纯 JS 对象,不触发 Vue 响应式
    const waypointsData = ref([])

    // Cesium Entity 引用
    const waypointEntities = ref([])
    const waypointLineEntities = ref([])

    /**
     * 创建航点 Entity (使用 CallbackProperty)
     */
    const createWaypointEntity = (index, lng, lat, height, terrainHeight = 0) => {
        // 创建数据对象
        const positionData = {
            lng,
            lat,
            height,
            terrainHeight,
            aglHeight: height - terrainHeight,
            amslHeight: height
        }

        waypointsData.value[index] = positionData

        // 位置 CallbackProperty - 实时从数据对象读取
        const positionCallback = new map.Cesium.CallbackProperty(() => {
            const data = waypointsData.value[index]
            if (!data) return map.Cesium.Cartesian3.ZERO
            return map.Cesium.Cartesian3.fromDegrees(data.lng, data.lat, data.height)
        }, false)

        // 标签文本 CallbackProperty - 实时显示高度
        const labelTextCallback = new map.Cesium.CallbackProperty(() => {
            const data = waypointsData.value[index]
            if (!data) return ''
            return `航点${index + 1}\nAMSL: ${data.amslHeight.toFixed(1)}m\nAGL: ${data.aglHeight.toFixed(1)}m`
        }, false)

        // 创建 Entity
        const entity = map.viewer.entities.add({
            id: `waypoint-${index}`,
            position: positionCallback,

            billboard: {
                image: '/map_png/waypoint.png', // 替换为你的图标路径
                width: 28,
                height: 34,
                verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
                heightReference: map.Cesium.HeightReference.NONE,
                disableDepthTestDistance: Number.POSITIVE_INFINITY,
                // 高亮效果
                color: new map.Cesium.CallbackProperty(() => {
                    return map.Cesium.Color.WHITE
                }, false),
                scale: new map.Cesium.CallbackProperty(() => {
                    return 1.0
                }, false)
            },

            label: {
                text: labelTextCallback,
                font: '12px sans-serif',
                fillColor: map.Cesium.Color.WHITE,
                showBackground: true,
                backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.7),
                pixelOffset: new map.Cesium.Cartesian2(0, -45),
                verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
                disableDepthTestDistance: Number.POSITIVE_INFINITY,
                style: map.Cesium.LabelStyle.FILL_AND_OUTLINE,
                outlineWidth: 2,
                outlineColor: map.Cesium.Color.BLACK
            }
        })

        waypointEntities.value[index] = entity
        return entity
    }

    /**
     * 创建航点连线 (使用 CallbackProperty)
     */
    const createWaypointLine = (index1, index2) => {
        // 连线位置 CallbackProperty
        const positionsCallback = new map.Cesium.CallbackProperty(() => {
            const wp1 = waypointsData.value[index1]
            const wp2 = waypointsData.value[index2]

            if (!wp1 || !wp2) return []

            return [
                map.Cesium.Cartesian3.fromDegrees(wp1.lng, wp1.lat, wp1.height),
                map.Cesium.Cartesian3.fromDegrees(wp2.lng, wp2.lat, wp2.height)
            ]
        }, false)

        // 距离标签 CallbackProperty
        const distanceCallback = new map.Cesium.CallbackProperty(() => {
            const wp1 = waypointsData.value[index1]
            const wp2 = waypointsData.value[index2]

            if (!wp1 || !wp2) return ''

            const c1 = map.Cesium.Cartesian3.fromDegrees(wp1.lng, wp1.lat, wp1.height)
            const c2 = map.Cesium.Cartesian3.fromDegrees(wp2.lng, wp2.lat, wp2.height)
            const distance = map.Cesium.Cartesian3.distance(c1, c2)

            return `${(distance / 1000).toFixed(2)} km`
        }, false)

        // 中点位置 CallbackProperty
        const midPointCallback = new map.Cesium.CallbackProperty(() => {
            const wp1 = waypointsData.value[index1]
            const wp2 = waypointsData.value[index2]

            if (!wp1 || !wp2) return map.Cesium.Cartesian3.ZERO

            const c1 = map.Cesium.Cartesian3.fromDegrees(wp1.lng, wp1.lat, wp1.height)
            const c2 = map.Cesium.Cartesian3.fromDegrees(wp2.lng, wp2.lat, wp2.height)

            return map.Cesium.Cartesian3.lerp(c1, c2, 0.5, new map.Cesium.Cartesian3())
        }, false)

        // 创建连线 Entity
        const lineEntity = map.viewer.entities.add({
            id: `wayline-${index1}-${index2}`,
            polyline: {
                positions: positionsCallback,
                width: 3,
                material: map.Cesium.Color.GREEN.withAlpha(0.8),
                clampToGround: false,
                arcType: map.Cesium.ArcType.NONE
            }
        })

        // 创建距离标签 Entity
        const labelEntity = map.viewer.entities.add({
            id: `distance-label-${index1}-${index2}`,
            position: midPointCallback,
            label: {
                text: distanceCallback,
                font: '11px sans-serif',
                fillColor: map.Cesium.Color.YELLOW,
                showBackground: true,
                backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.6),
                pixelOffset: new map.Cesium.Cartesian2(0, 0),
                disableDepthTestDistance: Number.POSITIVE_INFINITY
            }
        })

        return { lineEntity, labelEntity }
    }

    /**
     * 更新所有连线
     */
    const updateWaypointLines = () => {
        // 移除旧的连线
        waypointLineEntities.value.forEach(({ lineEntity, labelEntity }) => {
            map.viewer.entities.remove(lineEntity)
            map.viewer.entities.remove(labelEntity)
        })
        waypointLineEntities.value = []

        // 创建新的连线
        for (let i = 0; i < waypointsData.value.length - 1; i++) {
            const entities = createWaypointLine(i, i + 1)
            waypointLineEntities.value.push(entities)
        }
    }

    /**
     * 添加航点
     */
    const addWaypoint = async (lng, lat, relativeHeight, terrainHeight = 0) => {
        const index = waypointsData.value.length
        const amslHeight = terrainHeight + relativeHeight

        createWaypointEntity(index, lng, lat, amslHeight, terrainHeight)
        updateWaypointLines()

        return index
    }

    /**
     * 删除航点
     */
    const removeWaypoint = (index) => {
        if (index < 0 || index >= waypointsData.value.length) return

        // 移除 Entity
        const entity = waypointEntities.value[index]
        if (entity) {
            map.viewer.entities.remove(entity)
        }

        // 移除数据
        waypointsData.value.splice(index, 1)
        waypointEntities.value.splice(index, 1)

        // 更新后续航点的 ID
        for (let i = index; i < waypointEntities.value.length; i++) {
            const entity = waypointEntities.value[i]
            if (entity) {
                entity.id = `waypoint-${i}`
            }
        }

        // 更新连线
        updateWaypointLines()
    }

    /**
     * 优化的拖拽处理 - 直接修改数据对象
     */
    const updateWaypointPosition = (index, lng, lat, height) => {
        const data = waypointsData.value[index]
        if (!data) return

        // 直接修改数据,CallbackProperty 会自动更新视图
        if (lng !== undefined) data.lng = lng
        if (lat !== undefined) data.lat = lat
        if (height !== undefined) {
            data.height = height
            data.amslHeight = height
            data.aglHeight = height - data.terrainHeight
        }

        // 无需手动更新 Entity,Cesium 会自动处理
    }

    /**
     * 批量查询地形高度
     */
    const queryTerrainHeights = async () => {
        if (!map.viewer.terrainProvider || waypointsData.value.length === 0) {
            return
        }

        const positions = waypointsData.value.map(wp =>
            map.Cesium.Cartographic.fromDegrees(wp.lng, wp.lat)
        )

        try {
            const updatedPositions = await map.Cesium.sampleTerrainMostDetailed(
                map.viewer.terrainProvider,
                positions
            )

            updatedPositions.forEach((pos, index) => {
                const terrainHeight = pos.height || 0
                const data = waypointsData.value[index]

                data.terrainHeight = terrainHeight
                data.aglHeight = data.height - terrainHeight
                data.amslHeight = data.height
            })

            console.log('地形高度已更新')
        } catch (error) {
            console.error('地形高度查询失败:', error)
        }
    }

    /**
     * 获取航点数据 (用于导出)
     */
    const getWaypointsData = () => {
        return waypointsData.value.map((wp, index) => ({
            index,
            longitude: wp.lng,
            latitude: wp.lat,
            height_AMSL: wp.amslHeight,
            height_AGL: wp.aglHeight,
            terrain_height: wp.terrainHeight
        }))
    }

    /**
     * 清空所有航点
     */
    const clearAllWaypoints = () => {
        // 移除所有 Entity
        waypointEntities.value.forEach(entity => {
            if (entity) map.viewer.entities.remove(entity)
        })

        waypointLineEntities.value.forEach(({ lineEntity, labelEntity }) => {
            map.viewer.entities.remove(lineEntity)
            map.viewer.entities.remove(labelEntity)
        })

        // 清空数据
        waypointsData.value = []
        waypointEntities.value = []
        waypointLineEntities.value = []
    }

    return {
        waypointsData,
        waypointEntities,
        addWaypoint,
        removeWaypoint,
        updateWaypointPosition,
        updateWaypointLines,
        queryTerrainHeights,
        getWaypointsData,
        clearAllWaypoints
    }
}

/**
 * 设置优化的拖拽处理器
 */
export function setupOptimizedDragHandler(map, waypointManager, dragState) {
    const handler = new map.Cesium.ScreenSpaceEventHandler(map.viewer.scene.canvas)

    // 左键按下 - 开始拖拽
    handler.setInputAction((click) => {
        const pickedObject = map.viewer.scene.pick(click.position)

        if (map.Cesium.defined(pickedObject) && pickedObject.id) {
            const entityId = pickedObject.id.id

            if (entityId && entityId.startsWith('waypoint-')) {
                const index = parseInt(entityId.split('-')[1])

                if (index >= 0 && index < waypointManager.waypointsData.value.length) {
                    const wp = waypointManager.waypointsData.value[index]

                    dragState.isDragging = true
                    dragState.waypointIndex = index
                    dragState.mode = dragState.altKeyPressed ? 'vertical' : 'horizontal'
                    dragState.startMouseY = click.position.y
                    dragState.startHeight = wp.height
                    dragState.startPosition = { lng: wp.lng, lat: wp.lat, height: wp.height }

                    // 禁用相机控制
                    map.viewer.scene.screenSpaceCameraController.enableRotate = false
                    map.viewer.scene.screenSpaceCameraController.enableTranslate = false
                    map.viewer.scene.screenSpaceCameraController.enableZoom = false

                    console.log('开始拖拽航点:', index, dragState.mode)
                }
            }
        }
    }, map.Cesium.ScreenSpaceEventType.LEFT_DOWN)

    // 鼠标移动 - 拖拽中
    handler.setInputAction((movement) => {
        if (!dragState.isDragging || dragState.waypointIndex < 0) return

        const index = dragState.waypointIndex
        const wp = waypointManager.waypointsData.value[index]

        if (dragState.altKeyPressed) {
            // 垂直拖拽 - 调整高度
            const dy = dragState.startMouseY - movement.endPosition.y
            const sensitivity = 0.5 // 0.5米/像素
            let newHeight = dragState.startHeight + dy * sensitivity

            if (newHeight < wp.terrainHeight) {
                newHeight = wp.terrainHeight // 不能低于地面
            }

            // 直接修改数据,CallbackProperty 自动更新
            waypointManager.updateWaypointPosition(index, undefined, undefined, newHeight)

        } else {
            // 水平拖拽 - 调整经纬度
            const ray = map.viewer.scene.camera.getPickRay(movement.endPosition)
            let cartesian = map.viewer.scene.globe.pick(ray, map.viewer.scene)

            if (!cartesian) {
                cartesian = map.viewer.scene.camera.pickEllipsoid(
                    movement.endPosition,
                    map.viewer.scene.globe.ellipsoid
                )
            }

            if (cartesian) {
                const carto = map.Cesium.Cartographic.fromCartesian(cartesian)
                const lng = map.Cesium.Math.toDegrees(carto.longitude)
                const lat = map.Cesium.Math.toDegrees(carto.latitude)

                // 直接修改数据,CallbackProperty 自动更新
                waypointManager.updateWaypointPosition(index, lng, lat, undefined)
            }
        }
    }, map.Cesium.ScreenSpaceEventType.MOUSE_MOVE)

    // 左键抬起 - 结束拖拽
    handler.setInputAction(() => {
        if (dragState.isDragging) {
            // 恢复相机控制
            map.viewer.scene.screenSpaceCameraController.enableRotate = true
            map.viewer.scene.screenSpaceCameraController.enableTranslate = true
            map.viewer.scene.screenSpaceCameraController.enableZoom = true

            console.log('拖拽结束')

            dragState.isDragging = false
            dragState.waypointIndex = -1
        }
    }, map.Cesium.ScreenSpaceEventType.LEFT_UP)

    return handler
}

/**
 * 使用示例:
 * 
 * // 在 setup 中
 * const waypointManager = useOptimizedWaypoints(map)
 * 
 * // 在 onViewerReady 中
 * const dragHandler = setupOptimizedDragHandler(map, waypointManager, waypointDragState)
 * 
 * // 添加航点
 * await waypointManager.addWaypoint(106.5, 29.5, 100, 50)
 * 
 * // 查询地形高度
 * await waypointManager.queryTerrainHeights()
 * 
 * // 导出数据
 * const data = waypointManager.getWaypointsData()
 */
