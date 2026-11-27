<template>
  <!-- 只有在 video-webrtc 存在时才渲染 -->
  <div v-show="false" class="absolute pointer-events-none" style="z-index: 10;">
    <canvas id="canvas-webrtc"></canvas>
  </div>


  <div class=" h-[100vh] w-full flex flex-col bg-gray-900">
    <div class="h-10 bg-gray-800 border-b border-gray-700 flex items-center justify-between px-6 relative z-[50]">
      <div class="flex items-center gap-4 flex-shrink-0">
        <!-- <h1 class="text-white text-lg font-semibold">虚拟座舱驾驶系统</h1> -->
        <span class="text-gray-200 text-sm font-semibold">{{ deviceInfo.device?.device_name || model || '在线设备' }}</span>
        <!-- 当前操作人员 -->
        <span v-if="payloadAuthorityOwner"
          class="text-xs px-2 py-0.5 rounded bg-green-600/20 border border-green-500/30 text-green-400">
          <span class="text-gray-400">操作员:</span> {{ payloadAuthorityOwner }}
        </span>

        <a-button type="primary" size="small" @click="toggleFullScreenFlight" class="ml-2">
          {{ isFullScreenFlight ? '退出全屏' : '全屏模拟飞行' }}
        </a-button>
        <!-- 航线按钮及下拉列表 -->
        <div class="relative">
          <a-button size="small" @click="handleWaylineClick">航线</a-button>
          <!-- 航线列表弹窗 -->
          <div v-if="waylineVisible"
            class="absolute top-full left-0 mt-2 z-[10000] bg-gray-900 rounded-xl shadow-2xl border border-gray-600 w-[350px] max-h-[50vh] flex flex-col">
            <!-- 头部 -->
            <div class="flex justify-between items-center px-4 py-2 bg-gray-800 border-b border-gray-700 rounded-t-xl">
              <span class="text-white text-sm font-bold flex items-center gap-2">
                <span>🛫</span>
                航线列表
                <span v-if="waylineList.length" class="text-gray-400 text-xs">({{ waylineList.length }} 条)</span>
              </span>
              <div class="cursor-pointer text-gray-400 hover:text-white transition-colors p-1"
                @click="waylineVisible = false">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </div>
            </div>

            <!-- 内容 -->
            <div class="flex-1 overflow-y-auto p-2">
              <!-- 加载中 -->
              <div v-if="waylineLoading" class="flex items-center justify-center py-6">
                <div class="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-500"></div>
                <span class="ml-2 text-gray-400 text-sm">加载中...</span>
              </div>

              <!-- 空数据 -->
              <div v-else-if="waylineList.length === 0" class="flex flex-col items-center justify-center py-6">
                <span class="text-2xl mb-2">📭</span>
                <span class="text-gray-400 text-sm">暂无航线数据</span>
              </div>

              <!-- 航线列表 -->
              <div v-else class="space-y-1">
                <div v-for="wayline in waylineList" :key="wayline.id"
                  class="bg-gray-800 rounded-lg px-3 py-2 border border-gray-700 hover:border-blue-500 transition-all">
                  <div class="flex justify-between items-center">
                    <span class="text-blue-400 font-medium text-sm flex-1 cursor-pointer"
                      @click="selectWayline(wayline)">{{ wayline.name }}</span>
                    <div class="flex items-center gap-2">
                      <a-button size="small" type="primary" :loading="waylineExecutingId === wayline.id"
                        @click.stop="executeWayline(wayline)">
                        执行
                      </a-button>
                      <a-button size="small" type="primary" ghost :loading="waylineDrawingId === wayline.id"
                        @click.stop="drawWaylineOnMap(wayline)">
                        绘制
                      </a-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>


        <!-- HMS 按钮及下拉列表 -->
        <div class="relative">
          <a-button size="small" @click="handleHmsClick" :danger="hmsList.length > 0">
            <template #icon v-if="hmsList.length > 0">
              <span class="mr-1">⚠️</span>
            </template>
            HMS {{ hmsList.length > 0 ? `(${hmsList.length})` : '' }}
          </a-button>

          <!-- HMS 列表弹窗 -->
          <div v-if="hmsVisible" style="display: flex; flex-direction: column;"
            class="absolute top-full left-0 mt-2 z-[10000] bg-gray-900 rounded-xl shadow-2xl border border-gray-600 w-[400px] max-h-[60vh] overflow-hidden">
            <!-- 头部 -->
            <div
              class="flex justify-between items-center px-4 py-2 bg-gray-800 border-b border-gray-700 rounded-t-xl shrink-0">
              <span class="text-white text-sm font-bold flex items-center gap-2">
                <span>🛡️</span>
                设备健康管理 (HMS)
                <span v-if="hmsList.length" class="text-gray-400 text-xs">({{ hmsList.length }} 条)</span>
              </span>
              <div class="cursor-pointer text-gray-400 hover:text-white transition-colors p-1"
                @click="hmsVisible = false">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </div>
            </div>

            <!-- 内容 -->
            <div class="flex-1 overflow-y-auto p-2 min-h-0">
              <!-- 加载中 -->
              <div v-if="hmsLoading" class="flex items-center justify-center py-6">
                <div class="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-500"></div>
                <span class="ml-2 text-gray-400 text-sm">加载中...</span>
              </div>

              <!-- 空数据 -->
              <div v-else-if="hmsList.length === 0" class="flex flex-col items-center justify-center py-6">
                <span class="text-2xl mb-2">✅</span>
                <span class="text-gray-400 text-sm">设备运行正常</span>
              </div>

              <!-- HMS 列表 -->
              <div v-else class="space-y-1">
                <div v-for="hms in hmsList" :key="hms.hms_id"
                  class="bg-gray-800 rounded-lg px-3 py-2 border border-gray-700 hover:border-blue-500 transition-all">
                  <div class="flex gap-2 items-start">
                    <!-- 等级点 -->
                    <div class="mt-1.5 w-2 h-2 rounded-full flex-shrink-0" :class="getHmsLevelBgColor(hms.level)"></div>

                    <div class="flex-1 min-w-0">
                      <div class="flex items-center mb-1 gap-2">
                        <div class="font-bold text-xs truncate flex-1" :class="getHmsLevelColor(hms.level)"
                          :title="hms.code || hms.key">
                          {{ hms.code || hms.key || 'Unknown' }}
                        </div>
                        <div class="text-gray-500 text-[10px] whitespace-nowrap flex-shrink-0">{{ hms.update_time ||
                          hms.create_time }}</div>
                      </div>
                      <div class="text-xs text-gray-300 break-words">
                        {{ hms.message_zh || hms.message_en }}
                      </div>
                      <div class="flex justify-between items-center mt-1">
                        <span class="text-[10px] px-1.5 py-0.5 rounded bg-gray-700 text-gray-400">
                          {{ hms.domain === EDeviceTypeName.Aircraft ? '飞行器' : (hms.domain === EDeviceTypeName.Dock ?
                            '机场' : '其他')
                          }}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 底部操作 -->
            <div class="px-4 py-2 border-t border-gray-700 bg-gray-800/50 flex justify-end shrink-0">
              <a-button size="small" type="link" @click="fetchHmsData">刷新</a-button>
            </div>
          </div>
        </div>
      </div>
      <div class="flex items-center gap-3 flex-shrink-0">
        <!-- 响应延迟 -->
        <a-tooltip title="机场指令最新响应延迟">
          <span v-if="dockCmdResponseTime !== null" class="flex items-center gap-1 px-1.5 py-0.5 rounded bg-gray-800/80 border border-gray-700/50">
            <span class="text-[10px] text-gray-400 scale-95">机场延迟:</span>
            <span class="text-xs font-bold" :class="getResponseTimeClass(dockCmdResponseTime)">
              {{ dockCmdResponseTime }}ms
            </span>
          </span>
        </a-tooltip>

        <a-tooltip title="负载指令最新响应延迟">
          <span v-if="payloadCmdResponseTime !== null" class="flex items-center gap-1 px-1.5 py-0.5 rounded bg-gray-800/80 border border-gray-700/50">
            <span class="text-[10px] text-gray-400 scale-95">负载延迟:</span>
            <span class="text-xs font-bold" :class="getResponseTimeClass(payloadCmdResponseTime)">
              {{ payloadCmdResponseTime }}ms
            </span>
          </span>
        </a-tooltip>

        <!-- AI状态 -->
        <a-tooltip title="AI开启状态">
          <span class="cursor-help flex items-center gap-1"
            :class="aiStatusSuccess === 2 ? 'text-green-400' : aiStatusSuccess === 3 ? 'text-red-400' : 'text-gray-400'">
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none">
              <!-- 外框圆角矩形 -->
              <rect x="2" y="4" width="20" height="16" rx="3" stroke="currentColor" stroke-width="1.5" fill="none" />
              <!-- AI文字 -->
              <text x="12" y="15" text-anchor="middle" fill="currentColor" font-size="9" font-weight="bold"
                font-family="Arial, sans-serif">AI</text>
              <!-- 装饰电路点 -->
              <circle cx="5" cy="7" r="1" fill="currentColor" />
              <circle cx="19" cy="7" r="1" fill="currentColor" />
              <circle cx="5" cy="17" r="1" fill="currentColor" />
              <circle cx="19" cy="17" r="1" fill="currentColor" />
              <!-- 顶部信号线 -->
              <path d="M9 4V2M12 4V1M15 4V2" stroke="currentColor" stroke-width="1" stroke-linecap="round" />
            </svg>
            <span class="text-xs">{{ aiStatusSuccess === 1 ? '开启中' : aiStatusSuccess === 2 ? '成功' : aiStatusSuccess ===
              3 ? '失败' : '未开启' }}</span>
          </span>
        </a-tooltip>

        <!-- 无人机状态 -->
        <a-tooltip title="无人机状态">
          <span class="cursor-help flex items-center gap-1 text-cyan-400">
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12,12m-3,0a3,3,0,1,0,6,0a3,3,0,1,0,-6,0" />
              <path d="M4.5,4.5L7.5,7.5M19.5,4.5L16.5,7.5M4.5,19.5L7.5,16.5M19.5,19.5L16.5,16.5" stroke="currentColor"
                stroke-width="2" stroke-linecap="round" />
              <circle cx="4.5" cy="4.5" r="2" />
              <circle cx="19.5" cy="4.5" r="2" />
              <circle cx="4.5" cy="19.5" r="2" />
              <circle cx="19.5" cy="19.5" r="2" />
            </svg>
            <span class="text-xs text-gray-300">{{ (EModeWord as any)[EModeCode[deviceStatus]] }}</span>
          </span>
        </a-tooltip>
        <!-- 无人机已搭载负载 -->
        <div class="relative" v-if="dronePayloadList.length > 0">
          <div
            class="flex items-center gap-1 px-2 py-1 bg-gray-800/80 rounded-lg border cursor-pointer transition-colors"
            :class="payloadListVisible ? 'border-purple-500/50 bg-purple-900/20' : 'border-gray-600/30 hover:border-purple-500/30'"
            @click="payloadListVisible = !payloadListVisible">
            <svg class="w-4 h-4 text-purple-400" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              stroke-width="1.5">
              <rect x="4" y="3" width="16" height="18" rx="2" />
              <path d="M9 3V1M15 3V1" stroke-linecap="round" />
              <circle cx="12" cy="11" r="3" />
              <path d="M8 17h8" stroke-linecap="round" />
            </svg>
            <span class="text-xs text-purple-300">负载({{ dronePayloadList.length }})</span>
          </div>
          <div v-if="payloadListVisible"
            class="absolute top-full right-0 mt-2 z-[1000] bg-gray-900 rounded-xl shadow-2xl border border-gray-600 w-[280px] overflow-hidden">
            <div class="flex items-center justify-between px-3 py-2 border-b border-gray-700">
              <span class="text-sm text-white font-medium">已搭载负载</span>
              <div class="cursor-pointer text-gray-400 hover:text-white transition-colors p-0.5"
                @click="payloadListVisible = false">
                <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </div>
            </div>
            <div class="max-h-[40vh] overflow-y-auto p-2 space-y-2 custom-scrollbar">
              <div v-for="payload in dronePayloadList" :key="payload.id"
                class="bg-gray-800 rounded-lg p-2.5 border border-gray-700">
                <div class="flex items-center justify-between mb-1.5">
                  <span class="text-sm font-medium text-white">{{ payload.psdk_name }}</span>
                  <span class="text-[10px] px-1.5 py-0.5 rounded-full"
                    :class="payload.status === 1 ? 'bg-green-500/20 text-green-400 border border-green-500/30' : 'bg-gray-600/20 text-gray-400 border border-gray-500/30'">
                    {{ payload.status === 1 ? '在线' : '离线' }}
                  </span>
                </div>
                <div class="grid grid-cols-2 gap-x-3 gap-y-1 text-[11px]">
                  <div><span class="text-gray-500">SN: </span><span class="text-gray-300">{{ payload.psdk_sn }}</span>
                  </div>
                  <div><span class="text-gray-500">索引: </span><span class="text-gray-300">{{ payload.psdk_index
                  }}</span>
                  </div>
                  <div class="col-span-2"><span class="text-gray-500">版本: </span><span class="text-gray-300">{{
                    payload.psdk_version }}</span></div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 显示网络质量数据，数据来源deviceInfo.device?.position_state?.quality 最高5 最低1，越高越好 -->
        <div v-if="deviceInfo.device?.position_state?.quality !== undefined" title="网络质量"
          class="flex items-center gap-1 px-2 py-1 bg-gray-800/80 rounded-lg border border-gray-600/30">
          <svg class="w-4 h-4" viewBox="0 0 24 24" fill="currentColor"
            :class="deviceInfo.device?.position_state?.quality >= 4 ? 'text-green-400' : (deviceInfo.device?.position_state?.quality >= 2 ? 'text-yellow-400' : 'text-red-400')">
            <!-- 信号条 1 -->
            <rect x="2" y="16" width="3" height="6" rx="1"
              :fill-opacity="deviceInfo.device?.position_state?.quality >= 1 ? 1 : 0.3" />
            <!-- 信号条 2 -->
            <rect x="6" y="13" width="3" height="9" rx="1"
              :fill-opacity="deviceInfo.device?.position_state?.quality >= 2 ? 1 : 0.3" />
            <!-- 信号条 3 -->
            <rect x="10" y="10" width="3" height="12" rx="1"
              :fill-opacity="deviceInfo.device?.position_state?.quality >= 3 ? 1 : 0.3" />
            <!-- 信号条 4 -->
            <rect x="14" y="7" width="3" height="15" rx="1"
              :fill-opacity="deviceInfo.device?.position_state?.quality >= 4 ? 1 : 0.3" />
            <!-- 信号条 5 -->
            <rect x="18" y="4" width="3" height="18" rx="1"
              :fill-opacity="deviceInfo.device?.position_state?.quality >= 5 ? 1 : 0.3" />
          </svg>
          <span class="text-xs font-bold"
            :class="deviceInfo.device?.position_state?.quality >= 4 ? 'text-green-400' : (deviceInfo.device?.position_state?.quality >= 2 ? 'text-yellow-400' : 'text-red-400')">
            {{ deviceInfo.device?.position_state?.quality }}
          </span>
        </div>

        <!-- 遥控器电量 -->
        <div v-if="deviceInfo.dock?.basic_osd?.capacity_percent !== undefined" title="遥控器电量"
          class="flex items-center gap-1 px-2 py-1 bg-gray-800/80 rounded-lg border border-gray-600/30 ml-2">
          <svg class="w-3.5 h-3.5 text-green-400" viewBox="0 0 24 24" fill="currentColor">
            <path d="M17 4h-3V2h-4v2H7c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 18H7V6h10v16z" />
          </svg>
          <span class="text-xs font-bold text-green-400">RC: {{ deviceInfo.dock.basic_osd.capacity_percent }}%</span>
        </div>

        <!-- 遥控器图传链路状态 wireless_link -->
        <div v-if="deviceInfo.dock?.link_osd?.wireless_link"
          class="flex items-center gap-1 px-3 py-1 bg-gray-800/80 rounded-lg border border-gray-600/30 ml-2">
          <!-- 链路模式: 1=4G融合, 0=SDR -->
          <div class="flex items-center"
            :title="deviceInfo.dock.link_osd.wireless_link.link_workmode === 1 ? '4G融合模式' : 'SDR模式'">
            <span class="text-xs font-bold"
              :class="deviceInfo.dock.link_osd.wireless_link.link_workmode === 1 ? 'text-blue-400' : 'text-green-400'">
              {{ deviceInfo.dock.link_osd.wireless_link.link_workmode === 1 ? 'MIX' : 'SDR' }}
            </span>
          </div>

          <!-- Divider -->
          <div class="w-[1px] h-3 bg-gray-600"></div>

          <!-- SDR 信号 -->
          <div class="flex items-center gap-1" title="SDR链路">
            <span class="text-[10px] uppercase text-gray-400">SDR</span>
            <div class="flex items-center">
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="currentColor" :class="deviceInfo.dock.link_osd.wireless_link.sdr_link_state === 1
                ? (deviceInfo.dock.link_osd.wireless_link.sdr_quality >= 4 ? 'text-green-400' : (deviceInfo.dock.link_osd.wireless_link.sdr_quality >= 2 ? 'text-yellow-400' : 'text-red-400'))
                : 'text-gray-600'">
                <rect x="2" y="16" width="3" height="6" rx="1"
                  :fill-opacity="deviceInfo.dock.link_osd.wireless_link.sdr_quality >= 1 ? 1 : 0.3" />
                <rect x="6" y="13" width="3" height="9" rx="1"
                  :fill-opacity="deviceInfo.dock.link_osd.wireless_link.sdr_quality >= 2 ? 1 : 0.3" />
                <rect x="10" y="10" width="3" height="12" rx="1"
                  :fill-opacity="deviceInfo.dock.link_osd.wireless_link.sdr_quality >= 3 ? 1 : 0.3" />
                <rect x="14" y="7" width="3" height="15" rx="1"
                  :fill-opacity="deviceInfo.dock.link_osd.wireless_link.sdr_quality >= 4 ? 1 : 0.3" />
                <rect x="18" y="4" width="3" height="18" rx="1"
                  :fill-opacity="deviceInfo.dock.link_osd.wireless_link.sdr_quality >= 5 ? 1 : 0.3" />
              </svg>
            </div>
          </div>

          <!-- 4G 信号 -->
          <div class="flex items-center gap-1" title="4G链路">
            <span class="text-[10px] uppercase text-gray-400">4G</span>
            <div class="flex items-center">
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="currentColor" :class="deviceInfo.dock.link_osd.wireless_link['4g_link_state'] === 1
                ? (deviceInfo.dock.link_osd.wireless_link['4g_quality'] >= 4 ? 'text-green-400' : (deviceInfo.dock.link_osd.wireless_link['4g_quality'] >= 2 ? 'text-yellow-400' : 'text-red-400'))
                : 'text-gray-600'">
                <rect x="2" y="16" width="3" height="6" rx="1"
                  :fill-opacity="deviceInfo.dock.link_osd.wireless_link['4g_quality'] >= 1 ? 1 : 0.3" />
                <rect x="6" y="13" width="3" height="9" rx="1"
                  :fill-opacity="deviceInfo.dock.link_osd.wireless_link['4g_quality'] >= 2 ? 1 : 0.3" />
                <rect x="10" y="10" width="3" height="12" rx="1"
                  :fill-opacity="deviceInfo.dock.link_osd.wireless_link['4g_quality'] >= 3 ? 1 : 0.3" />
                <rect x="14" y="7" width="3" height="15" rx="1"
                  :fill-opacity="deviceInfo.dock.link_osd.wireless_link['4g_quality'] >= 4 ? 1 : 0.3" />
                <rect x="18" y="4" width="3" height="18" rx="1"
                  :fill-opacity="deviceInfo.dock.link_osd.wireless_link['4g_quality'] >= 5 ? 1 : 0.3" />
              </svg>
            </div>
          </div>
        </div>

        <div class="text-sm" v-if="playUrl">
          <div class="relative">
            <div @click="showQualityMenu = !showQualityMenu"
              class="cursor-pointer flex items-center justify-center w-6 h-6 rounded hover:bg-gray-700 transition-colors">
              <svg-icon name="setting" />
            </div>
            <div v-if="showQualityMenu"
              class="absolute right-0 top-full mt-2 w-24 bg-gray-800 border border-gray-700 rounded-md shadow-xl overflow-hidden z-[99]">
              <div class="flex flex-col py-1">
                <div
                  class="px-4 py-2 text-gray-300 text-xs hover:bg-gray-700 hover:text-white cursor-pointer transition-colors"
                  @click="onUpdateQuality(0); showQualityMenu = false;">自动</div>
                <div
                  class="px-4 py-2 text-gray-300 text-xs hover:bg-gray-700 hover:text-white cursor-pointer transition-colors"
                  @click="onUpdateQuality(1); showQualityMenu = false;">流畅</div>
                <div
                  class="px-4 py-2 text-gray-300 text-xs hover:bg-gray-700 hover:text-white cursor-pointer transition-colors"
                  @click="onUpdateQuality(2); showQualityMenu = false;">标清</div>
                <div
                  class="px-4 py-2 text-gray-300 text-xs hover:bg-gray-700 hover:text-white cursor-pointer transition-colors"
                  @click="onUpdateQuality(3); showQualityMenu = false;">高清</div>
                <div
                  class="px-4 py-2 text-gray-300 text-xs hover:bg-gray-700 hover:text-white cursor-pointer transition-colors"
                  @click="onUpdateQuality(4); showQualityMenu = false;">超清</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div ref="telemetryContainerRef"
      class="w-full h-8 flex items-center justify-between overflow-hidden bg-gradient-to-r from-gray-900 to-gray-800 border-b border-gray-700 relative z-[40] px-2">
      <!-- 左侧：设备属性设置 & MQTT 重置按钮 -->
      <div class="flex items-center gap-1.5 flex-shrink-0 mr-2">
        <!-- 设备属性设置按钮 -->
        <a-button type="primary" ghost size="small" @click="dronePropsModalVisible = true" class="h-6 text-xs px-2 flex items-center gap-1">
          <template #icon><SlidersOutlined /></template>
          属性设置
        </a-button>

        <!-- MQTT 重置按钮 -->
        <a-popconfirm
          title="确定要重置当前设备的 MQTT 连接吗？"
          ok-text="确定"
          cancel-text="取消"
          placement="bottom"
          @confirm="resetMqtt"
        >
          <a-tooltip title="踢除当前设备在 MQTT Broker 中的连接以触发重新连接">
            <a-button size="small" :loading="mqttResetLoading" class="h-6 text-xs px-2 flex items-center gap-1">
              <template #icon><ReloadOutlined /></template>
              MQTT重置
            </a-button>
          </a-tooltip>
        </a-popconfirm>
      </div>

      <!-- 中间：遥测数据走马灯 (在线时展示遥测信息，离线时展示状态提示) -->
      <div v-if="droneOnlineStatus === 1" class="telemetry-marquee flex-1 min-w-0">
        <div ref="telemetryContentRef" :class="['telemetry-content', { 'telemetry-scrolling': needsScroll }]">
          <a-tooltip title="电量"><span class="text-green-400 font-bold cursor-help flex items-center gap-1">
              <svg class="w-3 h-3" viewBox="0 0 24 24" fill="currentColor">
                <path
                  d="M17 4h-3V2h-4v2H7c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 18H7V6h10v16z" />
              </svg>
              {{ deviceInfo.device?.battery?.capacity_percent }}%
            </span></a-tooltip>
          <!-- <a-tooltip title="剩余飞行时间"><span class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-amber-400" viewBox="0 0 24 24" fill="currentColor">
                <path
                  d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10 10-4.5 10-10S17.5 2 12 2zm4.2 14.2L11 13V7h1.5v5.2l4.5 2.7-.8 1.3z" />
              </svg>
              {{ deviceInfo.device?.battery?.remain_flight_time }}s
            </span></a-tooltip> -->
          <a-tooltip title="RTK卫星数"><span class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-teal-400" viewBox="0 0 24 24" fill="currentColor">
                <path
                  d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z" />
              </svg>
              {{ deviceInfo.device?.position_state?.rtk_number }}
            </span></a-tooltip>
          <a-tooltip title="相对高度"><span class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-sky-400" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2L4 12h3v8h10v-8h3L12 2zm0 3l5 6h-2v7H9v-7H7l5-6z" />
              </svg>
              {{ deviceInfo.device?.elevation }}m
            </span></a-tooltip>
          <a-tooltip title="绝对高度"><span class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-cyan-400" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2L4 12h3v8h10v-8h3L12 2z" />
              </svg>
              {{ Number(deviceInfo.device?.height).toFixed(1) }}m
            </span></a-tooltip>
          <a-tooltip title="距地高度（仅供参考，实际高度可能有偏差）"><span class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-emerald-400" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2L4 12h3v8h10v-8h3L12 2z" />
                <path d="M4 22h16v2H4z" />
              </svg>
              {{ distanceToGround.toFixed(1) }}m
            </span></a-tooltip>
          <a-tooltip title="俯仰角"><span class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-orange-400" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 4L4 20h16L12 4zm0 4l5 10H7l5-10z" />
              </svg>
              {{ deviceInfo.device?.attitude_pitch }}°
            </span></a-tooltip>
          <a-tooltip title="横滚角"><span class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-pink-400" viewBox="0 0 24 24" fill="currentColor">
                <path
                  d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93z" />
              </svg>
              {{ deviceInfo.device?.attitude_roll }}°
            </span></a-tooltip>
          <a-tooltip title="风速"><span class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-blue-400" viewBox="0 0 24 24" fill="currentColor">
                <path
                  d="M12.5 2C9.46 2 7 4.46 7 7.5c0 .75.17 1.45.5 2.08L2 16v6h6l6.5-5.5c.63.33 1.33.5 2.08.5 3.04 0 5.5-2.46 5.5-5.5S19.54 6 16.5 6c-.75 0-1.45.17-2.08.5L12.5 2z" />
              </svg>
              {{ (deviceInfo.device?.wind_speed / 10).toFixed(1) }}m/s
            </span></a-tooltip>
          <a-tooltip title="风向"><span class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-purple-400" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z" />
              </svg>
              {{ WindDirectionEnum[deviceInfo.device?.wind_direction] }}
            </span></a-tooltip>
          <a-tooltip title="水平速度"><span class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-green-400" viewBox="0 0 24 24" fill="currentColor">
                <path d="M2 12l4-4v3h12V8l4 4-4 4v-3H6v3l-4-4z" />
              </svg>
              {{ Number(deviceInfo.device?.horizontal_speed).toFixed(1) }}m/s
            </span></a-tooltip>
          <a-tooltip title="垂直速度"><span class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-red-400" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2L8 6h3v12H8l4 4 4-4h-3V6h3L12 2z" />
              </svg>
              {{ deviceInfo.device?.vertical_speed }}m/s
            </span></a-tooltip>
          <a-tooltip title="距机场"><span class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-yellow-400" viewBox="0 0 24 24" fill="currentColor">
                <path
                  d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z" />
              </svg>
              {{ Number(deviceInfo.device?.home_distance).toFixed(0) }}m
            </span></a-tooltip>
          <a-tooltip title="返航预留电量" v-if="returnHomeBattery !== undefined"><span
              class="text-white cursor-help flex items-center gap-1">
              <svg class="w-3 h-3 text-amber-400" viewBox="0 0 24 24" fill="currentColor">
                <path
                  d="M15.67 4H14V2h-4v2H8.33C7.6 4 7 4.6 7 5.33v15.33C7 21.4 7.6 22 8.33 22h7.33c.74 0 1.34-.6 1.34-1.33V5.33C17 4.6 16.4 4 15.67 4z" />
              </svg>
              {{ returnHomeBattery }}%
            </span></a-tooltip>
        </div>
      </div>
      <div v-else class="flex-1 flex items-center justify-center text-xs text-gray-500">
        无人机未上线
      </div>

      <!-- 手动飞行打点与快速生成航线组件 (集成在遥测信息栏右侧) -->
      <div class="flex-shrink-0 ml-2 flex items-center">
        <CockpitWaypointRecorder
          ref="waypointRecorderRef"
          :osdInfo="mergedOsdInfo"
          :dockMapLocation="dockMapLocation"
          :dockSn="dockSn"
          :dockName="dockName"
          :droneType="deviceInfo.device?.drone_type"
          :payloadType="deviceInfo.device?.payload_type"
          :cameraPayloadIndex="cameraPayloadIndex"
          :videoElement="videowebrtc"
          @update:waylinePoints="onRecordedWaypointsUpdate"
          @fly-to-waypoint="onFlyToRecordedWaypoint"
        />
      </div>
    </div>

    <div class="flex-1 flex gap-1 overflow-hidden">
      <div v-show="!isFullScreenFlight" class="w-1/3 bg-gray-800 rounded-lg overflow-hidden relative">
        <!-- 机场实况直播已移至顶部导航栏以防全屏时被隐藏 -->

        <div class="h-[98%]">
          <DjiCesium ref="djiCesiumRef" @viewer-ready="onViewerReady" :sceneMode="3" :loadTerrain="true" :dockMapLocation="dockMapLocation" :osdInfo="mergedOsdInfo"
            :dockMap="stableDockMap" :waylinePoints="displayWaylinePoints" :markedPoint="markedPoint"
            :flightPathPoints="flyToPointProgress.plannedPathPoints"></DjiCesium>
        </div>
      </div>
      <div class="flex-1 flex flex-col gap-1">
        <div class="bg-gray-800 rounded-lg overflow-hidden relative w-full"
          :class="isFullScreenFlight ? 'flex-1 h-full' : 'max-h-[65%]'"
          :style="isFullScreenFlight ? {} : { aspectRatio: '16/9' }">
          <div class="absolute inset-0">
            <!-- 视频播放器组件 -->
            <div class="video-container h-full w-full" ref="videoContainer" style="position: relative;">
              <!-- 手持端直播手动测试按钮 -->
              <div class="absolute top-4 right-4 z-40 flex items-center gap-2">
                <button 
                  @click="triggerLivestream" 
                  class="px-3 py-1.5 text-xs font-bold text-white bg-green-600/90 hover:bg-green-500 rounded border border-green-500/30 shadow-lg backdrop-blur-sm transition-all active:scale-95 cursor-pointer flex items-center gap-1">
                  <span class="w-1.5 h-1.5 rounded-full bg-white animate-pulse"></span>
                  开启直播
                </button>
                <button 
                  @click="stopLivestreamHandler" 
                  class="px-3 py-1.5 text-xs font-bold text-white bg-red-600/90 hover:bg-red-500 rounded border border-red-500/30 shadow-lg backdrop-blur-sm transition-all active:scale-95 cursor-pointer">
                  关闭直播
                </button>
                <span v-if="hasCloudControl" class="px-2 py-1 text-xs font-bold text-green-400 bg-green-950/80 rounded border border-green-500/30 select-none">
                  已获取手持控制权
                </span>
                <button 
                  @click="grabCloudControlHandler" 
                  :disabled="hasCloudControl"
                  :class="hasCloudControl ? 'opacity-50 cursor-not-allowed' : 'hover:bg-blue-500'"
                  class="px-3 py-1.5 text-xs font-bold text-white bg-blue-600/90 rounded border border-blue-500/30 shadow-lg backdrop-blur-sm transition-all active:scale-95 cursor-pointer">
                  夺取手持控制权
                </button>
                <button 
                  @click="releaseCloudControlHandler" 
                  class="px-3 py-1.5 text-xs font-bold text-white bg-orange-600/90 rounded border border-orange-500/30 shadow-lg backdrop-blur-sm transition-all active:scale-95 cursor-pointer">
                  释放手持控制权
                </button>
              </div>
              <!-- 视频元素始终渲染（不使用v-show），避免display:none导致浏览器无法处理WebRTC媒体流 -->
              <video id="video-webrtc" ref="videowebrtc" autoplay muted playsinline
                :style="{ height: '100%', width: '100%', objectFit: 'contain' }" @loadedmetadata="onLoadedMetadata"
                @resize="onVideoResize"></video>

              <!-- 视频加载占位符，覆盖在视频上层，视频加载后隐藏 -->
              <div v-show="!isVideoLoaded"
                class="absolute inset-0 z-10 flex flex-col items-center justify-center bg-gray-900 border border-gray-800 rounded-lg">
                <div class="relative w-20 h-20 mb-4 opacity-30 animate-pulse">
                  <svg class="w-full h-full text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1"
                      d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
                  </svg>
                  <div class="absolute inset-0 flex items-center justify-center">
                    <div class="w-1 h-24 bg-gray-900 rotate-45 transform origin-center"></div>
                  </div>
                </div>
                <div class="flex flex-col items-center gap-1">
                  <span class="text-gray-400 text-sm font-medium tracking-wide">视频信号未连接</span>
                  <span class="text-gray-600 text-xs">Waiting for video signal...</span>
                </div>
              </div>
              

              <!-- 覆盖层：用于绘制矩形 -->
              <canvas ref="overlayCanvas" id="overlayCanvas" class="overlay-canvas absolute z-20"
                @dblclick="handleVideoDblClick" @mousedown="onRectMouseDown" @mousemove="onRectMouseMove"
                @mouseup="onRectMouseUp"></canvas>

              <!-- 智能平视座舱HUD (スマートHUD) -->
              <div v-if="isVideoLoaded && deviceInfo.device" class="hud-overlay absolute inset-0 pointer-events-none z-30 select-none font-mono">

                <!-- 中央姿态仪 & 十字准星 (Attitude & Center Reticle) -->
                <div class="hud-center-reticle">
                  <!-- 中心固定十字架 -->
                  <div class="hud-crosshair"></div>
                  
                  <!-- 姿态旋转刻度线 -->
                  <div class="hud-pitch-ladder"
                    :style="{ transform: `rotate(${-Number(deviceInfo.device?.attitude_roll || 0)}deg) translateY(${Number(deviceInfo.device?.attitude_pitch || 0) * 1.5}px)` }">
                    <div class="pitch-line" data-angle="+10°"></div>
                    <div class="horizon-line"></div>
                    <div class="pitch-line" data-angle="-10°"></div>
                  </div>
                </div>

                <!-- 底部姿态与环境读数栏 (HUD Footer Stats) -->
                <div class="hud-footer-stats">
                  <div class="flex items-center gap-1">
                    <span class="stat-label">PITCH:</span>
                    <span class="stat-value">{{ Number(deviceInfo.device?.attitude_pitch || 0).toFixed(1) }}°</span>
                  </div>
                  <div class="stat-divider"></div>
                  <div class="flex items-center gap-1">
                    <span class="stat-label">ROLL:</span>
                    <span class="stat-value">{{ Number(deviceInfo.device?.attitude_roll || 0).toFixed(1) }}°</span>
                  </div>
                  <div class="stat-divider"></div>
                  <div class="flex items-center gap-1">
                    <span class="stat-label">YAW:</span>
                    <span class="stat-value">{{ Number(deviceInfo.device?.attitude_head || 0).toFixed(0) }}°</span>
                  </div>
                  <div class="stat-divider" v-if="distanceToGround !== undefined"></div>
                  <div class="flex items-center gap-1" v-if="distanceToGround !== undefined">
                    <span class="stat-label">AGL:</span>
                    <span class="stat-value text-green-400">{{ distanceToGround.toFixed(1) }}m</span>
                  </div>
                </div>
              </div>
              <div
                class="absolute top-2 left-2 w-[320px] rounded-xl p-3 bg-gray-900/95 border border-purple-500/30 text-white backdrop-blur-md shadow-2xl z-50 transition-all duration-300"
                v-if="psdkShow">
                <div class="flex items-center justify-between mb-2 pb-1 border-b border-gray-800">
                  <span class="text-xs font-bold text-purple-300 flex items-center gap-1.5">
                    <span class="w-2 h-2 rounded-full bg-purple-500 animate-pulse"></span>
                    负载实时数据
                  </span>
                  <span class="text-[11px] text-gray-400 cursor-pointer hover:text-white transition-colors" @click="psdkShow = false">✕</span>
                </div>
                <div class="text-xs text-gray-200 leading-relaxed font-mono max-h-[220px] overflow-y-auto custom-scrollbar break-words whitespace-pre-wrap">
                  {{ psdkContentText || '暂无实时数据，等待负载数据流输入...' }}
                </div>
              </div>
       

              <div v-if="playUrl" class="absolute top-1/6 left-4 z-30 flex flex-col gap-3">
                <!-- 广角 -->
                <div @click="onSwitch('wide')"
                  class="group relative flex items-center justify-center w-12 h-8 bg-gray-900/70 backdrop-blur-[2px] border border-gray-600 rounded cursor-pointer hover:border-blue-400 hover:bg-gray-800/90 transition-all shadow-lg active:scale-95">
                  <span class="text-[11px] font-bold text-gray-200 group-hover:text-white tracking-widest">广角</span>
                  <!-- 角标 -->
                  <div
                    class="absolute -top-1.5 -right-1.5 w-3.5 h-3.5 bg-blue-600 rounded flex items-center justify-center border border-gray-900 shadow-sm z-10">
                    <span class="text-[9px] font-bold text-white leading-none">1</span>
                  </div>
                </div>

                <!-- 变焦 -->
                <div @click="onSwitch('zoom')"
                  class="group relative flex items-center justify-center w-12 h-8 bg-gray-900/70 backdrop-blur-[2px] border border-gray-600 rounded cursor-pointer hover:border-blue-400 hover:bg-gray-800/90 transition-all shadow-lg active:scale-95">
                  <span class="text-[11px] font-bold text-gray-200 group-hover:text-white tracking-widest">变焦</span>
                  <div
                    class="absolute -top-1.5 -right-1.5 w-3.5 h-3.5 bg-blue-600 rounded flex items-center justify-center border border-gray-900 shadow-sm z-10">
                    <span class="text-[9px] font-bold text-white leading-none">2</span>
                  </div>
                </div>

                <!-- 红外 -->
                <div @click="onSwitch('ir')"
                  class="group relative flex items-center justify-center w-12 h-8 bg-gray-900/70 backdrop-blur-[2px] border border-gray-600 rounded cursor-pointer hover:border-blue-400 hover:bg-gray-800/90 transition-all shadow-lg active:scale-95">
                  <span class="text-[11px] font-bold text-gray-200 group-hover:text-white tracking-widest">红外</span>
                  <div
                    class="absolute -top-1.5 -right-1.5 w-3.5 h-3.5 bg-blue-600 rounded flex items-center justify-center border border-gray-900 shadow-sm z-10">
                    <span class="text-[9px] font-bold text-white leading-none">3</span>
                  </div>
                </div>

                

                <div class="pt-2">
                  <a-badge>
                    <template #count>
                      <span class="text-white relative w-[0.8rem]  font-bold text-center border-2">F
                      </span>
                    </template>
                    <svg-icon @click="onTakeCameraPhoto" class="cursor-pointer" :size="35" name="circle" />
                  </a-badge>
                </div>

                <div class="pt-2">
                  <a-badge>
                    <template #count>
                      <span class="text-white relative font-bold w-[0.8rem] text-center border-2">R</span>
                    </template>
                    <svg-icon @click="recording" class="cursor-pointer" title="录像" :size="35" name="circle_red" />
                  </a-badge>
                </div>

              </div>
              <!-- 中心十字准星 (SVG) -->
              <svg class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 pointer-events-none" width="45"
                height="45" viewBox="0 0 60 60">
                <line x1="0" y1="30" x2="26" y2="30" stroke="white" stroke-width="2" stroke-linecap="round" />
                <line x1="34" y1="30" x2="60" y2="30" stroke="white" stroke-width="2" stroke-linecap="round" />
                <line x1="30" y1="0" x2="30" y2="26" stroke="white" stroke-width="2" stroke-linecap="round" />
                <line x1="30" y1="34" x2="30" y2="60" stroke="white" stroke-width="2" stroke-linecap="round" />
              </svg>

              <!-- 激光测距中心数据 (十字准星下方) -->
              <div v-if="laserRangefinderEnabled && laserMeasureData && playUrl"
                class="absolute top-1/2 left-1/2 -translate-x-1/2 z-30 pointer-events-none" style="margin-top: 30px;">
                <div class="flex flex-col items-center gap-0.5">
                  <!-- 距离 + 状态 -->
                  <div class="flex items-baseline gap-1.5">
                    <span class="text-xl font-bold tabular-nums drop-shadow-[0_1px_3px_rgba(0,0,0,0.9)]"
                      :class="laserMeasureData.errorState === 0 ? 'text-green-400' : 'text-gray-400'">{{
                        laserMeasureData.errorState === 0 ? Number(laserMeasureData.distance).toFixed(1) : '--'
                      }}</span>
                    <span class="text-[11px] font-medium drop-shadow-[0_1px_3px_rgba(0,0,0,0.9)]"
                      :class="laserMeasureData.errorState === 0 ? 'text-green-400/80' : 'text-gray-500'">m</span>
                    <span class="text-[10px] font-bold px-1 py-0 rounded drop-shadow-[0_1px_3px_rgba(0,0,0,0.9)]"
                      :class="MeasureTargetErrorStateMap[laserMeasureData.errorState]?.color || 'text-gray-400'">
                      {{ MeasureTargetErrorStateMap[laserMeasureData.errorState]?.label || '' }}
                    </span>
                  </div>
                  <!-- 坐标 (紧凑一行) -->
                  <div class="flex items-center gap-2 text-[11px] text-white font-medium"
                    style="text-shadow: 0 0 4px rgba(0,0,0,1), 0 0 8px rgba(0,0,0,0.8), 1px 1px 2px rgba(0,0,0,1);">
                    <span class="tabular-nums">{{ laserMeasureData.longitude != null ?
                      Number(laserMeasureData.longitude).toFixed(6) : '--' }}</span>
                    <span class="text-gray-300">/</span>
                    <span class="tabular-nums">{{ laserMeasureData.latitude != null ?
                      Number(laserMeasureData.latitude).toFixed(6) : '--' }}</span>
                  </div>
                </div>
              </div>





              <div class="absolute text-white top-1/3 right-20 z-30 " v-if="playUrl">
                <!--            <svg-icon :size="35" name="rect"/>-->

                <!-- 尺子样式变焦滑块 -->
                <div class="zoom-ruler-container">
                  <div class="zoom-ruler">
                    <!-- 刻度区域 (top: 10px, bottom: 10px, 所以可用高度是 120px) -->
                    <div class="ruler-track">
                      <!-- 主刻度: 2, 10, 20, 30, 40, 50, 57 - 百分比基于 track 高度 -->
                      <div v-for="tickValue in [2, 10, 20, 30, 40, 50, 57]" :key="tickValue" class="ruler-tick major"
                        :style="{ bottom: `${((tickValue - 2) / 55) * 100}%` }">
                        <span class="tick-label">{{ tickValue }}x</span>
                      </div>
                      <!-- 次刻度: 5, 15, 25, 35, 45, 55 -->
                      <div v-for="tickValue in [5, 15, 25, 35, 45, 55]" :key="'minor-' + tickValue" class="ruler-tick"
                        :style="{ bottom: `${((tickValue - 2) / 55) * 100}%` }">
                      </div>
                      <!-- 滑块指示器 -->
                      <div class="ruler-indicator" :style="{ bottom: `${((zoomValue - 2) / 55) * 100}%` }"
                        @mousedown="startZoomDrag">
                        <div class="indicator-value">{{ zoomValue }}x</div>
                        <div class="indicator-arrow">▶</div>
                      </div>
                    </div>
                    <!-- 隐藏的实际滑块 -->
                    <input type="range" class="hidden-slider" v-model.number="zoomValue"
                      @change="() => changeZoom(zoomValue)" min="2" max="57" step="1" orient="vertical" />
                  </div>
                </div>
              </div>
            </div>

          </div>
        </div>
        <!-- Full Screen Flight Overlay -->
        <div v-if="isFullScreenFlight"
          class="absolute inset-0 z-[45] flex flex-col justify-between pointer-events-none">


          <!-- Center Area (Empty for view) -->
          <div class="flex-1 relative">
            <!-- Right Side Tools - Full Toolbar (Vertically Centered to avoid blocking top HUD/waypoint controls) -->
            <div
              class="absolute right-2 top-1/2 -translate-y-1/2 flex flex-col gap-1 p-2 pointer-events-auto bg-gray-900/70 backdrop-blur-sm rounded-lg border border-white/10 shadow-2xl z-30">

              <!-- 云台控制 -->
              <div class="relative">
                <a-tooltip title="云台控制" color="purple" placement="left">
                  <div class="bg-gray-800/80 p-2 rounded cursor-pointer hover:bg-gray-700"
                    @click="showGimbalMenu = !showGimbalMenu">
                    <svg-icon name="yuntai" :size="20" class="text-white" />
                  </div>
                </a-tooltip>
                <!-- 全屏模式下的云台控制弹窗 -->
                <div v-if="showGimbalMenu"
                  class="absolute right-full top-0 mr-2 w-24 bg-gray-800 border border-gray-700 rounded-lg shadow-xl overflow-hidden z-[100] flex flex-col py-1">
                  <div v-for="item in gimbalOptions" :key="item.key" @click="handleGimbalSelect(item)"
                    class="px-3 py-2 text-gray-300 text-xs cursor-pointer hover:bg-gray-700 hover:text-white transition-colors text-center">
                    {{ item.label }}
                  </div>
                </div>
              </div>

              <!-- 负载数据 -->
              <div class="relative">
                <a-tooltip title="负载实时查看数据" color="purple" placement="left">
                  <div class="bg-gray-800/80 p-2 rounded cursor-pointer hover:bg-gray-700"
                    @click="psdkShow = !psdkShow">
                    <svg-icon name="psdk" :size="20" class="text-white" />
                  </div>
                </a-tooltip>
              </div>

              <!-- 高光谱采集 -->
              <div class="relative">
                <a-tooltip title="高光谱采集" color="purple" placement="left">
                  <div class="bg-gray-800/80 p-2 rounded cursor-pointer hover:bg-gray-700"
                    @click="aeroSpotVisible = !aeroSpotVisible">
                    <!-- 使用彩色圆锥表示光谱图标 -->
                    <svg class="w-5 h-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                        d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
                    </svg>
                  </div>
                </a-tooltip>
                <!-- 弹窗 -->
                <div v-if="aeroSpotVisible"
                  class="absolute right-full top-0 mr-2 w-32 bg-gray-800 border border-gray-700 rounded-lg shadow-xl overflow-hidden z-[100] flex flex-col py-1">
                  <div @click="handleAeroSpotAction(8)"
                    class="px-3 py-2 text-gray-300 text-xs cursor-pointer hover:bg-gray-700 hover:text-white transition-colors text-center">
                    单次采集
                  </div>
                  <div @click="handleAeroSpotAction(10)"
                    class="px-3 py-2 text-gray-300 text-xs cursor-pointer hover:bg-gray-700 hover:text-green-400 transition-colors text-center"
                    :style="{ color: aeroSpotContinuousCollecting ? '#00ee8b' : undefined }">
                    开始连续采集
                  </div>
                  <div @click="handleAeroSpotAction(11)"
                    class="px-3 py-2 text-gray-300 text-xs cursor-pointer hover:bg-gray-700 hover:text-red-400 transition-colors text-center">
                    停止连续采集
                  </div>
                </div>
              </div>


              <!-- 负载后台记录 -->
              <div class="relative">
                <a-tooltip :title="lingXiuEnabled ? '负载记录已开启' : '负载记录已关闭'" color="purple" placement="left">
                  <div class="bg-gray-800/80 p-2 rounded cursor-pointer hover:bg-gray-700"
                    @click="handleLingXiuButtonClick">
                    <svg class="w-5 h-5" :style="{ color: lingXiuEnabled ? '#00ee8b' : '#ffffff' }" viewBox="0 0 24 24">
                      <text x="12" y="16" text-anchor="middle" fill="currentColor" font-size="10"
                        font-family="SimHei, sans-serif">载</text>
                    </svg>
                  </div>
                </a-tooltip>
              </div>


              <!-- ONNX 前端识别 -->
              <div class="relative">
                <a-tooltip
                  :title="isRealTimeRecognitionActive ? `FPS: ${onnxDetection.fps.value.toFixed(1)}` : '前端实况识别 (ONNX)'"
                  color="purple" placement="left">
                  <div class="bg-gray-800/80 p-2 rounded cursor-pointer hover:bg-gray-700"
                    @click="handleRealTimeButtonClick">
                    <svg class="w-5 h-5" :style="{ color: isRealTimeRecognitionActive ? '#00ee8b' : '#ffffff' }"
                      viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                      <rect x="5" y="5" width="14" height="14" rx="2" stroke-linecap="round" stroke-linejoin="round" />
                      <ellipse cx="12" cy="12" rx="3" ry="2" stroke-linecap="round" stroke-linejoin="round" />
                      <circle cx="12" cy="12" r="1" fill="currentColor" stroke="none" />
                    </svg>
                  </div>
                </a-tooltip>
                <!-- 全屏模式下的前端实况识别弹窗 -->
                <div v-if="onnxConfigVisible && !isRealTimeRecognitionActive"
                  class="absolute right-full top-0 mr-2 w-52 z-[1000] bg-gray-900/95 backdrop-blur-sm border border-gray-600 rounded-lg shadow-xl overflow-hidden">
                  <div class="flex justify-between items-center px-2 py-1.5 bg-gray-800 border-b border-gray-700">
                    <span class="text-white text-xs font-bold flex items-center gap-1">
                      <span class="text-sm">🎯</span>
                      ONNX 实况识别配置
                    </span>
                    <div class="cursor-pointer text-gray-400 hover:text-white" @click="onnxConfigVisible = false">
                      <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12">
                        </path>
                      </svg>
                    </div>
                  </div>
                  <div class="p-2 space-y-2 text-xs">
                    <div class="space-y-1">
                      <label class="text-gray-400 font-medium">选择模型</label>
                      <select v-model="selectedOnnxModel"
                        class="w-full px-2 py-1 bg-gray-800 border border-gray-600 rounded text-white text-xs focus:outline-none focus:border-blue-500 transition-colors cursor-pointer">
                        <option v-for="(config, key) in ONNX_MODEL_CONFIGS" :key="key" :value="String(key)">
                          {{ config.name }}
                        </option>
                      </select>
                    </div>
                    <div class="space-y-1">
                      <div class="flex justify-between items-center">
                        <label class="text-gray-400 font-medium">置信度阈值</label>
                        <span class="text-blue-400 font-bold">{{ (onnxDetection.confidenceThreshold.value *
                          100).toFixed(0)
                        }}%</span>
                      </div>
                      <input type="range" v-model.number="onnxDetection.confidenceThreshold.value" min="0.1" max="0.9"
                        step="0.05"
                        class="w-full h-1.5 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-blue-500" />
                      <div class="flex justify-between text-[10px] text-gray-500">
                        <span>低</span>
                        <span>高</span>
                      </div>
                    </div>
                    <div class="space-y-1">
                      <div class="flex justify-between items-center">
                        <label class="text-gray-400 font-medium">识别间隔</label>
                        <span class="text-green-400 font-bold">{{ onnxDetection.inferenceInterval.value }}ms</span>
                      </div>
                      <input type="range" v-model.number="onnxDetection.inferenceInterval.value" min="0" max="500"
                        step="50"
                        class="w-full h-1.5 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-green-500" />
                      <div class="flex justify-between text-[10px] text-gray-500">
                        <span>快</span>
                        <span>慢</span>
                      </div>
                    </div>
                    <div class="space-y-1">
                      <div class="flex justify-between items-center">
                        <label class="text-gray-400 font-medium">NMS 阈值</label>
                        <span class="text-yellow-400 font-bold">{{ (onnxDetection.nmsThreshold.value * 100).toFixed(0)
                        }}%</span>
                      </div>
                      <input type="range" v-model.number="onnxDetection.nmsThreshold.value" min="0.1" max="0.9"
                        step="0.05"
                        class="w-full h-1.5 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-yellow-500" />
                      <div class="flex justify-between text-[10px] text-gray-500">
                        <span>严格</span>
                        <span>宽松</span>
                      </div>
                    </div>
                    <button @click="confirmStartOnnxRecognition" :disabled="onnxDetection.isLoading.value"
                      class="w-full py-1.5 px-3 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 disabled:from-gray-600 disabled:to-gray-600 text-white font-medium rounded transition-all flex items-center justify-center gap-1.5 text-xs">
                      <svg v-if="onnxDetection.isLoading.value" class="w-3 h-3 animate-spin" fill="none"
                        viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4">
                        </circle>
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z">
                        </path>
                      </svg>
                      <span v-else>▶</span>
                      {{ onnxDetection.isLoading.value ? '加载中...' : '开始识别' }}
                    </button>
                  </div>
                </div>
              </div>

              <!-- 激光测距 (全屏模式) -->
              <div class="relative">
                <a-tooltip :title="laserRangefinderEnabled ? '关闭激光测距' : '开启激光测距'" color="purple" placement="left">
                  <div class="bg-gray-800/80 p-2 rounded cursor-pointer hover:bg-gray-700"
                    @click="laserRangefinderEnabled = !laserRangefinderEnabled">
                    <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none"
                      :style="{ color: laserRangefinderEnabled ? '#00ee8b' : '#ffffff' }">
                      <rect x="3" y="6" width="18" height="12" rx="2" stroke="currentColor" stroke-width="1.5"
                        fill="none" />
                      <line x1="21" y1="12" x2="24" y2="12" stroke="currentColor" stroke-width="2"
                        stroke-linecap="round" />
                      <line x1="0" y1="12" x2="3" y2="12" stroke="currentColor" stroke-width="2"
                        stroke-linecap="round" />
                      <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1" fill="none" />
                      <line x1="12" y1="8" x2="12" y2="10" stroke="currentColor" stroke-width="1" />
                      <line x1="12" y1="14" x2="12" y2="16" stroke="currentColor" stroke-width="1" />
                      <line x1="8" y1="12" x2="10" y2="12" stroke="currentColor" stroke-width="1" />
                      <line x1="14" y1="12" x2="16" y2="12" stroke="currentColor" stroke-width="1" />
                    </svg>
                  </div>
                </a-tooltip>
              </div>
            </div>
          </div>

          <!-- Bottom Panel -->
          <div class="w-full px-4 pb-4 flex items-end justify-between pointer-events-none">

            <!-- Left Dashboard -->
            <div
              class="w-80 bg-gray-900/80 backdrop-blur-md rounded-xl p-3 border border-white/10 pointer-events-auto flex flex-col gap-2">
              <!-- Battery Bar -->
              <div class="w-full">
                <div class="flex justify-between text-xs text-gray-300 mb-1">
                  <span>电量</span>
                  <span>{{ deviceInfo.device?.battery?.capacity_percent || 0 }}%</span>
                </div>
                <div class="w-full h-2 bg-gray-700 rounded-full overflow-hidden relative">
                  <div class="absolute h-full bg-green-500"
                    :style="{ width: (deviceInfo.device?.battery?.capacity_percent || 0) + '%' }"></div>
                  <!-- Markers -->
                  <div class="absolute h-full w-0.5 bg-white top-0"
                    :style="{ left: (deviceInfo.device?.battery?.return_home_power || 0) + '%' }"></div>
                  <div class="absolute h-full w-0.5 bg-red-500 top-0"
                    :style="{ left: (deviceInfo.device?.battery?.landing_power || 0) + '%' }"></div>
                </div>
              </div>

              <div class="flex gap-2">
                <!-- Compass Reuse -->
                <div class="w-20 h-20 relative flex-shrink-0">
                  <img class="absolute inset-0 w-full h-full opacity-80" src="@/assets/airControl/compass-base.svg"
                    alt="">
                  <img class="absolute inset-0 w-full h-full transition-transform duration-300"
                    src="@/assets/airControl/compass-arrow.svg" alt=""
                    :style="{ transform: `rotate(${(deviceInfo.device?.attitude_head || 0) + 45}deg)` }">
                  <div class="absolute bottom-1 left-1/2 -translate-x-1/2 text-white text-[10px] font-bold">{{
                    deviceInfo.device?.attitude_head || 0 }}°</div>
                </div>
                <!-- Text Data -->
                <div class="flex-1 grid grid-cols-2 gap-x-1 gap-y-0 text-[14px] text-gray-300 leading-tight">
                  <div>绝对高度: {{ Number(deviceInfo.device?.height).toFixed(1) || 0 }}m</div>
                  <div>相对高度: {{ deviceInfo.device?.elevation || 0 }}m</div>
                  <div>距地高度：{{ distanceToGround.toFixed(1) || 0 }}m </div>
                  <div>HOME: {{ Number(deviceInfo.device?.home_distance).toFixed(0) || 0 }}m</div>
                  <div>HS: {{ Number(deviceInfo.device?.horizontal_speed).toFixed(1) }}m/s</div>
                  <div>VS: {{ deviceInfo.device?.vertical_speed }}m/s</div>
                  <div>Pitch: {{ deviceInfo.device?.attitude_pitch || 0 }}°</div>
                  <div>Roll: {{ deviceInfo.device?.attitude_roll || 0 }}°</div>
                  <div v-if="returnHomeBattery !== undefined">返航预留: {{ returnHomeBattery }}%</div>
                </div>
              </div>
            </div>

            <!-- Center Joysticks -->
            <div
              class="flex items-center gap-4 pointer-events-auto px-6 py-2 bg-gray-900/40 rounded-full backdrop-blur-sm border border-white/5">
              <!-- Left Stick (Elevation/Yaw) -->
              <div
                class="joystick-circle relative w-20 h-20 bg-gray-800/80 rounded-full border border-gray-500 shadow-lg flex items-center justify-center">
                <div class="absolute -bottom-2 cursor-pointer active:scale-90 transition-transform"
                  @mousedown="onMouseDown(KeyCode.Key_Z)" @mouseup="onMouseUp(KeyCode.Key_Z)"
                  @mouseleave="onMouseUp(KeyCode.Key_Z)">
                  <div class="w-6 h-6 bg-gray-700 rounded-full flex items-center justify-center text-[10px] text-white">
                    Z
                  </div>
                </div>
                <div class="absolute -top-2 cursor-pointer active:scale-90 transition-transform"
                  @mousedown="onMouseDown(KeyCode.Key_C)" @mouseup="onMouseUp(KeyCode.Key_C)"
                  @mouseleave="onMouseUp(KeyCode.Key_C)">
                  <div class="w-6 h-6 bg-gray-700 rounded-full flex items-center justify-center text-[10px] text-white">
                    C
                  </div>
                </div>
                <div class="absolute -left-2 cursor-pointer active:scale-90 transition-transform"
                  @mousedown="onMouseDown(KeyCode.KEY_Q)" @mouseup="onMouseUp(KeyCode.KEY_Q)"
                  @mouseleave="onMouseUp(KeyCode.KEY_Q)">
                  <div class="w-6 h-6 bg-gray-700 rounded-full flex items-center justify-center text-[10px] text-white">
                    Q
                  </div>
                </div>
                <div class="absolute -right-2 cursor-pointer active:scale-90 transition-transform"
                  @mousedown="onMouseDown(KeyCode.KEY_E)" @mouseup="onMouseUp(KeyCode.KEY_E)"
                  @mouseleave="onMouseUp(KeyCode.KEY_E)">
                  <div class="w-6 h-6 bg-gray-700 rounded-full flex items-center justify-center text-[10px] text-white">
                    E
                  </div>
                </div>
              </div>

              <!-- Emergency Stop -->
              <div class="flex flex-col items-center gap-1">
                <div class="text-white text-[10px] font-bold tracking-widest">控制_</div>
                <div
                  class="w-10 h-10 rounded-full bg-red-600 border-2 border-red-400 flex items-center justify-center cursor-pointer shadow-lg active:scale-95 transition-all"
                  @click="handleEmergencyStopHandler">
                  <span class="text-white font-bold text-[10px]">STOP</span>
                </div>
                <button class="bg-blue-600 text-[10px] text-white px-2 py-0.5 rounded shadow active:scale-95"
                  @click="onClickFightControl">{{ flightController ? '释放' : '获取' }}</button>
              </div>

              <!-- Right Stick (Horizontal) -->
              <div
                class="joystick-circle relative w-20 h-20 bg-gray-800/80 rounded-full border border-gray-500 shadow-lg flex items-center justify-center">
                <div class="absolute -top-2 cursor-pointer active:scale-90 transition-transform"
                  @mousedown="onMouseDown(KeyCode.KEY_W)" @mouseup="onMouseUp(KeyCode.KEY_W)"
                  @mouseleave="onMouseUp(KeyCode.KEY_W)">
                  <div class="w-6 h-6 bg-gray-700 rounded-full flex items-center justify-center text-[10px] text-white">
                    W
                  </div>
                </div>
                <div class="absolute -bottom-2 cursor-pointer active:scale-90 transition-transform"
                  @mousedown="onMouseDown(KeyCode.KEY_S)" @mouseup="onMouseUp(KeyCode.KEY_S)"
                  @mouseleave="onMouseUp(KeyCode.KEY_S)">
                  <div class="w-6 h-6 bg-gray-700 rounded-full flex items-center justify-center text-[10px] text-white">
                    S
                  </div>
                </div>
                <div class="absolute -left-2 cursor-pointer active:scale-90 transition-transform"
                  @mousedown="onMouseDown(KeyCode.KEY_A)" @mouseup="onMouseUp(KeyCode.KEY_A)"
                  @mouseleave="onMouseUp(KeyCode.KEY_A)">
                  <div class="w-6 h-6 bg-gray-700 rounded-full flex items-center justify-center text-[10px] text-white">
                    A
                  </div>
                </div>
                <div class="absolute -right-2 cursor-pointer active:scale-90 transition-transform"
                  @mousedown="onMouseDown(KeyCode.KEY_D)" @mouseup="onMouseUp(KeyCode.KEY_D)"
                  @mouseleave="onMouseUp(KeyCode.KEY_D)">
                  <div class="w-6 h-6 bg-gray-700 rounded-full flex items-center justify-center text-[10px] text-white">
                    D
                  </div>
                </div>
              </div>

              <!-- Gimbal D-Pad (Camera) -->
              <div
                class="joystick-circle relative w-20 h-20 bg-gray-800/80 rounded-full border border-gray-500 shadow-lg flex items-center justify-center">
                <div class="absolute -top-2 cursor-pointer active:scale-90 transition-transform"
                  @mousedown="handleCameraDrag(5, 0)" @mouseup="stopCameraDrag" @mouseleave="stopCameraDrag">
                  <div class="w-6 h-6 bg-gray-700 rounded-full flex items-center justify-center text-[10px] text-white">
                    <CaretUpOutlined />
                  </div>
                </div>
                <div class="absolute -bottom-2 cursor-pointer active:scale-90 transition-transform"
                  @mousedown="handleCameraDrag(-5, 0)" @mouseup="stopCameraDrag" @mouseleave="stopCameraDrag">
                  <div class="w-6 h-6 bg-gray-700 rounded-full flex items-center justify-center text-[10px] text-white">
                    <CaretDownOutlined />
                  </div>
                </div>
                <div class="absolute -left-2 cursor-pointer active:scale-90 transition-transform"
                  @mousedown="handleCameraDrag(0, -5)" @mouseup="stopCameraDrag" @mouseleave="stopCameraDrag">
                  <div class="w-6 h-6 bg-gray-700 rounded-full flex items-center justify-center text-[10px] text-white">
                    <CaretLeftOutlined />
                  </div>
                </div>
                <div class="absolute -right-2 cursor-pointer active:scale-90 transition-transform"
                  @mousedown="handleCameraDrag(0, 5)" @mouseup="stopCameraDrag" @mouseleave="stopCameraDrag">
                  <div class="w-6 h-6 bg-gray-700 rounded-full flex items-center justify-center text-[10px] text-white">
                    <CaretRightOutlined />
                  </div>
                </div>
                <span class="text-[9px] text-gray-400 font-bold">CAM</span>
              </div>
            </div>

            <!-- Right Control Panel -->
            <div class="w-52 bg-gray-900/80 backdrop-blur-md rounded-xl p-3 border border-white/10 pointer-events-auto">
              <div class="flex justify-between items-center mb-2 border-b border-gray-700 pb-1">
                <span class="text-white text-xs">飞行控制</span>
              </div>
              <!-- 控制按钮区 (全屏模式) -->
              <div class="flex flex-col gap-1">
                <div class="grid grid-cols-2 gap-1">
                  <div v-for="(cmdItem) in cmdList.slice(0, 1)" :key="cmdItem.cmdKey">
                    <Button :loading="cmdItem.loading" ghost size="small" class="text-[10px] h-6 px-1 w-full"
                      @click="sendControlCmd(cmdItem, 0)">{{ cmdItem.operateText }}</Button>
                  </div>
                  <div v-for="(cmdItem) in cmdList.slice(1, 4)" :key="cmdItem.cmdKey">
                    <Button :loading="cmdItem.loading" ghost size="small" class="text-[10px] h-6 px-1 w-full"
                      @click="sendControlCmd(cmdItem, 0)">{{ cmdItem.operateText }}</Button>
                  </div>
                </div>
                <!-- 航线暂停/恢复按钮 -->
                <div class="grid grid-cols-2 gap-1">
                  <Button :loading="waylinePauseLoading" ghost size="small" class="text-[10px] h-6 px-1"
                    @click="handleWaylinePause">航线暂停</Button>
                  <Button :loading="waylineResumeLoading" ghost size="small" class="text-[10px] h-6 px-1"
                    @click="handleWaylineResume">航线恢复</Button>
                </div>
                <div>
                  <Button ghost size="small" class="text-[10px] h-6 px-1 w-full"
                    @click="testFlightControl">抢夺控制权</Button>

                </div>
                <!-- <div>
                  <Button ghost size="small" class="text-[10px] h-6 px-1 w-full"
                    @click="testReleaseControl">测试释放控制权</Button>
                </div> -->
                <div class="mt-1">
                  <a-popover title="操控速度设置" trigger="click" placement="left">
                    <template #content>
                      <div class="space-y-3 p-1 w-56">
                        <div class="flex items-center justify-between">
                          <span class="text-gray-600 text-xs font-bold">水平速度 (%)</span>
                          <a-input-number size="small" v-model:value="controlSpeeds.horizontal" :min="1" :max="100"
                            class="w-20" :step="10" />
                        </div>
                        <div class="flex items-center justify-between">
                          <span class="text-gray-600 text-xs font-bold">垂直速度 (%)</span>
                          <a-input-number size="small" v-model:value="controlSpeeds.vertical" :min="1" :max="100"
                            class="w-20" :step="10" />
                        </div>
                        <div class="flex items-center justify-between">
                          <span class="text-gray-600 text-xs font-bold">旋转速度 (%)</span>
                          <a-input-number size="small" v-model:value="controlSpeeds.yaw" :min="1" :max="100"
                            class="w-20" :step="10" />
                        </div>
                      </div>
                    </template>
                    <Button ghost size="small"
                      class="text-[10px] h-6 px-1 w-full !flex !items-center !justify-center gap-1">
                      <svg-icon name="setting" :size="11" class="shrink-0" />
                      <span class="text-[10px] leading-none">速度设置</span>
                    </Button>
                  </a-popover>
                </div>
              </div>
            </div>

          </div>
        </div>
        <!-- ONNX 实况识别信息面板 (识别运行时显示) - 叠加在视频右上角 -->
        <div v-if="isRealTimeRecognitionActive"
          class="absolute top-[5%] right-[10px] w-64 z-50 bg-gray-900/80 backdrop-blur-sm border border-gray-600 rounded-lg shadow-xl overflow-hidden">
          <div class="flex justify-between items-center px-3 py-2 bg-gray-800 border-b border-gray-700">
            <span class="text-white text-xs font-bold flex items-center gap-2">
              <span class="w-2 h-2 bg-green-500 rounded-full animate-pulse"></span>
              ONNX 实况识别
            </span>
            <div class="cursor-pointer text-gray-400 hover:text-white" @click="handleRealTimeButtonClick">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </div>
          </div>
          <div class="p-3 space-y-2 text-xs">
            <!-- 性能指标 -->
            <div class="grid grid-cols-3 gap-2">
              <div class="bg-gray-800 rounded p-2 text-center">
                <div class="text-gray-400">FPS</div>
                <div class="text-green-400 font-bold">{{ onnxDetection.fps.value.toFixed(1) }}</div>
              </div>
              <div class="bg-gray-800 rounded p-2 text-center">
                <div class="text-gray-400">推理</div>
                <div class="text-blue-400 font-bold">{{ onnxDetection.inferenceTime.value.toFixed(0) }}ms</div>
              </div>
              <div class="bg-gray-800 rounded p-2 text-center">
                <div class="text-gray-400">目标</div>
                <div class="text-yellow-400 font-bold">{{ onnxDetection.detectionCount.value }}</div>
              </div>
            </div>
            <!-- 模型信息 -->
            <div class="bg-gray-800 rounded p-2 flex justify-between items-center">
              <span class="text-gray-400">模型</span>
              <span class="text-white">{{ ONNX_MODEL_CONFIGS[selectedOnnxModel]?.name || selectedOnnxModel }}</span>
            </div>
            <!-- 当前配置 -->
            <div class="bg-gray-800 rounded p-2 grid grid-cols-3 gap-1 text-center">
              <div>
                <div class="text-gray-500 text-[10px]">置信度</div>
                <div class="text-blue-400">{{ (onnxDetection.confidenceThreshold.value * 100).toFixed(0) }}%</div>
              </div>
              <div>
                <div class="text-gray-500 text-[10px]">间隔</div>
                <div class="text-green-400">{{ onnxDetection.inferenceInterval.value }}ms</div>
              </div>
              <div>
                <div class="text-gray-500 text-[10px]">NMS</div>
                <div class="text-yellow-400">{{ (onnxDetection.nmsThreshold.value * 100).toFixed(0) }}%</div>
              </div>
            </div>
          </div>
        </div>
        <div v-show="!isFullScreenFlight" class="flex-1 bg-gray-800 rounded-lg p-1 overflow-visible">
          <div v-if="deviceInfo.device && deviceInfo?.device?.battery?.remain_flight_time !== 0" class="battery-slide"
            style="border: 1px solid red">
            <div class="w-full" style="background: #535759;"></div>
            <div :style="{ width: deviceInfo?.device?.battery?.capacity_percent + '%' }" class="capacity-percent"></div>
            <div :style="{ width: deviceInfo.device?.battery?.return_home_power + '%' }" class="return-home"></div>
            <div :style="{ width: deviceInfo.device?.battery?.landing_power + '%' }" class="landing"></div>
            <div :style="{ left: deviceInfo.device?.battery?.landing_power + '%' }" class="white-point"></div>
            <div :style="{ left: deviceInfo.device?.battery?.capacity_percent + '%' }" class="battery">
              {{ Math.floor(deviceInfo.device?.battery?.remain_flight_time / 60) }}:
              {{ 10 > (deviceInfo.device?.battery?.remain_flight_time % 60) ? '0' : '' }}{{
                deviceInfo.device?.battery?.remain_flight_time % 60
              }}
            </div>
          </div>
          <div
            class="w-full flex items-center justify-start px-2 py-1 mb-1 bg-gray-800 border-b border-gray-700 rounded h-[2rem]">

            <div class="relative">
              <a-tooltip title="云台控制" color="purple">
                <a-button type="text" @click="showGimbalMenu = !showGimbalMenu">
                  <svg-icon :size="22" name="yuntai" class="text-white" />
                </a-button>
              </a-tooltip>

              <div v-if="showGimbalMenu"
                class="absolute left-0 bottom-full mb-2 w-24 bg-gray-800 border border-gray-700 rounded-lg shadow-xl overflow-hidden z-[100] flex flex-col py-1">
                <div v-for="item in gimbalOptions" :key="item.key" @click="handleGimbalSelect(item)"
                  class="px-3 py-2 text-gray-300 text-xs cursor-pointer hover:bg-gray-700 hover:text-white transition-colors text-center">
                  {{ item.label }}
                </div>
              </div>
            </div>

            <div>
              <a-tooltip title="负载实时查看数据" color="purple">
                <a-button @click="psdkShow = !psdkShow" type="text">
                  <svg-icon :size="22" name="psdk" class="text-white" />
                </a-button>
              </a-tooltip>
            </div>

            <div class="relative">
              <a-tooltip title="高光谱采集" color="purple">
                <a-button @click="aeroSpotVisible = !aeroSpotVisible" type="text"
                  class="flex items-center justify-center">
                  <svg class="w-[22px] h-[22px] text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                      d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
                  </svg>
                </a-button>
              </a-tooltip>

              <div v-if="aeroSpotVisible"
                class="absolute left-0 bottom-full mb-2 w-32 bg-gray-800 border border-gray-700 rounded-lg shadow-xl overflow-hidden z-[100] flex flex-col py-1">
                <div @click="handleAeroSpotAction(8)"
                  class="px-3 py-2 text-gray-300 text-xs cursor-pointer hover:bg-gray-700 hover:text-white transition-colors text-center">
                  单次采集
                </div>
                <div @click="handleAeroSpotAction(10)"
                  class="px-3 py-2 text-gray-300 text-xs cursor-pointer hover:bg-gray-700 hover:text-green-400 transition-colors text-center"
                  :style="{ color: aeroSpotContinuousCollecting ? '#00ee8b' : undefined }">
                  开始连续采集
                </div>
                <div @click="handleAeroSpotAction(11)"
                  class="px-3 py-2 text-gray-300 text-xs cursor-pointer hover:bg-gray-700 hover:text-red-400 transition-colors text-center">
                  停止连续采集
                </div>
              </div>
            </div>


            <div class="flex items-center">
              <a-tooltip :title="lingXiuEnabled ? '负载记录已开启 (点击关闭)' : '负载记录已关闭 (点击开启)'" color="purple">
                <a-button @click="handleLingXiuButtonClick" type="text">
                  <svg class="w-8 h-5" :style="{ color: lingXiuEnabled ? '#00ee8b' : '#ffffff' }" viewBox="0 0 40 24">
                    <text x="20" y="17" text-anchor="middle" fill="currentColor" font-size="14"
                      font-family="SimHei, Microsoft YaHei, sans-serif">负载</text>
                  </svg>
                </a-button>
              </a-tooltip>
            </div>


            <div class="relative flex items-center">
              <a-tooltip
                :title="isRealTimeRecognitionActive ? `FPS: ${onnxDetection.fps.value.toFixed(1)} | 目标: ${onnxDetection.detectionCount.value}` : '前端实况识别 (ONNX)'"
                color="purple">
                <a-button @click="handleRealTimeButtonClick" type="text" :loading="onnxDetection.isLoading.value">
                  <svg class="w-5 h-5" :style="{ color: isRealTimeRecognitionActive ? '#00ee8b' : '#ffffff' }"
                    viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <rect x="5" y="5" width="14" height="14" rx="2" stroke-linecap="round" stroke-linejoin="round" />
                    <path stroke-linecap="round" stroke-linejoin="round"
                      d="M9 2v3M15 2v3M9 19v3M15 19v3M2 9h3M2 15h3M19 9h3M19 15h3" />
                    <ellipse cx="12" cy="12" rx="3" ry="2" stroke-linecap="round" stroke-linejoin="round" />
                    <circle cx="12" cy="12" r="1" fill="currentColor" stroke="none" />
                  </svg>
                </a-button>
              </a-tooltip>

              <div v-if="onnxConfigVisible && !isRealTimeRecognitionActive"
                class="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 w-52 z-[1000] bg-gray-900/95 backdrop-blur-sm border border-gray-600 rounded-lg shadow-xl overflow-hidden">
                <div class="flex justify-between items-center px-2 py-1.5 bg-gray-800 border-b border-gray-700">
                  <span class="text-white text-xs font-bold flex items-center gap-1">
                    <span class="text-sm">🎯</span>
                    ONNX 实况识别配置
                  </span>
                  <div class="cursor-pointer text-gray-400 hover:text-white" @click="onnxConfigVisible = false">
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12">
                      </path>
                    </svg>
                  </div>
                </div>
                <div class="p-2 space-y-2 text-xs">
                  <div class="space-y-1">
                    <label class="text-gray-400 font-medium">选择模型</label>
                    <select v-model="selectedOnnxModel"
                      class="w-full px-2 py-1 bg-gray-800 border border-gray-600 rounded text-white text-xs focus:outline-none focus:border-blue-500 transition-colors cursor-pointer">
                      <option v-for="(config, key) in ONNX_MODEL_CONFIGS" :key="key" :value="String(key)">
                        {{ config.name }}
                      </option>
                    </select>
                  </div>
                  <div class="space-y-1">
                    <div class="flex justify-between items-center">
                      <label class="text-gray-400 font-medium">置信度阈值</label>
                      <span class="text-blue-400 font-bold">{{ (onnxDetection.confidenceThreshold.value *
                        100).toFixed(0)
                      }}%</span>
                    </div>
                    <input type="range" v-model.number="onnxDetection.confidenceThreshold.value" min="0.1" max="0.9"
                      step="0.05"
                      class="w-full h-1.5 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-blue-500" />
                    <div class="flex justify-between text-[10px] text-gray-500">
                      <span>低</span>
                      <span>高</span>
                    </div>
                  </div>
                  <div class="space-y-1">
                    <div class="flex justify-between items-center">
                      <label class="text-gray-400 font-medium">识别间隔</label>
                      <span class="text-green-400 font-bold">{{ onnxDetection.inferenceInterval.value }}ms</span>
                    </div>
                    <input type="range" v-model.number="onnxDetection.inferenceInterval.value" min="0" max="500"
                      step="50"
                      class="w-full h-1.5 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-green-500" />
                    <div class="flex justify-between text-[10px] text-gray-500">
                      <span>快</span>
                      <span>慢</span>
                    </div>
                  </div>
                  <div class="space-y-1">
                    <div class="flex justify-between items-center">
                      <label class="text-gray-400 font-medium">NMS 阈值</label>
                      <span class="text-yellow-400 font-bold">{{ (onnxDetection.nmsThreshold.value * 100).toFixed(0)
                      }}%</span>
                    </div>
                    <input type="range" v-model.number="onnxDetection.nmsThreshold.value" min="0.1" max="0.9"
                      step="0.05"
                      class="w-full h-1.5 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-yellow-500" />
                    <div class="flex justify-between text-[10px] text-gray-500">
                      <span>严格</span>
                      <span>宽松</span>
                    </div>
                  </div>
                  <button @click="confirmStartOnnxRecognition" :disabled="onnxDetection.isLoading.value"
                    class="w-full py-1.5 px-3 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 disabled:from-gray-600 disabled:to-gray-600 text-white font-medium rounded transition-all flex items-center justify-center gap-1.5 text-xs">
                    <svg v-if="onnxDetection.isLoading.value" class="w-3 h-3 animate-spin" fill="none"
                      viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4">
                      </circle>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z">
                      </path>
                    </svg>
                    <span v-else>▶</span>
                    {{ onnxDetection.isLoading.value ? '加载中...' : '开始识别' }}
                  </button>
                </div>
              </div>
            </div>

            <!-- 激光测距 (非全屏模式) -->
            <div class="flex items-center">
              <a-tooltip :title="laserRangefinderEnabled ? '关闭激光测距' : '开启激光测距'" color="purple">
                <a-button @click="laserRangefinderEnabled = !laserRangefinderEnabled" type="text">
                  <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none"
                    :style="{ color: laserRangefinderEnabled ? '#00ee8b' : '#ffffff' }">
                    <rect x="3" y="6" width="18" height="12" rx="2" stroke="currentColor" stroke-width="1.5"
                      fill="none" />
                    <line x1="21" y1="12" x2="24" y2="12" stroke="currentColor" stroke-width="2"
                      stroke-linecap="round" />
                    <line x1="0" y1="12" x2="3" y2="12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                    <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1" fill="none" />
                    <line x1="12" y1="8" x2="12" y2="10" stroke="currentColor" stroke-width="1" />
                    <line x1="12" y1="14" x2="12" y2="16" stroke="currentColor" stroke-width="1" />
                    <line x1="8" y1="12" x2="10" y2="12" stroke="currentColor" stroke-width="1" />
                    <line x1="14" y1="12" x2="16" y2="12" stroke="currentColor" stroke-width="1" />
                  </svg>
                </a-button>
              </a-tooltip>
            </div>
          </div>

          <!-- 指南针和控制区 -->
          <div class="control-panel-1080p grid grid-cols-12 gap-2 px-3 py-2 bg-gray-800 border-t border-gray-700"
            style="max-height: 10vh;">

            <!-- 指南针 -->
            <div class="col-span-2 flex items-center justify-center">
              <div class="compass-1080p relative w-30 h-30 flex-shrink-0">
                <img class="absolute inset-0 w-full h-full" src="../../../assets/airControl/compass-base.svg" alt="">
                <img class="absolute inset-0 w-full h-full transition-transform duration-300"
                  src="../../../assets/airControl/compass-arrow.svg" alt=""
                  :style="{ transform: `rotate(${(deviceInfo.device?.attitude_head || 0) + 45}deg)` }">
                <div class="absolute bottom-0 left-1/2 -translate-x-1/2 text-white text-xs font-bold">{{
                  deviceInfo.device?.attitude_head || 0 }}°</div>
              </div>
            </div>

            <!-- 虚拟摇杆区 -->
            <div class="col-span-5 flex gap-4 items-center justify-center border-l border-gray-700">
              <!-- 左摇杆 -->
              <div
                class="joystick-circle relative w-24 h-24 bg-gray-800 rounded-full border-2 border-gray-600 flex items-center justify-center">
                <div class="absolute -bottom-2 cursor-pointer" @mousedown="onMouseDown(KeyCode.Key_Z)"
                  @mouseup="onMouseUp(KeyCode.Key_Z)" @mouseleave="onMouseUp(KeyCode.Key_Z)">
                  <div
                    class="joystick-btn w-7 h-7 bg-gray-700 hover:bg-gray-600 rounded-full flex items-center justify-center text-xs text-white font-medium">
                    Z</div>
                </div>
                <div class="absolute -top-2 cursor-pointer" @mousedown="onMouseDown(KeyCode.Key_C)"
                  @mouseup="onMouseUp(KeyCode.Key_C)" @mouseleave="onMouseUp(KeyCode.Key_C)">
                  <div
                    class="joystick-btn w-7 h-7 bg-gray-700 hover:bg-gray-600 rounded-full flex items-center justify-center text-xs text-white font-medium">
                    C</div>
                </div>
                <div class="absolute -left-2 cursor-pointer" @mousedown="onMouseDown(KeyCode.KEY_Q)"
                  @mouseup="onMouseUp(KeyCode.KEY_Q)" @mouseleave="onMouseUp(KeyCode.KEY_Q)">
                  <div
                    class="joystick-btn w-7 h-7 bg-gray-700 hover:bg-gray-600 rounded-full flex items-center justify-center text-xs text-white font-medium">
                    Q</div>
                </div>
                <div class="absolute -right-2 cursor-pointer" @mousedown="onMouseDown(KeyCode.KEY_E)"
                  @mouseup="onMouseUp(KeyCode.KEY_E)" @mouseleave="onMouseUp(KeyCode.KEY_E)">
                  <div
                    class="joystick-btn w-7 h-7 bg-gray-700 hover:bg-gray-600 rounded-full flex items-center justify-center text-xs text-white font-medium">
                    E</div>
                </div>
                <span class="text-sm text-gray-400 font-bold">L</span>
              </div>
              <!-- 右摇杆 -->
              <div
                class="joystick-circle relative w-24 h-24 bg-gray-800 rounded-full border-2 border-gray-600 flex items-center justify-center">
                <div class="absolute -top-2 cursor-pointer" @mousedown="onMouseDown(KeyCode.KEY_W)"
                  @mouseup="onMouseUp(KeyCode.KEY_W)" @mouseleave="onMouseUp(KeyCode.KEY_W)">
                  <div
                    class="joystick-btn w-7 h-7 bg-gray-700 hover:bg-gray-600 rounded-full flex items-center justify-center text-xs text-white font-medium">
                    W</div>
                </div>
                <div class="absolute -bottom-2 cursor-pointer" @mousedown="onMouseDown(KeyCode.KEY_S)"
                  @mouseup="onMouseUp(KeyCode.KEY_S)" @mouseleave="onMouseUp(KeyCode.KEY_S)">
                  <div
                    class="joystick-btn w-7 h-7 bg-gray-700 hover:bg-gray-600 rounded-full flex items-center justify-center text-xs text-white font-medium">
                    S</div>
                </div>
                <div class="absolute -left-2 cursor-pointer" @mousedown="onMouseDown(KeyCode.KEY_A)"
                  @mouseup="onMouseUp(KeyCode.KEY_A)" @mouseleave="onMouseUp(KeyCode.KEY_A)">
                  <div
                    class="joystick-btn w-7 h-7 bg-gray-700 hover:bg-gray-600 rounded-full flex items-center justify-center text-xs text-white font-medium">
                    A</div>
                </div>
                <div class="absolute -right-2 cursor-pointer" @mousedown="onMouseDown(KeyCode.KEY_D)"
                  @mouseup="onMouseUp(KeyCode.KEY_D)" @mouseleave="onMouseUp(KeyCode.KEY_D)">
                  <div
                    class="joystick-btn w-7 h-7 bg-gray-700 hover:bg-gray-600 rounded-full flex items-center justify-center text-xs text-white font-medium">
                    D</div>
                </div>
                <span class="text-sm text-gray-400 font-bold">R</span>
              </div>
              <!-- 云台控制 -->
              <div
                class="joystick-circle relative w-24 h-24 bg-gray-800 rounded-full border-2 border-gray-600 flex items-center justify-center">
                <div class="absolute -top-2 cursor-pointer" @mousedown="handleCameraDrag(5, 0)"
                  @mouseup="stopCameraDrag" @mouseleave="stopCameraDrag">
                  <div
                    class="joystick-btn w-7 h-7 bg-gray-700 hover:bg-gray-600 rounded-full flex items-center justify-center text-white">
                    <CaretUpOutlined />
                  </div>
                </div>
                <div class="absolute -bottom-2 cursor-pointer" @mousedown="handleCameraDrag(-5, 0)"
                  @mouseup="stopCameraDrag" @mouseleave="stopCameraDrag">
                  <div
                    class="joystick-btn w-7 h-7 bg-gray-700 hover:bg-gray-600 rounded-full flex items-center justify-center text-white">
                    <CaretDownOutlined />
                  </div>
                </div>
                <div class="absolute -left-2 cursor-pointer" @mousedown="handleCameraDrag(0, -5)"
                  @mouseup="stopCameraDrag" @mouseleave="stopCameraDrag">
                  <div
                    class="joystick-btn w-7 h-7 bg-gray-700 hover:bg-gray-600 rounded-full flex items-center justify-center text-white">
                    <CaretLeftOutlined />
                  </div>
                </div>
                <div class="absolute -right-2 cursor-pointer" @mousedown="handleCameraDrag(0, 5)"
                  @mouseup="stopCameraDrag" @mouseleave="stopCameraDrag">
                  <div
                    class="joystick-btn w-7 h-7 bg-gray-700 hover:bg-gray-600 rounded-full flex items-center justify-center text-white">
                    <CaretRightOutlined />
                  </div>
                </div>
                <span class="text-sm text-gray-400 font-bold">C</span>
              </div>
            </div>

            <!-- 控制按钮区 - 2列 -->
            <div class="col-span-2 flex flex-col gap-1 justify-center border-l border-gray-700 pl-2">
              <div class="grid grid-cols-2 gap-1">
                <div
                  class="flex items-center justify-center text-white text-[10px] h-6 bg-red-600 cursor-pointer rounded hover:bg-red-500 transition-all"
                  @click="handleEmergencyStopHandler">
                  <svg-icon :size="12" name="stopImm" class="mr-0.5" />急停
                </div>
                <Button :ghost="!flightController" size="small" class="text-[10px] h-6 px-1"
                  @click="onClickFightControl">{{
                    flightController ? '释放' : '控制' }}</Button>
              </div>
              <div class="grid grid-cols-2 gap-1">
                <!-- <Button ghost size="small" class="text-[10px] h-6 px-1" @click="openDrone">开机</Button> -->
                <div v-for="(cmdItem) in cmdList.slice(0, 1)" :key="cmdItem.cmdKey">
                  <Button :loading="cmdItem.loading" ghost size="small" class="text-[10px] h-6 px-1 w-full"
                    @click="sendControlCmd(cmdItem, 0)">{{ cmdItem.operateText }}</Button>
                </div>
                <div v-for="(cmdItem) in cmdList.slice(1, 4)" :key="cmdItem.cmdKey">
                  <Button :loading="cmdItem.loading" ghost size="small" class="text-[10px] h-6 px-1 w-full"
                    @click="sendControlCmd(cmdItem, 0)">{{ cmdItem.operateText }}</Button>
                </div>
              </div>
              <!-- 航线暂停/恢复按钮 -->
              <div class="grid grid-cols-2 gap-1">
                <Button :loading="waylinePauseLoading" ghost size="small" class="text-[10px] h-6 px-1"
                  @click="handleWaylinePause">航线暂停</Button>
                <Button :loading="waylineResumeLoading" ghost size="small" class="text-[10px] h-6 px-1"
                  @click="handleWaylineResume">航线恢复</Button>
              </div>
              <div>
                <Button ghost size="samll" class="text-[10px] h-6 px-1 w-full" @click="testFlightControl">抢夺控制权</Button>
              </div>
              <!-- <div>
                <Button ghost size="small" class="text-[10px] h-6 px-1 w-full"
                  @click="testReleaseControl">测试释放控制权</Button>
              </div> -->
              <div class="mt-1">
                <a-popover title="操控速度设置" trigger="click" placement="left">
                  <template #content>
                    <div class="space-y-3 p-1 w-56">
                      <div class="flex items-center justify-between">
                        <span class="text-gray-600 text-xs font-bold">水平速度 (%)</span>
                        <a-input-number size="small" v-model:value="controlSpeeds.horizontal" :min="1" :max="100"
                          class="w-20" :step="10" />
                      </div>
                      <div class="flex items-center justify-between">
                        <span class="text-gray-600 text-xs font-bold">垂直速度 (%)</span>
                        <a-input-number size="small" v-model:value="controlSpeeds.vertical" :min="1" :max="100"
                          class="w-20" :step="10" />
                      </div>
                      <div class="flex items-center justify-between">
                        <span class="text-gray-600 text-xs font-bold">旋转速度 (%)</span>
                        <a-input-number size="small" v-model:value="controlSpeeds.yaw" :min="1" :max="100" class="w-20"
                          :step="10" />
                      </div>
                    </div>
                  </template>
                  <Button ghost size="small"
                    class="text-[10px] h-6 px-1 w-full !flex !items-center !justify-center gap-1">
                    <svg-icon name="setting" :size="11" class="shrink-0" />
                    <span class="text-[10px] leading-none">速度设置</span>
                  </Button>
                </a-popover>
              </div>
            </div>

            <!-- 一键起飞和操作面板 - 同一行 -->
            <div class="col-span-2 flex items-center justify-center gap-1 border-l border-gray-700 pl-2">
              <DroneControlPopover :loading="takeoffToPointPopoverData.loading"
                :visible="takeoffToPointPopoverData.visible" @cancel="($event: any) => onTakeoffToPointConfirm(false)"
                @confirm="($event: any) => onTakeoffToPointConfirm(true)">
                <template #formContent>
                  <div class="form-content">
                    <div><span class="form-label">纬度</span><a-input-number
                        v-model:value="takeoffToPointPopoverData.latitude" /></div>
                    <div><span class="form-label">经度</span><a-input-number
                        v-model:value="takeoffToPointPopoverData.longitude" /></div>
                    <div><span class="form-label">安全起飞高度(米)<a-tooltip><template
                            #title>相对(机场)起飞点的高度（ALT），飞行器先升到特定的高度，然后再飞向目标点。</template>
                          <InfoCircleOutlined />
                        </a-tooltip></span><a-input-number
                        v-model:value="takeoffToPointPopoverData.securityTakeoffHeight" />
                    </div>
                    <div><span class="form-label">返航高度(米)<a-tooltip><template #title>相对(机场)起飞点的高度，相对高 ALT</template>
                          <InfoCircleOutlined />
                        </a-tooltip></span><a-input-number v-model:value="takeoffToPointPopoverData.rthAltitude" />
                    </div>
                    <div><span class="form-label">遥控器失控动作:</span><a-select
                        v-model:value="takeoffToPointPopoverData.rcLostAction"
                        :options="LostControlActionInCommandFLightOptions" style="width: 120px"></a-select></div>
                    <div><span class="form-label">返航模式:</span><a-select
                        v-model:value="takeoffToPointPopoverData.rthMode" :options="RthModeInCommandFlightOptions"
                        style="width: 120px"></a-select></div>
                    <div><span class="form-label">指点飞行失控动作:</span><a-select
                        v-model:value="takeoffToPointPopoverData.commanderModeLostAction"
                        :options="CommanderModeLostActionInCommandFlightOptions" style="width: 120px"></a-select></div>
                    <div><span class="form-label">指点飞行模式:</span><a-select
                        v-model:value="takeoffToPointPopoverData.commanderFlightMode"
                        :options="CommanderFlightModeInCommandFlightOptions" style="width: 120px"></a-select></div>
                    <div><span class="form-label">指点飞行高度(米)<a-tooltip><template #title>相对(机场)起飞点的高度，相对高 ALT</template>
                          <InfoCircleOutlined />
                        </a-tooltip></span><a-input-number
                        v-model:value="takeoffToPointPopoverData.commanderFlightHeight" /></div>
                  </div>
                </template>
              </DroneControlPopover>
              <a-tooltip title="一键起飞">
                <div class="takeoff-btn-1080p cursor-pointer hover:scale-110 transition-transform"
                  @click="onShowTakeoffToPointPopover">
                  <svg width="200" height="200" aria-hidden="true">
                    <use href="#icon-oneStepFly"></use>
                  </svg>
                </div>
              </a-tooltip>

            </div>

          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 指点飞行进度浮窗 -->
  <div v-if="flyToPointProgress.visible"
    class="fixed top-20 left-1/2 transform -translate-x-1/2 z-[9999] bg-gray-900/95 backdrop-blur-sm rounded-lg shadow-2xl border border-gray-600 px-4 py-3 min-w-[280px]">
    <div class="flex items-center justify-between mb-2">
      <span class="text-white text-sm font-bold flex items-center gap-2">
        <span class="text-blue-400">✈️</span>
        飞行进度
        <span v-if="flyToPointProgress.status === 'wayline_progress'" class="text-green-400 text-xs animate-pulse">●
          飞行中</span>
        <span v-else-if="flyToPointProgress.status === 'task_ready'" class="text-yellow-400 text-xs">● 任务准备</span>
        <span v-else-if="flyToPointProgress.status === 'wayline_ok'" class="text-green-500 text-xs">✓ 已到达</span>
      </span>
      <div class="cursor-pointer text-gray-400 hover:text-white transition-colors p-1"
        @click="flyToPointProgress.visible = false">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
        </svg>
      </div>
    </div>
    <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
      <div class="text-gray-400">剩余距离:</div>
      <div class="text-white font-medium">{{ flyToPointProgress.remainingDistance.toFixed(1) }} m</div>
      <div class="text-gray-400">预计时间:</div>
      <div class="text-white font-medium">{{ flyToPointProgress.remainingTime }} 秒</div>
      <div class="text-gray-400">航点索引:</div>
      <div class="text-white font-medium">{{ flyToPointProgress.wayPointIndex }}</div>
    </div>
    <!-- 进度条 (基于剩余距离的视觉反馈) -->
    <div v-if="flyToPointProgress.status === 'wayline_progress'" class="mt-2">
      <div class="h-1.5 bg-gray-700 rounded-full overflow-hidden">
        <div class="h-full bg-gradient-to-r from-blue-500 to-cyan-400 transition-all duration-300"
          :style="{ width: flyToPointProgress.initialDistance > 0 ? ((1 - flyToPointProgress.remainingDistance / flyToPointProgress.initialDistance) * 100) + '%' : '0%' }">
        </div>
      </div>
    </div>
  </div>


  <!-- 无人机已设置属性配置 Modal -->
  <a-modal
    v-model:visible="dronePropsModalVisible"
    title="无人机已设置属性配置"
    :footer="null"
    width="780px"
    destroy-on-close
  >
    <DronePropertiesPanel
      v-if="dronePropsModalVisible"
      :device-sn="deviceSn || deviceInfo?.dock?.basic_osd?.sub_device?.device_sn"
      :gateway-sn="dockSn"
      title="控制通道"
      :dark-theme="true"
      :auto-refresh="true"
    />
  </a-modal>
