import { message } from 'ant-design-vue'
import {
  PayloadCommandsEnum,
  PostCameraAimBody,
  PostCameraFocalLengthBody,
  PostCameraFrameZoomBody,
  PostCameraModeBody, PostCameraScreenDrag,
  PostGimbalResetBody,
  PostIrMeteringModeBody,
  PostIrMeteringPointBody,
  PostIrMeteringAreaBody,
  postPayloadAuth,
  postPayloadCommands,
} from '@/api/drone-control/payload'
import { ControlSource } from '@/types/device'

export function usePayloadControl() {
  function checkPayloadAuth(controlSource?: ControlSource) {
    if (controlSource !== ControlSource.A) {
      message.error('Get Payload Control first')
      return false
    }
    return true
  }

  async function authPayload(sn: string, payloadIndx: string) {
    const { code } = await postPayloadAuth(sn, {
      payload_index: payloadIndx
    })
    if (code === 0) {
      message.success('获取负载控制权成功')
      return true
    }
    return false
  }

  async function resetGimbal(sn: string, data: PostGimbalResetBody) {
    const { code } = await postPayloadCommands(sn, {
      cmd: PayloadCommandsEnum.GimbalReset,
      data: data
    })
    if (code === 0) {
      // message.success('Gimbal Reset successfully')
    }
  }

  async function switchCameraMode(sn: string, data: PostCameraModeBody) {
    const { code } = await postPayloadCommands(sn, {
      cmd: PayloadCommandsEnum.CameraModeSwitch,
      data: data
    })
    if (code === 0) {
      message.success('相机模式切换成功')
    }
  }

  async function takeCameraPhoto(sn: string, payloadIndx: string) {
    const { code } = await postPayloadCommands(sn, {
      cmd: PayloadCommandsEnum.CameraPhotoTake,
      data: {
        payload_index: payloadIndx
      }
    })
    if (code === 0) {
      message.success('拍照成功')
    }
  }

  async function startCameraRecording(sn: string, payloadIndx: string) {
    const { code } = await postPayloadCommands(sn, {
      cmd: PayloadCommandsEnum.CameraRecordingStart,
      data: {
        payload_index: payloadIndx
      }
    })
    if (code === 0) {
      message.success('开始录像成功')
    }
  }

  async function stopCameraRecording(sn: string, payloadIndx: string) {
    const { code } = await postPayloadCommands(sn, {
      cmd: PayloadCommandsEnum.CameraRecordingStop,
      data: {
        payload_index: payloadIndx
      }
    })
    if (code === 0) {
      message.success('结束录像成功')
    }
  }

  async function changeCameraFocalLength(sn: string, data: PostCameraFocalLengthBody) {
    const { code } = await postPayloadCommands(sn, {
      cmd: PayloadCommandsEnum.CameraFocalLengthSet,
      data: data,
    })
    if (code === 0) {
      // message.success('Zoom successfully')
    }
  }

  async function cameraAim(sn: string, data: PostCameraAimBody) {
    const { code } = await postPayloadCommands(sn, {
      cmd: PayloadCommandsEnum.CameraAim,
      data: data,
    })
    if (code === 0) {
      // message.success('Zoom Aim successfully')
    }
  }
  async function cameraScreenDrag(sn: string, data: PostCameraScreenDrag) {
    const { code } = await postPayloadCommands(sn, {
      cmd: PayloadCommandsEnum.CameraScreenDrag,
      data: data,
    })
    if (code === 0) {
      // message.success('cameraScreenDrag successfully')
    }
  }
  async function cameraFrameZoom(sn: string, data: PostCameraFrameZoomBody) {
    const { code } = await postPayloadCommands(sn, {
      cmd: PayloadCommandsEnum.CameraFrameZoom,
      data: data
    })

    if (code === 0) {
      message.success('缩放成功')
    }
  }

  // ---- 红外测温控制 ----
  async function setIrMeteringMode(sn: string, data: PostIrMeteringModeBody) {
    const { code } = await postPayloadCommands(sn, {
      cmd: PayloadCommandsEnum.IrMeteringModeSet,
      data: data,
    })
    if (code === 0) {
      const modeNames = ['关闭测温', '点测温', '区域测温']
      message.success(`测温模式切换: ${modeNames[data.mode] || data.mode}`)
    }
  }

  async function setIrMeteringPoint(sn: string, data: PostIrMeteringPointBody) {
    const { code } = await postPayloadCommands(sn, {
      cmd: PayloadCommandsEnum.IrMeteringPointSet,
      data: data,
    })
    if (code === 0) {
      // message.success('测温点设置成功')
    }
  }

  async function setIrMeteringArea(sn: string, data: PostIrMeteringAreaBody) {
    const { code } = await postPayloadCommands(sn, {
      cmd: PayloadCommandsEnum.IrMeteringAreaSet,
      data: data,
    })
    if (code === 0) {
      // message.success('测温区域设置成功')
    }
  }

  return {
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
    setIrMeteringMode,
    setIrMeteringPoint,
    setIrMeteringArea,
  }
}
