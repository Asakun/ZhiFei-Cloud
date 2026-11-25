<script setup lang="ts">
import { useRouter, useRoute } from "vue-router";
import { ref, onMounted, reactive, computed, watch, onBeforeUnmount } from 'vue'
import { message } from 'ant-design-vue'
import {
  UserOutlined,
  DownOutlined,
  LockOutlined,
  CloseOutlined,
  LogoutOutlined,
  HomeOutlined,
  AimOutlined,
  NodeIndexOutlined,
  DatabaseOutlined,
  ClusterOutlined,
  EnvironmentOutlined,
  CloudServerOutlined,
  TeamOutlined,
  BarChartOutlined,
  AppstoreOutlined
} from '@ant-design/icons-vue'
import { EBizCode, ELocalStorageKey } from '@/types'
import { useConnectWebSocket } from '@/hooks/use-connect-websocket'
import { resetPassword, getLoginPublicKey, getUserInfo } from '@/api/manage'
import { rsaEncrypt } from '@/utils/rsa'
import EventBus from '@/event-bus'
import { useMyStore } from '@/store'
import GMapNew from "@/components/GMap_new.vue";
import { OsdThrottle } from '@/utils/osd-throttle'

const store = useMyStore()

// ── 智能优先级 OSD 节流器 ────────────────────────────────────────────────────────
// 优先级 1: 正在飞行的无人机 -> 100ms 刷新 (~10 FPS)
// 优先级 2: 当前 UI 列表页/可视范围内的设备 -> 150ms 刷新
// 优先级 3: 非当前页静止待机的设备 -> 1000ms 低频刷新
const gatewayOsdThrottle = new OsdThrottle((data) => store.commit('SET_GATEWAY_INFO', data))
const deviceOsdThrottle  = new OsdThrottle((data) => store.commit('SET_DEVICE_INFO',  data))
const dockOsdThrottle    = new OsdThrottle((data) => store.commit('SET_DOCK_INFO',    data))

// 监听首页广播的“当前 UI 界面可视 SN 列表”
const handleUpdateVisibleSns = (sns: string[]) => {
  if (Array.isArray(sns)) {
    gatewayOsdThrottle.setVisibleSns(sns)
    deviceOsdThrottle.setVisibleSns(sns)
    dockOsdThrottle.setVisibleSns(sns)
  }
}

EventBus.on('updateVisibleSns', handleUpdateVisibleSns)

onBeforeUnmount(() => {
  // 组件卸载时销毁节流器，清理 setInterval，防止内存泄漏
  EventBus.off('updateVisibleSns', handleUpdateVisibleSns)
  gatewayOsdThrottle.destroy()
  deviceOsdThrottle.destroy()
  dockOsdThrottle.destroy()
})
// ───────────────────────────────────────────────────────────────────────────────

