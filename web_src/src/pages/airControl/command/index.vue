<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { message } from "ant-design-vue";
import { CalendarOutlined, CheckSquareOutlined, BorderOutlined, InfoCircleOutlined, FileImageOutlined } from "@ant-design/icons-vue";
import { getWaylineJobs, Task, WaylineBody, getWaylineFiles } from "@/api/wayline";
import { WaylineType } from "@/types/wayline";
import { TaskStatus, TaskStatusMap } from "@/types/task";
import { ELocalStorageKey, EDeviceTypeName } from "@/types";
import { getBindingDevices } from "@/api/manage";
import { useFormatTask } from "@/components/task/use-format-task";
import { useRouter } from "vue-router";
import { RedoOutlined } from "@ant-design/icons-vue";
const router = useRouter();
import { useMyStore } from "@/store";
import { HmsErrorCodes } from "@/utils/hmsErrorCodes";
import { getFloatingWindowData } from "@/api/psdk";
import { hasPayloadPoints } from "./payload-data";

const getHmsErrorDesc = (code: string | number) => {
  if (!code) return "";
  const codeStr = String(code);
  return HmsErrorCodes[codeStr] || "未知错误";
};

const state = useMyStore()
const isChecked = ref(true); // 控制选框状态
const isHistoryChecked = ref(false); // 控制历史设备选框状态

// 任务详情弹窗相关状态
const taskDetailVisible = ref(false);
const selectedTask = ref<Task | null>(null);

const toggleCheck = () => {
  isChecked.value = !isChecked.value;
};

const toggleHistoryCheck = () => {
  isHistoryChecked.value = !isHistoryChecked.value;
};

// 获取工作区ID
const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId)!;

// 计划库统计数据
const planStats = reactive({
  total: 0,
  wait: 0,
  carrying: 0,
  success: 0,
  failed: 0,
  totalMedia: 0,
  successRate: '0%',
  loading: false,
});

// 获取全局计划库统计信息
const fetchPlanStatistics = async () => {
  if (!workspaceId) return;
  planStats.loading = true;
  try {
    const res = await getWaylineJobs(workspaceId, { page: 1, page_size: 1000, total: 0 }, {});
    if (res.code === 0 && res.data?.list) {
      const list = res.data.list as Task[];
      planStats.total = res.data.pagination.total || list.length;
      let waitCount = 0;
      let carryingCount = 0;
      let successCount = 0;
      let failedCount = 0;
      let mediaTotal = 0;

      list.forEach((t) => {
        mediaTotal += t.media_count || 0;
        if (t.status === TaskStatus.Wait) waitCount++;
        else if (t.status === TaskStatus.Carrying) carryingCount++;
        else if (t.status === TaskStatus.Success) successCount++;
        else if (t.status === TaskStatus.Fail || t.status === TaskStatus.CanCel) failedCount++;
      });

      planStats.wait = waitCount;
      planStats.carrying = carryingCount;
      planStats.success = successCount;
      planStats.failed = failedCount;
      planStats.totalMedia = mediaTotal;

      const rate = planStats.total > 0 ? (successCount / planStats.total) * 100 : 0;
      planStats.successRate = `${rate.toFixed(1)}%`;
    }
  } catch (e) {
    console.error('获取计划库统计数据失败', e);
  } finally {
    planStats.loading = false;
  }
};

// 分页配置
const pagination = reactive({
  page: 1,
  page_size: 10,
  total: 0,
});

// 表格数据
const dataSource = reactive({
  data: [] as Task[],
});

// 查询条件
const filterState = reactive<Partial<WaylineBody>>({
  file_id: undefined,
  wayline_type: undefined,
  dock_sn: undefined,
  status: undefined,
  start_time: undefined,
  end_time: undefined,
});

// 日期范围 (ant-design-vue RangePicker 返回 dayjs 对象数组)
const dateRange = ref<any[] | null>(null);

// 航线类型选项
const waylineTypeOptions = [
  { value: WaylineType.noWayLine, label: '手飞任务' },
  { value: WaylineType.NormalWaypointWayline, label: '普通航点航线' },
  { value: WaylineType.AccurateReshootingWayline, label: '精准复拍航线' },
];

