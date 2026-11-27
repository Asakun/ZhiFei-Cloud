<script setup lang="ts">
import { onMounted, ref, computed, watch } from "vue";
import { getBindingDevices } from "@/api/manage";
import {
  getFlightTotalStatistics,
  getFlightStatisticsByDate,
  getFlightRanking,
  FlightTotalStatistics,
  FlightStatisticsByDate,
  FlightRankingItem
} from "@/api/wayline";
import { ELocalStorageKey } from "@/types";
import { IPage } from "@/api/http/type";
import dayjs from "dayjs";
// ECharts 相关导入
import * as echarts from 'echarts/core';
import { LineChart, BarChart, PieChart } from 'echarts/charts';
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import VChart from 'vue-echarts';

// 注册 ECharts 组件
echarts.use([
  LineChart,
  BarChart,
  PieChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  CanvasRenderer
]);

const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ""

// 机场列表
const dockList = ref<any[]>([])

// 加载状态
const loading = ref(false)

// 日期范围选择（null 表示查询全部）
const dateRange = ref<[dayjs.Dayjs, dayjs.Dayjs] | null>([
  dayjs().subtract(30, 'day'),
  dayjs()
])

// 统计数据
const totalStats = ref<FlightTotalStatistics>({
  total_distance: 0,
  total_count: 0,
  total_time: 0
})

// 按日期的统计数据
const statsByDate = ref<FlightStatisticsByDate[]>([])

// 飞行排名数据（按机场）
const rankingByDock = ref<FlightRankingItem[]>([])

// 飞行排名数据（按用户）
const rankingByUser = ref<FlightRankingItem[]>([])

// ============== ECharts 图表配置 ==============

// 台账统计折线图配置
const trendChartOption = computed(() => {
  const dateSet = new Set<string>()
  statsByDate.value.forEach(item => dateSet.add(item.stat_date))
  const dates = Array.from(dateSet).sort()

  // 创建日期到数据的映射
  const flightMap = new Map(statsByDate.value.map(item => [item.stat_date, item]))

  // 生成各个系列的数据
  const timeData = dates.map(date => {
    const item = flightMap.get(date)
    return item ? parseFloat((item.total_time / 3600).toFixed(2)) : 0 // 转换为小时
  })
  const distanceData = dates.map(date => {
    const item = flightMap.get(date)
    return item ? parseFloat((item.total_distance / 1000).toFixed(2)) : 0 // 转换为公里
  })
  const countData = dates.map(date => {
    const item = flightMap.get(date)
    return item ? item.total_count : 0
  })

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(0, 0, 0, 0.7)',
      borderColor: 'rgba(255, 255, 255, 0.2)',
      textStyle: { color: '#fff' }
    },
    legend: {
      data: ['时长(h)', '里程(km)', '架次'],
      textStyle: { color: 'rgba(255, 255, 255, 0.8)' },
      top: 10
    },
    grid: {
      left: '100px',
      right: '100px',
      bottom: '10px', // 增加底部边距
      top: '30px',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLabel: {
        color: 'rgba(255, 255, 255, 0.6)',
        interval: 0, // 强制显示所有标签
        hideOverlap: true // 防止重叠
      },
      axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.2)' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: 'rgba(255, 255, 255, 0.6)' },
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.1)' } }
    },
    series: [
      {
        name: '时长(h)',
        type: 'line',
        smooth: true,
        data: timeData,
        lineStyle: { color: '#00d4ff', width: 2 },
        itemStyle: { color: '#00d4ff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(0, 212, 255, 0.3)' },
            { offset: 1, color: 'rgba(0, 212, 255, 0.05)' }
          ])
        }
      },
      {
        name: '里程(km)',
        type: 'line',
        smooth: true,
        data: distanceData,
        lineStyle: { color: '#00ee8b', width: 2 },
        itemStyle: { color: '#00ee8b' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(0, 238, 139, 0.3)' },
            { offset: 1, color: 'rgba(0, 238, 139, 0.05)' }
          ])
        }
      },
      {
        name: '架次',
        type: 'line',
        smooth: true,
        data: countData,
        lineStyle: { color: '#ffc107', width: 2 },
        itemStyle: { color: '#ffc107' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(255, 193, 7, 0.3)' },
            { offset: 1, color: 'rgba(255, 193, 7, 0.05)' }
          ])
        }
      }
    ]
  }
})

