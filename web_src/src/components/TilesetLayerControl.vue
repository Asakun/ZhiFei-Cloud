<template>
  <div class="tileset-layer-control-wrapper">
    <a-popover
      :placement="placement"
      trigger="click"
      overlayClassName="tileset-layer-popover"
      :getPopupContainer="(triggerNode: any) => triggerNode.parentElement || document.body"
    >
      <template #content>
        <div class="tileset-popover-panel">
          <div class="tileset-popover-header">
            <div class="header-left">
              <span class="header-icon">🏢</span>
              <span class="header-title">3D 倾斜摄影建模</span>
            </div>
            <span class="header-count" v-if="totalCount > 0">
              共 {{ totalCount }} 项
            </span>
          </div>

          <!-- 关键词搜索框 -->
          <div class="tileset-popover-search">
            <a-input
              v-model:value="searchKeyword"
              placeholder="搜索模型名称..."
              size="small"
              allow-clear
              @change="onKeywordChange"
              @pressEnter="handleSearch"
            >
              <template #prefix>
                <SearchOutlined style="color: #64748b; font-size: 12px;" />
              </template>
            </a-input>
          </div>

          <div class="tileset-popover-list" v-if="tilesetsList.length > 0">
            <div
              v-for="(item, idx) in tilesetsList"
              :key="'ts_' + (item.file_id || item.id || idx)"
              class="tileset-popover-item"
              :class="{ active: item.checked }"
            >
              <div class="item-left">
                <span class="dot" :class="{ on: item.checked }"></span>
                <span class="name" :title="item.name">{{ item.name }}</span>
              </div>
              <div class="item-right">
                <a-switch
                  size="small"
                  :loading="item.loading"
                  v-model:checked="item.checked"
                  @change="(val: boolean) => handleTilesetToggle(val, item)"
                />
              </div>
            </div>
          </div>
          <div v-else class="tileset-popover-empty">
            <span>{{ loading ? '加载中...' : '暂无 3D 建模图层' }}</span>
          </div>

          <!-- 分页控制 -->
          <div class="tileset-popover-pagination" v-if="totalCount > pageSize || totalPages > 1">
            <a-button
              type="text"
              size="small"
              class="pager-btn"
              :disabled="currentPage <= 1 || loading"
              @click="handlePageChange(currentPage - 1)"
            >
              <LeftOutlined style="font-size: 10px;" />
            </a-button>
            <span class="pager-text">{{ currentPage }} / {{ totalPages }}</span>
            <a-button
              type="text"
              size="small"
              class="pager-btn"
              :disabled="currentPage >= totalPages || loading"
              @click="handlePageChange(currentPage + 1)"
            >
              <RightOutlined style="font-size: 10px;" />
            </a-button>
          </div>
        </div>
      </template>

      <!-- 触发按钮：支持 pill 胶囊风格与 button 工具栏按钮风格 -->
      <div
        v-if="variant === 'pill'"
        class="tileset-pill-btn"
        :class="{ active: hasActiveTileset }"
        title="点击配置 3D 倾斜摄影建模图层"
      >
        <span class="pill-icon">🏢</span>
        <span class="pill-text">{{ buttonText }}</span>
        <span class="pill-badge" v-if="activeTilesetCount > 0">{{ activeTilesetCount }}</span>
        <span class="active-pulse-dot" v-else-if="hasActiveTileset"></span>
      </div>

      <a-button
        v-else
        class="tool-btn tileset-btn"
        :class="[buttonClass, { active: hasActiveTileset }]"
      >
        <span class="btn-icon">🏢</span>
        <span>{{ buttonText }}</span>
        <span class="active-dot" v-if="hasActiveTileset"></span>
      </a-button>
    </a-popover>
  </div>
</template>

<script setup lang="ts">
import * as Cesium from 'cesium'
import { ref, computed, onMounted, onBeforeUnmount, markRaw, watch, type PropType } from 'vue'
import { SearchOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getTilesets } from '@/api/manage'

export interface TilesetItem {
  id?: number
  file_id?: string
  name: string
  url: string
  object_key?: string
  checked: boolean
  loading?: boolean
  primitive?: any
}

