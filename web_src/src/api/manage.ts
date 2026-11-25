import { Firmware, FirmwareQueryParam, FirmwareUploadParam } from '@/types/device-firmware'
import request, { CommonListResponse, IListWorkspaceResponse, IPage, IWorkspaceResponse } from '@/api/http/request'
import { Device } from '@/types/device'
import { ELocalStorageKey } from '@/types/enums'
import axios from 'axios'
import { CURRENT_CONFIG } from '@/api/http/config'

const HTTP_PREFIX = '/manage/api/v1'
const MEDIA_HTTP_PREFIX = '/media/api/v1'

// login
export interface LoginBody {
  username: string,
  password: string,
  flag: number,
  captcha_code: string,
  captcha_key: string,
}
export interface BindBody {
  device_sn: string,
  user_id: string,
  workspace_id: string,
  domain?: string
}
export interface HmsQueryBody {
  device_sn: string,
  language: string,
  level: number | string,
  begin_time: number,
  end_time: number,
  message: string,
}


// 注册用户接口
export interface RegisterBody {
  username: string,
  password: string,
  flag: number,
  remark?: string
}
export const registerUser = async function (body: RegisterBody): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/users/register`
  const result = await request.post(url, body)
  return result.data
}

// 获取登录RSA公钥接口
export const getLoginPublicKey = async function (): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/rsa/public-key`
  const result = await request.get(url)
  return result.data
}
// 获取登录验证码接口
export const getLoginCaptcha = async function (): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/captcha`
  const result = await request.get(url)
  return result.data
}

// 重置密码
export const resetPassword = async function (body: {}): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/users/reset-password`
  const result = await request.post(url, body)
  return result.data
}

// 登录
export const login = async function (body: LoginBody): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/login`
  const result = await request.post(url, body)
  return result.data
}

// Refresh Token
export const refreshToken = async function (body: {}): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/token/refresh`
  const result = await request.post(url, body)
  return result.data
}

// Get Platform Info
export const getPlatformInfo = async function (): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/current`
  const result = await request.get(url)
  return result.data
}

// Get User Info
export const getUserInfo = async function (): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/users/current`
  const result = await request.get(url)
  return result.data
}

// Get Device Topo
export const getDeviceTopo = async function (workspace_id: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/devices/${workspace_id}/devices`
  const result = await request.get(url)
  return result.data
}

// Get Livestream Capacity
export const getLiveCapacity = async function (body: {}): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/live/capacity`
  const result = await request.get(url, body)
  return result.data
}

// Start Livestream
export const startLivestream = async function (body: {}): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/live/streams/start`
  const result = await request.post(url, body)
  return result.data
}

// Stop Livestream
export const stopLivestream = async function (body: {}): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/live/streams/stop`
  const result = await request.post(url, body)
  return result.data
}
// Update Quality
export const setLivestreamQuality = async function (body: {}): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/live/streams/update`
  const result = await request.post(url, body)
  return result.data
}

export const getAllUsersInfo = async function (wid: string, body: IPage): Promise<CommonListResponse<any>> {
  const url = `${HTTP_PREFIX}/users/${wid}/users?&page=${body.page}&page_size=${body.page_size}`
  const result = await request.get(url)
  return result.data
}

export const updateUserInfo = async function (wid: string, user_id: string, body: {}): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/users/${wid}/users/${user_id}`
  const result = await request.put(url, body)
  return result.data
}

// 更新用户的状态
export const upateUserStatus = async function (user_id: string, status: number): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/users/${user_id}/status?status=${status}`
  const result = await request.put(url)
  return result.data
}

// 管理员初始化用户密码
export const initUserPassword = async function (user_id: string, passwordString: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/users/init-password/${user_id}`
  const result = await request.post(url, passwordString, {
    headers: {
      'Content-Type': 'text/plain'
    }
  })
  return result.data
}

export const bindDevice = async function (body: BindBody): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/devices/${body.device_sn}/binding`
  const result = await request.post(url, body)
  return result.data
}

export const unbindDevice = async function (device_sn: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/devices/${device_sn}/unbinding`
  const result = await request.delete(url)
  return result.data
}