const messageHandler = async (payload: any) => {
  if (!payload) {
    return
  }
  switch (payload.biz_code) {
    case EBizCode.DeviceUpgrade: {
      EventBus.emit('deviceUpgrade', payload)
      break
    }
    case EBizCode.DeviceLogUploadProgress: {
      EventBus.emit('deviceLogUploadProgress', payload)
      break
    }
    case EBizCode.GatewayOsd: {
      gatewayOsdThrottle.push(payload.data?.sn, payload.data, false)
      break
    }
    case EBizCode.DeviceOsd: {
      // isDeviceOsd = true，自动识别飞行状态并赋予 100ms 最高优先级
      deviceOsdThrottle.push(payload.data?.sn, payload.data, true)
      break
    }
    case EBizCode.DockOsd: {
      dockOsdThrottle.push(payload.data?.sn, payload.data, false)
      break
    }
    case EBizCode.MapElementCreate: {
      store.commit('SET_MAP_ELEMENT_CREATE', payload.data)
      break
    }
    case EBizCode.MapElementUpdate: {
      store.commit('SET_MAP_ELEMENT_UPDATE', payload.data)
      break
    }
    case EBizCode.MapElementDelete: {
      store.commit('SET_MAP_ELEMENT_DELETE', payload.data)
      break
    }
    case EBizCode.DeviceOnline: {
      store.commit('SET_DEVICE_ONLINE', payload.data)
      EventBus.emit('deviceTopologyChanged', { status: 'online', data: payload.data })
      break
    }
    case EBizCode.DeviceOffline: {
      store.commit('SET_DEVICE_OFFLINE', payload.data)
      EventBus.emit('deviceTopologyChanged', { status: 'offline', data: payload.data })
      break
    }
    case EBizCode.FlightTaskProgress:
    case EBizCode.FlightTaskMediaProgress:
    case EBizCode.FlightTaskMediaHighestPriority: {
      EventBus.emit('flightTaskWs', payload)
      break
    }
    case EBizCode.DeviceHms: {
      store.commit('SET_DEVICE_HMS_INFO', payload.data)
      break
    }
    case EBizCode.DeviceReboot:
    case EBizCode.DroneOpen:
    case EBizCode.DroneClose:
    case EBizCode.CoverOpen:
    case EBizCode.CoverClose:
    case EBizCode.PutterOpen:
    case EBizCode.PutterClose:
    case EBizCode.ChargeOpen:
    case EBizCode.ChargeClose:
    case EBizCode.DeviceFormat:
    case EBizCode.DroneFormat:
      {
        store.commit('SET_DEVICES_CMD_EXECUTE_INFO', {
          biz_code: payload.biz_code,
          timestamp: payload.timestamp,
          ...payload.data,
        })
        break
      }
    case EBizCode.ControlSourceChange:
    case EBizCode.FlyToPointProgress:
    case EBizCode.TakeoffToPointProgress:
    case EBizCode.JoystickInvalidNotify:
    case EBizCode.DrcStatusNotify:
    case EBizCode.PSDKFloatingWindowText:
      {
        EventBus.emit('droneControlWs', payload)
        break
      }
    case EBizCode.FlightAreasSyncProgress: {
      EventBus.emit('flightAreasSyncProgressWs', payload.data)
      break
    }
    case EBizCode.FlightAreasDroneLocation: {
      EventBus.emit('flightAreasDroneLocationWs', payload)
      break
    }
    case EBizCode.FlightAreasUpdate: {
      EventBus.emit('flightAreasUpdateWs', payload.data)
      break
    }
    case EBizCode.Authority_Revoked: {
      EventBus.emit('droneControlWs', payload)
      break
    }
    case EBizCode.Authority_Exit: {
      EventBus.emit('droneControlWs', payload)
      break
    }
    default:
      break
  }
}

// 监听ws 消息
useConnectWebSocket(messageHandler)



const router = useRouter();
const currentRoute = useRoute();
const isAdmin = ref(false)

const routes = computed(() => {
  // 优先取 router.options.routes 保持嵌套层级树，防止 vue-router4 的 getRoutes() 扁平化导致 children 为空
  const airControlConfig = router.options?.routes?.find(r => r.name === 'airControl')
  const allRoutes = airControlConfig?.children || router.getRoutes().find(route => 'airControl' === route.name)?.children || []
  return allRoutes.filter(item => {
    if (item.meta?.show === false) return false
    if (item.meta?.requiresAdmin && !isAdmin.value) return false
    return true
  })
})

const username = ref('')
const showUserMenu = ref(false)

onMounted(async () => {
  username.value = localStorage.getItem(ELocalStorageKey.Username) || '未知用户'
  try {
    const res = await getUserInfo()
    if (res.code === 0 && res.data?.role_name === '管理员') {
      isAdmin.value = true
    }
  } catch (e) {
    console.error('获取用户信息失败:', e)
  }
})

const handleLogout = () => {
  // 清除本地存储
  localStorage.removeItem(ELocalStorageKey.Token)
  localStorage.removeItem(ELocalStorageKey.WorkspaceId)
  localStorage.removeItem(ELocalStorageKey.Username)
  localStorage.removeItem(ELocalStorageKey.UserId)
  localStorage.removeItem(ELocalStorageKey.Flag)

  message.success('退出登录成功')
  // 跳转到登录页
  router.push('/')
}

const handleMenuClick = ({ key }: { key: string }) => {
  if (key === 'logout') {
    handleLogout()
  }
}

// Reset Password Logic
const showResetModal = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')

const openResetModal = () => {
  oldPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  showResetModal.value = true
  showUserMenu.value = false
}

const closeResetModal = () => {
  showResetModal.value = false
}

