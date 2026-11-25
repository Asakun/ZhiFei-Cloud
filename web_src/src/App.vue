<template>

  <div class="demo-app">
    <a-config-provider :locale="zhCN">
      <router-view />
    </a-config-provider>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, ref } from 'vue'
import { useMyStore } from './store'
import zhCN from 'ant-design-vue/es/locale/zh_CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';

dayjs.locale('zh-cn');

export default defineComponent({
  name: 'App',

  setup() {
    const store = useMyStore()
    return {
      store,
      zhCN
    }
  }
})
</script>
<style lang="scss" scoped>
.demo-app {
  width: 100%;
  height: 100%;

  .map-wrapper {
    height: 100%;
    width: 100%;
  }
}
</style>

<style lang="scss">
#demo-app {
  width: 100%;
  height: 100%
}
</style>