export const getDeviceBySn = async function (workspace_id: string, device_sn: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/devices/${workspace_id}/devices/${device_sn}`
  const result = await request.get(url)
  return result.data
}

/**
 * 获取绑定设备信息
 * @param workspace_id
 * @param body
 * @param domain
 * @returns
 */
export const getBindingDevices = async function (workspace_id: string, body: IPage, domain: number): Promise<IListWorkspaceResponse<Device>> {
  const url = `${HTTP_PREFIX}/devices/${workspace_id}/devices/bound?&page=${body.page}&page_size=${body.page_size}&domain=${domain}`
  const result = await request.get(url)
  return result.data
}

export const updateDevice = async function (body: {}, workspace_id: string, device_sn: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/devices/${workspace_id}/devices/${device_sn}`
  const result = await request.put(url, body)
  return result.data
}

export const getUnreadDeviceHms = async function (workspace_id: string, device_sn: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/devices/${workspace_id}/devices/hms/${device_sn}`
  const result = await request.get(url)
  return result.data
}

export const updateDeviceHms = async function (workspace_id: string, device_sn: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/devices/${workspace_id}/devices/hms/${device_sn}`
  const result = await request.put(url)
  return result.data
}

export const getDeviceHms = async function (body: HmsQueryBody, workspace_id: string, pagination: IPage): Promise<IListWorkspaceResponse<any>> {
  let url = `${HTTP_PREFIX}/devices/${workspace_id}/devices/hms?page=${pagination.page}&page_size=${pagination.page_size}` +
    `&level=${body.level ?? ''}&begin_time=${body.begin_time ?? ''}&end_time=${body.end_time ?? ''}&message=${body.message ?? ''}&language=${body.language}`
  if (body.device_sn) {
    url = url.concat(`&device_sn=${body.device_sn}`)
  }
  const result = await request.get(url)
  return result.data
}

export const changeLivestreamLens = async function (body: {}): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/live/streams/switch`
  const result = await request.post(url, body)
  return result.data
}

export const getFirmwares = async function (workspace_id: string, page: IPage, body: FirmwareQueryParam): Promise<IListWorkspaceResponse<Firmware>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspace_id}/firmwares?page=${page.page}&page_size=${page.page_size}` +
    `&device_name=${body.device_name}&product_version=${body.product_version}&status=${body.firmware_status ?? ''}`
  const result = await request.get(url)
  return result.data
}

export const importFirmareFile = async function (
  workspaceId: string,
  param: FormData,
  onUploadProgress?: (progressEvent: any) => void
): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/firmwares/file/upload`
  const result = await request.post(url, param, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress
  })
  return result.data
}

// 分片上传：校验/断点续传查询
export const checkFirmwareChunkStatus = async function (workspaceId: string, fileHash: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/firmwares/chunk/check?hash=${fileHash}`
  const result = await request.get(url)
  return result.data
}

// 分片上传：单片上传
export const importFirmwareChunk = async function (
  workspaceId: string,
  chunkData: FormData,
  onUploadProgress?: (progressEvent: any) => void
): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/firmwares/chunk/upload`
  const result = await request.post(url, chunkData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress
  })
  return result.data
}

// 分片上传：合并请求
export const mergeFirmwareChunks = async function (workspaceId: string, param: { fileHash: string; fileName: string; totalChunks: number; uploadParam: any }): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/firmwares/chunk/merge`
  const result = await request.post(url, param)
  return result.data
}


export const changeFirmareStatus = async function (workspaceId: string, firmwareId: string, param: { status: boolean }): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/firmwares/${firmwareId}`
  const result = await request.put(url, param)
  return result.data
}

// 获取登录失败IP列表
export const loginFailList = async function (): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/login-fail/list`
  const result = await request.get(url)
  return result.data
}