</template><!--suppress TypeScriptValidateTypes -->
<script lang="ts" setup>
import { h } from 'vue';
import DjiCesium from "@/components/DjiCesium.vue";
import CockpitWaypointRecorder from "@/components/wayLine/CockpitWaypointRecorder.vue";
import webRtcPlay from "@/components/webRtcPlay.vue";
import {
  onMounted,
  onUnmounted,
  reactive,
  ref,
  shallowRef,
  watch,
  computed,
  nextTick,
  provide,
  onBeforeUnmount
} from "vue";
import { DeviceCmd, DeviceCmdItem, noDebugCmdList as baseCmdList } from '@/types/device-cmd'

import { KeyCode, useManualControl, ControlSpeeds } from '@/components/g-map/use-manual-control'
import { DeviceTopicInfo, useMqtt } from '@/components/g-map/use-mqtt'
import { postDrcEnter, postDrcExit } from '@/api/drc'

import { Button, message, notification, Modal, InputNumber } from "ant-design-vue";
import {
  ECommanderFlightMode,
  ECommanderModeLostAction,
  ERthMode,
  LostControlActionInCommandFLight,
  postFlightAuth,
  WaylineLostControlActionInCommandFlight
} from "@/api/drone-control/drone";
import {
  CommanderFlightModeInCommandFlightOptions,
  CommanderModeLostActionInCommandFlightOptions,
  LostControlActionInCommandFLightOptions,
  RthModeInCommandFlightOptions, TakeoffToPointMessage
} from "@/types/drone-control";
import { InfoCircleOutlined, ExpandOutlined, RobotOutlined, CaretUpOutlined, CaretDownOutlined, CaretLeftOutlined, CaretRightOutlined } from "@ant-design/icons-vue";
import DroneControlPopover from "@/components/g-map/DroneControlPopover.vue";
import {
  ControlSource,
  DeviceOsd,
  DeviceOsdCamera,
  DockOsd,
  EDockModeCode,
  EDockModeCodeZh,
  EModeCode,
  EModeWord,
  GatewayOsd,
  OnlineDevice,
  WindDirectionEnum,
  DeviceHms,
  PAYLOAD_TYPE
} from "@/types/device";
import { useDroneControl } from "@/components/g-map/use-drone-control";
import { useDockControl } from "@/components/g-map/use-dock-control";
import { useDroneControlMqttEvent } from "@/components/g-map/use-drone-control-mqtt-event";
import { changeLivestreamLens, getDeviceTopo, setLivestreamQuality, startLivestream, stopLivestream, getDeviceHms, HmsQueryBody, postAeroSpotCollect, getPayloadList, getReturnHomeBattery, checkClientIdExists, kickClientId } from "@/api/manage";
import { useConnectWebSocket } from '@/hooks/use-connect-websocket'

