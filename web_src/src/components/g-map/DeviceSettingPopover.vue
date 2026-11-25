<template>
  <a-popover :open="state.sVisible" trigger="click" v-bind="$attrs" :overlay-class-name="overlayClassName"
    placement="bottom" @openChange=";" v-on="$attrs">
    <template #content>
      <div class="title-content">
      </div>
      <slot name="formContent" />
      <div class="uranus-popconfirm-btns">
        <a-button size="sm" @click="onCancel">
          {{ cancelText || '取消' }}
        </a-button>
        <a-button size="sm" :loading="loading" type="primary" class="confirm-btn" @click="onConfirm">
          {{ okText || '确定' }}
        </a-button>
      </div>
    </template>
    <template v-if="$slots.default">
      <slot></slot>
    </template>
  </a-popover>
</template>

<script lang="ts" setup>
import { defineProps, defineEmits, reactive, watch, computed } from 'vue'

const props = defineProps<{
  visible?: boolean,
  loading?: Boolean,
  disabled?: Boolean,
  title?: String,
  okText?: String,
  cancelText?: String,
  width?: Number,
}>()

const emit = defineEmits(['cancel', 'confirm'])

const state = reactive({
  sVisible: false,
  loading: false,
})

watch(() => props.visible, (val) => {
  state.sVisible = val || false
})

const loading = computed(() => {
  return props.loading
})
const okLabel = computed(() => {
  return props.loading ? '' : '确定'
})

const overlayClassName = computed(() => {
  const classList = ['device-setting-popconfirm']
  return classList.join(' ')
})

function onConfirm(e: Event) {
  if (props.disabled) {
    return
  }
  emit('confirm', e)
}

function onCancel(e: Event) {
  state.sVisible = false
  emit('cancel', e)
}

</script>

<style lang="scss">
.device-setting-popconfirm {
  z-index: 9999 !important;

  .ant-popover-inner {
    background: rgba(4, 22, 58, 0.95);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    border: 1px solid rgba(255, 255, 255, 0.15);
    box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.4);
    border-radius: 8px;
    color: #fff;
    padding: 12px 16px;
  }

  .ant-popover-arrow {
    display: none; // Hide arrow for cleaner look or style it if needed
  }

  .ant-popover-inner-content {
    color: rgba(255, 255, 255, 0.9);
  }

  .form-content {
    display: flex;
    align-items: center;
    margin-bottom: 12px;
    font-size: 14px;

    .form-label {
      padding-right: 12px;
      color: rgba(255, 255, 255, 0.8);
      white-space: nowrap;
    }

    // Ant Design Override for dark theme inputs within popover
    .ant-input-number {
      background-color: rgba(255, 255, 255, 0.1);
      border-color: rgba(255, 255, 255, 0.2);
      color: #fff;

      input {
        color: #fff;
      }
    }
  }

  .uranus-popconfirm-btns {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    border-top: 1px solid rgba(255, 255, 255, 0.1);
    padding-top: 12px;
    margin-top: 4px;

    .ant-btn {
      height: 28px;
      padding: 0 12px;
      border-radius: 4px;
      border: 1px solid rgba(255, 255, 255, 0.2);
      background: transparent;
      color: rgba(255, 255, 255, 0.8);
      font-size: 13px;

      &:hover {
        color: #fff;
        border-color: rgba(255, 255, 255, 0.4);
      }
    }

    .confirm-btn {
      background: #2b85e4;
      border-color: #2b85e4;
      color: #fff;

      &:hover {
        background: #5cadff;
        border-color: #5cadff;
      }

      &.ant-btn-loading {
        opacity: 0.7;
      }
    }
  }
}
</style>