// 任务状态选项
const taskStatusOptions = [
  { value: undefined, label: '全部状态' },
  { value: TaskStatus.Wait, label: TaskStatusMap[TaskStatus.Wait] },
  { value: TaskStatus.Carrying, label: TaskStatusMap[TaskStatus.Carrying] },
  { value: TaskStatus.Success, label: TaskStatusMap[TaskStatus.Success] },
  { value: TaskStatus.CanCel, label: TaskStatusMap[TaskStatus.CanCel] },
  { value: TaskStatus.Fail, label: TaskStatusMap[TaskStatus.Fail] },
  { value: TaskStatus.Paused, label: TaskStatusMap[TaskStatus.Paused] },
];

// 航线文件选项
const waylineFileOptions = ref<{ value: string; label: string }[]>([]);

// 获取航线文件列表
const fetchWaylineFiles = async () => {
  try {
    const res = await getWaylineFiles(workspaceId, {
      page: 1,
      page_size: 1000,
      order_by: 'update_time desc',
    });
    if (res.code === 0 && res.data?.list) {
      waylineFileOptions.value = res.data.list.map((item: any) => ({
        value: item.id,
        label: item.name,
      }));
    }
  } catch (error) {
    console.error('获取航线列表失败', error);
  }
};

// 机场选项
const dockOptions = ref<{ value: string; label: string }[]>([]);

// 获取机场设备列表
const fetchDockDevices = async () => {
  try {
    const res = await getBindingDevices(workspaceId, { page: 1, page_size: 1000, total: 0 }, EDeviceTypeName.Dock);
    if (res.code === 0 && res.data?.list) {
      dockOptions.value = res.data.list.map((item: any) => ({
        value: item.device_sn,
        label: item.nickname || item.device_sn,
      }));
    }
  } catch (error) {
    console.error('获取机场列表失败', error);
  }
};

// 使用格式化工具
const { formatTaskType, formatTaskTime, formatTaskStatus, formatLostAction, formatMediaTaskStatus } = useFormatTask();

// 表格列配置
const columns = ref([
  {
    title: "计划|实际时间",
    dataIndex: "duration",
    key: "duration",
    slots: { customRender: "duration" },
    width: 296,
  },
  {
    title: "执行状态",
    dataIndex: "status",
    key: "status",
    slots: { customRender: "status" },
    width: 110,
  },
  {
    title: "类型",
    dataIndex: "type",
    key: "type",
    slots: { customRender: "taskType" },
    width: 100,
  },
  {
    title: "计划名称",
    dataIndex: "job_name",
    key: "job_name",
    width: 140,
  },
  {
    title: "航线名称",
    dataIndex: "file_name",
    key: "file_name",
    width: 140,
    slots: { customRender: "file_name" },
  },
  {
    title: "设备名称",
    dataIndex: "dock_name",
    key: "dock_name",
    width: 120,
  },
  {
    title: "创建人",
    dataIndex: "username",
    key: "username",
    width: 100,
  },
  {
    title: "媒体上传",
    dataIndex: "media_upload",
    key: "media_upload",
    slots: { customRender: "media_upload" },
    width: 120,
  },
  {
    title: "操作",
    dataIndex: "action",
    key: "action",
    slots: { customRender: "action" },
    width: 200,
    fixed: "right",
  },
]);

// 获取选中行的数据
const selectedRows = ref<any[]>([])
const selectedRowKeys = ref<Array<number>>([]);
const rowSelection = {
  onChange: (keys: any[], rows: any[]) => {
    selectedRowKeys.value = keys;
    selectedRows.value = rows;
    console.log("选中的 key：", keys);
    console.log("选中的整行数据：", rows);
  }
}
// 记录每个 jobId 是否有可展示的负载数据；没有（或格式不可解析）时隐藏"负载数据"按钮
const payloadStateMap = reactive<Record<string, 'loading' | 'ok' | 'none'>>({});

function checkPayloadData(taskList: Task[]) {
  if (!Array.isArray(taskList)) return;
  const queue = taskList
    .filter((task) => task.job_id && payloadStateMap[task.job_id] === undefined)
    .map((task) => task.job_id);
  if (queue.length === 0) return;
  queue.forEach((jobId) => {
    payloadStateMap[jobId] = "loading";
  });

  // 限制并发，避免一次性打满浮窗数据接口
  const worker = async () => {
    while (queue.length) {
      const jobId = queue.shift() as string;
      try {
        const points = await getFloatingWindowData("", jobId);
        payloadStateMap[jobId] = hasPayloadPoints(points) ? "ok" : "none";
      } catch (error) {
        console.error("检查负载数据失败:", jobId, error);
        payloadStateMap[jobId] = "none";
      }
    }
  };
  Promise.all(Array.from({ length: Math.min(4, queue.length) }, worker));
}

