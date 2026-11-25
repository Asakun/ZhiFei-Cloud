<template>
  <div class="firmware-container">
    <!-- 顶部操作栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <a-button
            type="primary"
            size="large"
            @click="sVisible = true"
            class="upload-btn">
          <template #icon>
            <UploadOutlined />
          </template>
          上传固件
        </a-button>
      </div>

      <div class="toolbar-right">
        <a-select
            class="filter-select"
            v-model:value="param.firmware_status"
            placeholder="状态筛选"
            @select="getAllFirmwares(pageParam)"
            :bordered="false">
          <template #suffixIcon>
            <span class="filter-icon">📊</span>
          </template>
          <a-select-option
              v-for="(key, value) in FirmwareStatusEnum"
              :key="key"
              :value="value">
            {{ key }}
          </a-select-option>
        </a-select>

        <a-select
            class="filter-select"
            v-model:value="param.device_name"
            placeholder="设备型号"
            @select="getAllFirmwares(pageParam)"
            :bordered="false">
          <template #suffixIcon>
            <span class="filter-icon">📱</span>
          </template>
          <a-select-option
              v-for="item in deviceNameList"
              :key="item.label"
              :value="item.value">
            {{ item.label }}
          </a-select-option>
        </a-select>

        <a-input-search
            class="search-input"
            v-model:value="param.product_version"
            placeholder="搜索固件版本..."
            :enter-button="true"
            size="large"
            @search="getAllFirmwares(pageParam)">
          <template #prefix>
            <span style="opacity: 0.5">🔍</span>
          </template>
        </a-input-search>
      </div>
    </div>

    <!-- 上传弹窗 -->
    <a-modal
        v-model:open="sVisible"
        title="上传固件文件"
        :closable="!isUploading || isUploadingSuccess"
        :mask-closable="!isUploading || isUploadingSuccess"
        @cancel="onCancel"
        :width="600"
        centered
        class="upload-modal">
      <a-form
          v-if="!isUploading"
          :rules="rules"
          ref="formRef"
          :model="uploadParam"
          :label-col="{ span: 6 }"
          class="upload-form">
        <a-form-item name="status" label="立即发布">
          <a-switch
              v-model:checked="uploadParam.status"
              checked-children="是"
              un-checked-children="否" />
        </a-form-item>

        <a-form-item name="device_name" label="适用设备" required>
          <a-select
              mode="multiple"
              placeholder="可选择多个设备型号"
              v-model:value="uploadParam.device_name"
              :max-tag-count="2">
            <a-select-option
                v-for="k in DeviceNameEnum"
                :key="k"
                :value="k">
              {{ k }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item name="release_note" label="更新说明" required>
          <a-textarea
              v-model:value="uploadParam.release_note"
              :rows="4"
              showCount
              :maxlength="300"
              placeholder="请输入固件更新说明..." />
        </a-form-item>

        <a-form-item label="固件文件" required>
          <a-upload
              :multiple="false"
              :before-upload="beforeUpload"
              :show-upload-list="true"
              :file-list="fileList"
              @remove="removeFile"
              class="upload-dragger">
            <a-button type="dashed" size="large" block>
              <UploadOutlined />
              选择 ZIP 文件
            </a-button>
          </a-upload>
        </a-form-item>
      </a-form>

      <!-- 上传中实时进度及网速 UI -->
      <div v-else class="upload-progress-container">
        <div class="file-info">
          <span class="file-name" :title="currentUploadFileName">📦 {{ currentUploadFileName }}</span>
          <span class="file-size">{{ bytesToSize(currentUploadFileSize) }}</span>
        </div>

        <a-progress
            :percent="uploadProgress.percent"
            :status="uploadProgress.percent === 100 ? 'success' : 'active'"
            :stroke-width="14"
            stroke-color="linear-gradient(90deg, #2563eb 0%, #00d4ff 100%)"
        />

        <div class="progress-meta-grid">
          <div class="meta-item">
            <span class="meta-label">🚀 传输速度</span>
            <span class="meta-value speed">{{ uploadProgress.speedStr }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">⏱️ 预计剩余</span>
            <span class="meta-value eta">{{ uploadProgress.etaStr }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">📊 传输大小</span>
            <span class="meta-value size">{{ bytesToSize(uploadProgress.loaded) }} / {{ bytesToSize(uploadProgress.total) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">⚡ 传输状态</span>
            <span class="meta-value mode">{{ uploadProgress.percent === 100 ? '解析校验中' : '高极速传输' }}</span>
          </div>
        </div>

        <div class="status-hint">
          <a-spin size="small" style="margin-right: 8px" v-if="uploadProgress.percent < 100" />
          <span>{{ uploadStatusText }}</span>
        </div>
      </div>

      <template #footer>
        <div v-if="!isUploading" class="modal-footer-btns">
          <a-button class="cancel-btn" @click="onCancel">取消</a-button>
          <a-button type="primary" class="submit-btn" @click="uploadFile">开始上传</a-button>
        </div>
        <div v-else class="modal-footer-btns">
          <a-button 
            v-if="!isUploadingSuccess" 
            class="bg-transfer-btn" 
            @click="onCancel">
            后台传输
          </a-button>
          <a-button 
            v-else 
            type="primary" 
            class="success-close-btn" 
            @click="onCancel">
            完成并关闭
          </a-button>
        </div>
      </template>
    </a-modal>

    <!-- 数据表格 -->
    <div class="table-container">
      <a-table
          :columns="columns"
          :data-source="data.firmware"
          :pagination="false"
          @change="refreshData"
          row-key="firmware_id"
          :row-class-name="(record, index) => ((index % 2) === 0 ? 'table-striped' : '')"
          :scroll="{ x: '100%', y: 600 }"
          class="firmware-table">
        <template #bodyCell="{ column, record, text }">
          <template v-if="column.dataIndex === 'device_name'">
            <div class="device-tags">
              <a-tag
                  v-for="tagText in record.device_name"
                  :key="tagText"
                  color="blue">
                {{ tagText }}
              </a-tag>
            </div>
          </template>

          <template v-else-if="column.dataIndex === 'file_size'">
            <span class="file-size">{{ bytesToSize(record.file_size) }}</span>
          </template>

          <template v-else-if="column.dataIndex === 'firmware_status'">
            <DeviceFirmwareStatus :firmware="record" @refresh="getAllFirmwares(pageParam)" />
          </template>

          <template v-else-if="['file_name', 'release_note'].includes(column.dataIndex)">
            <a-tooltip :title="text">
              <span class="text-ellipsis">{{ text }}</span>
            </a-tooltip>
          </template>
        </template>
      </a-table>
      <div class="pagination-container">
        <a-pagination
            v-model:current="paginationProp.current"
            v-model:page-size="paginationProp.pageSize"
            :total="paginationProp.total"
            :show-total="(total: number) => `共 ${total} 条记录`"
            show-quick-jumper
            @change="handlePageChange"
            @showSizeChange="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { message, notification, PaginationProps } from 'ant-design-vue'
import { TableState } from 'ant-design-vue/lib/table/interface'
import { onMounted, reactive, Ref, ref, UnwrapRef } from 'vue'
import { IPage } from '@/api/http/type'
import { getFirmwares, importFirmareFile } from '@/api/manage'
import DeviceFirmwareStatus from '@/components/devices/DeviceFirmwareStatus.vue'
import { ELocalStorageKey } from '@/types'
import { UploadOutlined } from '@ant-design/icons-vue'
import { Firmware, FirmwareQueryParam, FirmwareStatusEnum, DeviceNameEnum, FirmwareUploadParam } from '@/types/device-firmware'
import { commonColor } from '@/utils/color'
import { bytesToSize } from '@/utils/bytes'
import { UploadSpeedTracker, UploadProgressInfo } from '@/utils/chunkUpload'
import moment from 'moment'


interface FirmwareData {
  firmware: Firmware[]
}

const columns = [
  { title: '设备型号', dataIndex: 'device_name', width: 140, ellipsis: true },
  { title: '文件名称', dataIndex: 'file_name', width: 220, ellipsis: true },
  { title: '固件版本', dataIndex: 'product_version', width: 150 },
  { title: '文件大小', dataIndex: 'file_size', width: 120 },
  { title: '创建者', dataIndex: 'username', width: 100 },
  { title: '发布日期', dataIndex: 'released_time', width: 160, sorter: (a: Firmware, b: Firmware) => a.released_time.localeCompare(b.released_time) },
  { title: '更新说明', dataIndex: 'release_note', width: 300, ellipsis: true },
  { title: '状态', dataIndex: 'firmware_status', width: 100 },
]

const data = reactive<FirmwareData>({
  firmware: []
})

const paginationProp = reactive({
  pageSizeOptions: ['20', '50', '100'],
  showQuickJumper: true,
  showSizeChanger: true,
  pageSize: 50,
  current: 1,
  total: 0
})

// 分页改变
const handlePageChange = (page: number, pageSize: number) => {
  paginationProp.current = page
  paginationProp.pageSize = pageSize
}

const deviceNameList = ref<any[]>([{ label: '全部设备', value: '' }])

type Pagination = TableState['pagination']

const pageParam: IPage = {
  page: 1,
  total: 0,
  page_size: 50
}
const workspaceId: string = localStorage.getItem(ELocalStorageKey.WorkspaceId)!

const param = reactive<FirmwareQueryParam>({
  product_version: '',
  device_name: '',
  firmware_status: FirmwareStatusEnum.NONE
})

onMounted(() => {
  getAllFirmwares(pageParam)
  for (const key in DeviceNameEnum) {
    const value = DeviceNameEnum[key]
    deviceNameList.value.push({ label: value, value: value })
  }
})

function refreshData (page: Pagination) {
  pageParam.page = page?.current!
  pageParam.page_size = page?.pageSize!
  getAllFirmwares(pageParam)
}

function getAllFirmwares (page: IPage) {
  getFirmwares(workspaceId, page, param).then(res => {
    const firmwareList: Firmware[] = res.data.list
    data.firmware = firmwareList
    paginationProp.total = res.data.pagination.total
    paginationProp.current = res.data.pagination.page
  })
}

const sVisible = ref(false)
const uploadParam = reactive<FirmwareUploadParam>({
  device_name: [],
  release_note: '',
  status: true
})

const rules = {
  status: [{ required: true }],
  release_note: [{ required: true, message: '请输入更新说明' }],
  device_name: [{ required: true, message: '请选择适用的设备型号' }]
}

interface FileItem {
  uid: string;
  name?: string;
  status?: string;
  response?: string;
  url?: string;
}

interface FileInfo {
  file: FileItem;
  fileList: FileItem[];
}

const fileList = ref<FileItem[]>([])

function beforeUpload (file: FileItem) {
  if (!file.name || !file.name?.endsWith('.zip')) {
    message.error('文件格式错误，请选择 ZIP 文件')
    return false
  }
  fileList.value = [file]
  return false
}

const formRef = ref()

function removeFile (file: FileItem) {
  fileList.value = []
}

const isUploading = ref(false)
const isUploadingSuccess = ref(false)
const currentUploadFileName = ref('')
const currentUploadFileSize = ref(0)
const uploadStatusText = ref('')
const speedTracker = new UploadSpeedTracker()

const uploadProgress = reactive<UploadProgressInfo>({
  percent: 0,
  loaded: 0,
  total: 0,
  speed: 0,
  speedStr: '0 B/s',
  eta: 0,
  etaStr: '--',
  statusText: ''
})

function onCancel () {
  if (isUploading.value && !isUploadingSuccess.value) {
    sVisible.value = false
    message.info('上传已切入后台继续进行')
    return
  }
  isUploading.value = false
  isUploadingSuccess.value = false
  if (formRef.value) {
    formRef.value.resetFields()
  }
  fileList.value = []
  sVisible.value = false
}

const uploadFile = async () => {
  if (fileList.value.length === 0) {
    message.error('请至少选择一个文件')
    return
  }

  formRef.value.validate().then(async () => {
    const rawFile = (fileList.value[0] as any).originFileObj || fileList.value[0]
    const fileObj: File = rawFile as File
    currentUploadFileName.value = fileObj.name || 'firmware.zip'
    currentUploadFileSize.value = fileObj.size || 0

    isUploading.value = true
    isUploadingSuccess.value = false
    uploadStatusText.value = '正在准备文件传输...'
    speedTracker.reset(fileObj.size)

    const fileData = new FormData()
    fileData.append('file', fileObj, fileObj.name)
    Object.keys(uploadParam).forEach((key) => {
      const val = uploadParam[key as keyof FirmwareUploadParam]
      if (val instanceof Array) {
        val.forEach((value) => {
          fileData.append(key, value)
        })
      } else {
        fileData.append(key, val.toString())
      }
    })

    const startTime = Date.now()
    uploadStatusText.value = '文件极速传输中...'

    try {
      const res = await importFirmareFile(workspaceId, fileData, (progressEvent) => {
        if (progressEvent.lengthComputable || progressEvent.total) {
          const loaded = progressEvent.loaded
          const total = progressEvent.total || fileObj.size
          const info = speedTracker.update(loaded)
          Object.assign(uploadProgress, info)
          if (loaded >= total) {
            uploadStatusText.value = '传输完成，后端正在进行格式解压与设备校验...'
          } else {
            uploadStatusText.value = `上传中... ${info.percent}%`
          }
        }
      })

      if (res.code === 0) {
        uploadProgress.percent = 100
        uploadStatusText.value = '固件上传成功！'
        isUploadingSuccess.value = true
        notification.success({
          message: `上传成功 ${moment().format('HH:mm:ss')}`,
          description: `[${fileObj.name}] 上传成功，耗时: ${((Date.now() - startTime) / 1000).toFixed(1)} 秒`,
          duration: 4
        })
        getAllFirmwares(pageParam)
        setTimeout(() => {
          onCancel()
        }, 1200)
      } else {
        notification.error({
          message: `上传失败 [${fileObj.name}]`,
          description: `错误信息: ${res.message}`,
          style: { color: commonColor.FAIL },
          duration: null,
        })
        isUploading.value = false
        isUploadingSuccess.value = false
      }
    } catch (err: any) {
      notification.error({
        message: '上传异常',
        description: err?.message || '网络传输中断，请重试',
        duration: 4
      })
      isUploading.value = false
      isUploadingSuccess.value = false
    }
  })
}
</script>

<style scoped lang="scss">
.upload-progress-container {
  padding: 16px 8px;
  display: flex;
  flex-direction: column;
  gap: 16px;

  .file-info {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .file-name {
      font-weight: 600;
      color: #ffffff;
      font-size: 15px;
      max-width: 380px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .file-size {
      color: #00d4ff;
      font-family: 'Monaco', 'Consolas', monospace;
      font-weight: 600;
    }
  }

  .progress-meta-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    background: rgba(0, 0, 0, 0.25);
    padding: 16px;
    border-radius: 10px;
    border: 1px solid rgba(255, 255, 255, 0.08);

    .meta-item {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .meta-label {
        font-size: 12px;
        color: rgba(255, 255, 255, 0.5);
      }

      .meta-value {
        font-size: 14px;
        font-weight: 600;
        font-family: 'Monaco', 'Consolas', monospace;
        color: #ffffff;

        &.speed {
          color: #52c41a;
        }

        &.eta {
          color: #faad14;
        }

        &.size {
          color: #1890ff;
        }

        &.mode {
          color: #00d4ff;
        }
      }
    }
  }

  .status-hint {
    display: flex;
    align-items: center;
    justify-content: center;
    color: rgba(255, 255, 255, 0.7);
    font-size: 13px;
    margin-top: 4px;
  }
}

.firmware-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #0a1929 0%, #041428 50%, #002658 100%);
  padding: clamp(16px, 2vw, 24px);
  display: flex;
  flex-direction: column;
}

/* 工具栏样式 */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  padding: clamp(12px, 1.5vw, 20px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  margin-bottom: clamp(16px, 2vw, 24px);
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.upload-btn {
  height: 40px;
  padding: 0 24px;
  font-weight: 500;
  border-radius: 8px;
  background: linear-gradient(135deg, #2563eb, #60a5fa);
  border: none;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(37, 99, 235, 0.4);
  }
}

.filter-select {
  min-width: 140px;
  height: 40px;
  
  :deep(.ant-select-selector) {
    background: rgba(255, 255, 255, 0.1) !important;
    border: 1px solid rgba(255, 255, 255, 0.1) !important;
    color: white !important;
    border-radius: 8px !important;
    height: 40px !important;
    display: flex;
    align-items: center;
  }

  :deep(.ant-select-arrow) {
    color: rgba(255, 255, 255, 0.6);
  }
}

.filter-icon {
  font-size: 16px;
  margin-right: 4px;
}

.search-input {
  width: 260px;
  height: 40px;
  
  :deep(.ant-input-wrapper) {
    height: 40px;
  }

  :deep(.ant-input-affix-wrapper) {
    background: rgba(255, 255, 255, 0.1) !important;
    border: 1px solid rgba(255, 255, 255, 0.1) !important;
    border-radius: 8px 0 0 8px !important;
    height: 40px !important;
    
    &:hover, &:focus {
      border-color: rgba(255, 255, 255, 0.2) !important;
    }
  }
  
  :deep(.ant-input) {
    background: transparent !important;
    border: none !important;
    color: white;
    height: 100%;
    
    &::placeholder {
      color: rgba(255, 255, 255, 0.4);
    }
  }

  :deep(.ant-btn) {
    border-radius: 0 8px 8px 0 !important;
    height: 40px !important;
    background: rgba(255, 255, 255, 0.15) !important;
    border: 1px solid rgba(255, 255, 255, 0.1) !important;
    border-left: none !important;
    
    &:hover {
      background: rgba(255, 255, 255, 0.25) !important;
    }

    .anticon {
      color: rgba(255, 255, 255, 0.8);
    }
  }
}

/* 表格容器样式 */
.table-container {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  padding: 24px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* 表格样式优化 */
.firmware-table :deep(.ant-table) {
  background: transparent;
  color: white;
}

.firmware-table :deep(.ant-table-thead > tr > th) {
  background: rgba(0, 0, 0, 0.2);
  color: rgba(255, 255, 255, 0.8);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  font-weight: 600;
}

/* 表格行基础样式 - 添加过渡效果防止闪烁 */
.firmware-table :deep(.ant-table-tbody > tr) {
  background: transparent;
  transition: background-color 0.2s ease;
}

.firmware-table :deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.9);
  background: inherit !important;
  transition: background-color 0.2s ease;
}

/* 斑马纹行样式 */
.firmware-table :deep(.ant-table-tbody > tr.table-striped) {
  background: rgba(255, 255, 255, 0.02);
}

.firmware-table :deep(.ant-table-tbody > tr.table-striped > td) {
  background: rgba(255, 255, 255, 0.02) !important;
}

/* 普通行 hover 样式 */
.firmware-table :deep(.ant-table-tbody > tr:hover) {
  background: rgba(255, 255, 255, 0.05);
}

.firmware-table :deep(.ant-table-tbody > tr:hover > td) {
  background: rgba(255, 255, 255, 0.05) !important;
}

/* 斑马纹行 hover 样式 */
.firmware-table :deep(.ant-table-tbody > tr.table-striped:hover) {
  background: rgba(255, 255, 255, 0.05);
}

.firmware-table :deep(.ant-table-tbody > tr.table-striped:hover > td) {
  background: rgba(255, 255, 255, 0.05) !important;
}

.device-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.device-tags :deep(.ant-tag) {
  margin: 0;
  border-radius: 4px;
  font-size: 12px;
  padding: 2px 8px;
  background: rgba(24, 144, 255, 0.2);
  border-color: rgba(24, 144, 255, 0.3);
  color: #40a9ff;
}

.file-size {
  font-family: 'Monaco', 'Consolas', monospace;
  font-weight: 500;
  color: #00d4ff;
}

.text-ellipsis {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 上传弹窗样式 - 需要全局样式或者穿透 */
:deep(.ant-modal-content) {
  background: #1f2937;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

:deep(.ant-modal-header) {
  background: rgba(255, 255, 255, 0.05);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

:deep(.ant-modal-title) {
  color: white;
}

:deep(.ant-modal-close-x) {
  color: rgba(255, 255, 255, 0.6);
  
  &:hover {
    color: white;
  }
}

:deep(.ant-form-item-label > label) {
  color: rgba(255, 255, 255, 0.8);
}

:deep(.ant-input),
:deep(.ant-input-textarea) {
  background: rgba(0, 0, 0, 0.25) !important;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  color: #ffffff !important;
  
  &:focus {
    border-color: #2563eb !important;
    box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2) !important;
  }
}

/* 文本域字数统计与 Placeholder 颜色适配 */
:deep(.ant-input-textarea-show-count::after),
:deep(.ant-input-data-count) {
  color: rgba(255, 255, 255, 0.6) !important;
}

:deep(.ant-input::placeholder),
:deep(textarea::placeholder) {
  color: rgba(255, 255, 255, 0.4) !important;
}

/* 表单内部 Select 下拉框暗色模式适配 */
.upload-form :deep(.ant-select) {
  .ant-select-selector {
    background: rgba(0, 0, 0, 0.25) !important;
    border: 1px solid rgba(255, 255, 255, 0.15) !important;
    border-radius: 8px !important;
    color: #ffffff !important;
    min-height: 40px !important;
    display: flex;
    align-items: center;
  }

  .ant-select-selection-placeholder {
    color: rgba(255, 255, 255, 0.4) !important;
  }

  .ant-select-selection-item {
    background: rgba(37, 99, 235, 0.25) !important;
    border: 1px solid rgba(37, 99, 235, 0.4) !important;
    color: #60a5fa !important;
    border-radius: 4px !important;

    .ant-select-selection-item-remove {
      color: rgba(255, 255, 255, 0.7) !important;

      &:hover {
        color: #ffffff !important;
      }
    }
  }

  .ant-select-arrow {
    color: rgba(255, 255, 255, 0.6) !important;
  }
}

/* 上传已选文件列表项样式适配 */
:deep(.ant-upload-list) {
  .ant-upload-list-item {
    background: rgba(255, 255, 255, 0.06) !important;
    border: 1px solid rgba(255, 255, 255, 0.15) !important;
    border-radius: 8px !important;
    margin-top: 8px !important;
    padding: 4px 8px !important;
    color: #ffffff !important;
    transition: all 0.2s ease;

    &:hover {
      background: rgba(255, 255, 255, 0.12) !important;
      border-color: rgba(0, 212, 255, 0.4) !important;
    }

    .ant-upload-list-item-name {
      color: #00d4ff !important;
      font-weight: 500 !important;
      font-size: 14px !important;
      padding-left: 24px !important;
    }

    .anticon-paper-clip {
      color: #00d4ff !important;
      font-size: 16px !important;
    }

    .anticon-delete,
    .ant-upload-list-item-card-actions-btn {
      color: rgba(255, 255, 255, 0.6) !important;

      &:hover {
        color: #ff4d4f !important;
      }
    }
  }

  .ant-upload-list-item-info {
    color: #ffffff !important;

    a {
      color: #00d4ff !important;
    }
  }
}

:deep(.ant-modal-footer) {
  border-top: 1px solid rgba(255, 255, 255, 0.1) !important;
  padding: 12px 16px !important;
  background: rgba(0, 0, 0, 0.2) !important;

  .modal-footer-btns {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }

  .ant-btn {
    height: 38px !important;
    padding: 0 20px !important;
    border-radius: 8px !important;
    font-weight: 500 !important;
    font-size: 14px !important;
    color: rgba(255, 255, 255, 0.9) !important;
    background: rgba(255, 255, 255, 0.08) !important;
    border: 1px solid rgba(255, 255, 255, 0.2) !important;

    &:hover {
      color: #ffffff !important;
      background: rgba(255, 255, 255, 0.18) !important;
      border-color: rgba(255, 255, 255, 0.35) !important;
    }

    &.ant-btn-primary,
    &.submit-btn {
      color: #ffffff !important;
      background: linear-gradient(135deg, #2563eb, #3b82f6) !important;
      border: none !important;

      &:hover {
        background: linear-gradient(135deg, #1d4ed8, #2563eb) !important;
        box-shadow: 0 4px 12px rgba(37, 99, 235, 0.4) !important;
      }
    }

    &.bg-transfer-btn {
      color: #38bdf8 !important;
      background: rgba(56, 189, 248, 0.12) !important;
      border: 1px solid rgba(56, 189, 248, 0.4) !important;

      &:hover {
        background: rgba(56, 189, 248, 0.25) !important;
        color: #ffffff !important;
      }
    }

    &.success-close-btn {
      color: #ffffff !important;
      background: linear-gradient(135deg, #16a34a, #22c55e) !important;
      border: none !important;

      &:hover {
        background: linear-gradient(135deg, #15803d, #16a34a) !important;
        box-shadow: 0 4px 12px rgba(34, 197, 94, 0.4) !important;
      }
    }
  }
}

/* 分页器样式覆盖 */
:deep(.ant-pagination-item),
:deep(.ant-pagination-prev),
:deep(.ant-pagination-next) {
  background: rgba(255, 255, 255, 0.05) !important;
  border-color: rgba(255, 255, 255, 0.2) !important;
  
  a {
    color: white !important;
  }
  
  &:hover {
    border-color: #2563eb !important;
    color: #2563eb !important;
  }
}

:deep(.ant-pagination-item-active) {
  background: #2563eb !important;
  border-color: #2563eb !important;
  
  a {
    color: white !important;
  }
}

:deep(.ant-pagination-total-text) {
  color: rgba(255, 255, 255, 0.6);
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-left,
  .toolbar-right {
    width: 100%;
    justify-content: space-between;
  }

  .search-input {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .firmware-container {
    padding: 16px;
  }

  .toolbar-right {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-select,
  .search-input {
    width: 100%;
  }
}

/* 覆盖 DeviceFirmwareStatus 组件样式以适配暗色主题 */
.firmware-table :deep(.status-badge.status-available) {
  background-color: rgba(82, 196, 26, 0.2) !important;
  border-color: rgba(82, 196, 26, 0.4) !important;
  color: #73d13d !important;
}

.firmware-table :deep(.status-badge.status-disabled) {
  background-color: rgba(255, 77, 79, 0.2) !important;
  border-color: rgba(255, 77, 79, 0.4) !important;
  color: #ff7875 !important;
}
</style>