// 机场飞行统计饼图配置
const dockPieChartOption = computed(() => {
  const data = rankingByDock.value.map(item => ({
    name: item.nickname || item.dock_sn,
    value: item.total_count
  }))

  return {
    backgroundColor: 'transparent',
    // 显式禁用坐标轴，防止默认显示
    xAxis: { show: false },
    yAxis: { show: false },
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(0, 0, 0, 0.7)',
      borderColor: 'rgba(255, 255, 255, 0.2)',
      textStyle: { color: '#fff' },
      formatter: '{b}: {c} 架次 ({d}%)'
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { color: 'rgba(255, 255, 255, 0.8)' },
      pageIconColor: '#00ee8b',
      pageIconInactiveColor: '#555',
      pageTextStyle: { color: '#fff' }
    },
    series: [
      {
        name: '机场飞行统计',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: 'rgba(0, 0, 0, 0.3)',
          borderWidth: 2
        },
        label: {
          show: true,
          position: 'center',
          formatter: () => {
            const total = data.reduce((sum, item) => sum + item.value, 0)
            return `{total|${total}}\n{label|架次总数}`
          },
          rich: {
            total: {
              fontSize: 28,
              fontWeight: 'bold',
              color: '#00d4ff'
            },
            label: {
              fontSize: 14,
              color: 'rgba(255, 255, 255, 0.6)',
              padding: [5, 0, 0, 0]
            }
          }
        },
        emphasis: {
          label: { show: true }
        },
        labelLine: { show: false },
        data: data,
        color: ['#00d4ff', '#00ee8b', '#ffc107', '#ff5722', '#9c27b0', '#3f51b5']
      }
    ]
  }
})

// 格式化飞行时长
const formatFlightTime = (seconds: number) => {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = Math.floor(seconds % 60)
  if (hours > 0) {
    return `${hours}时${minutes}分${secs}秒`
  } else if (minutes > 0) {
    return `${minutes}分${secs}秒`
  }
  return `${secs}秒`
}

// 格式化飞行里程
const formatDistance = (meters: number) => {
  if (meters >= 1000) {
    return `${(meters / 1000).toFixed(2)} km`
  }
  return `${meters.toFixed(0)} m`
}

// 加载所有机场
const loadDocks = async () => {
  try {
    // domain=3 表示获取机场数据
    const res = await getBindingDevices(workspaceId, { page: 1, page_size: 100 } as IPage, 3)
    if (res.code === 0 && res.data?.list) {
      dockList.value = res.data.list
    }
  } catch (error) {
    console.error('加载机场列表失败:', error)
  }
}

