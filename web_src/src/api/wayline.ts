import { message } from 'ant-design-vue'
import request, { IPage, IWorkspaceResponse, IListWorkspaceResponse } from '@/api/http/request'
import { TaskType, TaskStatus, OutOfControlAction } from '@/types/task'
import { WaylineType } from '@/types/wayline'
import { ELocalStorageKey } from '@/types'

const HTTP_PREFIX = '/wayline/api/v1'

// Get Wayline Files
export const getWaylineFiles = async function (wid: string, body: {
  page: any;
  page_size: any;
  order_by: any;
}): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${wid}/waylines?order_by=${body.order_by}&page=${body.page}&page_size=${body.page_size}`
  const result = await request.get(url)
  return result.data
}

// 获取航线文件，响应值中新增dockSn字段
export const getWaylineFilesDetail = async function (workspaceId: string, page: number, page_size: number, body: {
  wayline_id?: string,
  wayline_name?: string,
  dock_sn?: string
}): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/waylines/list?page=${page}&page_size=${page_size}`
  const result = await request.post(url, body)
  return result.data
}

// Download Wayline File
export const downloadWaylineFile = async function (workspaceId: string, waylineId: string): Promise<any> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/waylines/${waylineId}/url`
  const result = await request.get(url, { responseType: 'blob' })
  if (result.data && result.data.type === 'application/json') {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = async function () {
        try {
          const text = reader.result as string
          const json = JSON.parse(text)
          if (json.data && json.data.url) {
            const fileRes = await fetch(json.data.url)
            const fileBlob = await fileRes.blob()
            resolve(fileBlob)
          } else if (json.url) {
            const fileRes = await fetch(json.url)
            const fileBlob = await fileRes.blob()
            resolve(fileBlob)
          } else {
            message.error(json.message || '获取航线下载地址失败')
            resolve(null)
          }
        } catch (e) {
          reject(e)
        }
      }
      reader.onerror = reject
      reader.readAsText(result.data, 'utf-8')
    })
  } else {
    return result.data
  }
}

// Delete Wayline File
export const deleteWaylineFile = async function (workspaceId: string, waylineId: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/waylines/${waylineId}`
  const result = await request.delete(url)
  return result.data
}

export interface CreatePlan {
  name: string,
  file_id: string,
  dock_sn: string,
  task_type: TaskType, // 任务类型
  wayline_type: WaylineType, // 航线类型
  task_days: number[] // 执行任务的日期（秒）
  task_periods: number[][] // 执行任务的时间点（秒）
  rth_altitude: number // 相对机场返航高度 20 - 500
  out_of_control_action: OutOfControlAction // 失控动作
  min_battery_capacity?: number, // The minimum battery capacity of aircraft.
  min_storage_capacity?: number, // The minimum storage capacity of dock and aircraft.
}

// Create Wayline Job
export const createPlan = async function (workspaceId: string, plan: CreatePlan): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/flight-tasks`
  const result = await request.post(url, plan)
  return result.data
}

export interface Task {
  job_id: string,
  job_name: string,
  task_type: TaskType, // 任务类型
  file_id: string, // 航线文件id
  file_name: string, // 航线名称
  wayline_type: WaylineType, // 航线类型
  dock_sn: string,
  dock_name: string,
  workspace_id: string,
  username: string,
  begin_time: string,
  end_time: string,
  execute_time: string,
  completed_time: string,
  status: TaskStatus, // 任务状态
  progress: number, // 执行进度
  code: number, // 错误码
  rth_altitude: number // 相对机场返航高度 20 - 500
  out_of_control_action: OutOfControlAction // 失控动作
  media_count: number // 媒体数量
  uploading: boolean // 是否正在上传媒体
  uploaded_count: number // 已上传媒体数量
}

export interface WaylineBody {
  file_id: string,
  start_time: string,
  end_time: string,
  status: TaskStatus,
  wayline_type: WaylineType,
  dock_sn: string,

}
// Get Wayline Jobs
export const getWaylineJobs = async function (workspaceId: string, page: IPage, body: WaylineBody = {} as WaylineBody): Promise<IListWorkspaceResponse<Task>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/jobs?page=${page.page}&page_size=${page.page_size}`
  const result = await request({
    method: 'POST',
    url: url,
    data: body
  })
  return result.data
}