//@ts-ignore
import srs from '@/vendors/srs1.sdk.js'
import { useMyStore } from "@/store";
import SvgIcon from "@/components/svgIcon.vue";
import { EBizCode, EDeviceTypeName, ELocalStorageKey, EHmsLevel } from "@/types";
import { useDroneControlWsEvent } from "@/components/g-map/use-drone-control-ws-event";
import { usePayloadControl } from "@/components/g-map/use-payload-control";
import { CameraMode, CameraModeLabel, CameraType, CameraTypeOptions } from "@/types/live-stream";
import { useOnnxDetection, ONNX_MODEL_CONFIGS } from "@/hooks/use-onnx-detection";
import { postExitAuthority, getAuthoriyInfo, grabCloudControl, releaseCloudControl } from "@/api/drone-control/authority";
import { CURRENT_CONFIG } from "@/api/http/config";
import { getSTSTokenApi } from "@/api/minio-api";
import { UranusMqtt } from "@/mqtt";
import { postDrc } from "@/api/drc";
import { dockCmdResponseTime, payloadCmdResponseTime } from "@/api/http/cmd-responseTime";
import { getWaylineListByDockSn, getWaylineData, createPlan, CreatePlan, pauseOrResumeWayline, getFlightId, lingxiuSwitch } from "@/api/wayline";
import { TaskType, OutOfControlAction } from "@/types/task";
import { WaylineType } from "@/types/wayline";
import { useRoute } from "vue-router";
import { useEventEmitter, useFavicon, useLocalStorageState, useTimeout, useTitle } from "vue-hooks-plus";
import EventBus from "@/event-bus";
import { DroneOpen, PSDKFloatingWindowText } from "@/types/virtaulCockpit";
import moment from "moment";

import { SlidersOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { DronePropertiesPanel } from "@/components/drone-properties";

const isFullScreenFlight = ref(false)
const toggleFullScreenFlight = () => { isFullScreenFlight.value = !isFullScreenFlight.value }

const dronePropsModalVisible = ref(false)

// MQTT 重置逻辑
const mqttResetLoading = ref(false)
async function resetMqtt() {
  let targetSn = dockSn.value || deviceSn.value
  if (!targetSn) {
    notification.warning({
      message: '重置失败',
      description: '未获取到当前设备的有效序列号(SN)',
      duration: 2,
    })
    return
  }

  mqttResetLoading.value = true
  try {
    let res = await checkClientIdExists(targetSn)
    let clients = Array.isArray(res) ? res : (res?.data || [])

    // 若当前 targetSn 未查到客户端且存在不同的 deviceSn，尝试 deviceSn
    if ((!clients || clients.length === 0) && deviceSn.value && deviceSn.value !== targetSn) {
      const devRes = await checkClientIdExists(deviceSn.value)
      const devClients = Array.isArray(devRes) ? devRes : (devRes?.data || [])
      if (devClients && devClients.length > 0) {
        targetSn = deviceSn.value
        clients = devClients
      }
    }

    if (clients && clients.length > 0) {
      const kickRes = await kickClientId(targetSn)
      if (kickRes) {
        console.log(`✅ 成功踢除设备连接: ${targetSn}`)
        notification.success({
          message: '踢除连接成功',
          description: `已重置设备 (${targetSn}) MQTT 客户端，正在重新连接`,
          duration: 3,
        })
      }
    } else {
      notification.success({
        message: '重置成功',
        description: '设备当前在 MQTT 服务端未保持活跃连接',
        duration: 3,
      })
    }
  } catch (error: any) {
    console.error('resetMqtt error:', error)
    notification.error({
      message: '踢除失败',
      description: error?.message || 'MQTT 重置失败',
      duration: 3,
    })
  } finally {
    mqttResetLoading.value = false
  }
}

const mqttState = ref<UranusMqtt | null>(null)
const route = useRoute()
const dockSn = computed<string>(() => {
  const sn = route.query.dockSn
  if (Array.isArray(sn)) return sn[0] ?? ''
  return sn ?? ''
})
const dockName = computed(() => {
  return route.query.dockName
})
const clientId = computed<string>(() => {
  const id = route.query.clientId
  if (Array.isArray(id)) return id[0] ?? ''
  return id ?? ''
})
const deviceSn = computed<string>(() => {
  const sn = route.query.deviceSn
  if (Array.isArray(sn)) return sn[0] ?? ''
  return sn ?? ''
})

const model = computed<string>(() => {
  const m = route.query.model
  if (Array.isArray(m)) return m[0] ?? ''
  return m ?? ''
})

const cameraPayloadEnumVal = computed(() => {
  const m = String(model.value || '').toUpperCase()
  // 注意：更长的型号名必须在前面匹配，避免短名误匹配（如 M3TD 被 M3T 先截获）
  if (m.includes('M30T')) return PAYLOAD_TYPE.M30T
  if (m.includes('M30D')) return PAYLOAD_TYPE.M30D
  if (m.includes('M30')) return PAYLOAD_TYPE.M30T // Default to M30T if it is just M30
  if (m.includes('M3TD')) return PAYLOAD_TYPE.M3TD
  if (m.includes('M3E')) return PAYLOAD_TYPE.M3E
  if (m.includes('M3T')) return PAYLOAD_TYPE.M3T
  if (m.includes('M3D')) return PAYLOAD_TYPE.M3D
  if (m.includes('M4TD')) return PAYLOAD_TYPE.M4TD
  if (m.includes('M4D')) return PAYLOAD_TYPE.M4D
  if (m.includes('H20T')) return PAYLOAD_TYPE.H20T
  if (m.includes('H20N')) return PAYLOAD_TYPE.H20N
  if (m.includes('H20')) return PAYLOAD_TYPE.H20
  if (m.includes('FPV')) return PAYLOAD_TYPE.FPV
  
  // Fallback check against actual PAYLOAD_TYPE keys
  const matchedKey = Object.keys(PAYLOAD_TYPE).find(k => m.includes(k.toUpperCase()))
  if (matchedKey) {
    return PAYLOAD_TYPE[matchedKey as keyof typeof PAYLOAD_TYPE]
  }
  
  // Fallback to M30T
  return PAYLOAD_TYPE.M30T
})

const cameraPayloadIndex = computed(() => {
  return cameraPayloadEnumVal.value + '-0-0'
})

const str: string = '--'

const deviceInfo = reactive<{
  device: Partial<DeviceOsd>,
  dock: Partial<DockOsd>
}>({
  device: {},
  dock: {
    basic_osd: {},
    link_osd: {},
    work_osd: {
      sub_device: {
        device_sn: ''
      } as any
    },
  } as any
})

const { flyToPoint, stopFlyToPoint, takeoffToPoint } = useDroneControl()
const initCmdList = baseCmdList.map(cmdItem => Object.assign({}, cmdItem))
const cmdList = ref(initCmdList)
const droneStartupPercent = ref(0) // 无人机开机进度百分比
const psdkShow = ref(false)
const dronePayloadList = ref<any[]>([])
const payloadListVisible = ref(false)
const aeroSpotVisible = ref(false)
const aeroSpotContinuousCollecting = ref(false)
const aeroSpotPreparedFlightId = ref<string>('') // 记录本次飞行任务是否已做过连续采集前置动作
const laserRangefinderEnabled = ref(false)

// 激光测距状态枚举映射
const MeasureTargetErrorStateMap: Record<number, { label: string; color: string }> = {
  0: { label: '正常', color: 'text-green-400' },
  1: { label: '过近', color: 'text-yellow-400' },
  2: { label: '过远', color: 'text-yellow-400' },
  3: { label: '无信号', color: 'text-red-400' },
}

// 获取激光测距数据 (从相机 payload 的 OSD 数据中获取)
const laserMeasureData = computed(() => {
  const cameras = deviceInfo.device?.cameras
  if (!cameras || cameras.length === 0) return null
  const payloadIndex = cameraPayloadIndex.value
  if (!payloadIndex) return null
  const cameraOsd = (deviceInfo.device as any)?.[payloadIndex]
  if (!cameraOsd) return null
  return {
    longitude: cameraOsd.measure_target_longitude,
    latitude: cameraOsd.measure_target_latitude,
    altitude: cameraOsd.measure_target_altitude,
    distance: cameraOsd.measure_target_distance,
    errorState: cameraOsd.measure_target_error_state,
  }
})

// 指点飞行进度状态
const flyToPointProgress = reactive({
  visible: false,
  status: '', // task_ready, wayline_progress, wayline_ok
  flightId: '',
  wayPointIndex: 0,
  remainingDistance: 0,
  initialDistance: 0, // 初始距离，用于计算进度百分比
  remainingTime: 0,
  plannedPathPoints: [] as any[]
})

const djiCesiumRef = ref<any>(null)
const onViewerReady = () => {
  djiCesiumRef.value?.tryFlyToDock(dockMapLocation.value)
}

// 标记点相关
const markedPoint = ref(null)

// 航线列表相关
const waylineVisible = ref(false)
const waylineList = ref<any[]>([])
const waylineLoading = ref(false)

// 获取航线列表
const handleWaylineClick = async () => {
  waylineLoading.value = true
  waylineVisible.value = true
  try {
    const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''
    const res = await getWaylineListByDockSn(workspaceId, dockSn.value)
    if (res.code === 0 && res.data?.list) {
      waylineList.value = res.data.list
    } else {
      waylineList.value = []
      message.warning('暂无航线数据')
    }
  } catch (error: any) {
    console.error('获取航线列表错误:', error)
    message.error('获取航线列表失败: ' + (error.message || '未知错误'))
    waylineList.value = []
  } finally {
    waylineLoading.value = false
  }
}

// 选择航线
const selectWayline = (wayline: any) => {
  // TODO: 根据需求处理航线选择
  message.success(`已选择航线: ${wayline.name}`)
  waylineVisible.value = false
}

// 航线绘制相关
const waylineDrawingId = ref<string | null>(null)
const waylinePoints = ref<any[]>([])

// 手动打点录入的航线点位
const waypointRecorderRef = ref<any>(null)
const recordedWaylinePoints = ref<any[]>([])
const onRecordedWaypointsUpdate = (points: any[]) => {
  recordedWaylinePoints.value = points
}
const onFlyToRecordedWaypoint = (wp: any) => {
  djiCesiumRef.value?.flyToCoordinate?.(wp.longitude, wp.latitude, wp.absoluteHeight || wp.height, 300)
}
const displayWaylinePoints = computed(() => {
  if (recordedWaylinePoints.value && recordedWaylinePoints.value.length > 0) {
    return recordedWaylinePoints.value
  }
  return waylinePoints.value
})

// 绘制航线到地图
const drawWaylineOnMap = async (wayline: any) => {
  waylineDrawingId.value = wayline.id
  try {
    const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''
    const res = await getWaylineData(workspaceId, wayline.id)
    console.log('航线点位数据:', res)

    if (res.code === 0 && res.data) {
      // 解析航线点位数据
      const points = parseWaylinePoints(res.data)

      // 获取起飞点高度
      const takeoffHeight = (deviceInfo.device?.height || 0) - (deviceInfo.device?.elevation || 0)

      // 将相对高度转换为绝对高度 (加上起飞点高度)
      points.forEach(p => {
        p.height = Number(p.height || 0) + Number(takeoffHeight)
      })

      if (points.length > 0) {
        // 先清空现有航线，确保触发完整的响应式更新
        waylinePoints.value = []
        // 使用 nextTick 确保清空操作被处理后再赋新值
        await nextTick()
        waylinePoints.value = points
        message.success(`已加载航线 "${wayline.name}"，共 ${points.length} 个点位`)
        waylineVisible.value = false
      } else {
        message.warning('航线无有效点位数据')
      }
    } else {
      message.error(res.message || '获取航线数据失败')
    }
  } catch (error: any) {
    console.error('获取航线点位数据错误:', error)
    message.error('获取航线数据失败: ' + (error.message || '未知错误'))
  } finally {
    waylineDrawingId.value = null
  }
}

// 航线执行相关
const waylineExecutingId = ref<string | null>(null)
const tempRthAltitude = ref(100) // 临时返航高度

// 执行航线任务（创建立即执行的任务）
const executeWayline = async (wayline: any) => {
  drawWaylineOnMap(wayline)
  await dockDebugOnOff(dockSn.value, false)

  // 确认对话框，让用户设置返航高度
  Modal.confirm({
    title: h('span', { class: 'text-white' }, '确认执行航线任务?'),
    content: () => h('div', { class: 'text-white space-y-3' }, [
      h('div', { class: 'mb-2' }, `航线: ${wayline.name}`),
      h('div', { class: 'flex items-center gap-2' }, [
        h('span', '返航高度:'),
        h(InputNumber, {
          value: tempRthAltitude.value,
          'onUpdate:value': (val: number) => tempRthAltitude.value = val,
          min: 20,
          max: 1500,
          class: 'bg-gray-700 text-white border-gray-600 w-32'
        }),
        h('span', '米')
      ]),
      h('div', { class: 'text-gray-400 text-xs mt-2' }, '任务将立即开始执行')
    ]),
    okText: '确认执行',
    cancelText: '取消',
    zIndex: 10001,
    class: 'dark-confirm-modal',
    onOk: async () => {
      waylineExecutingId.value = wayline.id
      try {
        const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''

        // 构建任务参数
        const planBody: CreatePlan = {
          name: wayline.name + '_' + new Date().toLocaleString('zh-CN', {
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
          }).replace(/\//g, '').replace(/:/g, '').replace(' ', '_'),
          file_id: wayline.id,
          dock_sn: dockSn.value,
          task_type: TaskType.Immediate, // 立即执行
          wayline_type: wayline.template_types?.[0] ?? WaylineType.NormalWaypointWayline,
          task_days: [], // 立即执行不需要日期
          task_periods: [], // 立即执行不需要时间段
          rth_altitude: tempRthAltitude.value,
          out_of_control_action: OutOfControlAction.ReturnToHome, // 失控返航
        }

        message.loading({ content: '正在创建航线任务...', key: 'execute-wayline' })

        const res = await createPlan(workspaceId, planBody)

        if (res.code === 0) {
          message.success({ content: `航线任务 "${wayline.name}" 已创建并开始执行`, key: 'execute-wayline' })
          waylineVisible.value = false
        } else {
          message.error({ content: '创建任务失败: ' + (res.message || '未知错误'), key: 'execute-wayline' })
        }
      } catch (error: any) {
        console.error('执行航线任务错误:', error)
        message.error({ content: '执行航线失败: ' + (error.message || '未知错误'), key: 'execute-wayline' })
      } finally {
        waylineExecutingId.value = null
      }
    }
  })
}

// 获取无人机已搭载负载
const fetchDronePayloads = async () => {
  try {
    const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''
    if (!workspaceId) return
    const res = await getPayloadList(workspaceId)
    if (res) {
      const sn = deviceSn.value
      dronePayloadList.value = sn && res[sn] ? res[sn] : []
    }
  } catch (e) {
    console.warn('获取负载列表失败', e)
  }
}

// 获取配置的预留返航剩余电量
const returnHomeBattery = ref<number | undefined>(undefined)
const fetchReturnHomeBatteryData = async () => {
  try {
    const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''
    if (!workspaceId || !deviceSn.value) return
    const res = await getReturnHomeBattery(workspaceId, deviceSn.value)
    if (res.code === 0 && res.data && res.data.remaining_power_for_return_home !== undefined) {
      returnHomeBattery.value = res.data.remaining_power_for_return_home
    }
  } catch (e) {
    console.warn('获取预留返航电量失败', e)
  }
}

// HMS 信息相关
const hmsVisible = ref(false)
const hmsList = ref<DeviceHms[]>([])
const hmsLoading = ref(false)
const hmsWarningCount = ref(0)
const hmsQueryParams = reactive<HmsQueryBody>({
  device_sn: '',
  language: 'zh', // 默认中文
  level: '',
  begin_time: new Date().getTime() - 60 * 60 * 1000 * 24 * 2, // 最近48小时
  end_time: new Date().getTime(),
  message: ''
})

// 获取 HMS 信息的颜色
const getHmsLevelColor = (level: number) => {
  switch (level) {
    case EHmsLevel.NOTICE: return 'text-green-400'
    case EHmsLevel.CAUTION: return 'text-yellow-400'
    case EHmsLevel.WARN: return 'text-red-400'
    default: return 'text-gray-400'
  }
}

// 获取 HMS 信息的背景色 (用于标记点)
const getHmsLevelBgColor = (level: number) => {
  switch (level) {
    case EHmsLevel.NOTICE: return 'bg-green-400'
    case EHmsLevel.CAUTION: return 'bg-yellow-400'
    case EHmsLevel.WARN: return 'bg-red-400'
    default: return 'bg-gray-400'
  }
}

// 获取 HMS 列表
const handleHmsClick = async () => {
  hmsVisible.value = !hmsVisible.value
  if (hmsVisible.value) {
    await fetchHmsData()
  }
}

// 加载 HMS 数据
const fetchHmsData = async () => {
  hmsLoading.value = true
  try {
    const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''

    // 设置查询参数
    const droneSn = deviceSn.value
    const currentDockSn = dockSn.value

    // 只查最近的警告
    const pagination = { page: 1, page_size: 50 } as IPage

    const promises = []

    // 1. Query Dock
    if (currentDockSn) {
      const p = { ...hmsQueryParams, device_sn: currentDockSn }
      promises.push(getDeviceHms(p, workspaceId, pagination).then(res => ({ ...res, sn: currentDockSn })))
    }

    // 2. Query Drone
    if (droneSn) {
      const p = { ...hmsQueryParams, device_sn: droneSn }
      promises.push(getDeviceHms(p, workspaceId, pagination).then(res => ({ ...res, sn: droneSn })))
    }

    const results = await Promise.all(promises)
    let mergedList: any[] = []

    results.forEach((res: any) => {
      if (res.code === 0 && res.data?.list) {
        // Tag domain
        const isDrone = res.sn === droneSn
        res.data.list.forEach((item: any) => {
          item.domain = isDrone ? EDeviceTypeName.Aircraft : EDeviceTypeName.Dock
          item.sn = res.sn
        })
        mergedList = mergedList.concat(res.data.list)
      }
    })

    // Sort
    mergedList.sort((a, b) => b.create_time - a.create_time)

    hmsList.value = mergedList
    hmsWarningCount.value = hmsList.value.filter(h => h.level === EHmsLevel.WARN || h.level === EHmsLevel.CAUTION).length

  } catch (error) {
    console.error('获取HMS信息失败:', error)
    message.error('获取设备告警信息失败')
  } finally {
    hmsLoading.value = false
  }
}

// 定时刷新 HMS 数据以保持告警提醒实时性
let hmsAutoRefreshTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  // 进入页面立即获取一次
  fetchHmsData()
  fetchReturnHomeBatteryData()
  // 开启每10秒自动刷新
  hmsAutoRefreshTimer = setInterval(() => {
    // 当弹窗打开时，暂停自动刷新，以免影响用户阅读
    if (!hmsVisible.value) {
      fetchHmsData()
    }
  }, 10000)
})

