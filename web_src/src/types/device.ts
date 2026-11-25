import { commonColor } from '@/utils/color'
import { NightLightsStateEnum, DistanceLimitStatus, ObstacleAvoidance } from './device-setting'
import { AlarmModeEnum, BatteryStoreModeEnum, DroneBatteryStateEnum, FourGLinkStateEnum, SdrLinkStateEnum, LinkWorkModeEnum } from './airport-tsa'
import { CameraMode } from '@/types/live-stream'

export interface DeviceValue {
  key: string; // 'domain-type-subtype'
  domain: string; // 表示一个领域，作为一个命名空间，暂时分 飞机类-0, 负载类-1,RC类-2,机场类-3 4种
  type: number; // 设备类型枚举
  sub_type: number; // 设备类型枚举 负载一般表示镜头
}

// domain
export enum DOMAIN {
  DRONE = '0', // 飞行器
  PAYLOAD = '1', // 负载
  RC = '2', // 遥控
  DOCK = '3', // 机场
}

// DJI飞机类型
export enum DRONE_TYPE {
  M30 = 67,
  M300 = 60,
  Mavic3EnterpriseAdvanced = 77,
  M350 = 89,
  M3D = 91,
  M4D = 100,
  M4E = 99,
  M4T = 99,
}

// DJI负载类型枚举值
export enum PAYLOAD_TYPE {
  FPV = 39,
  H20 = 42,
  H20T = 43,
  H20N = 61,
  EP600 = 50,
  EP800 = 90742,
  M30D = 52,
  M30T = 53,
  XT2 = 26,
  XTS = 41,
  Z30 = 20,
  DockTopCamera = 165,

  M3E = 66,
  M3T = 67,
  M3D = 80,
  M3TD = 81,
  M4TD = 99,
  M4D = 98,
  M4E = 88,
  M4T = 89,
  // UNKNOWN = 65535
}

// RC type
export enum RC_TYPE {
  RC = 56,
  RCPlus = 119,
  RC144 = 144,
}

// DOCK type
export enum DOCK_TYPE {
  Dock = 1,
  Dock2 = 2,
  Dock3 = 3
}

// 设备sub_type 从0升序
export enum DEVICE_SUB_TYPE {
  ZERO,
  ONE,
  TWO,
  THREE,
  UNKNOWN = 65535,
}

