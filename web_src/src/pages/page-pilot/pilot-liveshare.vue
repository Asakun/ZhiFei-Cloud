<template>
    <div class="width100 flex-column flex-justify-start flex-align-start" style="background-color: white;">

      <p class="fz16 ml10 mt15 mb10 color-text-title color-font-bold" style="color: #939393">
        在手动启动之前，请先选择视频推流模式和直播类型
      </p>
    <div
      class="mt15 flex-row flex-align-center flex-justify-between"
      style="width: 100%;">
      <p class="ml10 mb0 fz16" style="color: black">
        选择视频推流模式：
      </p>
      <a-select
        style="width: 200px; margin-right: 20px;"
        placeholder="请选择推流模式"
        @select="onPublishModeSelect"
      >
        <a-select-option
          v-for="item in publishModeList"
          :key="item.label"
          :value="item.value"
        >
          {{ item.label }}
        </a-select-option>
      </a-select>
    </div>

    <div class="ml10 mr10" style="width: 96%; margin-top: -10px;">
      <a-divider />
    </div>
    <div
      class="flex-row flex-align-center flex-justify-between"
      style="width: 100%; margin-top: -10px;"
    >
      <p class="ml10 mb0 fz16">选择直播类型：</p>
      <a-select
        style="width: 200px; margin-right: 20px;"
        placeholder="请选择直播类型"
        :value="liveStreamStatus.type"
        @select="onLiveTypeSelect"
      >
        <a-select-option
          v-for="item in liveTypeList"
          :key="item.label"
          :value="item.value"
        >
          {{ item.label }}
        </a-select-option>
      </a-select>
    </div>
    <div class="ml10 mr10" style="width: 96%; margin-top: -10px;">
      <a-divider />
    </div>
    <div class="width-100" style="margin-top: -10px;">
      <div class="ml10" style="width: 97%;">
        <span class="fz16">推流参数：</span>
        <span v-if="liveStreamStatus.type === ELiveTypeValue.Agora" style="word-break: break-all; color: #75c5f6;">
          <div class="flex-col flex-justify-center flex-align-center">
            <div>
              <span class="ml10">Token:</span>
              <a-input
                class="ml10"
                v-model:value="agoraParam.token"
                placeholder="请输入 Token"
              ></a-input>
            </div>
            <div>
              <span class="ml10">通道 ID:</span>
              <a-input
                class="ml10"
                v-model:value="agoraParam.channelId"
                placeholder="请输入通道 ID"
              ></a-input>
            </div>
          </div>
        </span>
        <span v-else-if="liveStreamStatus.type === ELiveTypeValue.RTMP" style="word-break: break-all; color: #75c5f6;">{{ rtmpParam }}</span>
        <span v-else-if="liveStreamStatus.type === ELiveTypeValue.RTSP" style="word-break: break-all; color: #75c5f6;">{{ rtspParam }}</span>
        <span v-else-if="liveStreamStatus.type === ELiveTypeValue.GB28181" style="word-break: break-all; color: #75c5f6;">{{ gb28181Param }}</span>
        <span v-else-if="liveStreamStatus.type === ELiveTypeValue.WEBRTC" style="word-break: break-all; color: #75c5f6;">{{ webrtcParam }}</span>
        <span v-else></span>
      </div>

    </div>
    <div class="ml10 mr10" style="width: 96%; margin-top: -10px;">
      <a-divider />
    </div>
    <div class="mb20 flex-row flex-align-center flex-justify-center"
      style="width: 100%; ">
      <a-button class="flex-column fz20 flex-align-center flex-justify-center" style="width: 100px;" type="ghost" @click="onPlay">开始直播</a-button>
      <a-button class="flex-column fz20 flex-align-center flex-justify-center ml40" style="width: 100px;" type="ghost" @click="onStop">停止直播</a-button>
    </div>
    <a-button v-if="playVisiable" class="flex-column flex-align-center" shape="circle" @click="showLivingStatus"
      style="position: fixed; top: 13vh; left: 5vw; opacity: 0.8; background-color: rgb(0,0,0,0)">
      <template #icon><CaretRightFilled style="font-size: 26px; color: " /></template>
    </a-button>

    <a-drawer  placement="right" v-model:visible="drawerVisible" width="280px" :mask="false" @close="closeDrawer">
      <div class="fz16 width-100">
        <div class="mt20" style=" margin-bottom: -10px;">
          <span class="fz20 flex-row flex-align-center flex-justify-center">
            <font :color="liveState === EStatusValue.LIVING ? 'green' : liveState === EStatusValue.CONNECTED ? 'blue' : 'red'">
              {{ liveState === EStatusValue.LIVING ? '直播中' : liveState === EStatusValue.CONNECTED ? '已连接' : '已断开' }}
            </font></span>
        </div>
        <a-divider />
        <div style=" margin-top: -10px; margin-bottom: -15px;">
          <span>帧率：</span><span style="float: right; color: #75c5f6;">{{ liveStreamStatus.fps }}<span v-if="liveStreamStatus.fps != -1"> fps</span></span><br/>
        </div>
        <a-divider />
        <div style=" margin-top: -10px; margin-bottom: -10px;">
          <span>视频码率：</span><span style="float: right; color: #75c5f6;">{{ liveStreamStatus.videoBitRate }}<span v-if="liveStreamStatus.videoBitRate != -1"> kbps</span></span><br/>
        </div>
        <a-divider />
        <div style=" margin-top: -10px; margin-bottom: -10px;">
          <span>音频码率：</span><span style="float: right; color: #75c5f6;">{{ liveStreamStatus.audioBitRate }}<span v-if="liveStreamStatus.audioBitRate != -1"> kbps</span></span><br/>
        </div>
        <a-divider />
        <div style=" margin-top: -10px; margin-bottom: -10px;">
          <span>丢包率：</span><span style="float: right; color: #75c5f6;">{{ liveStreamStatus.dropRate }}<span v-if="liveStreamStatus.dropRate != -1"> %</span></span><br/>
        </div>
        <a-divider />
        <div style=" margin-top: -10px; margin-bottom: -10px;">
          <span>往返时延 (RTT)：</span><span style="float: right; color: #75c5f6;">{{ liveStreamStatus.rtt }}<span v-if="liveStreamStatus.rtt != -1"> ms</span></span><br/>
        </div>
        <a-divider />
        <div style=" margin-top: -10px;">
          <span>抖动：</span><span style="float: right; color: #75c5f6;">{{ liveStreamStatus.jitter }}</span><br/>
        </div>
      </div>
    </a-drawer>
  </div>