const handleResetConfirm = async () => {
  if (!oldPassword.value) {
    message.warning('请输入旧密码')
    return
  }
  if (!newPassword.value) {
    message.warning('请输入新密码')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    message.warning('两次输入的新密码不一致')
    return
  }

  try {
    // Fetch Public Key
    const keyRes = await getLoginPublicKey()
    const publicKey = keyRes.data
    if (!publicKey) {
      message.error('获取加密密钥失败')
      return
    }

    const encOld = rsaEncrypt(oldPassword.value, publicKey)
    const encNew = rsaEncrypt(newPassword.value, publicKey)

    if (!encOld || !encNew) {
      message.error('加密失败')
      return
    }

    const res = await resetPassword({
      old_password: encOld,
      new_password: encNew
    })
    if (res.code === 0) {
      message.success('密码重置成功，请重新登录')
      closeResetModal()
      handleLogout()
    } else {
      message.error(res.message || '重置失败')
    }
  } catch (e) {
    console.error(e)
    message.error('请求失败')
  }
}

// Menu Dropdown Logic (Fixed Position via Teleport)
const menuHoverState = reactive({
  visible: false,
  top: 0,
  left: 0,
  children: [] as any[],
  timer: null as any
})

const handleMenuEnter = (e: MouseEvent, children: any[]) => {
  if (menuHoverState.timer) clearTimeout(menuHoverState.timer)

  // 过滤掉隐藏的子菜单
  const visibleChildren = children?.filter((child: any) => child.meta?.show !== false) || []

  if (!visibleChildren.length) {
    menuHoverState.visible = false
    return
  }

  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  menuHoverState.left = rect.left
  menuHoverState.top = rect.bottom
  menuHoverState.children = visibleChildren
  menuHoverState.visible = true
}

const handleMenuLeave = () => {
  menuHoverState.timer = setTimeout(() => {
    menuHoverState.visible = false
  }, 200)
}

const handleDropdownEnter = () => {
  if (menuHoverState.timer) clearTimeout(menuHoverState.timer)
}

const handleDropdownLeave = () => {
  menuHoverState.timer = setTimeout(() => {
    menuHoverState.visible = false
  }, 200)
}

// 点击含有子菜单的顶层菜单项时，自动导航到其首个可见子路由
const handleParentClick = (route: any) => {
  menuHoverState.visible = false
  if (menuHoverState.timer) clearTimeout(menuHoverState.timer)
  if (route.children && route.children.length) {
    const visibleChild = route.children.find((c: any) => c.meta?.show !== false)
    if (visibleChild) {
      if (visibleChild.name) {
        router.push({ name: visibleChild.name })
      } else if (visibleChild.path) {
        router.push(visibleChild.path)
      }
      return
    }
  }
  if (route.path) {
    router.push(route.path)
  }
}

const handleChildClick = () => {
  menuHoverState.visible = false
  if (menuHoverState.timer) clearTimeout(menuHoverState.timer)
}

// 监听路由变更，随时清除悬浮下拉状态与定时器，防止浮层残存遮挡点击
watch(() => currentRoute.fullPath, () => {
  menuHoverState.visible = false
  showUserMenu.value = false
  if (menuHoverState.timer) {
    clearTimeout(menuHoverState.timer)
    menuHoverState.timer = null
  }
})

onBeforeUnmount(() => {
  if (menuHoverState.timer) {
    clearTimeout(menuHoverState.timer)
    menuHoverState.timer = null
  }
})

// 菜单项图标映射
const getMenuIcon = (name: string) => {
  const icons: Record<string, any> = {
    '首页': HomeOutlined,
    '指挥调度': AimOutlined,
    '航线规划': NodeIndexOutlined,
    '数据管理': DatabaseOutlined,
    '基站管理': ClusterOutlined,
    '地图标注': EnvironmentOutlined,
    '固件管理库': CloudServerOutlined,
    '用户管理': TeamOutlined,
    '应用统计': BarChartOutlined
  }
  return icons[name] || AppstoreOutlined
}
</script>