// 获取计划数据
function getPlans() {
  // 处理日期范围 - 转换为时间戳（毫秒）
  if (dateRange.value && dateRange.value.length === 2) {
    const startDate = dateRange.value[0];
    const endDate = dateRange.value[1];
    // 判断是否为 dayjs 对象或字符串，转换为时间戳
    filterState.start_time = typeof startDate === 'object' && startDate?.valueOf ? startDate.valueOf() : new Date(startDate).getTime();
    filterState.end_time = typeof endDate === 'object' && endDate?.valueOf ? endDate.valueOf() : new Date(endDate).getTime();
  } else {
    filterState.start_time = undefined;
    filterState.end_time = undefined;
  }

  const body = { ...filterState } as WaylineBody;

  getWaylineJobs(workspaceId, {
    page: pagination.page,
    page_size: pagination.page_size,
    total: pagination.total,
  }, body)
    .then((res) => {
      if (res.code !== 0) {
        message.error("获取数据失败");
        return;
      }
      dataSource.data = res.data.list;
      pagination.total = res.data.pagination.total;
      pagination.page = res.data.pagination.page;
      checkPayloadData(res.data.list);
    })
    .catch((err) => {
      console.error("获取计划数据失败:", err);
      message.error("获取数据失败");
    });
}

// 日期范围变化
const handleDateChange = (dates: [string, string] | null) => {
  dateRange.value = dates;
};

// 重置筛选条件
const resetFilters = () => {
  filterState.file_id = undefined;
  filterState.wayline_type = undefined;
  filterState.dock_sn = undefined;
  filterState.status = undefined;
  filterState.start_time = undefined;
  filterState.end_time = undefined;
  dateRange.value = null;
  pagination.page = 1;
  getPlans();
};

// 页面挂载时获取数据
onMounted(() => {
  getPlans();
  fetchPlanStatistics();
  fetchWaylineFiles();
  fetchDockDevices();
});

// 处理分页变化
const handleTableChange = (page: number, pageSize: number) => {
  pagination.page = page;
  pagination.page_size = pageSize;
  getPlans();
};

// 点击任务操作栏按钮，在独立路由窗口展示完整负载数据
async function viewPayloadData(task: Task) {
  if (!task.job_id) {
    message.warning('任务无效，无法查看负载数据')
    return
  }

  const viewer = window.open('about:blank', '_blank')
  if (!viewer) {
    message.warning('浏览器阻止了新窗口，请允许本站弹窗后重试')
    return
  }

  try {
    const points = await getFloatingWindowData('', task.job_id)
    if (!hasPayloadPoints(points)) {
      viewer.close()
      payloadStateMap[task.job_id] = 'none'
      message.warning('暂无负载数据')
      return
    }
    payloadStateMap[task.job_id] = 'ok'

    const transferKey = `payload-viewer-${Date.now()}-${Math.random().toString(36).slice(2)}`
    viewer.sessionStorage.setItem(transferKey, JSON.stringify(points))
    const url = router.resolve({
      name: 'payloadViewer',
      query: {
        transferKey,
        jobId: task.job_id,
        jobName: task.job_name || task.file_name || '',
        payloadName: task.dock_name || ''
      }
    }).href
    viewer.location.replace(url)
  } catch (error) {
    viewer.close()
    console.error('获取负载数据失败:', error)
    message.error('获取负载数据失败')
  }
}


// 查看任务详情
const showTaskDetail = (task: Task) => {
  selectedTask.value = task;
  taskDetailVisible.value = true;
};

// 轨迹回放
const wayLineRecord = (task: Task) => {
  const url = router.resolve({
    name: 'wayLineRecord',
    query: {
      jobId: task.job_id,
    }
  }).href
  window.open(url, '_blank')
}

// 关闭任务详情弹窗
const closeTaskDetail = () => {
  taskDetailVisible.value = false;
  selectedTask.value = null;
};