onUnmounted(() => {
  if (hmsAutoRefreshTimer) {
    clearInterval(hmsAutoRefreshTimer)
    hmsAutoRefreshTimer = null
  }
  if (distanceToGroundTimer) {
    clearInterval(distanceToGroundTimer)
    distanceToGroundTimer = null
  }
})

// 距地高度计算
const distanceToGround = ref(0)
let distanceToGroundTimer: ReturnType<typeof setInterval> | null = null

const updateDistanceToGround = async () => {
  if (!djiCesiumRef.value || !deviceInfo.device || !deviceInfo.device.latitude || !deviceInfo.device.longitude) {
    return
  }

  const { latitude, longitude, height } = deviceInfo.device
  try {
    const terrainHeight = await djiCesiumRef.value.getTerrainHeight(Number(longitude), Number(latitude))
    const currentHeight = Number(height) || 0
    // 如果获取到的地面高度是负数（比如海洋）或者异常值，可能需要处理，这里暂时直接相减
    // 限制最小值为0，避免数据显示负数（除非钻地）
    let agl = currentHeight - terrainHeight
    distanceToGround.value = agl
  } catch (error) {
    console.error('计算距地高度出错', error)
  }
}

onMounted(() => {
  // 开启距地高度定时刷新
  distanceToGroundTimer = setInterval(() => {
    updateDistanceToGround()
  }, 1000)
})

// 解析航线点位数据
const parseWaylinePoints = (data: any): any[] => {
  const points: any[] = []

  // 尝试解析航线数据结构
  // 根据实际API返回的数据结构进行调整
  if (data.route_point_list && Array.isArray(data.route_point_list)) {
    // route_point_list 结构 (实际API返回格式)
    data.route_point_list.forEach((point: any) => {
      if (point.longitude && point.latitude) {
        points.push({
          longitude: point.longitude,
          latitude: point.latitude,
          height: point.height || 100
        })
      }
    })
  } else if (data.points && Array.isArray(data.points)) {
    // 直接有 points 数组
    data.points.forEach((point: any) => {
      if (point.longitude && point.latitude) {
        points.push({
          longitude: point.longitude,
          latitude: point.latitude,
          height: point.height || point.altitude || 100
        })
      }
    })
  } else if (data.waylineData && Array.isArray(data.waylineData)) {
    // waylineData 结构
    data.waylineData.forEach((item: any) => {
      if (item.longitude && item.latitude) {
        points.push({
          longitude: item.longitude,
          latitude: item.latitude,
          height: item.height || item.altitude || 100
        })
      }
    })
  } else if (data.coordinates && Array.isArray(data.coordinates)) {
    // GeoJSON 格式
    data.coordinates.forEach((coord: any) => {
      if (Array.isArray(coord) && coord.length >= 2) {
        points.push({
          longitude: coord[0],
          latitude: coord[1],
          height: coord[2] || 100
        })
      }
    })
  } else if (Array.isArray(data)) {
    // 直接是数组
    data.forEach((item: any) => {
      if (item.longitude && item.latitude) {
        points.push({
          longitude: item.longitude,
          latitude: item.latitude,
          height: item.height || item.altitude || 100
        })
      }
    })
  }

  return points
}

const showGimbalMenu = ref(false)
const gimbalOptions = [
  { key: '0', label: '云台回中' },
  { key: '1', label: '云台向下' },
  { key: '2', label: '偏航回中' },
  { key: '3', label: '俯仰向下' },
]
const handleGimbalSelect = (item: any) => {
  ptzControl({ key: item.key })
  showGimbalMenu.value = false
}
const psdkContentText = ref('')

// 处理高光谱采集操作
const handleAeroSpotAction = async (value: number) => {
  try {
    aeroSpotVisible.value = false
    const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''
    if (!workspaceId) {
      message.error('未找到工作空间ID')
      return
    }


    const res = await postAeroSpotCollect(workspaceId, {
      dock_sn: dockSn.value,
      value: value
    })

    if (res.code === 0) {
      if (value === 8) {
        message.success('发送单次高光谱采集指令成功')
      }
      if (value === 10) {
        aeroSpotContinuousCollecting.value = true
        message.success('开启连续高光谱采集指令成功')
      }
      if (value === 11) {
        message.success('停止连续高光谱采集指令成功')
        aeroSpotContinuousCollecting.value = false
        try {
          const lingxiuRes = await lingxiuSwitch(dockSn.value, false)
          if (lingxiuRes.code === 0 && lingxiuRes.data?.enable_psdk_floating_window_text_record === false) {
            setLingXiuEnabled(false)
          } else {
            message.error('负载关闭失败: ' + (lingxiuRes.message || '服务端未确认关闭状态'))
          }
        } catch (e: any) {
          message.error('负载关闭失败: ' + (e?.message || '未知错误'))
        }
      }
    } else {
      message.error(res.message || '指令发送失败')
    }
  } catch (error: any) {
    message.error(error.message || '指令发送失败')
  }
}

// Telemetry marquee scroll detection
const telemetryContainerRef = ref<HTMLElement | null>(null)
const telemetryContentRef = ref<HTMLElement | null>(null)
const needsScroll = ref(false)
let telemetryResizeObserver: ResizeObserver | null = null

const checkTelemetryOverflow = () => {
  if (telemetryContainerRef.value && telemetryContentRef.value) {
    const containerWidth = telemetryContainerRef.value.clientWidth
    const contentWidth = telemetryContentRef.value.scrollWidth
    console.log('containerWidth', containerWidth)
    console.log('contentWidth', contentWidth)
    needsScroll.value = contentWidth > containerWidth
  }
}

const showQualityMenu = ref(false)



const [lingXiuEnabled, setLingXiuEnabled] = useLocalStorageState('ling-xiu-' + dockSn.value, {
  defaultValue: false
})