<template>
  <div class="home-container">
    <!-- 标题栏 -->
    <header class="header">
      <!-- 左侧：平台标识 -->
      <div class="header-brand" @click="router.push('/airHome')">
        <div class="brand-emblem" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" class="w-4 h-4 text-cyan-300">
            <path d="M12 10a2 2 0 100 4 2 2 0 000-4z" fill="currentColor" />
            <path d="M4.5 7.5l4 2.5M19.5 7.5l-4 2.5M4.5 16.5l4-2.5M19.5 16.5l-4-2.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            <circle cx="4.5" cy="7.5" r="2.2" stroke="currentColor" stroke-width="1.4" />
            <circle cx="19.5" cy="7.5" r="2.2" stroke="currentColor" stroke-width="1.4" />
            <circle cx="4.5" cy="16.5" r="2.2" stroke="currentColor" stroke-width="1.4" />
            <circle cx="19.5" cy="16.5" r="2.2" stroke="currentColor" stroke-width="1.4" />
          </svg>
        </div>
        <span class="brand-title">无人机智飞控制平台</span>
      </div>

      <!-- 中间：主导航栏 -->
      <nav class="menu">
        <div class="menu-track">
          <template v-for="route in routes" :key="route.path">
            <div v-if="route.children && route.children.length" class="menu-item cursor-pointer"
              :class="{ 'active': currentRoute.matched.some(r => r.name === route.name) }"
              @mouseenter="handleMenuEnter($event, route.children)" @mouseleave="handleMenuLeave"
              @click="handleParentClick(route)">
              <component :is="getMenuIcon(String(route.name || ''))" class="menu-icon" />
              <span>{{ route.name }}</span>
            </div>
            <router-link v-else :to="route.path" class="menu-item" active-class="active"
              @mouseenter="handleMenuEnter($event, [])" @mouseleave="handleMenuLeave"
              @click="handleChildClick">
              <component :is="getMenuIcon(String(route.name || ''))" class="menu-icon" />
              <span>{{ route.name }}</span>
            </router-link>
          </template>
        </div>
      </nav>

      <!-- 右侧：用户菜单 -->
      <div class="header-actions">
        <div class="relative">
          <div class="user-trigger" :class="{ 'is-open': showUserMenu }" @click="showUserMenu = !showUserMenu">
            <span class="user-avatar">
              <UserOutlined class="text-[12px]" />
            </span>
            <span class="user-name">{{ username }}</span>
            <DownOutlined class="text-[10px] text-slate-400 transition-transform duration-300"
              :class="{ 'rotate-180 text-cyan-300': showUserMenu }" />
          </div>

          <!-- Dropdown Menu -->
          <Transition name="menu-fade">
            <div v-if="showUserMenu"
              class="absolute right-0 top-full mt-2 w-40 bg-[#081432]/95 backdrop-blur-md border border-sky-400/30 rounded-lg shadow-[0_12px_32px_rgba(2,6,23,0.7)] overflow-hidden py-1 z-[100]">
              <div @click="openResetModal"
                class="px-3.5 py-2 text-slate-300 text-[13px] hover:bg-sky-500/15 hover:text-white cursor-pointer transition-all flex items-center gap-2.5 select-none">
                <LockOutlined class="text-[14px] text-sky-400" />
                <span>重置密码</span>
              </div>
              <div @click="handleLogout"
                class="px-3.5 py-2 text-slate-300 text-[13px] hover:bg-rose-500/15 hover:text-rose-300 cursor-pointer transition-all flex items-center gap-2.5 select-none border-t border-sky-400/10">
                <LogoutOutlined class="text-[14px]" />
                <span>退出登录</span>
              </div>
            </div>
          </Transition>

          <!-- Click outside overlay (transparent) -->
          <div v-if="showUserMenu" class="fixed inset-0 z-[90]" @click="showUserMenu = false"></div>
        </div>
      </div>
    </header>

    <!-- Reset Password Modal -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="showResetModal"
          class="fixed inset-0 z-[1000] flex items-center justify-center bg-[#030914]/80 backdrop-blur-md"
          @click.self="closeResetModal">
          <div
            class="modal-container bg-[#0a1537]/80 backdrop-blur-xl border border-[#00ee8b]/30 rounded-2xl shadow-[0_0_40px_rgba(0,238,139,0.15)] w-[22rem] overflow-hidden relative transition-all duration-300">
            <!-- Decorative Top Bar -->
            <div
              class="absolute top-0 left-0 right-0 h-[3px] bg-gradient-to-r from-transparent via-[#00ee8b] to-[#00d4ff] opacity-90">
            </div>

            <!-- Close Button -->
            <button @click="closeResetModal"
              class="absolute right-4 top-4 w-8 h-8 rounded-full flex items-center justify-center text-gray-400 hover:text-white hover:bg-white/10 transition-all">
              <CloseOutlined style="font-size: 13px" />
            </button>

            <div class="p-6">
              <!-- Header Section -->
              <div class="flex flex-col items-center mb-6">
                <div
                  class="w-14 h-14 bg-gradient-to-br from-[#00ee8b]/20 to-[#00d4ff]/20 rounded-xl flex items-center justify-center mb-3 border border-[#00ee8b]/30 shadow-[0_0_20px_rgba(0,238,139,0.2)]">
                  <LockOutlined class="text-[#00ee8b] text-xl drop-shadow-[0_0_8px_rgba(0,238,139,0.8)]" />
                </div>
                <h3 class="text-white text-lg font-bold tracking-widest">修改密码</h3>
                <p class="text-gray-400 text-[11px] mt-1">请输入当前密码以验证并设置新密码</p>
              </div>

              <!-- Inputs Section -->
              <div class="space-y-4 mb-7">
                <div class="space-y-1.5">
                  <label class="text-gray-300 text-[12px] font-medium tracking-wide ml-1">当前密码</label>
                  <div class="relative flex items-center group">
                    <span class="absolute left-3 text-gray-500 group-focus-within:text-[#00ee8b] transition-colors pointer-events-none">
                      <LockOutlined style="font-size: 13px" />
                    </span>
                    <input v-model="oldPassword" type="password" placeholder="请输入旧密码"
                      class="w-full bg-[#111c3a]/50 border border-white/10 focus:border-[#00ee8b] focus:bg-[#111c3a]/80 focus:shadow-[0_0_12px_rgba(0,238,139,0.2)] rounded-lg pl-9 pr-3 py-2.5 text-white outline-none transition-all placeholder-gray-500 text-[13px]" />
                  </div>
                </div>

                <div class="space-y-1.5">
                  <label class="text-gray-300 text-[12px] font-medium tracking-wide ml-1">设置新密码</label>
                  <div class="relative flex items-center group">
                    <span class="absolute left-3 text-gray-500 group-focus-within:text-[#00ee8b] transition-colors pointer-events-none">
                      <LockOutlined style="font-size: 13px" />
                    </span>
                    <input v-model="newPassword" type="password" placeholder="请输入新密码"
                      class="w-full bg-[#111c3a]/50 border border-white/10 focus:border-[#00ee8b] focus:bg-[#111c3a]/80 focus:shadow-[0_0_12px_rgba(0,238,139,0.2)] rounded-lg pl-9 pr-3 py-2.5 text-white outline-none transition-all placeholder-gray-500 text-[13px]" />
                  </div>
                </div>

                <div class="space-y-1.5">
                  <label class="text-gray-300 text-[12px] font-medium tracking-wide ml-1">确认新密码</label>
                  <div class="relative flex items-center group">
                    <span class="absolute left-3 text-gray-500 group-focus-within:text-[#00ee8b] transition-colors pointer-events-none">
                      <LockOutlined style="font-size: 13px" />
                    </span>
                    <input v-model="confirmPassword" type="password" placeholder="请再次输入新密码"
                      class="w-full bg-[#111c3a]/50 border border-white/10 focus:border-[#00ee8b] focus:bg-[#111c3a]/80 focus:shadow-[0_0_12px_rgba(0,238,139,0.2)] rounded-lg pl-9 pr-3 py-2.5 text-white outline-none transition-all placeholder-gray-500 text-[13px]" />
                  </div>
                </div>
              </div>

              <!-- Action Buttons -->
              <div class="flex gap-3">
                <button @click="closeResetModal"
                  class="flex-1 px-3 py-2.5 rounded-lg border border-white/10 bg-white/5 text-gray-300 font-medium hover:bg-white/10 hover:text-white transition-all active:scale-[0.98] text-[12px] tracking-wide">
                  取消
                </button>
                <button @click="handleResetConfirm"
                  class="flex-1 px-3 py-2.5 rounded-lg bg-gradient-to-r from-[#00ee8b] to-[#00d4ff] text-[#041428] font-bold hover:shadow-[0_0_20px_rgba(0,238,139,0.4)] transition-all active:scale-[0.98] text-[12px] tracking-wide">
                  确认修改
                </button>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Global Menu Dropdown Teleport -->
    <Teleport to="body">
      <div v-if="menuHoverState.visible"
        class="fixed z-[9999] min-w-[8.5rem] bg-[#081432]/95 backdrop-blur-md border border-sky-400/30 rounded-lg shadow-[0_12px_30px_rgba(2,6,23,0.7)] overflow-hidden py-1.5"
        :style="{ top: menuHoverState.top + 4 + 'px', left: menuHoverState.left + 'px' }" @mouseenter="handleDropdownEnter"
        @mouseleave="handleDropdownLeave">
        <router-link v-for="child in menuHoverState.children" :key="child.path" :to="{ name: child.name }"
          class="block px-3.5 py-2 text-slate-200 font-medium text-[13px] hover:bg-sky-500/15 hover:text-cyan-300 transition-colors text-center no-underline whitespace-nowrap"
          active-class="bg-sky-500/20 text-cyan-300 font-semibold" @click="handleChildClick">
          {{ child.name }}
        </router-link>
      </div>
    </Teleport>

    <!-- 主体内容 -->
    <main class="content">
      <router-view v-slot="{ Component }">
        <component :is="Component" :key="currentRoute.fullPath" />
      </router-view>
    </main>
    <GMapNew />
    <!--    <GMap/>-->
  </div>
