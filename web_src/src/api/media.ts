import { message } from 'ant-design-vue'
import request, { IPage, IWorkspaceResponse } from '@/api/http/request'
const HTTP_PREFIX = '/media/api/v1'

export interface MediaBody {
  job_id: string,
  start_time: string,
  end_time: string,
  drone: string,
  file_name: string,
  is_original: number
}

// Get Media Files
export const getMediaFiles = async function (wid: string, pagination: IPage, body: MediaBody = {} as MediaBody): Promise<IWorkspaceResponse<any>> {
  const url = `${HTTP_PREFIX}/files/${wid}/files?page=${pagination.page}&page_size=${pagination.page_size}`
  const finalBody = Object.assign({ is_original: 1 }, body)
  const result = await request.post(url, finalBody)
  return result.data
}
// Download Media File
export const downloadMediaFile = async function (workspaceId: string, fileId: string): Promise<any> {
  const url = `${HTTP_PREFIX}/files/${workspaceId}/file/${fileId}/url`
  const result = await request.get(url, { responseType: 'blob' })
  if (result.data.type === 'application/json') {
    const reader = new FileReader()
    reader.onload = function (e) {
      const text = reader.result as string
      const result = JSON.parse(text)
      message.error(result.message)
    }
    reader.readAsText(result.data, 'utf-8')
  } else {
    return result.data
  }
}

// Get Media File URL for direct access, fetch with x-auth-token, intercept 302 and return redirect url
import { ELocalStorageKey } from "@/types/enums";
import { CURRENT_CONFIG } from "./http/config";

export const getMediaFileUrl = async function (workspaceId: string, fileId: string): Promise<string | undefined> {
  const url = `${CURRENT_CONFIG.baseURL}${HTTP_PREFIX}/files/${workspaceId}/file/${fileId}/url`;
  const token = localStorage.getItem(ELocalStorageKey.Token);
  const response = await fetch(url, {
    method: "HEAD",
    headers: {
      "x-auth-token": token || "",
    },
  });
  return response.url || undefined;
};

export interface StorageStatResponse {
  free_bytes: number;
  total_bytes: number;
  used_bytes: number;
}

export interface BucketStatResponse {
  bucket_name: string;
  object_count: number;
  total_size: number;
  latest_modified: string;
  image_count: number;
  image_size: number;
  video_count: number;
  video_size: number;
  other_count: number;
  other_size: number;
}

// 获取磁盘存储统计
export const getStorageStat = async function (workspaceId: string): Promise<IWorkspaceResponse<StorageStatResponse>> {
  const url = `${HTTP_PREFIX}/files/${workspaceId}/storage/stat`;
  const result = await request.get(url);
  return result.data;
};

// 获取媒体 Bucket 统计
export const getBucketStat = async function (workspaceId: string): Promise<IWorkspaceResponse<BucketStatResponse>> {
  const url = `${HTTP_PREFIX}/files/${workspaceId}/bucket/stat`;
  const result = await request.get(url);
  return result.data;
};