</template>

<script lang="ts" setup>
import { message } from 'ant-design-vue'
import { onMounted, reactive, ref, UnwrapRef } from 'vue'
import { CURRENT_CONFIG as config, CURRENT_CONFIG } from '@/api/http/config'
import { ELiveTypeName, ELiveTypeValue, GB28181Param, LiveConfigParam, LiveStreamStatus, RTSPParam, EVideoPublishType } from '@/types/live-stream'
import apiPilot from '@/api/pilot-bridge'
import { getRoot } from '@/root'
import { ELiveStatusValue, EStatusValue } from '@/types'
import { CaretRightFilled } from '@ant-design/icons-vue'

const root = getRoot()

const publishModeList = [
  {
    value: EVideoPublishType.VideoOnDemand,
    label: '自主/按需推流 (VideoOnDemand)'
  },
  {
    value: EVideoPublishType.VideoByManual,
    label: '手动推流 (VideoByManual)'
  },
  {
    value: EVideoPublishType.VideoDemandAuxManual,
    label: '自主兼手动推流 (VideoDemandAuxManual)'
  }
]
const liveTypeList = [
  {
    value: ELiveTypeValue.Agora,
    label: '声网 (Agora)'
  },
  {
    value: ELiveTypeValue.RTMP,
    label: 'RTMP 推流'
  },
  {
    value: ELiveTypeValue.RTSP,
    label: 'RTSP 推流'
  },
  {
    value: ELiveTypeValue.GB28181,
    label: '国标 (GB28181)'
  },
  {
    value: ELiveTypeValue.WEBRTC,
    label: 'WebRTC (WHIP)'
  }
]
const agoraParam = reactive({
  uid: '2892130292',
  token: config.agoraToken,
  channelId: config.agoraChannel
})
const rtmpParam = {
  url: config.rtmpURL + new Date().getTime()
}
const rtspParam: RTSPParam = {
  userName: CURRENT_CONFIG.rtspUserName,
  password: CURRENT_CONFIG.rtspPassword,
  port: CURRENT_CONFIG.rtspPort
}
const gb28181Param: GB28181Param = {
  serverIp: CURRENT_CONFIG.gbServerIp,
  serverPort: CURRENT_CONFIG.gbServerPort,
  serverId: CURRENT_CONFIG.gbServerId,
  agentId: CURRENT_CONFIG.gbAgentId,
  password: CURRENT_CONFIG.gbPassword,
  agentPort: CURRENT_CONFIG.gbAgentPort,
  agentChannel: CURRENT_CONFIG.gbAgentChannel,
}
const webrtcParam = {
  url: CURRENT_CONFIG.webRtc || 'https://120.24.231.12:8001/index/api/whip?app=live&stream=test'
}

const playVisiable = ref(false)
const drawerVisible = ref(false)
const liveState = ref(EStatusValue.DISCONNECT)
const liveTypeSelected = ref<string>()
const publishModeSelected = ref<string>()
const liveStreamStatus: LiveStreamStatus = reactive({
  audioBitRate: -1,
  dropRate: -1,
  fps: -1,
  jitter: -1,
  quality: -1,
  rtt: -1,
  status: -1,
  type: -1,
  videoBitRate: -1
})

