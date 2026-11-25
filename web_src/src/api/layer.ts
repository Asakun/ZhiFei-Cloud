import { ELocalStorageKey } from '../types/enums'
import request, { IWorkspaceResponse } from '@/api/http/request'
import { mapLayers } from '@/constants/mock-layers'
import { elementGroupsReq, PostElementsBody, PutElementsBody } from '@/types/mapLayer'
const PREFIX = '/map/api/v1'
const workspace_id = localStorage.getItem(ELocalStorageKey.WorkspaceId)
type UnknownResponse = Promise<IWorkspaceResponse<unknown>>
// get elements group
// export const getLayers = async (reqParams: elementGroupsReq): UnknownResponse => {
//   const url = `${PREFIX}/workspaces/${workspace_id}/element_groups`
//   const result = await request.get(url, {
//     params: {
//       group_id: reqParams.groupId,
//       is_distributed: reqParams.isDistributed
//     },
//   })
//   return result.data
// }
export const getLayers = async (reqParams: elementGroupsReq): UnknownResponse => {
  return mapLayers
}

// Get elements groups request
export const getElementGroupsReq = async (body: elementGroupsReq): Promise<IWorkspaceResponse<any>> => {
  const url = `${PREFIX}/workspaces/` + workspace_id + '/element-groups'
  const result = await request.get(url, body)
  return result.data
}
// add element groups request
export const postElementsGroupsReq = async (body: PostElementsBody): Promise<IWorkspaceResponse<{ id: string }>> => {
  const url = `${PREFIX}/workspaces/` + workspace_id + `/element-groups`
  const result = await request.post(url, body)
  return result.data
}
// add element
export const postElementsReq = async (pid: string, body: PostElementsBody): Promise<IWorkspaceResponse<{ id: string }>> => {
  const url = `${PREFIX}/workspaces/` + workspace_id + `/element-groups/${pid}/elements`
  const result = await request.post(url, body)
  return result.data
}
// Update map element request
export const updateElementsReq = async (id: string, body: PutElementsBody): Promise<IWorkspaceResponse<{ id: string }>> => {
  const url = `${PREFIX}/workspaces/` + workspace_id + `/elements/${id}`
  const result = await request.put(url, body)
  return result.data
}
// Delete map element
export const deleteElementReq = async (id: string, body: {}): Promise<any> => {
  const url = `${PREFIX}/workspaces/` + workspace_id + `/elements/${id}`
  const result = await request.delete(url, body)
  return result.data
}

// Delete layer elements
export const deleteLayerEleReq = async (id: string, body: {}): Promise<any> => {
  const url = `${PREFIX}/workspaces/` + workspace_id + `/element-groups/${id}/elements`
  const result = await request.delete(url, body)
  return result.data
}

// Delete elements groups
export const deleteElementsGroupsReq = async (id: string, body: {}): Promise<any> => {
  const url = `${PREFIX}/workspaces/` + workspace_id + `/element-groups/${id}`
  const result = await request.delete(url, body)
  return result.data
}

/**
 * 获取分组列表
 * @returns 响应格式
 * {
    "code": 0,
    "message": "success",
    "data": [
        {
            "id": 1,
            "group_id": "e3dea0f5-37f2-4d79-ae58-490af3228060",
            "group_name": "Pilot Share Layer",
            "group_type": 2,
            "workspace_id": "e3dea0f5-37f2-4d79-ae58-490af3228069",
            "is_distributed": true,
            "is_lock": false,
            "create_time": 1638330077356,
            "update_time": 1638330077356
        }    
      ]
    }
 */
export const getGroupList = async (group_type: number): Promise<IWorkspaceResponse<any>> => {
  const url = `${PREFIX}/workspaces/` + workspace_id + '/element-groups/list'
  const result = await request.get(url, {
    params: { group_type: group_type }
  })
  return result.data
}

/**
 * 根据group_id获取元素列表
 */
export const getGroupElementsList = async (element_group_id: string): Promise<IWorkspaceResponse<any>> => {
  const url = `${PREFIX}/workspaces/` + workspace_id + `/element-groups/${element_group_id}/elements/slice?page_size=10000`
  const result = await request.get(url)
  return result.data
}
