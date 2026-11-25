<template>
    <div class="flex h-full bg-[#04163A]">
        <!-- 左侧分组列表 -->
        <div class="w-56 border-r border-[#1a3a6a] flex flex-col h-full overflow-hidden">
            <div class="flex items-center justify-between px-4 py-3 border-b border-[#1a3a6a] flex-shrink-0">
                <span class="text-white text-base font-medium">分组列表</span>
                <button @click="loadGroups" class="text-blue-400 hover:text-blue-300 transition-colors"
                    :class="{ 'animate-spin': groupLoading }">
                    <ReloadOutlined />
                </button>
            </div>
            <!-- 搜索框 -->
            <div class="px-3 py-2 border-b border-[#1a3a6a] flex-shrink-0">
                <div class="relative">
                    <SearchOutlined class="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400 text-sm" />
                    <input v-model="groupSearchKeyword" type="text" placeholder="搜索分组..."
                        class="w-full pl-8 pr-8 py-1.5 bg-gray-700/50 border border-gray-600 rounded text-sm text-white placeholder-gray-400 focus:outline-none focus:border-blue-500 transition-colors" />
                    <button v-if="groupSearchKeyword" @click="groupSearchKeyword = ''"
                        class="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white transition-colors">
                        <CloseOutlined class="text-xs" />
                    </button>
                </div>
            </div>
            <!-- 分组列表 - 可滚动区域 -->
            <div class="flex-1 overflow-y-auto min-h-0">
                <!-- 加载中 -->
                <div v-if="groupLoading" class="flex items-center justify-center py-8">
                    <div class="w-6 h-6 border-2 border-blue-400 border-t-transparent rounded-full animate-spin"></div>
                </div>
                <!-- 分组列表 -->
                <div v-else-if="filteredGroupList.length > 0" class="space-y-1 px-2 py-2">
                    <div v-for="group in filteredGroupList" :key="group.group_id" @click="selectGroup(group)"
                        class="flex items-center gap-2 px-3 py-2.5 rounded-md cursor-pointer transition-all duration-200"
                        :class="selectedGroupKeys.includes(group.group_id)
                            ? 'bg-blue-600/40 text-white'
                            : 'text-gray-300 hover:bg-[#365ca6]/30'">
                        <span class="flex-1 truncate text-sm">{{ group.group_name }}</span>
                        <span v-if="group.is_distributed"
                            class="px-1.5 py-0.5 text-[10px] bg-green-500/20 text-green-400 rounded flex-shrink-0">已分发</span>
                        <span v-if="group.is_lock"
                            class="px-1.5 py-0.5 text-[10px] bg-orange-500/20 text-orange-400 rounded flex-shrink-0">锁定</span>
                    </div>
                </div>
                <!-- 搜索无结果 -->
                <div v-else-if="groupSearchKeyword && groupList.length > 0"
                    class="flex flex-col items-center justify-center py-8 text-gray-500">
                    <SearchOutlined class="text-2xl mb-2 opacity-50" />
                    <span class="text-sm">未找到匹配的分组</span>
                </div>
                <!-- 空状态 -->
                <div v-else class="flex flex-col items-center justify-center py-12 text-gray-500">
                    <svg class="w-12 h-12 mb-2 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                            d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
                    </svg>
                    <span class="text-sm">暂无分组</span>
                </div>
            </div>
            <!-- 分组统计 -->
            <div class="px-3 py-2 border-t border-[#1a3a6a] flex-shrink-0 text-gray-500 text-xs">
                共 {{ filteredGroupList.length }} / {{ groupList.length }} 个分组
            </div>
        </div>

        <!-- 右侧主区域 -->
        <div class="flex-1 flex flex-col p-4 min-w-0">
            <!-- 顶部操作栏 -->
            <div class="flex items-center justify-between mb-4">
                <div class="flex items-center gap-4">
                    <h1 class="text-white text-lg font-medium">地图标注管理</h1>
                    <span v-if="currentGroup" class="text-blue-400 text-sm">
                        当前分组: {{ currentGroup.group_name }}
                    </span>
                </div>
                <div class="flex items-center gap-3">
                    <!-- 视图切换按钮组 -->
                    <div class="flex rounded-md overflow-hidden border border-gray-600">
                        <button v-for="mode in viewModes" :key="mode.value" @click="viewMode = mode.value"
                            class="px-3 py-1.5 text-sm transition-colors" :class="viewMode === mode.value
                                ? 'bg-blue-600 text-white'
                                : 'bg-gray-700 text-gray-300 hover:bg-gray-600'">
                            {{ mode.label }}
                        </button>
                    </div>
                    <!-- 新增元素下拉菜单 -->
                    <div class="relative" ref="addMenuRef">
                        <button @click="toggleAddMenu" :disabled="!currentGroup || isGroupLocked"
                            class="flex items-center gap-2 px-4 py-1.5 bg-blue-600 hover:bg-blue-500 disabled:bg-gray-600 disabled:cursor-not-allowed text-white text-sm rounded-md transition-colors"
                            :title="isGroupLocked ? '该分组已锁定，无法新增元素' : ''">
                            <PlusOutlined />
                            <span>新增元素</span>
                            <DownOutlined class="text-xs" />
                        </button>
                        <div v-if="addMenuVisible"
                            class="absolute right-0 top-full mt-1 w-32 bg-gray-800 border border-gray-600 rounded-md shadow-xl z-50 overflow-hidden">
                            <button v-for="item in addMenuItems" :key="item.key" @click="handleAddMenuClick(item.key)"
                                class="w-full flex items-center gap-2 px-3 py-2 text-sm text-gray-300 hover:bg-gray-700 transition-colors">
                                <component :is="item.icon" />
                                <span>{{ item.label }}</span>
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 主内容区域 -->
            <div class="flex-1 flex flex-row gap-4 min-h-0 h-0 overflow-hidden">
                <!-- 地图区域 -->
                <div v-show="viewMode !== 'table'"
                    class="bg-white/[0.02] rounded-lg border border-white/10 overflow-hidden h-[90vh]"
                    :class="viewMode === 'split' ? 'flex-[3] min-w-0' : 'flex-1'">
                    <LayerMapEditor ref="mapEditorRef" :elements="elementList" :drawMode="drawMode"
                        :editingElement="editingElement" :defaultColor="elementForm.resource.content.properties.color"
                        :draftCoordinates="draftCoordinatesForMap"
                        :readonly="isGroupLocked" @draw-complete="onDrawComplete" @draw-cancel="onDrawCancel"
                        @edit-confirmed="onEditConfirmed" @element-clicked="onElementClicked"
                        @edit="showEditElementModal" @delete="confirmDelete" />
                </div>

                <!-- 表格区域 -->
                <div v-show="viewMode !== 'map'"
                    class="bg-white/[0.02] rounded-lg border border-white/10 overflow-hidden flex flex-col min-h-0 h-[80vh]"
                    :class="viewMode === 'split' ? 'flex-[2] min-w-[300px] max-w-[500px]' : 'flex-1'">
                    <!-- 搜索框 -->
                    <div class="px-3 py-2 border-b border-gray-700/50 flex-shrink-0">
                        <div class="relative">
                            <SearchOutlined class="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400 text-sm" />
                            <input v-model="elementSearchKeyword" type="text" placeholder="搜索元素名称..."
                                class="w-full pl-8 pr-8 py-1.5 bg-gray-700/50 border border-gray-600 rounded text-sm text-white placeholder-gray-400 focus:outline-none focus:border-blue-500 transition-colors" />
                            <button v-if="elementSearchKeyword" @click="elementSearchKeyword = ''"
                                class="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white transition-colors">
                                <CloseOutlined class="text-xs" />
                            </button>
                        </div>
                    </div>
                    <!-- 表格头部 -->
                    <div
                        class="flex items-center px-3 py-2 bg-gray-800/50 border-b border-gray-700 text-gray-400 text-xs font-medium flex-shrink-0">
                        <div class="flex-[2] min-w-0">元素名称</div>
                        <div class="w-16 text-center">类型</div>
                        <div class="w-12 text-center">颜色</div>
                        <div class="w-24 text-center">操作</div>
                    </div>
                    <!-- 表格内容 -->
                    <div class="flex-1 overflow-y-auto min-h-0">
                        <!-- 加载中 -->
                        <div v-if="elementLoading" class="flex items-center justify-center py-12">
                            <div
                                class="w-6 h-6 border-2 border-blue-400 border-t-transparent rounded-full animate-spin">
                            </div>
                        </div>
                        <!-- 数据列表 -->
                        <div v-else-if="filteredElementList.length > 0">
                            <div v-for="element in paginatedElementList" :key="element.id"
                                class="flex items-center px-3 py-2 border-b border-gray-700/50 hover:bg-white/[0.03] transition-colors">
                                <!-- 名称 -->
                                <div class="flex-[2] min-w-0 pr-2">
                                    <div @click="flyToElement(element)" :title="element.name"
                                        class="text-blue-400 hover:text-blue-300 text-sm truncate cursor-pointer hover:underline">
                                        {{ element.name }}
                                    </div>
                                </div>
                                <!-- 类型 -->
                                <div class="w-16 text-center">
                                    <span class="px-2 py-0.5 text-xs rounded"
                                        :class="getTypeClass(element.resource?.type)">
                                        {{ getElementTypeName(element.resource?.type) }}
                                    </span>
                                </div>
                                <!-- 颜色 -->
                                <div class="w-12 flex justify-center">
                                    <div class="w-3.5 h-3.5 rounded border border-gray-500"
                                        :style="{ backgroundColor: element.resource?.content?.properties?.color || '#3366FF' }">
                                    </div>
                                </div>
                                <!-- 操作 -->
                                <div class="w-24 flex items-center justify-center gap-2">
                                    <button @click="flyToElement(element)" title="定位"
                                        class="text-green-400 hover:text-green-300 transition-colors text-xs">
                                        <AimOutlined />
                                    </button>
                                    <button v-if="!isGroupLocked" @click="showEditElementModal(element)" title="编辑"
                                        class="text-blue-400 hover:text-blue-300 transition-colors text-xs">
                                        <EditOutlined />
                                    </button>
                                    <button v-if="!isGroupLocked" @click="confirmDelete(element)" title="删除"
                                        class="text-red-400 hover:text-red-300 transition-colors text-xs">
                                        <DeleteOutlined />
                                    </button>
                                    <span v-if="isGroupLocked" class="text-gray-500 text-xs" title="该分组已锁定">
                                        <LockOutlined />
                                    </span>
                                </div>
                            </div>
                        </div>
                        <!-- 搜索无结果 -->
                        <div v-else-if="elementSearchKeyword && elementList.length > 0"
                            class="flex flex-col items-center justify-center py-12 text-gray-500">
                            <SearchOutlined class="text-2xl mb-2 opacity-50" />
                            <span class="text-sm">未找到匹配的元素</span>
                        </div>
                        <!-- 空状态 -->
                        <div v-else class="flex flex-col items-center justify-center py-12 text-gray-500">
                            <svg class="w-12 h-12 mb-2 opacity-50" fill="none" stroke="currentColor"
                                viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                                    d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                            </svg>
                            <span class="text-sm">暂无元素</span>
                        </div>
                    </div>
                    <!-- 分页信息 -->
                    <div v-if="elementList.length > 0"
                        class="px-3 py-1.5 bg-gray-800/30 border-t border-gray-700 text-gray-400 text-xs flex-shrink-0 flex items-center justify-between">
                        <span>共 {{ filteredElementList.length }} 条记录</span>
                        <div v-if="totalPages > 1" class="flex items-center gap-2">
                            <button @click="currentPage > 1 && currentPage--" :disabled="currentPage === 1"
                                class="p-1 hover:text-white disabled:text-gray-600 disabled:cursor-not-allowed transition-colors">
                                <LeftOutlined />
                            </button>
                            <span class="text-gray-300 min-w-[3rem] text-center">{{ currentPage }} / {{ totalPages
                                }}</span>
                            <button @click="currentPage < totalPages && currentPage++"
                                :disabled="currentPage === totalPages"
                                class="p-1 hover:text-white disabled:text-gray-600 disabled:cursor-not-allowed transition-colors">
                                <RightOutlined />
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 无选中分组时的提示 -->
            <div v-if="!currentGroup" class="flex-1 flex flex-col items-center justify-center text-gray-500">
                <svg class="w-16 h-16 mb-4 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                        d="M8 14v3m4-3v3m4-3v3M3 21h18M3 10h18M3 7l9-4 9 4M4 10h16v11H4V10z" />
                </svg>
                <span class="text-base">请先选择一个分组</span>
            </div>
        </div>

        <!-- 新增/编辑元素面板 -->
        <div v-if="elementModalVisible"
            class="fixed top-20 right-5 w-96 bg-gray-800 rounded-lg shadow-2xl border border-gray-600 z-50">
            <div class="flex items-center justify-between px-4 py-3 border-b border-gray-700">
                <h3 class="text-white font-medium">{{ isEditMode ? '编辑元素' : '新增元素' }}</h3>
                <button @click="handleElementModalCancel" class="text-gray-400 hover:text-white transition-colors">
                    <CloseOutlined />
                </button>
            </div>
            <div class="p-4 space-y-4">
                <!-- 元素名称 -->
                <div>
                    <label class="block text-gray-400 text-sm mb-1.5">元素名称</label>
                    <input v-model="elementForm.name" type="text" placeholder="请输入元素名称"
                        class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-md text-white text-sm focus:outline-none focus:border-blue-500 transition-colors" />
                </div>
                <!-- 元素类型 -->
                <div>
                    <label class="block text-gray-400 text-sm mb-1.5">元素类型</label>
                    <span class="px-2 py-1 text-xs rounded" :class="getTypeClass(elementForm.resource.type)">
                        {{ getElementTypeName(elementForm.resource.type) }}
                    </span>
                </div>
                <!-- 颜色 -->
                <div>
                    <label class="block text-gray-400 text-sm mb-1.5">颜色</label>
                    <div class="flex items-center gap-3">
                        <input v-model="elementForm.resource.content.properties.color" type="color"
                            class="w-10 h-8 rounded border border-gray-600 cursor-pointer" />
                        <span class="text-gray-300 text-sm font-mono">{{
                            elementForm.resource.content.properties.color }}</span>
                    </div>
                </div>
                <!-- 点类型显示坐标 -->
                <template v-if="elementForm.resource.type === 0">
                    <div class="grid grid-cols-2 gap-3">
                        <div>
                            <label class="block text-gray-400 text-sm mb-1.5">经度</label>
                            <input v-model.number="longitude" type="number" step="0.00000001" placeholder="经度"
                                class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-md text-white text-sm focus:outline-none focus:border-blue-500" />
                        </div>
                        <div>
                            <label class="block text-gray-400 text-sm mb-1.5">纬度</label>
                            <input v-model.number="latitude" type="number" step="0.00000001" placeholder="纬度"
                                class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-md text-white text-sm focus:outline-none focus:border-blue-500" />
                        </div>
                    </div>
                </template>
                <!-- 线/面类型显示点数 -->
                <div v-else>
                    <label class="block text-gray-400 text-sm mb-1.5">坐标点数</label>
                    <span class="text-blue-400 font-medium">{{ getCoordinatesCount() }} 个点</span>
                </div>
                <!-- 地图操作提示 -->
                <div v-if="!isEditMode"
                    class="flex items-center gap-2 px-3 py-2 bg-blue-500/10 border border-blue-500/30 rounded-md">
                    <InfoCircleOutlined class="text-blue-400" />
                    <span class="text-blue-300 text-sm">
                        请在地图上{{ elementForm.resource.type === 0 ? '点击选择位置' : '右键完成绘制' }}
                    </span>
                </div>
            </div>
            <div class="flex items-center justify-end gap-3 px-4 py-3 border-t border-gray-700">
                <button @click="handleElementModalCancel"
                    class="px-4 py-1.5 text-sm text-gray-300 hover:text-white border border-gray-600 rounded-md hover:bg-gray-700 transition-colors">
                    取消
                </button>
                <button @click="handleElementSubmit" :disabled="elementModalLoading"
                    class="px-4 py-1.5 text-sm text-white bg-blue-600 hover:bg-blue-500 disabled:bg-gray-600 rounded-md transition-colors flex items-center gap-2">
                    <span v-if="elementModalLoading"
                        class="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                    <span>{{ isEditMode ? '保存' : '创建' }}</span>
                </button>
            </div>
        </div>

        <!-- 删除确认弹窗 -->
        <div v-if="deleteConfirmVisible" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
            <div class="bg-gray-800 rounded-lg shadow-2xl border border-gray-600 w-80">
                <div class="px-4 py-3 border-b border-gray-700">
                    <h3 class="text-white font-medium">确认删除</h3>
                </div>
                <div class="p-4 text-gray-300">
                    确定要删除元素 "{{ deletingElement?.name }}" 吗？
                </div>
                <div class="flex items-center justify-end gap-3 px-4 py-3 border-t border-gray-700">
                    <button @click="deleteConfirmVisible = false"
                        class="px-4 py-1.5 text-sm text-gray-300 hover:text-white border border-gray-600 rounded-md hover:bg-gray-700 transition-colors">
                        取消
                    </button>
                    <button @click="handleDeleteElement"
                        class="px-4 py-1.5 text-sm text-white bg-red-600 hover:bg-red-500 rounded-md transition-colors">
                        删除
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted, computed, onBeforeUnmount, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
    ReloadOutlined,
    PlusOutlined,
    EditOutlined,
    DeleteOutlined,
    DownOutlined,
    EnvironmentOutlined,
    LineOutlined,
    BorderOutlined,
    AimOutlined,
    InfoCircleOutlined,
    CloseOutlined,
    SearchOutlined,
    LockOutlined,
    LeftOutlined,
    RightOutlined
} from '@ant-design/icons-vue'
import {
    getGroupList,
    getGroupElementsList,
    postElementsReq,
    updateElementsReq,
    deleteElementReq
} from '@/api/layer'
import LayerMapEditor from '@/components/LayerMapEditor.vue'