const props = defineProps({
  /** Cesium Viewer 实例或引用 */
  viewer: {
    type: Object as PropType<any>,
    default: null
  },
  /** 按钮外观模式：'button' (默认按钮) 或 'pill' (科技风胶囊) */
  variant: {
    type: String as PropType<'button' | 'pill'>,
    default: 'button'
  },
  /** 按钮文字 */
  buttonText: {
    type: String,
    default: '3D 建模'
  },
  /** 自定义按钮类名 */
  buttonClass: {
    type: String,
    default: ''
  },
  /** 弹窗方位 */
  placement: {
    type: String as PropType<'bottom' | 'bottomRight' | 'bottomLeft' | 'top' | 'topRight' | 'topLeft'>,
    default: 'bottomRight'
  },
  /** 几何误差容忍度 (数值越小精度越高，默认 0.3) */
  maximumScreenSpaceError: {
    type: Number,
    default: 0.1
  },
  /** 显存/内存缓存容量 (字节数，默认 512MB) */
  cacheBytes: {
    type: Number,
    default: 512 * 1024 * 1024
  },
  /** 组件挂载后是否自动拉取 3D Tileset 列表 */
  autoFetch: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits<{
  (e: 'change', payload: { item: TilesetItem; checked: boolean }): void
  (e: 'loaded', payload: { item: TilesetItem; primitive: any }): void
  (e: 'removed', payload: { item: TilesetItem }): void
}>()

const tilesetsList = ref<TilesetItem[]>([])
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(5)
const totalCount = ref(0)
const loading = ref(false)
const checkedMap = ref<Record<string, boolean>>({})

const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value) || 1)
const hasActiveTileset = computed(() => Object.values(checkedMap.value).some(Boolean))
const activeTilesetCount = computed(() => Object.values(checkedMap.value).filter(Boolean).length)

// 获取真实的 viewer 实例（兼容 ref 或普通对象）
const getViewerInstance = () => {
  if (!props.viewer) return null
  return props.viewer.value || props.viewer
}

