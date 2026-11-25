import { DEFAULT_PLACEHOLDER } from '@/utils/constants'
import { Task } from '@/api/wayline'
import { TaskStatusColor, TaskStatusMap, TaskTypeMap, OutOfControlActionMap, MediaStatusMap, MediaStatusColorMap, MediaStatus } from '@/types/task'
import { isNil } from 'lodash'
import { getMediaFiles, MediaBody } from '@/api/media'
import { IPage } from '@/api/http/request'

export function useFormatTask() {
  function formatTaskType(task: Task) {
    return TaskTypeMap[task.task_type] || DEFAULT_PLACEHOLDER
  }

  function formatTaskTime(time: string) {
    return time || DEFAULT_PLACEHOLDER
  }

  function formatLostAction(task: Task) {
    return OutOfControlActionMap[task.out_of_control_action] || DEFAULT_PLACEHOLDER
  }

  function formatTaskStatus(task: Task) {
    const statusObj = {
      text: '',
      color: ''
    }
    const { status } = task
    statusObj.text = TaskStatusMap[status]
    statusObj.color = TaskStatusColor[status]
    return statusObj
  }

  function formatMediaTaskStatus(task: Task) {
    const statusObj = {
      text: '',
      color: '',
      number: '',
      status: MediaStatus.Empty,
    }
    const { media_count, uploaded_count, uploading } = task
    if (isNil(media_count) || isNaN(media_count)) {
      return statusObj
    }
    const expectedFileCount = media_count || 0
    const uploadedFileCount = uploaded_count || 0

    if ((isNil(media_count) || media_count === 0) && !(task as any).mediaChecked) {
      (task as any).mediaChecked = true
      getTaskMediaFiles(task, { page: 1, page_size: 50 } as IPage).then(res => {
        if (res.data?.pagination?.total > 0) {
          task.media_count = res.data.pagination.total
          task.uploaded_count = res.data.pagination.total
        }
      })
    }

    if (media_count === 0) {
      statusObj.text = MediaStatusMap[MediaStatus.Empty]
      statusObj.color = MediaStatusColorMap[MediaStatus.Empty]
    } else if (media_count === uploaded_count) {
      statusObj.text = MediaStatusMap[MediaStatus.Success]
      statusObj.color = MediaStatusColorMap[MediaStatus.Success]
      statusObj.number = `(${uploadedFileCount}/${expectedFileCount})`
      statusObj.status = MediaStatus.Success
    } else {
      if (uploading) {
        statusObj.text = MediaStatusMap[MediaStatus.Uploading]
        statusObj.color = MediaStatusColorMap[MediaStatus.Uploading]
        statusObj.status = MediaStatus.Uploading
      } else {
        statusObj.text = MediaStatusMap[MediaStatus.ToUpload]
        statusObj.color = MediaStatusColorMap[MediaStatus.ToUpload]
        statusObj.status = MediaStatus.ToUpload
      }
      statusObj.number = `(${uploadedFileCount}/${expectedFileCount})`
    }
    return statusObj
  }

  async function getTaskMediaFiles(task: Task, pagination: IPage) {
    const body: MediaBody = {} as MediaBody
    if (task.job_id) {
      body.job_id = task.job_id
    }
    return await getMediaFiles(task.workspace_id, pagination, body)
  }

  return {
    formatTaskType,
    formatTaskTime,
    formatLostAction,
    formatTaskStatus,
    formatMediaTaskStatus,
    getTaskMediaFiles,
  }
}
