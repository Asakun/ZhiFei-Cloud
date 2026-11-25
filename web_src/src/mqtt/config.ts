import type {
  IClientOptions,
} from 'mqtt'

export const OPTIONS: IClientOptions = {
  clean: true, // true: 清除会话, false: 保留会话
  connectTimeout: 15000, // mqtt 连接超时时间：15s（公网延迟较高时需要更长时间）
  resubscribe: true, // 断开重连后，再次订阅原订阅
  reconnectPeriod: 3000, // 重连间隔时间：3s（更快恢复连接）
  keepalive: 30, // 心跳间隔时间：30s（需小于 NAT 超时时间，通常 60-120s）
}
