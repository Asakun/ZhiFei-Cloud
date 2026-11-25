<template>
  <a-layout class="layout-cent">
    <div class="table-box">
      <!-- 顶部筛选区域 -->
      <div class="searchBox" style="padding: 15px 20px 0 20px; display: flex; justify-content: space-between; align-items: center;">
        <a-tabs v-model:activeKey="selectedType" :animated="false" style="margin-bottom: 0;">
          <a-tab-pane :key="EDeviceTypeName.Dock" tab="机场" />
          <a-tab-pane :key="EDeviceTypeName.Aircraft" tab="飞行器" />
          <a-tab-pane :key="EDeviceTypeName.Gateway" tab="遥控器" />
        </a-tabs>
        <a-form layout="inline" style="display: flex; align-items: center;">
          <a-form-item label="按名称/SN筛选" style="margin-right: 0;">
            <a-input v-model:value="searchName" placeholder="输入名称、型号或SN" allow-clear style="width: 240px">
              <template #prefix>
                <SearchOutlined style="color: rgba(214, 229, 251, 0.6)" />
              </template>
            </a-input>
          </a-form-item>
        </a-form>
      </div>
      <div class="device-table-wrap table flex-display flex-column" style="margin: 10px 20px 0 20px; border-radius: 8px; overflow: hidden;">
        <a-table :columns="columns" :data-source="filteredDevices" :pagination="false" @change="refreshData"
          row-key="device_sn" :expandedRowKeys="expandRows" :row-selection="rowSelection" :rowClassName="rowClassName"
          :scroll="{ y: 'calc(100vh - 340px)' }" :expandIcon="expandIcon" :loading="loading">
            <template v-for="col in ['nickname']" #[col]="{ text, record }" :key="col">
              <div>
                <a-input v-if="editableData[record.device_sn]"
                  v-model:value="editableData[record.device_sn][col as keyof Device]" style="margin: -5px 0" />
                <template v-else>
                  <a-tooltip :title="text">
                    {{ text }}
                  </a-tooltip>
                </template>
              </div>
            </template>
            <template v-for="col in ['sn', 'workspace']" #[col]="{ text }" :key="col">
              <a-tooltip :title="text">
                <span>{{ text }}</span>
              </a-tooltip>
            </template>
            <!-- 固件版本 -->
            <template #firmware_version="{ record }">
              <span v-if="judgeCurrentType(EDeviceTypeName.Dock) && isAdmin">
                <DeviceFirmwareUpgrade :device="record" class="table-flex-col" @device-upgrade="handleDeviceUpgrade" />
              </span>
              <span v-else>
                {{ record.firmware_version }}
              </span>
            </template>
            <!-- 设备负载 -->
            <template #payloads="{ record }">
              <span v-if="getRecordPayloads(record).length > 0">
                <a-tooltip>
                  <template #title>
                    <div class="text-[11px] p-1">
                      <div class="font-bold border-b border-gray-700/80 pb-1 mb-1">负载信息</div>
                      <div v-for="p in getRecordPayloads(record)" :key="p.payload_sn">
                        <div>名称: {{ p.psdk_name }}</div>
                        <div>SN: {{ p.payload_sn }}</div>
                        <div>版本: {{ p.psdk_version }}</div>
                      </div>
                    </div>
                  </template>
                  <span class="payload-table-badge">
                    <span class="payload-badge-dot"></span>
                    <span class="payload-badge-text">
                      {{ getRecordPayloads(record)[0].psdk_name }}{{ getRecordPayloads(record).length > 1 ? ' +' + (getRecordPayloads(record).length - 1) : '' }}
                    </span>
                  </span>
                </a-tooltip>
              </span>
              <span v-else class="text-gray-500">-</span>
            </template>
            <!-- 第三方责任险到期日期 -->
            <template #insurance_expired="{ record }">
              <div v-if="record.domain === EDeviceTypeName.Aircraft">
                <a-date-picker v-if="editableData[record.device_sn]"
                  v-model:value="editableData[record.device_sn].insurance_expired"
                  show-time
                  value-format="YYYY-MM-DD HH:mm:ss"
                  placeholder="选择时间"
                  size="small"
                  style="width: 100%"
                  @click.stop />
                <span v-else>{{ record.insurance_expired || '无' }}</span>
              </div>
              <span v-else>-</span>
            </template>
            <!-- 保险到期提醒 -->
            <template #insurance_reminder="{ record }">
              <div v-if="record.domain === EDeviceTypeName.Aircraft">
                <a-tag :style="getInsuranceStatus(record.insurance_expired).style">
                  {{ getInsuranceStatus(record.insurance_expired).text }}
                </a-tag>
              </div>
              <span v-else>-</span>
            </template>
            <!-- 状态 -->
            <template #status="{ text }">
              <span v-if="text" class="flex-row flex-align-center">
                <span class="mr5" style="width: 12px; height: 12px; border-radius: 50%; background-color: green" />
                <span>在线</span>
              </span>
              <span class="flex-row flex-align-center" v-else>
                <span class="mr5" style="width: 12px; height: 12px; border-radius: 50%; background-color: red" />
                <span>离线</span>
              </span>
            </template>
            <!-- 操作 -->
            <template #action="{ record }">
              <div class="editable-row-operations">
                <!-- 编辑态操作 -->
                <div v-if="editableData[record.device_sn]">
                  <a-tooltip title="确认修改">
                    <span @click="save(record)" style="color: #28d445">
                      <CheckOutlined />
                    </span>
                  </a-tooltip>
                  <a-tooltip title="取消修改">
                    <span @click="() => delete editableData[record.device_sn]" style="color: #e70102">
                      <CloseOutlined />
                    </span>
                  </a-tooltip>
                </div>
                <!-- 非编辑态操作 -->
                <div v-else class="flex-align-center flex-row" style="color: #2d8cf0">
                  <a-tooltip v-if="current.indexOf(EDeviceTypeName.Dock) !== -1" title="机场详情">
                    <InfoCircleOutlined @click="showAirportDetail(record)" />
                  </a-tooltip>
                  <a-tooltip v-if="current.indexOf(EDeviceTypeName.Dock) !== -1" title="设备日志">
                    <CloudServerOutlined @click="showDeviceLogUploadRecord(record)" />
                  </a-tooltip>
                  <a-tooltip v-if="current.indexOf(EDeviceTypeName.Dock) !== -1" title="Hms 信息">
                    <FileSearchOutlined @click="showHms(record)" />
                  </a-tooltip>
                  <a-tooltip title="编辑">
                    <EditOutlined @click="edit(record)" />
                  </a-tooltip>
                  <a-tooltip title="删除">
                    <DeleteOutlined @click="
                      () => {
                        (deleteTip = true), (deleteSn = record.device_sn);
                      }
                    " />
                  </a-tooltip>
                  <a-tooltip title="mqtt重置" v-if="record.domain === EDeviceTypeName.Dock || record.domain === EDeviceTypeName.Gateway">
                    <!-- v-if="record.sub_type === 0 && !record.status && mqttClientExists[record.device_sn]"> -->
                    <ReloadOutlined @click="resetMqtt(record)" />
                  </a-tooltip>
                </div>
              </div>
            </template>
          </a-table>
          <div class="pagination-container">
            <a-pagination v-model:current="paginationProp.current" v-model:page-size="paginationProp.pageSize"
              :total="paginationProp.total" :show-total="(total: number) => `共 ${total} 条记录`" show-quick-jumper
              @change="handlePageChange" @showSizeChange="handlePageChange" />
          </div>
        </div>
      </div>
    </a-layout>
  <a-modal v-model:visible="deleteTip" width="450px" :closable="false" centered :okButtonProps="{ danger: true }"
    @ok="unbind">
    <p class="pt10 pl20" style="height: 50px">Delete device from workspace?</p>
    <template #title>
      <div class="flex-row flex-justify-center">
        <span>删除设备</span>
      </div>
    </template>
  </a-modal>

  <!-- 设备升级 -->
  <DeviceFirmwareUpgradeModal title="设备升级" v-model:visible="deviceFirmwareUpgradeModalVisible" :device="selectedDevice"
    @ok="onUpgradeDeviceOk"></DeviceFirmwareUpgradeModal>

  <!-- 设备日志上传记录 -->
  <DeviceLogUploadRecordDrawer v-model:visible="deviceLogUploadRecordVisible" :device="currentDevice">
  </DeviceLogUploadRecordDrawer>

  <!-- hms 信息 -->
  <DeviceHmsDrawer v-model:visible="hmsVisible" :device="currentDevice"> </DeviceHmsDrawer>

  <!-- 机场详情面板 -->
  <AirportInfoPanel v-model:visible="airportDetailVisible" :device="selectedAirportDevice"
    @refresh="refreshAirportData">
  </AirportInfoPanel>

