import request, { IWorkspaceResponse } from '@/api/http/request'
import { CameraType, CameraMode } from '@/types/live-stream'
import { GimbalResetMode } from '@/types/drone-control'
import { payloadCmdResponseTime } from '@/api/http/cmd-responseTime'
// import { ELocalStorageKey } from '@/types'

const API_PREFIX = '/control/api/v1'
// const workspaceId: string = localStorage.getItem(ELocalStorageKey.WorkspaceId) || '

export interface PostPayloadAuthBody {
  payload_index: string
}

// 获取负载控制权
export async function postPayloadAuth(sn: string, body: PostPayloadAuthBody): Promise<IWorkspaceResponse<null>> {
  const resp = await request.post(`${API_PREFIX}/devices/${sn}/authority/payload`, body)
  return resp.data
}

// TODO: 画面拖动控制
export enum PayloadCommandsEnum {
  CameraModeSwitch = 'camera_mode_switch',
  CameraPhotoTake = 'camera_photo_take',
  CameraRecordingStart = 'camera_recording_start',
  CameraRecordingStop = 'camera_recording_stop',
  CameraFocalLengthSet = 'camera_focal_length_set',
  GimbalReset = 'gimbal_reset',
  CameraAim = 'camera_aim',
  CameraFrameZoom = 'camera_frame_zoom',
  CameraScreenDrag = 'camera_screen_drag',
  IrMeteringModeSet = 'ir_metering_mode_set',
  IrMeteringPointSet = 'ir_metering_point_set',
  IrMeteringAreaSet = 'ir_metering_area_set',
}

export interface PostCameraModeBody {
  payload_index: string
  camera_mode: CameraMode
}

export interface PostCameraPhotoBody {
  payload_index: string
}

export interface PostCameraRecordingBody {
  payload_index: string
}

export interface DeleteCameraRecordingParams {
  payload_index: string
}

export interface PostCameraFocalLengthBody {
  payload_index: string,
  camera_type: CameraType,
  zoom_factor: number
}

export interface PostGimbalResetBody {
  payload_index: string,
  reset_mode: GimbalResetMode,
}

export interface PostCameraAimBody {
  payload_index: string,
  camera_type: CameraType,
  locked: boolean,
  x: number,
  y: number,
}

// 负载控制—画面拖动控制
export interface PostCameraScreenDrag {
  payload_index: string,
  locked: boolean,
  pitch_speed: number,
  yaw_speed: number,
}
export interface PostCameraFrameZoomBody {
  payload_index: string,
  camera_type: CameraType,
  locked: boolean,
  x: number,
  y: number,
  width: number,
  height: number,
}

// 红外测温模式: 0=关闭测温, 1=点测温, 2=区域测温
export enum IrMeteringMode {
  Off = 0,
  Point = 1,
  Area = 2,
}

export interface PostIrMeteringModeBody {
  payload_index: string,
  mode: IrMeteringMode,
}

export interface PostIrMeteringPointBody {
  payload_index: string,
  x: number,  // 0~1, 以镜头左上角为坐标中心点，水平方向
  y: number,  // 0~1, 以镜头左上角为坐标中心点，竖直方向
}

export interface PostIrMeteringAreaBody {
  payload_index: string,
  x: number,      // 0~1, 测温区域左上角 x
  y: number,      // 0~1, 测温区域左上角 y
  width: number,  // 0~1, 测温区域宽度
  height: number, // 0~1, 测温区域高度
}


export type PostPayloadCommandsBody = {
  cmd: PayloadCommandsEnum.CameraModeSwitch,
  data: PostCameraModeBody
} | {
  cmd: PayloadCommandsEnum.CameraPhotoTake,
  data: PostCameraPhotoBody
} | {
  cmd: PayloadCommandsEnum.CameraRecordingStart,
  data: PostCameraRecordingBody
} | {
  cmd: PayloadCommandsEnum.CameraRecordingStop,
  data: DeleteCameraRecordingParams
} | {
  cmd: PayloadCommandsEnum.CameraFocalLengthSet,
  data: PostCameraFocalLengthBody
} | {
  cmd: PayloadCommandsEnum.GimbalReset,
  data: PostGimbalResetBody
} | {
  cmd: PayloadCommandsEnum.CameraAim,
  data: PostCameraAimBody
} | {
  cmd: PayloadCommandsEnum.CameraFrameZoom,
  data: PostCameraFrameZoomBody
} | {
  cmd: PayloadCommandsEnum.CameraScreenDrag,
  data: PostCameraScreenDrag
} | {
  cmd: PayloadCommandsEnum.IrMeteringModeSet,
  data: PostIrMeteringModeBody
} | {
  cmd: PayloadCommandsEnum.IrMeteringPointSet,
  data: PostIrMeteringPointBody
} | {
  cmd: PayloadCommandsEnum.IrMeteringAreaSet,
  data: PostIrMeteringAreaBody
}


// 发送负载名称
export async function postPayloadCommands(sn: string, body: PostPayloadCommandsBody): Promise<IWorkspaceResponse<any>> {
  const resp = await request.post(`${API_PREFIX}/devices/${sn}/payload/commands`, body)
  if (resp.data && resp.data.data && typeof resp.data.data.time === 'number') {
    payloadCmdResponseTime.value = resp.data.data.time
  }
  return resp.data
}
