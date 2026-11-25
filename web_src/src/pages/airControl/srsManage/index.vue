<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue';
import { CURRENT_CONFIG } from '@/api/http/config';
import axios from 'axios';
import srs from '@/vendors/srs1.sdk.js';
import { message, Modal } from 'ant-design-vue';
import { getDeviceTopo } from '@/api/manage';
import {
  ReloadOutlined,
  SettingOutlined,
  VideoCameraOutlined,
  TeamOutlined,
  DashboardOutlined,
  PlayCircleOutlined,
  PoweroffOutlined,
  GlobalOutlined,
  ExclamationCircleOutlined,
  LinkOutlined,
  InfoCircleOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined
} from '@ant-design/icons-vue';

// SRS API URL configuration
const srsApiInput = ref('');
const connectionStatus = ref<'connected' | 'disconnected' | 'unknown'>('unknown');
const pingLatency = ref<number | null>(null);
const testingConnection = ref(false);

// Auto refresh settings
const autoRefreshInterval = ref<number>(5); // Default 5 seconds
const countdown = ref<number>(5);
let refreshTimer: any = null;
let countdownTimer: any = null;

// Raw SRS responses
const summaries = ref<any>(null);
const versions = ref<any>(null);
const streamsList = ref<any[]>([]);
const clientsList = ref<any[]>([]);
const loading = ref(false);

// Network bandwidth tracking variables
const lastSrsSample = ref<{ time: number; recv: number; send: number } | null>(null);
const currentKbps = ref({ recv: 0, send: 0 });

// Active Tab
const activeTab = ref('streams');

// Custom Confirm Modal State
const confirmModal = ref({
  visible: false,
  title: '',
  content: '',
  okText: '确认',
  cancelText: '取消',
  onOk: null as (() => Promise<void> | void) | null,
});

// Stream Preview Modal State
const previewModalVisible = ref(false);
const previewUrl = ref('');
const previewStreamInfo = ref<any>(null);
const previewVideoRef = ref<HTMLMediaElement | null>(null);
let srsPlayer: any = null;

// Search queries
const streamSearchQuery = ref('');
const clientSearchQuery = ref('');

const streamColumns = [
  { title: '流 ID', dataIndex: 'id', key: 'id', className: 'titleStyle' },
  { title: '应用 (App)', dataIndex: 'app', key: 'app', className: 'titleStyle' },
  { title: '流名称 (Stream)', dataIndex: 'name', key: 'name', className: 'titleStyle' },
  { title: '绑定设备', key: 'deviceName', align: 'center', className: 'titleStyle' },
  { title: '媒体编码格式', key: 'codec', className: 'titleStyle' },
  { title: '网络速率', key: 'kbps', align: 'center', className: 'titleStyle' },
  { title: '播放观众数', key: 'clients', align: 'center', className: 'titleStyle' },
  { title: '操作', key: 'action', align: 'right', className: 'titleStyle' }
];

const clientColumns = [
  { title: '客户端 ID', dataIndex: 'id', key: 'id', className: 'titleStyle' },
  { title: 'IP 地址', dataIndex: 'ip', key: 'ip', className: 'titleStyle' },
  { title: '连接类型', dataIndex: 'type', key: 'type', className: 'titleStyle' },
  { title: '绑定视频流 (Stream)', key: 'stream', className: 'titleStyle' },
  { title: '绑定设备', key: 'deviceName', align: 'center', className: 'titleStyle' },
  { title: '连接累计时长', key: 'duration', align: 'center', className: 'titleStyle' },
  { title: '网络数据 (发 / 收)', key: 'network', className: 'titleStyle' },
  { title: '管理操作', key: 'action', align: 'right', className: 'titleStyle' }
];

// Format SRS API URL for requests
const getSrsApiUrl = () => {
  let url = srsApiInput.value.trim();
  if (!url) {
    url = CURRENT_CONFIG.srsAPI ;
  }

  return url;
};

// Device SN to Name Map
const deviceNameMap = ref<Record<string, string>>({});

// Fetch device list and build SN -> Name mapping
const fetchDeviceNames = async () => {
  try {
    const workspaceId = localStorage.getItem('workspace_id');
    if (!workspaceId) return;
    const res = await getDeviceTopo(workspaceId);
    if (res && res.code === 0 && Array.isArray(res.data)) {
      const mapping: Record<string, string> = {};
      res.data.forEach((gateway: any) => {
        // Map gateway (e.g. Dock / RC)
        if (gateway?.device_sn) {
          mapping[gateway.device_sn] = gateway.nickname || gateway.device_name || gateway.device_sn;
        }
        
        // Map children (e.g. Drone)
        const child = gateway.children;
        if (child && child.device_sn) {
          mapping[child.device_sn] = child.nickname || child.device_name || child.device_sn;
        }
      });
      deviceNameMap.value = mapping;
    }
  } catch (err) {
    console.error('[SRS Manage] Failed to fetch device topology for name mapping:', err);
  }
};

const getDeviceNameByStreamName = (streamName: string) => {
  if (!streamName) return '未知设备';
  // Try exact match first
  if (deviceNameMap.value[streamName]) {
    return deviceNameMap.value[streamName];
  }
  // Try substring matching (e.g. SN is part of streamName)
  for (const sn in deviceNameMap.value) {
    if (streamName.includes(sn)) {
      return deviceNameMap.value[sn];
    }
  }
  // If no match, check if we can extract the prefix before '/'
  const parts = streamName.split('/');
  if (parts.length > 0) {
    const snPrefix = parts[0];
    if (deviceNameMap.value[snPrefix]) {
      return deviceNameMap.value[snPrefix];
    }
  }
  return '未绑定设备';
};