</template>
<script lang="ts" setup>
import { ColumnProps, TableState } from "ant-design-vue/lib/table/interface";
import { h, onMounted, reactive, ref, UnwrapRef, computed } from "vue";
import { IPage } from "@/api/http/type";
import { getBindingDevices, unbindDevice, updateDevice, getPayloadList, getUserInfo, checkClientIdExists, kickClientId } from "@/api/manage";
import { EDeviceTypeName, ELocalStorageKey } from "@/types";
import { EditOutlined, CheckOutlined, CloseOutlined, DeleteOutlined, FileSearchOutlined, CloudServerOutlined, InfoCircleOutlined, ReloadOutlined, SearchOutlined } from "@ant-design/icons-vue";
import { Device, DeviceFirmwareStatusEnum } from "@/types/device";
import DeviceFirmwareUpgrade from "@/components/devices/device-upgrade/DeviceFirmwareUpgrade.vue";
import DeviceFirmwareUpgradeModal from "@/components/devices/device-upgrade/DeviceFirmwareUpgradeModal.vue";
import { useDeviceFirmwareUpgrade } from "@/components/devices/device-upgrade/use-device-upgrade";
import { useDeviceUpgradeEvent } from "@/components/devices/device-upgrade/use-device-upgrade-event";
import { DeviceCmdExecuteInfo, DeviceCmdExecuteStatus } from "@/types/device-cmd";
import DeviceLogUploadRecordDrawer from "@/components/devices/device-log/DeviceLogUploadRecordDrawer.vue";
import DeviceHmsDrawer from "@/components/devices/device-hms/DeviceHmsDrawer.vue";
import AirportInfoPanel from "./AirportInfoPanel.vue";
import { notification, Modal } from "ant-design-vue";
import { onBeforeUnmount } from "vue";
import EventBus from "@/event-bus";

