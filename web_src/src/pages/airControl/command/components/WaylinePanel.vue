<template>
  <div class="wayline-panel-container">
    <div class="search-bar">
      <a-input placeholder="搜索航线名称" v-model:value="searchText" class="search-input">
        <template #prefix>
          <SearchOutlined class="search-icon" />
        </template>
      </a-input>
    </div>

    <div class="wayline-list" @scroll="onScroll">
      <div v-if="loading" class="loading-container">
        <a-spin size="large" />
      </div>

      <div v-else-if="waylinesData.data.length === 0" class="empty-container">
        <a-empty description="暂无航线数据" />
      </div>

      <div v-else>
        <div v-for="wayline in filteredWaylines" :key="wayline.id" class="wayline-item" @click="selectWayline(wayline)" :class="{ selected: selectedWaylineId === wayline.id }">
          <div class="wayline-header">
            <div class="wayline-name">
              <a-tooltip :title="wayline.name">
                <div class="name-text">{{ wayline.name }}</div>
              </a-tooltip>
            </div>
            <div class="wayline-user">
              <UserOutlined class="user-icon" />
              <a-tooltip :title="wayline.user_name">
                <span class="user-name">{{ wayline.user_name }}</span>
              </a-tooltip>
            </div>
          </div>

          <div class="wayline-details">
            <div class="device-info icon-center">
              <svg-icon name="drone" class="device-icon" :size="14" />
              <span class="device-name">{{ DEVICE_NAME[wayline.drone_model_key] || wayline.drone_model_key }}</span>
            </div>

            <div class="payload-info icon-center" v-if="wayline.payload_model_keys && wayline.payload_model_keys.length > 0">
              <svg-icon name="playload" class="payload-icon" :size="14" />
              <span class="payload-names">
                <span v-for="(payload, index) in wayline.payload_model_keys" :key="index">
                  {{ DEVICE_NAME[payload] || payload }}
                  <span v-if="index < wayline.payload_model_keys.length - 1">, </span>
                </span>
              </span>
            </div>
          </div>

          <div class="wayline-meta">
            <span class="update-time">更新时间: {{ formatTime(wayline.update_time) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import { SearchOutlined, UserOutlined } from "@ant-design/icons-vue";
import { getWaylineFiles } from "@/api/wayline";
import { ELocalStorageKey } from "@/types";
import { WaylineFile } from "@/types/wayline";
import { DEVICE_NAME } from "@/types/device";
import { IPage } from "@/api/http/type";

const emit = defineEmits<{
  "select-wayline": [wayline: WaylineFile];
}>();

const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId)!;

const loading = ref(false);
const searchText = ref("");
const selectedWaylineId = ref<string>("");

const waylinesData = reactive({
  data: [] as WaylineFile[],
  pagination: {
    page: 1,
    page_size: 50,
    total: 0,
  } as IPage,
});

// 过滤的航线数据
const filteredWaylines = computed(() => {
  if (!searchText.value.trim()) {
    return waylinesData.data;
  }

  return waylinesData.data.filter((wayline) => wayline.name.toLowerCase().includes(searchText.value.toLowerCase()) || wayline.user_name.toLowerCase().includes(searchText.value.toLowerCase()));
});

// 获取航线数据
const getWaylines = async () => {
  loading.value = true;
  try {
    const params = {
      page: waylinesData.pagination.page,
      page_size: waylinesData.pagination.page_size,
      order_by: "update_time",
    };
    const res = await getWaylineFiles(workspaceId, params);
    if (res.code === 0) {
      waylinesData.data = res.data.list;
      waylinesData.pagination = res.data.pagination;
    } else {
      message.error("获取航线数据失败");
    }
  } catch (error) {
    console.error("获取航线数据失败:", error);
    message.error("获取航线数据失败");
  } finally {
    loading.value = false;
  }
};

// 选择航线（自动触发）
const selectWayline = (wayline: WaylineFile) => {
  selectedWaylineId.value = wayline.id;
  // 立即触发选择事件
  emit("select-wayline", wayline);
};

// 滚动加载更多
const onScroll = (event: Event) => {
  const target = event.target as HTMLElement;
  if (target.scrollTop + target.clientHeight >= target.scrollHeight - 10) {
    // 可以在这里实现加载更多逻辑
  }
};

// 格式化时间
const formatTime = (timestamp: number) => {
  return new Date(timestamp).toLocaleString();
};

onMounted(() => {
  getWaylines();
});
</script>

<style scoped lang="scss">
.wayline-panel-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.search-bar {
  margin-bottom: 16px;

  .search-input {
    background: #1e3461;
    border-color: #3a558f;
    color: #fff;

    &:hover {
      border-color: #5b8bfa;
    }

    &:focus-within {
      border-color: #3a7af9;
      box-shadow: 0 0 0 2px rgba(58, 122, 249, 0.2);
    }

    .search-icon {
      color: #8fa4c7;
    }
  }
}

.wayline-list {
  flex: 1;
  overflow-y: auto;

  /* 自定义滚动条 */
  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: #1e3461;
    border-radius: 3px;
  }

  &::-webkit-scrollbar-thumb {
    background: #3a558f;
    border-radius: 3px;

    &:hover {
      background: #5b8bfa;
    }
  }
}

.loading-container,
.empty-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
  color: #8fa4c7;
}

.wayline-item {
  background: rgba(9, 25, 39, 0.3);
  border: 1px solid #214565;
  border-radius: 6px;
  padding: 16px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: #3a7af9;
    background: rgba(58, 122, 249, 0.1);
  }

  &.selected {
    border-color: #3a7af9;
    background: rgba(58, 122, 249, 0.2);
    box-shadow: 0 0 0 1px #3a7af9;
  }

  .wayline-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    .wayline-name {
      flex: 1;

      .name-text {
        font-size: 16px;
        font-weight: 500;
        color: #fff;
        max-width: 200px;
        .device-info,
        .payload-info {
          gap: 6px;
          margin-bottom: 4px;
          color: #d6e5fb;
          font-size: 14px;
          .device-icon,
          .payload-icon {
            font-size: 14px;
            color: #3a7af9;
          }
          .device-name,
          .payload-names {
            font-size: 14px;
          }
        }
        .icon-center {
          display: flex;
          align-items: center;
          justify-content: center;
        }
        text-overflow: ellipsis;
        overflow: hidden;
      }
    }
  }

  .wayline-details {
    margin-bottom: 8px;

    .device-info,
    .payload-info {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 4px;
      color: #d6e5fb;
      font-size: 14px;

      .device-icon,
      .payload-icon {
        font-size: 14px;
        color: #3a7af9;
      }

      .device-name,
      .payload-names {
        font-size: 14px;
      }
    }
  }

  .wayline-meta {
    .update-time {
      font-size: 12px;
      color: #8fa4c7;
    }
  }
}

/* 全局覆盖 Ant Design 组件样式 */
:deep(.ant-input) {
  background: #1e3461 !important;
  border-color: #3a558f !important;
  color: #fff !important;

  &::placeholder {
    color: #8fa4c7 !important;
  }

  &:hover {
    border-color: #5b8bfa !important;
  }

  &:focus {
    border-color: #3a7af9 !important;
    box-shadow: 0 0 0 2px rgba(58, 122, 249, 0.2) !important;
  }
}

:deep(.ant-empty-description) {
  color: #8fa4c7 !important;
}
</style>