const realTimeVideoUrl = ref<string>('')
const isRealTimeRecognitionActive = ref(false)
const onnxConfigVisible = ref(false) // ONNX 配置面板可见性
const currentAiSelectionMode = ref<'ai_auto' | 'real_time'>('ai_auto')
const selectedOnnxModel = ref<string>('6') // 默认选择人员检测模型

// ONNX 检测 composable
const onnxDetection = useOnnxDetection({
  confidenceThreshold: 0.5,
  nmsThreshold: 0.45,
  inferenceInterval: 100
})

// 开启实况识别的核心逻辑 - 使用 ONNX 前端模型
const startRealTimeRecognition = async (modelId: string) => {
  // 获取 WebRTC 视频元素和 overlay canvas
  const videoElement = videowebrtc.value as HTMLVideoElement | null
  const overlayCanvasElement = document.getElementById('overlayCanvas') as HTMLCanvasElement | null

  if (!videoElement) {
    message.error('未找到视频元素')
    return
  }

  if (!overlayCanvasElement) {
    message.error('未找到绘制画布')
    return
  }

  // 检查视频是否已准备好
  if (playUrl.value == null) {
    message.error('未开启无人机直播。')
    return
  }

  // 映射后端模型ID到 ONNX 模型
  // 这里简化处理，可以根据需要扩展映射逻辑
  let onnxModelKey = '6' // 默认人员检测
  if (modelId === '6' || modelId === '人') {
    onnxModelKey = '6'
  } else if (modelId === '9' || modelId === 'fire') {
    onnxModelKey = '9'
  } else if (modelId === '25' || modelId === '车') {
    onnxModelKey = '25'
  } else if (modelId === 'biomass' || modelId === '烟') {
    onnxModelKey = 'biomass'
  } else if (modelId === '26' || modelId === '工程车') {
    onnxModelKey = '26'
  } else if (modelId === '27' || modelId === '无人机工程车') {
    onnxModelKey = '27'
  }

  selectedOnnxModel.value = onnxModelKey

  let hideLoading: any = null
  try {
    hideLoading = message.loading('正在加载 ONNX 模型...', 0)

    const success = await onnxDetection.startDetection(
      onnxModelKey,
      videoElement,
      overlayCanvasElement
    )

    if (success) {
      isRealTimeRecognitionActive.value = true
      message.success(`ONNX 实况识别已开启 (${ONNX_MODEL_CONFIGS[onnxModelKey]?.name || onnxModelKey})`)
    } else {
      message.error('ONNX 模型加载失败')
    }
  } catch (error) {
    console.error('启动 ONNX 识别失败:', error)
    message.error('启动实况识别失败')
  } finally {
    if (hideLoading) hideLoading()
  }
}

// 实况识别按钮点击处理
const handleRealTimeButtonClick = async () => {
  // 如果已经开启，则关闭
  if (isRealTimeRecognitionActive.value) {
    // 停止 ONNX 检测
    onnxDetection.stopDetection()

    // 清除 overlayCanvas
    const overlayCanvasElement = document.getElementById('overlayCanvas') as HTMLCanvasElement | null
    if (overlayCanvasElement) {
      const ctx = overlayCanvasElement.getContext('2d')
      if (ctx) {
        ctx.clearRect(0, 0, overlayCanvasElement.width, overlayCanvasElement.height)
      }
    }


    realTimeVideoUrl.value = '';
    isRealTimeRecognitionActive.value = false;
    onnxConfigVisible.value = false;
    message.info('实况识别已关闭');
    return;
  }

  // 如果未开启，切换配置面板显示状态
  onnxConfigVisible.value = !onnxConfigVisible.value
}

// 从配置面板确认开始 ONNX 识别
const confirmStartOnnxRecognition = async () => {
  onnxConfigVisible.value = false
  await startRealTimeRecognition(selectedOnnxModel.value)
}
// 开启负载后台记录开关
const handleLingXiuButtonClick = async () => {
  const targetState = !lingXiuEnabled.value
  try {

    const res = await lingxiuSwitch(dockSn.value, targetState)
    if (res.code === 0 && res.data?.enable_psdk_floating_window_text_record === targetState) {
      setLingXiuEnabled(targetState)
      message.success('负载后台记录' + (targetState ? '开启' : '关闭') + '成功')
    } else {
      message.error('负载后台记录' + (targetState ? '开启' : '关闭') + '失败: ' + (res.message || '服务端未确认目标状态'))
    }
  } catch (e) {
    console.error(e)
  }
}

const handleDroneControlWsEvent = (payload: any) => {
  if (!payload) return

  // 全局前置过滤：当打开虚拟座舱活动页面时，严格拦截任何与当前机场或设备无关的 WebSocket 广播数据，节省内存与解包开销
  if (payload.data) {
    const d = payload.data
    const sn = d.dock_sn || d.sn || d.gateway_sn || d.device_sn
    if (sn && sn !== dockSn.value && sn !== deviceSn.value) {
      return
    }
  }

  if (payload.biz_code === EBizCode.TakeoffToPointProgress) {
    const data = payload.data as TakeoffToPointMessage
    // 处理起飞进度数据
    console.log('起飞进度:', data)
    // 不是当前机场消息，丢弃
    if (data.dock_sn !== dockSn.value) {
      return
    }
    // 更新进度状态
    if (data.status === 'task_finish') {
      if (!isWatchOnly.value && !flightController.value) {
        console.log('一键起飞结束，自动获取控制权')
        enterFlightControl()
      }
      return
    }
    flyToPointProgress.visible = true
    flyToPointProgress.status = data.status || ''
    flyToPointProgress.flightId = data.flight_id || ''
    flyToPointProgress.wayPointIndex = data.way_point_index || 0
    flyToPointProgress.remainingDistance = data.remaining_distance || 0
    flyToPointProgress.remainingTime = data.remaining_time || 0
    flyToPointProgress.plannedPathPoints = data.planned_path_points || []
    // 设置初始距离（新任务或首次接收到非零距离时）
    if (flyToPointProgress.initialDistance === 0 && data.remaining_distance > 0) {
      flyToPointProgress.initialDistance = data.remaining_distance
    }

    // 如果任务完成，3秒后自动隐藏并重置初始距离
    if (data.status === 'wayline_ok') {
      if (!isWatchOnly.value && !flightController.value) {
        console.log('一键起飞到位，自动获取控制权')
        enterFlightControl()
      }
      setTimeout(() => {
        flyToPointProgress.visible = false
        flyToPointProgress.initialDistance = 0 // 重置初始距离
      }, 3000)
    }

  }
  if (payload.biz_code === EBizCode.DroneOpen) {
    const data = payload.data as DroneOpen
    // 不是当前机场消息，丢弃
    if (data.dock_sn !== dockSn.value) {
      return
    }
    console.log('开机进度:', data.output.progress.percent)
    droneStartupPercent.value = data.output.progress.percent
  }
  if (payload.biz_code === EBizCode.PSDKFloatingWindowText) {
    const data = payload.data as PSDKFloatingWindowText
    // console.log('负载数据:', data.text)

    if (dockSn.value === data.sn) {
      psdkContentText.value = data.text
    }
  }
  if (payload.biz_code === EBizCode.FlyToPointProgress) {
    const data = payload.data
    console.log('指点飞行进度:', data)
    // 不是当前机场消息，丢弃
    if (data.dock_sn !== dockSn.value) {
      return
    }
    flyToPointProgress.visible = true
    flyToPointProgress.status = data.status || ''
    flyToPointProgress.flightId = data.fly_to_id || '' // 指点飞行使用 fly_to_id
    flyToPointProgress.wayPointIndex = data.way_point_index || 0
    flyToPointProgress.remainingDistance = data.remaining_distance || 0
    flyToPointProgress.remainingTime = data.remaining_time || 0
    flyToPointProgress.plannedPathPoints = data.planned_path_points || []
    // 设置初始距离（新任务或首次接收到非零距离时）
    if (flyToPointProgress.initialDistance === 0 && data.remaining_distance > 0) {
      flyToPointProgress.initialDistance = data.remaining_distance
    }

    // 如果任务完成，3秒后自动隐藏并重置初始距离
    if (data.status === 'wayline_ok') {
      if (!isWatchOnly.value && !flightController.value) {
        console.log('指点飞行到位，自动获取控制权')
        enterFlightControl()
      }
      setTimeout(() => {
        flyToPointProgress.visible = false
        flyToPointProgress.initialDistance = 0 // 重置初始距离
        // 清除地图上的指点飞行路线
        djiCesiumRef.value?.clearFlightPathLine?.()

      }, 3000)
    }
  }
  if (payload.biz_code === EBizCode.FlightTaskProgress) {
    const data = payload.data
    if (data) {
      const output = data.output || {}
      const status = output.status
      const ext = output.ext || {}
      flyToPointProgress.visible = true
      if (status === 'Sent' || status === 'in_progress' || status === 'inProgress' || status === 'wayline_progress') {
        flyToPointProgress.status = 'wayline_progress'
      } else if (status === 'OK' || status === 'ok' || status === 'wayline_ok') {
        flyToPointProgress.status = 'wayline_ok'
      } else if (status === 'task_ready') {
        flyToPointProgress.status = 'task_ready'
      } else {
        flyToPointProgress.status = status || 'wayline_progress'
      }
      if (ext.current_waypoint_index !== undefined) {
        flyToPointProgress.wayPointIndex = ext.current_waypoint_index
      }
      if (data.remaining_distance !== undefined) flyToPointProgress.remainingDistance = data.remaining_distance
      if (data.remaining_time !== undefined) flyToPointProgress.remainingTime = data.remaining_time
      if (data.planned_path_points) flyToPointProgress.plannedPathPoints = data.planned_path_points

      if (status === 'OK' || status === 'ok' || status === 'wayline_ok') {
        setTimeout(() => {
          flyToPointProgress.visible = false
          flyToPointProgress.initialDistance = 0
          djiCesiumRef.value?.clearFlightPathLine?.()
        }, 3000)
      }
    }
  }
  if (payload.biz_code === EBizCode.Authority_Revoked) {
    const data = payload.data
    // 不是当前机场消息，丢弃
    if (data.dock_sn !== dockSn.value) {
      return
    }
    console.log('抢夺飞行控制权通知:', data)
    if (flightController.value) {
      message.error('您的飞行控制权已被抢夺')
      exitFlightControl()
      checkOperator()
    }

  }
  if (payload.biz_code === EBizCode.Authority_Exit) {
    const data = payload.data
    console.log(data)
    if (data.flight_authority_exit_from_dock_sn !== dockSn.value) {
      return
    }
    console.log('释放飞行控制权通知', data)
    if (flightController.value) {
      message.warning('您的飞行控制权已被释放')
      exitFlightControl()
    } else {
      const uName = data.username ? data.username + ' ' : ''
      message.success(`${uName}已释放设备控制权，当前空闲，您可随时接管`)
    }
    // 重新检查操作员以更新观看模式状态

    checkOperator()
  }
  if (payload.biz_code === 'cloud_control_auth_notify' || payload.type === 'cloud_control_auth_notify') {
    const data = payload.data
    if (!data || data.sn !== dockSn.value) {
      return
    }
    console.log('手持设备控制权通知:', data)
    const hasAuth = data.has_auth ?? data.authorized ?? data.success ?? (data.result === 0) ?? (data.status === 'success' || data.status === 'OK') ?? false
    hasCloudControl.value = hasAuth

    if (hasAuth) {
      message.success({ content: '夺取手持设备控制权成功！', key: 'grab_control', duration: 2 })
      message.success({ content: '夺取手持设备控制权成功！', key: 'release_control', duration: 2 })
    }
  }
}
useConnectWebSocket(handleDroneControlWsEvent)

const handleBeforeUnload = () => {
  const token = localStorage.getItem(ELocalStorageKey.Token) || ''
  const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''
  const baseUrl = CURRENT_CONFIG.baseURL

  const headers = {
    'Content-Type': 'application/json',
    [ELocalStorageKey.Token]: token
  }

  // 释放飞行控制权
  if (flightController.value && clientId.value && dockSn.value) {
    const drcExitUrl = `${baseUrl}/control/api/v1/workspaces/${workspaceId}/drc/exit`
    const body = JSON.stringify({
      client_id: clientId.value,
      dock_sn: dockSn.value
    })
    fetch(drcExitUrl, {
      method: 'POST',
      headers,
      body,
      keepalive: true
    }).catch(() => { })
  }

  // 释放所有控制权
  if (dockSn.value) {
    const authorityExitUrl = `${baseUrl}/control/api/v1/devices/${dockSn.value}/authority?dock_sn=${dockSn.value}`
    fetch(authorityExitUrl, {
      method: 'DELETE',
      headers,
      keepalive: true
    }).catch(() => { })
  }
}

const isWatchOnly = ref(true)

function checkWatchOnly() {
  if (isWatchOnly.value) {
    message.warning('当前为仅观看模式，如需操作请先点抢夺控制权接管！')
    return true
  }
  return false
}

async function checkOperator() {
  try {
    const res = await getAuthoriyInfo(dockSn.value)
    if (res.code === 0 && res.data) {
      const flightOwner = res.data.flight_authority?.authority_owner || ''
      payloadAuthorityOwner.value = flightOwner
      const currentUser = localStorage.getItem(ELocalStorageKey.Username) || ''
      if (flightOwner && flightOwner !== currentUser) {
        isWatchOnly.value = true
        message.warning(`当前由 ${flightOwner} 操作，您只能观看直播。可点击抢夺控制权按钮进行抢夺。`)
      } else {
        isWatchOnly.value = false
        if (flightOwner === currentUser && deviceInfo.device?.mode_code == 3) {
          // 当无人机mode_code状态为3，才调用方法
          await enterFlightControl()
        }

        if (deviceInfo.device?.mode_code >= 0) {
          const result = await authPayload(dockSn.value, payloadSelectInfo.value.payloadIndex)
          if (result) {
            payloadControlSource.value = ControlSource.A
          }
        }
      }
    } else {
      isWatchOnly.value = false
    }
  } catch (err) {
    console.error('获取操作员信息失败:', err)
    isWatchOnly.value = false
  }
}

onMounted(async () => {
  if (typeof window !== 'undefined') {
    (window as any)._activeCockpitSn = { dockSn: dockSn.value, deviceSn: deviceSn.value, gatewaySn: dockSn.value }
  }
  window.addEventListener('beforeunload', handleBeforeUnload)
  await nextTick() // 确保 DOM 已渲染
  useTitle(dockName.value + '- 虚拟座舱驾驶')
  // 检查操作员
  await checkOperator()

  // 获取无人机搭载负载
  fetchDronePayloads()


  // Setup telemetry scroll detection
  checkTelemetryOverflow()
  if (telemetryContainerRef.value) {
    telemetryResizeObserver = new ResizeObserver(() => {
      checkTelemetryOverflow()
    })
    telemetryResizeObserver.observe(telemetryContainerRef.value)
  }

  console.log('准备发送请求')
  const result = await postDrc({})
  console.log('结果：', result)
  if (result?.code === 0) {
    const { address, client_id, username, password } = result.data
    // @TODO: 校验 expire_time
    mqttState.value = new UranusMqtt(address, {
      clientId: client_id,
      username: `${username}-virtual`,
      keepalive: 30, // 30秒心跳间隔，兼顾稳定性和快速断开检测
      clean: true,
      password,
      properties: {
        sessionExpiryInterval: 180
      }
    })
    mqttState.value?.initMqtt()
    store.commit('SET_MQTT_STATE', mqttState.value)
    mqttState.value?.subscribeMqtt(`thing/product/${deviceSn.value}/osd`)
    if (dockSn.value) {
      mqttState.value?.subscribeMqtt(`thing/product/${dockSn.value}/osd`)
    }
    mqttState.value?.on('onMessageMqtt', handler)
  }
})


// 飞行器和遥控器osd
const handler = async (message: any) => {
  if (message.topic === 'thing/product/' + deviceSn.value + '/osd') {
    const payloadStr = new TextDecoder('utf-8').decode(message.payload)
    const payloadObj = JSON.parse(payloadStr)
    deviceInfo.device = payloadObj.data
  } else if (dockSn.value && message.topic === 'thing/product/' + dockSn.value + '/osd') {
    const payloadStr = new TextDecoder('utf-8').decode(message.payload)
    const payloadObj = JSON.parse(payloadStr)
    const data = payloadObj.data
    if (data) {
      deviceInfo.dock.basic_osd = {
        ...deviceInfo.dock.basic_osd,
        ...data,
      }
      deviceInfo.dock.link_osd = {
        ...deviceInfo.dock.link_osd,
        ...data,
      }
    }
  }
}
onBeforeUnmount(() => {


  // 移除事件监听器

  mqttState.value?.off('onMessageMqtt', handler)
  mqttState.value?.unsubscribeMqtt(`thing/product/${deviceSn.value}/osd`)
  if (dockSn.value) {
    mqttState.value?.unsubscribeMqtt(`thing/product/${dockSn.value}/osd`)
  }
})
onUnmounted(() => {
  if (typeof window !== 'undefined') {
    (window as any)._activeCockpitSn = null
  }
  window.removeEventListener('beforeunload', handleBeforeUnload)
  // 释放飞行控制权和负载控制权
  if (flightController.value) {
    exitFlightControl()
  }
  // 移除事件监听器
  mqttState.value?.off('onMessageMqtt', handler)
  mqttState.value?.unsubscribeMqtt(`thing/product/${deviceSn.value}/osd`)
  if (dockSn.value) {
    mqttState.value?.unsubscribeMqtt(`thing/product/${dockSn.value}/osd`)
  }
  mqttState.value?.destroyed()
  store.commit('SET_MQTT_STATE', null)


  // 释放飞行控制权和负载控制权
  if (flightController.value) {
    exitFlightControl()
  }
  if (dockSn.value) {
    postExitAuthority(dockSn.value).catch((err: any) => {
      console.error('退出页面释放控制权失败:', err)
    })
  }
})

const getResponseTimeClass = (time: number) => {
  if (time > 3000) return 'text-red-500 animate-pulse font-extrabold'
  if (time > 500) return 'text-yellow-500 font-bold'
  return 'text-green-500'
}

watch(dockCmdResponseTime, (newTime) => {
  if (newTime !== null && newTime > 3000) {
    notification.error({
      message: '飞行安全警告',
      description: `机场控制指令延迟过高 (${newTime} ms)，已触发安全预警！`,
      duration: 8,
      placement: 'topRight',
    })
  }
})

watch(payloadCmdResponseTime, (newTime) => {
  if (newTime !== null && newTime > 3000) {
    notification.error({
      message: '飞行安全警告',
      description: `负载控制指令延迟过高 (${newTime} ms)，已触发安全预警！`,
      duration: 8,
      placement: 'topRight',
    })
  }
})

const videowebrtc = ref(null)
const videoContainer = ref(null)
let webrtc: any = null

const {
  sendDockControlCmd,
  dockDebugOnOff
} = useDockControl()
// 飞行控制
const flightController = ref(false)

const latitude = ref(0)
const longitude = ref(0)
const height = ref(0)
const sn = ref('')
const plateData = ref('')
const payloads = ref(null)
const store = useMyStore()



const dockMapLocation = computed(() => {
  return {
    latitude: deviceInfo?.dock?.basic_osd?.latitude,
    longitude: deviceInfo?.dock?.basic_osd?.longitude,
    height: deviceInfo?.dock?.basic_osd?.height
  }
})

// 合并的 OSD 信息：优先使用 DRC 模式下的 osdInfo，当其为空时使用 deviceInfo.device（航线飞行时的常规OSD推送）
// 这样无论是指点飞行（DRC模式）还是航线飞行（自主任务模式），都能在地图与打点组件中获取无人机实时多维姿态
const mergedOsdInfo = computed(() => {
  const payloadIndex = cameraPayloadIndex.value
  const dev = deviceInfo.device
  const cameraOsd = payloadIndex && dev ? (dev as any)[payloadIndex] : null
  const dockH = Number(dockMapLocation.value?.height || 0)

  // 如果 osdInfo 有有效的经纬度数据（DRC模式下），并且不为0，直接使用
  if (osdInfo.value && osdInfo.value.longitude !== undefined && osdInfo.value.longitude !== 0 && osdInfo.value.latitude !== 0) {
    const drcOsd = osdInfo.value as any
    const relH = drcOsd.elevation !== undefined ? Number(drcOsd.elevation) : (drcOsd.height !== undefined ? Number(drcOsd.height) : 50)
    const absH = drcOsd.height !== undefined ? Number(drcOsd.height) : (relH + dockH)

    return {
      ...drcOsd,
      elevation: relH,
      relative_height: relH,
      height: absH,
      absolute_height: absH,
      gimbal_pitch: drcOsd.gimbal_pitch ?? cameraOsd?.gimbal_pitch ?? -45,
      gimbalPitch: drcOsd.gimbal_pitch ?? cameraOsd?.gimbal_pitch ?? -45,
      zoom_factor: drcOsd.zoom_factor ?? cameraOsd?.zoom_factor ?? 1,
      zoomFactor: drcOsd.zoom_factor ?? cameraOsd?.zoom_factor ?? 1,
    }
  }

  // 否则使用 deviceInfo.device 的数据，并且不为0（航线飞行等场景）
  if (deviceInfo.device && (deviceInfo.device.longitude !== undefined || deviceInfo.device.position_state?.longitude !== undefined)) {
    const lon = deviceInfo.device.position_state?.longitude ?? deviceInfo.device.longitude ?? 0
    const lat = deviceInfo.device.position_state?.latitude ?? deviceInfo.device.latitude ?? 0
    const relH = deviceInfo.device.elevation !== undefined ? Number(deviceInfo.device.elevation) : 50
    const absH = deviceInfo.device.height !== undefined ? Number(deviceInfo.device.height) : (relH + dockH)

    return {
      longitude: lon,
      latitude: lat,
      lon,
      lat,
      elevation: relH,
      relative_height: relH,
      height: absH,
      absolute_height: absH,
      attitude_head: deviceInfo.device.attitude_head ?? 0,
      heading: deviceInfo.device.attitude_head ?? 0,
      yaw: deviceInfo.device.attitude_head ?? 0,
      attitude_pitch: deviceInfo.device.attitude_pitch ?? 0,
      attitude_roll: deviceInfo.device.attitude_roll ?? 0,
      gimbal_pitch: cameraOsd?.gimbal_pitch ?? -45,
      gimbalPitch: cameraOsd?.gimbal_pitch ?? -45,
      zoom_factor: cameraOsd?.zoom_factor ?? 1,
      zoomFactor: cameraOsd?.zoom_factor ?? 1,
    }
  }

  // 都没有数据时返回空对象
  return {}
})

// 创建稳定的 dockMap，避免使用 localStorage 导致闪烁
// 创建稳定的 dockMap，避免使用 localStorage 导致闪烁
// 使用一次性初始化：只在首次获取到有效位置时设置，之后永不更新
const stableDockMapCache = shallowRef<Array<{ sn: string, callsign: string, longitude: number, latitude: number, height: number }>>([])
const stableDockMapInitialized = ref(false)

watch(
  [
    () => deviceInfo?.dock?.basic_osd?.latitude,
    () => deviceInfo?.dock?.basic_osd?.longitude,
    () => deviceInfo?.dock?.basic_osd?.height
  ],
  ([lat, lng, h]) => {
    // 如果已经初始化过，直接返回，永不再更新
    if (stableDockMapInitialized.value) {
      return
    }

    // 等待有效的位置信息
    if (!lat || !lng || lat === 0 || lng === 0) {
      return
    }

    // 首次初始化 - 只执行一次
    stableDockMapInitialized.value = true
    stableDockMapCache.value = Object.freeze([{
      sn: dockSn.value || '',
      callsign: (dockName.value as string) || '当前机场',
      longitude: lng,
      latitude: lat,
      height: h || 0
    }]) as any
    console.log('[stableDockMap] 首次初始化完成，数据已冻结')
    djiCesiumRef.value?.tryFlyToDock({ latitude: lat, longitude: lng, height: h })
  },
  { immediate: true } // 移除 deep: true
)

// 直接导出缓存引用
const stableDockMap = stableDockMapCache


// 机场状态
const dockStatus = computed(() => 0)

// 无人机状态
const deviceStatus = computed(() => {
  return deviceInfo?.device?.mode_code
})

// 无人机是否在机场内
const droneInDock = computed(() => false)

// 无人机在线状态 (0: 离线, 1: 在线)
const droneOnlineStatus = computed(() => {
  return deviceInfo?.device ? 1 : 0
})

// 机场盖板状态
const droneCoverState = computed(() => 0)

// 无人机充电状态
const droneChargeState = computed(() => null)

// 无人机开机进度展示条件
const showDroneStartupProgress = computed(() => false)



const deviceTopicInfo: DeviceTopicInfo = reactive({
  sn: dockSn.value,
  pubTopic: '',
  subTopic: ''
})

