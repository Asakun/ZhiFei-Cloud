// 夜航灯开关
export enum NightLightsStateEnum {
  CLOSE = 0, // 0-关闭
  OPEN = 1, // 1-打开
}

// 限远开关
export enum DistanceLimitStatusEnum {
  UNSET = 0, // 0-未设置
  SET = 1, // 1-已设置
}

export interface DistanceLimitStatus {
  state?: DistanceLimitStatusEnum;
  distance_limit?: number; // 限远
}

// 避障
export enum ObstacleAvoidanceStatusEnum {
  CLOSE = 0, // 0-关闭
  OPEN = 1, // 1-开启
}


export interface ObstacleAvoidance {
  horizon?: ObstacleAvoidanceStatusEnum;// 水平避障开关
  upside?: ObstacleAvoidanceStatusEnum;// 上行方向避障开关
  downside?: ObstacleAvoidanceStatusEnum;// 下行方向避障开关
}

// 相机水印设置
export enum WatermarkLayoutEnum {
  TOP_LEFT = 0,
  BOTTOM_LEFT = 1,
  TOP_RIGHT = 2,
  BOTTOM_RIGHT = 3
}

export interface CameraWatermarkSettings {
  global_enable?: number; // 0-关闭, 1-开启
  drone_type_enable?: number;
  drone_sn_enable?: number;
  datetime_enable?: number;
  gps_enable?: number;
  user_custom_string_enable?: number;
  user_custom_string?: string;
  layout?: WatermarkLayoutEnum | number;
}

// 无人机 Redis 已设置状态接口返回全量类型
export interface DroneStateData {
  camera_watermark_settings?: CameraWatermarkSettings;
  photo_storage_settings?: string[];
  video_storage_settings?: string[];
  cameras?: Array<{
    payload_index?: string;
    photo_storage_settings?: string[];
    video_storage_settings?: string[];
    [key: string]: any;
  }>;
  remaining_power_for_return_home?: number;
  night_lights_state?: NightLightsStateEnum;
  height_limit?: number;
  distance_limit_status?: DistanceLimitStatus;
  obstacle_avoidance?: ObstacleAvoidance;
  rth_altitude?: number;
  commander_flight_height?: number;
  commander_flight_mode?: number;
  commander_mode_lost_action?: number;
  thermal_current_palette_style?: number;
  thermal_gain_mode?: number;
  thermal_isotherm_state?: number;
  thermal_isotherm_upper_limit?: number;
  thermal_isotherm_lower_limit?: number;
  [key: string]: any;
}

// 设备管理设置key
export enum DeviceSettingKeyEnum {
  NIGHT_LIGHTS_MODE_SET = 'night_lights_state', // 夜航灯开关
  HEIGHT_LIMIT_SET = 'height_limit', // 限高设置
  DISTANCE_LIMIT_SET = 'distance_limit_status', // 限远开关
  OBSTACLE_AVOIDANCE_HORIZON = 'obstacle_avoidance_horizon', // 水平避障状态
  OBSTACLE_AVOIDANCE_UPSIDE = 'obstacle_avoidance_upside', // 上视避障状态
  OBSTACLE_AVOIDANCE_DOWNSIDE = 'obstacle_avoidance_downside', // 下视避障状态
}

export type DeviceSettingType = Record<DeviceSettingKeyEnum, any>