</template>

<style scoped lang="scss">
/* 全局样式 */
.home-container {
  min-height: 100vh;
  background-color: #03142A;
  //background-color: #ffffff;
}

/* 标题栏 */
.header {
  position: relative;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  width: 100%;
  height: 4.1875rem;
  padding: 0 1.25rem;
  background:
    radial-gradient(ellipse 32% 140% at 8% 50%, rgba(14, 165, 233, 0.2) 0%, rgba(30, 64, 175, 0.12) 55%, transparent 100%),
    linear-gradient(180deg, #0d1f4d 0%, #081538 55%, #06102b 100%);
  border-bottom: 1px solid rgba(56, 189, 248, 0.28);
  color: white;
  box-shadow: 0 4px 20px rgba(2, 6, 23, 0.45);

  &::after {
    content: '';
    position: absolute;
    left: 0;
    bottom: -1px;
    width: 100%;
    height: 1px;
    pointer-events: none;
    background: linear-gradient(90deg, rgba(56, 189, 248, 0.9) 0%, rgba(56, 189, 248, 0.45) 35%, rgba(120, 152, 227, 0.15) 75%, transparent 100%);
  }
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  flex-shrink: 0;
  cursor: pointer;
  user-select: none;
  padding-right: 0.5rem;
}

.brand-emblem {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.125rem;
  height: 2.125rem;
  border-radius: 9px;
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.28) 0%, rgba(37, 99, 235, 0.16) 100%);
  border: 1px solid rgba(56, 189, 248, 0.45);
  box-shadow: 0 0 14px rgba(14, 165, 233, 0.22), inset 0 1px 0 rgba(255, 255, 255, 0.18);
  transition: transform 0.25s ease, box-shadow 0.25s ease;

  .header-brand:hover & {
    transform: translateY(-1px);
    box-shadow: 0 0 18px rgba(56, 189, 248, 0.38);
  }
}