export const DEVICE_MODEL_KEY = {
  M30: `${DOMAIN.DRONE}-${DRONE_TYPE.M30}-${DEVICE_SUB_TYPE.ZERO}`,
  M30T: `${DOMAIN.DRONE}-${DRONE_TYPE.M30}-${DEVICE_SUB_TYPE.ONE}`,

  M3E: `${DOMAIN.DRONE}-${DRONE_TYPE.Mavic3EnterpriseAdvanced}-${DEVICE_SUB_TYPE.ZERO}`,
  M3T: `${DOMAIN.DRONE}-${DRONE_TYPE.Mavic3EnterpriseAdvanced}-${DEVICE_SUB_TYPE.ONE}`,

  M300: `${DOMAIN.DRONE}-${DRONE_TYPE.M300}-${DEVICE_SUB_TYPE.ZERO}`,
  M350: `${DOMAIN.DRONE}-${DRONE_TYPE.M350}-${DEVICE_SUB_TYPE.ZERO}`,

  M3D: `${DOMAIN.DRONE}-${DRONE_TYPE.M3D}-${DEVICE_SUB_TYPE.ZERO}`,
  M3TD: `${DOMAIN.DRONE}-${DRONE_TYPE.M3D}-${DEVICE_SUB_TYPE.ONE}`,

  M4D: `${DOMAIN.DRONE}-${DRONE_TYPE.M4D}-${DEVICE_SUB_TYPE.ZERO}`,
  M4TD: `${DOMAIN.DRONE}-${DRONE_TYPE.M4D}-${DEVICE_SUB_TYPE.ONE}`,
  M4E: `${DOMAIN.DRONE}-${DRONE_TYPE.M4E}-${DEVICE_SUB_TYPE.ZERO}`,
  M4T: `${DOMAIN.DRONE}-${DRONE_TYPE.M4E}-${DEVICE_SUB_TYPE.ONE}`,

  FPV: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.FPV}-${DEVICE_SUB_TYPE.ZERO}`,
  H20: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.H20}-${DEVICE_SUB_TYPE.ZERO}`,
  H20T: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.H20T}-${DEVICE_SUB_TYPE.ZERO}`,
  H20N: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.H20N}-${DEVICE_SUB_TYPE.ZERO}`,
  EP600: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.EP600}-${DEVICE_SUB_TYPE.UNKNOWN}`,
  EP800: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.EP800}-${DEVICE_SUB_TYPE.ZERO}`,
  M30Camera: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.M30D}-${DEVICE_SUB_TYPE.ZERO}`,
  M30TCamera: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.M30T}-${DEVICE_SUB_TYPE.ZERO}`,

  M3ECamera: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.M3E}-${DEVICE_SUB_TYPE.ZERO}`,
  M3TCamera: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.M3T}-${DEVICE_SUB_TYPE.ZERO}`,
  M3DCamera: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.M3D}-${DEVICE_SUB_TYPE.ZERO}`,
  M3TDCamera: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.M3TD}-${DEVICE_SUB_TYPE.ZERO}`,
  M4DCamera: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.M4D}-${DEVICE_SUB_TYPE.ZERO}`,
  M4TDCamera: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.M4TD}-${DEVICE_SUB_TYPE.ZERO}`,
  M4ECamera: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.M4E}-${DEVICE_SUB_TYPE.ZERO}`,
  M4TCamera: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.M4T}-${DEVICE_SUB_TYPE.ZERO}`,
  // M3MCamera: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.M3M}-${DEVICE_SUB_TYPE.ZERO}`,

  XT2: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.XT2}-${DEVICE_SUB_TYPE.ZERO}`,
  XTS: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.XTS}-${DEVICE_SUB_TYPE.ZERO}`,
  Z30: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.Z30}-${DEVICE_SUB_TYPE.ZERO}`,
  DockTopCamera: `${DOMAIN.PAYLOAD}-${PAYLOAD_TYPE.DockTopCamera}-${DEVICE_SUB_TYPE.ZERO}`,

  RC: `${DOMAIN.RC}-${RC_TYPE.RC}-${DEVICE_SUB_TYPE.ZERO}`,
  RCPlus: `${DOMAIN.RC}-${RC_TYPE.RCPlus}-${DEVICE_SUB_TYPE.ZERO}`,

  Dock: `${DOMAIN.DOCK}-${DOCK_TYPE.Dock}-${DEVICE_SUB_TYPE.ZERO}`,
  Dock2: `${DOMAIN.DOCK}-${DOCK_TYPE.Dock2}-${DEVICE_SUB_TYPE.ZERO}`,
  Dock3: `${DOMAIN.DOCK}-${DOCK_TYPE.Dock3}-${DEVICE_SUB_TYPE.ZERO}`,
}

export const DEVICE_NAME = {
  // drone
  [DEVICE_MODEL_KEY.M30]: 'M30',
  [DEVICE_MODEL_KEY.M30T]: 'M30T',
  [DEVICE_MODEL_KEY.M3E]: 'Mavic 3E',
  [DEVICE_MODEL_KEY.M3T]: 'Mavic 3T',
  // [DEVICE_MODEL_KEY.M3M]: 'Mavic 3M',
  [DEVICE_MODEL_KEY.M300]: 'M300 RTK',
  [DEVICE_MODEL_KEY.M350]: 'M350 RTK',
  [DEVICE_MODEL_KEY.M3D]: 'M3D',
  [DEVICE_MODEL_KEY.M3TD]: 'M3TD',
  [DEVICE_MODEL_KEY.M4D]: 'M4D',
  [DEVICE_MODEL_KEY.M4TD]: 'M4TD',
  [DEVICE_MODEL_KEY.M4E]: 'Mavic 4E',
  [DEVICE_MODEL_KEY.M4T]: 'Mavic 4T',

  // payload
  [DEVICE_MODEL_KEY.FPV]: 'FPV',
  [DEVICE_MODEL_KEY.H20]: 'H20',
  [DEVICE_MODEL_KEY.H20T]: 'H20T',
  [DEVICE_MODEL_KEY.H20N]: 'H20N',
  [DEVICE_MODEL_KEY.EP600]: 'P1',
  [DEVICE_MODEL_KEY.EP800]: 'L1',
  [DEVICE_MODEL_KEY.M30Camera]: 'M30 Camera',
  [DEVICE_MODEL_KEY.M30TCamera]: 'M30T Camera',
  [DEVICE_MODEL_KEY.M3ECamera]: 'Mavic 3E',
  [DEVICE_MODEL_KEY.M3TCamera]: 'Mavic 3T',
  // [DEVICE_MODEL_KEY.M3MCamera]: 'Mavic 3M',
  [DEVICE_MODEL_KEY.XT2]: 'XT2',
  [DEVICE_MODEL_KEY.XTS]: 'XTS',
  [DEVICE_MODEL_KEY.Z30]: 'Z30',
  [DEVICE_MODEL_KEY.DockTopCamera]: 'Dock Camera',
  [DEVICE_MODEL_KEY.M3DCamera]: 'M3D Camera',
  [DEVICE_MODEL_KEY.M3TDCamera]: 'M3TD Camera',
  [DEVICE_MODEL_KEY.M4DCamera]: 'M4D Camera',
  [DEVICE_MODEL_KEY.M4TDCamera]: 'M4TD Camera',
  [DEVICE_MODEL_KEY.M4ECamera]: 'Mavic 4E Camera',
  [DEVICE_MODEL_KEY.M4TCamera]: 'Mavic 4T Camera',

  // rc
  [DEVICE_MODEL_KEY.RC]: 'RC',
  [DEVICE_MODEL_KEY.RCPlus]: 'RC Plus',

  // dock
  [DEVICE_MODEL_KEY.Dock]: 'Dock',
  [DEVICE_MODEL_KEY.Dock2]: 'Dock2',
  [DEVICE_MODEL_KEY.Dock3]: 'Dock3',
}

// 控制权
export enum ControlSource {
  A = 'A',
  B = 'B'
}

export interface PayloadInfo {
  index: number,
  model: string,
  control_source?: ControlSource,
  payload_sn?: string,
  payload_index?: string,
  payload_name?: string,
}

// 设备信息
export interface OnlineDevice {
  model: string,
  callsign: string,
  sn: string,
  mode: number,
  gateway: {
    model: string,
    callsign: string,
    sn: string,
    domain: string,
  },
  payload: PayloadInfo[]
}

export interface dockMap {
  callsign: string,
  sn: string,
  domain: string,
  latitude: any,
  longitude: any,
  height: any
}
// 固件升级类型
export enum DeviceFirmwareTypeEnum {
  CONSISTENT_UPGRADE = 2, // 一致性升级
  NORMAL_UPGRADE = 3,     // 普通升级
  PSDK_UPGRADE = 4,       // PSDK升级
  ConsistencyUpgrade = 2, // 兼容别名
  ToUpgraded = 3,         // 兼容别名
  '普通升级' = 3,
  '一致性升级' = 2,
}

// 固件升级状态
export enum DeviceFirmwareStatusEnum {
  None = 1, // 无需升级
  ToUpgraded = 2, // 待升级
  ConsistencyUpgrade = 3, // 一致性升级
  DuringUpgrade = 4, // 升级中
}

export const DeviceFirmwareStatus = {
  [DeviceFirmwareStatusEnum.None]: '',
  [DeviceFirmwareStatusEnum.ToUpgraded]: '待升级',
  [DeviceFirmwareStatusEnum.ConsistencyUpgrade]: '一致性升级',
  [DeviceFirmwareStatusEnum.DuringUpgrade]: '升级中',
}

export const DeviceFirmwareStatusColor = {
  [DeviceFirmwareStatusEnum.None]: commonColor.WHITE,
  [DeviceFirmwareStatusEnum.ToUpgraded]: commonColor.BLUE,
  [DeviceFirmwareStatusEnum.ConsistencyUpgrade]: commonColor.WARN,
  [DeviceFirmwareStatusEnum.DuringUpgrade]: commonColor.NORMAL,
}

export interface Device {
  device_name: string,
  device_sn: string,
  nickname: string,
  firmware_version: string,
  firmware_status: DeviceFirmwareStatusEnum,
  status: string,
  workspace_name: string,
  bound_time: string,
  login_time: string,
  children?: Device[],
  domain: number,
  type: number,
  firmware_progress?: string, // 升级进度
  insurance_expired?: string,
}

export interface DeviceStatus {
  sn: string,
  online_status: boolean,
  device_callsign: string,
  user_id: string,
  user_callsign: string
  bound_status: boolean,
  model: string,
  gateway_sn: string,
  domain: number
}

export interface OSDVisible {
  sn: string,
  model: string,
  callsign: string,
  visible: boolean,
  is_dock: boolean,
  gateway_sn: string,
  gateway_callsign: string,
  payloads: null | PayloadInfo[],
}

export interface GatewayOsd {
  capacity_percent: string,
  transmission_signal_quality: string,
  longitude: number,
  latitude: number,
}

export interface OsdCameraLiveview {
  bottom: number,
  left: number,
  right: number,
  top: number,
}
export interface DeviceOsdCamera {
  camera_mode: CameraMode,
  payload_index: string,
  photo_state: number,
  record_time: number,
  recording_state: number,
  remain_photo_num: number,
  remain_record_duration: number,
  liveview_world_region: OsdCameraLiveview
}

export interface DeviceOsd {
  longitude: number,
  latitude: number,
  gear: number,
  mode_code: number,
  height: number | string,
  home_distance: number | string,
  horizontal_speed: number | string,
  vertical_speed: number | string,
  wind_speed: number | string,
  wind_direction: string,
  elevation: number | string,
  position_state: {
    gps_number: string,
    is_fixed: number,
    rtk_number: string
  },
  battery: {
    capacity_percent: string,
    landing_power: string,
    remain_flight_time: number,
    return_home_power: string,
  },
  storage?: {
    total: number,
    used: number,
  },
  night_lights_state?: NightLightsStateEnum;// 夜航灯开关
  height_limit?: number;// 限高设置
  distance_limit_status?: DistanceLimitStatus;// 限远开关
  obstacle_avoidance?: ObstacleAvoidance;// 飞行器避障开关设置
  cameras?: DeviceOsdCamera[]
  remaining_power_for_return_home?: number; // 返航预留电量 0-100
}

export enum NetworkStateTypeEnum {
  '4G' = 1,
  FOUR_G = 1,
  '以太网' = 2,
  ETHERNET = 2,
}

export enum NetworkStateQualityEnum {
  '无信号' = 0,
  '差' = 1,
  '较差' = 2,
  '一般' = 3,
  FAIR = 3,
  '较好' = 4,
  '好' = 5,
}



export enum RainfallEnum {
  '无雨' = 0,
  '小雨' = 1,
  '中雨' = 2,
  '大雨' = 3,
}
export enum WindDirectionEnum {
  '正北' = 1,
  '东北' = 2,
  '东' = 3,
  '东南' = 4,
  '南' = 5,
  '西南' = 6,
  '西' = 7,
  '西北' = 8,
  '无风' = 0
}

export enum DroneInDockEnum {
  OUTSIDE, INSIDE
}

export interface DockBasicOsd {
  network_state?: {
    type: NetworkStateTypeEnum,
    quality: number,
    rate: number,
  },
  drone_charge_state?: {
    state: number,
    capacity_percent: number,
  },
  drone_in_dock: boolean,
  rainfall: RainfallEnum,
  wind_speed: number,
  environment_temperature: number,
  temperature: number,
  humidity: number,
  latitude: number,
  longitude: number,
  height: number,
  alternate_land_point?: {
    latitude: number,
    longitude: number,
    height: number,
    safe_land_height: number,
    is_configured: number
  }
  first_power_on: number,
  positionState?: {
    gps_number: number,
    is_fixed: number,
    rtk_number: number,
    is_calibration: number,
    quality: number,
  },
  storage?: {
    total: number,
    used: number,
  },
  mode_code: number,
  cover_state: number,
  supplement_light_state: number,
  emergency_stop_state: number,
  air_conditioner?: {
    air_conditioner_state: number,
    switch_time: number,
  }
  battery_store_mode?: BatteryStoreModeEnum; // 电池保养(存储)模式
  alarm_state?: AlarmModeEnum; // 机场声光报警状态
  putter_state: number,
  sub_device?: {
    device_sn?: string,
    device_model_key?: string,
    device_online_status: number,
    device_paired: number,
  },
  // live_capacity?: LiveCapacity; // 直播能力
  // live_status?: Array<LiveStatus>; // 直播状态
}
export enum DrcStateEnum {
  DISCONNECT = 0,
  CONNECTING = 1,
  CONNECTED = 2
}
export interface DockLinkOsd {
  drc_state: DrcStateEnum,
  flighttask_prepare_capacity: number,
  flighttask_step_code: number,
  media_file_detail?: {
    remain_upload: number
  },
  sdr?: {
    up_quality: string,
    down_quality: string,
    frequency_band: number,
  },
  wireless_link?: { // 图传链路<会包括4G和sdr信息
    dongle_number: number, // dongle 数量
    ['4g_link_state']: FourGLinkStateEnum, // 4g_link_state
    sdr_link_state: SdrLinkStateEnum, // sdr链路连接状态
    link_workmode: LinkWorkModeEnum, // 图传链路模式
    sdr_quality: number, // sdr信号质量 0-5
    ['4g_quality']: number, // 4G信号质量 0-5
    ['4g_freq_band']: number,
    ['4g_gnd_quality']: number,
    ['4g_uav_quality']: number,
    sdr_freq_band: number,
  }
}

export interface MaintainStatus {
  state: number,
  last_maintain_type: number,
  last_maintain_time: number,
  last_maintain_work_sorties: number,
}

export interface DockWorkOsd {
  job_number: number,
  acc_time: number,
  activation_time: number,
  maintain_status?: {
    maintain_status_array: MaintainStatus[]
  }
  electric_supply_voltage: number,
  working_voltage: string,
  working_current: string,
  backup_battery?: {
    voltage: number,
    temperature: number,
    switch: number,
  }
  drone_battery_maintenance_info?: { // 飞行器电池保养信息
    maintenance_state: DroneBatteryStateEnum, // 保养状态
    maintenance_time_left: number, // 电池保养剩余时间(小时)
  }
}

export interface DockOsd {
  basic_osd: DockBasicOsd,
  link_osd: DockLinkOsd,
  work_osd: DockWorkOsd
}

export enum EModeCode {
  Standby,
  Preparing,
  Ready,
  Manual,
  Automatic,
  Waypoint,
  Panoramic,
  Active_Track,
  ADS_B,
  Return_To_Home,
  Landing,
  Forced_Landing,
  Three_Blades_Landing,
  Upgrading,
  Disconnected,
  VirtualJoystick = 16,
  CommandFly = 17
}
export enum EModeWord {
  Standby = '待机',
  Preparing = '起飞准备',
  Ready = '起飞准备完毕',
  Manual = '手动飞行',
  Automatic = '自动起飞',
  Waypoint = '航线飞行',
  Panoramic = '全景拍照',
  Active_Track = '智能跟随',
  ADS_B = 'ADS—B躲避',
  Return_To_Home = '自动返航',
  Landing = '自动降落',
  Forced_Landing = '强制降落',
  Three_Blades_Landing = '三桨叶降落',
  Upgrading = '升级中',
  Disconnected = '舱内关机',
  VirtualJoystick = '虚拟摇杆',
  CommandFly = '指令飞行'
}

export enum EGear {
  A,
  P,
  NAV,
  FPV,
  FARM,
  S,
  F,
  M,
  G,
  T
}

export enum EDockModeCode {
  Disconnected = -1,
  Idle,
  Debugging,
  Remote_Debugging,
  Upgrading,
  Working,
}

export enum EDockModeCodeZh {
  Disconnected = '机场未连接',
  Idle = '设备空闲中',
  Debugging = '现场调试中',
  Remote_Debugging = '远程调试中',
  Upgrading = '固件升级中',
  Working = '飞行作业中',
}
export interface DeviceHms {
  hms_id: string,
  tid: string,
  bid: string,
  sn: string,
  level: number,
  module: number,
  key: string,
  message_en: string,
  message_zh: string,
  create_time: string,
  update_time: string,
  domain: number
}

// TODO: 设备拓扑管理优化
// 设备osd信息
export interface DeviceInfoType {
  gateway: GatewayOsd, // 遥控器
  dock: DockOsd, // 机场
  device: DeviceOsd, // 飞机
}
