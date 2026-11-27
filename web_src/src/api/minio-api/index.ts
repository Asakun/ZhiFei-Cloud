import request from '@/api/http/request'
import {ELocalStorageKey} from "@/types";

const prefer = '/storage/api/v1'
const workspaceId: string = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''

export async function getSTSTokenApi() {
    const resp = await request.post(`${prefer}/workspaces/${workspaceId}/sts`)
    return resp.data.data
}
