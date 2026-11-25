import { ref } from 'vue'
import { postFlyToPoint, PostFlyToPointBody, deleteFlyToPoint, postTakeoffToPoint, PostTakeoffToPointBody } from '@/api/drone-control/drone'
import { message } from 'ant-design-vue'

export function useDroneControl() {
  const droneControlPanelVisible = ref(false)

  function setDroneControlPanelVisible(visible: boolean) {
    droneControlPanelVisible.value = visible
  }

  async function flyToPoint(sn: string, body: PostFlyToPointBody) {
    const { code } = await postFlyToPoint(sn, body)
    if (code === 0) {
      message.success('指点飞行')
    }
  }

  async function stopFlyToPoint(sn: string) {
    const { code } = await deleteFlyToPoint(sn)
    if (code === 0) {
      message.success('停止指点飞行')
    }
  }

  async function takeoffToPoint(sn: string, body: PostTakeoffToPointBody) {
    const { code } = await postTakeoffToPoint(sn, body)
    if (code === 0) {
      message.success('一键起飞成功')
    }
  }

  return {
    droneControlPanelVisible,
    setDroneControlPanelVisible,
    flyToPoint,
    stopFlyToPoint,
    takeoffToPoint
  }
}