// 获取倾斜摄影图层列表
const fetchTilesetsList = async () => {
  loading.value = true
  try {
    const res = await getTilesets({
      page: currentPage.value,
      page_size: pageSize.value,
      keyword: searchKeyword.value.trim() || undefined
    })
    let rawList: any[] = []
    if (res && (res.code === 200 || res.code === 0) && res.data) {
      if (Array.isArray(res.data)) {
        rawList = res.data
        totalCount.value = rawList.length
      } else if (Array.isArray(res.data.list)) {
        rawList = res.data.list
        totalCount.value = res.data.pagination?.total ?? rawList.length
      }
    } else {
      rawList = []
      totalCount.value = 0
    }
    tilesetsList.value = rawList.map((item: any) => {
      const key = item.file_id || item.object_key || item.name
      const isChecked = checkedMap.value[key] ?? false
      return {
        ...item,
        name: item.name,
        url: item.url || (item.object_key ? (item.object_key.startsWith('/') ? item.object_key : `/${item.object_key}`) : ''),
        checked: isChecked,
        loading: false,
        primitive: null
      }
    })
  } catch (err) {
    console.warn('获取3D建模列表失败:', err)
    tilesetsList.value = []
    totalCount.value = 0
  } finally {
    loading.value = false
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

// 移除单个 Tileset Primitive
const removeTilesetPrimitive = (item: TilesetItem) => {
  const viewer = getViewerInstance()
  if (!viewer || viewer.isDestroyed?.()) return

  // 1. 移除 item 自身持有的 primitive
  if (item.primitive) {
    try {
      item.primitive.show = false
      viewer.scene.primitives.remove(item.primitive)
      if (typeof item.primitive.destroy === 'function' && !item.primitive.isDestroyed?.()) {
        item.primitive.destroy()
      }
    } catch (e) {
      console.warn('移除 primitive 出错:', e)
    }
    item.primitive = null
  }

  // 2. 深度遍历场景图元集合，清理 URL 匹配的残留 Tileset 实例
  try {
    const primitives = viewer.scene.primitives
    const len = primitives.length
    for (let i = len - 1; i >= 0; i--) {
      const p = primitives.get(i)
      if (p) {
        const pUrl = p.url || p._url || p.resource?._url || p.resource?.url || ''
        if (pUrl && (pUrl.includes(item.url) || item.url.includes(pUrl))) {
          p.show = false
          primitives.remove(p)
          if (typeof p.destroy === 'function' && !p.isDestroyed?.()) {
            p.destroy()
          }
        }
      }
    }
  } catch (e) {
    console.warn('清理残留 3D Tileset 失败:', e)
  }

  // 3. 触发场景按需重绘
  try {
    viewer.scene.requestRender()
  } catch {}

  emit('removed', { item })
}

// 切换某个 3D Tileset 的开/关
const handleTilesetToggle = async (checked: boolean, item: TilesetItem) => {
  const viewer = getViewerInstance()
  if (!viewer) {
    message.warning('三维地图尚未初始化就绪')
    item.checked = false
    return
  }

  if (checked) {
    removeTilesetPrimitive(item)
    item.loading = true
    try {
      const tileset = await Cesium.Cesium3DTileset.fromUrl(item.url, {
        maximumScreenSpaceError: props.maximumScreenSpaceError,
        cacheBytes: props.cacheBytes,
        skipLevelOfDetail: true,
        preferLeaves: false
      })

      const currentViewer = getViewerInstance()
      if (item.checked && currentViewer && !currentViewer.isDestroyed?.()) {
        item.primitive = markRaw(currentViewer.scene.primitives.add(tileset))
        try {
          currentViewer.scene.requestRender()
        } catch {}
        message.success(`已加载【${item.name}】3D建模`)
        emit('loaded', { item, primitive: item.primitive })
      } else {
        tileset.show = false
        if (!tileset.isDestroyed?.()) tileset.destroy()
      }
    } catch (e) {
      console.error(`加载3D建模【${item.name}】失败:`, e)
      message.error(`加载3D建模【${item.name}】失败`)
      item.checked = false
    } finally {
      item.loading = false
    }
  } else {
    removeTilesetPrimitive(item)
    message.info(`已关闭【${item.name}】3D建模`)
  }

  emit('change', { item, checked })
}

// 清理所有图层
const clearAllTilesets = () => {
  tilesetsList.value.forEach(item => {
    item.checked = false
    removeTilesetPrimitive(item)
  })
}

// 监听 viewer 变化（如切换路由或重建 viewer 时清理旧图层）
watch(() => props.viewer, (newViewer, oldViewer) => {
  if (oldViewer && newViewer !== oldViewer) {
    clearAllTilesets()
  }
})

onMounted(() => {
  if (props.autoFetch) {
    fetchTilesetsList()
  }
})

onBeforeUnmount(() => {
  clearAllTilesets()
})

defineExpose({
  tilesetsList,
  hasActiveTileset,
  activeTilesetCount,
  fetchTilesetsList,
  handleTilesetToggle,
  removeTilesetPrimitive,
  clearAllTilesets
})
</script>

<style scoped lang="scss">
.tileset-layer-control-wrapper {
  display: inline-flex;
  align-items: center;
}

/* ====== 胶囊 (Pill) 风格按钮 (用于 wayLineRecord 等深色状态栏) ====== */
.tileset-pill-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: rgba(11, 15, 25, 0.85);
  backdrop-filter: blur(15px);
  -webkit-backdrop-filter: blur(15px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  color: #94a3b8;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  user-select: none;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);

  .pill-icon {
    font-size: 15px;
    transition: transform 0.2s ease;
  }

  .pill-text {
    letter-spacing: 0.3px;
  }

  .pill-badge {
    padding: 0 6px;
    height: 18px;
    line-height: 18px;
    font-size: 11px;
    font-weight: 700;
    color: #ffffff;
    background: linear-gradient(135deg, #0284c7 0%, #38bdf8 100%);
    border-radius: 9px;
  }

  .active-pulse-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: #38bdf8;
    box-shadow: 0 0 8px #38bdf8;
  }

  &:hover {
    background: rgba(15, 23, 42, 0.95);
    border-color: rgba(56, 189, 248, 0.4);
    color: #38bdf8;
    transform: translateY(-1px);

    .pill-icon {
      transform: scale(1.15);
    }
  }

  &.active {
    background: rgba(14, 116, 144, 0.25);
    border-color: rgba(56, 189, 248, 0.6);
    color: #38bdf8;
    box-shadow: 0 0 16px rgba(56, 189, 248, 0.25);
  }
}

/* ====== 标准按钮 (Button) 风格 ====== */
.tool-btn.tileset-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #e2e8f0;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  height: 32px;
  padding: 0 12px;
  transition: all 0.2s ease;

  .btn-icon {
    font-size: 14px;
  }

  .active-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #38bdf8;
    box-shadow: 0 0 6px #38bdf8;
    margin-left: 2px;
  }

  &:hover {
    background: rgba(56, 189, 248, 0.15);
    border-color: rgba(56, 189, 248, 0.45);
    color: #38bdf8;
  }

  &.active {
    background: rgba(56, 189, 248, 0.2);
    border-color: rgba(56, 189, 248, 0.6);
    color: #38bdf8;
  }
}