// Test connection to SRS API
const testConnection = async (showNotify = true) => {
  testingConnection.value = true;
  const startTime = Date.now();
  try {
    const apiUrl = getSrsApiUrl();
    const res = await axios.get(`${apiUrl}/api/v1/versions`, { timeout: 3000 });
    if (res.status === 200) {
      connectionStatus.value = 'connected';
      pingLatency.value = Date.now() - startTime;
      if (showNotify) {
        message.success('连接 SRS HTTP API 成功！');
      }
      return true;
    } else {
      connectionStatus.value = 'disconnected';
      pingLatency.value = null;
      return false;
    }
  } catch (error) {
    connectionStatus.value = 'disconnected';
    pingLatency.value = null;
    return false;
  } finally {
    testingConnection.value = false;
  }
};

// Fetch summaries, streams and clients from SRS
const fetchData = async (silent = false) => {
  if (loading.value) return;
  if (!silent) {
    loading.value = true;
  }
  const apiUrl = getSrsApiUrl();
  try {
    // 1. Fetch versions and summaries
    const [summaryRes, versionRes, streamsRes, clientsRes] = await Promise.all([
      axios.get(`${apiUrl}/api/v1/summaries`, { timeout: 3000 }).catch(() => null),
      axios.get(`${apiUrl}/api/v1/versions`, { timeout: 3000 }).catch(() => null),
      axios.get(`${apiUrl}/api/v1/streams/?count=3000`, { timeout: 3000 }).catch(() => null),
      axios.get(`${apiUrl}/api/v1/clients/?count=3000`, { timeout: 3000 }).catch(() => null)
    ]);

    if (summaryRes?.status === 200) {
      summaries.value = summaryRes.data;
      connectionStatus.value = 'connected';
      
      // Calculate real-time kbps from system network bytes
      const data = summaryRes.data?.data || summaryRes.data;
      const sys = data?.system;
      if (sys && sys.srs_sample_time && sys.srs_recv_bytes !== undefined && sys.srs_send_bytes !== undefined) {
        const currTime = sys.srs_sample_time;
        const currRecv = sys.srs_recv_bytes;
        const currSend = sys.srs_send_bytes;
        
        if (lastSrsSample.value) {
          const timeDiff = currTime - lastSrsSample.value.time;
          if (timeDiff > 0) {
            const recvDiff = currRecv - lastSrsSample.value.recv;
            const sendDiff = currSend - lastSrsSample.value.send;
            
            // kbps = (bytesDiff * 8) / timeDiff
            currentKbps.value.recv = Math.max(0, (recvDiff * 8) / timeDiff);
            currentKbps.value.send = Math.max(0, (sendDiff * 8) / timeDiff);
          }
        }
        
        // Update last sample
        lastSrsSample.value = {
          time: currTime,
          recv: currRecv,
          send: currSend
        };
      }
    }
    if (versionRes?.status === 200) {
      versions.value = versionRes.data;
    }
    
    // Parse streams
    if (streamsRes?.status === 200) {
      streamsList.value = streamsRes.data?.data?.streams || streamsRes.data?.streams || [];
    }

    // Parse clients
    if (clientsRes?.status === 200) {
      clientsList.value = clientsRes.data?.data?.clients || clientsRes.data?.clients || [];
    }
  } catch (error) {
    console.error('Fetch SRS details failed:', error);
    connectionStatus.value = 'disconnected';
  } finally {
    if (!silent) {
      loading.value = false;
    }
    resetCountdown();
  }
};

// Handle Manual Refresh
const handleRefresh = async () => {
  fetchDeviceNames();
  const ok = await testConnection(false);
  if (ok) {
    await fetchData();
  } else {
    message.error('无法连接到 SRS API，请检查地址或 SRS 服务器运行状态。');
  }
};

// Save custom API address to localStorage
const saveApiConfig = () => {
  localStorage.setItem('custom_srs_api', srsApiInput.value);
  message.success('保存 API 配置成功');
  handleRefresh();
};

// Reset API config to default from config.ts
const resetApiConfig = () => {
  srsApiInput.value = CURRENT_CONFIG.srsAPI || '';
  localStorage.removeItem('custom_srs_api');
  message.info('已恢复为默认配置');
  handleRefresh();
};

// Custom Confirmation Helper Functions
const showCustomConfirm = (options: {
  title: string;
  content: string;
  okText?: string;
  cancelText?: string;
  onOk: () => Promise<void> | void;
}) => {
  confirmModal.value = {
    visible: true,
    title: options.title,
    content: options.content,
    okText: options.okText || '确认',
    cancelText: options.cancelText || '取消',
    onOk: options.onOk,
  };
};

const handleCustomConfirmOk = async () => {
  const onOkCallback = confirmModal.value.onOk;
  confirmModal.value.visible = false;
  if (onOkCallback) {
    try {
      await onOkCallback();
    } catch (e) {
      console.error('Confirm callback failed:', e);
    }
  }
};

const handleCustomConfirmCancel = () => {
  confirmModal.value.visible = false;
};