//解锁登录失败IP
export const unlockLoginFail = async function (ip: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/login-fail/del?ip=${ip}`
  const result = await request.delete(url)
  return result.data
}

// 高光谱负载数据采集 (AeroSpot)
export interface PostAeroSpotBody {
  dock_sn: string
  value: number // 8: 单次采集指令, 10: 连续采集开始指令, 11: 连续采集停止指令
}

export const postAeroSpotCollect = async function (workspace_id: string, body: PostAeroSpotBody): Promise<IWorkspaceResponse<any>> {
  const url = `/psdk/api/v1/aero_spot/${workspace_id}/widget_value_set`
  const result = await request.post(url, body)
  return result.data
}

// 负载操作列表项
export interface WidgetValueSetItem {
  id?: number
  name: string
  sn?: string
  psdk_index: number
  index: number
  value: number | string
  action?: string
  remark?: string
  [key: string]: any
}

// 获取负载拥有的操作列表
export const getWidgetValueSetList = async function (name: string): Promise<IWorkspaceResponse<WidgetValueSetItem[]>> {
  const url = `${HTTP_PREFIX}/psdk/widget_value_set_list`
  const result = await request.get(url, {
    params: { name }
  })
  return result.data
}

// 调用负载操作
export interface PostWidgetValueSetBody {
  dock_sn: string
  psdk_index: number
  index: number
  value: number | string
  name: string
  [key: string]: any
}

export const postWidgetValueSet = async function (workspace_id: string, body: PostWidgetValueSetBody): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/psdk/${workspace_id}/widget_value_set`
  const result = await request.post(url, body)
  return result.data
}

// ============== 第三方 API-Key 管理 ==============

// API Key 数据类型
export interface ThirdPartyApiKey {
  uid: string              // 唯一标识
  app: string              // 应用名称/Key名称
  expire_time?: number     // 过期时间
  enable: boolean          // 状态: true-启用, false-禁用
  create_time?: number     // 创建时间
  update_time?: number     // 更新时间
  // 前端兼容字段（可选，用于保留现有UI逻辑，或者如果后端确实还有这些字段）
  description?: string
  keyType?: string
  keyValue?: string        // 如果 uid 不是 key 值，可能需要额外字段，暂且假设 uid 或 app 包含关键信息，或者需要保持 keyType/keyValue 用于表单提交
}

// 创建/更新 API Key 请求参数
export interface ApiKeyFormData {
  uid?: string
  app: string
  expire_time?: number | null  // 不传或 null 代表永久生效
  enable?: boolean
}

// 查询所有 API Key
export async function getThirdPartyApiKeyList() {
  const resp = await request.get(`${HTTP_PREFIX}/apikey/all-list`)
  return resp.data
}

// 创建 API Key
export async function createThirdPartyApiKey(data: ApiKeyFormData) {
  const body: { app: string; expire_time?: number } = { app: data.app }
  if (data.expire_time) {
    body.expire_time = data.expire_time
  }
  const resp = await request.put(`${HTTP_PREFIX}/apikey/add`, body)
  return resp.data
}

// 更新 API Key
export async function updateThirdPartyApiKey(data: ApiKeyFormData) {
  const resp = await request.put(`${HTTP_PREFIX}/apikey/update`, data)
  return resp.data
}

// 删除 API Key
export async function deleteThirdPartyApiKey(app: string) {
  const resp = await request.delete(`${HTTP_PREFIX}/apikey/del?app=${app}`)
  return resp.data
}

// 切换 API Key 状态,请求参数格式：
//{
//     "app": "test1",
//     "enable": true
// }
export async function toggleThirdPartyApiKeyStatus(app: string, enable: boolean) {
  const resp = await request.put(`${HTTP_PREFIX}/apikey/enable`, { app, enable })
  return resp.data
}

// 获取无人机负载
export async function getPayloadList(workspace_id: string) {
  const resp = await request.get(`${HTTP_PREFIX}/psdk/${workspace_id}/widget`)
  return resp.data
}

// 获取无人机配置的预留返航电量
export async function getReturnHomeBattery(workspace_id: string, device_sn: string) {
  const resp = await request.get(`${HTTP_PREFIX}/devices/${workspace_id}/devices/${device_sn}/state`)
  return resp.data
}