// 视图模式
const viewMode = ref<'split' | 'map' | 'table'>('split')
const viewModes: { value: 'split' | 'map' | 'table'; label: string }[] = [
    { value: 'split', label: '分屏' },
    { value: 'map', label: '地图' },
    { value: 'table', label: '表格' }
]

// 地图编辑器引用
const mapEditorRef = ref<InstanceType<typeof LayerMapEditor>>()

// 绘制模式
const drawMode = ref<'point' | 'line' | 'polygon' | null>(null)
const editingElement = ref<any>(null)

// 分组相关
const groupLoading = ref(false)
const groupList = ref<any[]>([])
const selectedGroupKeys = ref<string[]>([])
const groupSearchKeyword = ref('')

const currentGroup = computed(() => {
    return groupList.value.find(g => g.group_id === selectedGroupKeys.value[0])
})

// 分组是否被锁定
const isGroupLocked = computed(() => {
    return currentGroup.value?.is_lock === true
})

// 过滤后的分组列表
const filteredGroupList = computed(() => {
    if (!groupSearchKeyword.value.trim()) {
        return groupList.value
    }
    const keyword = groupSearchKeyword.value.toLowerCase().trim()
    return groupList.value.filter(g =>
        g.group_name?.toLowerCase().includes(keyword)
    )
})

