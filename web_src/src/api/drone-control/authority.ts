import request, { IWorkspaceResponse } from '@/api/http/request'

const API_PREFIX = '/control/api/v1'

// 获取当前控制权信息
export async function getAuthoriyInfo(dockSn: string): Promise<IWorkspaceResponse<null>> {
    const resp = await request.get(`${API_PREFIX}/devices/${dockSn}/authority`, {
        params: {
            dock_sn: dockSn
        }
    })
    return resp.data
}

// 退出控制权

export async function postExitAuthority(dockSn: string): Promise<IWorkspaceResponse<null>> {
    const resp = await request.delete(`${API_PREFIX}/devices/${dockSn}/authority`, {
        params: {
            dock_sn: dockSn
        }
    })
    return resp.data
}

// 夺取手持设备控制权
export async function grabCloudControl(dockSn: string): Promise<IWorkspaceResponse<null>> {
    const resp = await request.post(`${API_PREFIX}/devices/${dockSn}/auth/cloud-control`, {}, {
        params: {
            dock_sn: dockSn
        }
    })
    return resp.data
}

// 释放手持设备控制权
export async function releaseCloudControl(dockSn: string): Promise<IWorkspaceResponse<null>> {
    const resp = await request.delete(`${API_PREFIX}/devices/${dockSn}/auth/cloud-control`, {
        params: {
            dock_sn: dockSn
        }
    })
    return resp.data
}