// Kick Client
const handleKickClient = (clientId: string) => {
  showCustomConfirm({
    title: '确认断开连接吗？',
    content: `即将断开客户端 ID 为 [${clientId}] 的连接。`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      const apiUrl = getSrsApiUrl();
      try {
        const res = await axios.delete(`${apiUrl}/api/v1/clients/${clientId}`);
        if (res.status === 200 || res.data?.code === 0) {
          message.success(`已断开客户端 ${clientId}`);
          fetchData();
        } else {
          message.error('操作失败: ' + (res.data?.msg || '未知错误'));
        }
      } catch (err: any) {
        message.error('断开连接请求失败: ' + (err.message || '网络异常'));
      }
    }
  });
};

// Jump to clients table and filter by stream ID
const handleJumpToClients = (stream: any) => {
  if (!stream || !stream.id) return;
  clientSearchQuery.value = stream.id;
  activeTab.value = 'clients';
};

// Close Stream (Kick the stream publisher client ID)
const handleCloseStream = (stream: any) => {
  const publisherCid = stream.publish?.cid;
  if (!publisherCid) {
    message.warning('无法获取该流的推流客户端 ID，推流可能已处于离线状态');
    return;
  }
  showCustomConfirm({
    title: '确认终止推流吗？',
    content: `此操作将强制剔除该流的发布端 (客户端 ID: ${publisherCid})，视频流 [${stream.app}/${stream.name}] 将被关闭。`,
    okText: '确认终止',
    cancelText: '取消',
    onOk: async () => {
      const apiUrl = getSrsApiUrl();
      try {
        const res = await axios.delete(`${apiUrl}/api/v1/clients/${publisherCid}`);
        if (res.status === 200 || res.data?.code === 0) {
          message.success(`已强制关闭视频流: ${stream.name}`);
          fetchData();
        } else {
          message.error('关闭失败: ' + (res.data?.msg || '未知错误'));
        }
      } catch (err: any) {
        message.error('请求失败: ' + (err.message || '网络异常'));
      }
    }
  });
};

// Generate WHEP HTTP play URL
const getPlayUrl = (app: string, streamName: string) => {
  let apiUrl = getSrsApiUrl();
  if (apiUrl.startsWith('/')) {
    apiUrl = window.location.origin + apiUrl;
  }
  return `${apiUrl}/rtc/v1/whep/?app=${app}&stream=${streamName}`;
};

// Start WebRTC live stream preview in Modal
const handleStartPreview = (stream: any) => {
  previewStreamInfo.value = stream;
  const url = getPlayUrl(stream.app, stream.name);
  previewUrl.value = url;
  previewModalVisible.value = true;

  nextTick(() => {
    const video = previewVideoRef.value;
    if (!video) return;

    if (srsPlayer) {
      srsPlayer.close();
    }

    srsPlayer = new srs.SrsRtcWhipWhepAsync();
    video.srcObject = srsPlayer.stream;

    console.log('[SRS Play] Initializing WebRTC stream connection:', url);
    srsPlayer.play(url).then(() => {
      console.log('[SRS Play] WebRTC stream playing successfully!');
    }).catch((err: any) => {
      console.error('[SRS Play] WebRTC play failed:', err);
      message.error({
        content: 'WebRTC 连接失败。若 SRS 部署在公网，请确保 8000 (WebRTC UDP) 端口已开放。',
        duration: 5
      });
    });
  });
};

// Close preview and stop stream playback
const handleStopPreview = () => {
  if (srsPlayer) {
    srsPlayer.close();
    srsPlayer = null;
  }
  previewModalVisible.value = false;
  previewStreamInfo.value = null;
  previewUrl.value = '';
};

// Computed Stats from summary
const serverStats = computed(() => {
  const data = summaries.value?.data || summaries.value;
  const ver = versions.value?.data?.version || versions.value?.version || data?.self?.version || '未知';
  return {
    version: ver,
    pid: data?.self?.pid || '未知',
    uptime: data?.self?.srs_uptime || data?.self?.uptime || 0,
    cpu: data?.self?.cpu_percent !== undefined ? (data.self.cpu_percent * (data.self.cpu_percent <= 1 ? 100 : 1)).toFixed(1) : '0.0',
    mem: data?.self?.mem_kbyte ? (data.self.mem_kbyte / 1024).toFixed(1) : 0,
    kbpsRecv: currentKbps.value.recv,
    kbpsSend: currentKbps.value.send,
    connections: data?.system?.conn_srs || 0
  };
});

// Filter streams and clients based on search queries
const filteredStreams = computed(() => {
  if (!streamSearchQuery.value) return streamsList.value;
  const q = streamSearchQuery.value.toLowerCase();
  return streamsList.value.filter(s => 
    s.name.toLowerCase().includes(q) || 
    s.app.toLowerCase().includes(q) ||
    s.id.toLowerCase().includes(q)
  );
});

const filteredClients = computed(() => {
  if (!clientSearchQuery.value) return clientsList.value;
  const q = clientSearchQuery.value.toLowerCase();
  return clientsList.value.filter(c => 
    c.id.toLowerCase().includes(q) || 
    c.ip.toLowerCase().includes(q) || 
    c.type.toLowerCase().includes(q) ||
    (c.stream && c.stream.toLowerCase().includes(q))
  );
});

// Pagination states for performance
const streamPage = ref(1);
const streamPageSize = ref(10);
const clientPage = ref(1);
const clientPageSize = ref(10);

const paginatedStreams = computed(() => {
  const start = (streamPage.value - 1) * streamPageSize.value;
  return filteredStreams.value.slice(start, start + streamPageSize.value);
});

const paginatedClients = computed(() => {
  const start = (clientPage.value - 1) * clientPageSize.value;
  return filteredClients.value.slice(start, start + clientPageSize.value);
});

