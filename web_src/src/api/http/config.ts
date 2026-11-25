export const CURRENT_CONFIG = {

  // DJI Cloud API / Pilot 2 平台应用授权信息（请替换为在司空/DJI 开发者平台申请的凭证）
  appId: '请填写 DJI 开发者平台 App ID',
  appKey: '请填写 DJI 开发者平台 App Key',
  appLicense: '请填写 DJI 开发者平台 App License',

  // 后端 HTTP 与 WebSocket 基础路径（默认走 Vite / Nginx 反向代理前缀，也可直接填写完整服务地址如 http://<服务器IP>:<端口>/）
  baseURL: '/backEnd',
  websocketURL: '/backEndWs/api/v1/ws',

  // 直播推流与国标配置
  // RTMP 推流地址前缀，例如: rtmp://<媒体服务器IP>:<端口>/live/
  rtmpURL: '请填写 RTMP 推流地址前缀（如 rtmp://<服务器IP>:5003/live/）',
  // GB28181 国标推流参数（需与后端及 Pilot 2 中的配置保持一致）
  gbServerIp: '请填写 GB28181 服务端 IP',
  gbServerPort: '请填写 GB28181 服务端端口（如 30001）',
  gbServerId: '请填写 GB28181 服务端国标编码（20位）',
  gbAgentId: '请填写 GB28181 设备国标编码（20位）',
  gbPassword: '请填写 GB28181 鉴权密码',
  gbAgentPort: '5060', // 本地 SIP 信令端口
  gbAgentChannel: '请填写 GB28181 通道编码（20位）',

  // RTSP 推流配置
  rtspUserName: '请填写 RTSP 用户名',
  rtspPassword: '请填写 RTSP 密码',
  rtspPort: '8554', // RTSP 默认端口

  // 高德地图 Key（请前往高德开放平台申请 Web 端 JS API Key）
  amapKey: '请填写高德地图 Web JS API Key',

  // WebRTC WHIP 推流地址模板，例如: http://<SRS服务器IP>:<端口>/index/api/whip?app=live&stream=test
  webRtc: '请填写 WebRTC WHIP 推流地址（如 http://<服务器IP>:5004/index/api/whip?app=live&stream=test）',

  // SRS 媒体服务配置
  srsRtmp: '请填写 SRS HTTP-FLV 播放基地址（如 http://<服务器IP>:13006）',
  srsAPI: '/srsAPI', // SRS 接口代理前缀（在 vite.config.ts / Nginx 中配置转发）
  backEndPlayUrl: '请填写后端媒体播放地址（如 http://<服务器IP>:8080）',
  srsPushRtmp: '请填写 SRS RTMP 推流基地址（如 rtmp://<服务器IP>）',
  srsPushInterval: 5, // 推流检测/采样间隔（秒）

  // MinIO 对象存储外部访问地址（用于加载模型与资源文件）
  minio_url: '请填写 MinIO 访问地址（如 http://<服务器IP>:9002）',

  // 运行环境标识：'development' | 'production'
  run_env: 'development',

  // 遥控器连接 MQTT Broker 地址（TCP 协议，如 tcp://<服务器IP>:18084）
  pilotHost: '请填写遥控器 MQTT 连接地址（如 tcp://<服务器IP>:18084）',

  // Cesium Ion Token（请前往 https://ion.cesium.com/ 申请个人 Access Token，用于 3D 地形与影像）
  cesiumIonToken: '请填写 Cesium Ion Access Token',
  VITE_CESIUM_ION_TOKEN: '请填写 Cesium Ion Access Token',

  // 天地图 Token（请前往天地图控制台申请浏览器端 Key，用于底图影像与地名注记）
  tiandituToken: '请填写天地图开发者 Token',
  VITE_TIANDITU_TOKEN: '请填写天地图开发者 Token',
}