// 组织/工作空间管理 API
export interface WorkspaceAddBody {
  workspace_name: string
  workspace_desc: string
  bind_code: string
}

export interface WorkspaceUpdateBody {
  workspace_id: string
  workspace_name: string
  workspace_desc: string
  bind_code: string
}

export const getWorkspacePageList = async function (page: number, page_size: number): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/page-list?page=${page}&page_size=${page_size}`
  const result = await request.get(url)
  return result.data
}

export const addWorkspace = async function (body: WorkspaceAddBody): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/add`
  const result = await request.post(url, body)
  return result.data
}

export const updateWorkspace = async function (body: WorkspaceUpdateBody): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/update`
  const result = await request.put(url, body)
  return result.data
}

// 切换管理员自身的组织ID
export const changeSelfWorkspaceId = async function (workspaceId: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/users/change-self-workspace-id/${workspaceId}`
  const result = await request.put(url)
  return result.data
}

// 3D 建模文件数据结构
export interface TilesetItem {
  id: number
  file_id: string
  name: string
  file_path: string
  workspace_id: string
  object_key: string
  file_type: number
  create_time: string
  update_time: string
  url?: string
}

export interface TilesetQueryParams {
  page?: number
  page_size?: number
  keyword?: string
  file_type?: number
  workspace_id?: string
}

export interface TilesetListResponse {
  list: TilesetItem[]
  pagination: IPage
}

// 获取3D 建模列表（支持分页、关键词搜索与指定组织）
export const getTilesets = async function (
  paramsOrPage?: TilesetQueryParams | number,
  pageSizeOrBody?: number | { keyword?: string; file_type?: number },
  bodyParam?: { keyword?: string; file_type?: number },
  workspaceIdParam?: string
): Promise<IWorkspaceResponse<TilesetListResponse>> {
  let page = 1
  let pageSize = 10
  let keyword: string | undefined = undefined
  let fileType = 2
  let wsId = workspaceIdParam || localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''

  if (typeof paramsOrPage === 'number') {
    page = paramsOrPage
    if (typeof pageSizeOrBody === 'number') {
      pageSize = pageSizeOrBody
    }
    if (bodyParam) {
      if (bodyParam.keyword) keyword = bodyParam.keyword
      if (bodyParam.file_type !== undefined) fileType = bodyParam.file_type
    }
  } else if (typeof paramsOrPage === 'object' && paramsOrPage !== null) {
    if (paramsOrPage.page) page = paramsOrPage.page
    if (paramsOrPage.page_size) pageSize = paramsOrPage.page_size
    if (paramsOrPage.keyword) keyword = paramsOrPage.keyword
    if (paramsOrPage.file_type !== undefined) fileType = paramsOrPage.file_type
    if (paramsOrPage.workspace_id) wsId = paramsOrPage.workspace_id
  }

  const url = `${MEDIA_HTTP_PREFIX}/map-files/${wsId}/page-list?page=${page}&page_size=${pageSize}`
  const body: { file_type: number; keyword?: string } = {
    file_type: fileType
  }
  if (keyword) {
    body.keyword = keyword
  }
  const result = await request.post(url, body)
  if (result.data?.data?.list && Array.isArray(result.data.data.list)) {
    result.data.data.list = result.data.data.list.map((item: any) => ({
      ...item,
      url: item.url || (item.object_key ? (item.object_key.startsWith('/') ? item.object_key : `/${item.object_key}`) : '')
    }))
  }
  return result.data
}

  // emqx检查clientId是否存在
  export const checkClientIdExists = async function (clientId: string): Promise<any> {
    const resp = await request.get(`/proxy/emqx/clients?clientId=${clientId}`)
    return resp.data
  }

  // emqx踢除clientId
  export const kickClientId = async function (clientId: string): Promise<any> {
    const resp = await request.post(`/proxy/emqx/kick`, [clientId])
    return resp.data
  }
