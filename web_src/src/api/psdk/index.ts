import request from '@/api/http/request'
import { ELocalStorageKey } from '@/types'

export interface PayloadDataPoint {
  latitude: number
  longitude: number
  height: number
  create_time?: number | string
  psdk_name?: string
  value: Record<string, string> | string
}

export async function getFloatingWindowData(_psdkIndex: string, jobId: string): Promise<PayloadDataPoint[]> {
  const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''
  const response = await request.get(`/manage/api/v1/psdk/${encodeURIComponent(workspaceId)}/floating_window_datas`, {
    params: { jobId }
  })
  const body = response.data
  if (Array.isArray(body)) return body
  return Array.isArray(body?.data) ? body.data : []
}
