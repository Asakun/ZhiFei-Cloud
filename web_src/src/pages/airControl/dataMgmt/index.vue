<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { getMediaFiles, getMediaFileUrl, downloadMediaFile, getStorageStat, getBucketStat, StorageStatResponse, BucketStatResponse } from "@/api/media";
import { ELocalStorageKey } from "@/types/enums";
import { message } from "ant-design-vue";
import { useRoute, useRouter } from "vue-router";
import { useFormatTask } from "@/components/task/use-format-task";
import { FolderFilled, FileImageFilled, VideoCameraFilled, FileZipFilled, RocketFilled } from "@ant-design/icons-vue";
import exifr from "exifr";
import JSZip from "jszip";
import ImageLocationMap from "@/components/ImageLocationMap.vue";
const route = useRoute();
const router = useRouter();

const mapLoadedMediaChecked = ref(false); // 地图上加载的媒体文件
const sharedMediaChecked = ref(false); // 已分享的媒体文件
const loading = ref(false);
const downloadingFiles = ref(new Set<string>()); // 正在下载的文件ID集合

// 文件夹下载进度状态: key = folder record.key，value = { current, total, visible }
const folderDownloadProgress = ref<Record<string, { current: number; total: number; visible: boolean }>>({});

// 后端统计数据响应状态
const storageStatData = ref<StorageStatResponse | null>(null);
const bucketStatData = ref<BucketStatResponse | null>(null);

// 字节大小换算格式化
const formatSize = (bytes: number = 0) => {
  if (!bytes || bytes <= 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

// 获取后端真实统计接口数据
const fetchBackendStats = async () => {
  const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId);
  if (!workspaceId) return;

  try {
    const [storageRes, bucketRes] = await Promise.all([
      getStorageStat(workspaceId),
      getBucketStat(workspaceId)
    ]);

    if (storageRes && storageRes.code === 0 && storageRes.data) {
      storageStatData.value = storageRes.data;
      console.log('获取到的后端 Storage 统计数据', storageRes.data);
    }

    if (bucketRes && bucketRes.code === 0 && bucketRes.data) {
      bucketStatData.value = bucketRes.data;
      console.log('获取到的后端 Bucket 统计数据', bucketRes.data);
    }
  } catch (e) {
    console.error('获取后端统计数据失败:', e);
  }
};

fetchBackendStats();

// 媒体文件分类统计 (照片、视频、其他/工程数据)
const categorizedStats = computed(() => {
  if (bucketStatData.value) {
    const b = bucketStatData.value;
    return {
      photos: { count: b.image_count || 0, size: formatSize(b.image_size || 0) },
      videos: { count: b.video_count || 0, size: formatSize(b.video_size || 0) },
      others: { count: b.other_count || 0, size: formatSize(b.other_size || 0) }
    };
  }
  return {
    photos: { count: 0, size: '0 B' },
    videos: { count: 0, size: '0 B' },
    others: { count: 0, size: '0 B' }
  };
});

// 整体磁盘占用统计
const diskUsage = computed(() => {
  if (storageStatData.value) {
    const s = storageStatData.value;
    const total = s.total_bytes || 0;
    const used = s.used_bytes || 0;
    const free = s.free_bytes || 0;
    const percentNum = total > 0 ? parseFloat(((used / total) * 100).toFixed(2)) : 0;

    return {
      usedSize: formatSize(used),
      totalSize: formatSize(total),
      freeSize: formatSize(free),
      usagePercentStr: `${percentNum}%`,
      usagePercentNum: percentNum
    };
  }
  return {
    usedSize: '--',
    totalSize: '--',
    freeSize: '--',
    usagePercentStr: '0%',
    usagePercentNum: 0
  };
});
// 任务筛选相关状态
const selectedJobId = ref<string>(""); // 选中的任务ID
const selectedJobName = ref<string>(""); // 选中的任务名称
const isFromTaskPage = ref(false); // 是否从任务页面跳转过来

// 使用格式化工具
const { formatTaskType, formatTaskTime, formatTaskStatus, formatLostAction, formatMediaTaskStatus } = useFormatTask();

// 定义媒体文件类型
interface MediaFile {
  file_id: string;
  file_name: string;
  file_path: string;
  object_key: string;
  is_original: boolean;
  drone: string;
  payload: string;
  tinny_fingerprint: string;
  fingerprint: string;
  create_time: string;
  job_id: string;
}

// 表格显示的数据类型（支持树形结构）
interface TableData {
  key: string;
  fileName: string;
  payload: string;
  drone: string; // 新增字段
  createTime: string;
  file_id: string;
  isFolder?: boolean;  // 是否是文件夹节点
  children?: TableData[];  // 子节点（文件夹下的文件）
  fileCount?: number;  // 文件夹下的文件数量
  jobId?: string; // 任务ID
}

const columns = ref([
  {
    title: "文件夹/文件",
    dataIndex: "fileName",
    key: "fileName",
    ellipsis: true,
    width: 400,
  },
  {
    title: "缩略图",
    dataIndex: "thumbnail",
    key: "thumbnail",
    width: 90,
  },
  {
    title: "无人机",
    dataIndex: "drone",
    key: "drone",
    width: 150,
  },
  {
    title: "拍摄负载",
    dataIndex: "payload",
    key: "payload",
    width: 120,
  },
  {
    title: "创建时间",
    dataIndex: "createTime",
    key: "createTime",
    width: 180,
  },
  {
    title: "操作",
    dataIndex: "action",
    key: "action",
    width: 200,
  },
]);

// 获取当前工作区ID
function getWorkspaceId(): string {
  const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId);
  if (!workspaceId) {
    console.error("Workspace ID not found in localStorage");
    message.error("未找到工作区ID");
    return "";
  }
  return workspaceId;
}

// 后端请求分页（一次拉取大批量文件，由前端按文件夹分页展示）
const pagination = ref({
  page: 1,
  page_size: 500,
  total: 0,
});

// 前端文件夹分页状态
const allFolders = ref<TableData[]>([]); // 后端返回并分组后的全量文件夹列表
const folderPage = ref(1);
const folderPageSize = ref(10); // 每页展示的文件夹数量

// 后端查询条件
const filterState = ref({
  job_id: '' as string,
  start_time: undefined as number | undefined,
  end_time: undefined as number | undefined,
  drone: '' as string,
  file_name: '' as string,
  // 默认不过滤文件类型（查询全部）
  is_original: undefined as number | undefined,
});

// 媒体文件数据
const dataSource = ref<TableData[]>([]);

// 展开的行 keys
const expandedRowKeys = ref<string[]>([]);

// 空间使用统计
const spaceUsage = ref({
  photos: { count: 0 },
  videos: { count: 0 },
  compressed: { count: 0 },
});

// 根据 job_id 生成文件夹名称
function extractFolderName(filePath: string, jobId: string): string {
  // 直接使用 jobId 作为文件夹名称
  return jobId || '未知任务';
}

import { getBindingDevices } from "@/api/manage";
import { getWaylineJobs, Task } from "@/api/wayline";
import { EDeviceTypeName } from "@/types";
import { Device } from "@/types/device";

// 设备SN到名称的映射
const deviceMap = ref<Map<string, string>>(new Map());
// 任务ID到计划名称的映射
const planMap = ref<Map<string, string>>(new Map());
// 任务详细数据映射
const taskMap = ref<Map<string, Task>>(new Map());

// 飞行数据详情弹窗
const flightDetailVisible = ref(false);
const selectedTask = ref<Task | null>(null);

const showFlightDetail = (jobId: string) => {
  const task = taskMap.value.get(jobId);
  if (task) {
    selectedTask.value = task;
    flightDetailVisible.value = true;
  } else {
    message.warning("未找到该任务的详细数据");
  }
};

// 获取所有绑定的设备信息并建立映射
async function fetchDeviceMap() {
  const workspaceId = getWorkspaceId();
  if (!workspaceId) return;

  try {
    // 获取飞行器列表 (Domain 0)
    const aircraftRes = await getBindingDevices(workspaceId, { page: 1, page_size: 100, total: 0 }, EDeviceTypeName.Aircraft);
    if (aircraftRes.code === 0 && aircraftRes.data) {
      aircraftRes.data.list.forEach((device: Device) => {
        deviceMap.value.set(device.device_sn, device.nickname || device.device_name || device.device_sn);
      });
    }
  } catch (error) {
    console.error("Failed to fetch device map:", error);
  }
}

// 获取计划列表并建立映射
async function fetchPlanMap() {
  const workspaceId = getWorkspaceId();
  if (!workspaceId) return;
  try {
    // 尝试获取更多计划，增加 page_size
    const res = await getWaylineJobs(workspaceId, { page: 1, page_size: 500, total: 0 });
    if (res.code === 0 && res.data) {
      console.log(`Fetched ${res.data.list.length} plans.`);
      res.data.list.forEach((task: Task) => {
        planMap.value.set(task.job_id, task.job_name);
        taskMap.value.set(task.job_id, task);
      });
      console.log('Plan map updated. Size:', planMap.value.size);
    }
  } catch (error) {
    console.error("Failed to fetch plan map:", error);
  }
}


// 内存数据缓存（规避无搜索参数改变时的重复全量接口轮询）
const rawFilesCache = ref<MediaFile[] | null>(null);
const cachedWorkspaceId = ref<string>('');
const cachedTimeRangeKey = ref<string>('');