// 跳转到媒体管理页面并筛选对应文件
const viewMediaFiles = (task: Task) => {
  if (!task.media_count || task.media_count === 0) {
    message.warning("该任务没有媒体文件");
    return;
  }

  // 跳转到数据管理页面，并传递查询参数
  router.push({
    path: "/data-mgmt",
    query: {
      jobId: task.job_id,
      jobName: task.job_name,
    },
  });
};

// 新建计划
const createNewPlan = () => {
  router.push("/command/create-plan");
};
</script>

<template>
  <div class="command-container">
    <aside class="sidebar">
      <div class="sidebar-item">
        <div class="sidebar-header">
          <CalendarOutlined class="sidebar-icon" />
          <span>计划库统计概览</span>
        </div>
        <div class="divider"></div>
        <div class="sidebar-content">
          <!-- 顶部总览卡片 -->
          <div class="stat-summary-group">
            <div class="stat-summary-card">
              <span class="summary-label">计划总数</span>
              <span class="summary-value total-color">{{ planStats.total }}</span>
            </div>
            <div class="stat-summary-card">
              <span class="summary-label">完成率</span>
              <span class="summary-value rate-color">{{ planStats.successRate }}</span>
            </div>
          </div>

          <!-- 任务状态分布 -->
          <div class="stat-block">
            <div class="stat-block-title">任务状态分布</div>
            <div class="stat-grid">
              <div class="stat-grid-item success-bg">
                <span class="item-label">已完成</span>
                <span class="item-value">{{ planStats.success }}</span>
              </div>
              <div class="stat-grid-item carrying-bg">
                <span class="item-label">进行中</span>
                <span class="item-value">{{ planStats.carrying }}</span>
              </div>
              <div class="stat-grid-item wait-bg">
                <span class="item-label">等待中</span>
                <span class="item-value">{{ planStats.wait }}</span>
              </div>
              <div class="stat-grid-item fail-bg">
                <span class="item-label">异常/取消</span>
                <span class="item-value">{{ planStats.failed }}</span>
              </div>
            </div>
          </div>

          <div class="divider"></div>

          <!-- 资源配置统计 -->
          <div class="stat-block">
            <div class="stat-block-title">资源配置与成果</div>
            <div class="resource-list">
              <div class="resource-item">
                <div class="resource-text">
                  <span class="resource-name">可用航线文件</span>
                  <span class="resource-sub">用于创建执行计划</span>
                </div>
                <span class="resource-num">{{ waylineFileOptions.length }} 条</span>
              </div>

              <div class="resource-item">
                <div class="resource-text">
                  <span class="resource-name">关联执飞机场</span>
                  <span class="resource-sub">当前空间绑定的机场</span>
                </div>
                <span class="resource-num">{{ dockOptions.length }} 台</span>
              </div>

              <div class="resource-item">
                <div class="resource-text">
                  <span class="resource-name">累计媒体成果</span>
                  <span class="resource-sub">航线任务上传的照片/视频</span>
                </div>
                <span class="resource-num">{{ planStats.totalMedia }} 个</span>
              </div>
            </div>
          </div>

        </div>
      </div>
    </aside>
    <main class="main-content table-box">
      <div class="toolbar searchBox">
        <div class="toolbar-row">
          <a-button class="create-plan-button" type="primary" @click="createNewPlan">新建计划</a-button>
        </div>
        <div class="toolbar-row">
          <a-range-picker class="date-picker" v-model:value="dateRange" :placeholder="['开始时间', '结束时间']"
            :show-time="{ format: 'HH:mm' }" format="YYYY-MM-DD HH:mm"
            :get-popup-container="(triggerNode: any) => triggerNode.parentNode" @change="handleDateChange" />
          <a-select class="select" style="width: 200px;" v-model:value="filterState.file_id" placeholder="选择航线"
            allow-clear :options="waylineFileOptions" show-search
            :filter-option="(input: string, option: any) => option.label.toLowerCase().includes(input.toLowerCase())" />
          <a-select class="select" v-model:value="filterState.wayline_type" placeholder="航线类型" allow-clear
            :options="waylineTypeOptions" />
          <a-select class="select" style="width: 200px;" v-model:value="filterState.dock_sn" placeholder="选择机场"
            allow-clear :options="dockOptions" show-search
            :filter-option="(input: string, option: any) => option.label.toLowerCase().includes(input.toLowerCase())" />
          <a-select class="select" v-model:value="filterState.status" placeholder="任务状态" allow-clear
            :options="taskStatusOptions" />
          <a-button type="primary" @click="getPlans">查询</a-button>
          <a-button class="reset-button" @click="resetFilters">
            <RedoOutlined />
            重置
          </a-button>
        </div>
      </div>
      <div class="table-container flex-display flex-column table">
        <a-table :data-source="dataSource.data" :columns="columns" :rowSelection="rowSelection" row-key="job_id"
          :scroll="{ x: 'max-content', y: 'calc(100vh - 350px)' }" :pagination="false">
          <!-- 执行时间 -->
          <template #duration="{ record }">
            <div class="flex-row" style="white-space: pre-wrap">
              <div>
                <div>{{ formatTaskTime(record.begin_time) }}</div>
                <div>{{ formatTaskTime(record.end_time) }}</div>
              </div>
              <div class="ml10">
                <div>{{ formatTaskTime(record.execute_time) }}</div>
                <div>{{ formatTaskTime(record.completed_time) }}</div>
              </div>
            </div>
          </template>

          <!-- 状态 -->
          <template #status="{ record }">
            <div>
              <div class="flex-display flex-align-center">
                <span class="circle-icon" :style="{ backgroundColor: formatTaskStatus(record).color }"></span>
                {{ formatTaskStatus(record).text }}
              </div>
              <a-tooltip v-if="record.code && String(record.code) !== '0'" :title="getHmsErrorDesc(record.code)">
                <div style="font-size: 12px; color: #ff4d4f; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100px; margin-top: 4px;">
                  {{ getHmsErrorDesc(record.code) }}
                </div>
              </a-tooltip>
            </div>
          </template>

          <!-- 任务类型 -->
          <template #taskType="{ record }">
            <div>{{ formatTaskType(record) }}</div>
          </template>

          <!-- 航线名称 -->
          <template #file_name="{ record }">
            <div>{{ record.file_name || '手飞任务' }}</div>
          </template>

          <!-- 媒体上传状态 -->
          <template #media_upload="{ record }">
            <div>
              <div class="flex-display flex-align-center">
                <span class="circle-icon" :style="{ backgroundColor: formatMediaTaskStatus(record).color }"></span>
                {{ formatMediaTaskStatus(record).text }}
              </div>
              <div class="pl15">
                {{ formatMediaTaskStatus(record).number }}
              </div>
            </div>
          </template>

          <!-- 操作 -->
          <template #action="{ record }">
            <div class="action-area">
              <a-button type="primary" size="small" @click="showTaskDetail(record)">查看详情</a-button>
              <a-button v-if="payloadStateMap[record.job_id] === 'ok'" type="default" size="small"
                @click="viewPayloadData(record)">负载数据</a-button>
              <a-button v-if="record.media_count && record.media_count > 0" type="default" size="small"
                @click="viewMediaFiles(record)">
                <span style="display: inline-flex; align-items: center; justify-content: center">
                  <FileImageOutlined style="font-size: 16px; vertical-align: middle; margin-right: 4px" />
                  <span>查看媒体</span>
                </span>
              </a-button>
              <a-button type="primary" size="small" @click="wayLineRecord(record)">
                轨迹回放
              </a-button>
            </div>
          </template>
        </a-table>
        <div class="pagination-container">
          <a-pagination v-model:current="pagination.page" v-model:page-size="pagination.page_size"
            :total="pagination.total" :show-total="(total: number) => `共 ${total} 条记录`" show-quick-jumper
            @change="handleTableChange" @showSizeChange="handleTableChange" />
        </div>
      </div>
    </main>

    <!-- 任务详情弹窗 -->
    <a-modal v-model:visible="taskDetailVisible" title="任务详情" width="800px" :footer="null" @cancel="closeTaskDetail">
      <div v-if="selectedTask" class="task-detail-content">
        <a-descriptions title="基本信息" bordered :column="2">
          <a-descriptions-item label="任务ID">{{ selectedTask.job_id }}</a-descriptions-item>
          <a-descriptions-item label="计划名称">{{ selectedTask.job_name }}</a-descriptions-item>
          <a-descriptions-item label="航线名称">{{ selectedTask.file_name || '手飞任务' }}</a-descriptions-item>
          <a-descriptions-item label="任务类型">{{ formatTaskType(selectedTask) }}</a-descriptions-item>
          <a-descriptions-item label="设备名称">{{ selectedTask.dock_name }}</a-descriptions-item>
          <a-descriptions-item label="设备SN">{{ selectedTask.dock_sn }}</a-descriptions-item>
          <a-descriptions-item label="创建人">{{ selectedTask.username }}</a-descriptions-item>
          <a-descriptions-item label="工作区ID">{{ selectedTask.workspace_id }}</a-descriptions-item>
        </a-descriptions>

        <a-descriptions title="执行信息" bordered :column="2" style="margin-top: 20px">
          <a-descriptions-item label="任务状态">
            <span class="flex-display flex-align-center">
              <span class="circle-icon" :style="{ backgroundColor: formatTaskStatus(selectedTask).color }"></span>
              {{ formatTaskStatus(selectedTask).text }}
            </span>
          </a-descriptions-item>
          <a-descriptions-item label="执行进度">{{ selectedTask.progress || 0 }}%</a-descriptions-item>
          <a-descriptions-item label="计划开始时间">{{ formatTaskTime(selectedTask.begin_time) }}</a-descriptions-item>
          <a-descriptions-item label="计划结束时间">{{ formatTaskTime(selectedTask.end_time) }}</a-descriptions-item>
          <a-descriptions-item label="实际执行时间">{{ formatTaskTime(selectedTask.execute_time) }}</a-descriptions-item>
          <a-descriptions-item label="完成时间">{{ formatTaskTime(selectedTask.completed_time) }}</a-descriptions-item>
        </a-descriptions>

        <a-descriptions title="配置信息" bordered :column="2" style="margin-top: 20px">
          <a-descriptions-item label="返航高度">{{ selectedTask.rth_altitude }}米</a-descriptions-item>
          <a-descriptions-item label="最后动作">{{ formatLostAction(selectedTask) }}</a-descriptions-item>
          <a-descriptions-item label="媒体文件数量">{{ selectedTask.media_count || 0 }}</a-descriptions-item>
          <a-descriptions-item label="已上传媒体">{{ selectedTask.uploaded_count || 0 }}</a-descriptions-item>
        </a-descriptions>

        <a-descriptions title="媒体上传状态" bordered :column="1" style="margin-top: 20px">
          <a-descriptions-item label="上传状态">
            <div class="flex-display flex-align-center">
              <span class="circle-icon" :style="{ backgroundColor: formatMediaTaskStatus(selectedTask).color }"></span>
              {{ formatMediaTaskStatus(selectedTask).text }}
              <span class="ml10">{{ formatMediaTaskStatus(selectedTask).number }}</span>
            </div>
          </a-descriptions-item>
        </a-descriptions>

        <div v-if="selectedTask.code && String(selectedTask.code) !== '0'" style="margin-top: 20px">
          <a-alert message="错误信息" :description="`错误代码: ${selectedTask.code} (${getHmsErrorDesc(selectedTask.code)})`" type="error" show-icon />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.command-container {
  display: flex;
  height: 100%;
}