// Reset page to 1 on query changes
watch(streamSearchQuery, () => {
  streamPage.value = 1;
});
watch(clientSearchQuery, () => {
  clientPage.value = 1;
});

// Format Duration Utility
const formatDuration = (val: number, isMs = false) => {
  let seconds = isMs ? Math.floor(val / 1000) : Math.floor(val);
  if (seconds <= 0) return '0秒';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;

  let result = '';
  if (h > 0) result += `${h}小时`;
  if (m > 0) result += `${m}分`;
  if (s > 0 || result === '') result += `${s}秒`;
  return result;
};

// Format Server Uptime specifically to prevent layout overflow
const formatUptime = (seconds: number) => {
  if (seconds <= 0) return '0秒';
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  
  if (d > 0) {
    return `${d}天${h}小时`;
  }
  if (h > 0) {
    return `${h}小时${m}分`;
  }
  if (m > 0) {
    return `${m}分${s}秒`;
  }
  return `${s}秒`;
};

// Format Bytes/KBPS Utility
const formatKbps = (kbps: number) => {
  if (kbps < 1024) return `${kbps.toFixed(1)} kbps`;
  return `${(kbps / 1024).toFixed(2)} Mbps`;
};

const formatBytes = (bytes: number) => {
  if (bytes === undefined || bytes === null || bytes <= 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
};

// Reset countdown timer
const resetCountdown = () => {
  countdown.value = autoRefreshInterval.value;
};

// Setup Refresh timers
const startTimers = () => {
  stopTimers();
  
  if (autoRefreshInterval.value <= 0) return;
  
  resetCountdown();
  
  // Interval to perform actual fetch
  refreshTimer = setInterval(() => {
    fetchData(true);
  }, autoRefreshInterval.value * 1000);
  
  // Interval for visually updating the countdown progress
  countdownTimer = setInterval(() => {
    if (countdown.value > 1) {
      countdown.value--;
    } else {
      resetCountdown();
    }
  }, 1000);
};

const stopTimers = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
};

// Watchers
watch(autoRefreshInterval, () => {
  startTimers();
});

// Lifecycles
onMounted(async () => {
  const savedApi = localStorage.getItem('custom_srs_api');
  srsApiInput.value = savedApi || CURRENT_CONFIG.srsAPI || '';
  
  fetchDeviceNames();
  const connected = await testConnection(false);
  if (connected) {
    fetchData();
  }
  startTimers();
});

onUnmounted(() => {
  stopTimers();
  if (srsPlayer) {
    srsPlayer.close();
    srsPlayer = null;
  }
});
</script>