// 加载媒体文件数据（结合后端筛选 + 并发分批拉取 + 内存缓存）
const loadMediaFiles = async (forceRefresh = false) => {
  try {
    loading.value = true;
    const workspaceId = getWorkspaceId();
    if (!workspaceId) {
      return;
    }

    // 确保获取设备映射和计划映射
    if (deviceMap.value.size === 0) {
      await fetchDeviceMap();
    }
    if (planMap.value.size === 0) {
      await fetchPlanMap();
    }

    // 处理日期范围 - 转换为时间戳
    if (dateRange.value && dateRange.value.length === 2) {
      const startDate = dateRange.value[0];
      const endDate = dateRange.value[1];
      filterState.value.start_time = typeof startDate === 'object' && startDate?.valueOf ? startDate.valueOf() : new Date(startDate).getTime();
      filterState.value.end_time = typeof endDate === 'object' && endDate?.valueOf ? endDate.valueOf() : new Date(endDate).getTime();
    } else {
      filterState.value.start_time = undefined;
      filterState.value.end_time = undefined;
    }

    // 同步服务端筛选条件（把空字符传置为 undefined，防止后端误触发全量慢速模糊匹配）
    filterState.value.job_id = selectedJobId.value || undefined;
    filterState.value.drone = selectedDeviceSn.value || undefined;
    filterState.value.file_name = searchKeyword.value || undefined;

    const timeKey = `${filterState.value.start_time || ''}_${filterState.value.end_time || ''}`;
    let allFiles: MediaFile[] = [];

    // 若未发生后端过滤参数变化、非强刷且存在全量/已有内存缓存，直接复用缓存，实现 0ms 秒响应
    if (
      !forceRefresh &&
      rawFilesCache.value &&
      cachedWorkspaceId.value === workspaceId &&
      cachedTimeRangeKey.value === timeKey &&
      !selectedJobId.value &&
      !selectedDeviceSn.value &&
      !searchKeyword.value
    ) {
      allFiles = rawFilesCache.value;
    } else {
      // ---- 优化：Promise.all 并发分批拉取 ----
      const PAGE_SIZE = 500;

      // 发起第一页请求，获取数据总量与首页数据
      const firstResp = await getMediaFiles(
        workspaceId,
        { page: 1, page_size: PAGE_SIZE, total: 0 },
        filterState.value as any
      );

      if (firstResp.code !== 0 || !firstResp.data) {
        message.error('加载媒体文件失败');
        return;
      }

      const totalCount = firstResp.data.pagination.total || 0;
      allFiles.push(...(firstResp.data.list || []));

      const totalPages = Math.ceil(totalCount / PAGE_SIZE);

      // 若数据超过 1 页，开启 6 并发 batch 分批并行拉取，替代原先慢速的单线程串行 while 循环
      if (totalPages > 1) {
        const remainingPages: number[] = [];
        for (let p = 2; p <= totalPages; p++) {
          remainingPages.push(p);
        }

        const BATCH_CONCURRENCY = 6;
        for (let i = 0; i < remainingPages.length; i += BATCH_CONCURRENCY) {
          const batchPages = remainingPages.slice(i, i + BATCH_CONCURRENCY);
          const batchPromises = batchPages.map((pageNum) =>
            getMediaFiles(
              workspaceId,
              { page: pageNum, page_size: PAGE_SIZE, total: 0 },
              filterState.value as any
            )
          );
          const batchResults = await Promise.all(batchPromises);
          for (const res of batchResults) {
            if (res && res.code === 0 && res.data?.list) {
              allFiles.push(...res.data.list);
            }
          }
        }
      }

      // 未带极精准搜索条件时保存或更新全量内存缓存
      if (!selectedJobId.value && !selectedDeviceSn.value && !searchKeyword.value) {
        rawFilesCache.value = allFiles;
        cachedWorkspaceId.value = workspaceId;
        cachedTimeRangeKey.value = timeKey;
      }
    }

    let filteredList = allFiles;

    // 客户端多维度精细二次快筛
    if (selectedJobId.value) {
      filteredList = filteredList.filter((file: MediaFile) => file.job_id === selectedJobId.value);
    }
    if (searchKeyword.value) {
      filteredList = filteredList.filter((file: MediaFile) => file.file_name.toLowerCase().includes(searchKeyword.value.toLowerCase()));
    }
    if (selectedPayload.value) {
      filteredList = filteredList.filter((file: MediaFile) => file.payload === selectedPayload.value);
    }
    if (selectedDeviceSn.value) {
      filteredList = filteredList.filter((file: MediaFile) => file.drone === selectedDeviceSn.value);
    }

    // 按 job_id 分组，形成文件夹结构
    const folderMap = new Map<string, { files: MediaFile[], folderName: string, createTime: string }>();

    filteredList.forEach((file: MediaFile) => {
      const jobId = file.job_id || 'unknown';
      if (!folderMap.has(jobId)) {
        const planName = planMap.value.get(jobId);
        const folderName = planName || jobId || '未知任务';
        folderMap.set(jobId, {
          files: [],
          folderName,
          createTime: file.create_time
        });
      }
      folderMap.get(jobId)!.files.push(file);
      const folder = folderMap.get(jobId)!;
      if (file.create_time > folder.createTime) {
        folder.createTime = file.create_time;
      }
    });

    // 转换为树形数据格式
    const treeData: TableData[] = [];
    folderMap.forEach((folder, jobId) => {
      const droneNames = new Set(folder.files.map(f => {
        const sn = f.drone;
        return deviceMap.value.get(sn) || sn || '--';
      }).filter(name => name !== '--'));

      let folderDrone = '--';
      if (droneNames.size === 1) {
        folderDrone = Array.from(droneNames)[0];
      } else if (droneNames.size > 1) {
        folderDrone = '多种设备';
      }

      const folderNode: TableData = {
        key: `folder-${jobId}`,
        fileName: folder.folderName,
        payload: `${folder.files.length} 个文件`,
        drone: folderDrone,
        createTime: folder.createTime,
        file_id: '',
        isFolder: true,
        fileCount: folder.files.length,
        jobId: jobId,
        children: folder.files.map((file: MediaFile) => {
          const droneName = deviceMap.value.get(file.drone) || file.drone || "--";
          return {
            key: file.file_id,
            fileName: file.file_name,
            payload: file.payload || "--",
            drone: droneName,
            createTime: file.create_time,
            file_id: file.file_id,
            isFolder: false,
          };
        }),
      };
      treeData.push(folderNode);
    });

    // 按最新时间降序排列
    treeData.sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime());

    // 保存全量文件夹列表，并做前端分页
    allFolders.value = treeData;
    folderPage.value = 1;
    updatePagedFolders();
    calculateSpaceUsage(filteredList);

  } catch (error) {
    console.error("加载媒体文件失败:", error);
    message.error("加载媒体文件时发生错误");
  } finally {
    loading.value = false;
  }
};

// 计算空间使用情况
const calculateSpaceUsage = (files: MediaFile[]) => {
  let photoCount = 0,
    videoCount = 0,
    compressedCount = 0;

  files.forEach((file) => {
    const fileName = file.file_name.toLowerCase();
    if (fileName.includes(".jpg") || fileName.includes(".jpeg") || fileName.includes(".png")) {
      photoCount++;
    } else if (fileName.includes(".mp4") || fileName.includes(".mov") || fileName.includes(".avi")) {
      videoCount++;
    } else if (fileName.includes(".zip") || fileName.includes(".rar") || fileName.includes(".7z")) {
      compressedCount++;
    }
  });

  spaceUsage.value = {
    photos: { count: photoCount },
    videos: { count: videoCount },
    compressed: { count: compressedCount },
  };
};

// 组件挂载时加载数据
onMounted(() => {
  // 检查是否从任务页面跳转过来
  const jobId = route.query.jobId as string;
  const jobName = route.query.jobName as string;

  if (jobId) {
    selectedJobId.value = jobId;
    selectedJobName.value = jobName || "";
    isFromTaskPage.value = true;
  }

  loadMediaFiles();
});

// 切割文件夹数据以渲染当前页
const updatePagedFolders = () => {
  const start = (folderPage.value - 1) * folderPageSize.value;
  const end = start + folderPageSize.value;
  dataSource.value = allFolders.value.slice(start, end);
};

// 文件夹分页处理函数
const handleFolderPageChange = (page: number, pageSize?: number) => {
  folderPage.value = page;
  if (pageSize && pageSize !== folderPageSize.value) {
    folderPageSize.value = pageSize;
    folderPage.value = 1;
  }
  updatePagedFolders();
  // 清除展开状态，避免换页后展开状态残留
  expandedRowKeys.value = [];
};

// 搜索相关
const searchKeyword = ref("");
const selectedDeviceSn = ref<string | undefined>(undefined);
const deviceOptions = computed(() => {
  const options: { value: string | undefined, label: string }[] = [{ value: undefined, label: '所有设备' }];
  deviceMap.value.forEach((name, sn) => {
    options.push({ value: sn, label: name });
  });
  return options;
});
const selectedPayload = ref("");
const selectedTag = ref("");
// 日期范围 (ant-design-vue RangePicker 返回 dayjs 对象数组)
const dateRange = ref<any[] | []>([]);

// 重置筛选条件
const resetFilters = () => {
  rawFilesCache.value = null; // 清空缓存
  searchKeyword.value = "";
  selectedDeviceSn.value = undefined;
  selectedPayload.value = "";
  selectedTag.value = "";
  dateRange.value = [];
  mapLoadedMediaChecked.value = false;
  sharedMediaChecked.value = false;
  selectedJobId.value = "";
  selectedJobName.value = "";
  isFromTaskPage.value = false;
  pagination.value.page = 1;

  // 重置后端查询条件
  filterState.value = {
    job_id: undefined,
    start_time: undefined,
    end_time: undefined,
    drone: undefined,
    file_name: undefined,
    // 默认不过滤文件类型（查询全部）
    is_original: undefined,
  };

  // 清除路由查询参数
  router.replace({ path: "/data-mgmt" });

  loadMediaFiles(true);
};

// 搜索功能
const handleSearch = () => {
  pagination.value.page = 1;
  loadMediaFiles(true);
};

// 下载文件
const handleDownload = async (fileId: string, fileName: string) => {
  if (downloadingFiles.value.has(fileId)) return;
  try {
    downloadingFiles.value.add(fileId);
    const workspaceId = getWorkspaceId();
    if (!workspaceId) return;
    const url = await getMediaFileUrl(workspaceId, fileId);
    if (!url) throw new Error("获取下载链接失败");
    const res = await fetch(url);
    if (!res.ok) throw new Error("网络错误");
    const blob = await res.blob();
    if (blob.type.includes("json")) {
      const text = await blob.text();
      try {
        message.error(JSON.parse(text).message || "下载失败");
      } catch {
        message.error("下载失败");
      }
      return;
    }
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(link.href);
    message.success(`文件 "${fileName}" 开始下载`);
  } catch (error) {
    console.error(error);
    message.error("下载失败");
  } finally {
    downloadingFiles.value.delete(fileId);
  }
};


// 下载文件夹（将文件夹下所有文件批量获取后用JSZip打包下载）
const handleFolderDownload = async (record: TableData) => {
  const folderKey = record.key;

  // 防止重复触发
  if (folderDownloadProgress.value[folderKey]?.visible) return;

  const children = record.children;
  if (!children || children.length === 0) {
    message.warning("该文件夹下没有文件");
    return;
  }

  const workspaceId = getWorkspaceId();
  if (!workspaceId) return;

  // 初始化进度
  folderDownloadProgress.value[folderKey] = {
    current: 0,
    total: children.length,
    visible: true,
  };

  const zip = new JSZip();
  const folderName = record.fileName || "media";
  const zipFolder = zip.folder(folderName)!;
  let successCount = 0;
  let failCount = 0;

  message.loading({ content: `正在准备打包 ${children.length} 个文件...`, key: folderKey, duration: 0 });

  // 分批并发请求，每批 5 个，避免压垮服务器
  const BATCH_SIZE = 5;
  for (let i = 0; i < children.length; i += BATCH_SIZE) {
    const batch = children.slice(i, i + BATCH_SIZE);
    await Promise.all(
      batch.map(async (child) => {
        try {
          const url = await getMediaFileUrl(workspaceId, child.file_id);
          if (!url) throw new Error("获取预签名URL失败");
          const res = await fetch(url);
          if (!res.ok) throw new Error(`HTTP ${res.status}`);
          const blob = await res.blob();
          zipFolder.file(child.fileName, blob);
          successCount++;
        } catch (e) {
          console.warn(`文件 ${child.fileName} 下载失败:`, e);
          failCount++;
        } finally {
          folderDownloadProgress.value[folderKey].current++;
        }
      })
    );
  }

  if (successCount === 0) {
    message.error({ content: "所有文件下载失败，请检查网络或权限", key: folderKey, duration: 3 });
    folderDownloadProgress.value[folderKey].visible = false;
    return;
  }

  message.loading({ content: `正在压缩 ${successCount} 个文件...`, key: folderKey, duration: 0 });

  try {
    const zipBlob = await zip.generateAsync({
      type: "blob",
      compression: "DEFLATE",
      compressionOptions: { level: 6 },
    });

    const link = document.createElement("a");
    link.href = URL.createObjectURL(zipBlob);
    link.download = `${folderName}.zip`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(link.href);

    const msg = failCount > 0
      ? `下载完成（${successCount} 成功，${failCount} 失败）`
      : `"${folderName}" 已打包下载（共 ${successCount} 个文件）`;
    message.success({ content: msg, key: folderKey, duration: 4 });
  } catch (e) {
    console.error("ZIP 生成失败:", e);
    message.error({ content: "打包文件失败", key: folderKey, duration: 3 });
  } finally {
    folderDownloadProgress.value[folderKey].visible = false;
  }
};

