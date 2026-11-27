<template>
  <div class="firmware-status">
    <a-switch
      :checked="firmware.firmware_status"
      :loading="loading"
      checked-children="可用"
      un-checked-children="禁用"
      @change="handleStatusChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Firmware } from '@/types/device-firmware'
import { changeFirmareStatus } from '@/api/manage'
import { ELocalStorageKey } from '@/types'
import { message } from 'ant-design-vue'

interface Props {
  firmware: Firmware
}

const props = defineProps<Props>()
const emit = defineEmits(['refresh'])

const loading = ref(false)
const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId) || ''

const handleStatusChange = async (checked: boolean) => {
  loading.value = true
  try {
    const res = await changeFirmareStatus(workspaceId, props.firmware.firmware_id, { status: checked })
    if (res.code === 0) {
      message.success('更新状态成功')
      emit('refresh')
    } else {
      message.error(res.message || '更新状态失败')
    }
  } catch (error: any) {
    message.error(error.message || '更新状态失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.firmware-status {
  display: flex;
  align-items: center;
}
</style>