<template>
  <div class="page-root">
    
    <!-- Metrics Summary Cards Grid -->
    <div class="px-6 pt-4 mb-4 flex-shrink-0">
      <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        
        <!-- Card 1: Connection Status & Version -->
        <div class="summary-card">
          <div class="flex items-center justify-between text-slate-400 mb-1.5">
            <span class="text-xs font-bold">连接状态</span>
            <GlobalOutlined class="text-[#3b82f6]" />
          </div>
          <div class="flex items-baseline gap-2">
            <span 
              class="text-lg font-bold flex items-center gap-1.5" 
              :class="connectionStatus === 'connected' ? 'text-green-400' : 'text-red-400'"
            >
              <span class="w-2 h-2 rounded-full animate-pulse" :class="connectionStatus === 'connected' ? 'bg-green-400' : 'bg-red-400'"></span>
              {{ connectionStatus === 'connected' ? '已连接' : '未连接' }}
            </span>
            <span v-if="connectionStatus === 'connected' && pingLatency !== null" class="text-xs text-slate-500 font-mono">
              ({{ pingLatency }}ms)
            </span>
          </div>
          <div class="text-xs text-slate-500 flex justify-between border-t border-slate-700/60 pt-1 mt-1.5">
            <span>版本: v{{ serverStats.version }}</span>
            <span>PID: {{ serverStats.pid }}</span>
          </div>
        </div>

        <!-- Card 2: Network Ingress -->
        <div class="summary-card">
          <div class="flex items-center justify-between text-slate-400 mb-1.5">
            <span class="text-xs font-bold">网络流入带宽</span>
            <ArrowDownOutlined class="text-emerald-400" />
          </div>
          <div class="text-2xl font-bold text-white font-mono">{{ formatKbps(serverStats.kbpsRecv) }}</div>
          <div class="text-xs text-slate-500 border-t border-slate-700/60 pt-1 mt-1.5">推流端上传吞吐 (kbps_recv)</div>
        </div>

        <!-- Card 3: Network Egress -->
        <div class="summary-card">
          <div class="flex items-center justify-between text-slate-400 mb-1.5">
            <span class="text-xs font-bold">网络流出带宽</span>
            <ArrowUpOutlined class="text-blue-400" />
          </div>
          <div class="text-2xl font-bold text-white font-mono">{{ formatKbps(serverStats.kbpsSend) }}</div>
          <div class="text-xs text-slate-500 border-t border-slate-700/60 pt-1 mt-1.5">播放端分发吞吐 (kbps_send)</div>
        </div>

        <!-- Card 4: CPU Usage -->
        <div class="summary-card">
          <div class="flex items-center justify-between text-slate-400 mb-1">
            <span class="text-xs font-bold">CPU 使用率</span>
            <DashboardOutlined class="text-emerald-400" />
          </div>
          <div class="text-xl font-bold text-white font-mono">{{ serverStats.cpu }} %</div>
          <div class="w-full bg-slate-900/60 rounded-full h-1 overflow-hidden mt-1">
            <div class="bg-gradient-to-r from-emerald-500 to-teal-400 h-full rounded-full" :style="{ width: `${serverStats.cpu}%` }"></div>
          </div>
          <div class="text-xs text-slate-500 border-t border-slate-700/60 pt-1 mt-1">进程 CPU 负载率</div>
        </div>

        <!-- Card 5: Memory Usage -->
        <div class="summary-card">
          <div class="flex items-center justify-between text-slate-400 mb-1">
            <span class="text-xs font-bold">内存占用</span>
            <DashboardOutlined class="text-amber-400" />
          </div>
          <div class="text-xl font-bold text-white font-mono">{{ serverStats.mem }} MB</div>
          <div class="w-full bg-slate-900/60 rounded-full h-1 overflow-hidden mt-1">
            <div class="bg-gradient-to-r from-amber-500 to-orange-400 h-full rounded-full" :style="{ width: `${Math.min(100, (Number(serverStats.mem) / 1024) * 100)}%` }"></div>
          </div>
          <div class="text-xs text-slate-500 border-t border-slate-700/60 pt-1 mt-1">物理内存开销</div>
        </div>

        <!-- Card 6: Uptime & Operation Controls -->
        <div class="summary-card">
          <div class="flex items-center justify-between text-slate-400 mb-1">
            <span class="text-xs font-bold">运行时长 & 控制</span>
            <InfoCircleOutlined class="text-purple-400" />
          </div>
          <div class="text-sm font-bold text-white font-mono" :title="formatDuration(serverStats.uptime)">{{ formatUptime(serverStats.uptime) }}</div>
          <div class="flex items-center justify-between border-t border-slate-700/60 pt-1.5 mt-1.5">
            <div class="flex items-center gap-0.5">
              <a-select v-model:value="autoRefreshInterval" class="w-16 text-xs dark-select" :bordered="false" style="color: #60a5fa !important;">
                <a-select-option :value="0">关闭</a-select-option>
                <a-select-option :value="3">3s</a-select-option>
                <a-select-option :value="5">5s</a-select-option>
                <a-select-option :value="10">10s</a-select-option>
                <a-select-option :value="30">30s</a-select-option>
              </a-select>
              <span v-if="autoRefreshInterval > 0" class="font-mono text-blue-400 bg-blue-900/40 px-1 py-0.25 rounded border border-blue-800/30 text-[10px]">
                {{ countdown }}s
              </span>
            </div>
            <button class="action-btn action-btn--enable flex items-center gap-1 text-xs py-1" @click="handleRefresh">
              <ReloadOutlined class="text-[10px]" /> 刷新
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Tabs 导航 -->
    <div class="tabs-bar">
      <button :class="['tab-btn', { active: activeTab === 'streams' }]" @click="activeTab = 'streams'">
        <VideoCameraOutlined class="tab-icon" />
        活动流列表 ({{ streamsList.length }})
      </button>
      <button :class="['tab-btn', { active: activeTab === 'clients' }]" @click="activeTab = 'clients'">
        <TeamOutlined class="tab-icon" />
        在线客户端管理 ({{ clientsList.length }})
      </button>
    </div>

    <!-- Tab 1: Active Streams List -->
    <div v-show="activeTab === 'streams'" class="tab-panel">
      <!-- 顶部工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <h2 class="panel-title">活动视频流</h2>
          <span class="record-count">共 {{ filteredStreams.length }} 条</span>
        </div>
        <div class="toolbar-right">
          <input 
            v-model="streamSearchQuery"
            placeholder="搜索流 ID, 名称或应用App..."
            class="form-input"
            style="width: 250px"
          />
        </div>
      </div>

      <!-- 表格区域 -->
      <div class="table-card">
        <a-table 
          :columns="streamColumns" 
          :data-source="paginatedStreams" 
          :pagination="false" 
          :loading="loading"
          row-key="id" 
          :scroll="{ x: '100%', y: 'calc(100vh - 420px)' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'id'">
               <span class="font-mono text-slate-400 transition-colors">{{ record.id }}</span>
            </template>
            <template v-if="column.key === 'app'">
               <span class="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-bold bg-slate-800/80 text-slate-300 border border-slate-700/60 select-none">
                 {{ record.app }}
               </span>
            </template>
            <template v-if="column.key === 'name'">
               <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-blue-900/40 text-blue-400 border border-blue-500/20 shadow-[0_0_10px_rgba(59,130,246,0.1)] transition-all">
                 {{ record.name }}
               </span>
            </template>
            <template v-if="column.key === 'codec'">
               <div class="flex flex-col gap-1">
                 <div v-if="record.video" class="flex items-center gap-1.5 text-slate-300 text-xs">
                   <span class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                   <span class="font-bold text-xs text-slate-200">{{ record.video.codec }}</span>
                   <span class="text-slate-500 font-mono text-xs bg-slate-800/60 px-1.5 py-0.5 rounded border border-slate-700/40">{{ record.video.width }}x{{ record.video.height }}</span>
                   <span v-if="record.video.fps !== undefined && record.video.fps !== null" class="text-slate-400 text-xs font-mono">{{ record.video.fps.toFixed(0) }}fps</span>
                 </div>
                 <div v-if="record.audio" class="flex items-center gap-1.5 text-slate-400 text-xs">
                   <span class="font-semibold text-slate-400">{{ record.audio.codec }}</span>
                   <span class="text-slate-500 font-mono text-xs bg-slate-800/40 px-1.5 py-0.25 rounded border border-slate-700/20">{{ (record.audio.sample_rate / 1000).toFixed(1) }}kHz</span>
                 </div>
                 <div v-if="!record.video && !record.audio" class="text-slate-500 italic text-xs">无媒体数据</div>
               </div>
            </template>
            <template v-if="column.key === 'kbps'">
               <span class="font-mono font-bold text-emerald-400 text-xs">{{ record.kbps ? formatKbps(record.kbps.recv_30s) : '0.0 kbps' }}</span>
            </template>
            <template v-if="column.key === 'clients'">
               <span class="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold uppercase border transition-all cursor-pointer hover:bg-blue-800/40 hover:border-blue-400/40" 
                     :class="record.clients > 0 ? 'bg-blue-900/40 text-blue-400 border-blue-500/20 shadow-[0_0_8px_rgba(59,130,246,0.15)]' : 'bg-slate-800/60 text-slate-500 border-slate-700/40'"
                     @click="handleJumpToClients(record)">
                 <span class="w-1.5 h-1.5 rounded-full" :class="record.clients > 0 ? 'bg-blue-400 animate-pulse' : 'bg-slate-600'"></span>
                 {{ record.clients }} 观众
               </span>
            </template>
            <template v-if="column.key === 'deviceName'">
               <span class="inline-flex items-center px-3 py-1 rounded bg-[#1e293b]/90 border border-slate-600/60 text-slate-200 text-[13px] font-bold">
                 {{ getDeviceNameByStreamName(record.name) }}
               </span>
            </template>
            <template v-if="column.key === 'action'">
               <div class="action-btns" style="justify-content: flex-end;">
                 <button class="action-btn action-btn--enable" style="display: flex; align-items: center; gap: 4px;" @click="handleStartPreview(record)">
                   <PlayCircleOutlined /> 预览
                 </button>
                 <button class="action-btn action-btn--delete" style="display: flex; align-items: center; gap: 4px;" @click="handleCloseStream(record)">
                   <PoweroffOutlined /> 关闭流
                 </button>
               </div>
            </template>
          </template>
        </a-table>
        <div class="pagination-bar">
          <a-pagination v-model:current="streamPage" v-model:page-size="streamPageSize" :total="filteredStreams.length" :show-total="(total: number) => `共 ${total} 条记录`" show-size-changer />
        </div>
      </div>
    </div>

    <!-- Tab 2: Connected Clients List -->
    <div v-show="activeTab === 'clients'" class="tab-panel clients-panel">
      <!-- 顶部工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <h2 class="panel-title">在线客户端</h2>
          <span class="record-count">共 {{ filteredClients.length }} 条</span>
        </div>
        <div class="toolbar-right">
          <input 
            v-model="clientSearchQuery"
            placeholder="搜索客户端 ID, IP, 连接类型或流名..."
            class="form-input"
            style="width: 250px"
          />
        </div>
      </div>

      <!-- 表格区域 -->
      <div class="table-card">
        <a-table 
          :columns="clientColumns" 
          :data-source="paginatedClients" 
          :pagination="false" 
          :loading="loading"
          row-key="id" 
          :scroll="{ x: '100%', y: 'calc(100vh - 420px)' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'id'">
               <span class="font-mono text-[#3b82f6] font-bold">{{ record.id }}</span>
            </template>
            <template v-if="column.key === 'ip'">
               <span class="font-mono text-slate-300">{{ record.ip }}</span>
            </template>
            <template v-if="column.key === 'type'">
               <span class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-extrabold uppercase border select-none transition-all"
                     :class="record.type.includes('publish') ? 'bg-emerald-900/40 text-emerald-400 border-emerald-500/20 shadow-[0_0_8px_rgba(16,185,129,0.15)]' : 'bg-blue-900/40 text-blue-400 border-blue-500/20 shadow-[0_0_8px_rgba(59,130,246,0.15)]'">
                 <span class="w-1.5 h-1.5 rounded-full animate-pulse" :class="record.type.includes('publish') ? 'bg-emerald-400' : 'bg-blue-400'"></span>
                 {{ record.type }}
               </span>
            </template>
            <template v-if="column.key === 'stream'">
               <span v-if="record.stream" class="inline-flex items-center px-2 py-0.5 rounded bg-slate-800/60 border border-slate-700 text-slate-400 font-mono text-xs">
                 {{ record.app }}/{{ record.stream }}
               </span>
               <span v-else class="text-slate-500 italic text-xs">无</span>
            </template>
            <template v-if="column.key === 'deviceName'">
               <span v-if="record.stream" class="inline-flex items-center px-3 py-1 rounded bg-[#1e293b]/90 border border-slate-600/60 text-slate-200 text-[13px] font-bold">
                 {{ getDeviceNameByStreamName(record.name) }}
               </span>
               <span v-else class="text-slate-500 italic text-xs">无</span>
            </template>
            <template v-if="column.key === 'duration'">
               <span class="text-slate-300 font-mono text-xs">{{ formatDuration(record.alive) }}</span>
            </template>
            <template v-if="column.key === 'network'">
               <div class="flex flex-col gap-0.5 text-xs font-mono">
                 <div class="flex items-center gap-1.5 text-slate-300">
                   <span class="text-blue-400 font-bold">↑</span>
                   <span class="text-slate-200 font-bold">{{ record.kbps ? formatKbps(record.kbps.send_30s) : '0.0 kbps' }}</span>
                   <span class="text-slate-500">({{ formatBytes(record.send_bytes) }})</span>
                 </div>
                 <div class="flex items-center gap-1.5 text-slate-300">
                   <span class="text-emerald-400 font-bold">↓</span>
                   <span class="text-slate-200 font-bold">{{ record.kbps ? formatKbps(record.kbps.recv_30s) : '0.0 kbps' }}</span>
                   <span class="text-slate-500">({{ formatBytes(record.recv_bytes) }})</span>
                 </div>
               </div>
            </template>
            <template v-if="column.key === 'action'">
               <div class="action-btns" style="justify-content: flex-end;">
                 <button class="action-btn action-btn--delete" @click="handleKickClient(record.id)">断开连接</button>
               </div>
            </template>
          </template>
        </a-table>
        <div class="pagination-bar">
          <a-pagination v-model:current="clientPage" v-model:page-size="clientPageSize" :total="filteredClients.length" :show-total="(total: number) => `共 ${total} 条记录`" show-size-changer />
        </div>
      </div>
    </div>

    <!-- WebRTC Live Stream Preview Modal -->
    <a-modal 
      v-model:visible="previewModalVisible" 
      :title="`视频流预览: ${previewStreamInfo?.app}/${previewStreamInfo?.name}`"
      :footer="null" 
      :destroy-on-close="true"
      width="720px"
      class="srs-preview-modal"
      @cancel="handleStopPreview"
    >
      <div class="bg-black border border-slate-700 rounded-lg overflow-hidden relative aspect-video flex flex-col justify-center items-center">
        <!-- HTML5 Video element -->
        <video 
          ref="previewVideoRef" 
          autoplay 
          controls 
          muted 
          playsinline 
          class="w-full h-full object-contain"
        ></video>
      </div>
    </a-modal>

    <!-- Custom Dark Confirmation Modal Overlay -->
    <transition name="fade">
      <div v-if="confirmModal.visible" class="custom-confirm-overlay" @click.self="handleCustomConfirmCancel">
        <div class="custom-confirm-card">
          <div class="custom-confirm-header">
            <ExclamationCircleOutlined class="custom-confirm-icon" />
            <h3 class="custom-confirm-title">{{ confirmModal.title }}</h3>
          </div>
          <div class="custom-confirm-body">
            {{ confirmModal.content }}
          </div>
          <div class="custom-confirm-footer">
            <button class="custom-confirm-btn custom-confirm-btn--cancel" @click="handleCustomConfirmCancel">
              {{ confirmModal.cancelText }}
            </button>
            <button class="custom-confirm-btn custom-confirm-btn--ok" @click="handleCustomConfirmOk">
              {{ confirmModal.okText }}
            </button>
          </div>
        </div>
      </div>
    </transition>

  </div>
