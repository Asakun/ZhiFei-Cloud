<script setup lang="ts">

import {onBeforeUnmount, ref, watch} from "vue";
import {startLivestream, stopLivestream} from "@/api/manage";
import srs from '@/vendors/srs1.sdk.js'
import {message} from "ant-design-vue";

const props = defineProps({
  droneId: String,
  dockId: String,
})

const videoWebRtc = ref(null)
let webrtc: any = null

const startPlay = async (playId: string) => {
  await startLivestream({
    url: '',
    video_id: playId,
    url_type: 4,
    video_quality: 4
  }).then(res => {
    if (res.code !== 0 && res.code !== 513003) {
      return
    }
    const videoElement = videoWebRtc.value as unknown as HTMLMediaElement
    // videoElement.muted = true
    playWebrtc(videoElement, res.data.url)
  }).catch(err => {
    // console.error(err)
  })
}

const playWebrtc = (videoElement: HTMLMediaElement, url: string) => {
  if (webrtc) {
    webrtc.close()
  }
  webrtc = new srs.SrsRtcWhipWhepAsync()
  videoElement.srcObject = webrtc.stream
  webrtc.play(url).then(function (session: any) {
    console.info(session)
  }).catch(function (reason: any) {
    webrtc.close()
    console.error(reason)
  })
}

const stopWebrtc = () => {
  if (webrtc) {
    try {
      webrtc.close()
    } catch (e) {
      console.error('停止WebRTC连接失败:', e)
    }
    webrtc = null
  }
  const videoElement = videoWebRtc.value as unknown as HTMLMediaElement
  if (videoElement) {
    videoElement.srcObject = null
  }
}

watch(()=> props.dockId, async (newId, oldId) => {
  if (newId === oldId) return

  if (oldId) {
    stopWebrtc()
    await stopLivestream({
      video_id: oldId,
    }).catch(err => console.error('停止旧机场直播推流失败:', err))
  }

  if (newId != undefined) {
    await startPlay(newId)
  }
},{
  immediate: true,
})

watch(()=> props.droneId, async (newId, oldId) => {
  if (newId === oldId) return

  if (oldId) {
    stopWebrtc()
  }

  if (newId != undefined) {
    await startPlay(newId)
  }
},{
  immediate: true,
})
onBeforeUnmount(async () => {
  stopWebrtc()
  if (props.dockId) {
    await stopLivestream({
      video_id: props.dockId,
    }).then(res => {
      if (res.code === 0) {
        console.log('机场推流关闭成功')
      }
    })
  }
})
</script>

<template>
  <video
      id="video-webrtc"
      ref="videoWebRtc"
      :style="{ width: '100%', height: '100%' }"
      autoplay
      controls
  ></video>
</template>

<style scoped lang="scss">

</style>