.brand-title {
  font-size: clamp(1rem, 1.2vw, 1.25rem);
  font-weight: 700;
  letter-spacing: 0.06em;
  white-space: nowrap;
  background: linear-gradient(180deg, #ffffff 20%, #cce8ff 70%, #7dd3fc 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 0 10px rgba(56, 189, 248, 0.35));
}

.menu {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.menu-track {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  max-width: 100%;
  padding: 0.25rem;
  border-radius: 10px;
  background: rgba(6, 17, 45, 0.65);
  border: 1px solid rgba(56, 189, 248, 0.16);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
  overflow-x: auto;
  overflow-y: hidden;

  &::-webkit-scrollbar {
    height: 3px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(56, 189, 248, 0.3);
    border-radius: 2px;
  }

  scrollbar-width: thin;
  scrollbar-color: rgba(56, 189, 248, 0.3) transparent;
}

.menu-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.38rem;
  flex-shrink: 0;
  height: 2.15rem;
  padding: 0 0.78rem;
  border-radius: 7px;
  border: 1px solid transparent;
  font-size: 0.84rem;
  font-weight: 500;
  letter-spacing: 0.02em;
  color: #94a3b8;
  text-decoration: none;
  white-space: nowrap;
  transition: color 0.2s ease, background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;

  .menu-icon {
    font-size: 0.88rem;
    opacity: 0.8;
    transition: transform 0.2s ease, color 0.2s ease, opacity 0.2s ease;
  }

  &:hover {
    color: #f8fafc;
    background: rgba(56, 189, 248, 0.1);
    border-color: rgba(56, 189, 248, 0.24);

    .menu-icon {
      opacity: 1;
      color: #7dd3fc;
    }
  }

  &.active {
    color: #ffffff;
    font-weight: 600;
    background: linear-gradient(180deg, rgba(14, 165, 233, 0.28) 0%, rgba(37, 99, 235, 0.2) 100%);
    border-color: rgba(56, 189, 248, 0.48);
    box-shadow: 0 0 14px rgba(14, 165, 233, 0.22), inset 0 1px 0 rgba(255, 255, 255, 0.16);

    .menu-icon {
      opacity: 1;
      color: #38bdf8;
    }

    &::after {
      content: '';
      position: absolute;
      left: 22%;
      right: 22%;
      bottom: -1px;
      height: 2px;
      border-radius: 2px;
      background: #38bdf8;
      box-shadow: 0 0 8px #38bdf8;
    }
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-shrink: 0;
  position: relative;
  z-index: 70;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  height: 2.15rem;
  padding: 0 0.72rem 0 0.45rem;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.55);
  border: 1px solid rgba(120, 152, 227, 0.26);
  cursor: pointer;
  user-select: none;
  transition: all 0.2s ease;

  &:hover,
  &.is-open {
    background: rgba(30, 58, 138, 0.35);
    border-color: rgba(56, 189, 248, 0.45);
  }
}

.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.45rem;
  height: 1.45rem;
  border-radius: 6px;
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.28), rgba(59, 130, 246, 0.22));
  border: 1px solid rgba(56, 189, 248, 0.35);
  color: #7dd3fc;
}