export interface DeleteTaskParams {
  job_id: string
}

//  删除机场任务
export async function deleteTask(workspaceId: string, params: DeleteTaskParams): Promise<IWorkspaceResponse<{}>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/jobs`
  const result = await request.delete(url, {
    params: params
  })
  return result.data
}

export enum UpdateTaskStatus {
  Suspend = 0, // 暂停
  Resume = 1, // 恢复
}
export interface UpdateTaskStatusBody {
  job_id: string
  status: UpdateTaskStatus
}

// 更新机场任务状态
export async function updateTaskStatus(workspaceId: string, body: UpdateTaskStatusBody): Promise<IWorkspaceResponse<{}>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/jobs/${body.job_id}`
  const result = await request.put(url, {
    status: body.status
  })
  return result.data
}

// Upload Wayline file
export const importKmzFile = async function (workspaceId: string, file: {}): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/waylines/file/upload`
  const result = await request.post(url, file, {
    headers: {
      'Content-Type': 'multipart/form-data',
    }
  })
  return result.data
}

// 媒体立即上传
export const uploadMediaFileNow = async function (workspaceId: string, jobId: string): Promise<IWorkspaceResponse<{}>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/jobs/${jobId}/media-highest`
  const result = await request.post(url)
  return result.data
}



// ============== 面状航线 (mapping2d) 相关类型与接口 ==============

export interface Mapping2dCoordinate {
  latitude: number
  longitude: number
}

export interface SimRoutePoint {
  wg_lon?: number
  wg_lat?: number
  longitude?: number
  latitude?: number
  height?: number
  type?: string
  [key: string]: any
}

export interface Mapping2dTypeReq {
  collection_method?: string // 目前支持 'ortho'
  overlap_h: number // 航向重叠率 (%)
  overlap_w: number // 旁向重叠率 (%)
  is_elevation_optimize?: number // 是否开启高程优化 0 | 1
  elevation_optimize_enable?: number // 兼容部分后端字段名
  shoot_type?: string // 'time' | 'distance'
  direction?: string | number // 航线角度，可不传，后端自动计算最优角度
  margin?: number // 外扩边距（米）
  coordinates: Mapping2dCoordinate[] // 测区多边形顶点列表
  sim_route_point_list?: SimRoutePoint[]
}

export interface PrepareWaylineReq {
  template_type: string // 'mapping2d'
  auto_flight_speed: number
  drone_type: number
  sub_drone_type: number
  payload_type: number
  payload_sub_type: number
  payload_position: number
  image_format: string
  finish_action: string
  exit_on_rc_lost_action: string
  global_height: number
  height_mode?: string
  take_off_ref_point?: string
  take_off_ref_point_agl_height?: number
  mapping_type_req: Mapping2dTypeReq
  [key: string]: any
}

// 航线预览计算 (POST wayline prepare edit，不保存)
export const prepareWaylineEdit = async function (workspaceId: string, body: PrepareWaylineReq | any): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/waylines/file/prepare-edit`
  const result = await request.post(url, body)
  return result.data
}

// 保存航线数据
export const saveWaylineEdit = async function (workspaceId: string, name: string, body: {}): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/waylines/file/edit?filename=${name}`
  const result = await request.post(url, body)
  return result.data
}

// 读取航线数据
export const getWaylineData = async function (workspaceId: string, waylineId: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/waylines/file/edit?waylineId=${waylineId}`
  const result = await request.get(url)
  return result.data
}

// 修改航线, 新增一个dock_sn参数, 用于指定航线所属的机场,位置放在params中
export const modifyWaylineEdit = async function (workspaceId: string, waylineId: string, body: {}, dock_sn: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/waylines/file/edit/${waylineId}`
  const result = await request.post(url, body, {
    params: {
      dock_sn: dock_sn
    }
  })
  return result.data
}

