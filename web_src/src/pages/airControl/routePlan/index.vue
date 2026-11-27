<script setup lang="ts">
import { reactive, ref, onMounted, Ref, computed } from "vue";
import { message } from "ant-design-vue";
import { EllipsisOutlined, UserOutlined, SelectOutlined, PlusOutlined, DeleteOutlined, SaveOutlined, ExclamationCircleOutlined, EditOutlined } from "@ant-design/icons-vue";
import { deleteWaylineFile, downloadWaylineFile, getWaylineFiles, getWaylineFilesDetail, importKmzFile, getWaylineData, modifyWaylineEdit } from "@/api/wayline";
import { getBindingDevices } from "@/api/manage";
import { ELocalStorageKey } from "@/types";
import { DEVICE_NAME, Device } from "@/types/device";
import storeInstance, { useMyStore } from "@/store";
import { WaylineFile } from "@/types/wayline";
import { downloadFile } from "@/utils/common";
import { IPage } from "@/api/http/type";
import NewWayLineDjiCesium from "@/components/NewWayLineDjiCesium.vue";
import Mapping2dCesium from "@/components/mapping2d/Mapping2dCesium.vue";
import { parseWaylineFile, WaylineData, WayPoint } from "@/utils/kml-parser";
// import NewWayLine from "@/components/wayLine/newWayLine.vue";
import { useEventEmitter } from "vue-hooks-plus";
import router from "@/router";

const store = useMyStore() || storeInstance;
const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId)!;

// Cesium 相关
const cesiumViewer = ref<any>(null);

// 页面状态
const loading = ref(false);

// 当前正在查看的航线相关状态
const currentViewingWaylineName = ref<string>("");
const currentViewingWaylineData = ref<any>(null); // 用于传递给地图组件的航线数据
const currentViewingWayline = ref<WaylineFile | null>(null); // 当前查看的航线列表项信息

// 判断当前正在查看的航线是否为面状航线
const isCurrentViewingMapping2d = computed(() => {
  if (currentViewingWayline.value && isMapping2d(currentViewingWayline.value)) {
    return true;
  }
  if (!currentViewingWaylineData.value) return false;
  const data = currentViewingWaylineData.value.data || currentViewingWaylineData.value;
  if (data.template_type === 'mapping2d' || data.template_type === 1) return true;
  if (Array.isArray(data.template_types) && (data.template_types.includes(1) || data.template_types.includes('mapping2d'))) return true;
  if (data.mapping_type_req?.coordinates && data.mapping_type_req.coordinates.length >= 3) return true;
  return false;
});

// Cesium 地图就绪回调
const onCesiumViewerReady = (viewer: any) => {
  cesiumViewer.value = viewer;
  console.log("Cesium Viewer 已就绪", viewer);
};

// 航线库相关状态
const waylinesData = reactive({
  data: [] as WaylineFile[],
});

const waylinePagination = reactive<IPage>({
  page: 1,
  total: 0,
  page_size: 10,
});

const deleteTip = ref(false);
const deleteWaylineId = ref<string>("");
const canRefresh = ref(true);

// 机场列表相关
const dockList = ref<Device[]>([]);
const selectedDockSn = ref<string>(""); // 筛选的机场SN

// 创建 dock_sn 到 nickname 的映射
const dockNameMap = computed(() => {
  const map = new Map<string, string>();
  dockList.value.forEach(dock => {
    map.set(dock.device_sn, dock.nickname || dock.device_name || dock.device_sn);
  });
  return map;
});

// 获取机场中文名称
function getDockName(dockSn: string): string {
  if (!dockSn) return "未关联机场";
  return dockNameMap.value.get(dockSn) || dockSn;
}


// 文件上传相关
interface FileItem {
  uid: string;
  name?: string;
  status?: string;
  response?: string;
  url?: string;
  originFileObj?: File;
}

const fileList = ref<FileItem[]>([]);

onMounted(async () => {
  // 加载机场列表
  await loadDocks();
  // 加载航线数据
  getWaylines();
});

// 获取机场列表
async function loadDocks() {
  try {
    // domain=3 表示获取机场数据
    const res = await getBindingDevices(workspaceId, { page: 1, page_size: 100 } as IPage, 3);
    if (res.code === 0 && res.data?.list) {
      dockList.value = res.data.list;
    }
  } catch (error) {
    console.error("加载机场列表失败:", error);
  }
}

// 机场筛选变更
function onDockFilterChange(value: string) {
  selectedDockSn.value = value;
  waylinePagination.page = 1; // 重置页码
  getWaylines();
}

// 关联机场相关状态
const associateModalVisible = ref(false);
const associatingWayline = ref<WaylineFile | null>(null);
const associateDockSn = ref<string>("");
const associateLoading = ref(false);

// 显示关联机场弹窗
function showAssociateDockModal(wayline: WaylineFile) {
  associatingWayline.value = wayline;
  associateDockSn.value = "";
  associateModalVisible.value = true;
}

