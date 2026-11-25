import { uuidv4 } from './uuid'
import type { UranusMqtt } from '@/mqtt'

export interface MqttPropertySetPayload {
  bid: string
  data: Record<string, any>
  tid: string
  timestamp: number
}

export interface MqttPropertySetReplyResult {
  success: boolean
  topic: string
  replyTopic: string
  payload: MqttPropertySetPayload
  resultCode?: number
  resultMsg?: string
  error?: string
  rawData?: any
}

/**
 * 构建设备属性设置 MQTT Payload (参考图2)
 * @param propertyData 包含需设置属性的键值对数据
 */
export function buildPropertySetPayload(propertyData: Record<string, any>): MqttPropertySetPayload {
  return {
    bid: uuidv4(),
    data: propertyData,
    tid: uuidv4(),
    timestamp: Date.now()
  }
}

/**
 * 递归查找 reply 报文中的 result 状态码
 * result: 0-成功, 1-失败, 2-超时, 其他十六进制/整数为具体错误原因代码
 */
export function findResultCode(obj: any): { result: number } | null {
  if (obj === null || obj === undefined) return null
  if (typeof obj === 'number') return { result: obj }
  if (typeof obj === 'object') {
    if ('result' in obj && typeof obj.result === 'number') {
      return { result: obj.result }
    }
    for (const key of Object.keys(obj)) {
      const found = findResultCode(obj[key])
      if (found) return found
    }
  }
  return null
}

/**
 * 将 MQTT set_reply 结果码转化为可读信息
 */
export function getMqttSetReplyResultMessage(resultCode: number): string {
  if (resultCode === 0) return '设置成功'
  if (resultCode === 1) return '设备返回设置失败'
  if (resultCode === 2) return '指令超时未回应'
  const hexStr = '0x' + resultCode.toString(16)
  return `设备错误代码: ${resultCode} (${hexStr})`
}

/**
 * 检查 reply 的 data 键名是否包含请求属性的键名
 */
function hasMatchingKey(replyData: any, reqPropertyData: Record<string, any>): boolean {
  if (!replyData || typeof replyData !== 'object') return false
  const reqKeys = Object.keys(reqPropertyData)
  const replyKeys = Object.keys(replyData)
  return reqKeys.some(k => replyKeys.includes(k))
}

/**
 * 通过 MQTT 下发设备属性设置并等待监听 thing/product/{gateway_sn}/property/set_reply
 * @param gatewaySn 网关/机场 SN
 * @param propertyData 设置属性 Payload
 * @param mqttInstance MQTT 客户端实例
 * @param timeoutMs 超时时间毫秒 (默认 8000ms)
 */
export function publishMqttPropertySetWithReply(
  gatewaySn: string,
  propertyData: Record<string, any>,
  mqttInstance?: UranusMqtt | null,
  timeoutMs: number = 8000
): Promise<MqttPropertySetReplyResult> {
  return new Promise((resolve) => {
    if (!gatewaySn) {
      return resolve({
        success: false,
        topic: '',
        replyTopic: '',
        payload: buildPropertySetPayload(propertyData),
        error: '网关或设备 SN 为空'
      })
    }

    const setTopic = `thing/product/${gatewaySn}/property/set`
    const replyTopic = `thing/product/${gatewaySn}/property/set_reply`
    const payload = buildPropertySetPayload(propertyData)

    if (!mqttInstance || typeof mqttInstance.publishMqtt !== 'function') {
      return resolve({
        success: false,
        topic: setTopic,
        replyTopic,
        payload,
        error: '未连接 MQTT 客户端服务'
      })
    }

    let timeoutTimer: any = null
    let messageListener: any = null

    // 清理事件监听与定时器
    const cleanup = () => {
      if (timeoutTimer) clearTimeout(timeoutTimer)
      if (messageListener && mqttInstance && typeof mqttInstance.off === 'function') {
        mqttInstance.off('onMessageMqtt', messageListener)
      }
    }

    // 设置超时处理
    timeoutTimer = setTimeout(() => {
      cleanup()
      resolve({
        success: false,
        topic: setTopic,
        replyTopic,
        payload,
        resultCode: 2,
        resultMsg: '等待设备响应超时 (8秒)',
        error: '等待 MQTT 响应超时'
      })
    }, timeoutMs)

    // 监听 MQTT set_reply
    messageListener = (data: { topic: string; payload: Buffer }) => {
      const { topic: msgTopic, payload: msgBuffer } = data || {}
      if (msgTopic !== replyTopic || !msgBuffer) return

      try {
        const replyObj = JSON.parse(msgBuffer.toString())
        // 匹配 bid / tid / data 属性 key
        const isBidMatch = replyObj && (replyObj.bid === payload.bid || replyObj.tid === payload.tid)
        const isKeyMatch = replyObj && replyObj.data && hasMatchingKey(replyObj.data, propertyData)

        if (isBidMatch || isKeyMatch) {
          cleanup()
          const found = findResultCode(replyObj.data || replyObj)
          const resultCode = found ? found.result : 0
          const success = resultCode === 0
          const resultMsg = getMqttSetReplyResultMessage(resultCode)

          resolve({
            success,
            topic: setTopic,
            replyTopic,
            payload,
            resultCode,
            resultMsg,
            rawData: replyObj,
            error: success ? undefined : resultMsg
          })
        }
      } catch (err: any) {
        console.error('[MqttProperty] 解析 set_reply 报文失败:', err)
      }
    }

    // 订阅 set_reply Topic 并下发消息
    try {
      mqttInstance.subscribeMqtt(replyTopic)
      mqttInstance.on('onMessageMqtt', messageListener)
      mqttInstance.publishMqtt(setTopic, JSON.stringify(payload))
    } catch (err: any) {
      cleanup()
      resolve({
        success: false,
        topic: setTopic,
        replyTopic,
        payload,
        error: err.message || 'MQTT 发送异常'
      })
    }
  })
}