// 根据dockSn码获取航线
export const getWaylineListByDockSn = async function (workspaceId: string, dock_sn: string): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/waylines/list?page=1&page_size=100`
  const result = await request.post(url, {
    "favorited": 0,
    "template_type": 0,
    "dock_sn": dock_sn
  })
  return result.data
}

// ============== 飞行统计相关接口类型定义 ==============

// job-sum 接口响应数据类型
export interface FlightTotalStatistics {
  total_distance: number  // 总飞行距离（米）
  total_count: number     // 总飞行架次
  total_time: number      // 总飞行时长（秒）
}

// job-sum-by-date 接口响应数据类型
export interface FlightStatisticsByDate {
  stat_date: string       // 统计日期，格式：YYYY-MM-DD
  total_count: number     // 当日飞行架次
  total_distance: number  // 当日飞行距离（米）
  total_time: number      // 当日飞行时长（秒）
}

// job-rank 接口响应数据类型
export interface FlightRankingItem {
  dock_sn: string         // 机场序列号
  nickname: string        // 机场昵称
  username: string        // 用户名
  total_count: number     // 飞行架次
  total_distance: number  // 飞行距离（米）
  total_time: number      // 飞行时长（秒）
}

// 飞行总统计,时间格式：1766652105000，不传时间参数则查询全部
export const getFlightTotalStatistics = async function (workspaceId: string, start_time?: number, end_time?: number): Promise<IWorkspaceResponse<FlightTotalStatistics>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/job-sum`
  const params: Record<string, number> = {}
  if (start_time !== undefined) params.start_time = start_time
  if (end_time !== undefined) params.end_time = end_time
  const result = await request.get(url, { params })
  return result.data
}

// 飞行统计按日期分组,时间格式：1766652105000，不传时间参数则查询全部
export const getFlightStatisticsByDate = async function (workspaceId: string, start_time?: number, end_time?: number): Promise<IWorkspaceResponse<FlightStatisticsByDate[]>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/job-sum-by-date`
  const params: Record<string, number> = {}
  if (start_time !== undefined) params.start_time = start_time
  if (end_time !== undefined) params.end_time = end_time
  const result = await request.get(url, { params })
  return result.data
}

// 飞行排名 group_by可选值：DOCK_SN\USERNAME ， sort可选值：TOTAL_COUNT ,时间格式：1766652105000，不传时间参数则查询全部
export const getFlightRanking = async function (workspaceId: string, start_time: number | undefined, end_time: number | undefined, group_by: string, sort: string): Promise<IWorkspaceResponse<FlightRankingItem[]>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/job-rank`
  const params: Record<string, string | number> = {
    group_by: group_by,
    sort: sort
  }
  if (start_time !== undefined) params.start_time = start_time
  if (end_time !== undefined) params.end_time = end_time
  const result = await request.get(url, { params })
  return result.data
}


// 航线暂停/恢复 status = 0 暂停 status = 1 恢复
export const pauseOrResumeWayline = async function (workspaceId: string, job_id: string, status: number): Promise<IWorkspaceResponse<{}>> {
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/jobs/${job_id}`
  const result = await request.put(url, {
    status: status
  })
  return result.data
}

// 获取当前正在执行的任务 ID
export const getFlightId = async function (dockSn: string): Promise<any> {
  const workspaceId: string = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''
  const url = `${HTTP_PREFIX}/workspaces/${workspaceId}/ongoing-job?dockSn=${dockSn}`
  const resp = await request.get(url)
  return resp.data
}

// 负载后台记录开关
export const lingxiuSwitch = async function (dockSn: string, enable_record: boolean): Promise<any> {
  const workspaceId: string = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''
  const resp = await request.post(`${HTTP_PREFIX}/workspaces/${workspaceId}/ongoing-job?dockSn=${dockSn}&enable_record=${enable_record}`)
  return resp.data
}