// 确认关联机场
async function confirmAssociateDock() {
  if (!associatingWayline.value) {
    message.warning("请先选择航线");
    return;
  }
  if (!associateDockSn.value) {
    message.warning("请选择要关联的机场");
    return;
  }

  associateLoading.value = true;
  try {
    // 第一步：获取航线数据
    const waylineDataRes = await getWaylineData(workspaceId, associatingWayline.value.id);
    if (waylineDataRes.code !== 0 || !waylineDataRes.data) {
      message.error("获取航线数据失败: " + (waylineDataRes.message || "未知错误"));
      return;
    }

    // 第二步：调用关联接口，传入完整航线数据和dock_sn
    const modifyRes = await modifyWaylineEdit(
      workspaceId,
      associatingWayline.value.id,
      waylineDataRes.data,
      associateDockSn.value
    );

    if (modifyRes.code === 0) {
      message.success(`航线已成功关联到机场: ${getDockName(associateDockSn.value)}`);
      associateModalVisible.value = false;
      associatingWayline.value = null;
      associateDockSn.value = "";
      // 刷新航线列表
      refreshWaylines();
    } else {
      message.error("关联机场失败: " + (modifyRes.message || "未知错误"));
    }
  } catch (error: any) {
    message.error(`关联机场失败: ${error.message || "未知错误"}`);
  } finally {
    associateLoading.value = false;
  }
}

// 取消关联
function cancelAssociateDock() {
  associateModalVisible.value = false;
  associatingWayline.value = null;
  associateDockSn.value = "";
}

// 获取航线文件
function getWaylines() {
  if (!canRefresh.value) {
    return;
  }
  canRefresh.value = false;
  loading.value = true;
  getWaylineFilesDetail(workspaceId, waylinePagination.page, waylinePagination.page_size, {
    dock_sn: selectedDockSn.value || undefined
  })
    .then((res) => {
      if (res.code !== 0) {
        return;
      }
      // 改为覆盖数据，不再追加
      waylinesData.data = res.data.list;
      waylinePagination.total = res.data.pagination.total;
      waylinePagination.page = res.data.pagination.page;
    })
    .finally(() => {
      canRefresh.value = true;
      loading.value = false;
    });
}

// 分页页码变更处理
function onPageChange(page: number, pageSize: number) {
  waylinePagination.page = page;
  if (pageSize && pageSize !== waylinePagination.page_size) {
    waylinePagination.page_size = pageSize;
  }
  getWaylines();
}

// 每页条数变更处理
function onPageSizeChange(pageSize: number) {
  waylinePagination.page = 1;
  waylinePagination.page_size = pageSize;
  getWaylines();
}

// 显示删除提示
function showWaylineTip(waylineId: string) {
  deleteWaylineId.value = waylineId;
  deleteTip.value = true;
}

// 删除航线文件
function deleteWayline() {
  deleteWaylineFile(workspaceId, deleteWaylineId.value).then((res) => {
    if (res.code === 0) {
      message.success("航线文件删除成功");
    }
    deleteWaylineId.value = "";
    deleteTip.value = false;
    // 刷新列表
    refreshWaylines();
  });
}

// 下载航线文件
function downloadWayline(waylineId: string, fileName: string) {
  loading.value = true;
  downloadWaylineFile(workspaceId, waylineId)
    .then((res) => {
      if (!res) {
        return;
      }
      const data = new Blob([res], { type: "application/zip" });
      downloadFile(data, fileName + ".kmz");
    })
    .finally(() => {
      loading.value = false;
    });
}

// 选择路线
function selectRoute(wayline: WaylineFile) {
  console.log('store', store)
  if (store) {
    store.commit("SET_SELECT_WAYLINE_INFO", wayline);
  } else {
    console.error("Store is not initialized");
    message.error("系统错误：Store未初始化");
  }
  // message.success(`已选择航线: ${wayline.name}`);

  // 下载并解析航线文件，然后在地图上绘制
  downloadAndDrawWayline(wayline.id, wayline.name, wayline);
}