</template>

<style scoped lang="scss">
/* ========== Design Tokens ========== */
$bg-page: #0a101e;
$bg-card: #111827;
$bg-surface: #1e293b;
$bg-hover: rgba(255, 255, 255, 0.04);
$border: rgba(255, 255, 255, 0.08);
$border-focus: #3b82f6;
$text-primary: rgba(255, 255, 255, 0.92);
$text-secondary: rgba(255, 255, 255, 0.60);
$text-muted: rgba(255, 255, 255, 0.38);
$accent: #3b82f6;
$accent-hover: #2563eb;
$radius-sm: 6px;
$radius-md: 10px;
$radius-lg: 14px;
$transition: 200ms ease;

/* ========== Page Root ========== */
.page-root {
    height: 100%;
    background: $bg-page;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

/* ========== Summary Cards ========== */
.summary-card {
    background: $bg-card;
    border: 1px solid $border;
    border-radius: $radius-lg;
    padding: 12px 16px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    height: 140px;
    transition: all $transition;
    
    &:hover {
        border-color: rgba(255, 255, 255, 0.2);
    }
}

/* ========== Tabs Bar ========== */
.tabs-bar {
    display: flex;
    gap: 4px;
    padding: 0 24px;
    border-bottom: 1px solid $border;
    background: $bg-card;
    flex-shrink: 0;
}

.tab-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 20px;
    font-size: 13px;
    font-weight: 500;
    color: $text-secondary;
    background: transparent;
    border: none;
    border-bottom: 2px solid transparent;
    cursor: pointer;
    transition: color $transition, border-color $transition, background $transition;
    margin-bottom: -1px;

    &:hover {
        color: $text-primary;
        background: $bg-hover;
    }

    &.active {
        color: $accent;
        border-bottom-color: $accent;
    }
}