.user-name {
  color: #e2e8f0;
  font-size: 0.8125rem;
  font-weight: 500;
  max-width: 6rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 用户信息区域 */
.user-info {
  position: absolute;
  right: 2rem;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.8rem;
  background: rgba(120, 152, 227, 0.08);
  border-radius: 4px;
  border: 1px solid rgba(120, 152, 227, 0.2);
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: rgba(120, 152, 227, 0.15);
    border-color: rgba(120, 152, 227, 0.4);
  }

  .user-icon {
    color: #7898E3;
    font-size: 0.875rem;
  }

  .username {
    color: #7898E3;
    font-size: 0.8125rem;
    font-weight: 400;
    max-width: 100px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .dropdown-icon {
    color: #7898E3;
    font-size: 0.625rem;
    transition: transform 0.3s ease;
  }
}

/* 下拉菜单样式 */
:deep(.user-dropdown-menu) {
  background: rgba(10, 21, 55, 0.95);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(120, 152, 227, 0.3);
  border-radius: 4px;
  padding: 0.25rem 0;

  .ant-dropdown-menu-item {
    color: #7898E3;
    font-size: 0.8125rem;
    padding: 0.5rem 1rem;
    transition: all 0.3s ease;

    &:hover {
      background: rgba(120, 152, 227, 0.15);
      color: #ffffff;
    }
  }
}


/* 主体内容 */
.content {
  // padding: 20px;
  color: white;
  height: calc(100vh - 4.1875rem);
  // margin-top: -20px;
}

/* Modal Transitions */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);

  .modal-container {
    transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
  }
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;

  .modal-container {
    transform: scale(0.9) translateY(20px);
    opacity: 0;
  }
}

.modal-fade-enter-to,
.modal-fade-leave-from {
  opacity: 1;

  .modal-container {
    transform: scale(1) translateY(0);
    opacity: 1;
  }
}

/* Menu Transitions */
.menu-fade-enter-active,
.menu-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.menu-fade-enter-from,
.menu-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.menu-fade-enter-to,
.menu-fade-leave-from {
  opacity: 1;
  transform: translateY(0);
}
</style>