// 下载并加载航线数据用于预览
async function downloadAndDrawWayline(waylineId: string, fileName: string, waylineItem?: WaylineFile) {
  loading.value = true;
  if (waylineItem) {
    currentViewingWayline.value = waylineItem;
  } else {
    const matched = waylinesData.data.find(w => w.id === waylineId);
    if (matched) currentViewingWayline.value = matched;
  }

  try {
    currentViewingWaylineName.value = fileName;

    // 1. 优先调用 getWaylineData 获取航线完整数据
    let loadedData: any = null;
    try {
      const res = await getWaylineData(workspaceId, waylineId);
      if (res && res.code === 0 && res.data) {
        if (res.data.route_point_list && res.data.route_point_list.length > 0) {
          loadedData = res.data;
        } else if (res.data.mapping_type_req?.sim_route_point_list && res.data.mapping_type_req.sim_route_point_list.length > 0) {
          loadedData = {
            ...res.data,
            route_point_list: res.data.mapping_type_req.sim_route_point_list.map((p: any) => ({
              longitude: p.wg_lon ?? p.longitude ?? p.lon,
              latitude: p.wg_lat ?? p.latitude ?? p.lat,
              height: p.height || 100
            }))
          };
        } else if (res.data.mapping_type_req?.coordinates && res.data.mapping_type_req.coordinates.length >= 3) {
          // 纯测区多边形面状航线数据
          loadedData = res.data;
        }
      }
    } catch (apiErr) {
      console.warn("getWaylineData 获取航线失败，尝试 KMZ 解析:", apiErr);
    }

    // 2. 备选方案：下载并解压 KMZ 文件解析航线点位
    if (!loadedData) {
      try {
        const kmzRes = await downloadWaylineFile(workspaceId, waylineId);
        if (kmzRes) {
          const blob = kmzRes instanceof Blob ? kmzRes : new Blob([kmzRes], { type: "application/zip" });
          const waylineData = await parseWaylineFile(blob);
          if (waylineData && waylineData.waypoints && waylineData.waypoints.length > 0) {
            const takeOff = waylineData.takeOffRefPoint || {
              latitude: waylineData.waypoints[0].latitude,
              longitude: waylineData.waypoints[0].longitude,
              height: 0
            };
            loadedData = {
              take_off_ref_point: `${takeOff.latitude},${takeOff.longitude},${takeOff.height || 0}`,
              route_point_list: waylineData.waypoints.map(wp => ({
                longitude: wp.longitude,
                latitude: wp.latitude,
                height: wp.height || 100,
                heading: 0,
                gimbal_pitch: -90,
                gimbal_yaw_rotate_angle: 0
              }))
            };
          }
        }
      } catch (kmzErr) {
        console.warn("KMZ 解析航线失败:", kmzErr);
      }
    }

    if (loadedData) {
      // 检查是否缺少有效起飞参考点，如果缺少则尝试通过 KMZ 或关联机场补全
      const hasValidTakeoff = loadedData.take_off_ref_point &&
        loadedData.take_off_ref_point !== '0,0,0' &&
        loadedData.take_off_ref_point !== '0,0' &&
        !loadedData.take_off_ref_point.startsWith('0,0');

      if (!hasValidTakeoff) {
        try {
          const kmzRes = await downloadWaylineFile(workspaceId, waylineId);
          if (kmzRes) {
            const blob = kmzRes instanceof Blob ? kmzRes : new Blob([kmzRes], { type: "application/zip" });
            const waylineData = await parseWaylineFile(blob);
            if (waylineData?.takeOffRefPoint && (waylineData.takeOffRefPoint.latitude || waylineData.takeOffRefPoint.longitude)) {
              loadedData.take_off_ref_point = `${waylineData.takeOffRefPoint.latitude},${waylineData.takeOffRefPoint.longitude},${waylineData.takeOffRefPoint.height || 0}`;
            }
          }
        } catch (kmzErr) {
          console.warn("尝试从 KMZ 补充起飞点失败:", kmzErr);
        }
      }

      // 如果依然没有起飞点，尝试根据绑定的 dock_sn 从关联的机场坐标补全
      const currentTakeoffValid = loadedData.take_off_ref_point &&
        loadedData.take_off_ref_point !== '0,0,0' &&
        !loadedData.take_off_ref_point.startsWith('0,0');
      const targetDockSn = currentViewingWayline.value?.dock_sn || loadedData.dock_sn;
      if (!currentTakeoffValid && targetDockSn) {
        let matchedDock: any = null;
        const dockMapStr = localStorage.getItem('dockMap');
        if (dockMapStr) {
          try {
            const list = JSON.parse(dockMapStr);
            if (Array.isArray(list)) matchedDock = list.find((d: any) => d.sn === targetDockSn);
          } catch (e) {}
        }
        if (!matchedDock && dockList.value?.length > 0) {
          const d: any = dockList.value.find(item => item.device_sn === targetDockSn);
          if (d) {
            matchedDock = {
              latitude: d.basic_osd?.latitude ?? d.latitude,
              longitude: d.basic_osd?.longitude ?? d.longitude,
              height: d.basic_osd?.height ?? d.height ?? 0
            };
          }
        }
        if (matchedDock && matchedDock.latitude && matchedDock.longitude) {
          loadedData.take_off_ref_point = `${matchedDock.latitude},${matchedDock.longitude},${matchedDock.height || 0}`;
        }
      }

      // 重新赋值触发响应式更新
      currentViewingWaylineData.value = { ...loadedData };
      const pointCount = loadedData.route_point_list?.length || loadedData.mapping_type_req?.sim_route_point_list?.length || 0;
      message.success(`已加载航线 "${fileName}"${pointCount > 0 ? `，共 ${pointCount} 个航点` : ''}`);
    } else {
      message.warning("该航线暂无有效数据");
      currentViewingWaylineData.value = null;
    }
  } catch (error: any) {
    console.error("航线加载失败:", error);
    message.error(`航线加载失败: ${error.message || "未知错误"}`);
    currentViewingWaylineData.value = null;
  } finally {
    loading.value = false;
  }
}

// 清除当前查看的航线
function clearCurrentWayline() {
  currentViewingWaylineName.value = "";
  currentViewingWaylineData.value = null;
  currentViewingWayline.value = null;
}

const eventBack = useEventEmitter({ global: true })
eventBack.subscribe('back', () => {
  createNewWaylineFlag.value = false
})
const createNewWaylineFlag = ref(false)
// 判断是否为面状航线 (template_types 为 0 是航点航线，为 1 是面状航线)
const isMapping2d = (wayline: any) => {
  if (!wayline) return false
  if (Array.isArray(wayline.template_types)) {
    if (wayline.template_types.includes(1) || (wayline.template_types as any).includes('mapping2d')) {
      return true
    }
  }
  if (wayline.template_type === 1 || wayline.template_type === 'mapping2d') {
    return true
  }
  return false
}

// 新增航线
function createNewWayline() {
  createNewWaylineFlag.value = true;
  sessionStorage.removeItem('editWaylineData');
  localStorage.removeItem('editWaylineData');
  router.push({
    path: '/newWayLine',
    query: { mode: 'create' }
  });
}