.sidebar {
  background: linear-gradient(178deg, #041428 0%, #002658 100%);
  border-radius: 4px 4px 4px 4px;
  color: #fff;
  padding: 20px;
}

.sidebar-item {
  margin-bottom: 20px;
}

.sidebar-header {
  font-size: 1rem;
  font-weight: bold;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.sidebar-icon {
  font-size: 16px;
  color: #1890ff;
}

.divider {
  height: 3px;
  background-color: #203a6e;
  margin: 15px 0;
}

.stat-summary-group {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;

  .stat-summary-card {
    flex: 1;
    background: rgba(9, 25, 39, 0.4);
    border: 1px solid #214565;
    border-radius: 8px;
    padding: 12px;
    display: flex;
    flex-direction: column;
    align-items: center;

    .summary-label {
      font-size: 12px;
      color: #94a3b8;
      margin-bottom: 4px;
    }

    .summary-value {
      font-size: 20px;
      font-weight: 700;

      &.total-color {
        color: #38bdf8;
      }

      &.rate-color {
        color: #52c41a;
      }
    }
  }
}

.stat-block {
  margin-bottom: 16px;

  .stat-block-title {
    font-size: 13px;
    color: #e2e8f0;
    font-weight: 600;
    margin-bottom: 10px;
  }

  .stat-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;

    .stat-grid-item {
      padding: 10px;
      border-radius: 6px;
      display: flex;
      flex-direction: column;
      border: 1px solid rgba(255, 255, 255, 0.08);

      .item-label {
        font-size: 11px;
        opacity: 0.85;
      }

      .item-value {
        font-size: 16px;
        font-weight: 600;
        margin-top: 2px;
      }

      &.success-bg {
        background: rgba(82, 196, 26, 0.12);
        border-color: rgba(82, 196, 26, 0.3);
        color: #52c41a;
      }

      &.carrying-bg {
        background: rgba(24, 144, 255, 0.12);
        border-color: rgba(24, 144, 255, 0.3);
        color: #1890ff;
      }

      &.wait-bg {
        background: rgba(250, 173, 20, 0.12);
        border-color: rgba(250, 173, 20, 0.3);
        color: #faad14;
      }

      &.fail-bg {
        background: rgba(255, 77, 79, 0.12);
        border-color: rgba(255, 77, 79, 0.3);
        color: #ff4d4f;
      }
    }
  }

  .resource-list {
    display: flex;
    flex-direction: column;
    gap: 10px;

    .resource-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: rgba(9, 25, 39, 0.3);
      border: 1px solid #214565;
      border-radius: 6px;
      padding: 10px 12px;

      .resource-text {
        display: flex;
        flex-direction: column;

        .resource-name {
          font-size: 13px;
          color: #ffffff;
          font-weight: 500;
        }

        .resource-sub {
          font-size: 11px;
          color: #64748b;
          margin-top: 2px;
        }
      }

      .resource-num {
        font-size: 14px;
        color: #00f2fe;
        font-weight: 600;
      }
    }
  }
}

