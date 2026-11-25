<template>
  <div class="relative">
    <a-button type="primary" size="small" @click="toggleVisible">
      {{ visible ? '关闭机场直播' : '机场直播' }}
    </a-button>

    <div v-if="visible"
      class="absolute top-full left-0 mt-2 z-[100] w-[400px] bg-gray-900/95 backdrop-blur-lg rounded-xl shadow-2xl border border-gray-600 flex flex-col overflow-hidden">
      <div class="absolute top-2 right-2 cursor-pointer text-gray-400 hover:text-white transition-colors p-1 z-10"
        @click="close">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
        </svg>
      </div>
      <div class="p-2 border-b border-gray-700 flex-shrink-0">
        <span class="text-white text-xs font-medium px-1">机场直播</span>
      </div>
      <div class="w-full h-[300px] bg-black flex-1">
        <webRtcPlay v-if="visible" :dockId="`${dockId}/165-0-7/normal-0`" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import webRtcPlay from '@/components/webRtcPlay.vue'

defineProps<{
  dockId: string
}>()

const visible = ref(false)

const toggleVisible = () => {
  visible.value = !visible.value
}

const close = () => {
  visible.value = false
}
</script>
