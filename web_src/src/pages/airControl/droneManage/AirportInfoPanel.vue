<template>
  <a-drawer title="机场详情信息" :visible="visible" placement="right" width="600" @close="handleClose">
    <div v-if="device" class="airport-info-panel">
      <!-- 基本信息 -->
      <div class="info-section">
        <h3>基本信息</h3>
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="设备名称">
            {{ device.nickname || device.device_name }}
          </a-descriptions-item>
          <a-descriptions-item label="设备 SN">
            {{ device.device_sn }}
          </a-descriptions-item>
          <a-descriptions-item label="设备型号">
            {{ device.device_name }}
          </a-descriptions-item>
          <a-descriptions-item label="固件版本">
            {{ device.firmware_version }}
          </a-descriptions-item>
          <a-descriptions-item label="在线状态">
            <a-badge :status="device.status ? 'success' : 'error'" :text="device.status ? '在线' : '离线'" />
          </a-descriptions-item>
          <a-descriptions-item label="加入时间">
            {{ device.bound_time }}
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <!-- 机场状态信息 -->
      <div class="info-section" v-if="dockInfo">
        <h3>机场状态</h3>
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="工作模式">
            <a-tag :color="getDockModeColor(dockInfo.basic_osd?.mode_code)">
              {{ (EDockModeCodeZh as any)[EDockModeCode[dockInfo.basic_osd?.mode_code]] || "未知" }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="运行时间">
            {{ getRunningTime(dockInfo.work_osd?.acc_time) }}
          </a-descriptions-item>
          <a-descriptions-item label="舱盖状态">
            <a-tag :color="getCoverStateColor(dockInfo.basic_osd?.cover_state)">
              {{ getCoverStateText(dockInfo.basic_osd?.cover_state) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="推杆状态">
            <a-tag :color="getPutterStateColor(dockInfo.basic_osd?.putter_state)">
              {{ getPutterStateText(dockInfo.basic_osd?.putter_state) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="补光灯状态">
            <a-tag :color="dockInfo.basic_osd?.supplement_light_state ? 'green' : 'red'">
              {{ dockInfo.basic_osd?.supplement_light_state ? "开启" : "关闭" }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="声光报警">
            <a-tag :color="dockInfo.basic_osd?.alarm_state ? 'orange' : 'green'">
              {{ dockInfo.basic_osd?.alarm_state ? "开启" : "关闭" }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="存储容量" :span="2">
            {{ getStorageInfo(dockInfo.basic_osd?.storage) }}
          </a-descriptions-item>
          <a-descriptions-item label="激活时间" :span="2">
            {{ getActivationTime(dockInfo.work_osd?.activation_time) }}
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <!-- 网络状态 -->
      <div class="info-section" v-if="dockInfo?.basic_osd?.network_state">
        <h3>网络状态</h3>
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="网络类型">
            {{ getNetworkTypeText(dockInfo.basic_osd.network_state.type) }}
          </a-descriptions-item>
          <a-descriptions-item label="网络速率"> {{ dockInfo.basic_osd.network_state.rate || 0 }} kb/s </a-descriptions-item>
          <template v-if="dockInfo.basic_osd.network_state.type !== 2">
            <a-descriptions-item label="信号质量" :span="2">
              <a-progress :percent="(dockInfo.basic_osd.network_state.quality || 0) * 20" size="small" :stroke-color="getSignalColor((dockInfo.basic_osd.network_state.quality || 0) * 20)" />
            </a-descriptions-item>
          </template>
        </a-descriptions>
      </div>

      <!-- 环境信息 -->
      <div class="info-section" v-if="dockInfo?.basic_osd">
        <h3>环境信息</h3>
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="降雨状态">
            <a-tag :color="dockInfo.basic_osd.rainfall ? 'blue' : 'green'">
              {{ getRainfallText(dockInfo.basic_osd.rainfall) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="风速"> {{ dockInfo.basic_osd.wind_speed || 0 }} m/s </a-descriptions-item>
          <a-descriptions-item label="环境温度"> {{ dockInfo.basic_osd.environment_temperature || 0 }}°C </a-descriptions-item>
          <a-descriptions-item label="设备温度"> {{ dockInfo.basic_osd.temperature || 0 }}°C </a-descriptions-item>
          <a-descriptions-item label="湿度"> {{ dockInfo.basic_osd.humidity || 0 }}% </a-descriptions-item>
          <a-descriptions-item label="位置信息">
            <div>
              <div>纬度: {{ dockInfo.basic_osd.latitude?.toFixed(6) || "--" }}</div>
              <div>经度: {{ dockInfo.basic_osd.longitude?.toFixed(6) || "--" }}</div>
              <div>高度: {{ dockInfo.basic_osd.height?.toFixed(1) || "--" }}m</div>
            </div>
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <!-- 电源状态 -->
      <div class="info-section" v-if="dockInfo?.work_osd">
        <h3>电源状态</h3>
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="供电电压"> {{ dockInfo.work_osd.electric_supply_voltage || 0 }} mV </a-descriptions-item>
          <a-descriptions-item label="工作电压"> {{ dockInfo.work_osd.working_voltage || 0 }} mV </a-descriptions-item>
          <a-descriptions-item label="工作电流"> {{ dockInfo.work_osd.working_current || 0 }} mA </a-descriptions-item>
          <a-descriptions-item label="备用电池" v-if="dockInfo.work_osd.backup_battery">
            {{ dockInfo.work_osd.backup_battery.voltage || 0 }} mV / {{ dockInfo.work_osd.backup_battery.temperature || 0 }}°C
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <!-- 定位状态 -->
      <div class="info-section" v-if="dockInfo?.basic_osd?.positionState">
        <h3>定位状态</h3>
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="GPS 状态">
            <a-tag :color="getFixedStateColor(dockInfo.basic_osd.positionState.is_fixed)">
              {{ getFixedStateText(dockInfo.basic_osd.positionState.is_fixed) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="GPS 卫星数"> {{ dockInfo.basic_osd.positionState.gps_number || 0 }} </a-descriptions-item>
          <a-descriptions-item label="RTK 卫星数"> {{ dockInfo.basic_osd.positionState.rtk_number || 0 }} </a-descriptions-item>
          <a-descriptions-item label="定位质量">
            {{ dockInfo.basic_osd.positionState.quality || 0 }}
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <!-- 飞行器信息 -->
      <div class="info-section" v-if="droneInfo && droneInfo.children && droneInfo.children.length > 0">
        <h3>飞行器信息</h3>
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="飞行器型号">
            {{ droneInfo.children[0]?.device_name || "无" }}
          </a-descriptions-item>
          <a-descriptions-item label="飞行器SN">
            {{ droneInfo.children[0]?.device_sn || "无" }}
          </a-descriptions-item>
          <a-descriptions-item label="工作模式" v-if="deviceOsdInfo">
            <a-tag :color="getDeviceModeColor(deviceOsdInfo.mode_code)">
              {{ EModeWord[EModeCode[deviceOsdInfo.mode_code] as keyof typeof EModeWord] || "断开连接" }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="定位状态" v-if="deviceOsdInfo?.position_state">
            <a-tag :color="getFixedStateColor(deviceOsdInfo.position_state.is_fixed)">
              {{ getFixedStateText(deviceOsdInfo.position_state.is_fixed) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="GPS 卫星数" v-if="deviceOsdInfo?.position_state"> {{ deviceOsdInfo.position_state.gps_number || "--" }} </a-descriptions-item>
          <a-descriptions-item label="RTK 卫星数" v-if="deviceOsdInfo?.position_state"> {{ deviceOsdInfo.position_state.rtk_number || "--" }} </a-descriptions-item>
          <a-descriptions-item label="海拔高度" v-if="deviceOsdInfo"> {{ deviceOsdInfo.height || "--" }} </a-descriptions-item>
          <a-descriptions-item label="相对高度" v-if="deviceOsdInfo"> {{ deviceOsdInfo.elevation || "--" }} </a-descriptions-item>
          <a-descriptions-item label="水平速度" v-if="deviceOsdInfo"> {{ deviceOsdInfo.horizontal_speed || "--" }} </a-descriptions-item>
          <a-descriptions-item label="垂直速度" v-if="deviceOsdInfo"> {{ deviceOsdInfo.vertical_speed || "--" }} </a-descriptions-item>
          <a-descriptions-item label="风速" v-if="deviceOsdInfo"> {{ deviceOsdInfo.wind_speed || "--" }} </a-descriptions-item>
          <a-descriptions-item label="风向" v-if="deviceOsdInfo">
            {{ deviceOsdInfo.wind_direction || "--" }}
          </a-descriptions-item>
          <a-descriptions-item label="电池电量" v-if="deviceOsdInfo?.battery">
            <div class="battery-info">
              <a-progress :percent="parseInt(deviceOsdInfo.battery.capacity_percent) || 0" size="small" :stroke-color="getBatteryColor(parseInt(deviceOsdInfo.battery.capacity_percent))" />
              <div class="battery-details">
                <span>剩余飞行时间: {{ deviceOsdInfo.battery.remain_flight_time || 0 }}分钟</span>
                <br />
                <span>返航电量: {{ deviceOsdInfo.battery.return_home_power || "--" }}</span>
                <br />
                <span>降落电量: {{ deviceOsdInfo.battery.landing_power || "--" }}</span>
              </div>
            </div>
          </a-descriptions-item>
          <a-descriptions-item label="返航预留电量" v-if="deviceOsdInfo?.remaining_power_for_return_home !== undefined">
            <a-progress :percent="deviceOsdInfo.remaining_power_for_return_home || 0" size="small" :stroke-color="getBatteryColor(deviceOsdInfo.remaining_power_for_return_home)" />
          </a-descriptions-item>
          <a-descriptions-item label="位置信息" v-if="deviceOsdInfo">
            <div>
              <div>纬度: {{ deviceOsdInfo.latitude?.toFixed(6) || "--" }}</div>
              <div>经度: {{ deviceOsdInfo.longitude?.toFixed(6) || "--" }}</div>
              <div>机场距离: {{ deviceOsdInfo.home_distance || "--" }}</div>
            </div>
          </a-descriptions-item>
        </a-descriptions>
      </div>

    </div>

    <template #footer>
      <a-space>
        <a-button @click="handleClose">关闭</a-button>
        <a-button type="primary" @click="refreshData">刷新数据</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script lang="ts" setup>
import { ref, computed, watch, onMounted, onUnmounted } from "vue";
import { Device } from "@/types/device";
import { EDockModeCode, EDockModeCodeZh, EModeCode, EModeWord } from "@/types/device";
import { CoverStateEnum, PutterStateEnum, AirportStorage } from "@/types/airport-tsa";
import { EHmsLevel } from "@/types/enums";
import { getAirportStorage } from "@/utils/device-cmd";
import { useMyStore } from "@/store";
import { getDeviceState } from "@/api/device-setting";

interface Props {
  visible: boolean;
  device?: Device;
}

interface Emits {
  (e: "update:visible", value: boolean): void;
  (e: "refresh"): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();
const store = useMyStore();

// 定时器用于实时更新运行时间
const updateTimer = ref<number>();
const forceUpdate = ref(0);

// 开始定时更新
const startRealTimeUpdate = () => {
  if (updateTimer.value) {
    clearInterval(updateTimer.value);
  }
  updateTimer.value = window.setInterval(() => {
    forceUpdate.value++;
  }, 1000); // 每秒更新一次
};

// 停止定时更新
const stopRealTimeUpdate = () => {
  if (updateTimer.value) {
    clearInterval(updateTimer.value);
    updateTimer.value = undefined;
  }
};

// 组件挂载时开始定时更新
onMounted(() => {
  if (props.visible) {
    startRealTimeUpdate();
  }
});

// 组件卸载时清理定时器
onUnmounted(() => {
  stopRealTimeUpdate();
});

// 获取运行时间 - 按 m/d/h/min/s 分段显示
const getRunningTime = (accTime?: number): string => {
  forceUpdate.value; // 依赖这个值来触发重新计算
  if (!accTime || accTime === 0) return "--";
  // accTime 单位为秒
  const month = Math.floor(accTime / 2592000); // 30天
  const day = Math.floor((accTime % 2592000) / 86400);
  const hour = Math.floor(((accTime % 2592000) % 86400) / 3600);
  const min = Math.floor((((accTime % 2592000) % 86400) % 3600) / 60);
  const sec = Math.floor((((accTime % 2592000) % 86400) % 3600) % 60);
  let result = "";
  if (month > 0) result += `${month}m `;
  if (day > 0) result += `${day}d `;
  if (hour > 0) result += `${hour}h `;
  if (min > 0) result += `${min}min `;
  result += `${sec}s`;
  return result.trim();
};

// 获取激活时间
const getActivationTime = (timestamp?: number): string => {
  if (!timestamp || timestamp === 0) return "--";

  const date = new Date(timestamp * 1000);
  return date.toLocaleString("zh-CN");
};

// 获取降雨状态文本
const getRainfallText = (rainfallCode?: number): string => {
  const rainfallTexts: Record<number, string> = {
    0: "无雨",
    1: "小雨",
    2: "中雨",
    3: "大雨",
  };
  return rainfallTexts[rainfallCode || 0] || "无雨";
};

// 计算属性获取设备信息
const dockInfo = computed(() => {
  if (!props.device?.device_sn) return null;
  const info = store.state.deviceState.dockInfo[props.device.device_sn];
  return info;
});

const deviceOsdInfo = computed(() => {
  if (!props.device?.children || props.device.children.length === 0) return null;
  const droneSn = props.device.children[0]?.device_sn;
  if (!droneSn) return null;
  const info = store.state.deviceState.deviceInfo[droneSn];
  return info;
});

const droneInfo = computed(() => props.device);


const hmsInfo = computed(() => {
  if (!props.device?.device_sn) return [];
  const gatewayHms = store.state.hmsInfo[props.device.device_sn] || [];
  const droneHms = props.device.children && props.device.children.length > 0 && props.device.children[0]?.device_sn ? store.state.hmsInfo[props.device.children[0].device_sn] || [] : [];
  return [...gatewayHms, ...droneHms];
});

// 主动从 Redis 后端拉取最新设备状态并写入 Store
const fetchAirportState = async () => {
  const sn = props.device?.device_sn;
  if (!sn) return;
  try {
    const res = await getDeviceState(sn);
    if (res && res.code === 0 && res.data) {
      if (res.data.basic_osd || res.data.mode_code !== undefined) {
        const hostData = res.data.basic_osd || res.data;
        store.commit("SET_DOCK_INFO", { sn, host: hostData });
      }
      if (res.data.work_osd) {
        store.commit("SET_DOCK_INFO", { sn, host: res.data.work_osd });
      }
      if (res.data.link_osd) {
        store.commit("SET_DOCK_INFO", { sn, host: res.data.link_osd });
      }
    }
  } catch (err) {
    console.error("获取机场 Redis 状态失败:", err);
  }

  if (props.device?.children && props.device.children.length > 0) {
    const droneSn = props.device.children[0]?.device_sn;
    if (droneSn) {
      try {
        const droneRes = await getDeviceState(droneSn);
        if (droneRes && droneRes.code === 0 && droneRes.data) {
          store.commit("SET_DEVICE_INFO", { sn: droneSn, host: droneRes.data });
        }
      } catch (err) {
        console.error("获取无人机 Redis 状态失败:", err);
      }
    }
  }
};

// 监听面板显示状态，当显示时刷新数据
watch(
  () => props.visible,
  (newVisible) => {
    if (newVisible && props.device?.device_sn) {
      fetchAirportState();
      emit("refresh");
      startRealTimeUpdate();
    } else {
      stopRealTimeUpdate();
    }
  },
  { immediate: true }
);

// 关闭抽屉
const handleClose = () => {
  emit("update:visible", false);
};

// 刷新数据
const refreshData = () => {
  fetchAirportState();
  emit("refresh");
};

// 获取机场模式颜色
const getDockModeColor = (mode?: number) => {
  if (!mode) return "red";
  switch (mode) {
    case EDockModeCode.Idle:
      return "blue";
    case EDockModeCode.Working:
      return "green";
    case EDockModeCode.Debugging:
      return "orange";
    case EDockModeCode.Remote_Debugging:
      return "purple";
    case EDockModeCode.Upgrading:
      return "cyan";
    default:
      return "red";
  }
};

// 获取设备模式颜色
const getDeviceModeColor = (mode?: number) => {
  if (!mode) return "red";
  switch (mode) {
    case EModeCode.Standby:
      return "blue";
    case EModeCode.Ready:
      return "green";
    case EModeCode.Manual:
      return "green";
    case EModeCode.Automatic:
      return "green";
    case EModeCode.Waypoint:
      return "green";
    case EModeCode.Return_To_Home:
      return "orange";
    case EModeCode.Landing:
      return "orange";
    default:
      return "red";
  }
};

// 获取舱盖状态文本
const getCoverStateText = (state?: number) => {
  switch (state) {
    case CoverStateEnum.Close:
      return "关闭";
    case CoverStateEnum.Open:
      return "打开";
    case CoverStateEnum.HalfOpen:
      return "半打开";
    case CoverStateEnum.Failed:
      return "失败";
    default:
      return "未知";
  }
};

// 获取舱盖状态颜色
const getCoverStateColor = (state?: number) => {
  switch (state) {
    case CoverStateEnum.Close:
      return "green";
    case CoverStateEnum.Open:
      return "blue";
    case CoverStateEnum.HalfOpen:
      return "orange";
    case CoverStateEnum.Failed:
      return "red";
    default:
      return "default";
  }
};

// 获取推杆状态文本
const getPutterStateText = (state?: number) => {
  switch (state) {
    case PutterStateEnum.Close:
      return "关闭";
    case PutterStateEnum.Open:
      return "打开";
    case PutterStateEnum.HalfOpen:
      return "半打开";
    case PutterStateEnum.Failed:
      return "失败";
    default:
      return "未知";
  }
};

// 获取推杆状态颜色
const getPutterStateColor = (state?: number) => {
  switch (state) {
    case PutterStateEnum.Close:
      return "green";
    case PutterStateEnum.Open:
      return "blue";
    case PutterStateEnum.HalfOpen:
      return "orange";
    case PutterStateEnum.Failed:
      return "red";
    default:
      return "default";
  }
};

// 获取网络类型文本
const getNetworkTypeText = (type?: number) => {
  switch (type) {
    case 1:
      return "4G";
    case 2:
      return "以太网";
    default:
      return "未知";
  }
};

// 获取存储信息
const getStorageInfo = (storage?: AirportStorage) => {
  if (!storage) return "无数据";
  return getAirportStorage(storage);
};

// 获取信号强度颜色
const getSignalColor = (quality?: number) => {
  if (!quality) return "#ff4d4f";
  if (quality >= 80) return "#52c41a";
  if (quality >= 60) return "#faad14";
  if (quality >= 40) return "#fa8c16";
  return "#ff4d4f";
};

// 获取电池颜色
const getBatteryColor = (percent?: number) => {
  if (!percent) return "#ff4d4f";
  if (percent >= 50) return "#52c41a";
  if (percent >= 30) return "#faad14";
  if (percent >= 20) return "#fa8c16";
  return "#ff4d4f";
};

// 获取HMS级别颜色
const getHmsLevelColor = (level?: number) => {
  switch (level) {
    case EHmsLevel.NOTICE:
      return "green";
    case EHmsLevel.CAUTION:
      return "orange";
    case EHmsLevel.WARN:
      return "red";
    default:
      return "default";
  }
};

// 获取HMS级别文本
const getHmsLevelText = (level?: number) => {
  switch (level) {
    case EHmsLevel.NOTICE:
      return "提示";
    case EHmsLevel.CAUTION:
      return "注意";
    case EHmsLevel.WARN:
      return "警告";
    default:
      return "未知";
  }
};

// 获取收敛状态文本
const getFixedStateText = (state?: number) => {
  switch (state) {
    case 0:
      return "未开始";
    case 1:
      return "收敛中";
    case 2:
      return "收敛成功";
    case 3:
      return "收敛失败";
    default:
      return "未知";
  }
};

// 获取收敛状态颜色
const getFixedStateColor = (state?: number) => {
  switch (state) {
    case 0:
      return "default";
    case 1:
      return "processing";
    case 2:
      return "success";
    case 3:
      return "error";
    default:
      return "default";
  }
};
</script>

<style lang="scss" scoped>
.airport-info-panel {
  .info-section {
    margin-bottom: 24px;

    h3 {
      margin-bottom: 16px;
      color: #262626;
      font-weight: 600;
      border-bottom: 1px solid #f0f0f0;
      padding-bottom: 8px;
    }
  }

  .status-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 16px;
    margin-top: 16px;
  }

  .status-card {
    border: 1px solid #f0f0f0;
    border-radius: 8px;
    padding: 16px;
    background: #fafafa;
    transition: all 0.3s ease;

    &:hover {
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      border-color: #1890ff;
    }
  }

  .status-title {
    font-weight: 600;
    color: #262626;
    margin-bottom: 12px;
    font-size: 14px;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .status-content {
    .status-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;

      &:last-child {
        margin-bottom: 0;
      }

      .label {
        color: #666;
        font-size: 12px;
        min-width: 80px;
      }

      .value {
        color: #262626;
        font-size: 12px;
        font-weight: 500;
        text-align: right;

        &.highlight {
          color: #1890ff;
          font-weight: 600;
        }

        &.small {
          font-size: 11px;
        }
      }
    }
  }

  .text-gray {
    color: #999 !important;
    font-size: 11px;
  }

  .text-gray {
    color: #999;
    font-style: italic;
  }

  .battery-info {
    .battery-details {
      margin-top: 8px;
      font-size: 11px;
      color: #D6E5FB;
      line-height: 1.4;
    }
  }

  .hms-list {
    overflow-y: hidden;
  }
}

:deep(.ant-descriptions-item-label) {
  background-color: #fafafa;
  font-weight: 500;
}

:deep(.ant-progress-text) {
  font-size: 12px;
}
</style>
