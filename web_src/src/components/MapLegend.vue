<template>
    <div class="map-legend">
        <div class="map-legend-item">
            <div class="item" v-for="(item, index) in legendList" :key="'l_' + index">
                <div class="name">
                    <img :src="item.url" alt="">
                    {{ item.name }}
                </div>
                <a-switch size="small" v-model:checked="item.checked" @change="handleChange" />
            </div>
            <div class="item" v-for="(item, index) in legendList1" :key="'l1_' + index">
                <div class="name">
                    <span :style="{ backgroundColor: item.color }"></span>
                    {{ item.name }}
                </div>
                <a-switch size="small" v-model:checked="item.checked" @change="handleChange" />
            </div>
        </div>
        <!-- 3D 建模独立控制模块 -->
        <div class="map-legend-item tileset-legend-module" v-if="totalCount > 0 || searchKeyword || loadingTilesets || (tilesetsList && tilesetsList.length > 0)">
            <div class="tileset-header">
                <span class="title">3D 建模</span>
                <span class="count-tag" v-if="totalCount > 0">{{ totalCount }}</span>
            </div>

            <!-- 关键词搜索框 -->
            <div class="tileset-search">
                <a-input
                    v-model:value="searchKeyword"
                    placeholder="搜索模型"
                    size="small"
                    allow-clear
                    @change="onKeywordChange"
                    @pressEnter="handleSearch"
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
                    <a-switch size="small" v-model:checked="item.checked" @change="(val) => handleTilesetChange(val, item)" />
                </div>
            </div>
            <div class="tileset-empty" v-else>
                <span>{{ loadingTilesets ? '加载中...' : '暂无 3D 建模数据' }}</span>
            </div>

            <!-- 分页控制 -->
            <div class="tileset-pagination" v-if="totalCount > pageSize || totalPages > 1">
                <a-button
                    type="text"
                    size="small"
                    class="pager-btn"
                    :disabled="currentPage <= 1 || loadingTilesets"
                    @click="handlePageChange(currentPage - 1)"
                >
                    <LeftOutlined style="font-size: 10px;" />
                </a-button>
                <span class="pager-text">{{ currentPage }} / {{ totalPages }}</span>
                <a-button
                    type="text"
                    size="small"
                    class="pager-btn"
                    :disabled="currentPage >= totalPages || loadingTilesets"
                    @click="handlePageChange(currentPage + 1)"
                >
                    <RightOutlined style="font-size: 10px;" />
                </a-button>
            </div>
        </div>
    </div>
</template>