// 元素相关
const elementLoading = ref(false)
const elementList = ref<any[]>([])
const elementSearchKeyword = ref('')

const filteredElementList = computed(() => {
    if (!elementSearchKeyword.value.trim()) {
        return elementList.value
    }
    const keyword = elementSearchKeyword.value.toLowerCase().trim()
    return elementList.value.filter(e =>
        e.name?.toLowerCase().includes(keyword)
    )
})

// 分页相关
const currentPage = ref(1)
const pageSize = ref(10)

const totalPages = computed(() => {
    return Math.ceil(filteredElementList.value.length / pageSize.value) || 1
})

const paginatedElementList = computed(() => {
    const start = (currentPage.value - 1) * pageSize.value
    const end = start + pageSize.value
    return filteredElementList.value.slice(start, end)
})

// 搜索关键词变化重置分页
watch(elementSearchKeyword, () => {
    currentPage.value = 1
})

// 新增菜单
const addMenuVisible = ref(false)
const addMenuRef = ref<HTMLElement>()
const addMenuItems = [
    { key: 'point', label: '标记点', icon: EnvironmentOutlined },
    { key: 'line', label: '线', icon: LineOutlined },
    { key: 'polygon', label: '面', icon: BorderOutlined }
]

// 生成 UUID (兼容非安全上下文)
function generateUUID(): string {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
        return crypto.randomUUID()
    }
    // Fallback for non-secure contexts
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0
        const v = c === 'x' ? r : (r & 0x3) | 0x8
        return v.toString(16)
    })
}