import { CURRENT_CONFIG } from "@/api/http/config";

interface DeviceData {
  device: Device[];
}

const loading = ref(true);
const deleteTip = ref<boolean>(false);
const deleteSn = ref<string>();
const columns: ColumnProps[] = [
  { title: "设备型号", dataIndex: "device_name", width: 90, className: "titleStyle" },
  { title: "设备SN", dataIndex: "device_sn", width: 240, className: "titleStyle", ellipsis: true, slots: { customRender: "sn" } },
  {
    title: "设备名称",
    dataIndex: "nickname",
    width: 200,
    sorter: (a: Device, b: Device) => a.nickname.localeCompare(b.nickname),
    className: "titleStyle",
    ellipsis: true,
    slots: { customRender: "nickname" },
  },
  { title: "固件版本", dataIndex: "firmware_version", width: 110, className: "titleStyle", slots: { customRender: "firmware_version" } },
  { title: "设备负载", dataIndex: "payloads", width: 150, className: "titleStyle", slots: { customRender: "payloads" } },
  { title: "第三方责任险到期日期", dataIndex: "insurance_expired", width: 180, className: "titleStyle", slots: { customRender: "insurance_expired" } },
  { title: "保险到期提醒", dataIndex: "insurance_reminder", width: 110, className: "titleStyle", slots: { customRender: "insurance_reminder" } },
  { title: "状态", dataIndex: "status", width: 80, className: "titleStyle", slots: { customRender: "status" } },
  {
    title: "所属项目",
    dataIndex: "workspace_name",
    width: 140,
    className: "titleStyle",
    ellipsis: true,
    slots: { customRender: "workspace" },
    customRender: ({ text, record }) => {
      if (current.value.indexOf(EDeviceTypeName.Dock) !== -1 && record.domain === EDeviceTypeName.Aircraft) {
        return "";
      }
      return text;
    },
  },
  { title: "加入时间", dataIndex: "bound_time", width: 140, sorter: (a: Device, b: Device) => a.bound_time.localeCompare(b.bound_time), className: "titleStyle" },
  { title: "最后在线时间", dataIndex: "login_time", width: 140, sorter: (a: Device, b: Device) => a.login_time.localeCompare(b.login_time), className: "titleStyle" },
  {
    title: "操作",
    dataIndex: "actions",
    width: 220,
    className: "titleStyle",
    slots: { customRender: "action" },
  },
];