// 修改航线
async function editWayline(wayline: WaylineFile) {
  loading.value = true;
  try {
    const res = await getWaylineData(workspaceId, wayline.id);
    if (res.code === 0 && res.data) {
      // 判断是否为面状航线并补充 template_type
      const isMap2d = isMapping2d(wayline) || res.data.template_type === 1 || res.data.template_type === 'mapping2d';
      const editData = {
        waylineId: wayline.id,
        waylineName: wayline.name,
        waylineData: {
          ...res.data,
          template_type: isMap2d ? 'mapping2d' : 'waypoint'
        },
        isEditMode: true
      };
      // 多层存储保证刷新或新窗口不丢失
      const serialized = JSON.stringify(editData);
      sessionStorage.setItem(`editWaylineData_${wayline.id}`, serialized);
      sessionStorage.setItem('editWaylineData', serialized);
      localStorage.setItem('editWaylineData', serialized);

      router.push({
        path: '/newWayLine',
        query: { id: wayline.id, mode: 'edit' }
      });
      message.success(`正在编辑航线: ${wayline.name}`);
    } else {
      message.error('获取航线数据失败: ' + (res.message || '未知错误'));
    }
  } catch (error: any) {
    message.error(`获取航线数据失败: ${error.message || '未知错误'}`);
  } finally {
    loading.value = false;
  }
}

// 刷新航线列表
function refreshWaylines() {
  waylinePagination.page = 1;
  getWaylines();
}

// 文件上传相关
function beforeUpload(file: FileItem) {
  fileList.value = [file];
  loading.value = true;
  return true;
}

const uploadFile = async () => {
  fileList.value.forEach(async (file: FileItem) => {
    const fileData = new FormData();
    fileData.append("file", file.originFileObj || (file as any), file.name);
    await importKmzFile(workspaceId, fileData)
      .then((res) => {
        if (res.code === 0) {
          message.success(`${file.name} 文件上传成功`);
          canRefresh.value = true;
          waylinePagination.total = 0;
          waylinePagination.page = 1;
          waylinesData.data = [];
          getWaylines();
        }
      })
      .finally(() => {
        loading.value = false;
        fileList.value = [];
      });
  });
};
</script>