export const initDeviceSetting = {
  [DeviceSettingKeyEnum.NIGHT_LIGHTS_MODE_SET]:
  {
    label: '飞行器夜航灯',
    value: '',
    trueValue: NightLightsStateEnum.CLOSE,
    editable: false,
    popConfirm: {
      visible: false,
      loading: false,
      // content: '为保证飞行器的作业安全，建议打开夜航灯',
      label: '飞行器夜航灯',
    },
    settingKey: DeviceSettingKeyEnum.NIGHT_LIGHTS_MODE_SET,
  },
  [DeviceSettingKeyEnum.HEIGHT_LIMIT_SET]:
  {
    label: '限高',
    value: '',
    trueValue: 120,
    editable: false,
    popConfirm: {
      visible: false,
      loading: false,
      // content: '限高：20 - 1500m',
      // info: '修改限高会影响当前机场的所有作业任务，建议确认作业情况后再进行修改',
      label: '限高',
    },
    settingKey: DeviceSettingKeyEnum.HEIGHT_LIMIT_SET,
  },
  [DeviceSettingKeyEnum.DISTANCE_LIMIT_SET]:
  {
    label: '限远',
    value: '',
    trueValue: DistanceLimitStatusEnum.UNSET,
    // info: '限远（15 - 8000m）是约束飞行器相对机场的最大作业距离',
    editable: false,
    popConfirm: {
      visible: false,
      loading: false,
      // content: '限远 (15- 8000m) 是约束飞行器相对机场的最大作业距离',
      // info: '修改限远会影响当前机场的所有作业任务，建议确认作业情况后再进行修改',
      label: '限远',

    },
    settingKey: DeviceSettingKeyEnum.DISTANCE_LIMIT_SET,
  },
  [DeviceSettingKeyEnum.OBSTACLE_AVOIDANCE_HORIZON]:
  {
    label: '水平避障',
    value: '',
    trueValue: ObstacleAvoidanceStatusEnum.CLOSE,
    // info: '飞行器的避障工作状态显示，可以快速开启/关闭飞行器避障，如需进一步设置请在设备运维页面设置',
    editable: false,
    popConfirm: {
      visible: false,
      loading: false,
      // content: '飞行器避障是保障飞行作业安全的基础功能，建议保持飞行器避障开启',
      label: '水平避障',

    },
    settingKey: DeviceSettingKeyEnum.OBSTACLE_AVOIDANCE_HORIZON,
  },
  [DeviceSettingKeyEnum.OBSTACLE_AVOIDANCE_UPSIDE]:
  {
    label: '上视避障',
    value: '',
    trueValue: ObstacleAvoidanceStatusEnum.CLOSE,
    // info: '飞行器的避障工作状态显示，可以快速开启/关闭飞行器避障，如需进一步设置请在设备运维页面设置',
    editable: false,
    popConfirm: {
      visible: false,
      loading: false,
      // content: '飞行器避障是保障飞行作业安全的基础功能，建议保持飞行器避障开启',
      label: '上视避障',

    },
    settingKey: DeviceSettingKeyEnum.OBSTACLE_AVOIDANCE_UPSIDE,
  },
  [DeviceSettingKeyEnum.OBSTACLE_AVOIDANCE_DOWNSIDE]:
  {
    label: '下视避障',
    value: '',
    trueValue: ObstacleAvoidanceStatusEnum.CLOSE,
    // info: '飞行器的避障工作状态显示，可以快速开启/关闭飞行器避障，如需进一步设置请在设备运维页面设置',
    editable: false,
    popConfirm: {
      visible: false,
      loading: false,
      // content: '飞行器避障是保障飞行作业安全的基础功能，建议保持飞行器避障开启',
      label: '下视避障',

    },
    settingKey: DeviceSettingKeyEnum.OBSTACLE_AVOIDANCE_DOWNSIDE,
  },
} as DeviceSettingType

export const initDeviceSettingFormModel = {
  nightLightsState: false, // 夜航灯开关
  heightLimit: 200, // 限高设置
  distanceLimitStatus: { state: false, distanceLimit: 2000 }, // 限远开关
  obstacleAvoidanceHorizon: false, // 飞行器避障-水平开关设置
  obstacleAvoidanceUpside: false, // 飞行器避障-上视开关设置
  obstacleAvoidanceDownside: false, // 飞行器避障-下视开关设置
}

export type DeviceSettingFormModel = typeof initDeviceSettingFormModel