const expandIcon = (props: any) => {
  if ((judgeCurrentType(EDeviceTypeName.Dock) || judgeCurrentType(EDeviceTypeName.Gateway)) && !props.expanded) {
    return h("div", {
      style: "border-left: 2px solid rgb(200,200,200); border-bottom: 2px solid rgb(200,200,200); height: 16px; width: 16px; float: left;",
      class: "mt-5 ml0",
    });
  }
};

const rowClassName = (record: any, index: number) => {
  const className = [];
  if ((index & 1) === 0) {
    className.push("table-striped");
  }
  if (record.domain !== current.value[0]) {
    className.push("child-row");
  }
  return className.toString().replaceAll(",", " ");
};

const expandRows = ref<string[]>([]);
const data = reactive<DeviceData>({
  device: [],
});

// 分页改变
const handlePageChange = (page: number, pageSize: number) => {
  paginationProp.current = page
  paginationProp.pageSize = pageSize
}


const paginationProp = reactive({
  pageSizeOptions: ["20", "50", "100"],
  showQuickJumper: true,
  showSizeChanger: true,
  buildOptionText: ({ value }) => `${value} 条/页`,
  pageSize: 50,
  current: 1,
  total: 0,
});

// 获取分页信息
function getPaginationBody() {
  return {
    page: paginationProp.current,
    page_size: paginationProp.pageSize,
  } as IPage;
}

const rowSelection = {
  onChange: (selectedRowKeys: (string | number)[], selectedRows: []) => {
    console.log(`selectedRowKeys: ${selectedRowKeys}`, "selectedRows: ", selectedRows);
  },
  onSelect: (record: any, selected: boolean, selectedRows: []) => {
    console.log(record, selected, selectedRows);
  },
  onSelectAll: (selected: boolean, selectedRows: [], changeRows: []) => {
    console.log(selected, selectedRows, changeRows);
  },
  getCheckboxProps: (record: any) => ({
    disabled: (judgeCurrentType(EDeviceTypeName.Dock) && record.domain !== EDeviceTypeName.Dock) ||
              (judgeCurrentType(EDeviceTypeName.Gateway) && record.domain !== EDeviceTypeName.Gateway),
    style: ((judgeCurrentType(EDeviceTypeName.Dock) && record.domain !== EDeviceTypeName.Dock) ||
            (judgeCurrentType(EDeviceTypeName.Gateway) && record.domain !== EDeviceTypeName.Gateway)) ? "display: none" : "",
  }),
};
type Pagination = TableState["pagination"];

const workspaceId: string = localStorage.getItem(ELocalStorageKey.WorkspaceId) || "";
const editableData: UnwrapRef<Record<string, Device>> = reactive({});
const current = ref([EDeviceTypeName.Dock]);

const psdkWidgets = ref<Record<string, any[]>>({});
const fetchPsdkWidgets = () => {
  if (!workspaceId) return;
  getPayloadList(workspaceId).then(res => {
    if (res) {
      psdkWidgets.value = res;
    }
  }).catch(err => {
    console.error('获取无人机负载失败:', err);
  });
};

const getRecordPayloads = (record: any) => {
  if (record.domain === EDeviceTypeName.Aircraft) {
    return psdkWidgets.value[record.device_sn] || [];
  }
  return [];
};

const getInsuranceStatus = (expireDateStr: string | null) => {
  if (!expireDateStr) return { text: "未设置", style: "color: rgba(255, 255, 255, 0.65); background: rgba(255, 255, 255, 0.08); border-color: rgba(255, 255, 255, 0.15);" };
  const expireDate = new Date(expireDateStr);
  const now = new Date();
  if (isNaN(expireDate.getTime())) return { text: "格式错误", style: "color: #ff4d4f; background: rgba(255, 77, 79, 0.1); border-color: rgba(255, 77, 79, 0.2);" };
  
  const diffTime = expireDate.getTime() - now.getTime();
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  
  if (diffDays < 0) {
    return { text: "已到期", style: "color: #ff4d4f; background: rgba(255, 77, 79, 0.1); border-color: rgba(255, 77, 79, 0.2);" };
  } else if (diffDays <= 30) {
    return { text: `即将到期(${diffDays}天)`, style: "color: #faad14; background: rgba(250, 173, 20, 0.1); border-color: rgba(250, 173, 20, 0.2);" };
  } else {
    return { text: "正常", style: "color: #52c41a; background: rgba(82, 196, 26, 0.1); border-color: rgba(82, 196, 26, 0.2);" };
  }
};