// 元素模态框
const elementModalVisible = ref(false)
const elementModalLoading = ref(false)
const isEditMode = ref(false)
const editingElementId = ref<string>('')

const elementForm = reactive({
    id: '',
    name: '',
    resource: {
        type: 0 as 0 | 1 | 2,
        content: {
            type: 'Feature',
            properties: {
                color: '#3366FF',
                clampToGround: true
            },
            geometry: {
                type: 'Point',
                coordinates: [0, 0] as unknown
            }
        }
    }
})

// 单独的经纬度变量
const longitude = ref<number>(0)
const latitude = ref<number>(0)

// 临时存储绘制的坐标
const drawnCoordinates = ref<any>(null)

// 地图草稿预览坐标 (用于在弹窗未提交保存时即时地图回显)
const draftCoordinatesForMap = computed(() => {
    if (!elementModalVisible.value) return null
    if (elementForm.resource.type === 0) {
        if (longitude.value && latitude.value) {
            return [longitude.value, latitude.value]
        }
    } else {
        return drawnCoordinates.value
    }
    return null
})

// 点击外部关闭菜单
function handleClickOutside(e: MouseEvent) {
    if (addMenuRef.value && !addMenuRef.value.contains(e.target as Node)) {
        addMenuVisible.value = false
    }
}