.tab-icon {
    width: 16px;
    height: 16px;
}

/* ========== Tab Panel ========== */
.tab-panel {
    flex: 1;
    overflow: auto;
    padding: 20px 24px;
}

/* ========== Toolbar ========== */
.toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
}

.toolbar-left {
    display: flex;
    align-items: baseline;
    gap: 12px;
}

.panel-title {
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
    margin: 0;
}

.record-count {
    font-size: 12px;
    color: $text-muted;
    background: $bg-surface;
    padding: 2px 10px;
    border-radius: 999px;
}

.toolbar-right {
    display: flex;
    gap: 10px;
}

.form-input {
    width: 100%;
    padding: 8px 14px;
    font-size: 13px;
    color: $text-primary;
    background: $bg-surface;
    border: 1px solid $border;
    border-radius: $radius-sm;
    outline: none;
    transition: border-color $transition, box-shadow $transition;

    &::placeholder {
        color: $text-muted;
    }

    &:focus {
        border-color: $border-focus;
        box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
    }
}

/* ========== Action Buttons ========== */
.action-btns {
    display: flex;
    gap: 4px;
}

.action-btn {
    padding: 3px 12px;
    font-size: 12px;
    border-radius: $radius-sm;
    border: none;
    cursor: pointer;
    transition: all $transition;
    font-weight: 500;

    &--enable {
        background: rgba(52, 211, 153, 0.12);
        color: #34d399;

        &:hover {
            background: rgba(52, 211, 153, 0.22);
        }
    }

    &--delete {
        background: rgba(248, 113, 113, 0.10);
        color: #f87171;

        &:hover {
            background: rgba(248, 113, 113, 0.20);
        }
    }
}

