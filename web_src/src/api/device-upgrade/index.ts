import request, { IWorkspaceResponse } from '@/api/http/request'
import { DeviceFirmwareTypeEnum } from '@/types/device'

const MNG_API_PREFIX = '/manage/api/v1'

export interface GetDeviceUpgradeInfoParams {
  device_name: string
}

export interface GetDeviceUpgradeInfoRsp {
  device_name: string
  product_version: string
  release_note: string
  released_time: string
}

/**
 * 获取设备升级信息
 * @param params
 * @returns
 */
export async function getDeviceUpgradeInfo(params: GetDeviceUpgradeInfoParams): Promise<IWorkspaceResponse<GetDeviceUpgradeInfoRsp[]>> {
  const resp = await request.get(`${MNG_API_PREFIX}/workspaces/firmware-release-notes/latest`, {
    params: params
  })
  return resp.data
}

export interface UpgradeDeviceInfo {
  device_name: string,
  sn: string,
  product_version: string,
  firmware_upgrade_type: DeviceFirmwareTypeEnum | number // 2-一致性升级(CONSISTENT_UPGRADE), 3-普通升级(NORMAL_UPGRADE), 4-PSDK升级(PSDK_UPGRADE)
}

export type DeviceUpgradeBody = UpgradeDeviceInfo[]

/**
 * 设备升级
 * @param workspace_id
 * @param body
 * @returns
 */
export async function postDeviceUpgrade(workspace_id: string, body: DeviceUpgradeBody): Promise<IWorkspaceResponse<{}>> {
  const formattedBody = body.map(item => ({
    ...item,
    firmware_upgrade_type: item.firmware_upgrade_type ?? DeviceFirmwareTypeEnum.NORMAL_UPGRADE
  }))
  const resp = await request.post(`${MNG_API_PREFIX}/devices/${workspace_id}/devices/ota`, formattedBody)
  return resp.data
}