onMounted(() => {
  const config: LiveConfigParam = JSON.parse(apiPilot.getLiveshareConfig())
  liveStreamStatus.type = config.type
  refreshLiveType()

  window.liveStatusCallback = arg => {
    liveStatusCallback(arg)
  }
})

const liveStatusCallback = async (arg: LiveStreamStatus) => {
  liveStreamStatus.fps = arg.fps
  liveStreamStatus.audioBitRate = arg.audioBitRate
  liveStreamStatus.dropRate = arg.dropRate
  liveStreamStatus.jitter = arg.jitter
  liveStreamStatus.rtt = arg.rtt
  liveStreamStatus.videoBitRate = arg.videoBitRate
  liveStreamStatus.quality = arg.quality
  liveStreamStatus.type = arg.type
  liveStreamStatus.status = arg.status

  switch (liveStreamStatus.status) {
    case ELiveStatusValue.LIVING:
      liveState.value = EStatusValue.LIVING
      break
    case ELiveStatusValue.CONNECTED:
      liveState.value = EStatusValue.CONNECTED
      break
    default:
      liveState.value = EStatusValue.DISCONNECT
  }
}
function refreshLiveType () {
  switch (liveStreamStatus.type) {
    case ELiveTypeValue.Agora:
      liveTypeSelected.value = ELiveTypeName.Agora
      break
    case ELiveTypeValue.RTMP:
      liveTypeSelected.value = ELiveTypeName.RTMP
      break
    case ELiveTypeValue.RTSP:
      liveTypeSelected.value = ELiveTypeName.RTSP
      break
    case ELiveTypeValue.GB28181:
      liveTypeSelected.value = ELiveTypeName.GB28181
      break
    case ELiveTypeValue.WEBRTC:
      liveTypeSelected.value = ELiveTypeName.WEBRTC
      break
    default:
      liveTypeSelected.value = ELiveTypeName.Unknown
  }
}
const onLiveTypeSelect = (val: number) => {
  liveStreamStatus.type = val
  refreshLiveType()
}
const onPublishModeSelect = (val: string) => {
  publishModeSelected.value = val
  apiPilot.setVideoPublishType(publishModeSelected.value)
}
const onPlay = () => {
  console.info(JSON.stringify(agoraParam))
  if (!publishModeSelected.value) {
    message.warn('请选择视频推流模式！')
    return
  }
  if (liveTypeSelected.value === ELiveTypeName.Unknown) {
    message.warn('请选择直播类型！')
    return
  }
  switch (liveStreamStatus.type) {
    case ELiveTypeValue.Agora: {
      apiPilot.setLiveshareConfig(ELiveTypeValue.Agora, JSON.stringify(agoraParam))
      break
    }
    case ELiveTypeValue.RTMP: {
      const deviceSn = apiPilot.getAircraftSN()
      if (deviceSn) {
        rtmpParam.url = config.rtmpURL + deviceSn
        console.log('📌 成功获取无人机 SN, 动态生成推流地址:', rtmpParam.url)
      } else {
        rtmpParam.url = config.rtmpURL + new Date().getTime()
        console.warn('⚠️ 未能获取到无人机 SN, 降级使用时间戳生成推流地址:', rtmpParam.url)
      }
      apiPilot.setLiveshareConfig(ELiveTypeValue.RTMP, JSON.stringify(rtmpParam))
      break
    }
    case ELiveTypeValue.RTSP: {
      apiPilot.setLiveshareConfig(ELiveTypeValue.RTSP, JSON.stringify(rtspParam))
      break
    }
    case ELiveTypeValue.GB28181: {
      apiPilot.setLiveshareConfig(ELiveTypeValue.GB28181, JSON.stringify(gb28181Param))
      break
    }
    case ELiveTypeValue.WEBRTC: {
      apiPilot.setLiveshareConfig(ELiveTypeValue.WEBRTC, JSON.stringify(webrtcParam))
      break
    }
  }
  const status = apiPilot.startLiveshare()
  if (status) {
    playVisiable.value = true
    drawerVisible.value = true
    message.success('开启直播成功！')
  }
}

const showLivingStatus = () => {
  drawerVisible.value = !drawerVisible.value
}

const onStop = () => {
  const status = apiPilot.stopLiveshare()
  if (status) {
    message.success('停止直播成功！')
    playVisiable.value = false
    drawerVisible.value = false
    setTimeout(() => {
      let key: (keyof LiveStreamStatus)
      for (key in liveStreamStatus) {
        if (key === 'type') {
          continue
        }
        liveStreamStatus[key] = -1
      }
    }, 2000)
  }
}
</script>

<style lang="scss" scoped>
 @use '@/styles/index.scss';
</style>