/* ========== Table Card ========== */
.table-card {
    background: $bg-card;
    border-radius: $radius-lg;
    border: 1px solid $border;
    overflow: hidden;
}

.pagination-bar {
    padding: 14px 20px;
    border-top: 1px solid $border;
    display: flex;
    justify-content: flex-end;
}

/* ========== Ant Design Dark Overrides ========== */
:deep(.ant-table) {
    background: transparent !important;
    color: $text-primary;
}

:deep(.ant-table-thead > tr > th) {
    background: $bg-surface !important;
    color: $text-secondary !important;
    border-bottom: 1px solid $border !important;
    font-weight: 600;
    font-size: 13px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    padding: 12px 16px !important;
}

:deep(.ant-table-tbody > tr > td) {
    background: #111827 !important;
    border-bottom: 1px solid $border !important;
    color: $text-primary;
    padding: 12px 16px !important;
    font-size: 14px;
}

:deep(.ant-table-tbody > tr:hover > td),
:deep(.ant-table-tbody > tr.ant-table-row-hover > td),
:deep(.ant-table-tbody > tr > td.ant-table-cell-row-hover) {
    background: #1f2937 !important;
}

:deep(.ant-table-tbody > tr:last-child > td) {
    border-bottom: none !important;
}

:deep(.ant-table-header) {
    border: none !important;
}

:deep(.ant-table-cell-scrollbar) {
    box-shadow: none !important;
}

:deep(.ant-pagination-item) {
    background: transparent !important;
    border-color: $border !important;

    a {
        color: $text-secondary !important;
    }
}

:deep(.ant-pagination-item-active) {
    border-color: $accent !important;
    background: rgba(59, 130, 246, 0.12) !important;

    a {
        color: $accent !important;
    }
}

:deep(.ant-pagination-prev button),
:deep(.ant-pagination-next button) {
    color: $text-secondary !important;
    background: transparent !important;
}

:deep(.ant-pagination-total-text) {
    color: $text-muted !important;
    font-size: 12px;
}

:deep(.ant-pagination-options) {
    .ant-select-selector {
        background: $bg-surface !important;
        border-color: $border !important;
        color: $text-secondary !important;
    }
}

:deep(.ant-spin-container) {
    &::after {
        background: $bg-card !important;
    }
}

:deep(.dark-select) {
  .ant-select-selector {
    color: #94a3b8 !important;
    font-size: 12px !important;
    background: transparent !important;
    border: none !important;
  }
  .ant-select-arrow {
    color: #64748b !important;
  }
}

// Global modal background styling for Dark Theme
:deep(.srs-preview-modal) {
  .ant-modal-content {
    background-color: #0a101e !important;
    border: 1px solid rgba(255, 255, 255, 0.08) !important;
    border-radius: 12px !important;
    box-shadow: 0 24px 48px rgba(0, 0, 0, 0.4) !important;
  }
  .ant-modal-header {
    background-color: #111827 !important;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08) !important;
    padding: 16px 24px !important;
  }
  .ant-modal-title {
    color: rgba(255, 255, 255, 0.92) !important;
    font-size: 15px !important;
    font-weight: 600 !important;
  }
  .ant-modal-close {
    color: rgba(255, 255, 255, 0.38) !important;
    &:hover {
      color: white !important;
    }
  }
  .ant-modal-body {
    padding: 24px !important;
  }
}

/* ========== Custom Confirm Modal ========== */
.custom-confirm-overlay {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.65);
    backdrop-filter: blur(4px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 2000;
}

.custom-confirm-card {
    background: #111827;
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 12px;
    width: 420px;
    max-width: 90%;
    padding: 24px;
    box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5), 0 10px 10px -5px rgba(0, 0, 0, 0.4);
    animation: confirmScaleIn 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes confirmScaleIn {
    from {
        transform: scale(0.95);
        opacity: 0;
    }
    to {
        transform: scale(1);
        opacity: 1;
    }
}

.custom-confirm-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
}

.custom-confirm-icon {
    font-size: 22px;
    color: #ef4444; /* Alert red */
}

.custom-confirm-title {
    color: #ffffff;
    font-size: 16px;
    font-weight: 600;
    margin: 0;
}

.custom-confirm-body {
    color: #94a3b8;
    font-size: 14px;
    line-height: 1.6;
    margin-bottom: 24px;
    padding-left: 34px;
}

.custom-confirm-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
}

.custom-confirm-btn {
    padding: 8px 18px;
    border-radius: 6px;
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.15s ease;
    border: none;
    outline: none;
}

.custom-confirm-btn--cancel {
    background: rgba(255, 255, 255, 0.06);
    color: #d6e5fb;
    border: 1px solid rgba(255, 255, 255, 0.08);
    
    &:hover {
        background: rgba(255, 255, 255, 0.1);
        color: #ffffff;
        border-color: rgba(255, 255, 255, 0.2);
    }
}

.custom-confirm-btn--ok {
    background: #ef4444;
    color: #ffffff;
    
    &:hover {
        background: #dc2626;
    }
}
</style>