onMounted(() => {
    loadGroups()
    document.addEventListener('click', handleClickOutside)
})

onBeforeUnmount(() => {
    document.removeEventListener('click', handleClickOutside)
})

// 切换新增菜单
function toggleAddMenu() {
    if (currentGroup.value) {
        addMenuVisible.value = !addMenuVisible.value
    }
}

// 加载分组列表
async function loadGroups() {
    groupLoading.value = true
    try {
        const res = await getGroupList(0)
        if (res.code === 0 && res.data) {
            groupList.value = res.data
            if (groupList.value.length > 0 && selectedGroupKeys.value.length === 0) {
                selectedGroupKeys.value = [groupList.value[0].group_id]
                loadElements()
            }
        } else {
            message.error('获取分组列表失败: ' + (res.message || '未知错误'))
        }
    } catch (error: any) {
        message.error('获取分组列表失败: ' + (error.message || '未知错误'))
    } finally {
        groupLoading.value = false
    }
}

// 选择分组
function selectGroup(group: any) {
    selectedGroupKeys.value = [group.group_id]
    loadElements()
}

// 加载元素列表
async function loadElements() {
    if (!currentGroup.value) return

    currentPage.value = 1
    elementLoading.value = true
    try {
        const res = await getGroupElementsList(currentGroup.value.group_id)
        if (res.code === 0 && res.data) {
            elementList.value = res.data.list || res.data || []
        } else {
            elementList.value = []
        }
    } catch (error: any) {
        message.error('获取元素列表失败: ' + (error.message || '未知错误'))
        elementList.value = []
    } finally {
        elementLoading.value = false
    }
}