/* ====== 弹窗面板内容样式 ====== */
.tileset-popover-panel {
  width: 270px;
  padding: 14px;
}

.tileset-popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px solid rgba(56, 189, 248, 0.15);

  .header-left {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .header-icon {
    font-size: 16px;
  }

  .header-title {
    font-size: 13px;
    font-weight: 700;
    color: #38bdf8;
  }

  .header-count {
    font-size: 11px;
    color: #34d399;
    background: rgba(52, 211, 153, 0.12);
    border: 1px solid rgba(52, 211, 153, 0.3);
    padding: 1px 6px;
    border-radius: 4px;
  }
}

.tileset-popover-search {
  margin-bottom: 10px;

  :deep(.ant-input-affix-wrapper) {
    background: rgba(15, 23, 42, 0.6) !important;
    border: 1px solid rgba(56, 189, 248, 0.25) !important;
    border-radius: 6px;

    &:hover, &:focus, &.ant-input-affix-wrapper-focused {
      border-color: rgba(56, 189, 248, 0.6) !important;
      box-shadow: 0 0 8px rgba(56, 189, 248, 0.2) !important;
    }

    .ant-input {
      background: transparent !important;
      color: #e2e8f0;
      font-size: 12px;

      &::placeholder {
        color: #64748b;
      }
    }

    .ant-input-clear-icon {
      color: #64748b;
      &:hover {
        color: #94a3b8;
      }
    }
  }
}

.tileset-popover-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}

.tileset-popover-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(56, 189, 248, 0.08);
    border-color: rgba(56, 189, 248, 0.3);
  }

  &.active {
    background: rgba(56, 189, 248, 0.12);
    border-color: rgba(56, 189, 248, 0.4);
  }

  .item-left {
    display: flex;
    align-items: center;
    gap: 8px;
    overflow: hidden;
    max-width: 180px;

    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #64748b;
      flex-shrink: 0;
      transition: all 0.2s ease;

      &.on {
        background: #38bdf8;
        box-shadow: 0 0 6px #38bdf8;
      }
    }

    .name {
      font-size: 13px;
      color: #f1f5f9;
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}

.tileset-popover-empty {
  padding: 16px 0;
  text-align: center;
  font-size: 12px;
  color: #64748b;
}

.tileset-popover-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid rgba(56, 189, 248, 0.15);

  .pager-text {
    font-size: 11px;
    color: #94a3b8;
  }

  .pager-btn {
    padding: 0 6px;
    height: 22px;
    line-height: 22px;
    color: #38bdf8;

    &:disabled {
      color: #475569;
    }
  }
}
</style>

<style lang="scss">
/* ====== 弹窗深色毛玻璃主题全局样式 ====== */
.tileset-layer-popover.ant-popover {
  padding-top: 6px;

  .ant-popover-content {
    border-radius: 10px;
  }

  .ant-popover-inner {
    background: rgba(10, 25, 47, 0.95) !important;
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    border: 1px solid rgba(56, 189, 248, 0.35) !important;
    border-radius: 10px !important;
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.75) !important;
    padding: 0 !important;
  }

  .ant-popover-arrow {
    display: none !important;
  }

  .ant-popover-inner-content {
    padding: 0 !important;
    color: #e2e8f0 !important;
  }
}
</style>
