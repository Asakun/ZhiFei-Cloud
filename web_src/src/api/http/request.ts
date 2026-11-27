import axios from 'axios'
import { uuidv4 } from '@/utils/uuid'
import { CURRENT_CONFIG } from './config'
import { message } from 'ant-design-vue'
import router from '@/router'
import { ELocalStorageKey, ERouterName, EUserType } from '@/types/enums'
export * from './type'

const REQUEST_ID = 'X-Request-Id'
function getAuthToken() {
  return localStorage.getItem(ELocalStorageKey.Token)
}

// 防止多个并发请求同时触发 401 重定向
let isRedirectingToLogin = false
let isShowingNetworkError = false

const instance = axios.create({
  // withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
  // timeout: 12000,
})

instance.interceptors.request.use(
  config => {
    config.headers[ELocalStorageKey.Token] = getAuthToken()
    // config.headers[REQUEST_ID] = uuidv4()
    config.baseURL = CURRENT_CONFIG.baseURL
    return config
  },
  error => {
    return Promise.reject(error)
  },
)

instance.interceptors.response.use(
  response => {
    // console.info('URL: ' + response.config.baseURL + response.config.url, '\nData: ', response.data, '\nResponse:', response)
    if (response.config.responseType !== 'blob' && response.data.code && response.data.code !== 0) {
      // message.error(response.data.message)
    }
    return response
  },
  err => {
    const requestId = err?.config?.headers && err?.config?.headers[REQUEST_ID]
    if (requestId) {
      console.info(REQUEST_ID, '：', requestId)
    }
    console.info('url: ', err?.config?.url, `【${err?.config?.method}】 \n>>>> err: `, err)

    let description = '-'
    if (err.response?.data && err.response.data.message) {
      description = err.response.data.message
    }
    if (err.response?.data && err.response.data.result) {
      description = err.response.data.result.message
    }
    // @See: https://github.com/axios/axios/issues/383
    if (!err.response || !err.response.status) {
      if (!isShowingNetworkError) {
        isShowingNetworkError = true
        message.error('The network is abnormal, please check the backend service and try again')
        setTimeout(() => {
          isShowingNetworkError = false
        }, 3000)
      }
      return
    }

    // 401 认证失败：静默处理，不弹出错误码，直接跳转登录页
    if (err.response?.status === 401) {
      if (!isRedirectingToLogin) {
        isRedirectingToLogin = true
        console.error('认证已过期，正在跳转到登录页...', err.response)

        // 清除登录状态
        localStorage.removeItem(ELocalStorageKey.Token)

        // 给用户友好提示
        message.warning('登录已过期，请重新登录')

        const flag: number = Number(localStorage.getItem(ELocalStorageKey.Flag))
        switch (flag) {
          case EUserType.Pilot:
            router.push(ERouterName.PILOT)
            break
          default:
            router.push(ERouterName.PROJECT)
            break
        }

        // 延迟重置标志，确保短时间内的并发401请求不会重复触发
        setTimeout(() => {
          isRedirectingToLogin = false
        }, 3000)
      }
      return Promise.reject(err)
    }

    // if (err.response?.status === 403) {
    //   // window.location.href = '/'
    // }

    // 其他非200错误，显示错误提示
    if (err.response?.status !== 200) {
      message.error(`请求失败 (${err.response?.status})${description !== '-' ? '：' + description : ''}`)
    }

    return Promise.reject(err)
  },
)

export default instance