// 新增菜单点击
function handleAddMenuClick(key: string) {
    addMenuVisible.value = false

    if (!currentGroup.value) {
        message.warning('请先选择一个分组')
        return
    }

    isEditMode.value = false
    editingElementId.value = ''
    drawnCoordinates.value = null
    resetElementForm()

    const typeMap: Record<string, 0 | 1 | 2> = { point: 0, line: 1, polygon: 2 }
    const geoTypeMap: Record<string, string> = { point: 'Point', line: 'LineString', polygon: 'Polygon' }

    elementForm.resource.type = typeMap[key]
    elementForm.resource.content.geometry.type = geoTypeMap[key]
    elementForm.name = key === 'point' ? '标记点' : key === 'line' ? '路线' : '区域'

    drawMode.value = key as 'point' | 'line' | 'polygon'
    elementModalVisible.value = true
}

// 显示编辑元素模态框
function showEditElementModal(record: any) {
    isEditMode.value = true
    editingElementId.value = record.id
    editingElement.value = record

    elementForm.name = record.name
    elementForm.resource = JSON.parse(JSON.stringify(record.resource || {
        type: 0,
        content: {
            type: 'Feature',
            properties: { color: '#3366FF' },
            geometry: { type: 'Point', coordinates: [0, 0] }
        }
    }))

    const coords = record.resource?.content?.geometry?.coordinates || [0, 0]
    if (record.resource?.type === 0) {
        longitude.value = coords[0] || 0
        latitude.value = coords[1] || 0
    }

    drawnCoordinates.value = coords
    elementModalVisible.value = true

    // 切换到分屏视图以便在地图上编辑
    if (viewMode.value === 'table') {
        viewMode.value = 'split'
    }
}