.main-content {
  flex: 1;
  padding: 20px;
  background-color: #0f1e3a;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.toolbar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 10px;

  .toolbar-row {
    display: flex;
    gap: 10px;
    align-items: center;

    &:first-child {
      justify-content: flex-start;
    }

    &:last-child {
      flex-wrap: wrap;
    }
  }

  .create-plan-button {
    background: #3a7af9;
    border-color: #3a7af9;
    color: #fff;

    &:hover {
      background: #5b8bfa;
      border-color: #5b8bfa;
    }
  }

  .date-picker {
    width: 380px !important;
    color: #fff;
  }

  .select {
    width: 120px;
  }

  .search-input {
    width: 240px;
  }

  .reset-button {
    background: #27437a;
    border-color: #27437a;
    color: #fff;

    &:hover {
      background: #3a558f;
      border-color: #3a558f;
    }
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

.table-container {
  flex: 1;
  overflow: scroll;
  display: flex;
  flex-direction: column;

  /* 自定义滚动条样式 */
  &::-webkit-scrollbar {
    width: 2px;
    height: 8px;
  }

  &::-webkit-scrollbar-track {
    background: #1e3461;
    border-radius: 0px;
  }

  &::-webkit-scrollbar-thumb {
    background: #3a558f;
    border-radius: 0px;

    &:hover {
      background: #5b8bfa;
    }
  }

  &::-webkit-scrollbar-corner {
    background: #1e3461;
  }

  /* Firefox 滚动条样式 */
  scrollbar-width: thin;
  scrollbar-color: #3a558f #1e3461;
}

/* 添加自定义样式 */
.flex-row {
  display: flex;
  flex-direction: row;
}

.ml10 {
  margin-left: 10px;
}

.flex-display {
  display: flex;
}

.flex-align-center {
  align-items: center;
}

.circle-icon {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 8px;
  display: inline-block;
}

.pl15 {
  padding-left: 15px;
}

.action-area {
  display: flex;
  gap: 8px;

  .ant-btn {
    &.ant-btn-default {
      background: #406dc7;
      border-color: #2b4b8a;
      color: #fff;

      &:hover {
        background: #577dce;
        border-color: #3a558f;
        color: #fff;
      }
    }
  }
}

/* 详情弹窗标题白色 */
:deep(.ant-descriptions-title) {
  color: #fff !important;
}

:deep(.ant-btn > .anticon) {
  position: relative;
  top: -4px;
}

:deep(.ant-table-row-selected) {
  color: black !important;
}
</style>