const selectedType = computed({
  get() {
    return current.value[0];
  },
  set(val: EDeviceTypeName) {
    current.value = [val];
    paginationProp.current = 1;
    getDevices(val);
  }
});

const searchName = ref("");

const filteredDevices = computed(() => {
  const query = searchName.value.trim().toLowerCase();
  if (!query) {
    return data.device;
  }
  return data.device.filter((item: Device) => {
    const parentMatch =
      (item.nickname && item.nickname.toLowerCase().includes(query)) ||
      (item.device_name && item.device_name.toLowerCase().includes(query)) ||
      (item.device_sn && item.device_sn.toLowerCase().includes(query));

    let childMatch = false;
    if (item.children) {
      const childrenArr = Array.isArray(item.children) ? item.children : [item.children];
      childMatch = childrenArr.some((child: Device) =>
        (child.nickname && child.nickname.toLowerCase().includes(query)) ||
        (child.device_name && child.device_name.toLowerCase().includes(query)) ||
        (child.device_sn && child.device_sn.toLowerCase().includes(query))
      );
    }
    return parentMatch || childMatch;
  });
});

const mqttClientExists = reactive<Record<string, boolean>>({});

const checkMqttClients = async (devices: Device[]) => {
  for (const device of devices) {
    if (device.domain === EDeviceTypeName.Dock && device.sub_type === 0 && !device.status) {
      try {
        checkClientIdExists(device.device_sn).then(res => {
          const clients = Array.isArray(res) ? res : (res?.data || []);
          mqttClientExists[device.device_sn] = clients && clients.length > 0;
        }).catch(() => {
          mqttClientExists[device.device_sn] = false;
        });
      } catch (e) {
        mqttClientExists[device.device_sn] = false;
      }
    }
    if (device.children) {
      checkMqttClients(device.children);
    }
  }
}

// 监听父级组件广播的设备上线/下线拓扑变化事件
const handleDeviceTopologyChanged = () => {
  getDevices(current.value[0], true);
  fetchPsdkWidgets();
};

EventBus.on('deviceTopologyChanged', handleDeviceTopologyChanged);

onBeforeUnmount(() => {
  EventBus.off('deviceTopologyChanged', handleDeviceTopologyChanged);
});

function judgeCurrentType(type: EDeviceTypeName): boolean {
  return current.value.indexOf(type) !== -1;
}

const isAdmin = ref(false);

// 设备升级
const { deviceFirmwareUpgradeModalVisible, selectedDevice, onDeviceUpgrade, onUpgradeDeviceOk } = useDeviceFirmwareUpgrade(workspaceId);

function handleDeviceUpgrade(record: Device) {
  if (!isAdmin.value) {
    notification.warning({ message: "仅管理员可以使用固件升级功能" });
    return;
  }
  onDeviceUpgrade(record);
}

function onDeviceUpgradeWs(payload: DeviceCmdExecuteInfo) {
  updateDevicesByWs(data.device, payload);
}

function updateDevicesByWs(devices: Device[], payload: DeviceCmdExecuteInfo) {
  if (!devices || devices.length <= 0) {
    return;
  }
  for (let i = 0; i < devices.length; i++) {
    if (devices[i].device_sn === payload.sn) {
      if (!payload.output) return;
      const { status, progress, ext } = payload.output;
      if (status === DeviceCmdExecuteStatus.Sent || status === DeviceCmdExecuteStatus.InProgress) {
        // 升级中
        const rate = ext?.rate ? (ext.rate / 1024).toFixed(2) + "kb/s" : "";
        devices[i].firmware_status = DeviceFirmwareStatusEnum.DuringUpgrade;
        devices[i].firmware_progress = (progress?.percent || 0) + "% " + rate;
      } else {
        // 终态：成功，失败，超时
        if (status === DeviceCmdExecuteStatus.Failed || status === DeviceCmdExecuteStatus.Timeout) {
          notification.error({
            message: `(${payload.sn}) Upgrade failed`,
            description: `Error Code: ${payload.result}`,
            duration: null,
          });
        }
        // 拉取列表
        getDevices(current.value[0], true);
      }
      return;
    }
    if (devices[i].children) {
      updateDevicesByWs(devices[i].children || [], payload);
    }
  }
}

