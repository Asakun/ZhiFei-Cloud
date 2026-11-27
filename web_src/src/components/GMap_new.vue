<template>
  <div class="g-map-wrapper">
    <!-- 飞机OSD -->
    <div v-if="osdVisible.visible && !osdVisible.is_dock" v-drag-window class="osd-panel fz12">
      <div class="osd-header drag-title">
        <span>{{ osdVisible.callsign }}</span>
        <span><a class="fz16" style="color: white; cursor: pointer;" @mousedown.stop @pointerdown.stop
            @click="() => osdVisible.visible = false">
            <CloseOutlined />
          </a></span>
      </div>
      <div class="osd-body">
        <div class="osd-icon-box">
          <a-tooltip :title="osdVisible.model">
            <div class="flex-column flex-align-center flex-justify-center" style="width: 100%;">
              <span><a-image :preview="false" :src="M30" style="width: 40px;" /></span>
              <span style="font-size: 10px; margin-top: 4px; opacity: 0.8;">{{ osdVisible.model }}</span>
            </div>
          </a-tooltip>
        </div>
        <div class="osd osd-info-grid">
          <a-row>
            <a-col
              :style="deviceInfo.device.mode_code === EModeCode.Disconnected ? 'color: #ff4d4f; font-weight: 700;' : 'color: #52c41a'"
              span="16">
              {{ EModeWord[EModeCode[deviceInfo.device.mode_code] as keyof typeof EModeWord] }}
            </a-col>
          </a-row>
          <a-row>
            <a-col span="6">
              <a-tooltip title="信号强度">
                <div class="osd-item">
                  <span class="osd-label">HD</span>
                  <span class="osd-value">{{ deviceInfo.gateway?.transmission_signal_quality }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="遥控器电量">
                <div class="osd-item">
                  <span class="osd-label"><ThunderboltOutlined class="fz14" /></span>
                  <span class="osd-value">{{
                    deviceInfo.gateway && deviceInfo.gateway.capacity_percent !== str ?
                      deviceInfo.gateway?.capacity_percent + ' %' : deviceInfo.gateway?.capacity_percent
                  }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="飞机电量">
                <div class="osd-item">
                  <span class="osd-label"><ThunderboltOutlined class="fz14" /></span>
                  <span class="osd-value">{{
                    deviceInfo.device.battery.capacity_percent !== str ? deviceInfo.device.battery.capacity_percent + ' %'
                      : deviceInfo.device.battery.capacity_percent
                  }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip placement="top">
                <template #title>
                  <div style="line-height: 1.6; white-space: nowrap;">
                    <div>总容量: {{ Math.floor((deviceInfo.device?.storage?.total ?? 0) / 1024 / 1024) }}GB</div>
                    <div>已使用: {{ Math.floor((deviceInfo.device?.storage?.used ?? 0) / 1024) }}MB</div>
                  </div>
                </template>
                <div class="osd-item">
                  <span class="osd-label"><FolderOpenOutlined class="fz14" /></span>
                  <span class="osd-value">{{
                    (deviceInfo.device?.storage?.total ?? 0) > 0 ?
                      Math.round((deviceInfo.device?.storage?.used ?? 0) * 100 / (deviceInfo.device?.storage?.total || 1)) + ' %'
                      : str
                  }}</span>
                </div>
              </a-tooltip>
            </a-col>
          </a-row>
          <a-row>
            <a-col span="6">
              <a-tooltip title="RTK 定位状态">
                <div class="osd-item">
                  <span class="osd-label">Fixed</span>
                  <span
                    :style="deviceInfo.device.position_state.is_fixed === 1 ? 'background: #52c41a;' : 'background: #ff4d4f;'"
                    class="circle"></span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="GPS 卫星数">
                <div class="osd-item">
                  <span class="osd-label">GPS</span>
                  <span class="osd-value">{{ deviceInfo.device.position_state.gps_number }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="RTK 卫星数">
                <div class="osd-item">
                  <span class="osd-label"><TrademarkOutlined class="fz14" /></span>
                  <span class="osd-value">{{ deviceInfo.device.position_state.rtk_number }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="飞行档位">
                <div class="osd-item">
                  <span class="osd-label"><ControlOutlined class="fz14" /></span>
                  <span class="osd-value">{{ EGear[deviceInfo.device.gear] }}</span>
                </div>
              </a-tooltip>
            </a-col>
          </a-row>
          <a-row>
            <a-col span="8">
              <a-tooltip title="绝对高度">
                <div class="osd-item">
                  <span class="osd-label">ASL</span>
                  <span class="osd-value">{{ deviceInfo.device.height === str ? str : Number(deviceInfo.device.height).toFixed(2) + ' m' }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="8">
              <a-tooltip title="相对起飞高度">
                <div class="osd-item">
                  <span class="osd-label">ALT</span>
                  <span class="osd-value">{{
                    deviceInfo.device.elevation === str ? str : Number(deviceInfo.device.elevation).toFixed(2) + ' m'
                  }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="8">
              <a-tooltip title="离HOME距离">
                <div class="osd-item">
                  <span class="osd-label">H</span>
                  <span class="osd-value">{{
                    deviceInfo.device.home_distance === str ? str : Number(deviceInfo.device.home_distance).toFixed(2) + ' m'
                  }}</span>
                </div>
              </a-tooltip>
            </a-col>
          </a-row>
          <a-row>
            <a-col span="8">
              <a-tooltip title="水平速度">
                <div class="osd-item">
                  <span class="osd-label">H.S</span>
                  <span class="osd-value">{{ deviceInfo.device.horizontal_speed === str ? str :
                    Number(deviceInfo.device.horizontal_speed).toFixed(2) + ' m/s' }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="8">
              <a-tooltip title="垂直速度">
                <div class="osd-item">
                  <span class="osd-label">V.S</span>
                  <span class="osd-value">{{ deviceInfo.device.vertical_speed === str ? str :
                    Number(deviceInfo.device.vertical_speed).toFixed(2) + ' m/s' }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="8">
              <a-tooltip title="风速">
                <div class="osd-item">
                  <span class="osd-label">W.S</span>
                  <span class="osd-value">{{ deviceInfo.device.wind_speed === str ? str : (Number(deviceInfo.device.wind_speed) /
                    10).toFixed(2) + ' m/s' }}</span>
                </div>
              </a-tooltip>
            </a-col>
          </a-row>
        </div>
      </div>
      <div v-if="deviceInfo.device.battery.remain_flight_time !== 0" class="battery-slide">
        <div class="width-100" style="background: #535759;"></div>
        <div :style="{ width: deviceInfo.device.battery.capacity_percent + '%' }" class="capacity-percent"></div>
        <div :style="{ width: deviceInfo.device.battery.return_home_power + '%' }" class="return-home"></div>
        <div :style="{ width: deviceInfo.device.battery.landing_power + '%' }" class="landing"></div>
        <div :style="{ left: deviceInfo.device.battery.landing_power + '%' }" class="white-point"></div>
        <div :style="{ left: deviceInfo.device.battery.capacity_percent + '%' }" class="battery">
          {{ Math.floor(deviceInfo.device.battery.remain_flight_time / 60) }}:
          {{ 10 > (deviceInfo.device.battery.remain_flight_time % 60) ? '0' : '' }}{{
            deviceInfo.device.battery.remain_flight_time % 60
          }}
        </div>
      </div>
    </div>
    <!-- 机场OSD -->
    <div v-if="osdVisible.visible && osdVisible.is_dock" v-drag-window class="osd-panel fz12">
      <div class="osd-header drag-title">
        <span>{{ osdVisible.gateway_callsign }}</span>
        <span><a class="fz16" style="color: white; cursor: pointer;" @mousedown.stop @pointerdown.stop
            @click="() => osdVisible.visible = false">
            <CloseOutlined />
          </a></span>
      </div>

      <!-- 机场 -->
      <div class="osd-body">
        <div class="osd-icon-box">
          <a-tooltip :title="osdVisible.model">
            <div class="flex-column flex-align-center flex-justify-center" style="width: 100%;">
              <a-image :preview="false" :src="Dock2" style="width: 40px; height: 40px;" />
              <span class="mt5" style="font-size: 10px; opacity: 0.8;"
                v-if="osdVisible.model === 'M3TD' || osdVisible.model === 'M3D'">Dock2</span>
              <span class="mt5" style="font-size: 10px; opacity: 0.8;"
                v-if="osdVisible.model === 'M4TD' || osdVisible.model === 'M4D'">Dock3</span>
            </div>
          </a-tooltip>
        </div>
        <div class="osd osd-info-grid">
          <a-row>
            <a-col
              :style="deviceInfo.dock.basic_osd?.mode_code === EDockModeCode.Disconnected ? 'color: #ff4d4f; font-weight: 700;' : 'color: #52c41a'"
              span="16">
              {{ (EDockModeCodeZh as any)[EDockModeCode[deviceInfo.dock.basic_osd?.mode_code]] }}
            </a-col>
          </a-row>
          <a-row>
            <a-col span="24">
              <a-tooltip title="机场累计运行时长">
                <span>
                  <HistoryOutlined />
                </span>
                <span class="ml10">
                  <span v-if="deviceInfo.dock.work_osd?.acc_time >= 2592000"> {{
                    Math.floor(deviceInfo.dock.work_osd?.acc_time / 2592000)
                  }}月</span>
                  <span v-if="(deviceInfo.dock.work_osd?.acc_time % 2592000) >= 86400"> {{
                    Math.floor((deviceInfo.dock.work_osd?.acc_time % 2592000) / 86400)
                  }}天</span>
                  <span v-if="(deviceInfo.dock.work_osd?.acc_time % 2592000 % 86400) >= 3600"> {{
                    Math.floor((deviceInfo.dock.work_osd?.acc_time % 2592000 % 86400) / 3600)
                  }}小时</span>
                  <span v-if="(deviceInfo.dock.work_osd?.acc_time % 2592000 % 86400 % 3600) >= 60"> {{
                    Math.floor((deviceInfo.dock.work_osd?.acc_time % 2592000 % 86400 % 3600) / 60)
                  }}分</span>
                  <span>{{ Math.floor(deviceInfo.dock.work_osd?.acc_time % 2592000 % 86400 % 3600 % 60) }}秒</span>
                </span>
              </a-tooltip>
            </a-col>
          </a-row>
          <a-row>
            <a-col span="24">
              <a-tooltip title="激活时间">
                <span>
                  <FieldTimeOutlined />
                </span>
                <span class="ml10">{{
                  new Date((deviceInfo.dock.work_osd?.activation_time ?? 0) * 1000).toLocaleString()
                }}
                </span>
              </a-tooltip>
            </a-col>
          </a-row>
          <a-row>
            <a-col span="6">
              <a-tooltip title="网络状态">
                <span :style="qualityStyle">
                  <span v-if="deviceInfo.dock.basic_osd?.network_state?.type === NetworkStateTypeEnum.FOUR_G">
                    <SignalFilled />
                  </span>
                  <span v-else>
                    <GlobalOutlined />
                  </span>
                </span>
                <span class="ml10">{{ deviceInfo.dock.basic_osd?.network_state?.rate }} kb/s</span>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="机场累计作业次数	">
                <span>
                  <CarryOutOutlined />
                </span>
                <span class="ml10">{{ deviceInfo.dock.work_osd?.job_number }} </span>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="媒体上传">
                <span>
                  <CloudUploadOutlined class="fz14" />
                </span>
                <span class="ml10">{{ deviceInfo.dock.link_osd?.media_file_detail?.remain_upload }}</span>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip placement="bottom">
                <template #title>
                  <div style="line-height: 1.6; white-space: nowrap;">
                    <div>总容量: {{ Math.floor((deviceInfo.dock.basic_osd?.storage?.total ?? 0) / 1024 / 1024) }}GB</div>
                    <div>已使用: {{ Math.floor((deviceInfo.dock.basic_osd?.storage?.used ?? 0) / 1024) }}MB</div>
                  </div>
                </template>
                <div class="osd-item">
                  <span class="osd-label"><FolderOpenOutlined class="fz14" /></span>
                  <span class="osd-value">{{
                    (deviceInfo.dock.basic_osd?.storage?.total ?? 0) > 0 ?
                      Math.round((deviceInfo.dock.basic_osd?.storage?.used ?? 0) * 100 / (deviceInfo.dock.basic_osd?.storage?.total || 1)) + ' %'
                      : str
                  }}</span>
                </div>
              </a-tooltip>
            </a-col>
          </a-row>
          <a-row>
            <a-col span="6">
              <a-tooltip title="风速">
                <span>W.S</span>
                <span class="ml10">{{ (deviceInfo.dock.basic_osd?.wind_speed ?? str) + ' m/s' }}</span>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="降雨量">
                <span>🌧</span>
                <span class="ml10">{{ RainfallEnum[deviceInfo.dock.basic_osd?.rainfall] }}</span>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="环境温度">
                <span>°C</span>
                <span class="ml10">{{ deviceInfo.dock.basic_osd?.environment_temperature }}</span>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="舱内温度">
                <span>°C</span>
                <span class="ml10">{{ deviceInfo.dock.basic_osd?.temperature }}</span>
              </a-tooltip>
            </a-col>
          </a-row>
          <a-row>
            <a-col span="6">
              <a-tooltip title="舱内湿度">
                <span>💦</span>
                <span class="ml10">{{ deviceInfo.dock.basic_osd?.humidity }}</span>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="工作电压">
                <span
                  style="border: 1px solid; border-radius: 50%; width: 18px; height: 18px; line-height: 16px; text-align: center; float: left;">V</span>
                <span class="ml10">{{ (deviceInfo.dock.work_osd?.working_voltage != null ? (Number(deviceInfo.dock.work_osd.working_voltage) / 1000) : str) + ' V' }}</span>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="工作电流">
                <span
                  style="border: 1px solid; border-radius: 50%; width: 18px; height: 18px; line-height: 15px; text-align: center; float: left;">A</span>
                <span class="ml10">{{ (deviceInfo.dock.work_osd?.working_current != null ? (Number(deviceInfo.dock.work_osd.working_current) / 1000) : str) + ' A' }}</span>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="飞行器是否在舱">
                <span>
                  <RocketOutlined />
                </span>
                <span class="ml10">{{ deviceInfo.dock.basic_osd?.drone_in_dock }}</span>
              </a-tooltip>
            </a-col>
          </a-row>
          <a-row class="p5">
            <a-col class="flex" span="24">
              <a-button class="w-11/12" ghost size="small" @click="DockLive">机场直播</a-button>
              <a-image :disabled="dockControlPanelVisible" :preview="false" :src="actions" class="cursor-pointer"
                style="width: 20px;height: 20px;" @click="setDockControlPanelVisible(true)" />
            </a-col>
          </a-row>
          <a-row>
            <a-col v-if="dockVisible" span="24">
              <div class="w-full h-[12rem] bg-black/40 rounded mt-2 overflow-hidden">
                <webRtcPlay ref="dockPlayer" v-if="dockVisible" :dockId="dockId"></webRtcPlay>
              </div>
            </a-col>
          </a-row>
        </div>
      </div>
      <!--  飞机-->
      <div class="osd-body">
        <div class="osd-icon-box">
          <a-tooltip :title="osdVisible.model">
            <div class="flex-column flex-align-center flex-justify-center" style="width: 100%;">
              <span><a-image :preview="false" :src="M30" style="width: 40px;" /></span>
              <span style="font-size: 10px; margin-top: 4px; opacity: 0.8;">{{ osdVisible.model }}</span>
            </div>
          </a-tooltip>
        </div>
        <div class="osd osd-info-grid">
          <a-row>
            <a-col :style="!deviceInfo.device || deviceInfo.device?.mode_code === EModeCode.Disconnected
              ? 'color: #8c8c8c; font-weight: 700;' : 'color: #52c41a'" span="16">
              {{
                !deviceInfo.device ? EModeWord[EModeCode[EModeCode.Disconnected] as keyof typeof EModeWord] :
                  EModeWord[EModeCode[deviceInfo.device?.mode_code] as keyof typeof EModeWord]
              }}
            </a-col>
          </a-row>
          <a-row>
            <a-col span="6">
              <a-tooltip title="上行图传信号质量">
                <div class="osd-item">
                  <span class="osd-label">
                    <SignalFilled />
                    <ArrowUpOutlined style="font-size: 9px; vertical-align: top;" />
                  </span>
                  <span class="osd-value">{{ deviceInfo.dock.link_osd?.sdr?.up_quality }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="下行图传信号质量">
                <div class="osd-item">
                  <span class="osd-label">
                    <SignalFilled />
                    <ArrowDownOutlined style="font-size: 9px; vertical-align: top;" />
                  </span>
                  <span class="osd-value">{{ deviceInfo.dock.link_osd?.sdr?.down_quality }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="飞机电量">
                <div class="osd-item">
                  <span class="osd-label"><ThunderboltOutlined class="fz14" /></span>
                  <span class="osd-value">{{
                    deviceInfo.device && deviceInfo.device.battery.capacity_percent !== str ?
                      deviceInfo.device?.battery.capacity_percent + ' %' : str
                  }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip placement="bottom">
                <template #title>
                  <div style="line-height: 1.6; white-space: nowrap;">
                    <div>总容量: {{ Math.floor((deviceInfo.device?.storage?.total ?? 0) / 1024 / 1024) }}GB</div>
                    <div>已使用: {{ Math.floor((deviceInfo.device?.storage?.used ?? 0) / 1024) }}MB</div>
                  </div>
                </template>
                <div class="osd-item">
                  <span class="osd-label"><FolderOpenOutlined class="fz14" /></span>
                  <span class="osd-value">{{
                    (deviceInfo.device?.storage?.total ?? 0) > 0 ?
                      Math.round((deviceInfo.device?.storage?.used ?? 0) * 100 / (deviceInfo.device?.storage?.total || 1)) + ' %'
                      : str
                  }}</span>
                </div>
              </a-tooltip>
            </a-col>
          </a-row>
          <a-row>
            <a-col span="6">
              <a-tooltip title="RTK 定位状态">
                <div class="osd-item">
                  <span class="osd-label">Fixed</span>
                  <span
                    :style="deviceInfo.device?.position_state.is_fixed === 1 ? 'background: #52c41a;' : 'background: #ff4d4f;'"
                    class="circle"></span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="GPS 卫星数">
                <div class="osd-item">
                  <span class="osd-label">GPS</span>
                  <span class="osd-value">{{ deviceInfo.device ? deviceInfo.device.position_state.gps_number : str }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="RTK 卫星数">
                <div class="osd-item">
                  <span class="osd-label"><TrademarkOutlined class="fz14" /></span>
                  <span class="osd-value">{{ deviceInfo.device ? deviceInfo.device.position_state.rtk_number : str }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="6">
              <a-tooltip title="飞行档位">
                <div class="osd-item">
                  <span class="osd-label"><ControlOutlined class="fz16" /></span>
                  <span class="osd-value">{{ deviceInfo.device ? EGear[deviceInfo.device?.gear] : str }}</span>
                </div>
              </a-tooltip>
            </a-col>
          </a-row>
          <a-row>
            <a-col span="8">
              <a-tooltip title="绝对高度">
                <div class="osd-item">
                  <span class="osd-label">ASL</span>
                  <span class="osd-value">{{ !deviceInfo.device || deviceInfo.device.height === str ? str :
                    Number(deviceInfo.device?.height).toFixed(2) + ' m' }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="8">
              <a-tooltip title="相对起飞高度">
                <div class="osd-item">
                  <span class="osd-label">ALT</span>
                  <span class="osd-value">{{
                    !deviceInfo.device || deviceInfo.device.elevation === str ? str :
                      Number(deviceInfo.device?.elevation).toFixed(2) +
                      ' m'
                  }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="8">
              <a-tooltip title="离HOME距离">
                <div class="osd-item">
                  <span class="osd-label">H</span>
                  <span class="osd-value">{{
                    !deviceInfo.device || deviceInfo.device.home_distance === str ? str :
                      Number(deviceInfo.device?.home_distance).toFixed(2) + ' m'
                  }}</span>
                </div>
              </a-tooltip>
            </a-col>
          </a-row>
          <a-row>
            <a-col span="8">
              <a-tooltip title="水平速度">
                <div class="osd-item">
                  <span class="osd-label">H.S</span>
                  <span class="osd-value">{{ !deviceInfo.device || deviceInfo.device?.horizontal_speed === str ? str :
                    Number(deviceInfo.device?.horizontal_speed).toFixed(2) + ' m/s' }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="8">
              <a-tooltip title="垂直速度">
                <div class="osd-item">
                  <span class="osd-label">V.S</span>
                  <span class="osd-value">{{ !deviceInfo.device || deviceInfo.device.vertical_speed === str ? str :
                    Number(deviceInfo.device.vertical_speed).toFixed(2) + ' m/s' }}</span>
                </div>
              </a-tooltip>
            </a-col>
            <a-col span="8">
              <a-tooltip title="风速">
                <div class="osd-item">
                  <span class="osd-label">W.S</span>
                  <span class="osd-value">{{ !deviceInfo.device || deviceInfo.device.wind_speed === str ? str :
                    (Number(deviceInfo.device?.wind_speed) / 10).toFixed(2) + ' m/s' }}</span>
                </div>
              </a-tooltip>
            </a-col>
          </a-row>
          <a-row v-if="deviceInfo.device" class="p5">
            <a-col :span="12">
              <a-button class="w-11/12" size="small" @click="virtualCockpit">虚拟座舱</a-button>
            </a-col>
            <a-col :span="12">
              <a-button class="w-11/12" size="small" @click="PlateLive"> {{ osdVisible.model }}</a-button>
            </a-col>
          </a-row>
          <a-row v-if="!deviceInfo.device">
            <a-col :span="10">
              <a-button class="w-11/12" size="small" @click="virtualCockpit">虚拟座舱</a-button>
            </a-col>
            <a-col :span="14">
              <div class="text-gray-300"> 当前设备已关机，无法进行直播</div>
            </a-col>
          </a-row>
          <a-row>
            <a-col :span="24">
              <div v-if="droneVisible" class="w-full h-[12rem] bg-black/40 rounded mt-2 overflow-hidden">
                <webRtcPlay ref="dronePlayer" v-if="droneVisible" :droneId="droneId"></webRtcPlay>
              </div>
            </a-col>
          </a-row>
        </div>
      </div>
      <div v-if="deviceInfo.device && deviceInfo.device.battery.remain_flight_time !== 0" class="battery-slide">
        <div class="width-100" style="background: #535759;"></div>
        <div :style="{ width: deviceInfo.device.battery.capacity_percent + '%' }" class="capacity-percent"></div>
        <div :style="{ width: deviceInfo.device.battery.return_home_power + '%' }" class="return-home"></div>
        <div :style="{ width: deviceInfo.device.battery.landing_power + '%' }" class="landing"></div>
        <div :style="{ left: deviceInfo.device.battery.landing_power + '%' }" class="white-point"></div>
        <div :style="{ left: deviceInfo.device.battery.capacity_percent + '%' }" class="battery">
          {{ Math.floor(deviceInfo.device.battery.remain_flight_time / 60) }}:
          {{ 10 > (deviceInfo.device.battery.remain_flight_time % 60) ? '0' : '' }}{{
            deviceInfo.device.battery.remain_flight_time % 60
          }}
        </div>
      </div>
      <!-- 飞行指令 -->
      <DroneControlPanel v-if="false" :deviceInfo="deviceInfo" :payloads="osdVisible.payloads"
        :sn="osdVisible.gateway_sn">
      </DroneControlPanel>
    </div>
    <!-- 机场控制面板 - Floating Outside OSD -->
    <DockControlPanel v-if="osdVisible.visible && osdVisible.is_dock && dockControlPanelVisible"
      :deviceInfo="deviceInfo" :sn="osdVisible.gateway_sn" @close-control-panel="onCloseControlPanel">
    </DockControlPanel>
    <!-- liveview -->
    <div v-if="livestreamOthersVisible" v-drag-window class="liveview">
      <div class="drag-title" style="height: 40px; width: 100%"></div>
      <a style="position: absolute; right: 10px; top: 10px; font-size: 16px; color: white;"
        @click="closeLivestreamOthers">
        <CloseOutlined />
      </a>
      <LivestreamOthers />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  generateLineContent,
  generatePointContent,
  generatePolyContent
} from '@/utils/map-layer-utils'
import { postElementsReq } from '@/api/layer'
import { MapDoodleType, MapElementEnum } from '@/constants/map'
import { getApp, getRoot } from '@/root'
import { useMyStore } from '@/store'
import { GeojsonCoordinate } from '@/types/map'
import { MapDoodleEnum } from '@/types/map-enum'
import { PostElementsBody } from '@/types/mapLayer'
import { uuidv4 } from '@/utils/uuid'
import { gcj02towgs84, wgs84togcj02 } from '@/vendors/coordtransform'
import { deviceTsaUpdate } from '@/hooks/use-g-map-tsa'
import { useGMapCover } from '@/hooks/use-g-map-cover'
import {
  DeviceOsd, DeviceStatus, DockOsd, EGear, EModeCode, EModeWord, GatewayOsd, EDockModeCode,
  NetworkStateQualityEnum, NetworkStateTypeEnum, RainfallEnum, DroneInDockEnum, EDockModeCodeZh, PAYLOAD_TYPE
} from '@/types/device'
import pin from '@/assets/icons/pin-2d8cf0.svg'
import M30 from '@/assets/icons/m30.png'
import Dock2 from '@/assets/icons/Dock2.png'
import actions from '@/assets/icons/actions.png'
import {
  BorderOutlined, LineOutlined, CloseOutlined, ControlOutlined, TrademarkOutlined, ArrowDownOutlined,
  ThunderboltOutlined, SignalFilled, GlobalOutlined, HistoryOutlined, CloudUploadOutlined, RocketOutlined,
  FieldTimeOutlined, CloudOutlined, CloudFilled, FolderOpenOutlined, RobotFilled, ArrowUpOutlined, CarryOutOutlined
} from '@ant-design/icons-vue'
import { EDeviceTypeName } from '@/types'
import DockControlPanel from './g-map/DockControlPanel.vue'
import { useDockControl } from './g-map/use-dock-control'
import DroneControlPanel from './g-map/DroneControlPanel.vue'
import { useConnectMqtt } from './g-map/use-connect-mqtt'
import LivestreamOthers from './livestream-others.vue'
import FlightAreaActionIcon from './flight-area/FlightAreaActionIcon.vue'
import { EFlightAreaType } from '@/types/flight-area'
import { useFlightArea } from './flight-area/use-flight-area'
import { useFlightAreaDroneLocationEvent } from './flight-area/use-flight-area-drone-location-event'
import { CURRENT_CONFIG as config } from "@/api/http/config";
import { startLivestream, stopLivestream } from "@/api/manage";
import { useRouter } from "vue-router";
import WebRtcPlay from "@/components/webRtcPlay.vue";
import { message } from "ant-design-vue";
import router from "@/router";
    // const useMouseToolHook = useMouseTool()
    // const useGMapManageHook = useGMapManage()
    const deviceTsaUpdateHook = deviceTsaUpdate()
    const store = useMyStore()
    const state = reactive({
      currentType: '',
      coverIndex: 0,
      isFlightArea: false,
    })
    const str: string = '--'
    const deviceInfo = reactive({
      gateway: {
        capacity_percent: str,
        transmission_signal_quality: str,
      } as GatewayOsd,
      dock: {} as DockOsd,
      device: {
        gear: -1,
        mode_code: EModeCode.Disconnected,
        height: str,
        home_distance: str,
        horizontal_speed: str,
        vertical_speed: str,
        wind_speed: str,
        wind_direction: str,
        elevation: str,
        position_state: {
          gps_number: str,
          is_fixed: 0,
          rtk_number: str
        },
        battery: {
          capacity_percent: str,
          landing_power: str,
          remain_flight_time: 0,
          return_home_power: str,
        },
        storage: {
          total: 0,
          used: 0,
        },
        latitude: 0,
        longitude: 0,
      } as DeviceOsd
    })
    const shareId = computed(() => {
      return store.state.layerBaseInfo.share
    })
    const defaultId = computed(() => {
      return store.state.layerBaseInfo.default
    })
    const drawVisible = computed(() => {
      return store.state.drawVisible
    })
    const livestreamOthersVisible = computed(() => {
      return store.state.livestreamOthersVisible
    })
    const osdVisible = computed(() => {
      return store.state.osdVisible
    })
    const qualityStyle = computed(() => {
      if (deviceInfo.dock.basic_osd?.network_state?.type === NetworkStateTypeEnum.ETHERNET ||
        (deviceInfo.dock.basic_osd?.network_state?.quality || 0) > NetworkStateQualityEnum.FAIR) {
        return 'color: #00ee8b'
      }
      if ((deviceInfo.dock.basic_osd?.network_state?.quality || 0) === NetworkStateQualityEnum.FAIR) {
        return 'color: yellow'
      }
      return 'color: red'
    })
    const droneVisible = ref(false)
    const dockVisible = ref(false)

    watch(
      () => store.state.deviceStatusEvent.deviceOnline,
      (data) => {
        if (data && Object.keys(data).length !== 0) {
          deviceTsaUpdateHook.initMarker(data.domain, data.device_callsign, data.sn)
          store.commit('CLEAR_DEVICE_ONLINE')
        }
      }
    )

    watch(
      () => store.state.deviceStatusEvent.deviceOffline,
      (data: DeviceStatus) => {
        if (data && Object.keys(data).length !== 0) {
          deviceTsaUpdateHook.removeMarker(data.sn)
          if ((data.sn === osdVisible.value.sn) || (osdVisible.value.is_dock && data.sn === osdVisible.value.gateway_sn)) {
            store.commit('SET_OSD_VISIBLE_INFO', { ...osdVisible.value, visible: false })
          }
          store.commit('CLEAR_DEVICE_OFFLINE')
        }
      }
    )

    watch(
      () => store.state.deviceState.latestOsdUpdate,
      (update) => {
        const { sn, type } = update
        if (!sn) return
        const data = store.state.deviceState
        if (type === EDeviceTypeName.Gateway && data.gatewayInfo[sn]) {
          const coordinate = wgs84togcj02(data.gatewayInfo[sn].longitude, data.gatewayInfo[sn].latitude)
          deviceTsaUpdateHook.moveTo(sn, coordinate[0], coordinate[1])
          if (osdVisible.value.visible && osdVisible.value.gateway_sn === sn) {
            deviceInfo.gateway = data.gatewayInfo[sn]
          }
        }
        if (type === EDeviceTypeName.Aircraft && data.deviceInfo[sn]) {
          const coordinate = wgs84togcj02(data.deviceInfo[sn].longitude, data.deviceInfo[sn].latitude)
          deviceTsaUpdateHook.moveTo(sn, coordinate[0], coordinate[1])
          if (osdVisible.value.visible && osdVisible.value.sn === sn) {
            deviceInfo.device = data.deviceInfo[sn]
          }
        }
        if (type === EDeviceTypeName.Dock && data.dockInfo[sn]) {
          const coordinate = wgs84togcj02(data.dockInfo[sn].basic_osd?.longitude, data.dockInfo[sn].basic_osd?.latitude)
          deviceTsaUpdateHook.initMarker(EDeviceTypeName.Dock, EDeviceTypeName[EDeviceTypeName.Dock], sn, coordinate[0], coordinate[1])
          if (osdVisible.value.visible && osdVisible.value.is_dock && osdVisible.value.gateway_sn === sn) {
            deviceInfo.dock = data.dockInfo[sn]
            const subSn = deviceInfo.dock.basic_osd?.sub_device?.device_sn ?? osdVisible.value.sn
            if (subSn && data.deviceInfo[subSn]) {
              deviceInfo.device = data.deviceInfo[subSn]
            }
          }
        }
      }
    )

    watch(
      () => store.state.wsEvent.mapElementCreat,
      (ele :any) => {
        if (!ele || Object.keys(ele).length === 0) return
        const useGMapCoverHook = useGMapCover()
        let exist = false
        store.state.Layers.forEach(layer => {
          layer.elements.forEach( (e : any) => {
            if (e.id === ele.id) {
              exist = true
            }
          })
        })
        if (!exist) {
          setLayers({
            id: ele.id,
            name: ele.name,
            resource: ele.resource
          })

          updateCoordinates('wgs84-gcj02', ele)
          const data = { id: ele.id, name: ele.name }
          if (MapElementEnum.PIN === ele.resource?.type) {
            useGMapCoverHook.init2DPin(
              ele.name,
              ele.resource.content.geometry.coordinates,
              ele.resource.content.properties.color,
              data
            )
          } else if (MapElementEnum.LINE === ele.resource?.type) {
            useGMapCoverHook.initPolyline(
              ele.name,
              ele.resource.content.geometry.coordinates,
              ele.resource.content.properties.color,
              data
            )
          } else if (MapElementEnum.POLY === ele.resource?.type) {
            useGMapCoverHook.initPolygon(
              ele.name,
              ele.resource.content.geometry.coordinates,
              ele.resource.content.properties.color,
              data
            )
          }
        }
        store.commit('CLEAR_MAP_ELEMENT_CREATE')
      }
    )

    watch(
      () => store.state.wsEvent.mapElementUpdate,
      (updateEle) => {
        if (!updateEle || Object.keys(updateEle).length === 0) return
        console.log('该功能还未实现，请开发商自己增加')
        store.commit('CLEAR_MAP_ELEMENT_UPDATE')
      }
    )

    watch(
      () => store.state.wsEvent.mapElementDelete,
      (deleteEle) => {
        if (!deleteEle || Object.keys(deleteEle).length === 0) return
        console.log('该功能还未实现，请开发商自己增加')
        store.commit('CLEAR_MAP_ELEMENT_DELETE')
      }
    )



    // dock 控制面板
    const {
      dockControlPanelVisible,
      setDockControlPanelVisible,
      onCloseControlPanel,
    } = useDockControl()

    // 连接或断开drc
    useConnectMqtt()

    onMounted(() => {
      const app = getApp()
    })

    const { getDrawFlightAreaCallback, onFlightAreaDroneLocationWs } = useFlightArea()
    useFlightAreaDroneLocationEvent(onFlightAreaDroneLocationWs)

    function selectFlightAreaAction({ type, isCircle }: { type: EFlightAreaType, isCircle: boolean }) {
      draw(isCircle ? MapDoodleEnum.CIRCLE : MapDoodleEnum.POLYGON, true, type)
    }

    function getDrawCallback({ obj }: { obj: any }) {
      if (state.isFlightArea) {
        getDrawFlightAreaCallback(obj)
        return
      }
      switch (state.currentType) {
        case MapDoodleEnum.PIN:
          postPinPositionResource(obj)
          break
        case MapDoodleEnum.POLYLINE:
          postPolylineResource(obj)
          break
        case MapDoodleEnum.POLYGON:
          postPolygonResource(obj)
          break
        default:
          break
      }
    }

    async function postPinPositionResource(obj:any) {
      const req = getPinPositionResource(obj)
      setLayers(req)
      const coordinates = req.resource.content.geometry.coordinates
      updateCoordinates('gcj02-wgs84', req);
      (req.resource.content.geometry.coordinates as GeojsonCoordinate).push((coordinates as GeojsonCoordinate)[2])
      const result = await postElementsReq(shareId.value, req)
      obj.setExtData({ id: req.id, name: req.name })
      store.commit('SET_COVER_MAP_ITEM', { id: req.id, value: [obj] })
    }

    async function postPolylineResource(obj) {
      const req = getPolylineResource(obj)
      setLayers(req)
      updateCoordinates('gcj02-wgs84', req)
      const result = await postElementsReq(shareId.value, req)
      obj.setExtData({ id: req.id, name: req.name })
      store.commit('SET_COVER_MAP_ITEM', { id: req.id, value: [obj] })
    }

    async function postPolygonResource(obj) {
      const req = getPoygonResource(obj)
      setLayers(req)
      updateCoordinates('gcj02-wgs84', req)
      const result = await postElementsReq(shareId.value, req)
      obj.setExtData({ id: req.id, name: req.name })
      store.commit('SET_COVER_MAP_ITEM', { id: req.id, value: [obj] })
    }

    function getPinPositionResource(obj) {
      const position = obj.getPosition()
      const resource = generatePointContent(position)
      const name = obj._originOpts.title
      const id = uuidv4()
      return {
        id,
        name,
        resource
      }
    }

    function getPolylineResource(obj) {
      const path = obj.getPath()
      const resource = generateLineContent(path)
      const { name, id } = getBaseInfo(obj._opts)
      return {
        id,
        name,
        resource
      }
    }

    function getPoygonResource(obj: any) {
      const path = obj.getPath()
      const resource = generatePolyContent(path)
      const { name, id } = getBaseInfo(obj._opts)
      return {
        id,
        name,
        resource
      }
    }

    function getBaseInfo(obj:any) {
      const name = obj.title
      const id = uuidv4()
      return { name, id }
    }

    function setLayers(resource: PostElementsBody) {
      store.commit('ADD_LAYER_ELEMENT', {
        layerId: shareId.value,
        element: resource
      })
    }

    function closeLivestreamOthers() {
      store.commit('SET_LIVESTREAM_OTHERS_VISIBLE', false)
    }

    function updateCoordinates(transformType: string, element: any) {
      const geoType = element.resource?.content.geometry.type
      const type = element.resource?.type as number
      if (element.resource) {
        if (MapElementEnum.PIN === type) {
          const coordinates = element.resource?.content.geometry
            .coordinates as GeojsonCoordinate
          if (transformType === 'wgs84-gcj02') {
            const transResult = wgs84togcj02(
              coordinates[0],
              coordinates[1]
            ) as GeojsonCoordinate
            element.resource.content.geometry.coordinates = transResult
          } else if (transformType === 'gcj02-wgs84') {
            const transResult = gcj02towgs84(
              coordinates[0],
              coordinates[1]
            ) as GeojsonCoordinate
            element.resource.content.geometry.coordinates = transResult
          }
        } else if (MapElementEnum.LINE === type) {
          const coordinates = element.resource?.content.geometry
            .coordinates as GeojsonCoordinate[]
          if (transformType === 'wgs84-gcj02') {
            coordinates.forEach((coordinate, i, arr) => {
              arr[i] = wgs84togcj02(
                coordinate[0],
                coordinate[1]
              ) as GeojsonCoordinate
            })
          } else if (transformType === 'gcj02-wgs84') {
            coordinates.forEach((coordinate, i, arr) => {
              arr[i] = gcj02towgs84(
                coordinate[0],
                coordinate[1]
              ) as GeojsonCoordinate
            })
          }
          element.resource.content.geometry.coordinates = coordinates
        } else if (MapElementEnum.POLY === type) {
          const coordinates = element.resource?.content.geometry
            .coordinates[0] as GeojsonCoordinate[]
          if (transformType === 'wgs84-gcj02') {
            coordinates.forEach((coordinate, i, arr) => {
              arr[i] = wgs84togcj02(
                coordinate[0],
                coordinate[1]
              ) as GeojsonCoordinate
            })
          } else if (transformType === 'gcj02-wgs84') {
            coordinates.forEach((coordinate, i, arr) => {
              arr[i] = gcj02towgs84(
                coordinate[0],
                coordinate[1]
              ) as GeojsonCoordinate
            })
          }
          element.resource.content.geometry.coordinates = [coordinates]
        }
      }
    }

    const DockLiveVisible = ref(false)
    let dockId = ref('')
    let droneId = ref('')
    function DockLive() {
      DockLiveVisible.value = !DockLiveVisible.value
      dockId.value = osdVisible.value.gateway_sn + '/165-0-7/normal-0'
      dockVisible.value = !dockVisible.value
    }

    const plateLiveVisible = ref(false)
    const plateData = ref(null)

    const PlateLive = () => {

      plateLiveVisible.value = !plateLiveVisible.value
      let model: string = ''
      model = osdVisible.value.model
      droneId.value = osdVisible.value.sn + '/' + PAYLOAD_TYPE[model as keyof typeof PAYLOAD_TYPE] + '-0-0/normal-0'
      droneVisible.value = !droneVisible.value
    }
    // 打开虚拟座舱页面
    const virtualCockpit = () => {
      const isDock = osdVisible.value.is_dock
      const url = router.resolve({
        name: isDock ? 'virtualCockpit' : 'virtualCockpit1',
        query: {
          dockSn: osdVisible.value.gateway_sn,
          dockName: osdVisible.value.gateway_callsign,
          deviceSn: osdVisible.value.sn,
          clientId: store.state.clientId,
          model: osdVisible.value.model
        }
      }).href
      window.open(url, '_blank', 'noopener,noreferrer')
    }



</script>

<style lang="scss" scoped>
.g-map-wrapper {
  height: 100%;
  width: 100%;

  .g-action-panel {
    position: absolute;
    top: 16px;
    right: 16px;

    .g-action-item {
      width: 28px;
      height: 28px;
      background: white;
      color: #2b85e4;
      border-radius: 2px;
      line-height: 28px;
      text-align: center;
      margin-bottom: 2px;
    }

    .g-action-item:hover {
      border: 1px solid #2b85e4;
      border-radius: 2px;
    }
  }

  .selection {
    border: 1px solid #2b85e4;
    border-radius: 2px;
  }

  // antd button 光晕
  &:deep(.ant-btn) {
    &::after {
      display: none;
    }
  }
}

.osd-panel {
  position: absolute;
  right: 64px;
  top: 80px;
  width: 460px;
  background: rgba(4, 22, 58, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.3);
  color: #fff;
  border-radius: 12px;
  z-index: 50;
  transition: opacity 0.3s ease;
  font-family: system-ui, -apple-system, sans-serif;

  // Header style
  .osd-header {
    height: 44px;
    padding: 0 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: rgba(0, 0, 0, 0.2);
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    font-size: 14px;
    font-weight: 600;
    letter-spacing: 0.5px;
    cursor: move;
  }

  // Body container (Flexbox)
  .osd-body {
    display: flex;
    position: relative;
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);

    &:last-child {
      border-bottom: none;
    }
  }

  // Left Icon Box
  .osd-icon-box {
    width: 72px;
    background: rgba(0, 0, 0, 0.15);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    border-right: 1px solid rgba(255, 255, 255, 0.1);
    padding: 12px 0;
  }

  // Right Info Grid
  .osd-info-grid {
    flex: 1;
    padding: 12px 16px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 8px;

    .osd-item {
      display: inline-flex;
      align-items: center;
      white-space: nowrap;
      font-size: 12px;
      color: #ffffff;

      .osd-label {
        color: #ffffff;
        font-size: 12px;
        font-weight: normal;
        margin-right: 6px;
        display: inline-flex;
        align-items: center;
        gap: 3px;
      }

      .osd-value {
        color: #ffffff;
        font-size: 12px;
        font-weight: normal;
      }
    }
  }
}

.osd>div:not(.dock-control-panel) {
  margin-top: 5px;
  padding-left: 5px;
}

.circle {
  border-radius: 50%;
  width: 10px;
  height: 10px;
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

.liveview {
  position: absolute;
  color: #fff;
  z-index: 40; // Increased to be visible but maybe below OSD (50)?
  left: 16px;
  top: 80px;
  text-align: center;
  width: 800px;
  height: 720px;

  // Pro-Max Glassmorphism
  background: rgba(4, 22, 58, 0.85); // Slightly more opaque for video content background
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  box-shadow: 0 12px 48px 0 rgba(0, 0, 0, 0.4);
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.3s ease;
}
</style>