/**
 * 基础同步下发 (兼容原有调法)
 */
export function publishMqttPropertySet(
  gatewaySn: string,
  propertyData: Record<string, any>,
  mqttInstance?: UranusMqtt | null
): { success: boolean; topic: string; payload: MqttPropertySetPayload; error?: string } {
  if (!gatewaySn) {
    return {
      success: false,
      topic: '',
      payload: buildPropertySetPayload(propertyData),
      error: '网关或设备 SN 为空'
    }
  }

  const topic = `thing/product/${gatewaySn}/property/set`
  const payload = buildPropertySetPayload(propertyData)

  try {
    const payloadStr = JSON.stringify(payload)
    if (mqttInstance && typeof mqttInstance.publishMqtt === 'function') {
      mqttInstance.publishMqtt(topic, payloadStr)
      return { success: true, topic, payload }
    } else {
      return {
        success: false,
        topic,
        payload,
        error: '未连接 MQTT 客户端服务'
      }
    }
  } catch (err: any) {
    return {
      success: false,
      topic,
      payload,
      error: err.message || 'MQTT 发送失败'
    }
  }
}

/**
 * 通过 MQTT Services 通道下发服务调用并等待响应
 * Topic: thing/product/{gateway_sn}/services
 * Reply: thing/product/{gateway_sn}/services_reply
 */
export function publishMqttServiceWithReply(
  gatewaySn: string,
  method: string,
  serviceData: Record<string, any>,
  mqttInstance?: UranusMqtt | null,
  timeoutMs: number = 8000
): Promise<MqttPropertySetReplyResult> {
  return new Promise((resolve) => {
    if (!gatewaySn) {
      return resolve({
        success: false,
        topic: '',
        replyTopic: '',
        payload: {
          bid: uuidv4(),
          data: serviceData,
          tid: uuidv4(),
          timestamp: Date.now()
        },
        error: '网关或设备 SN 为空'
      })
    }

    const serviceTopic = `thing/product/${gatewaySn}/services`
    const replyTopic = `thing/product/${gatewaySn}/services_reply`
    const payload = {
      bid: uuidv4(),
      data: serviceData,
      tid: uuidv4(),
      timestamp: Date.now(),
      method
    }

    if (!mqttInstance || typeof mqttInstance.publishMqtt !== 'function') {
      return resolve({
        success: false,
        topic: serviceTopic,
        replyTopic,
        payload,
        error: '未连接 MQTT 客户端服务'
      })
    }

    let timeoutTimer: any = null
    let messageListener: any = null

    const cleanup = () => {
      if (timeoutTimer) clearTimeout(timeoutTimer)
      if (messageListener && mqttInstance && typeof mqttInstance.off === 'function') {
        mqttInstance.off('onMessageMqtt', messageListener)
      }
    }

    timeoutTimer = setTimeout(() => {
      cleanup()
      resolve({
        success: false,
        topic: serviceTopic,
        replyTopic,
        payload,
        resultCode: 2,
        resultMsg: '等待设备响应超时 (8秒)',
        error: '等待 MQTT 服务响应超时'
      })
    }, timeoutMs)

    messageListener = (data: { topic: string; payload: Buffer }) => {
      const { topic: msgTopic, payload: msgBuffer } = data || {}
      if (msgTopic !== replyTopic || !msgBuffer) return

      try {
        const replyObj = JSON.parse(msgBuffer.toString())
        const isBidMatch = replyObj && (replyObj.bid === payload.bid || replyObj.tid === payload.tid)
        const isMethodMatch = replyObj && replyObj.method === method

        if (isBidMatch || isMethodMatch) {
          cleanup()
          const found = findResultCode(replyObj.data || replyObj)
          const resultCode = found ? found.result : (replyObj.result !== undefined ? replyObj.result : 0)
          const success = resultCode === 0
          const resultMsg = getMqttSetReplyResultMessage(resultCode)

          resolve({
            success,
            topic: serviceTopic,
            replyTopic,
            payload,
            resultCode,
            resultMsg,
            rawData: replyObj,
            error: success ? undefined : resultMsg
          })
        }
      } catch (err: any) {
        console.error('[MqttProperty] 解析 services_reply 报文失败:', err)
      }
    }

    try {
      mqttInstance.subscribeMqtt(replyTopic)
      mqttInstance.on('onMessageMqtt', messageListener)
      mqttInstance.publishMqtt(serviceTopic, JSON.stringify(payload))
    } catch (err: any) {
      cleanup()
      resolve({
        success: false,
        topic: serviceTopic,
        replyTopic,
        payload,
        error: err.message || 'MQTT 发送异常'
      })
    }
  })
}