const MAX_SPEED = 15
const takeoffToPointPopoverData = reactive({
  visible: false,
  loading: false,
  latitude: null as null | number,
  longitude: null as null | number,
  height: null as null | number,
  securityTakeoffHeight: null as null | number,
  maxSpeed: MAX_SPEED,
  rthAltitude: null as null | number,
  rcLostAction: LostControlActionInCommandFLight.RETURN_HOME,
  exitWaylineWhenRcLost: WaylineLostControlActionInCommandFlight.EXEC_LOST_ACTION,
  rthMode: ERthMode.SETTING,
  commanderModeLostAction: ECommanderModeLostAction.CONTINUE,
  commanderFlightMode: ECommanderFlightMode.SETTING,
  commanderFlightHeight: null as null | number,
})

// 一键起飞参数缓存 key
const getTakeoffCacheKey = () => `takeoff-params-${dockSn.value}`

// 获取缓存的起飞参数
const getCachedTakeoffParams = () => {
  try {
    const cached = localStorage.getItem(getTakeoffCacheKey())
    if (cached) {
      return JSON.parse(cached)
    }
  } catch (e) {
    console.warn('读取起飞参数缓存失败', e)
  }
  return null
}

// 保存起飞参数到缓存
const saveTakeoffParamsToCache = () => {
  try {
    const params = {
      securityTakeoffHeight: takeoffToPointPopoverData.securityTakeoffHeight,
      rthAltitude: takeoffToPointPopoverData.rthAltitude,
      rcLostAction: takeoffToPointPopoverData.rcLostAction,
      exitWaylineWhenRcLost: takeoffToPointPopoverData.exitWaylineWhenRcLost,
      rthMode: takeoffToPointPopoverData.rthMode,
      commanderModeLostAction: takeoffToPointPopoverData.commanderModeLostAction,
      commanderFlightMode: takeoffToPointPopoverData.commanderFlightMode,
      commanderFlightHeight: takeoffToPointPopoverData.commanderFlightHeight,
    }
    localStorage.setItem(getTakeoffCacheKey(), JSON.stringify(params))
  } catch (e) {
    console.warn('保存起飞参数缓存失败', e)
  }
}

const onShowTakeoffToPointPopover = async () => {
  takeoffToPointPopoverData.visible = !takeoffToPointPopoverData.visible
  takeoffToPointPopoverData.loading = false

  // 优先从飞行器获取起飞点经纬度和高度，若无效则从遥控器/机场获取
  const deviceLat = deviceInfo.device?.latitude
  const deviceLng = deviceInfo.device?.longitude
  if (deviceLat && deviceLng && deviceLat !== 0 && deviceLng !== 0) {
    takeoffToPointPopoverData.latitude = deviceLat
    takeoffToPointPopoverData.longitude = deviceLng
    takeoffToPointPopoverData.height = Math.floor(deviceInfo.device?.height || 0)
  } else {
    takeoffToPointPopoverData.latitude = deviceInfo.dock?.basic_osd?.latitude
    takeoffToPointPopoverData.longitude = deviceInfo.dock?.basic_osd?.longitude
    takeoffToPointPopoverData.height = Math.floor(deviceInfo.dock?.basic_osd?.height || deviceInfo.device?.height || 0)
  }

  // 尝试从缓存加载参数
  const cachedParams = getCachedTakeoffParams()
  if (cachedParams) {
    // 使用缓存的参数
    takeoffToPointPopoverData.securityTakeoffHeight = cachedParams.securityTakeoffHeight ?? 50
    takeoffToPointPopoverData.rthAltitude = cachedParams.rthAltitude ?? 50
    takeoffToPointPopoverData.rcLostAction = cachedParams.rcLostAction ?? LostControlActionInCommandFLight.RETURN_HOME
    takeoffToPointPopoverData.exitWaylineWhenRcLost = cachedParams.exitWaylineWhenRcLost ?? WaylineLostControlActionInCommandFlight.EXEC_LOST_ACTION
    takeoffToPointPopoverData.rthMode = cachedParams.rthMode ?? ERthMode.SETTING
    takeoffToPointPopoverData.commanderModeLostAction = cachedParams.commanderModeLostAction ?? ECommanderModeLostAction.CONTINUE
    takeoffToPointPopoverData.commanderFlightMode = cachedParams.commanderFlightMode ?? ECommanderFlightMode.SETTING
    takeoffToPointPopoverData.commanderFlightHeight = cachedParams.commanderFlightHeight ?? 50
  } else {
    // 没有缓存，使用默认值
    takeoffToPointPopoverData.securityTakeoffHeight = 50
    takeoffToPointPopoverData.rthAltitude = 50
    takeoffToPointPopoverData.rcLostAction = LostControlActionInCommandFLight.RETURN_HOME
    takeoffToPointPopoverData.exitWaylineWhenRcLost = WaylineLostControlActionInCommandFlight.EXEC_LOST_ACTION
    takeoffToPointPopoverData.rthMode = ERthMode.SETTING
    takeoffToPointPopoverData.commanderModeLostAction = ECommanderModeLostAction.CONTINUE
    takeoffToPointPopoverData.commanderFlightMode = ECommanderFlightMode.SETTING
    takeoffToPointPopoverData.commanderFlightHeight = 50
  }
}


async function onTakeoffToPointConfirm(confirm: boolean) {
  if (confirm) {
    if (checkWatchOnly()) return
    if (
      !takeoffToPointPopoverData.height ||
      !takeoffToPointPopoverData.latitude ||
      !takeoffToPointPopoverData.longitude ||
      !takeoffToPointPopoverData.securityTakeoffHeight ||
      !takeoffToPointPopoverData.rthAltitude ||
      !takeoffToPointPopoverData.commanderFlightHeight) {
      message.error('Input error')
      return
    }

    // 保存参数到缓存（以机场SN为key）
    saveTakeoffParamsToCache()
    //message.success({ content: '测试，不要真实起飞', key: 'takeoff-cmd' })

    
    try {
      // 准备起飞参数
      const takeoffParams = {
        target_latitude: takeoffToPointPopoverData.latitude,
        target_longitude: takeoffToPointPopoverData.longitude,
        target_height: takeoffToPointPopoverData.height + takeoffToPointPopoverData.commanderFlightHeight,
        security_takeoff_height: takeoffToPointPopoverData.securityTakeoffHeight,
        rth_altitude: takeoffToPointPopoverData.rthAltitude,
        max_speed: takeoffToPointPopoverData.maxSpeed,
        rc_lost_action: takeoffToPointPopoverData.rcLostAction,
        exit_wayline_when_rc_lost: takeoffToPointPopoverData.exitWaylineWhenRcLost,
        rth_mode: takeoffToPointPopoverData.rthMode,
        commander_mode_lost_action: takeoffToPointPopoverData.commanderModeLostAction,
        commander_flight_mode: takeoffToPointPopoverData.commanderFlightMode,
        commander_flight_height: takeoffToPointPopoverData.commanderFlightHeight,
      }

      // 执行起飞
      message.loading({ content: '正在发送起飞指令...', key: 'takeoff-cmd' })
      await takeoffToPoint(dockSn.value, takeoffParams)
      message.success({ content: '起飞指令已发送', key: 'takeoff-cmd' })

    } catch (error) {
      message.error({ content: '起飞失败: ' + (error as Error).message, key: 'takeoff-cmd' })
    }
  }
  takeoffToPointPopoverData.visible = false
}


// 返航函数
async function sendControlCmd(cmdItem: DeviceCmdItem, index: number) {
  if (checkWatchOnly()) return
  cmdItem.loading = true
  const result = await sendDockControlCmd({
    sn: dockSn.value,
    cmd: cmdItem.cmdKey,
    action: cmdItem.action
  }, false)
  if (result) {
    message.success(cmdItem.label + '成功')

  } else {
    message.error(cmdItem.label + '失败')
  }
  cmdItem.loading = false
}

// 航线暂停/恢复相关状态
const waylinePauseLoading = ref(false)
const waylineResumeLoading = ref(false)
const currentFlightId = ref<string>('')

// 获取当前飞行任务ID
async function fetchCurrentFlightId(): Promise<string> {
  try {
    const res = await getFlightId(dockSn.value)
    if (res.code === 0 && res.data?.flight_id) {
      currentFlightId.value = res.data.flight_id
      return res.data.flight_id
    }
  } catch (err) {
    console.error('获取当前任务ID失败:', err)
  }
  return ''
}

// 航线暂停
async function handleWaylinePause() {
  if (checkWatchOnly()) return
  waylinePauseLoading.value = true
  try {
    // 先获取当前任务ID
    let jobId = currentFlightId.value
    if (!jobId) {
      jobId = await fetchCurrentFlightId()
    }
    if (!jobId) {
      message.warning('当前无正在执行的航线任务')
      waylinePauseLoading.value = false
      return
    }

    const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''
    const result = await pauseOrResumeWayline(workspaceId, jobId, 0) // 0 = 暂停
    if (result.code === 0) {
      message.success('航线暂停成功')
    } else {
      message.error('航线暂停失败: ' + (result.message || '未知错误'))
    }
  } catch (err: any) {
    message.error('航线暂停失败: ' + (err.message || '未知错误'))
  } finally {
    waylinePauseLoading.value = false
  }
}

// 航线恢复
async function handleWaylineResume() {
  if (checkWatchOnly()) return
  waylineResumeLoading.value = true
  try {
    // 先获取当前任务ID
    let jobId = currentFlightId.value
    if (!jobId) {
      jobId = await fetchCurrentFlightId()
    }
    if (!jobId) {
      message.warning('当前无正在执行的航线任务')
      waylineResumeLoading.value = false
      return
    }

    const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''
    const result = await pauseOrResumeWayline(workspaceId, jobId, 1) // 1 = 恢复
    if (result.code === 0) {
      message.success('航线恢复成功')
    } else {
      message.error('航线恢复失败: ' + (result.message || '未知错误'))
    }
  } catch (err: any) {
    message.error('航线恢复失败: ' + (err.message || '未知错误'))
  } finally {
    waylineResumeLoading.value = false
  }
}

async function testFlightControl() {
  await enterFlightControl()
  if (flightController.value) {
    isWatchOnly.value = false

    const payloadRes = await authPayload(dockSn.value, payloadSelectInfo.value.payloadIndex)
    if (payloadRes) {
      payloadControlSource.value = ControlSource.A
    }
  }
}
// async function testReleaseControl() {
//   await postExitAuthority(dockSn.value)
// }

async function onClickFightControl() {
  if (flightController.value) {
    await exitFlightControl()
    await postExitAuthority(dockSn.value)
    return
  }
  await enterFlightControl()
  if (flightController.value) {
    isWatchOnly.value = false

    const payloadRes = await authPayload(dockSn.value, payloadSelectInfo.value.payloadIndex)
    if (payloadRes) {
      payloadControlSource.value = ControlSource.A
    }
  }
}

const hasCloudControl = ref(false)

const grabCloudControlHandler = async () => {
  if (!dockSn.value) {
    message.warning('请选择机场Sn')
    return
  }
  try {
    const res = await grabCloudControl(dockSn.value)
    if (res.code === 0) {
      message.loading({ content: '正在向手持端夺权...', key: 'grab_control', duration: 10 })
    } else {
      message.error(res.message || '指令发送失败')
    }
  } catch (error: any) {
    message.error('指令发送错误: ' + (error.message || '未知错误'))
  }
}

const releaseCloudControlHandler = async () => {
  if (!dockSn.value) {
    message.warning('请选择机场Sn')
    return
  }
  try {
    const res = await releaseCloudControl(dockSn.value)
    if (res.code === 0) {
      hasCloudControl.value = false
      message.success({ content: '手持设备控制权已释放！', key: 'release_control', duration: 2 })
    } else {
      message.error(res.message || '指令发送失败')
    }
  } catch (error: any) {
    message.error('指令发送错误: ' + (error.message || '未知错误'))
  }
}

useMqtt(deviceTopicInfo)

// 进入飞行控制
async function enterFlightControl() {
  try {
    // 1. 优先抢夺飞行控制权
    // await postFlightAuth(dockSn.value)

    // 2. 抢夺成功后，进入DRC控制模式
    const { code, data } = await postDrcEnter({
      client_id: clientId.value,
      dock_sn: dockSn.value,
    })

    if (code === 0) {
      flightController.value = true
      if (data.sub && data.sub.length > 0) {
        deviceTopicInfo.subTopic = data.sub[0]
      }
      if (data.pub && data.pub.length > 0) {
        deviceTopicInfo.pubTopic = data.pub[0]
      }
      deviceTopicInfo.sn = dockSn.value

      // 直播开启后获取操作人员名称
      try {
        getAuthoriyInfo(dockSn.value).then((res) => {
          if (res.code === 0 && res.data) {
            payloadAuthorityOwner.value = res.data?.flight_authority.authority_owner || ''
          }
        })
      } catch (err) {
        console.error('获取操作人员信息失败:', err)
      }

      message.success('飞行控制权获取成功，已进入DRC模式')
    } else {
      message.error('进入DRC控制模式失败')
    }
  } catch (error: any) {
    message.error(error?.message || '获取飞行控制权失败')
  }
}

console.log(deviceTopicInfo)

const [savedSpeeds, setSavedSpeeds] = useLocalStorageState<ControlSpeeds>('control-speeds', {
  defaultValue: {
    horizontal: 60,
    vertical: 60,
    yaw: 50
  }
})

const controlSpeeds = ref<ControlSpeeds>({
  horizontal: 60,
  vertical: 60,
  yaw: 50,
  ...savedSpeeds.value
})

watch(controlSpeeds, (val) => {
  setSavedSpeeds(val)
}, { deep: true })

const {
  handleKeyup,
  handleKeydown,
  handleEmergencyStop,
  resetControlState,
  startKeyboardManualControl,
  closeKeyboardManualControl
} = useManualControl(deviceTopicInfo, flightController, controlSpeeds)

function handleEmergencyStopHandler() {
  if (checkWatchOnly()) return
  handleEmergencyStop()
  setTimeout(() => {
    flyToPointProgress.visible = false
    flyToPointProgress.initialDistance = 0
    djiCesiumRef.value?.clearFlightPathLine?.()

  }, 500)

}

const { drcInfo, errorInfo, osdInfo, delayInfo } = useDroneControlMqttEvent(dockSn.value)

// 退出飞行控制
async function exitFlightControl() {
  try {
    const { code } = await postDrcExit({
      client_id: clientId.value,
      dock_sn: dockSn.value,
    })
    if (code === 0) {
      flightController.value = false
      deviceTopicInfo.subTopic = ''
      deviceTopicInfo.pubTopic = ''
      message.success('Exit flight control')
    }
  } catch (error: any) {
  }
}

function onMouseDown(type: KeyCode) {
  handleKeydown(type)
}

function onMouseUp(type: KeyCode) {
  handleKeyup(type)
}


const playWebrtc = async (url: string) => {
  console.log('[playWebrtc] 开始, url:', url)
  if (webrtc) {
    webrtc.close()
  }
  isVideoLoaded.value = false

  webrtc = new srs.SrsRtcWhipWhepAsync()
  console.log('[playWebrtc] SRS 实例已创建, stream:', webrtc.stream)
  const videoElement = videowebrtc.value as unknown as HTMLMediaElement
  if (!videoElement) {
    console.error('[playWebrtc] video 元素不存在!')
    return
  }
  console.log('[playWebrtc] video 元素已获取, display:', getComputedStyle(videoElement).display, 'size:', videoElement.offsetWidth, 'x', videoElement.offsetHeight)

  videoElement.srcObject = webrtc.stream
  console.log('[playWebrtc] srcObject 已设置, 开始 play...')
  try {
    const session = await webrtc.play(url)
    console.info('[playWebrtc] webrtc session 建立成功:', session)
  } catch (err) {
    webrtc.close()
    webrtc = null
    console.error('[playWebrtc] webrtc play 失败:', err)
  }
}


const playUrl = ref(null)
const isVideoLoaded = ref(false)
// 当前操作人员名称
const payloadAuthorityOwner = ref<string>('')
const onlineDevices = reactive({
  data: [] as OnlineDevice[]
})

const onlineDocks = reactive({
  data: [] as OnlineDevice[]
})

// 负载控制
// const payloadSelectInfo = {
//   value: null as any,
//   controlSource: undefined as undefined | ControlSource,
//   options: [] as any,
//   payloadIndex: '' as string,
//   camera: undefined as undefined | DeviceOsdCamera // 当前负载osd信息
// }
const payloadSelectInfo = computed(() => {
  return {
    value: deviceSn.value + '0',
    controlSource: ControlSource.A,
    payloadIndex: cameraPayloadIndex.value,
    options: [{
      label: deviceInfo.device,
      value: '无人机'
    }],
    camera: undefined
  }
})
const {
  checkPayloadAuth,
  authPayload,
  resetGimbal,
  switchCameraMode,
  takeCameraPhoto,
  startCameraRecording,
  stopCameraRecording,
  changeCameraFocalLength,
  cameraAim,
  cameraFrameZoom,
  cameraScreenDrag,
} = usePayloadControl()

// 统一开启直播与权限申请逻辑
const triggerLivestream = async () => {
  console.log('[triggerLivestream] 手持设备虚拟座舱直播触发, playUrl:', playUrl.value, 'deviceSn:', deviceSn.value, 'cameraPayloadIndex:', cameraPayloadIndex.value)
  if (playUrl.value != null) {
    console.log('[triggerLivestream] playUrl 已存在，跳过')
    return
  }

  if (!isWatchOnly.value) {
    try {
      const result = await authPayload(dockSn.value, payloadSelectInfo.value.payloadIndex)
      if (result) {
        payloadControlSource.value = ControlSource.A
      }
    } catch (e) {
      console.warn('获取负载控制权失败:', e)
    }
  }

  // 开启直播
  let plateId = deviceSn.value + '/' + cameraPayloadIndex.value + '/normal-0'
  console.log('[triggerLivestream] 自动开启手持设备直播中:', plateId)
  
  const fallbackPlay = () => {
    console.log('[triggerLivestream] 执行手持设备直播兜底播放逻辑')
    const fixedUrl = CURRENT_CONFIG.srsRtmp + '/live/' + deviceSn.value + '.flv'
    playUrl.value = fixedUrl
    if (fixedUrl.endsWith('.flv')) {
      isVideoLoaded.value = true
    }
    playWebrtc(fixedUrl)
  }

  try {
    const res = await startLivestream({
      url: '',
      video_id: plateId,
      url_type: 4,
      video_quality: 4,
      gateway_type: "rc"
    })
    console.log('[triggerLivestream] startLivestream 返回:', res)
    if (!res) {
      console.error('[triggerLivestream] startLivestream 返回为空')
      // fallbackPlay()
      return
    }
    if (res.code !== 0 && res.code !== 613003) {
      console.warn('[triggerLivestream] 直播开启申请失败, code:', res.code)
      // fallbackPlay()
      return
    }
    console.log('[triggerLivestream] 获取到手持设备直播 URL:', res.data?.url)
    playUrl.value = res.data.url
    playWebrtc(res.data.url)
  } catch (err) {
    console.error('[triggerLivestream] 接口调用异常，触发兜底播放:', err)
    // fallbackPlay()
  }
}

const stopLivestreamHandler = async () => {
  console.log('[stopLivestreamHandler] 手持设备虚拟座舱直播手动关闭')
  let plateId = deviceSn.value + '/' + cameraPayloadIndex.value + '/normal-0'
  try {
    const res = await stopLivestream({
      video_id: plateId
    })
    console.log('[stopLivestreamHandler] stopLivestream 返回:', res)
    message.success('已发送关闭直播请求')
  } catch (err) {
    console.error('[stopLivestreamHandler] 调用关闭直播接口异常:', err)
  }

  // 释放 WebRTC 与状态清理
  if (webrtc) {
    webrtc.close()
    webrtc = null
  }
  playUrl.value = null
  isVideoLoaded.value = false
  const videoElement = videowebrtc.value as HTMLVideoElement | null
  if (videoElement) {
    videoElement.srcObject = null
    videoElement.src = ''
  }
}

// 1. 监测 OSD 状态变化时触发
watch(() => deviceInfo.device?.mode_code, async (data) => {
  if (data >= 0 && playUrl.value != null) {
    await triggerLivestream()
  }
})

// 2. 挂载后自动延迟触发，确保兜底播放 (Telemetry OSD 消息可能因连接延迟/待机无数据而慢)
onMounted(() => {
  setTimeout(() => {
    if (playUrl.value != null) {
      console.log('OSD 状态尚未就绪，通过 Mounted 自动开启实况')
      triggerLivestream()
    }
  }, 1200)
})
// ws 消息通知
const { droneControlSource, payloadControlSource } = useDroneControlWsEvent(dockSn.value, payloadSelectInfo.value.value)
watch(() => payloadControlSource, (controlSource) => {
  payloadSelectInfo.value.controlSource = controlSource.value
}, {
  immediate: true,
  deep: true
})



watch(() => deviceInfo.device?.attitude_head, data => {
  if (data === undefined || data === null) return
  const degree = data + 45
  const arrow = document.querySelector('.compass-arrow') as HTMLElement | null
  const degreeDisplay = document.querySelector('.degree-display') as HTMLElement | null
  if (arrow && degreeDisplay) {
    arrow.style.transform = `rotate(${degree}deg)`
    degreeDisplay.textContent = `${data}°`
  }
})

const [wasBelow, setState] = useLocalStorageState('was-below' + deviceSn.value, {
})
watch(() => deviceInfo.device?.elevation, elevation => {
  if (isWatchOnly.value) {
    return
  }
  const current = ~~elevation
  if (current >= 10 && wasBelow.value) {
    if (deviceStatus.value == 5) {
      console.log('当前无人机正在航线飞行,取消自动获取控制权', deviceStatus.value)
      return
    }

    // enterFlightControl() // 已注释：取消高度>=10m时的自动强制抢夺控制权逻辑
    setState(false)
  } else if (current < 10) {
    // 仅当无人机处于自动降落模式 (mode_code === 10) 时才自动退出控制权
    // mode_code 10: AutoLanding
    if (flightController.value && deviceInfo.device?.mode_code === 10) {
      console.log('无人机正在自动降落 (<10m)，自动退出飞行控制')
      exitFlightControl()
    }
    setState(true) // 重置，允许下次触发
  }
}, { immediate: true })

const cameraAimPopoverData = reactive({
  visible: false,
  loading: false,
  cameraType: null as null | CameraType,
  locked: false,
  x: null as null | number,
  y: null as null | number,
})

async function onCameraAimConfirm(confirm: boolean) {
  if (confirm) {
    if (cameraAimPopoverData.cameraType === null || cameraAimPopoverData.x === null || cameraAimPopoverData.y === null) {
      message.error('Input error')
      return
    }
    try {
      await cameraAim(dockSn.value, {
        payload_index: payloadSelectInfo.value.payloadIndex,
        camera_type: cameraAimPopoverData.cameraType,
        locked: cameraAimPopoverData.locked,
        x: cameraAimPopoverData.x,
        y: cameraAimPopoverData.y,
      })
    } catch (error) {
    }
  }
  cameraAimPopoverData.visible = false
}

function onShowCameraAimPopover() {
  cameraAimPopoverData.visible = !cameraAimPopoverData.visible
  cameraAimPopoverData.loading = false
  cameraAimPopoverData.cameraType = null
  cameraAimPopoverData.locked = false
  cameraAimPopoverData.x = null
  cameraAimPopoverData.y = null
}

// 监听鼠标双击事件
const handleVideoDblClick = (event: MouseEvent) => {
  const canvas = document.getElementById('overlayCanvas') as HTMLCanvasElement
  if (!canvas) return

  const rect = canvas.getBoundingClientRect()
  console.log(rect)
  console.log(event)
  const x = event.clientX - rect.left
  const y = event.clientY - rect.top


  console.log('🎯 双击坐标:', x.toFixed(6), y.toFixed(6))

  // 🔧 转换为归一化坐标（0~1）用于 AI 算法
  const normalizedX = (x / rect.width)
  const normalizedY = (y / rect.height)

  console.log('🔄 归一化坐标:', normalizedX.toFixed(6), normalizedY.toFixed(6))

  cameraAim(dockSn.value, {
    payload_index: payloadSelectInfo.value.payloadIndex,
    camera_type: CameraType.WIDE,
    locked: cameraAimPopoverData.locked,
    x: parseFloat(normalizedX.toFixed(6)),
    y: parseFloat(normalizedY.toFixed(6)),
  })
}

// 画面拖动控制
let dragInterval: any = null
let dragTimeout: any = null

const handleCameraDrag = (pitch: number, yaw: number) => {
  // 立即执行一次
  cameraScreenDrag(dockSn.value, {
    payload_index: payloadSelectInfo.value.payloadIndex,
    locked: false,
    pitch_speed: pitch,
    yaw_speed: yaw
  })

  // 清除之前的定时器
  if (dragTimeout) clearTimeout(dragTimeout)
  if (dragInterval) clearInterval(dragInterval)

  // 设置0.2秒后的定时器
  dragTimeout = setTimeout(() => {
    // 0.2秒后开始每200ms执行一次
    dragInterval = setInterval(() => {
      cameraScreenDrag(dockSn.value, {
        payload_index: payloadSelectInfo.value.payloadIndex,
        locked: false,
        pitch_speed: pitch,
        yaw_speed: yaw
      })
    }, 200)
  }, 200)
}

