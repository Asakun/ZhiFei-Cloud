import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { ELocalStorageKey, ERouterName } from '@/types/index'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    redirect: '/' + ERouterName.PROJECT
  },
  // Web 登录页
  {
    path: '/' + ERouterName.PROJECT,
    name: ERouterName.PROJECT,
    component: () => import('@/pages/page-web/index.vue')
  },
  {
    path: '/payload-viewer',
    name: 'payloadViewer',
    component: () => import('@/pages/airControl/command/PayloadViewer.vue')
  },
  // 新飞控主系统
  {
    path: '/airControl',
    name: 'airControl',
    redirect: '/airHome',
    component: () => import('@/pages/airControl/index.vue'),
    children: [
      {
        path: '/airHome',
        name: '首页',
        component: () => import('@/pages/airControl/home/index.vue'),
      },
      {
        path: '/command',
        name: '指挥调度',
        component: () => import('@/pages/airControl/command/index.vue'),
      },
      {
        path: '/route-plan',
        name: '航线规划',
        component: () => import('@/pages/airControl/routePlan/index.vue'),
      },
      {
        path: '/data-mgmt',
        name: '数据管理',
        component: () => import('@/pages/airControl/dataMgmt/index.vue'),
      },
      {
        path: '/droneManage',
        name: '基站管理',
        component: () => import('@/pages/airControl/droneManage/index.vue'),
      },
      {
        path: '/layerManage',
        name: '地图标注',
        component: () => import('@/pages/airControl/layerManage/index.vue'),
      },
      {
        path: '/deviceFireware',
        name: '固件管理库',
        component: () => import('@/pages/airControl/deviceFireware/index.vue'),
      },
      {
        path: '/userManage',
        name: '用户管理',
        component: () => import('@/pages/airControl/userManage/index.vue'),
      },
      {
        path: '/srsManage',
        name: '媒体流管理',
        meta: {
          requiresAdmin: true
        },
        component: () => import('@/pages/airControl/srsManage/index.vue'),
      },
      {
        path: '/settings',
        name: '应用统计',
        component: () => import('@/pages/airControl/settings/index.vue'),
      },
      {
        path: '/apiKeyManage',
        name: 'API密钥管理',
        meta: {
          show: false
        },
        component: () => import('@/pages/airControl/apiKeyManage/index.vue'),
      },
      {
        path: '/login-fail-ip',
        name: 'IP安全管理',
        meta: {
          show: false
        },
        component: () => import('@/pages/airControl/loginFailIp/index.vue'),
      },
      {
        path: '/command/create-plan',
        name: '新建计划',
        meta: {
          show: false
        },
        component: () => import('@/pages/airControl/command/CreatePlan.vue'),
      }
    ]
  },
  {
    path: '/virtualCockpit',
    name: 'virtualCockpit',
    component: () => import('@/pages/airControl/cockpit/virtualCockpit.vue'),
  },
  {
    path: '/virtualCockpit1',
    name: 'virtualCockpit1',
    component: () => import('@/pages/airControl/cockpit/virtualCockpit1.vue'),
  },
  // 轨迹回放
  {
    path: '/wayLineRecord',
    name: 'wayLineRecord',
    component: () => import('@/components/wayLineRecord.vue'),
  },
  // 航线新增
  {
    path: '/newWayLine',
    name: 'newWayLine',
    component: () => import('@/components/wayLine/newWayLine.vue'),
  },
  // pilot
  {
    path: '/' + ERouterName.PILOT,
    name: ERouterName.PILOT,
    component: () => import('@/pages/page-pilot/pilot-index.vue'),
  },
  {
    path: '/' + ERouterName.PILOT_HOME,
    component: () => import('@/pages/page-pilot/pilot-home.vue')
  },
  {
    path: '/' + ERouterName.PILOT_MEDIA,
    component: () => import('@/pages/page-pilot/pilot-media.vue')
  },
  {
    path: '/' + ERouterName.PILOT_LIVESHARE,
    component: () => import('@/pages/page-pilot/pilot-liveshare.vue')
  },
  {
    path: '/' + ERouterName.PILOT_BIND,
    component: () => import('@/pages/page-pilot/pilot-bind.vue')
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// 不需要登录即可访问的路由白名单
const WHITE_LIST = [
  '/' + ERouterName.PROJECT, // Web 登录页 (/project)
  '/' + ERouterName.PILOT,   // Pilot 登录页 (/pilot-login)
]

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem(ELocalStorageKey.Token)

  // 白名单路由直接放行
  if (WHITE_LIST.includes(to.path)) {
    next()
    return
  }

  // 未登录且目标不在白名单中，跳转到 Web 登录页
  if (!token) {
    next({ path: '/' + ERouterName.PROJECT })
    return
  }

  // 权限控制：如果路由需要管理员权限
  if (to.meta?.requiresAdmin) {
    import('@/api/manage').then(async ({ getUserInfo }) => {
      try {
        const res = await getUserInfo()
        if (res.code === 0 && res.data?.role_name === '管理员') {
          next()
        } else {
          next({ path: '/' + ERouterName.PROJECT })
        }
      } catch (e) {
        console.error(e)
        next({ path: '/' + ERouterName.PROJECT })
      }
    }).catch((e) => {
      console.error(e)
      next({ path: '/' + ERouterName.PROJECT })
    })
    return
  }

  // 已登录且有权限，正常放行
  next()
})

export default router