useDeviceUpgradeEvent(onDeviceUpgradeWs);

// 获取设备列表信息
function getDevices(domain: number, closeLoading?: boolean) {
  if (!closeLoading) {
    loading.value = true;
  }
  getBindingDevices(workspaceId, getPaginationBody(), domain).then((res) => {
    if (res.code !== 0) {
      return;
    }
    const resData: Device[] = res.data.list;
    expandRows.value = [];
    resData.forEach((val: any) => {
      if (val.domain === EDeviceTypeName.Aircraft) {
        val.insurance_expired = val.insurance_expired || null;
      }
      if (val.children) {
        val.children = [val.children];
        val.children.forEach((child: any) => {
          if (child.domain === EDeviceTypeName.Aircraft) {
            child.insurance_expired = child.insurance_expired || null;
          }
        });
      }
      if (judgeCurrentType(EDeviceTypeName.Dock) || judgeCurrentType(EDeviceTypeName.Gateway)) {
        expandRows.value.push(val.device_sn);
      }
    });
    data.device = resData;
    paginationProp.total = res.data.pagination.total;
    paginationProp.current = res.data.pagination.page;
    paginationProp.pageSize = res.data.pagination.page_size;
    loading.value = false;
    checkMqttClients(data.device);
  });
}

function refreshData(page: Pagination) {
  paginationProp.current = page?.current!;
  paginationProp.pageSize = page?.pageSize!;
  getDevices(current.value[0]);
}

// 编辑
function edit(record: Device) {
  editableData[record.device_sn] = record;
}

// 保存
function save(record: Device) {
  delete editableData[record.device_sn];
  const params: any = { nickname: record.nickname };
  if (record.domain === EDeviceTypeName.Aircraft) {
    params.insurance_expired = record.insurance_expired || "";
  }
  updateDevice(params, workspaceId, record.device_sn).then((res) => {
    if (res.code === 0) {
      notification.success({ message: "修改成功" });
      getDevices(current.value[0], true);
    }
  }).catch((err) => {
    console.error(err);
    notification.error({ message: "修改失败" });
  });
}

// 解绑
function unbind() {
  deleteTip.value = false;
  unbindDevice(deleteSn.value?.toString()!).then((res) => {
    if (res.code !== 0) {
      return;
    }
    getDevices(current.value[0]);
  });
}

// 选择设备
function select(item: any) {
  getDevices(item.key);
}

const currentDevice = ref({} as Device);
// 设备日志
const deviceLogUploadRecordVisible = ref(false);
function showDeviceLogUploadRecord(dock: Device) {
  deviceLogUploadRecordVisible.value = true;
  currentDevice.value = dock;
}

// 健康状态
const hmsVisible = ref<boolean>(false);

function showHms(dock: Device) {
  hmsVisible.value = true;
  currentDevice.value = dock;
}

async function resetMqtt(dock: Device) {
  console.log(dock.device_sn);
  try {
    const res = await checkClientIdExists(dock.device_sn);
    // 检查是否存在
    // 假设返回结构中 data 为客户端列表，或者直接返回列表
    const clients = Array.isArray(res) ? res : (res?.data || []);

    if (clients && clients.length > 0) {
      const kickRes = await kickClientId(dock.device_sn);
      if (kickRes) {
        console.log(`✅ 成功踢除机场: ${dock.device_sn}`);
        notification.success({
          message: '踢除机场成功',
          description: '重置成功',
          duration: 2,
        });
      }
    } else {
      // 如果不存在，也视为成功
      notification.success({
        message: '重置成功',
        description: '设备不在线',
        duration: 2,
      });
    }
  } catch (error) {
    console.error(error);
    notification.error({
      message: '踢除机场失败',
      description: '重置失败',
      duration: 2,
    });
  }
}