const stopCameraDrag = () => {
  // 清除所有定时器
  if (dragTimeout) {
    clearTimeout(dragTimeout)
    dragTimeout = null
  }
  if (dragInterval) {
    clearInterval(dragInterval)
    dragInterval = null
  }

  // 发送停止指令
  cameraScreenDrag(dockSn.value, {
    payload_index: payloadSelectInfo.value.payloadIndex,
    locked: false,
    pitch_speed: 0,
    yaw_speed: 0
  })
}
const switchCameraState = async (e: any) => {
  if (!checkPayloadAuth(payloadSelectInfo.value.controlSource)) {
    return
  }

  await switchCameraMode(dockSn.value, {
    payload_index: payloadSelectInfo.value.payloadIndex,
    camera_mode: e.key
  })
}

const onTakeCameraPhoto = async () => {
  if (!checkPayloadAuth(payloadSelectInfo.value.controlSource)) {
    return
  }
  await takeCameraPhoto(dockSn.value, payloadSelectInfo.value.payloadIndex)
}

const isRecording = ref(false)
const recording = async () => {
  isRecording.value = !isRecording.value

  // 开始录像
  if (isRecording.value) {
    if (!checkPayloadAuth(payloadSelectInfo.value.controlSource)) {
      return
    }
    await startCameraRecording(dockSn.value, payloadSelectInfo.value.payloadIndex)
  }
  // 结束录像
  if (!isRecording.value) {
    if (!checkPayloadAuth(payloadSelectInfo.value.controlSource)) {
      return
    }
    await stopCameraRecording(dockSn.value, payloadSelectInfo.value.payloadIndex)
  }
}

const onUpdateQuality = (claritySelected: Number) => {
  if (!playUrl.value) {
    message.info('Please turn on the livestream first.')
    return
  }
  setLivestreamQuality({
    video_id: deviceSn.value + '/' + cameraPayloadIndex.value + '/normal-0',
    video_quality: claritySelected
  }).then(res => {
    if (res.code === 0) {
      getVideoResolution()
    }
  })
}

const onSwitch = (type: String) => {
  changeLivestreamLens({
    video_id: deviceSn.value + '/' + cameraPayloadIndex.value + '/normal-0',
    video_type: type
  }).then(res => {
    if (res.code === 0) {
      message.success('Switching live camera successfully.')
    }
  })
}


// 选区状态（仅用于计算，不用于渲染）
let startX = 0
let startY = 0
let endX = 0
let endY = 0
let isDrawing = false

const onRectMouseDown = (e: MouseEvent) => {
  if (e.button !== 0) return // 只响应左键
  const canvas = document.getElementById('overlayCanvas') as HTMLCanvasElement
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  console.log(e.clientX, rect.left)
  startX = e.clientX - rect.left
  startY = e.clientY - rect.top
  endX = startX
  endY = startY

  isDrawing = true
}

const onRectMouseMove = (e: MouseEvent) => {
  if (!isDrawing) return
  const canvas = document.getElementById('overlayCanvas') as HTMLCanvasElement
  if (!canvas) return

  const rect = canvas.getBoundingClientRect()
  endX = e.clientX - rect.left
  endY = e.clientY - rect.top

  const x: number = Number((Math.min(startX, endX) / rect.width).toFixed(6))
  const y: number = Number((Math.min(startY, endY) / rect.height).toFixed(6))
  const width: number = Number((Math.abs(endX - startX) / rect.width).toFixed(6))
  const height: number = Number((Math.abs(endY - startY) / rect.height).toFixed(6))


  if (currentRect.value) {
    currentRect.value.x = x * rect.width
    currentRect.value.y = y * rect.height
    currentRect.value.width = width * rect.width
    currentRect.value.height = height * rect.height
    redraw()
  }
}
const resolution = ref({ width: 0, height: 0 });

let videoResizeObserver: ResizeObserver | null = null

const onVideoResize = () => {
  getVideoResolution()
  adjustOverlay()
  nextTick(() => {
    redraw()
  })
}

const onLoadedMetadata = () => {
  getVideoResolution()
  isVideoLoaded.value = true

  // 等待 v-show 生效，DOM 渲染完成
  nextTick(() => {
    // 初始化 ResizeObserver
    if (!videoResizeObserver && videowebrtc.value) {
      videoResizeObserver = new ResizeObserver(() => {
        adjustOverlay()
      })
      videoResizeObserver.observe(videowebrtc.value)
    }
    adjustOverlay()
  })
}

// 调整遮罩层尺寸以匹配视频实际显示区域 (object-fit: contain)
const adjustOverlay = () => {
  const video = videowebrtc.value as HTMLVideoElement
  const canvas = document.getElementById('overlayCanvas') as HTMLCanvasElement
  if (!canvas) return

  if (!video) {
    // 兼容 FLV 播放模式下的遮罩层尺寸匹配
    const container = videoContainer.value as HTMLDivElement | null
    if (container) {
      canvas.style.width = `${container.clientWidth}px`
      canvas.style.height = `${container.clientHeight}px`
      canvas.style.left = '0px'
      canvas.style.top = '0px'
    }
    return
  }

  const videoRatio = video.videoWidth / video.videoHeight
  const elementRatio = video.clientWidth / video.clientHeight

  let renderWidth = video.clientWidth
  let renderHeight = video.clientHeight
  let left = 0
  let top = 0

  if (elementRatio > videoRatio) {
    // 容器更宽，视频由高度决定，左右留白
    renderWidth = video.clientHeight * videoRatio
    left = (video.clientWidth - renderWidth) / 2
  } else {
    // 容器更高，视频由宽度决定，上下留白
    renderHeight = video.clientWidth / videoRatio
    top = (video.clientHeight - renderHeight) / 2
  }

  canvas.style.width = `${renderWidth}px`
  canvas.style.height = `${renderHeight}px`
  canvas.style.left = `${left}px`
  canvas.style.top = `${top}px`

}


const getVideoResolution = () => {
  const rtc = document.getElementById('video-webrtc') as HTMLVideoElement
  console.log('video-webrtc', rtc.width, rtc.videoWidth)
  const video = videowebrtc.value;
  if (video && video.videoWidth && video.videoHeight) {
    resolution.value = {
      width: video.videoWidth,
      height: video.videoHeight
    };
    console.log('实际分辨率:', video.videoWidth, 'x', video.videoHeight);
  }
};

const currentRect = ref({ x: 0, y: 0, width: 0, height: 0 })


const onRectMouseUp = (e: MouseEvent) => {
  if (!isDrawing) return

  isDrawing = false
  const canvas = document.getElementById('overlayCanvas') as HTMLCanvasElement
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()

  // 计算最终选区数据（标准化为左上角起点 + 正宽高）
  const x: number = Number((Math.min(startX, endX) / rect.width).toFixed(6))
  const y: number = Number((Math.min(startY, endY) / rect.height).toFixed(6))
  const width: number = Number((Math.abs(endX - startX) / rect.width).toFixed(6))
  const height: number = Number((Math.abs(endY - startY) / rect.height).toFixed(6))



  // ✅ 输出或返回你需要的数据
  console.log('🎯 选区结果（无绘制）：', {
    x,
    y,
    width,
    height
  })
  if (width < 0.01) {
    clearRect()
    return
  }
  if (currentRect.value) {
    currentRect.value.x = x * rect.width
    currentRect.value.y = y * rect.height
    currentRect.value.width = width * rect.width
    currentRect.value.height = height * rect.height
  }
    cameraFrameZoom(dockSn.value, {
      payload_index: payloadSelectInfo.value.payloadIndex,
      camera_type: CameraType.ZOOM,
      locked: cameraAimPopoverData.locked,
      x: x,
      y: y,
      width: width,
      height: height
    })
    setTimeout(() => {
      clearRect()
    }, 500)
}

// 变焦值 - 初始值从设备变焦倍数参数获取
const zoomValue = ref(27)

// 监听设备变焦倍数变化，用于初始化 zoomValue
watch(
  () => deviceInfo.device?.[cameraPayloadIndex.value]?.zoom_factor,
  (newValue) => {
    if (newValue && typeof newValue === 'number' && newValue >= 2 && newValue <= 57) {
      // 只有当值有效时才更新（避免覆盖用户手动调整的值）
      if (zoomValue.value === 27) {
        zoomValue.value = Math.round(newValue)
      }
    }
  },
  { immediate: true }
)

// 尺子滑块拖拽功能
const startZoomDrag = (event: MouseEvent) => {
  event.preventDefault()
  const rulerContainer = (event.target as HTMLElement).closest('.zoom-ruler')
  if (!rulerContainer) return

  const rulerRect = rulerContainer.getBoundingClientRect()
  const trackTop = rulerRect.top + 8
  const trackHeight = rulerRect.height - 16

  const updateZoom = (e: MouseEvent) => {
    const relativeY = Math.max(0, Math.min(trackHeight, rulerRect.bottom - 8 - e.clientY))
    const percentage = relativeY / trackHeight
    const newValue = Math.round(2 + percentage * 55)
    zoomValue.value = Math.max(2, Math.min(57, newValue))
  }

  const stopDrag = () => {
    document.removeEventListener('mousemove', updateZoom)
    document.removeEventListener('mouseup', stopDrag)
    changeZoom(zoomValue.value)
  }

  document.addEventListener('mousemove', updateZoom)
  document.addEventListener('mouseup', stopDrag)
}

const keyDownHandler = (event: KeyboardEvent) => {
  const tag = (event.target as HTMLElement)?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || tag === 'select') return

  if ("1" === event.key) {
    onSwitch('wide')
  }
  if ("2" === event.key) {
    onSwitch('zoom')
  }
  if ("3" === event.key) {
    onSwitch('ir')
  }
  // 空格键 -> 急停
  if (event.code === 'Space') {
    event.preventDefault()
    handleEmergencyStopHandler()
  }
}
document.removeEventListener('keydown', keyDownHandler)
document.addEventListener('keydown', keyDownHandler)

const changeZoom = async (value: number) => {
  console.log('change zoom', value)
  await changeCameraFocalLength(dockSn.value, {
    payload_index: payloadSelectInfo.value.payloadIndex,
    camera_type: CameraType.ZOOM,
    zoom_factor: value
  })
}


const overlayCanvas = ref(null)
const redraw = () => {
  // const canvas = overlayCanvas.value as unknown as HTMLCanvasElement
  const canvas = document.getElementById('overlayCanvas') as HTMLCanvasElement
  const video = videowebrtc.value
  if (!video) return

  // 设置 canvas 内部尺寸为显示尺寸，以确保绘制坐标一致
  canvas.width = canvas.offsetWidth || video.clientWidth
  canvas.height = canvas.offsetHeight || video.clientHeight
  console.log(canvas.width, canvas.height)
  const ctx = canvas.getContext('2d')
  if (ctx) {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    if (currentRect.value) {
      ctx.strokeStyle = '#00ff00';
      ctx.lineWidth = 2;
      ctx.strokeRect(
        currentRect.value.x,
        currentRect.value.y,
        currentRect.value.width,
        currentRect.value.height
      );
    }
  }
}

const clearRect = () => {
  const canvas = document.getElementById('overlayCanvas') as HTMLCanvasElement
  const video = videowebrtc.value
  if (!video) return
  canvas.width = canvas.offsetWidth || video.clientWidth
  canvas.height = canvas.offsetHeight || video.clientHeight
  const ctx = canvas.getContext('2d')
  if (ctx) {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
  }
}



const getSTSToken = async () => {
  const minioSTS = await getSTSTokenApi()
  console.log(minioSTS)
}

// 云台快捷操作
const ptzControl = async (e: any) => {
  console.log('ptzControl', e)
  await resetGimbal(dockSn.value, {
    payload_index: payloadSelectInfo.value.payloadIndex,
    reset_mode: e.key
  })
}

const flyToPointPosition = reactive({
  longitude: 0,
  latitude: 0,
  height: 0
})
interface FlyToPointPosition {
  alt_height: number;
  longitude: number,
  latitude: number,
  height: number
}
const virtualFlyToPointMethod = (position: FlyToPointPosition, selectedDock: any) => {
  if (isWatchOnly.value) {
    message.warning('当前无人机正在航线飞行，无法操作')
    return
  }
  const modeCode = deviceInfo.device?.mode_code
  const isFlying = modeCode !== undefined && ![EModeCode.Standby, EModeCode.Preparing, EModeCode.Ready].includes(modeCode)
  const takeoffHeight = (deviceInfo.device?.height || 0) - (deviceInfo.device?.elevation || 0)

  if (isFlying) {
    console.log('无人机已起飞，执行指点飞行')
    flyToPoint(dockSn.value, {
      max_speed: 15,
      points: [{
        latitude: Number(position.latitude),
        longitude: Number(position.longitude),
        height: Number(position.alt_height) + Number(takeoffHeight)
      }]
    })
  } else {
    console.log('无人机未起飞，执行一键起飞指点')
    takeoffToPoint(dockSn.value, {
      target_latitude: position.latitude,
      target_longitude: position.longitude,
      target_height: Number(position.alt_height) + Number(takeoffHeight),
      security_takeoff_height: position.alt_height,
      max_speed: 15,
      rc_lost_action: LostControlActionInCommandFLight.RETURN_HOME,
      rth_altitude: position.alt_height,
      exit_wayline_when_rc_lost: WaylineLostControlActionInCommandFlight.EXEC_LOST_ACTION,
      rth_mode: ERthMode.SETTING,
      commander_mode_lost_action: ECommanderModeLostAction.CONTINUE,
      commander_flight_mode: ECommanderFlightMode.SETTING,
      commander_flight_height: position.alt_height
    })
  }
}

provide('virtualFlyToPointMethod', virtualFlyToPointMethod)


</script>

<style lang="scss" scoped>
.title {
  color: #868686;
}

.compass-base,
.compass-arrow {
  grid-area: 1 / 1;
}

.compass-arrow {
  transition: transform 1.5s ease;
}

.degree-display {
  position: absolute;
  bottom: 20%;
  left: 50%;
  transform: translateX(-50%);
  color: #fff;
  font-size: 24px;
  font-family: sans-serif;
  z-index: 1;
}

.bgBtnClass {
  grid-area: 1/1;
  position: relative;
  cursor: pointer;
}

.video-container {
  position: relative;
  width: 100%;
  height: 100%;
  //overflow: hidden;
}

.overlay-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
  /* pointer-events: none; */
  /* 允许鼠标事件穿透到 video */
}

/* 智能平视座舱HUD (スマートHUD) Styles */
.hud-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 10;
  font-family: 'Consolas', 'Courier New', monospace;
  overflow: hidden;

  .hud-speed-ladder {
    position: absolute;
    left: 20px;
    top: 50%;
    transform: translateY(-50%);
    display: flex;
    align-items: center;
    gap: 8px;
    background: rgba(0, 0, 0, 0.45);
    backdrop-filter: blur(4px);
    border: 1px solid rgba(0, 255, 255, 0.25);
    border-radius: 4px;
    padding: 8px 12px;
    color: #00ffff;
    box-shadow: 0 0 15px rgba(0, 255, 255, 0.1);
    
    .ladder-bar {
      height: 80px;
      width: 3px;
      background: rgba(0, 255, 255, 0.2);
      position: relative;
      border-radius: 2px;
      
      .indicator {
        position: absolute;
        width: 8px;
        height: 2px;
        background: #00ffff;
        left: -2px;
        box-shadow: 0 0 6px #00ffff;
        transition: bottom 0.1s ease-out;
      }
    }
  }

  .hud-alt-ladder {
    position: absolute;
    right: 20px;
    top: 50%;
    transform: translateY(-50%);
    display: flex;
    align-items: center;
    gap: 8px;
    background: rgba(0, 0, 0, 0.45);
    backdrop-filter: blur(4px);
    border: 1px solid rgba(0, 255, 255, 0.25);
    border-radius: 4px;
    padding: 8px 12px;
    color: #00ffff;
    box-shadow: 0 0 15px rgba(0, 255, 255, 0.1);
    
    .ladder-bar {
      height: 80px;
      width: 3px;
      background: rgba(0, 255, 255, 0.2);
      position: relative;
      border-radius: 2px;
      
      .indicator {
        position: absolute;
        width: 8px;
        height: 2px;
        background: #00ffff;
        right: -2px;
        box-shadow: 0 0 6px #00ffff;
        transition: bottom 0.1s ease-out;
      }
    }
  }

  .hud-center-reticle {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 200px;
    height: 200px;
    display: flex;
    align-items: center;
    justify-content: center;

    .hud-crosshair {
      width: 20px;
      height: 20px;
      border: 1px dashed rgba(0, 255, 255, 0.4);
      border-radius: 50%;
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;

      &::before, &::after {
        content: '';
        position: absolute;
        background: #00ffff;
      }
      &::before {
        width: 6px;
        height: 1.5px;
      }
      &::after {
        width: 1.5px;
        height: 6px;
      }
    }

    .hud-pitch-ladder {
      position: absolute;
      inset: 0;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      align-items: center;
      padding: 30px 0;
      transition: transform 0.1s ease-out;

      .pitch-line {
        width: 40px;
        height: 1px;
        background: rgba(0, 255, 255, 0.4);
        position: relative;

        &::before, &::after {
          content: attr(data-angle);
          position: absolute;
          font-size: 8px;
          color: rgba(0, 255, 255, 0.6);
          top: -5px;
        }
        &::before { left: -22px; }
        &::after { right: -22px; }
      }

      .horizon-line {
        width: 80px;
        height: 1.5px;
        background: rgba(0, 255, 255, 0.85);
        box-shadow: 0 0 8px rgba(0, 255, 255, 0.5);
        position: relative;

        &::before, &::after {
          content: '0°';
          position: absolute;
          font-size: 9px;
          color: #00ffff;
          font-weight: bold;
          top: -6px;
        }
        &::before { left: -18px; }
        &::after { right: -18px; }
      }
    }
  }

  .hud-footer-stats {
    position: absolute;
    bottom: 12px;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    align-items: center;
    gap: 16px;
    background: rgba(0, 0, 0, 0.55);
    backdrop-filter: blur(6px);
    border: 1px solid rgba(0, 255, 255, 0.25);
    padding: 6px 18px;
    border-radius: 20px;
    font-size: 10px;
    color: #00ffff;
    box-shadow: 0 4px 15px rgba(0, 0, 0, 0.3);

    .stat-divider {
      width: 1px;
      height: 10px;
      background: rgba(0, 255, 255, 0.2);
    }
    
    .stat-label {
      color: rgba(0, 255, 255, 0.55);
      margin-right: 4px;
    }
    
    .stat-value {
      font-weight: bold;
      color: #ffffff;
      text-shadow: 0 0 4px rgba(0, 255, 255, 0.3);
    }
  }
}

.fov-overlay {
  position: absolute;
  z-index: 15;
  transition: all 0.15s ease-out;

  .fov-svg {
    width: 100%;
    height: 100%;
    display: block;
  }


}

.preview-image-container {
  position: relative;
  display: inline-block;
}

.close-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 24px;
  height: 24px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: bold;
  transition: all 0.2s ease;
  z-index: 10;

  &:hover {
    background: rgba(255, 77, 79, 0.9);
    transform: scale(1.1);
  }
}

.battery-slide {
  .capacity-percent {
    background: #00ee8b;
  }

  .return-home {
    background: #ff9f0a;
  }

  .landing {
    background: #f5222d;
  }

  .white-point {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: white;
    bottom: -0.5px;
  }

  .battery {
    background: #141414;
    color: #00ee8b;
    margin-top: -10px;
    height: 20px;
    width: auto;
    border-left: 1px solid #00ee8b;
    padding: 0 5px;
  }
}

.battery-slide>div {
  position: absolute;
  min-height: 2px;
  border-radius: 2px;
}

.floating-preview {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  width: 200px;
  height: 200px;
  z-index: 1000;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.floating-preview .close-btn {
  margin-top: 8px;
  font-size: 12px;
  color: #999;
}

.d-pad-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 70px;
  height: 70px;
  background: rgba(31, 41, 55, 0.8);
  border-radius: 50%;
  border: 1px solid rgba(75, 85, 99, 0.5);
  padding: 2px;
  margin-left: 8px;
}

.d-pad-middle {
  display: flex;
  justify-content: space-between;
  width: 100%;
  align-items: center;
}

.d-pad-btn {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #9ca3af;
  transition: all 0.2s;
  border-radius: 4px;

  &:hover {
    color: #fff;
    background: rgba(255, 255, 255, 0.1);
  }

  &:active {
    color: #00ee8b;
    transform: scale(0.9);
  }
}

.d-pad-center {
  width: 16px;
  height: 16px;
  background: rgba(75, 85, 99, 0.5);
  border-radius: 50%;
}

/* Telemetry Marquee Animation */
.telemetry-marquee {
  overflow: hidden;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.telemetry-content {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  padding: 8px 12px;
  font-size: 12px;
  /* No animation by default */
}

/* Only animate when scrolling is needed */
.telemetry-content.telemetry-scrolling {
  animation: marquee 20s linear infinite;
}

.telemetry-content.telemetry-scrolling:hover {
  animation-play-state: paused;
}

@keyframes marquee {
  0% {
    transform: translateX(100%);
  }

  100% {
    transform: translateX(-100%);
  }
}

/* Drone Startup Progress Animation */
@keyframes droneStartupProgress {

  0%,
  100% {
    width: 30%;
  }

  50% {
    width: 80%;
  }
}


/* 尺子样式变焦滑块 */
.zoom-ruler-container {
  padding: 4px;
  user-select: none;
}

.zoom-ruler {
  position: relative;
  width: 50px;
  height: 140px;
  background: linear-gradient(to right, rgba(0, 0, 0, 0.85) 0%, rgba(20, 20, 20, 0.95) 100%);
  border-radius: 6px;
  border: 1px solid rgba(100, 100, 100, 0.5);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
}

.ruler-track {
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 100%;
}

.ruler-tick {
  position: absolute;
  left: 0;
  width: 100%;
  height: 1px;
  display: flex;
  align-items: center;
}

.ruler-tick::before {
  content: '';
  position: absolute;
  left: 4px;
  width: 6px;
  height: 1px;
  background: rgba(255, 255, 255, 0.3);
}

.ruler-tick.major::before {
  width: 10px;
  height: 1px;
  background: rgba(0, 255, 127, 0.6);
}

.ruler-tick .tick-label {
  position: absolute;
  left: 16px;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.8);
  white-space: nowrap;
  transform: translateY(-50%);
  font-family: 'Consolas', monospace;
}

.ruler-indicator {
  position: absolute;
  right: 100%;
  margin-right: 2px;
  display: flex;
  align-items: center;
  cursor: ns-resize;
  transform: translateY(50%);
  z-index: 10;
  transition: bottom 0.05s ease-out;
}

.indicator-value {
  background: linear-gradient(135deg, #00ff7f 0%, #00cc66 100%);
  color: #000;
  font-size: 11px;
  font-weight: bold;
  padding: 2px 6px;
  border-radius: 3px;
  white-space: nowrap;
  box-shadow: 0 1px 4px rgba(0, 255, 127, 0.5);
  font-family: 'Consolas', monospace;
}

.indicator-arrow {
  color: #00ff7f;
  font-size: 10px;
  text-shadow: 0 0 6px rgba(0, 255, 127, 1);
  margin-left: 2px;
}

.hidden-slider {
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 100%;
  opacity: 0;
  cursor: ns-resize;
  writing-mode: bt-lr;
  -webkit-appearance: slider-vertical;
  appearance: slider-vertical;
}

/* Modal Dark Theme Override */
:global(.dark-confirm-modal .ant-modal-content),
:global(.dark-confirm-modal .ant-modal-header) {
  background-color: #1f2937 !important;
  /* gray-800 */
}

:global(.dark-confirm-modal .ant-modal-title),
:global(.dark-confirm-modal .ant-modal-confirm-content) {
  color: white !important;
}

:global(.dark-confirm-modal .ant-modal-confirm-body .anticon) {
  color: #fbbf24 !important;
  /* yellow-400 */
}

/* Dark theme button styles */
.dark-cancel-btn {
  background: rgba(75, 85, 99, 0.8) !important;
  border-color: #6b7280 !important;
  color: #fff !important;
}

.dark-cancel-btn:hover {
  background: rgba(107, 114, 128, 0.9) !important;
  border-color: #9ca3af !important;
  color: #fff !important;
}

.dark-disabled-btn {
  background: rgba(55, 65, 81, 0.8) !important;
  border-color: #4b5563 !important;
  color: rgba(156, 163, 175, 0.8) !important;
}

/* 自定义暗色细滚动条 */
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
  height: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: rgba(31, 41, 55, 0.5);
  border-radius: 2px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(75, 85, 99, 0.8);
  border-radius: 2px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: rgba(107, 114, 128, 1);
}
</style>