// 重置表单
function resetElementForm() {
    elementForm.id = ''
    elementForm.name = ''
    elementForm.resource = {
        type: 0,
        content: {
            type: 'Feature',
            properties: { color: '#3366FF', clampToGround: true },
            geometry: { type: 'Point', coordinates: [0, 0] }
        }
    }
    longitude.value = 0
    latitude.value = 0
}

// 地图绘制/编辑完成
function onDrawComplete(data: { type: string; coordinates: any }) {
    drawnCoordinates.value = data.coordinates

    if (data.type === 'point' && data.coordinates) {
        longitude.value = data.coordinates[0]
        latitude.value = data.coordinates[1]
    }

    drawMode.value = null
    editingElement.value = null

    if (isEditMode.value) {
        message.success('位置已更新，请确认后提交保存')
    } else {
        message.success('绘制完成，请填写元素信息后提交')
    }
}

// 地图绘制/编辑取消
function onDrawCancel() {
    drawMode.value = null
    editingElement.value = null

    if (!isEditMode.value) {
        elementModalVisible.value = false
        resetElementForm()
    }
}

// 地图编辑确认 - 临时保存到表单，等待用户提交
function onEditConfirmed(data: { type: string; coordinates: any }) {
    drawnCoordinates.value = data.coordinates

    if (data.type === 'point' && data.coordinates) {
        longitude.value = data.coordinates[0]
        latitude.value = data.coordinates[1]
    }

    drawMode.value = null
    editingElement.value = null

    message.success('位置已更新，点击保存提交修改，点击取消恢复原位置')
}

// 地图元素点击
function onElementClicked(element: any) {
    console.log('Element clicked:', element)
}

// 飞到元素位置
function flyToElement(element: any) {
    mapEditorRef.value?.flyToElement(element)

    if (viewMode.value === 'table') {
        viewMode.value = 'split'
    }
}

// 获取坐标点数量
function getCoordinatesCount() {
    if (!drawnCoordinates.value) return 0

    if (elementForm.resource.type === 1) {
        return Array.isArray(drawnCoordinates.value) ? drawnCoordinates.value.length : 0
    } else if (elementForm.resource.type === 2) {
        return Array.isArray(drawnCoordinates.value?.[0]) ? drawnCoordinates.value[0].length : 0
    }
    return 0
}