<template>
  <!-- <newWayLine v-if="createNewWaylineFlag"/> -->
  <div class="route-plan-container">
    <!-- 地图背景 -->
    <div class="map-background">
      <!-- 面状航线在首页调用 Mapping2dCesium 组件进行展示 -->
      <Mapping2dCesium 
        v-if="isCurrentViewingMapping2d"
        style="height: 100%; width: 100%" 
        :editWaylineData="currentViewingWaylineData"
        :waylineName="currentViewingWaylineName"
        :isEditMode="false"
        :previewMode="true"
        :readOnly="true"
        @close="clearCurrentWayline"
      />
      <!-- 航点航线（及默认底图）调用 NewWayLineDjiCesium 展示 -->
      <NewWayLineDjiCesium 
        v-else
        style="height: 100%; width: 100%" 
        :editWaylineData="currentViewingWaylineData"
        :isEditMode="false"
        :previewMode="true"
        @viewer-ready="onCesiumViewerReady" 
      />

      <!-- 当前查看的航线名称显示 -->
      <div v-if="currentViewingWaylineName" class="viewing-wayline-panel">
        <div class="viewing-wayline-content">
          <span class="wayline-icon">✈️</span>
          <span class="wayline-label">当前航线：</span>
          <span class="wayline-name-display">{{ currentViewingWaylineName }}</span>
          <a class="close-btn" @click="clearCurrentWayline" title="关闭航线显示">
            ✕
          </a>
        </div>
      </div>
    </div>
    <main class="main-content">
      <a-spin :spinning="loading" :delay="300" tip="loading" size="large">
        <div class="library-main">
          <div class="library-header">
            <div class="library-title">
              <span>航线库</span>
            </div>
            <div class="library-actions">
              <div class="normal-actions">
                <a-button type="primary" @click="createNewWayline" class="upload-btn upload-btn-center action-btn"
                  size="small">
                  <PlusOutlined />
                  新建航线
                </a-button>
                <a-upload name="file" :multiple="false" :before-upload="beforeUpload" :show-upload-list="false"
                  :customRequest="uploadFile">
                  <a-button type="primary" class="upload-btn upload-btn-center action-btn" size="small">
                    <span class="upload-btn-content">
                      <SelectOutlined />
                      <span class="ml5">上传航线</span>
                    </span>
                  </a-button>
                </a-upload>
              </div>
            </div>
          </div>

          <!-- 机场筛选下拉框 -->
          <div class="dock-filter-section">
            <span class="filter-label">机场筛选：</span>
            <a-select v-model:value="selectedDockSn" @change="onDockFilterChange" placeholder="全部机场" allow-clear
              class="dock-select" size="small">
              <a-select-option value="">全部机场</a-select-option>
              <a-select-option v-for="dock in dockList" :key="dock.device_sn" :value="dock.device_sn">
                {{ dock.nickname || dock.device_name || dock.device_sn }}
              </a-select-option>
            </a-select>
          </div>

          <div class="library-content">
            <div class="wayline-scroll-area">
              <div v-if="waylinesData.data.length !== 0">
                <div v-for="wayline in waylinesData.data" :key="wayline.id">
                  <div class="wayline-panel" @click="selectRoute(wayline)">
                    <div class="title">
                      <span v-if="isMapping2d(wayline)" class="wayline-type-badge mapping2d-badge">面状</span>
                      <span v-else class="wayline-type-badge waypoint-badge">航点</span>
                      <a-tooltip :title="wayline.name">
                        <div class="wayline-name">{{ wayline.name }}</div>
                      </a-tooltip>
                      <div class="ml10">
                        <UserOutlined />
                      </div>
                      <a-tooltip :title="wayline.user_name">
                        <div class="user-name">{{ wayline.user_name }}</div>
                      </a-tooltip>
                      <div class="actions">
                        <a-dropdown>
                          <a style="color: white">
                            <EllipsisOutlined />
                          </a>
                          <template #overlay>
                            <a-menu theme="dark" class="more custom-context-menu"
                              style="background: #041428; border: 1px solid #1e3461">
                              <a-menu-item @click.stop="downloadAndDrawWayline(wayline.id, wayline.name, wayline)">
                                <span style="color: #fff">在地图上查看</span>
                              </a-menu-item>
                              <a-menu-item @click.stop="editWayline(wayline)">
                                <span style="color: #fff">
                                  <EditOutlined class="mr-1" />修改
                                </span>
                              </a-menu-item>
                              <a-menu-item @click.stop="downloadWayline(wayline.id, wayline.name)">
                                <span style="color: #fff">下载</span>
                              </a-menu-item>
                              <a-menu-item v-if="!wayline.dock_sn" @click.stop="showAssociateDockModal(wayline)">
                                <span style="color: #00d4ff">🏢 关联机场</span>
                              </a-menu-item>
                              <a-menu-item @click.stop="showWaylineTip(wayline.id)">
                                <span style="color: #fff">删除</span>
                              </a-menu-item>
                            </a-menu>
                          </template>
                        </a-dropdown>
                      </div>
                    </div>
                    <div class="device-info-row flex-row flex-align-center">
                      <span class="icon-center"><svg-icon name="drone" :size="14" /></span>
                      <span class="ml5">{{ DEVICE_NAME[wayline.drone_model_key] }}</span>
                      <span class="ml10 icon-center"
                        v-if="wayline.payload_model_keys && wayline.payload_model_keys.length > 0">
                        <svg-icon name="playload" :size="14" />
                      </span>
                      <span class="ml5" v-for="payload in wayline.payload_model_keys || []" :key="payload">
                        {{ DEVICE_NAME[payload] }}
                      </span>
                    </div>
                    <div class="dock-info-row flex-row flex-align-center">
                      <span class="icon-center">🏢</span>
                      <span class="ml5 dock-name">{{ getDockName(wayline.dock_sn) }}</span>
                    </div>
                    <div class="update-time">
                      <span>更新时间: {{ new Date(wayline.update_time).toLocaleString() }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="empty-state">
                <a-empty :image-style="{ height: '60px', marginTop: '60px' }" description="暂无航线数据" />
              </div>
            </div>
            <!-- 分页组件 - 固定在底部 (优化为双层清晰布局，适配 360px 窄边栏) -->
            <div v-if="waylinesData.data.length > 0" class="pagination-container">
              <div class="pagination-top-bar">
                <span class="total-text">共 <strong class="highlight-count">{{ waylinePagination.total }}</strong> 条航线</span>
                <a-select
                  v-model:value="waylinePagination.page_size"
                  size="small"
                  class="page-size-selector"
                  dropdownClassName="dark-page-select-dropdown"
                  popupClassName="dark-page-select-dropdown"
                  @change="onPageSizeChange"
                  :dropdown-match-select-width="false"
                >
                  <a-select-option :value="5">5 条/页</a-select-option>
                  <a-select-option :value="10">10 条/页</a-select-option>
                  <a-select-option :value="20">20 条/页</a-select-option>
                  <a-select-option :value="50">50 条/页</a-select-option>
                </a-select>
              </div>
              <div class="pagination-pager-wrapper">
                <a-pagination
                  v-model:current="waylinePagination.page"
                  :total="waylinePagination.total"
                  :page-size="waylinePagination.page_size"
                  :show-size-changer="false"
                  size="small"
                  @change="onPageChange"
                />
              </div>
            </div>
          </div>
        </div>
      </a-spin>
    </main>

    <!-- 删除确认弹窗 -->
    <a-modal v-model:visible="deleteTip" width="450px" :closable="false" :maskClosable="false" centered
      :okButtonProps="{ danger: true }" @ok="deleteWayline" wrap-class-name="delete-confirm-modal">
      <div class="flex-display flex-align-center flex-justify-center pt10 pb10" style="color: #fff">
        <ExclamationCircleOutlined style="font-size: 22px; color: #faad14; margin-right: 12px;" />
        <span style="font-size: 16px;">航线文件删除后无法恢复，确认删除吗？</span>
      </div>
      <template #title>
        <div class="flex-row flex-justify-center" style="color: #fff">
          <span>删除确认</span>
        </div>
      </template>
    </a-modal>

    <!-- 关联机场弹窗 -->
    <a-modal v-model:visible="associateModalVisible" width="450px" :closable="true" :maskClosable="false" centered
      :confirmLoading="associateLoading" @ok="confirmAssociateDock" @cancel="cancelAssociateDock"
      wrap-class-name="associate-dock-modal">
      <template #title>
        <div class="flex-row flex-justify-center" style="color: #fff">
          <span>🏢 关联机场</span>
        </div>
      </template>
      <div class="associate-dock-content" style="padding: 20px 0;">
        <div style="color: #d6e5fb; margin-bottom: 15px;">
          <span>航线名称：</span>
          <span style="color: #00d4ff; font-weight: bold;">{{ associatingWayline?.name }}</span>
        </div>
        <div style="color: #d6e5fb; margin-bottom: 10px;">
          <span>选择要关联的机场：</span>
        </div>
        <a-select v-model:value="associateDockSn" placeholder="请选择机场" style="width: 100%" size="large">
          <a-select-option v-for="dock in dockList" :key="dock.device_sn" :value="dock.device_sn">
            {{ dock.nickname || dock.device_name || dock.device_sn }}
          </a-select-option>
        </a-select>
      </div>
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.route-plan-container {
  position: relative;
  display: flex;
  height: 100%;
  width: 100%;
  overflow: hidden;
}

.map-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;

  // 当前查看的航线名称面板
  .viewing-wayline-panel {
    position: absolute;
    top: 20px;
    left: 50%;
    transform: translateX(-50%);
    z-index: 100;
    background: linear-gradient(135deg, rgba(4, 22, 58, 0.95), rgba(20, 43, 91, 0.95));
    border: 1px solid rgba(58, 122, 249, 0.5);
    border-radius: 8px;
    padding: 12px 20px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5), 0 0 15px rgba(58, 122, 249, 0.3);
    backdrop-filter: blur(10px);

    .viewing-wayline-content {
      display: flex;
      align-items: center;
      gap: 8px;

      .wayline-icon {
        font-size: 18px;
      }

      .wayline-label {
        color: #d6e5fb;
        font-size: 14px;
      }

      .wayline-name-display {
        color: #00d4ff;
        font-size: 16px;
        font-weight: bold;
        max-width: 300px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .close-btn {
        margin-left: 15px;
        color: rgba(255, 255, 255, 0.6);
        font-size: 14px;
        cursor: pointer;
        padding: 4px 8px;
        border-radius: 4px;
        transition: all 0.2s;

        &:hover {
          color: #ff4d4f;
          background: rgba(255, 77, 79, 0.1);
        }
      }
    }
  }
}