// 加载统计数据
const loadStatistics = async () => {
  loading.value = true

  try {
    // 如果日期范围为空，则不传时间参数（查询全部）
    let startTime: number | undefined
    let endTime: number | undefined

    if (dateRange.value && dateRange.value.length === 2) {
      startTime = dateRange.value[0].startOf('day').valueOf()
      endTime = dateRange.value[1].endOf('day').valueOf()
    }

    // 并行请求飞行统计接口
    const [totalRes, byDateRes, rankByDockRes, rankByUserRes] = await Promise.all([
      getFlightTotalStatistics(workspaceId, startTime, endTime),
      getFlightStatisticsByDate(workspaceId, startTime, endTime),
      getFlightRanking(workspaceId, startTime, endTime, 'DOCK_SN', 'TOTAL_COUNT'),
      getFlightRanking(workspaceId, startTime, endTime, 'USERNAME', 'TOTAL_COUNT')
    ])

    // 总统计
    if (totalRes.code === 0 && totalRes.data) {
      totalStats.value = totalRes.data
    }

    // 按日期统计
    if (byDateRes.code === 0 && byDateRes.data) {
      statsByDate.value = byDateRes.data
    }

    // 按机场排名
    if (rankByDockRes.code === 0 && rankByDockRes.data) {
      rankingByDock.value = rankByDockRes.data
    }

    // 按用户排名
    if (rankByUserRes.code === 0 && rankByUserRes.data) {
      rankingByUser.value = rankByUserRes.data
    }

  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 日期范围变化时重新加载
const onDateRangeChange = () => {
  loadStatistics()
}

// 快捷日期选择
const setDateRange = (days: number | null) => {
  if (days === null) {
    // 清空日期，查询全部
    dateRange.value = null
  } else {
    dateRange.value = [
      dayjs().subtract(days, 'day'),
      dayjs()
    ]
  }
  loadStatistics()
}

onMounted(async () => {
  // 加载机场列表
  await loadDocks()
  // 加载统计数据
  await loadStatistics()
})
</script>

<template>
  <div class="settings-container">
    <a-spin :spinning="loading">


      <!-- 日期选择区域 -->
      <div class="date-filter-section">
        <div class="date-picker-wrapper">
          <span class="filter-label">统计时间范围：</span>
          <a-range-picker v-model:value="dateRange" @change="onDateRangeChange" :allow-clear="true" format="YYYY-MM-DD"
            placeholder="['开始日期', '结束日期']" />
        </div>
        <div class="quick-buttons">
          <a-button size="small" type="primary" ghost @click="setDateRange(null)">全部</a-button>
          <a-button size="small" @click="setDateRange(7)">近7天</a-button>
          <a-button size="small" @click="setDateRange(30)">近30天</a-button>
          <a-button size="small" @click="setDateRange(90)">近90天</a-button>
          <a-button size="small" @click="setDateRange(365)">近一年</a-button>
        </div>
      </div>

      <!-- 统计卡片 -->
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon dock-icon">🏢</div>
          <div class="stat-content">
            <div class="stat-value">{{ dockList.length }}</div>
            <div class="stat-label">机场总数</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon sorties-icon">✈️</div>
          <div class="stat-content">
            <div class="stat-value">{{ totalStats.total_count }}</div>
            <div class="stat-label">总飞行架次</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon time-icon">⏱️</div>
          <div class="stat-content">
            <div class="stat-value">{{ formatFlightTime(totalStats.total_time) }}</div>
            <div class="stat-label">总飞行时长</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon distance-icon">📍</div>
          <div class="stat-content">
            <div class="stat-value">{{ formatDistance(totalStats.total_distance) }}</div>
            <div class="stat-label">总飞行里程</div>
          </div>
        </div>
      </div>
      <!-- 主内容区域：A组（图表）和 B组（排行榜）左右布局 -->
      <!-- 主内容区域：顶部三列，底部一列 -->
      <div class="main-content-section">
        <!-- 顶部区域：机场飞行分布 + 机场排名 + 用户排名 -->
        <div class="top-section">
          <!-- 机场飞行饼图 -->
          <div class="chart-card">
            <h3 class="chart-title">🏢 机场飞行分布</h3>
            <div class="chart-container chart-container-pie">
              <v-chart class="chart" :option="dockPieChartOption" autoresize />
            </div>
          </div>

          <!-- 机场飞行排行 -->
          <div class="ranking-card">
            <h3 class="ranking-title">🏢 机场飞行排行榜</h3>
            <div class="ranking-list">
              <div v-for="(item, index) in rankingByDock" :key="item.dock_sn" class="ranking-item">
                <div class="rank-badge" :class="`rank-${index + 1}`">{{ index + 1 }}</div>
                <div class="rank-info">
                  <div class="rank-name">{{ item.nickname }}</div>
                  <div class="rank-sn">{{ item.dock_sn }}</div>
                </div>
                <div class="rank-value">
                  <div class="value-primary">{{ item.total_count }} 架次</div>
                  <div class="value-secondary">{{ formatDistance(item.total_distance) }} | {{
                    formatFlightTime(item.total_time) }}</div>
                </div>
              </div>
              <div v-if="rankingByDock.length === 0" class="empty-state">暂无数据</div>
            </div>
          </div>

          <!-- 用户飞行排行 -->
          <div class="ranking-card">
            <h3 class="ranking-title">👤 用户飞行排行榜</h3>
            <div class="ranking-list">
              <div v-for="(item, index) in rankingByUser" :key="item.username" class="ranking-item">
                <div class="rank-badge" :class="`rank-${index + 1}`">{{ index + 1 }}</div>
                <div class="rank-info">
                  <div class="rank-name">{{ item.username }}</div>
                  <div class="rank-sn">{{ item.nickname || '-' }}</div>
                </div>
                <div class="rank-value">
                  <div class="value-primary">{{ item.total_count }} 架次</div>
                  <div class="value-secondary">{{ formatDistance(item.total_distance) }} | {{
                    formatFlightTime(item.total_time) }}</div>
                </div>
              </div>
              <div v-if="rankingByUser.length === 0" class="empty-state">暂无数据</div>
            </div>
          </div>
        </div>

        <!-- 底部区域：台账统计 -->
        <div class="bottom-section">
          <div class="chart-card">
            <h3 class="chart-title">📊 台账统计</h3>
            <div class="chart-container">
              <v-chart class="chart" :option="trendChartOption" autoresize />
            </div>
          </div>
        </div>
      </div>
    </a-spin>
  </div>
</template>

<style scoped lang="scss">
// 全屏自适应容器
// 全屏自适应容器
.settings-container {
  // padding: 24px; // 移除外边距，改在内部控制，或者保留但要计算 box-sizing
  padding: 8px;
  background: linear-gradient(135deg, #0a1929 0%, #041428 50%, #002658 100%);
  height: 100vh; // 强制全屏高度
  color: #fff;
  box-sizing: border-box;
  overflow: hidden; // 禁止出现滚动条
  display: flex;
  flex-direction: column;

  // 穿透 a-spin 容器样式，确保高度传递
  :deep(.ant-spin-nested-loading) {
    flex: 1;
    min-height: 0; // 关键：结合 flex: 1 使用，避免被内容撑开

    .ant-spin-container {
      height: 100%;
      display: flex;
      flex-direction: column;
    }
  }
}

// 日期选择区域样式
.date-filter-section {
  flex-shrink: 0; // 不缩小
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 6px 10px; // 进一步减小 padding
  margin-bottom: 6px; // 进一步减小间距
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;

  .date-picker-wrapper {
    display: flex;
    align-items: center;
    gap: 12px;

    .filter-label {
      color: rgba(255, 255, 255, 0.8);
      font-size: 14px;
      white-space: nowrap;
    }
  }

  .quick-buttons {
    display: flex;
    gap: 8px;

    :deep(.ant-btn) {
      background: rgba(0, 238, 139, 0.1);
      border-color: rgba(0, 238, 139, 0.3);
      color: #00ee8b;

      &:hover {
        background: rgba(0, 238, 139, 0.2);
        border-color: rgba(0, 238, 139, 0.5);
      }
    }
  }

  // 日期选择器样式覆盖
  :deep(.ant-picker) {
    background: rgba(0, 0, 0, 0.3);
    border-color: rgba(255, 255, 255, 0.2);

    .ant-picker-input>input {
      color: #fff;
    }

    .ant-picker-suffix,
    .ant-picker-separator {
      color: rgba(255, 255, 255, 0.5);
    }

    &:hover {
      border-color: rgba(0, 238, 139, 0.5);
    }
  }
}

.stats-grid {
  flex-shrink: 0; // 不缩小
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px; // 进一步减小 gap
  margin-bottom: 6px; // 进一步减小间距

  @media (max-width: 1400px) {
    grid-template-columns: repeat(3, 1fr);
  }

  @media (max-width: 768px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 600px) {
    grid-template-columns: 1fr;
  }
}

.stat-card {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 8px; // 进一步减小 padding
  display: flex;
  align-items: center;
  gap: 12px;
  transition: all 0.3s ease;

  .stat-icon {
    width: 28px; // 进一步减小图标
    height: 28px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    background: rgba(255, 255, 255, 0.1);

    &.dock-icon {
      background: rgba(0, 212, 255, 0.1);
      color: #00d4ff;
    }

    &.sorties-icon {
      background: rgba(0, 238, 139, 0.1);
      color: #00ee8b;
    }

    &.time-icon {
      background: rgba(255, 193, 7, 0.1);
      color: #ffc107;
    }

    &.distance-icon {
      background: rgba(255, 87, 34, 0.1);
      color: #ff5722;
    }

    &.event-icon {
      background: rgba(255, 152, 0, 0.1);
      color: #ff9800;
    }
  }

  // 事件卡片样式
  &.event-card {
    border-color: rgba(255, 152, 0, 0.2);

    .stat-content .stat-value {
      color: #ff9800;
    }

    &:hover {
      box-shadow: 0 8px 32px rgba(255, 152, 0, 0.2);
      border-color: rgba(255, 152, 0, 0.3);
    }
  }

  .stat-content {
    flex: 1;

    .stat-value {
      font-size: 16px; // 进一步减小字体
      font-weight: bold;
      color: #fff;
      font-family: 'D-DIN', sans-serif;
    }

    .stat-label {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.6);
      margin-top: 2px;
    }
  }

  &:hover {
    transform: translateY(-2px);
    background: rgba(255, 255, 255, 0.1);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
  }
}

// ============== 主内容区域样式（上下布局） ==============
.main-content-section {
  flex: 1; // 占据剩余所有高度
  min-height: 0; // 关键：允许子元素高度小于内容高度，防止溢出
  display: flex;
  flex-direction: column; // 上下布局
  gap: 8px;
  overflow: hidden; // 防止撑开
}

// 顶部区域：三列并排
.top-section {
  flex: 2; // 占据上方较大比例
  display: grid;
  grid-template-columns: 1fr 1fr 1fr; // 三等分
  gap: 8px;
  min-height: 0; // 允许收缩
}

// 底部区域：单列
.bottom-section {
  flex: 3; // 占据下方较小比例
  display: flex;
  flex-direction: column;
  min-height: 0; // 允许收缩
}

.chart-card {
  flex: 0.8; // 平分垂直空间
  min-height: 0; // 允许收缩
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px; // 减小圆角
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 8px; // 进一步减小 padding
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;

  &:hover {
    border-color: rgba(0, 238, 139, 0.3);
    box-shadow: 0 4px 24px rgba(0, 238, 139, 0.1);
  }

  .chart-title {
    flex-shrink: 0; // 标题不收缩
    font-size: 14px; // 减小标题字体
    font-weight: bold;
    color: #fff;
    margin-bottom: 4px; // 减小标题下边距
    padding-bottom: 4px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  }

  .chart-container,
  .chart-container-pie {
    flex: 1; // 自动填充剩余高度
    height: auto; // 移除固定高度
    min-height: 80px; // 减小最小高度
    width: 100%;
  }

  .chart {
    width: 100%;
    height: 100%;
  }
}

.ranking-card {
  flex: 1; // 平分垂直空间
  min-height: 0; // 允许收缩
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 8px;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;

  &:hover {
    border-color: rgba(0, 238, 139, 0.3);
    box-shadow: 0 4px 24px rgba(0, 238, 139, 0.1);
  }

  .ranking-title {
    flex-shrink: 0;
    font-size: 14px; // 减小标题字体
    font-weight: bold;
    margin-bottom: 4px;
    color: #fff;
    padding-bottom: 4px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  }
}

.ranking-list {
  flex: 1; // 自动填充剩余高度
  display: flex;
  flex-direction: column;
  gap: 4px; // 进一步减小列表间距
  max-height: none; // 移除最大高度限制
  overflow-y: auto;
  min-height: 0;
}

.ranking-item {
  display: flex;
  align-items: center;
  padding: 6px 8px; // 大幅减小列表项 padding
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px; // 减小圆角
  gap: 8px;
  transition: all 0.3s ease;

  &:hover {
    background: rgba(0, 238, 139, 0.1);
    transform: translateX(4px);
  }

  .rank-badge {
    width: 24px; // 减小排名图标
    height: 24px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: bold;
    font-size: 12px;
    background: rgba(255, 255, 255, 0.1);
    color: #fff;

    &.rank-1 {
      background: linear-gradient(135deg, #ffd700, #ffaa00);
      color: #000;
      box-shadow: 0 2px 8px rgba(255, 215, 0, 0.4);
    }

    &.rank-2 {
      background: linear-gradient(135deg, #c0c0c0, #a0a0a0);
      color: #000;
      box-shadow: 0 2px 8px rgba(192, 192, 192, 0.4);
    }

    &.rank-3 {
      background: linear-gradient(135deg, #cd7f32, #a0522d);
      color: #fff;
      box-shadow: 0 2px 8px rgba(205, 127, 50, 0.4);
    }
  }

  .rank-info {
    flex: 1;

    .rank-name {
      font-size: 16px;
      font-weight: 500;
      color: #fff;
    }

    .rank-sn {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.5);
      font-family: monospace;
      margin-top: 2px;
    }
  }

  .rank-value {
    text-align: right;

    .value-primary {
      font-size: 14px; // 减小数值字体
      font-weight: bold;
      color: #00d4ff;
    }

    .value-secondary {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.5);
      margin-top: 2px;
    }
  }
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: rgba(255, 255, 255, 0.5);
}
</style>