// 判断文件是否可预览（仅图片和视频）
const isPreviewable = (fileName: string) => {
  if (!fileName) return false;
  const name = fileName.toLowerCase();
  return /\.(jpg|jpeg|png|gif|bmp|webp|mp4|mov|avi|mkv|webm)$/i.test(name);
};

// 预览弹窗相关
const previewVisible = ref(false);
const previewUrl = ref("");
const previewType = ref("");
const previewName = ref("");
const imageMetadata = ref<Record<string, any> | null>(null);
const metadataLoading = ref(false);

const imageLatitude = computed(() => {
  if (!imageMetadata.value) return null;
  const lat = imageMetadata.value.latitude ?? imageMetadata.value.GPSLatitude;
  if (lat === undefined || lat === null) return null;
  const num = typeof lat === "number" ? lat : parseFloat(lat);
  return isNaN(num) ? null : num;
});

const imageLongitude = computed(() => {
  if (!imageMetadata.value) return null;
  const lng = imageMetadata.value.longitude ?? imageMetadata.value.GPSLongitude;
  if (lng === undefined || lng === null) return null;
  const num = typeof lng === "number" ? lng : parseFloat(lng);
  return isNaN(num) ? null : num;
});

let mp4ParseAbortController: AbortController | null = null;

const closePreview = () => {
  previewVisible.value = false;
  previewUrl.value = "";
  previewType.value = "";
  previewName.value = "";
  imageMetadata.value = null;
  videoTelemetry.value = null;
  videoFlightPath.value = [];
  videoCuesList.value = [];
  lastCueIndex = -1;
  
  if (mp4ParseAbortController) {
    mp4ParseAbortController.abort();
    mp4ParseAbortController = null;
  }
};

// 视频姿态数据类型 (包含 VLC / 大疆字幕数据)
interface VideoTelemetryData {
  latitude: number | null;
  longitude: number | null;
  heading: number | null;
  altitude: number | null;
  relAlt: number | null;
  absAlt: number | null;
  speed: number | null;
  pitch?: number | null;
  roll?: number | null;
  iso?: number | null;
  shutter?: string | null;
}

const videoRef = ref<HTMLVideoElement | null>(null);
const videoTelemetry = ref<VideoTelemetryData | null>(null);
const videoFlightPath = ref<Array<[number, number]>>([]);
const videoCuesList = ref<Array<{ time: number; data: VideoTelemetryData }>>([]);