.main-content {
  position: relative;
  width: 360px;
  height: 100%;
  z-index: 10;
  padding: 20px;
  overflow: hidden;
  margin-left: 0;
  background: #04163A;
  backdrop-filter: blur(10px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
  display: flex;
  flex-direction: column;

  // 确保 a-spin 组件不破坏 flex 布局
  :deep(.ant-spin-nested-loading) {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
  }

  :deep(.ant-spin-container) {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
  }
}

.library-main {
  flex: 1;
  min-height: 0; // 关键：确保能正确收缩
  display: flex;
  flex-direction: column;

  .library-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 0 20px 0;
    border-bottom: 3px solid #203A6E !important;
    margin-bottom: 20px;

    .library-title {
      font-size: 1rem;
      font-weight: bold;
      color: #fff;
    }

    .library-actions {
      display: flex;
      gap: 10px;

      .edit-actions,
      .normal-actions {
        display: flex;
        gap: 8px;
      }

      .action-btn {
        padding: 0 12px;
      }

      .upload-btn {
        background: #3a7af9;
        border-color: #3a7af9;
        color: #fff;
        display: flex;
        align-items: center;
        justify-content: center;

        &:hover {
          background: #5b8bfa;
          border-color: #5b8bfa;
        }
      }

      .upload-btn-center {
        width: auto;
        height: 32px;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 0 12px;
      }

      .upload-btn-content {
        display: flex;
        align-items: center;
        justify-content: center;
      }
    }
  }

  .edit-info-panel {
    background: rgba(58, 122, 249, 0.1);
    border: 1px solid #3a7af9;
    border-radius: 4px;
    padding: 12px;
    margin-bottom: 15px;
    color: #fff;

    .info-item {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;
      font-size: 13px;

      .label {
        color: #d6e5fb;
      }

      .value {
        color: #3a7af9;
        font-weight: bold;
      }
    }

    .waypoints-list {
      margin-top: 10px;
      max-height: 120px;
      overflow-y: auto;
      border-top: 1px solid #3a7af9;
      padding-top: 8px;

      .waypoint-item {
        font-size: 12px;
        color: #d6e5fb;
        margin-bottom: 4px;
        padding: 4px;
        background: rgba(0, 0, 0, 0.2);
        border-radius: 2px;
      }
    }
  }

  // 机场筛选区域样式
  .dock-filter-section {
    display: flex;
    align-items: center;
    margin-bottom: 15px;
    padding: 10px;
    background: rgba(58, 122, 249, 0.08);
    border-radius: 4px;
    border: 1px solid rgba(58, 122, 249, 0.2);

    .filter-label {
      color: #d6e5fb;
      font-size: 13px;
      white-space: nowrap;
      margin-right: 10px;
    }

    .dock-select {
      flex: 1;

      :deep(.ant-select-selector) {
        background: rgba(0, 0, 0, 0.3) !important;
        border-color: rgba(255, 255, 255, 0.2) !important;
        color: #fff;
      }

      :deep(.ant-select-selection-placeholder) {
        color: rgba(255, 255, 255, 0.5);
      }

      :deep(.ant-select-arrow) {
        color: rgba(255, 255, 255, 0.5);
      }

      :deep(.ant-select-clear) {
        background: transparent;
        color: rgba(255, 255, 255, 0.5);
      }
    }
  }

  .library-content {
    flex: 1;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    min-height: 0;

    .wayline-scroll-area {
      flex: 1;
      overflow-y: auto;
      overflow-x: hidden;
      min-height: 0;
      scrollbar-width: thin;
      scrollbar-color: #5babcb transparent;

      &::-webkit-scrollbar {
        width: 6px;
      }

      &::-webkit-scrollbar-track {
        background: rgba(255, 255, 255, 0.1);
        border-radius: 3px;
      }

      &::-webkit-scrollbar-thumb {
        background: #5babcb;
        border-radius: 3px;

        &:hover {
          background: #7cc5dd;
        }
      }
    }

    .wayline-panel {
      background: #142B5B;
      backdrop-filter: blur(8px);
      margin: 10px 0;
      height: 110px;
      width: 100%;
      font-size: 13px;
      border-radius: 4px;
      cursor: pointer;
      padding: 5px 10px;
      transition: all 0.3s;
      border: 1px solid rgba(255, 255, 255, 0.1);
      color: #D6E5FB;

      &:hover {
        background: rgba(67, 108, 203, 0.95);
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
      }

      .title {
        display: flex;
        flex-direction: row;
        align-items: center;
        height: 30px;
        font-weight: bold;
        margin-bottom: 5px;

        .wayline-type-badge {
          display: inline-block;
          font-size: 10px;
          line-height: 1;
          padding: 2px 5px;
          border-radius: 3px;
          font-weight: bold;
          margin-right: 6px;
          white-space: nowrap;
          flex-shrink: 0;

          &.mapping2d-badge {
            background: rgba(2, 132, 199, 0.3);
            color: #38bdf8;
            border: 1px solid rgba(56, 189, 248, 0.5);
          }

          &.waypoint-badge {
            background: rgba(16, 185, 129, 0.25);
            color: #34d399;
            border: 1px solid rgba(52, 211, 153, 0.4);
          }
        }

        .wayline-name {
          width: 120px;
          white-space: nowrap;
          text-overflow: ellipsis;
          overflow: hidden;
          padding-right: 10px;
        }

        .user-name {
          width: 80px;
          white-space: nowrap;
          text-overflow: ellipsis;
          overflow: hidden;
          padding-right: 10px;
          margin-left: 5px;
        }

        .actions {
          margin-left: auto;
          font-size: 20px;
        }
      }

      .device-info-row {
        margin-left: 10px;
        margin-top: 5px;
        color: hsla(0, 0%, 100%, 0.65);

        .ml5 {
          margin-left: 5px;
        }

        .ml10 {
          margin-left: 10px;
        }

        .icon-center {
          display: flex;
          align-items: center;
          justify-content: center;
        }
      }

      .dock-info-row {
        margin-left: 10px;
        margin-top: 5px;
        color: hsla(0, 0%, 100%, 0.65);
        font-size: 12px;

        .icon-center {
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 12px;
        }

        .ml5 {
          margin-left: 5px;
        }

        .dock-name {
          color: #00d4ff;
          font-weight: 500;
        }
      }

      .update-time {
        margin-top: 5px;
        margin-left: 10px;
        color: hsla(0, 0%, 100%, 0.35);
        font-size: 12px;
      }
    }

    .empty-state {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 300px;
    }

    .pagination-container {
      flex-shrink: 0; // 不收缩，保持固定高度
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 12px 14px;
      border-top: 1px solid #1e3461;
      background: #04163A; // 与背景色一致

      .pagination-top-bar {
        display: flex;
        align-items: center;
        justify-content: space-between;

        .total-text {
          font-size: 12px;
          color: rgba(255, 255, 255, 0.65);

          .highlight-count {
            color: #00d4ff;
            font-weight: 600;
          }
        }

        .page-size-selector {
          width: 96px;

          :deep(.ant-select-selector) {
            background-color: #08224d !important;
            border: 1px solid #1e3461 !important;
            color: #d6e5fb !important;
            border-radius: 4px;
            height: 24px !important;
            padding: 0 8px !important;

            .ant-select-selection-item {
              line-height: 22px !important;
              font-size: 11px;
            }
          }

          :deep(.ant-select-arrow) {
            color: rgba(255, 255, 255, 0.5);
            font-size: 10px;
          }
        }
      }

      .pagination-pager-wrapper {
        display: flex;
        justify-content: center;

        :deep(.ant-pagination) {
          display: flex;
          align-items: center;
          gap: 4px;

          .ant-pagination-prev,
          .ant-pagination-next,
          .ant-pagination-jump-prev,
          .ant-pagination-jump-next {
            min-width: 26px;
            height: 26px;
            line-height: 24px;
            margin: 0;

            .ant-pagination-item-link {
              background-color: rgba(255, 255, 255, 0.06);
              border: 1px solid #1e3461;
              color: rgba(255, 255, 255, 0.7);
              border-radius: 4px;
              transition: all 0.2s;

              &:hover {
                background-color: rgba(58, 122, 249, 0.25);
                border-color: #3a7af9;
                color: #fff;
              }
            }

            &.ant-pagination-disabled .ant-pagination-item-link {
              background-color: rgba(255, 255, 255, 0.02);
              border-color: rgba(255, 255, 255, 0.08);
              color: rgba(255, 255, 255, 0.25);
            }
          }

          .ant-pagination-item {
            min-width: 26px;
            height: 26px;
            line-height: 24px;
            margin: 0;
            background-color: rgba(255, 255, 255, 0.06);
            border: 1px solid #1e3461;
            border-radius: 4px;
            transition: all 0.2s;

            a {
              color: rgba(255, 255, 255, 0.75);
              font-size: 12px;
            }

            &:hover {
              background-color: rgba(58, 122, 249, 0.2);
              border-color: #3a7af9;

              a {
                color: #fff;
              }
            }

            &.ant-pagination-item-active {
              background: linear-gradient(135deg, #1d6bf3, #0a4ec9);
              border-color: #3a7af9;
              box-shadow: 0 0 8px rgba(29, 107, 243, 0.5);

              a {
                color: #fff;
                font-weight: 600;
              }
            }
          }

          .ant-pagination-jump-prev,
          .ant-pagination-jump-next {
            .ant-pagination-item-ellipsis {
              color: rgba(255, 255, 255, 0.5);
            }
          }
        }
      }
    }
  }
}

:deep(.ant-picker) {
  background: #1e3461;
  border-color: #1e3461;
  color: #fff;

  &:hover {
    border-color: #3a7af9;
  }

  .ant-calendar-picker-input * {
    color: #d6e5fb;
  }

  .ant-calendar-range-picker-separator {
    color: #d6e5fb;
  }
}

:deep(.ant-select) {
  .ant-select-selector {
    background: #1e3461;
    border-color: #1e3461;
    color: #fff;

    &:hover {
      border-color: #3a7af9;
    }

    .ant-select-selection-placeholder {
      color: #d6e5fb;
    }

    .ant-select-selection-item {
      color: #fff;
    }
  }

  .ant-select-arrow {
    color: #d6e5fb;
  }

  &:hover .ant-select-selector {
    border-color: #3a7af9;
  }
}

:deep(.ant-input) {
  background: #1e3461;
  border-color: #1e3461;
  color: #fff;

  &:hover {
    border-color: #3a7af9;
  }

  &:focus {
    border-color: #3a7af9;
    box-shadow: 0 0 0 2px rgba(58, 122, 249, 0.2);
  }

  &::placeholder {
    color: #d6e5fb;
  }
}

:deep(.ant-btn) {
  &.reset-button {
    background: #27437a;
    border-color: #27437a;
    color: #fff;

    &:hover {
      background: #3a558f;
      border-color: #3a558f;
    }
  }
}

.flex-row {
  display: flex;
  flex-direction: row;
}

.flex-display {
  display: flex;
}

.flex-align-center {
  align-items: center;
}

.flex-justify-center {
  justify-content: center;
}

.ml5 {
  margin-left: 5px;
}

.ml10 {
  margin-left: 10px;
}

.pl15 {
  padding-left: 15px;
}

.pl20 {
  padding-left: 20px;
}

.pt10 {
  padding-top: 10px;
}

.circle-icon {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.height-100 {
  height: 100%;
}

.scrollbar {
  overflow: auto;
}

// 分页组件暗色主题样式
:deep(.ant-pagination) {
  .ant-pagination-item {
    background: #1e3461;
    border-color: #1e3461;

    a {
      color: #d6e5fb;
    }

    &:hover {
      border-color: #3a7af9;

      a {
        color: #3a7af9;
      }
    }

    &.ant-pagination-item-active {
      background: #3a7af9;
      border-color: #3a7af9;

      a {
        color: #fff;
      }
    }
  }

  .ant-pagination-prev,
  .ant-pagination-next {
    .ant-pagination-item-link {
      background: #1e3461;
      border-color: #1e3461;
      color: #d6e5fb;
    }

    &:hover .ant-pagination-item-link {
      border-color: #3a7af9;
      color: #3a7af9;
    }
  }

  .ant-pagination-options {
    .ant-select-selector {
      background: #1e3461 !important;
      border-color: #1e3461 !important;
      color: #d6e5fb !important;
    }
  }

  .ant-pagination-total-text {
    color: #d6e5fb;
  }
}

// 关联机场弹窗样式
:deep(.associate-dock-modal) {
  .ant-modal-content {
    background: #041428;
    border: 1px solid #1e3461;
  }

  .ant-modal-header {
    background: #041428;
    border-bottom: 1px solid #1e3461;
  }

  .ant-modal-body {
    background: #041428;
  }

  .ant-modal-footer {
    background: #041428;
    border-top: 1px solid #1e3461;
  }

  .ant-modal-close-x {
    color: #d6e5fb;
  }

  .ant-select-selector {
    background: #1e3461 !important;
    border-color: #1e3461 !important;
    color: #fff !important;
  }

  .ant-select-selection-placeholder {
    color: #d6e5fb !important;
  }

  .ant-select-arrow {
    color: #d6e5fb;
  }
}

// 显示图例功能，将其定位到地图左下角（避开左侧360px的主内容面板）
:deep(.map-legend) {
  left: 380px !important;
  right: auto !important;
  bottom: 20px !important;
  z-index: 10;
}

:deep(.dark-page-select-dropdown) {
  background-color: #08224d !important;
  border: 1px solid #1e3461 !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5) !important;

  .ant-select-item {
    color: #d6e5fb;
    font-size: 12px;

    &-option-selected {
      background-color: rgba(58, 122, 249, 0.3) !important;
      color: #00d4ff !important;
      font-weight: 600;
    }

    &-option-active {
      background-color: rgba(58, 122, 249, 0.15) !important;
      color: #fff;
    }
  }
}
</style>
