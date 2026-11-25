<template>
  <div class="device-panel-container">
    <div class="search-bar">
      <a-input placeholder="搜索设备名称" v-model:value="searchText" class="search-input">
        <template #prefix>
          <SearchOutlined class="search-icon" />
        </template>
      </a-input>
    </div>

    <div class="device-list" @scroll="onScroll">
      <div v-if="loading" class="loading-container">
        <a-spin size="large" />
      </div>

      <div v-else-if="devicesData.data.length === 0" class="empty-container">
        <a-empty description="暂无设备数据" />
      </div>

      <div v-else>
        <div v-for="device in filteredDevices" :key="device.device_sn" class="device-item" @click="selectDevice(device)" :class="{ selected: selectedDeviceSn === device.device_sn }">
          <div class="device-header">
            <div class="device-name">
              <a-tooltip :title="device.nickname">
                <div class="name-text">{{ device.nickname }}</div>
              </a-tooltip>
            </div>
            <div class="device-status" :class="getStatusClass(device.status)">
              <span class="status-dot"></span>
              <span class="status-text">{{ getStatusText(device.status) }}</span>
            </div>
          </div>

          <div class="device-details">
            <div class="drone-info" v-if="isHandheld">
              <div class="drone-item">
                <svg-icon name="drone" class="drone-icon" :size="14" />
                <span class="drone-name">{{ device.children ? (device.children.nickname || device.children.device_name) : "手持无人机" }}</span>
              </div>
            </div>
            <div class="drone-info" v-else-if="device.children">
              <div class="drone-item">
                <svg-icon name="drone" class="drone-icon" :size="14" />
                <span class="drone-name">{{ device.children?.nickname || "无无人机" }}</span>
              </div>
            </div>
          </div>

          <div class="device-meta">
            <span class="workspace-id" v-if="device.workspace_name">工作区: {{ device.workspace_name }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import { SearchOutlined } from "@ant-design/icons-vue";
import { getBindingDevices } from "@/api/manage";
import { ELocalStorageKey } from "@/types";
import { Device, DEVICE_NAME } from "@/types/device";
import { IPage } from "@/api/http/type";
import { useMyStore } from "@/store";

const emit = defineEmits<{
  "select-device": [device: Device];
}>();

const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId)!;

const loading = ref(false);
const searchText = ref("");
const selectedDeviceSn = ref<string>("");

const devicesData = reactive({
  data: [] as Device[],
  pagination: {
    page: 1,
    page_size: 50,
    total: 0,
  } as IPage,
});

// 过滤的设备数据
const filteredDevices = computed(() => {
  if (!searchText.value.trim()) {
    return devicesData.data;
  }

  return devicesData.data.filter((device) => device.nickname.toLowerCase().includes(searchText.value.toLowerCase()) || device.device_sn.toLowerCase().includes(searchText.value.toLowerCase()));
});

const store = useMyStore();

const isHandheld = computed(() => {
  const modelKey = store.state.waylineInfo?.drone_model_key;
  if (!modelKey) return false;
  const parts = modelKey.split("-");
  if (parts.length >= 2) {
    const droneType = parseInt(parts[1], 10);
    return droneType === 77 || droneType === 99;
  }
  return false;
});

// 获取设备数据
const getDevices = async () => {
  loading.value = true;
  try {
    const domain = isHandheld.value ? 2 : 3;
    const res = await getBindingDevices(workspaceId, devicesData.pagination, domain);
    if (res.code === 0) {
      devicesData.data = res.data.list;
      devicesData.pagination = res.data.pagination;
    } else {
      message.error("获取设备数据失败");
    }
  } catch (error) {
    console.error("获取设备数据失败:", error);
    message.error("获取设备数据失败");
  } finally {
    loading.value = false;
  }
};

// 选择设备
const selectDevice = (device: Device) => {
  selectedDeviceSn.value = device.device_sn;
  // 立即触发选择事件
  emit("select-device", device);
};

// 滚动加载更多
const onScroll = (event: Event) => {
  const target = event.target as HTMLElement;
  if (target.scrollTop + target.clientHeight >= target.scrollHeight - 10) {
    // 可以在这里实现加载更多逻辑
  }
};

// 获取状态文本
const getStatusText = (status: string) => {
  const statusMap: { [key: string]: string } = {
    false: "离线",
    true: "在线",
    2: "忙碌",
  };
  return statusMap[status] || "未知";
};

// 获取状态样式类
const getStatusClass = (status: string) => {
  const statusClassMap: { [key: string]: string } = {
    false: "offline",
    true: "online",
    2: "busy",
  };
  return statusClassMap[status] || "unknown";
};



onMounted(() => {
  getDevices();
});
</script>

<style scoped lang="scss">
.device-panel-container {
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

.device-list {
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

.device-item {
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

  .device-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    .device-name {
      flex: 1;

      .name-text {
        font-size: 16px;
        font-weight: 500;
        color: #fff;
        max-width: 180px;
        white-space: nowrap;
        text-overflow: ellipsis;
        overflow: hidden;
      }
    }

    .device-status {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;

      .status-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
      }

      &.online {
        color: #52c41a;
        .status-dot {
          background-color: #52c41a;
        }
      }

      &.offline {
        color: #ff4d4f;
        .status-dot {
          background-color: #ff4d4f;
        }
      }

      &.busy {
        color: #faad14;
        .status-dot {
          background-color: #faad14;
        }
      }

      &.unknown {
        color: #8fa4c7;
        .status-dot {
          background-color: #8fa4c7;
        }
      }
    }
  }

  .device-details {
    margin-bottom: 12px;

    .device-sn {
      display: flex;
      align-items: center;
      margin-bottom: 8px;
      font-size: 14px;

      .label {
        color: #8fa4c7;
        margin-right: 8px;
        min-width: 60px;
      }

      .value {
        color: #d6e5fb;
        font-family: monospace;
      }
    }

    .drone-info {
      .drone-item {
        display: flex;
        align-items: center;
        gap: 6px;
        margin-bottom: 4px;
        color: #d6e5fb;
        font-size: 14px;

        .drone-icon {
          font-size: 14px;
          color: #3a7af9;
        }
      }
    }
  }

  .device-meta {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: #8fa4c7;

    .device-type {
      font-weight: 500;
    }

    .workspace-id {
      max-width: 160px;
      white-space: nowrap;
      text-overflow: ellipsis;
      overflow: hidden;
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