// 机场详情
const airportDetailVisible = ref(false);
const selectedAirportDevice = ref<Device>();

function showAirportDetail(device: Device) {
  selectedAirportDevice.value = device;
  airportDetailVisible.value = true;
}

function refreshAirportData() {
  // 刷新当前设备数据
  getDevices(current.value[0], true);
}

onMounted(async () => {
  getDevices(current.value[0]);
  fetchPsdkWidgets();
  try {
    const res = await getUserInfo();
    if (res.code === 0 && res.data?.role_name === '管理员') {
      isAdmin.value = true;
    }
  } catch (e) {
    console.error("获取用户信息失败:", e);
  }
});
</script>

<style lang="scss" scoped>
.device-table-wrap {
  .editable-row-operations {
    div>span {
      margin-right: 10px;
    }
  }
}

.layout-sider {
  background-color: #04163A;
}

:deep(.ant-menu) {
  background-color: #04163A;
}

:deep(.ant-menu-item) {
  font-size: .875rem;
  color: #FFFFFF;
  font-weight: 400;
  cursor: pointer;
  padding-left: 3.375rem;
  margin-left: .75rem;
  margin-right: .25rem;
  position: relative;
}

.menu-icon {
  width: .5rem;
  height: .5rem;
  position: absolute;
  right: 3.625rem;
  top: 50%;
  transform: translateY(-50%);
}

.menu-icon::after {
  content: '';
  position: absolute;
  left: 0%;
  top: 0%;
  // transform: translateX(-50%);
  width: 0;
  height: 0;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
  border-left: 6px solid #fff;
}

:deep(.ant-menu:not(.ant-menu-horizontal) .ant-menu-item-selected) {
  background: rgba(54, 92, 166, 0.34);
  border-radius: .25rem;
  // opacity: 0.34;
  color: #FFFFFF;
  position: relative;
}

:deep(.ant-menu:not(.ant-menu-horizontal) .ant-menu-item-selected)::before {
  content: '';
  display: inline-block;
  width: 1rem;
  height: 1rem;
  background: url('@/assets/airControl/jzgl-tab-icon.png') no-repeat center/100% 100%;
  position: absolute;
  left: 1.75rem;
  top: 50%;
  transform: translateY(-50%);
}

.layout-cent {
  background-color: #04163A;
}

:deep(.ant-table-header) {
  border: none !important;
}

:deep(.ant-menu-vertical) {
  border: none;
}

/* Reduce default table cell padding */
:deep(.ant-table-cell) {
  padding: 8px !important;
}

.payload-table-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(59, 130, 246, 0.12);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 14px;
  color: #93c5fd;
  max-width: 160px;
  box-shadow: 0 0 4px rgba(59, 130, 246, 0.15);
  cursor: default;

  .payload-badge-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background-color: #3b82f6;
    box-shadow: 0 0 6px #3b82f6;
    flex-shrink: 0;
  }

  .payload-badge-text {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>

<style lang="scss">
.child-row {
  height: 50px;
}

.notice {
  background: green;
  overflow: hidden;
  cursor: pointer;
}

.caution {
  background: orange;
  cursor: pointer;
  overflow: hidden;
}

.warn {
  background: red;
  cursor: pointer;
  overflow: hidden;
}

.searchBox {
  .ant-select-selection-item {
    color: #FFFFFF !important;
  }

  .ant-tabs {
    .ant-tabs-nav {
      margin-bottom: 0;
      &::before {
        border-bottom: 1px solid rgba(255, 255, 255, 0.1) !important;
      }
    }

    .ant-tabs-tab {
      background: transparent !important;
      border: none !important;
      
      .ant-tabs-tab-btn {
        color: rgba(214, 229, 251, 0.6) !important;
        font-size: 14px;
        transition: color 0.3s ease;
      }

      &:hover .ant-tabs-tab-btn {
        color: #3A7AF9 !important;
      }
    }

    .ant-tabs-tab-active {
      .ant-tabs-tab-btn {
        color: #3A7AF9 !important;
        font-weight: bold;
      }
    }

    .ant-tabs-ink-bar {
      background: #3A7AF9 !important;
    }
  }
}
</style>