// 从视频字幕/VLC/MP4文本段落解析无人机姿态与相机参数 (完美匹配截图中的 gb_yaw / latitude / longitude / rel_alt)
function parseDroneTelemetry(text: string): VideoTelemetryData {
  const result: VideoTelemetryData = {
    latitude: null,
    longitude: null,
    heading: null,
    altitude: null,
    relAlt: null,
    absAlt: null,
    speed: null,
    pitch: null,
    roll: null,
    iso: null,
    shutter: null,
  };

  if (!text) return result;

  // 1. [latitude: 29.304062]
  const latMatch = text.match(/\[?latitude[=:\s]+([+-]?\d+\.\d+)/i) ||
                   text.match(/GPS\s*\(\s*([+-]?\d+\.\d+)\s*,\s*([+-]?\d+\.\d+)/i);
  if (latMatch) {
    const val = parseFloat(latMatch[1] || latMatch[2]);
    if (!isNaN(val)) result.latitude = val;
  }

  // 2. [longitude: 105.479180]
  const lngMatch = text.match(/\[?longitude[=:\s]+([+-]?\d+\.\d+)/i);
  if (lngMatch) {
    const val = parseFloat(lngMatch[1]);
    if (!isNaN(val)) result.longitude = val;
  }

  // 3. 匹配通用双浮点坐标对: 29.304062, 105.479180
  if (result.latitude === null || result.longitude === null) {
    const pairMatch = text.match(/([+-]?\d{1,2}\.\d{4,8})[,\s]+([+-]?\d{1,3}\.\d{4,8})/);
    if (pairMatch) {
      const v1 = parseFloat(pairMatch[1]);
      const v2 = parseFloat(pairMatch[2]);
      if (!isNaN(v1) && !isNaN(v2)) {
        if (Math.abs(v1) <= 90 && Math.abs(v2) <= 180) {
          result.latitude = v1;
          result.longitude = v2;
        }
      }
    }
  }

  // 4. [gb_yaw: -136.0] 或 [drone_heading: 145.2] 或 [heading: 145.2]
  const yawMatch = text.match(/\[?gb_yaw[=:\s]+([+-]?\d+\.?\d*)/i) ||
                   text.match(/\[?(?:drone_)?heading[=:\s]+([+-]?\d+\.?\d*)/i) ||
                   text.match(/H:\s*([+-]?\d+\.?\d*)deg/i);
  if (yawMatch) {
    let val = parseFloat(yawMatch[1]);
    if (!isNaN(val)) {
      if (val < 0) val = (val % 360 + 360) % 360;
      result.heading = val;
    }
  }

  // 5. [gb_pitch: -29.5]
  const pitchMatch = text.match(/\[?gb_pitch[=:\s]+([+-]?\d+\.?\d*)/i);
  if (pitchMatch) {
    const val = parseFloat(pitchMatch[1]);
    if (!isNaN(val)) result.pitch = val;
  }

  // 6. [gb_roll: 0.0]
  const rollMatch = text.match(/\[?gb_roll[=:\s]+([+-]?\d+\.?\d*)/i);
  if (rollMatch) {
    const val = parseFloat(rollMatch[1]);
    if (!isNaN(val)) result.roll = val;
  }

  // 7. [rel_alt: 49.827]
  const relAltMatch = text.match(/rel_alt[=:\s]+([+-]?\d+\.?\d*)/i) ||
                      text.match(/BAROMETER:\s*([+-]?\d+\.?\d*)m/i);
  if (relAltMatch) {
    const val = parseFloat(relAltMatch[1]);
    if (!isNaN(val)) result.relAlt = val;
  }

  // 8. [abs_alt: 317.022]
  const absAltMatch = text.match(/abs_alt[=:\s]+([+-]?\d+\.?\d*)/i);
  if (absAltMatch) {
    const val = parseFloat(absAltMatch[1]);
    if (!isNaN(val)) result.absAlt = val;
  }

  // 9. [iso: 180]
  const isoMatch = text.match(/\[?iso[=:\s]+(\d+)/i);
  if (isoMatch) {
    const val = parseInt(isoMatch[1]);
    if (!isNaN(val)) result.iso = val;
  }

  // 10. [shutter: 1/2000.0]
  const shutterMatch = text.match(/\[?shutter[=:\s]+([^\s\]]+)/i);
  if (shutterMatch) {
    result.shutter = shutterMatch[1];
  }

  return result;
}

// 视频加载元数据后解析字幕轨道
const handleVideoLoadedMetadata = () => {
  const videoEl = videoRef.value;
  if (!videoEl) return;

  videoTelemetry.value = null;
  videoFlightPath.value = [];
  videoCuesList.value = [];

  const tracks = videoEl.textTracks;
  if (!tracks || tracks.length === 0) return;

  for (let i = 0; i < tracks.length; i++) {
    const track = tracks[i];
    track.mode = "hidden";
    track.oncuechange = () => {
      updateVideoTelemetryFromTrack(track);
    };
  }

  setTimeout(() => {
    extractAllVideoFlightPath(tracks);
  }, 350);
};

// 从视频所有 cues 提取完整航迹线
function extractAllVideoFlightPath(tracks: TextTrackList) {
  const path: Array<[number, number]> = [];
  const cuesList: Array<{ time: number; data: VideoTelemetryData }> = [];

  for (let i = 0; i < tracks.length; i++) {
    const track = tracks[i];
    const cues = track.cues;
    if (!cues) continue;

    for (let j = 0; j < cues.length; j++) {
      const cue = cues[j] as VTTCue;
      if (!cue.text) continue;
      const data = parseDroneTelemetry(cue.text);
      if (data.latitude !== null && data.longitude !== null) {
        path.push([data.latitude, data.longitude]);
        cuesList.push({ time: cue.startTime, data });
      }
    }
  }

  videoFlightPath.value = path;
  videoCuesList.value = cuesList;

  if (cuesList.length > 0 && !videoTelemetry.value) {
    videoTelemetry.value = cuesList[0].data;
  }
}

let lastCueIndex = -1;

// 视频播放 O(log N) 二分查找与防抖更新姿态，避免 timeupdate 高频触发卡顿
const handleVideoTimeUpdate = () => {
  const videoEl = videoRef.value;
  if (!videoEl) return;

  const currentTime = videoEl.currentTime;
  const tracks = videoEl.textTracks;
  let updated = false;

  if (tracks && tracks.length > 0) {
    for (let i = 0; i < tracks.length; i++) {
      const track = tracks[i];
      if (track.activeCues && track.activeCues.length > 0) {
        for (let j = 0; j < track.activeCues.length; j++) {
          const cue = track.activeCues[j] as VTTCue;
          if (cue.text) {
            const data = parseDroneTelemetry(cue.text);
            if (data.latitude !== null && data.longitude !== null) {
              videoTelemetry.value = data;
              updated = true;
              break;
            }
          }
        }
      }
      if (updated) break;
    }
  }

  // 使用 O(log N) 二分查找替代之前的数组遍历，防抖避免重复触发 Vue 响应用
  if (!updated && videoCuesList.value.length > 0) {
    const list = videoCuesList.value;
    let low = 0;
    let high = list.length - 1;
    let bestIdx = 0;

    while (low <= high) {
      const mid = (low + high) >> 1;
      if (list[mid].time <= currentTime) {
        bestIdx = mid;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }

    if (bestIdx !== lastCueIndex) {
      lastCueIndex = bestIdx;
      videoTelemetry.value = list[bestIdx].data;
    }
  }
};

function updateVideoTelemetryFromTrack(track: TextTrack) {
  if (track.activeCues && track.activeCues.length > 0) {
    for (let j = 0; j < track.activeCues.length; j++) {
      const cue = track.activeCues[j] as VTTCue;
      if (cue.text) {
        const data = parseDroneTelemetry(cue.text);
        if (data.latitude !== null && data.longitude !== null) {
          videoTelemetry.value = data;
          break;
        }
      }
    }
  }
}

// 从 SRT 字幕文本解析无人机姿态全量航迹
function parseSrtText(srtContent: string) {
  if (!srtContent) return;

  const path: Array<[number, number]> = [];
  const cuesList: Array<{ time: number; data: VideoTelemetryData }> = [];

  const blocks = srtContent.split(/\r?\n\r?\n/);
  for (const block of blocks) {
    if (!block.trim()) continue;
    const lines = block.trim().split(/\r?\n/);
    if (lines.length < 2) continue;

    let timeLine = lines[0].includes("-->") ? lines[0] : lines[1];
    let textStartIndex = lines[0].includes("-->") ? 1 : 2;

    let startTimeSec = 0;
    if (timeLine && timeLine.includes("-->")) {
      const matchTime = timeLine.match(/(\d{2}):(\d{2}):(\d{2})[,.](\d{3})/);
      if (matchTime) {
        const h = parseInt(matchTime[1]);
        const m = parseInt(matchTime[2]);
        const s = parseInt(matchTime[3]);
        const ms = parseInt(matchTime[4]);
        startTimeSec = h * 3600 + m * 60 + s + ms / 1000;
      }
    }

    const textContent = lines.slice(textStartIndex).join("\n");
    const data = parseDroneTelemetry(textContent);

    if (data.latitude !== null && data.longitude !== null) {
      path.push([data.latitude, data.longitude]);
      cuesList.push({ time: startTimeSec, data });
    }
  }

  if (cuesList.length > 0) {
    console.log(`成功解析 SRT 字幕，提取到 ${cuesList.length} 姿态帧数据！`);
    videoFlightPath.value = path;
    videoCuesList.value = cuesList;
    if (!videoTelemetry.value) {
      videoTelemetry.value = cuesList[0].data;
    }
  }
}



// 视频开始播放后，异步全量流式读取整个 MP4 中的姿态文本 (控制内存安全分块，使用 latin1 编码)
async function parseMp4EmbeddedSubtitlesStream(url: string) {
  if (videoCuesList.value.length > 0) return;

  if (mp4ParseAbortController) {
    mp4ParseAbortController.abort();
  }
  mp4ParseAbortController = new AbortController();

  // 使用 setTimeout 拆分到下一个微任务帧，完全不卡顿主线程与视频首帧播放
  setTimeout(async () => {
    try {
      const response = await fetch(url, { signal: mp4ParseAbortController!.signal });
      if (!response.ok || !response.body) return;

      const contentLengthStr = response.headers.get("content-length");
      const totalBytes = contentLengthStr ? parseInt(contentLengthStr) : 0;

      const reader = response.body.getReader();
      const decoder = new TextDecoder("latin1");

      const path: Array<[number, number]> = [];
      const cuesList: Array<{ time: number; data: VideoTelemetryData }> = [];

      let leftover = "";
      let bytesRead = 0;

      while (true) {
        const { done, value } = await reader.read();
        if (done || !value) break;

        bytesRead += value.length;
        const chunkText = decoder.decode(value, { stream: true });
        const textToScan = leftover + chunkText;

        // 线性 O(N) 切割，确保不爆内存
        const parts = textToScan.split(/\[latitude:\s*/i);

        if (parts.length > 1) {
          for (let i = 1; i < parts.length - 1; i++) {
            const chunk = "[latitude: " + parts[i].substring(0, 250);
            const data = parseDroneTelemetry(chunk);
            if (data.latitude !== null && data.longitude !== null) {
              // 提取 FrameCnt 保证时间戳精准，缺失则用字节进度兜底
              const previousText = parts[i - 1];
              const preContext = previousText.substring(Math.max(0, previousText.length - 200));
              const frameMatch = preContext.match(/FrameCnt:\s*(\d+)/);
              
              let timeSec = 0;
              if (frameMatch) {
                timeSec = parseInt(frameMatch[1]) / 30.0;
              } else if (totalBytes > 0 && videoRef.value && videoRef.value.duration) {
                timeSec = (bytesRead / totalBytes) * videoRef.value.duration;
              } else {
                timeSec = cuesList.length * 1.0;
              }

              path.push([data.latitude, data.longitude]);
              cuesList.push({ time: timeSec, data });
            }
          }
          // 保存最后一段可能截断的文本供下次拼接
          leftover = parts[parts.length - 1];
          if (leftover.length > 2000) leftover = leftover.substring(leftover.length - 2000);
        } else {
          leftover = textToScan;
          if (leftover.length > 2000) leftover = leftover.substring(leftover.length - 2000);
        }

        // 定期动态更新 Vue UI 航迹 (每收集超过 30 帧更新一次避免卡顿)
        if (cuesList.length >= 30 && cuesList.length % 30 === 0) {
          videoFlightPath.value = [...path];
          videoCuesList.value = [...cuesList];
          if (!videoTelemetry.value) {
            videoTelemetry.value = cuesList[0].data;
          }
        }
      }

      // 最后收尾 leftover
      if (leftover) {
        const chunk = "[latitude: " + leftover.substring(0, 250);
        const data = parseDroneTelemetry(chunk);
        if (data.latitude !== null && data.longitude !== null) {
          let timeSec = cuesList.length > 0 ? cuesList[cuesList.length - 1].time + 1.0 : 0;
          path.push([data.latitude, data.longitude]);
          cuesList.push({ time: timeSec, data });
        }
      }

      if (cuesList.length > 0) {
        console.log(`全量视频解析完毕！共提取出 ${cuesList.length} 帧姿态数据！`);
        videoFlightPath.value = [...path];
        videoCuesList.value = [...cuesList];
        if (!videoTelemetry.value) {
          videoTelemetry.value = cuesList[0].data;
        }
      }
    } catch (err: any) {
      if (err.name !== 'AbortError') {
        console.warn("全量读取 MP4 字幕流提示:", err);
      }
    }
  }, 100);
}


// 视频侧边栏属性项 (包含截图中的完整大疆姿态数据)
const flightHub2VideoMetadata = computed(() => {
  const items: { label: string; value: string }[] = [];
  items.push({ label: "视频名称", value: previewName.value || "--" });
  items.push({ label: "任务名称", value: selectedJobName.value || "--" });

  if (videoTelemetry.value) {
    const t = videoTelemetry.value;
    if (t.latitude !== null && t.longitude !== null) {
      items.push({ label: "无人机纬度", value: `${t.latitude.toFixed(6)}° N` });
      items.push({ label: "无人机经度", value: `${t.longitude.toFixed(6)}° E` });
    }
    if (t.heading !== null) {
      items.push({ label: "云台/航向角 (gb_yaw)", value: `${Math.round(t.heading)}°` });
    }
    if (t.pitch !== undefined && t.pitch !== null) {
      items.push({ label: "云台俯仰角 (gb_pitch)", value: `${t.pitch.toFixed(1)}°` });
    }
    if (t.relAlt !== null) {
      items.push({ label: "相对高度 (rel_alt)", value: `${t.relAlt.toFixed(2)} m` });
    }
    if (t.absAlt !== null) {
      items.push({ label: "绝对高度 (abs_alt)", value: `${t.absAlt.toFixed(2)} m` });
    }
    if (t.iso || t.shutter) {
      const isoStr = t.iso ? `ISO ${t.iso}` : '';
      const shutterStr = t.shutter ? `${t.shutter}s` : '';
      items.push({ label: "曝光参数", value: [isoStr, shutterStr].filter(Boolean).join("  ") || "--" });
    }
    if (videoCuesList.value.length > 0) {
      items.push({ label: "姿态采样", value: `${videoCuesList.value.length} 帧姿态数据` });
    }
  } else {
    items.push({ label: "姿态状态", value: "解析中..." });
  }

  return items;
});

// 大疆司空2 风格的图片详细信息项
const flightHub2Metadata = computed(() => {
  if (!imageMetadata.value) return [];
  const meta = imageMetadata.value;
  const items: { label: string; value: string }[] = [];

  // 1. 照片名称
  items.push({ label: "照片名称", value: previewName.value || "--" });

  // 2. 镜头类型
  let lensType = "--";
  if (meta.FocalLengthIn35mmFormat) {
    lensType = meta.FocalLengthIn35mmFormat > 100
      ? `变焦照片 (${meta.FocalLengthIn35mmFormat}mm)`
      : `广角照片 (${meta.FocalLengthIn35mmFormat}mm)`;
  } else if (meta.FocalLength) {
    lensType = `照片 (${meta.FocalLength}mm)`;
  } else if (meta.LensModel) {
    lensType = meta.LensModel;
  }
  items.push({ label: "镜头类型", value: lensType });

  // 3. 任务名称
  items.push({ label: "任务名称", value: selectedJobName.value || "--" });

  // 4. 航线名称
  items.push({ label: "航线名称", value: "--" });

  // 5. 照片分辨率
  const w = meta.ExifImageWidth || meta.ImageWidth;
  const h = meta.ExifImageHeight || meta.ImageHeight;
  items.push({ label: "照片分辨率", value: (w && h) ? `${w} x ${h}` : "--" });

  // 6. 照片大小
  items.push({ label: "照片大小", value: "--" });

  // 7. 拍摄设备
  const device = meta.Model || meta.Make || "--";
  items.push({ label: "拍摄设备", value: device });

  // 8. 文件来源
  items.push({ label: "文件来源", value: "--" });

  // 9. 拍摄时间
  let shootTime = "--";
  if (meta.DateTimeOriginal || meta.CreateDate) {
    const raw = meta.DateTimeOriginal || meta.CreateDate;
    if (raw instanceof Date) {
      const year = raw.getFullYear();
      const month = String(raw.getMonth() + 1).padStart(2, '0');
      const day = String(raw.getDate()).padStart(2, '0');
      const hours = String(raw.getHours()).padStart(2, '0');
      const mins = String(raw.getMinutes()).padStart(2, '0');
      const secs = String(raw.getSeconds()).padStart(2, '0');
      shootTime = `${year}-${month}-${day} ${hours}:${mins}:${secs} (UTC+08)`;
    } else {
      shootTime = `${String(raw)} (UTC+08)`;
    }
  }
  items.push({ label: "拍摄时间", value: shootTime });

  // 10. 标签
  items.push({ label: "标签", value: "--" });

  return items;
});

// 图片元数据字段映射（英文 -> 中文）
const metadataLabels: Record<string, string> = {
  Make: "相机厂商",
  Model: "相机型号",
  Software: "软件",
  DateTimeOriginal: "拍摄时间",
  CreateDate: "创建时间",
  ModifyDate: "修改时间",
  ImageWidth: "图像宽度",
  ImageHeight: "图像高度",
  ExifImageWidth: "图像宽度",
  ExifImageHeight: "图像高度",
  Orientation: "方向",
  XResolution: "X分辨率",
  YResolution: "Y分辨率",
  ResolutionUnit: "分辨率单位",
  ColorSpace: "色彩空间",
  ExposureTime: "曝光时间",
  FNumber: "光圈值",
  ISO: "ISO感光度",
  ShutterSpeedValue: "快门速度",
  ApertureValue: "光圈",
  BrightnessValue: "亮度值",
  ExposureBiasValue: "曝光补偿",
  MaxApertureValue: "最大光圈",
  MeteringMode: "测光模式",
  Flash: "闪光灯",
  FocalLength: "焦距",
  FocalLengthIn35mmFormat: "等效35mm焦距",
  WhiteBalance: "白平衡",
  DigitalZoomRatio: "数码变焦比",
  SceneCaptureType: "场景类型",
  Contrast: "对比度",
  Saturation: "饱和度",
  Sharpness: "锐度",
  GPSLatitude: "GPS纬度",
  GPSLongitude: "GPS经度",
  GPSAltitude: "GPS高度",
  GPSSpeed: "GPS速度",
  GPSImgDirection: "GPS方向",
  latitude: "纬度",
  longitude: "经度",
  LensModel: "镜头型号",
  LensMake: "镜头厂商",
};

// 格式化元数据值
function formatMetadataValue(key: string, value: any): string {
  if (value === undefined || value === null) return "--";

  // 日期格式化
  if (key.includes("Date") || key.includes("Time")) {
    if (value instanceof Date) {
      return value.toLocaleString("zh-CN");
    }
    return String(value);
  }

  // GPS坐标格式化
  if (key === "GPSLatitude" || key === "latitude") {
    return `${value.toFixed(6)}°`;
  }
  if (key === "GPSLongitude" || key === "longitude") {
    return `${value.toFixed(6)}°`;
  }
  if (key === "GPSAltitude") {
    return `${value.toFixed(2)} m`;
  }

  // 曝光时间格式化
  if (key === "ExposureTime") {
    if (value < 1) {
      return `1/${Math.round(1 / value)} s`;
    }
    return `${value} s`;
  }

  // 光圈格式化
  if (key === "FNumber") {
    return `f/${value}`;
  }

  // 焦距格式化
  if (key === "FocalLength" || key === "FocalLengthIn35mmFormat") {
    return `${value} mm`;
  }

  // 尺寸格式化
  if (key === "ImageWidth" || key === "ImageHeight" || key === "ExifImageWidth" || key === "ExifImageHeight") {
    return `${value} px`;
  }

  // 分辨率格式化
  if (key === "XResolution" || key === "YResolution") {
    return `${value} dpi`;
  }

  return String(value);
}

// 解析图片EXIF元数据
// 尝试多种方式获取图片原始数据以解析EXIF
async function parseImageMetadata(url: string, workspaceId?: string, fileId?: string): Promise<Record<string, any> | null> {
  try {
    let imageData: ArrayBuffer | null = null;

    // 方案1: 通过后端API下载图片原始数据（带认证）
    if (workspaceId && fileId) {
      try {
        const token = localStorage.getItem(ELocalStorageKey.Token);
        const apiUrl = `/media/api/v1/files/${workspaceId}/file/${fileId}/url`;

        const response = await fetch(apiUrl, {
          method: 'GET',
          headers: {
            'x-auth-token': token || '',
          },
          redirect: 'follow'  // 自动跟随重定向
        });

        if (response.ok) {
          const contentType = response.headers.get('content-type');
          // 检查是否返回了图片数据
          if (contentType && contentType.startsWith('image/')) {
            imageData = await response.arrayBuffer();
            console.log("方案1成功: 后端API直接返回图片数据");
          }
        }
      } catch (e) {
        console.warn("方案1失败 (后端API):", e);
      }
    }

    // 方案2: 通过Vite代理获取MinIO资源（开发环境）
    if (!imageData && url) {
      try {
        // 检查是否是MinIO URL，如果是则转换为代理URL
        const urlObj = new URL(url);
        // 匹配常见的MinIO/S3端口或域名模式
        if (urlObj.port === '9002' || urlObj.hostname.includes('minio') || urlObj.hostname.includes('oss')) {
          const proxyUrl = `/minio-proxy${urlObj.pathname}${urlObj.search}`;
          console.log("尝试通过代理访问:", proxyUrl);

          const response = await fetch(proxyUrl, {
            mode: 'cors',
            credentials: 'omit'
          });

          if (response.ok) {
            imageData = await response.arrayBuffer();
            console.log("方案2成功: Vite代理");
          }
        }
      } catch (e) {
        console.warn("方案2失败 (Vite代理):", e);
      }
    }

    // 方案3: 使用XMLHttpRequest获取（可能有更好的CORS处理）
    if (!imageData) {
      try {
        imageData = await new Promise<ArrayBuffer>((resolve, reject) => {
          const xhr = new XMLHttpRequest();
          xhr.open('GET', url, true);
          xhr.responseType = 'arraybuffer';
          xhr.withCredentials = false; // 预签名URL不需要credentials

          xhr.onload = () => {
            if (xhr.status === 200) {
              resolve(xhr.response);
            } else {
              reject(new Error(`HTTP ${xhr.status}`));
            }
          };
          xhr.onerror = () => reject(new Error('XHR network error'));
          xhr.ontimeout = () => reject(new Error('XHR timeout'));
          xhr.timeout = 30000; // 30秒超时
          xhr.send();
        });
        console.log("方案3成功: XMLHttpRequest");
      } catch (e) {
        console.warn("方案3失败 (XMLHttpRequest):", e);
      }
    }

    // 方案4: 使用fetch API获取
    if (!imageData) {
      try {
        const response = await fetch(url, {
          mode: 'cors',
          credentials: 'omit'
        });
        if (response.ok) {
          imageData = await response.arrayBuffer();
          console.log("方案4成功: fetch API");
        }
      } catch (e) {
        console.warn("方案4失败 (fetch):", e);
      }
    }

    if (!imageData) {
      console.warn("无法获取图片原始数据，跳过EXIF解析");
      console.log("预览URL:", url);
      // 返回一个提示信息，说明需要配置服务器CORS
      return {
        "_notice": "无法读取图片数据",
        "_hint": "需要在存储服务器(MinIO)配置CORS策略"
      };
    }

    // 使用exifr解析数据
    const metadata = await exifr.parse(imageData, {
      // 解析所有可用的模块
      tiff: true,
      exif: true,
      gps: true,
      ifd1: true,
      interop: true,
      // 包含GPS坐标
      translateValues: true,
      translateKeys: true,
    });

    if (!metadata) {
      console.log("图片不包含EXIF元数据");
      return null;
    }

    // 过滤并格式化有用的元数据
    const result: Record<string, any> = {};
    const priorityKeys = [
      "Make", "Model", "LensModel",
      "DateTimeOriginal", "CreateDate",
      "ExifImageWidth", "ExifImageHeight", "ImageWidth", "ImageHeight",
      "ExposureTime", "FNumber", "ISO",
      "FocalLength", "FocalLengthIn35mmFormat",
      "latitude", "longitude", "GPSAltitude",
      "WhiteBalance", "Flash", "MeteringMode",
      "Software"
    ];

    for (const key of priorityKeys) {
      if (metadata[key] !== undefined && metadata[key] !== null) {
        result[key] = metadata[key];
      }
    }

    return Object.keys(result).length > 0 ? result : null;
  } catch (error) {
    console.error("解析图片元数据失败:", error);
    return null;
  }
}

function getFileTypeByName(name: string) {
  const lower = name.toLowerCase();
  if (/(jpg|jpeg|png|gif|bmp|webp)$/.test(lower)) return "image";
  if (/(mp4|mov|avi|webm|ogg|mkv)$/.test(lower)) return "video";
  return "other";
}

// 缩略图 URL 缓存（响应式，确保 URL 加载后视图自动刷新）
// key: file_id, value: url | 'loading' | 'error'
const thumbnailCache = reactive<Record<string, string>>({})

const getThumbnailUrl = async (fileId: string): Promise<void> => {
  if (thumbnailCache[fileId]) return
  thumbnailCache[fileId] = 'loading'
  const workspaceId = getWorkspaceId()
  if (!workspaceId) { thumbnailCache[fileId] = 'error'; return }
  try {
    const url = await getMediaFileUrl(workspaceId, fileId)
    thumbnailCache[fileId] = url || 'error'
  } catch {
    thumbnailCache[fileId] = 'error'
  }
}

// 预览文件（弹窗）
const handlePreview = async (fileId: string, fileName: string) => {
  const workspaceId = getWorkspaceId();
  if (!workspaceId) return;
  const url = await getMediaFileUrl(workspaceId, fileId);
  if (url) {
    previewUrl.value = url;
    const fileType = getFileTypeByName(fileName);
    previewType.value = fileType;
    previewName.value = fileName;
    imageMetadata.value = null;
    videoTelemetry.value = null;
    videoFlightPath.value = [];
    videoCuesList.value = [];
    previewVisible.value = true;

    // 如果是图片文件，异步解析EXIF元数据
    if (fileType === "image") {
      metadataLoading.value = true;
      try {
        const metadata = await parseImageMetadata(url, workspaceId, fileId);
        imageMetadata.value = metadata;
      } catch (error) {
        console.error("解析元数据失败:", error);
      } finally {
        metadataLoading.value = false;
      }
    } else if (fileType === "video") {
      // 异步检索同名字幕文件 (.SRT)，避免强行并发 fetch 视频 URL 占用 MinIO 签名通道
      const srtBaseName = fileName.replace(/\.(mp4|mov|avi|webm)$/i, "");
      let srtChildFile: TableData | undefined;

      for (const folder of allFolders.value) {
        if (folder.children) {
          srtChildFile = folder.children.find(
            (c) => c.fileName.toLowerCase() === `${srtBaseName.toLowerCase()}.srt`
          );
          if (srtChildFile) break;
        }
      }

      if (srtChildFile) {
        try {
          const srtUrl = await getMediaFileUrl(workspaceId, srtChildFile.file_id);
          if (srtUrl) {
            const res = await fetch(srtUrl);
            if (res.ok) {
              const srtText = await res.text();
              parseSrtText(srtText);
            }
          }
        } catch (e) {
          console.warn("读取配套 SRT 字幕失败:", e);
        }
      }
    }
  } else {
    message.error("获取预览链接失败");
  }
};
</script>

<template>
  <div class="command-container">
    <aside class="sidebar">
      <div class="sidebar-item">
        <div class="sidebar-header">
          <img src="@/assets/icons/model.png" class="sidebar-icon" />
          媒体库
        </div>
        <div class="divider"></div>
        <div class="sidebar-content">
          <div class="space-usage-section">
            <div class="space-usage-header">
              <span>项目空间使用情况</span>
              <span v-if="storageStatData" class="space-usage-percent">
                使用率 {{ diskUsage.usagePercentStr }}
              </span>
            </div>

            <!-- 整体存储容量进度条 -->
            <div v-if="storageStatData" class="disk-progress-bar">
              <div class="progress-track">
                <div class="progress-fill" :style="{ width: diskUsage.usagePercentStr }"></div>
              </div>
              <div class="progress-labels">
                <span>已用 {{ diskUsage.usedSize }}</span>
                <span>容量 {{ diskUsage.totalSize }}</span>
              </div>
            </div>

            <div class="space-usage-items">
              <div class="space-usage-group">
                <div class="space-usage-item">
                  <div class="space-usage-item-header">
                    <img src="@/assets/icons/photo.png" class="space-icon" />
                    <div class="space-text-group">
                      <span class="space-label">照片</span>
                      <div class="space-count">{{ categorizedStats.photos.count }} 文件</div>
                    </div>
                  </div>
                  <div class="space-usage-details">
                    <div class="space-size">{{ categorizedStats.photos.size }}</div>
                  </div>
                </div>
                <div class="space-usage-item">
                  <div class="space-usage-item-header">
                    <img src="@/assets/icons/video.png" class="space-icon" />
                    <div class="space-text-group">
                      <span class="space-label">视频</span>
                      <div class="space-count">{{ categorizedStats.videos.count }} 文件</div>
                    </div>
                  </div>
                  <div class="space-usage-details">
                    <div class="space-size">{{ categorizedStats.videos.size }}</div>
                  </div>
                </div>
                <div v-if="bucketStatData" class="space-usage-item">
                  <div class="space-usage-item-header">
                    <img src="@/assets/icons/model.png" class="space-icon" />
                    <div class="space-text-group">
                      <span class="space-label">其他/工程数据</span>
                      <div class="space-count">{{ categorizedStats.others.count }} 文件</div>
                    </div>
                  </div>
                  <div class="space-usage-details">
                    <div class="space-size">{{ categorizedStats.others.size }}</div>
                  </div>
                </div>
              </div>
              <div class="space-usage-item remaining">
                <div class="space-usage-item-header">
                  <img src="@/assets/icons/remaining.png" class="space-icon" />
                  <div class="space-text-group">
                    <span class="space-label">剩余可用空间</span>
                  </div>
                </div>
                <div class="space-usage-details">
                  <div class="space-size">{{ diskUsage.freeSize }}</div>
                </div>
              </div>
            </div>
          </div>
          <!-- <div class="divider"></div> -->
        </div>
      </div>
    </aside>
    <main class="main-content">
      <!-- 任务筛选提示 -->
      <div v-if="isFromTaskPage && selectedJobName" class="task-filter-banner">
        <div class="filter-info">
          <span class="filter-label">当前正在查看任务：</span>
          <span class="filter-value">{{ selectedJobName }}</span>
          <a-button size="small" type="link" @click="resetFilters">清除筛选</a-button>
        </div>
      </div>

      <div class="toolbar searchBox">
        <!-- <div class="toolbar-row">
          <a-button class="toolbar-button" type="primary">模型重建</a-button>
          <a-button class="toolbar-button">导入</a-button>
          <a-button class="toolbar-button">变化检测</a-button>
          <a-button class="toolbar-button">创建文件夹</a-button>
        </div> -->
        <div class="toolbar-row">
          <a-range-picker class="date-picker" v-model:value="dateRange" :placeholder="['开始时间', '结束时间']"
            :show-time="{ format: 'HH:mm' }" format="YYYY-MM-DD HH:mm"
            :get-popup-container="(triggerNode: any) => triggerNode.parentNode" />
          <a-select v-model:value="selectedDeviceSn" :options="deviceOptions" class="select" placeholder="所有设备"
            allow-clear />
          <a-select v-model:value="filterState.is_original" class="select" placeholder="文件类型" allow-clear>
            <a-select-option :value="1">媒体文件</a-select-option>
            <a-select-option :value="0">非媒体文件</a-select-option>
            <a-select-option :value="undefined">全部</a-select-option>
          </a-select>
          <div class="checkbox-group">
            <a-button class="filter-button" @click="mapLoadedMediaChecked = !mapLoadedMediaChecked">
              <a-checkbox v-model:checked="mapLoadedMediaChecked" @click.stop=""> </a-checkbox>
              地图上加载的媒体文件
            </a-button>
            <a-button class="filter-button" @click="sharedMediaChecked = !sharedMediaChecked">
              <a-checkbox v-model:checked="sharedMediaChecked" @click.stop=""> </a-checkbox>
              已分享的媒体文件
            </a-button>
            <a-input v-model:value="searchKeyword" class="search-input" placeholder="按文件名搜索" allow-clear
              @pressEnter="handleSearch" />
            <a-button type="primary" @click="handleSearch">查询</a-button>
            <a-button class="reset-button" @click="resetFilters">重置</a-button>
          </div>
        </div>

      </div>
      <div class="table-container table">
        <a-table :data-source="dataSource" :columns="columns" :loading="loading"
          :scroll="{ x: 'max-content', y: 'calc(100vh - 380px)' }" :pagination="false" :locale="{ emptyText: '暂无数据' }"
          :expand-row-by-click="true" v-model:expanded-row-keys="expandedRowKeys" children-column-name="children"
          :row-class-name="(record: TableData) => record.isFolder ? 'folder-row' : 'file-row'">
          <!-- 文件名列自定义渲染 -->
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'fileName'">
              <div class="file-name-cell">
                <!-- 文件夹图标 -->
                <span v-if="record.isFolder" class="file-icon folder-icon">
                  <FolderFilled />
                </span>
                <!-- 文件图标（根据文件类型显示不同图标） -->
                <span v-else class="file-icon">
                  <!-- 图片文件 -->
                  <template v-if="record.fileName.match(/\.(jpg|jpeg|png|gif|bmp|webp)$/i)">
                    <FileImageFilled class="image-icon" />
                  </template>
                  <!-- 视频文件 -->
                  <template v-else-if="record.fileName.match(/\.(mp4|mov|avi|mkv|webm)$/i)">
                    <VideoCameraFilled class="video-icon" />
                  </template>
                  <!-- 压缩文件 -->
                  <template v-else-if="record.fileName.match(/\.(zip|rar|7z|tar|gz)$/i)">
                    <FileZipFilled class="archive-icon" />
                  </template>
                  <!-- 飞行数据文件 (默认) -->
                  <template v-else>
                    <RocketFilled class="flight-data-icon" />
                  </template>
                </span>
                <span class="file-name-text" :class="{ 'folder-name': record.isFolder }">
                  {{ record.fileName }}
                  <span v-if="record.isFolder && record.fileCount" class="file-count-badge">
                    ({{ record.fileCount }})
                  </span>
                </span>
              </div>
            </template>
            <!-- 缩略图列 -->
            <template v-if="column.key === 'thumbnail'">
              <div v-if="!record.isFolder && record.fileName.match(/\.(jpg|jpeg|png|gif|bmp|webp)$/i)"
                class="thumbnail-cell">
                <div class="thumbnail-wrapper"
                  @mouseenter="getThumbnailUrl(record.file_id)"
                  @click.stop="handlePreview(record.file_id, record.fileName)">
                  <template v-if="thumbnailCache[record.file_id] && thumbnailCache[record.file_id] !== 'loading' && thumbnailCache[record.file_id] !== 'error'">
                    <img
                      :src="thumbnailCache[record.file_id]"
                      class="thumbnail-img"
                      :alt="record.fileName"
                    />
                    <div class="thumbnail-overlay">
                      <span class="thumbnail-zoom-icon">🔍</span>
                    </div>
                  </template>
                  <template v-else-if="thumbnailCache[record.file_id] === 'loading'">
                    <a-spin size="small" />
                  </template>
                  <template v-else>
                    <div class="thumbnail-placeholder" @mouseenter="getThumbnailUrl(record.file_id)">
                      <FileImageFilled class="thumbnail-placeholder-icon" />
                    </div>
                  </template>
                </div>
              </div>
              <span v-else></span>
            </template>
            <template v-else-if="column.key === 'action'">
              <div class="action-buttons" v-if="!record.isFolder">
                <a-tooltip title="预览文件" v-if="isPreviewable(record.fileName)">
                  <a-button type="link" size="small" class="preview-button"
                    @click.stop="handlePreview(record.file_id, record.fileName)"> 预览 </a-button>
                </a-tooltip>
                <a-tooltip title="下载文件">
                  <a-button type="link" size="small" class="download-button"
                    :loading="downloadingFiles.has(record.file_id)"
                    @click.stop="handleDownload(record.file_id, record.fileName)"> 下载 </a-button>
                </a-tooltip>
              </div>
              <div class="action-buttons" v-else>
                <a-tooltip title="查看飞行详细数据">
                  <a-button type="link" size="small" class="preview-button"
                    @click.stop="showFlightDetail(record.jobId!)">
                    详情
                  </a-button>
                </a-tooltip>
                <a-tooltip title="展开/收起">
                  <a-button type="link" size="small" class="folder-action-button">
                    {{ expandedRowKeys.includes(record.key) ? '收起' : '展开' }}
                  </a-button>
                </a-tooltip>
                <!-- 文件夹批量打包下载按钮 -->
                <a-tooltip title="将该文件夹所有文件打包下载为ZIP,逐步调用单个文件实现，前端压缩实现，文件数量多会导致效率降低。等待后端实现">
                  <a-button type="link" size="small" class="folder-download-button"
                    :disabled="folderDownloadProgress[record.key]?.visible" @click.stop="handleFolderDownload(record)">
                    <template v-if="folderDownloadProgress[record.key]?.visible">
                      <a-spin size="small" style="margin-right:4px;" />
                      {{ folderDownloadProgress[record.key].current }}/{{ folderDownloadProgress[record.key].total }}
                    </template>
                    <template v-else>下载全部</template>
                  </a-button>
                </a-tooltip>
              </div>
            </template>
          </template>
        </a-table>
        <div class="pagination-container">
          <a-pagination v-model:current="folderPage" v-model:page-size="folderPageSize" :total="allFolders.length"
            :show-total="(total: number) => `共 ${total} 个任务文件夹`" :page-size-options="['5', '10', '20', '50']"
            show-size-changer show-quick-jumper @change="handleFolderPageChange"
            @show-size-change="handleFolderPageChange" />
        </div>
      </div>
    </main>
  </div>
  <a-modal v-model:visible="previewVisible" :title="null" :closable="false"
    width="85%" centered :footer="null"
    :bodyStyle="{ padding: 0, backgroundColor: '#141416' }" @cancel="closePreview">
    <div class="flighthub-preview-modal">
      <!-- 大疆司空2 顶部 Header -->
      <div class="flighthub-modal-header">
        <div class="header-info">
          <span class="file-title">{{ previewName }}</span>
          <div class="file-subtext" v-if="previewType === 'image' && imageMetadata">
            <span v-if="flightHub2Metadata.find(i => i.label === '拍摄时间')?.value !== '--'">
              {{ flightHub2Metadata.find(i => i.label === '拍摄时间')?.value }}
            </span>
            <span v-if="flightHub2Metadata.find(i => i.label === '照片分辨率')?.value !== '--'">
              {{ flightHub2Metadata.find(i => i.label === '照片分辨率')?.value }}
            </span>
          </div>
          <div class="file-subtext" v-else-if="previewType === 'video'">
            <span v-if="videoTelemetry && videoTelemetry.latitude !== null">🛸 实时无人机姿态跟踪</span>
            <span v-if="videoCuesList.length">航迹点: {{ videoCuesList.length }}</span>
          </div>
        </div>
        <button class="close-btn" @click="closePreview" title="关闭预览">✕</button>
      </div>

      <div class="flighthub-modal-body with-sidebar">
        <!-- 媒体主显示区域 -->
        <div class="preview-media-container">
          <template v-if="previewType === 'image'">
            <img :src="previewUrl" :alt="previewName" class="preview-media-element" />
          </template>
          <template v-else-if="previewType === 'video'">
            <video
              ref="videoRef"
              :src="previewUrl"
              autoplay
              muted
              controls
              class="preview-media-element"
              @loadedmetadata="handleVideoLoadedMetadata"
              @canplay="parseMp4EmbeddedSubtitlesStream(previewUrl)"
              @timeupdate="handleVideoTimeUpdate"
            />
          </template>
          <template v-else>
            <div class="preview-fallback">
              <a :href="previewUrl" target="_blank">点击新窗口打开文件</a>
            </div>
          </template>
        </div>

        <!-- 大疆司空2 右侧“详细信息”侧边栏 -->
        <div class="flighthub-sidebar">
          <div class="sidebar-header">
            <span class="sidebar-title">详细信息</span>
          </div>
          <div class="sidebar-content">
            <!-- 图片信息模式 -->
            <template v-if="previewType === 'image'">
              <template v-if="metadataLoading">
                <div class="metadata-loading">
                  <a-spin size="small" />
                  <span>正在解析元数据...</span>
                </div>
              </template>
              <template v-else-if="imageMetadata">
                <!-- CORS 限制提示 -->
                <div v-if="imageMetadata._notice" class="metadata-notice">
                  <span class="notice-icon">⚠️</span>
                  <span class="notice-text">{{ imageMetadata._notice }}</span>
                  <span v-if="imageMetadata._hint" class="notice-hint">{{ imageMetadata._hint }}</span>
                </div>

                <template v-else>
                  <!-- 司空2 列表样式 -->
                  <div class="flighthub-meta-list">
                    <div v-for="item in flightHub2Metadata" :key="item.label" class="flighthub-meta-row">
                      <span class="meta-label">{{ item.label }}</span>
                      <span class="meta-value">{{ item.value }}</span>
                    </div>
                  </div>

                  <!-- 拍摄位置 (司空2 微缩天地图) -->
                  <div v-if="imageLatitude !== null && imageLongitude !== null" class="location-section">
                    <div class="section-title">拍摄位置</div>
                    <ImageLocationMap
                      :latitude="imageLatitude"
                      :longitude="imageLongitude"
                    />
                  </div>
                </template>
              </template>
              <template v-else>
                <div class="metadata-empty">
                  <span>暂无元数据信息</span>
                </div>
              </template>
            </template>

            <!-- 视频实时无人机姿态模式 -->
            <template v-else-if="previewType === 'video'">
              <div class="flighthub-meta-list">
                <div v-for="item in flightHub2VideoMetadata" :key="item.label" class="flighthub-meta-row">
                  <span class="meta-label">{{ item.label }}</span>
                  <span class="meta-value">{{ item.value }}</span>
                </div>
              </div>

              <!-- 视频实时姿态与飞行航迹地图 (天地图) -->
              <div v-if="videoTelemetry && videoTelemetry.latitude !== null && videoTelemetry.longitude !== null" class="location-section">
                <div class="section-title">飞行姿态与航迹</div>
                <ImageLocationMap
                  :latitude="videoTelemetry.latitude"
                  :longitude="videoTelemetry.longitude"
                  :heading="videoTelemetry.heading || 0"
                  :pitch="videoTelemetry.pitch || 0"
                  :roll="videoTelemetry.roll || 0"
                  :altitude="videoTelemetry.relAlt || videoTelemetry.absAlt || undefined"
                  :flightPath="videoFlightPath"
                />
              </div>
            </template>

            <template v-else>
              <div class="metadata-empty">
                <span>暂无元数据信息</span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </a-modal>

  <a-modal v-model:visible="flightDetailVisible" title="任务详情" width="800px" :footer="null">
    <div v-if="selectedTask" class="task-detail-content" style="padding: 10px;">
      <a-descriptions title="基本信息" bordered :column="2">
        <a-descriptions-item label="任务ID">{{ selectedTask.job_id }}</a-descriptions-item>
        <a-descriptions-item label="计划名称">{{ selectedTask.job_name }}</a-descriptions-item>
        <a-descriptions-item label="航线名称">{{ selectedTask.file_name }}</a-descriptions-item>
        <a-descriptions-item label="任务类型">{{ formatTaskType(selectedTask) }}</a-descriptions-item>
        <a-descriptions-item label="设备名称">{{ selectedTask.dock_name }}</a-descriptions-item>
        <a-descriptions-item label="设备SN">{{ selectedTask.dock_sn }}</a-descriptions-item>
        <a-descriptions-item label="创建人">{{ selectedTask.username }}</a-descriptions-item>
        <a-descriptions-item label="工作区ID">{{ selectedTask.workspace_id }}</a-descriptions-item>
      </a-descriptions>

      <a-descriptions title="执行信息" bordered :column="2" style="margin-top: 20px">
        <a-descriptions-item label="任务状态">
          <span style="display: flex; align-items: center;">
            <span style="display:inline-block; width:8px; height:8px; border-radius:50%; margin-right:5px;"
              :style="{ backgroundColor: formatTaskStatus(selectedTask).color }"></span>
            {{ formatTaskStatus(selectedTask).text }}
          </span>
        </a-descriptions-item>
        <a-descriptions-item label="执行进度">{{ selectedTask.progress || 0 }}%</a-descriptions-item>
        <a-descriptions-item label="计划开始时间">{{ formatTaskTime(selectedTask.begin_time) }}</a-descriptions-item>
        <a-descriptions-item label="计划结束时间">{{ formatTaskTime(selectedTask.end_time) }}</a-descriptions-item>
        <a-descriptions-item label="实际执行时间">{{ formatTaskTime(selectedTask.execute_time) }}</a-descriptions-item>
        <a-descriptions-item label="完成时间">{{ formatTaskTime(selectedTask.completed_time) }}</a-descriptions-item>
      </a-descriptions>

      <div v-if="selectedTask.code" style="margin-top: 20px">
        <a-alert message="错误信息" :description="`错误代码: ${selectedTask.code}`" type="error" show-icon />
      </div>
    </div>
  </a-modal>
</template>

<style scoped lang="scss">
.task-detail-content {
  :deep(.ant-descriptions-title) {
    color: #fff;
  }

  :deep(.ant-descriptions-item-label) {
    color: #a6b5cc;
    background-color: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.1);
  }

  :deep(.ant-descriptions-item-content) {
    color: #fff;
    border-color: rgba(255, 255, 255, 0.1);
  }

  :deep(.ant-descriptions-bordered .ant-descriptions-view) {
    border-color: rgba(255, 255, 255, 0.1);
  }
}

.preview-wrapper {
  display: flex;
  background: #1f1f1f;

  &.with-metadata {
    .preview-container {
      flex: 1;
      min-width: 0;
    }
  }
}

/* 大疆司空2 (DJI FlightHub 2) 预览弹窗与详细信息样式 */
.flighthub-preview-modal {
  display: flex;
  flex-direction: column;
  background: #141416;
  color: #ffffff;
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.7);

  .flighthub-modal-header {
    height: 46px;
    padding: 0 16px;
    background: #1a1a1e;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    display: flex;
    align-items: center;
    justify-content: space-between;

    .header-info {
      display: flex;
      align-items: center;
      gap: 16px;

      .file-title {
        font-size: 14px;
        font-weight: 600;
        color: #ffffff;
        letter-spacing: 0.2px;
      }

      .file-subtext {
        font-size: 12px;
        color: #8c8c8c;
        display: flex;
        gap: 12px;
      }
    }

    .close-btn {
      background: transparent;
      border: none;
      color: rgba(255, 255, 255, 0.65);
      font-size: 16px;
      cursor: pointer;
      padding: 4px 8px;
      border-radius: 4px;
      transition: all 0.2s ease;

      &:hover {
        color: #ffffff;
        background: rgba(255, 255, 255, 0.1);
      }
    }
  }

  .flighthub-modal-body {
    display: flex;
    height: 75vh;
    background: #0d0d0e;

    .preview-media-container {
      flex: 1;
      min-width: 0;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #080809;
      padding: 12px;

      .preview-media-element {
        max-width: 100%;
        max-height: 100%;
        object-fit: contain;
        border-radius: 2px;
      }

      .preview-fallback {
        color: #fff;
        padding: 20px;
        a {
          color: #69b1ff;
        }
      }
    }

    .flighthub-sidebar {
      width: 320px;
      min-width: 320px;
      background: #16161a;
      border-left: 1px solid rgba(255, 255, 255, 0.08);
      display: flex;
      flex-direction: column;

      .sidebar-header {
        height: 40px;
        padding: 0 16px;
        display: flex;
        align-items: center;
        border-bottom: 1px solid rgba(255, 255, 255, 0.06);
        background: #141416;

        .sidebar-title {
          font-size: 13px;
          font-weight: 600;
          color: #ffffff;
        }
      }

      .sidebar-content {
        flex: 1;
        overflow-y: auto;
        padding: 12px 16px;

        &::-webkit-scrollbar {
          width: 4px;
        }
        &::-webkit-scrollbar-track {
          background: transparent;
        }
        &::-webkit-scrollbar-thumb {
          background: rgba(255, 255, 255, 0.15);
          border-radius: 2px;
        }
      }

      .metadata-loading,
      .metadata-empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 40px 20px;
        gap: 12px;
        color: rgba(255, 255, 255, 0.5);
        font-size: 12px;
      }

      .metadata-notice {
        padding: 16px;
        text-align: center;
        color: #faad14;
        font-size: 13px;
      }

      .flighthub-meta-list {
        display: flex;
        flex-direction: column;

        .flighthub-meta-row {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          padding: 6px 0;
          border-bottom: 1px dashed rgba(255, 255, 255, 0.05);

          &:last-child {
            border-bottom: none;
          }

          .meta-label {
            font-size: 12px;
            color: #8c8c8c;
            flex-shrink: 0;
            margin-right: 12px;
          }

          .meta-value {
            font-size: 12px;
            color: #d9d9d9;
            text-align: right;
            word-break: break-all;
            font-weight: 400;
          }
        }
      }

      .location-section {
        margin-top: 14px;

        .section-title {
          font-size: 13px;
          font-weight: 600;
          color: #ffffff;
          margin-bottom: 6px;
        }
      }
    }
  }
}



.command-container {
  display: flex;
  height: 100%;
}

.sidebar {
  background: linear-gradient(178deg, #041428 0%, #002658 100%);
  color: #fff;
  padding: 20px;
  max-width: 450px;
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
  width: 20px;
  height: 20px;
}

.divider {
  height: 3px;
  background-color: #203a6e;
  margin: 15px 0;
}

.space-usage-section {
  margin-bottom: 20px;

  .space-usage-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 1rem;
    color: #fff;
    margin-bottom: 0.9375rem;
    font-weight: 500;

    .space-usage-percent {
      font-size: 12px;
      color: #38bdf8;
      background: rgba(56, 189, 248, 0.15);
      border: 1px solid rgba(56, 189, 248, 0.3);
      padding: 2px 8px;
      border-radius: 10px;
      font-weight: 400;
    }
  }

  .disk-progress-bar {
    width: 329px;
    margin-bottom: 14px;

    .progress-track {
      width: 100%;
      height: 8px;
      background: rgba(9, 25, 39, 0.6);
      border: 1px solid #214565;
      border-radius: 4px;
      overflow: hidden;

      .progress-fill {
        height: 100%;
        background: linear-gradient(90deg, #1890ff, #00f2fe);
        border-radius: 4px;
        transition: width 0.4s ease;
      }
    }

    .progress-labels {
      display: flex;
      justify-content: space-between;
      font-size: 11px;
      color: #94a3b8;
      margin-top: 4px;
    }
  }

  .space-usage-items {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .space-usage-group {
    width: 329px;
    background: rgba(9, 25, 39, 0.3);
    border: 1px solid #214565;
    border-radius: 5px;
    padding: 10px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .space-usage-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px;

    &.remaining {
      width: 329px;
      background: rgba(9, 25, 39, 0.3);
      border: 1px solid #214565;
      border-radius: 5px;
      padding: 20px;
    }

    .space-usage-item-header {
      display: flex;
      align-items: flex-start;
      gap: 8px;

      .space-icon {
        width: 40px;
        height: 40px;
      }

      .space-text-group {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }

      .space-label {
        font-size: 14px;
        color: #ffffff;
      }

      .space-count {
        font-size: 12px;
        color: #ffffff;
      }
    }

    .space-usage-details {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100%;

      .space-size {
        font-size: 14px;
        color: #ffffff;
        font-weight: 500;
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

.task-filter-banner {
  background: linear-gradient(90deg, #1890ff, #40a9ff);
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 20px;

  .filter-info {
    display: flex;
    align-items: center;
    color: #fff;

    .filter-label {
      font-size: 14px;
      margin-right: 8px;
    }

    .filter-value {
      font-size: 14px;
      font-weight: 500;
      margin-right: 12px;
    }

    :deep(.ant-btn-link) {
      color: #fff;
      text-decoration: underline;
      padding: 0;
      height: auto;

      &:hover {
        color: #f0f0f0;
      }
    }
  }
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

  .toolbar-button {
    background: #27437a;
    border-color: #27437a;
    color: #fff;

    &:hover {
      background: #3a558f;
      border-color: #3a558f;
    }

    &.ant-btn-primary {
      background: #3a7af9;
      border-color: #3a7af9;
      color: #fff;

      &:hover {
        background: #5b8bfa;
        border-color: #5b8bfa;
      }
    }
  }

  .checkbox-group {
    display: flex;
    gap: 15px;
    align-items: center;

    .filter-button {
      background: #27437a;
      border-color: #27437a;
      color: #fff;
      font-size: 12px;
      padding: 4px 12px;
      height: auto;
      display: flex;
      align-items: center;
      gap: 8px;

      &:hover {
        background: #3a558f;
        border-color: #3a558f;
        color: #fff;
      }

      :deep(.ant-checkbox-wrapper) {
        color: #fff;
        margin: 0;
      }

      :deep(.ant-checkbox) {
        .ant-checkbox-inner {
          background: #1e3461;
          border-color: #3a558f;
        }

        &.ant-checkbox-checked .ant-checkbox-inner {
          background: #3a7af9;
          border-color: #3a7af9;
        }
      }
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

/* 全局覆盖 Ant Design 组件样式 */
:deep(.ant-picker) {
  background: #1e3461;
  border-color: #1e3461;
  color: #fff;

  &:hover {
    border-color: #3a7af9;
  }

  .ant-picker-input>input {
    color: #fff;

    &::placeholder {
      color: rgba(255, 255, 255, 0.5);
    }
  }

  .ant-picker-suffix {
    color: #fff;
  }

  .ant-picker-active-bar {
    background: #3a7af9;
  }

  .ant-picker-range-separator {
    color: #fff;
  }

  .ant-picker-clear {
    background: transparent;
    color: rgba(255, 255, 255, 0.5);

    &:hover {
      color: #fff;
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

  .action-buttons {
    display: flex;
    gap: 15px;
    /* 增加按钮间距 */

    .download-button,
    .preview-button {
      color: #69b1ff;
      /* 使用亮蓝色文字 */
      background-color: transparent;
      /* 去除背景色，改为纯文字/图标风格 */
      padding: 0;
      border: none;
      box-shadow: none;
      font-size: 14px;

      &:hover {
        color: #fff;
        background-color: transparent;
        text-shadow: 0 0 8px rgba(105, 177, 255, 0.6);
      }

      &.ant-btn-loading {
        color: rgba(255, 255, 255, 0.5);
      }
    }

    .folder-action-button {
      color: #69b1ff;
      padding: 0;
      font-size: 14px;

      &:hover {
        color: #fff;
        text-shadow: 0 0 8px rgba(105, 177, 255, 0.6);
      }
    }
  }

  // 缩略图列样式
  .thumbnail-cell {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 72px;
    height: 56px;
  }

  .thumbnail-wrapper {
    position: relative;
    width: 72px;
    height: 56px;
    border-radius: 6px;
    overflow: hidden;
    cursor: pointer;
    background: #1a1a2e;
    border: 1px solid rgba(255, 255, 255, 0.08);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: border-color 0.2s ease;

    &:hover {
      border-color: rgba(99, 179, 237, 0.6);

      .thumbnail-overlay {
        opacity: 1;
      }
    }
  }

  .thumbnail-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  .thumbnail-overlay {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.45);
    display: flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: opacity 0.2s ease;
  }

  .thumbnail-zoom-icon {
    font-size: 18px;
    filter: drop-shadow(0 1px 3px rgba(0,0,0,0.8));
  }

  .thumbnail-placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    cursor: pointer;

    &:hover {
      background: rgba(255,255,255,0.04);
    }
  }

  .thumbnail-placeholder-icon {
    font-size: 22px;
    color: #4CAF50;
    opacity: 0.6;
  }

  // 文件名单元格样式
  .file-name-cell {
    display: flex;
    align-items: center;
    gap: 12px;
    /* 增加图标与文字间距 */

    .file-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      font-size: 20px;

      &.folder-icon {
        color: #FFC107;
        filter: drop-shadow(0 0 2px rgba(255, 200, 0, 0.3));
      }

      .image-icon {
        color: #4CAF50;
      }

      .video-icon {
        color: #E91E63;
      }

      .archive-icon {
        color: #9C27B0;
      }

      .flight-data-icon {
        color: #2196F3;
      }
    }

    .file-name-text {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: #e6f7ff;
      font-family: 'Roboto', sans-serif;
      /* 优化字体 */

      &.folder-name {
        font-weight: 500;
        font-size: 15px;
        color: #fff;
      }

      .file-count-badge {
        display: inline-block;
        background: rgba(24, 144, 255, 0.2);
        color: #69b1ff;
        font-size: 12px;
        padding: 1px 6px;
        border-radius: 4px;
        margin-left: 8px;
        border: 1px solid rgba(24, 144, 255, 0.3);
      }
    }
  }

  // 表格通用样式覆盖
  :deep(.ant-table) {
    background: transparent;
    color: #fff;
  }

  :deep(.ant-table-thead > tr > th) {
    background: rgba(30, 52, 97, 0.8) !important;
    /* 表头深色背景 */
    color: #a0c4ff !important;
    border-bottom: 1px solid #3a558f !important;
    font-weight: 600;
  }

  :deep(.ant-table-tbody > tr > td) {
    border-bottom: 1px solid rgba(58, 85, 143, 0.3) !important;
    padding: 12px 16px;
    /* 调整单元格内边距 */
  }

  // 文件夹行样式
  :deep(.folder-row) {
    background: rgba(9, 25, 39, 0.4) !important;

    &:hover>td {
      background: rgba(58, 122, 249, 0.15) !important;
      /* 悬停高亮 */
    }
  }

  // 文件行样式（展开后的子项）
  :deep(.file-row) {
    background: rgba(14, 28, 48, 0.6) !important;
    /* 比文件夹行稍深或稍浅 */

    &:hover>td {
      background: rgba(58, 122, 249, 0.1) !important;
    }
  }

  // 移除默认的表格行 hover 背景（AntDesign默认是白色）
  :deep(.ant-table-tbody > tr.ant-table-row:hover > td) {
    background: inherit;
  }

  // 展开/收起图标样式优化
  :deep(.ant-table-row-expand-icon) {
    background: rgba(255, 255, 255, 0.1);
    border: 1px solid rgba(255, 255, 255, 0.3);
    color: #fff;
    width: 16px;
    height: 16px;
    line-height: 14px;

    &::before,
    &::after {
      background: #fff;
      /* 加号/减号颜色 */
      top: 7px;
      left: 3px;
      width: 8px;
    }

    &::after {
      height: 8px;
      top: 3px;
      left: 7px;
    }

    &:hover {
      border-color: #40a9ff;
      background: rgba(64, 169, 255, 0.2);

      &::before,
      &::after {
        background: #40a9ff;
      }
    }
  }

  // 嵌套表格缩进线（可选，增加视觉引导）
  :deep(.file-row td:first-child) {
    position: relative;

    &::before {
      content: '';
      position: absolute;
      left: 24px;
      /* 调整竖线位置 */
      top: 0;
      bottom: 0;
      width: 1px;
      background: rgba(255, 255, 255, 0.1);
    }
  }


}
</style>
```