<script lang="ts" setup>
import { ref, computed, watch, onMounted } from 'vue'
import { SearchOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import { getElementGroupsReq } from '@/api/layer'
import { getFlightAreaList } from '@/api/flight-area'
import { getTilesets } from '@/api/manage'


const emit = defineEmits(['change', 'change-tileset'])

// 图标列表
const legendList = ref<any[]>([
    {
        url: new URL(`../assets/airControl/map/map-icon-marker.svg`, import.meta.url).href,
        name: '标记点',
        checked: false,
        elements: []
    }
])

// 颜色区域列表
const legendList1 = ref<any[]>([

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
        url: new URL(`../assets/airControl/map/map-icon-gkd.png`, import.meta.url).href,
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

// 获取地图展示数据
const getMapLayer = async () => {
    try {
        // 1. 获取普通图层数据
        const res = await getElementGroupsReq({} as any)
        if (res && res.data) {

            const allLayerData = res.data
            // 处理普通图层列表
            legendList.value.forEach(ite => {
                const found = allLayerData.find((val: any) => val.name == ite.name)
                if (found && found.elements && found.elements.length > 0) {
                    ite.elements = found.elements.map((element: any) => ({
                        name: element?.name,
                        id: element?.id,
                        type: element?.resource?.content?.geometry?.type,
                        lonLat: element?.resource?.content?.geometry?.coordinates,
                        resource: element?.resource,
                    }))
                } else {
                    ite.elements = []
                }
            })
            // 处理颜色区域列表中的普通图层（非官方飞行区）
            legendList1.value.forEach(ite => {
                if (ite.isFlightArea) return // 官方飞行区单独处理

                const found = allLayerData.find((val: any) => val.name == ite.name)
                if (found && found.elements && found.elements.length > 0) {
                    ite.elements = found.elements.map((element: any) => ({
                        name: element?.name,
                        id: element?.id,
                        type: element?.resource?.content?.geometry?.type,
                        lonLat: element?.resource?.content?.geometry?.coordinates,
                        resource: element?.resource,
                    }))
                } else {
                    ite.elements = []
                }
            })
        }

        // 2. 获取官方飞行区域数据
        const flightRes = await getFlightAreaList()
        if (flightRes && flightRes.code === 0 && flightRes.data) {
            legendList1.value.forEach(ite => {
                if (ite.isFlightArea) {
                    const filteredAreas = flightRes.data.filter((area: any) => area.type === ite.flightAreaType)
                    ite.elements = filteredAreas.map((area: any) => ({
                        name: area.name,
                        id: area.area_id,
                        type: area.content?.geometry?.type,
                        lonLat: area.content?.geometry?.coordinates,
                        radius: area.content?.geometry?.radius,
                        resource: {
                            content: {
                                geometry: area.content.geometry,
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
}

const tilesetsList = ref<any[]>([])
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(4)
const totalCount = ref(0)
const loadingTilesets = ref(false)
const checkedTilesetMap = ref<Record<string, boolean>>({})

const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value) || 1)

const fetchTilesetsList = async () => {
    loadingTilesets.value = true
    try {
        const res = await getTilesets({
            page: currentPage.value,
            page_size: pageSize.value,
            keyword: searchKeyword.value.trim() || undefined
        })
        let listData: any[] = []
        if (res && (res.code === 200 || res.code === 0) && res.data) {
            if (Array.isArray(res.data)) {
                listData = res.data
                totalCount.value = listData.length
            } else if (Array.isArray(res.data.list)) {
                listData = res.data.list
                totalCount.value = res.data.pagination?.total ?? listData.length
            }
        } else {
            listData = []
            totalCount.value = 0
        }
        tilesetsList.value = listData.map((item: any) => {
            const key = item.file_id || item.object_key || item.name
            const isChecked = checkedTilesetMap.value[key] ?? false
            return {
                ...item,
                url: item.url || (item.object_key ? (item.object_key.startsWith('/') ? item.object_key : `/${item.object_key}`) : ''),
                checked: isChecked
            }
        })
    } catch (err) {
        console.error('获取3d建模列表失败:', err)
        tilesetsList.value = []
        totalCount.value = 0
    } finally {
        loadingTilesets.value = false
    }
}

let searchTimer: any = null

watch(searchKeyword, () => {
    clearTimeout(searchTimer)
    searchTimer = setTimeout(() => {
        currentPage.value = 1
        fetchTilesetsList()
    }, 300)
})

const onKeywordChange = (e: any) => {
    if (e && e.target && typeof e.target.value === 'string') {
        searchKeyword.value = e.target.value
    }
}

const handleSearch = () => {
    clearTimeout(searchTimer)
    currentPage.value = 1
    fetchTilesetsList()
}

const handlePageChange = (newPage: number) => {
    if (newPage < 1 || newPage > totalPages.value) return
    currentPage.value = newPage
    fetchTilesetsList()
}

const handleTilesetChange = (checked: boolean, item: any) => {
    const key = item.file_id || item.object_key || item.name
    checkedTilesetMap.value[key] = checked
    emit('change-tileset', {
        checked,
        name: item.name,
        url: item.url
    })
}

onMounted(() => {
    getMapLayer()
    fetchTilesetsList()
})


const handleChange = () => {
    emit('change', {
        legendList: legendList.value,
        legendList1: legendList1.value
    })
}

// 暴露获取所有图层列表的方法
const getAllLayers = () => {
    return [...legendList.value, ...legendList1.value]
}

// 设置图层元素
const setLayerElements = (legendName: string, elements: any[]) => {
    const allLayers = [...legendList.value, ...legendList1.value]
    const layer = allLayers.find(l => l.name === legendName)
    if (layer) {
        layer.elements = elements
    }
}

defineExpose({
    legendList,
    legendList1,
    tilesetsList,
    totalCount,
    searchKeyword,
    currentPage,
    pageSize,
    getAllLayers,
    setLayerElements,
    getMapLayer,
    fetchTilesetsList
})

</script>

<style lang="scss" scoped>
.map-legend {
    position: absolute;
    left: .25rem;
    bottom: .25rem;
    display: flex;
    align-items: flex-end;
    z-index: 100;

    .map-legend-item {
        background-color: #fff;
        border-radius: 2px;
        box-shadow: 0 0 4px rgba(0, 0, 0, .6);
        padding: 6px;
        margin-right: 4px;
    }

    .tileset-legend-module {
        min-width: 155px;
        max-width: 195px;

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
</style>