// 提交元素表单
async function handleElementSubmit() {
    if (!elementForm.name.trim()) {
        message.warning('请输入元素名称')
        return
    }

    if (elementForm.resource.type === 0) {
        if (!longitude.value || !latitude.value) {
            message.warning('请在地图上选择位置或输入经纬度')
            return
        }
    } else {
        if (!drawnCoordinates.value ||
            (elementForm.resource.type === 1 && drawnCoordinates.value.length < 2) ||
            (elementForm.resource.type === 2 && (!drawnCoordinates.value[0] || drawnCoordinates.value[0].length < 3))) {
            message.warning('请在地图上完成绘制')
            return
        }
    }

    elementModalLoading.value = true
    try {
        let coordinates: any
        if (elementForm.resource.type === 0) {
            coordinates = [longitude.value, latitude.value]
        } else {
            coordinates = drawnCoordinates.value
        }

        // 构建 GeoJSON content
        const content = {
            ...elementForm.resource.content,
            geometry: {
                ...elementForm.resource.content.geometry,
                coordinates
            }
        }

        if (isEditMode.value) {
            // 编辑模式：PutElementsBody 需要 name 和 content 作为顶层字段
            const body = {
                name: elementForm.name,
                content: content
            }
            const res = await updateElementsReq(editingElementId.value, body)
            if (res.code === 0) {
                message.success('编辑成功')
                // 清除临时编辑状态
                mapEditorRef.value?.clearPendingEdit()
                elementModalVisible.value = false
                loadElements()
            } else {
                message.error('编辑失败: ' + (res.message || '未知错误'))
            }
        } else {
            // 新增模式：PostElementsBody 需要 id, name, resource 结构
            const body = {
                id: generateUUID(),
                name: elementForm.name,
                resource: {
                    ...elementForm.resource,
                    content: content
                }
            }
            const res = await postElementsReq(currentGroup.value.group_id, body)
            if (res.code === 0) {
                message.success('新增成功')
                elementModalVisible.value = false
                loadElements()
            } else {
                message.error('新增失败: ' + (res.message || '未知错误'))
            }
        }
    } catch (error: any) {
        message.error((isEditMode.value ? '编辑' : '新增') + '失败: ' + (error.message || '未知错误'))
    } finally {
        elementModalLoading.value = false
        drawMode.value = null
        editingElement.value = null
        drawnCoordinates.value = null
    }
}

// 取消模态框
function handleElementModalCancel() {
    // 编辑模式下恢复原始位置
    if (isEditMode.value) {
        mapEditorRef.value?.revertEdit()
    }

    elementModalVisible.value = false
    drawMode.value = null
    editingElement.value = null
    drawnCoordinates.value = null
    resetElementForm()
}

// 删除确认
const deleteConfirmVisible = ref(false)
const deletingElement = ref<any>(null)

// 确认删除
function confirmDelete(element: any) {
    deletingElement.value = element
    deleteConfirmVisible.value = true
}

// 删除元素
async function handleDeleteElement() {
    if (!deletingElement.value) return

    try {
        const res = await deleteElementReq(deletingElement.value.id, {})
        if (res.code === 0) {
            message.success('删除成功')
            loadElements()
        } else {
            message.error('删除失败: ' + (res.message || '未知错误'))
        }
    } catch (error: any) {
        message.error('删除失败: ' + (error.message || '未知错误'))
    } finally {
        deleteConfirmVisible.value = false
        deletingElement.value = null
    }
}

// 获取元素类型名称
function getElementTypeName(type: number): string {
    const typeMap: Record<number, string> = { 0: '点', 1: '线', 2: '面' }
    return typeMap[type] || '未知'
}

// 获取类型样式类
function getTypeClass(type: number): string {
    const classMap: Record<number, string> = {
        0: 'bg-blue-500/20 text-blue-400',
        1: 'bg-green-500/20 text-green-400',
        2: 'bg-purple-500/20 text-purple-400'
    }
    return classMap[type] || 'bg-gray-500/20 text-gray-400'
}
</script>

<style scoped>
/* Tailwind already handles most styling, minimal custom CSS needed */
input[type="color"] {
    appearance: none;
    -webkit-appearance: none;
    padding: 0;
}

input[type="color"]::-webkit-color-swatch-wrapper {
    padding: 2px;
}

input[type="color"]::-webkit-color-swatch {
    border: none;
    border-radius: 4px;
}
</style>
