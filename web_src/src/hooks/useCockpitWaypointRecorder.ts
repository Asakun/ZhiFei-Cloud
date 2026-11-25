import { ref, computed, shallowRef } from 'vue';
import { message } from 'ant-design-vue';
import { useRouter } from 'vue-router';
import { saveWaylineEdit } from '@/api/wayline';
import { ELocalStorageKey } from '@/types/enums';
import { DRONE_TYPE, PAYLOAD_TYPE } from '@/types/device';

export interface WaypointAction {
  action_func: 'takePhoto' | 'hover' | 'gimbalRotate' | 'startRecord' | 'stopRecord' | 'custom';
  hover_time?: number; // 悬停秒数
  gimbal_pitch_rotate_angle?: number; // 俯仰角
  gimbal_yaw_rotate_angle?: number; // 偏航角
  action_index?: number;
  use_global_image_format?: number;
}

export interface CockpitRecordedWaypoint {
  id: string;
  index: number;
  name: string;
  longitude: number;
  latitude: number;
  height: number; // 相对起飞点高度 (m)
  absoluteHeight: number; // 绝对海拔高度 (m)
  yaw: number; // 机头偏航角 (-180 ~ 180)
  pitch?: number;
  roll?: number;
  gimbalPitch: number; // 云台俯仰角 (-90 ~ 30)
  gimbalYaw?: number;
  gimbalRoll?: number;
  zoomFactor: number; // 变焦倍数
  cameraType?: 'wide' | 'zoom' | 'ir';
  cameraMode?: number; // 0: 拍照, 1: 录像
  speed?: number; // 航点速度
  snapshot?: string; // 视频抓拍缩略图 (Base64)
  actions: WaypointAction[];
  createTime: number;
}

export interface RouteGlobalConfig {
  name: string;
  dockSn?: string;
  dockName?: string;
  takeoffPoint?: {
    longitude: number;
    latitude: number;
    height: number;
  };
  aircraftSeries?: number;
  aircraftModel?: number;
  droneType?: number;
  subDroneType?: number;
  payloadType?: number;
  globalSpeed: number; // 飞行速度 (m/s)
  rthAltitude: number; // 返航高度 (m)
  finishAction: 'goHome' | 'hover' | 'land' | 'returnToStart';
  exitOnRCLost: 'goBack' | 'hover' | 'land';
  yawMode: 'followWayline' | 'manual' | 'lockHeading';
  gimbalPitchMode: 'usePointSetting' | 'manual';
  photoType: string[]; // ['visable', 'wide', 'zoom', 'ir']
  templateType?: string;
}

// 计算三维地球表面与高度距离 (米)
function calculateDistance3D(
  p1: { longitude: number; latitude: number; height: number },
  p2: { longitude: number; latitude: number; height: number }
): number {
  const R = 6371000; // 地球半径 (米)
  const rad = Math.PI / 180;
  const lat1 = p1.latitude * rad;
  const lat2 = p2.latitude * rad;
  const dLat = (p2.latitude - p1.latitude) * rad;
  const dLon = (p2.longitude - p1.longitude) * rad;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  const horizontalDist = R * c;
  const dHeight = Math.abs((p2.height || 0) - (p1.height || 0));
  return Math.sqrt(horizontalDist * horizontalDist + dHeight * dHeight);
}

// 从 Video 元素截取当前视频帧快照
export function captureVideoFrame(videoEl?: HTMLVideoElement | null): string | undefined {
  if (!videoEl || videoEl.videoWidth === 0 || videoEl.videoHeight === 0) return undefined;
  try {
    const canvas = document.createElement('canvas');
    canvas.width = 320;
    canvas.height = 180;
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.drawImage(videoEl, 0, 0, canvas.width, canvas.height);
      return canvas.toDataURL('image/jpeg', 0.65);
    }
  } catch (err) {
    console.warn('[WaypointRecorder] 视频抓拍缩略图失败:', err);
  }
  return undefined;
}

export function useCockpitWaypointRecorder() {
  const router = useRouter();

  // 已记录的航点列表
  const waypoints = ref<CockpitRecordedWaypoint[]>([]);

  // 是否正在记录打点模式
  const isRecordingEnabled = ref(true);

  // 航点总数
  const totalCount = computed(() => waypoints.value.length);

  // 航线总航程 (米)
  const totalDistance = computed(() => {
    if (waypoints.value.length < 2) return 0;
    let dist = 0;
    for (let i = 0; i < waypoints.value.length - 1; i++) {
      dist += calculateDistance3D(waypoints.value[i], waypoints.value[i + 1]);
    }
    return dist;
  });

  // 预计飞行总耗时 (秒)
  const estimatedDuration = computed(() => {
    if (waypoints.value.length === 0) return 0;
    // 基础飞行时间 (假设10m/s) + 每个航点悬停/动作开销 5秒
    const flightTime = totalDistance.value / 10;
    const actionTime = waypoints.value.reduce((acc, wp) => {
      const hoverAction = wp.actions.find((a) => a.action_func === 'hover');
      return acc + (hoverAction?.hover_time || 5);
    }, 0);
    return Math.round(flightTime + actionTime);
  });

  // 格式化为 Cesium 渲染所需的航点实体列表 (DjiCesium: waylinePoints)
  const cesiumWaylinePoints = computed(() => {
    return waypoints.value.map((wp, idx) => ({
      longitude: wp.longitude,
      latitude: wp.latitude,
      height: wp.absoluteHeight || wp.height,
      name: wp.name || `航点 ${idx + 1}`,
      index: idx + 1
    }));
  });

  /**
   * 采集并记录当前无人机位置为航点
   */
  const recordWaypoint = (params: {
    osdInfo: any;
    dockLocation?: { longitude: number; latitude: number; height: number };
    gimbalPitch?: number;
    gimbalYaw?: number;
    zoomFactor?: number;
    cameraType?: 'wide' | 'zoom' | 'ir';
    cameraMode?: number;
    videoElement?: HTMLVideoElement | null;
    defaultAction?: 'takePhoto' | 'hover' | 'none';
  }): CockpitRecordedWaypoint | null => {
    const {
      osdInfo,
      dockLocation,
      gimbalPitch = -45,
      gimbalYaw,
      zoomFactor = 1,
      cameraType = 'zoom',
      cameraMode = 0,
      videoElement,
      defaultAction = 'none'
    } = params;

    if (!osdInfo) {
      message.warning('无法获取当前无人机遥测数据');
      return null;
    }

    const lon = Number(
      osdInfo.longitude ||
      osdInfo.lon ||
      osdInfo.position_state?.longitude ||
      0
    );
    const lat = Number(
      osdInfo.latitude ||
      osdInfo.lat ||
      osdInfo.position_state?.latitude ||
      0
    );

    if (isNaN(lon) || isNaN(lat) || lon === 0 || lat === 0) {
      message.warning('当前无人机 GPS 经纬度未就绪，无法打点');
      return null;
    }

    const dockH = Number(dockLocation?.height || 0);

    // 相对起飞点高度 (Relative Height, 例如 50m)
    // DJI Cloud API 中: elevation 为相对高度, height 为海拔高度(ASL)
    let relativeHeight = 50;
    if (osdInfo.elevation !== undefined && !isNaN(Number(osdInfo.elevation))) {
      relativeHeight = Number(osdInfo.elevation);
    } else if (osdInfo.relative_height !== undefined && !isNaN(Number(osdInfo.relative_height))) {
      relativeHeight = Number(osdInfo.relative_height);
    } else if (osdInfo.height !== undefined && dockH > 0 && Number(osdInfo.height) > dockH) {
      relativeHeight = Number(osdInfo.height) - dockH;
    } else if (osdInfo.height !== undefined) {
      relativeHeight = Number(osdInfo.height);
    }

    // 绝对海拔高度 (Absolute Altitude ASL, 例如 350m)
    let absoluteHeight = relativeHeight + dockH;
    if (osdInfo.absolute_height !== undefined && !isNaN(Number(osdInfo.absolute_height))) {
      absoluteHeight = Number(osdInfo.absolute_height);
    } else if (osdInfo.height !== undefined && Number(osdInfo.height) > relativeHeight) {
      absoluteHeight = Number(osdInfo.height);
    } else {
      absoluteHeight = relativeHeight + dockH;
    }

    // 机头偏航角 Yaw
    const yaw = Number(osdInfo.attitude_head || osdInfo.heading || osdInfo.yaw || 0);
    const pitch = Number(osdInfo.attitude_pitch || osdInfo.pitch || 0);
    const roll = Number(osdInfo.attitude_roll || osdInfo.roll || 0);

    // 云台俯仰角
    const curGimbalPitch = Number(
      gimbalPitch !== undefined
        ? gimbalPitch
        : osdInfo.gimbal_pitch || osdInfo.gimbalPitch || -45
    );

    // 截取缩略图
    const snapshot = captureVideoFrame(videoElement);

    // 默认航点动作列表 (自动包含当前记录的云台角度与偏航角)
    const actions: WaypointAction[] = [];

    // 1. 自动添加云台俯仰角度控制动作 (云台偏航角为相对机头角度，平时为 0°)
    actions.push({
      action_func: 'gimbalRotate',
      action_index: actions.length,
      gimbal_pitch_rotate_angle: Math.round(curGimbalPitch),
      gimbal_yaw_rotate_angle: Math.round(gimbalYaw || 0),
      payload_position_index: 0,
      use_global_payload_lens_index: 1
    });

    // 2. 自动添加机头偏航角控制动作
    actions.push({
      action_func: 'rotateYaw',
      action_index: actions.length,
      aircraft_heading: Math.round(yaw),
      aircraft_path_mode: 'clockwise'
    });

    // 3. 变焦倍数
    if (zoomFactor && Number(zoomFactor) > 1) {
      actions.push({
        action_func: 'zoom',
        action_index: actions.length,
        zoom: Number(Number(zoomFactor).toFixed(1))
      });
    }

    // 4. 拍照或悬停动作
    if (defaultAction === 'takePhoto') {
      actions.push({
        action_func: 'takePhoto',
        action_index: actions.length,
        use_global_image_format: 1,
        payload_lens_index: 'visable'
      });
    } else if (defaultAction === 'hover') {
      actions.push({
        action_func: 'hover',
        hover_time: 5,
        action_index: actions.length
      });
    }

    const newIndex = waypoints.value.length;
    const newWaypoint: CockpitRecordedWaypoint = {
      id: `wp_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      index: newIndex,
      name: `航点 ${newIndex + 1}`,
      longitude: Number(lon.toFixed(7)),
      latitude: Number(lat.toFixed(7)),
      height: Math.round(relativeHeight),
      absoluteHeight: Math.round(absoluteHeight),
      yaw: Math.round(yaw),
      pitch: Math.round(pitch),
      roll: Math.round(roll),
      gimbalPitch: Math.round(curGimbalPitch),
      gimbalYaw: (gimbalYaw !== undefined && gimbalYaw !== 0) ? Math.round(gimbalYaw) : Math.round(yaw),
      zoomFactor: Number(Number(zoomFactor).toFixed(1)),
      cameraType,
      cameraMode,
      speed: 10,
      snapshot,
      actions,
      createTime: Date.now()
    };

    waypoints.value.push(newWaypoint);
    message.success(`📍 成功记录【航点 ${newIndex + 1}】H:${newWaypoint.height}m 偏航:${newWaypoint.yaw}° 俯仰:${newWaypoint.gimbalPitch}°`);
    return newWaypoint;
  };

  /**
   * 删除单个航点
   */
  const removeWaypoint = (index: number) => {
    if (index >= 0 && index < waypoints.value.length) {
      const removed = waypoints.value.splice(index, 1)[0];
      // 重新编号
      waypoints.value.forEach((wp, idx) => {
        wp.index = idx;
        wp.name = `航点 ${idx + 1}`;
      });
      message.info(`已删除航点: ${removed?.name || (index + 1)}`);
    }
  };

  /**
   * 清空所有航点
   */
  const clearWaypoints = () => {
    waypoints.value = [];
    message.info('已清空所有记录的航点');
  };

  /**
   * 调整航点顺序
   */
  const moveWaypoint = (fromIndex: number, toIndex: number) => {
    if (
      fromIndex < 0 ||
      fromIndex >= waypoints.value.length ||
      toIndex < 0 ||
      toIndex >= waypoints.value.length ||
      fromIndex === toIndex
    ) {
      return;
    }
    const [moved] = waypoints.value.splice(fromIndex, 1);
    waypoints.value.splice(toIndex, 0, moved);
    waypoints.value.forEach((wp, idx) => {
      wp.index = idx;
    });
  };

  /**
   * 更新航点属性
   */
  const updateWaypoint = (index: number, patch: Partial<CockpitRecordedWaypoint>) => {
    if (index >= 0 && index < waypoints.value.length) {
      waypoints.value[index] = {
        ...waypoints.value[index],
        ...patch
      };
    }
  };

  /**
   * 生成符合 DJI WPML / 后端 API 规范的航线 JSON
   */
  const buildWaylineJson = (config: RouteGlobalConfig) => {
    let globalGroupId = 0;
    const takeoffHeight = config.takeoffPoint?.height || 0;
    const takeoffRefPointStr = config.takeoffPoint
      ? `${config.takeoffPoint.latitude},${config.takeoffPoint.longitude},${config.takeoffPoint.height || 0}`
      : '';

    const route_point_list = waypoints.value.map((point, index) => {
      const action_group_list: any[] = [];
      const pointActions: any[] = [];

      const hasGimbal = point.actions?.some(a => a.action_func === 'gimbalRotate');
      const hasYaw = point.actions?.some(a => a.action_func === 'rotateYaw');
      const hasZoom = point.actions?.some(a => a.action_func === 'zoom');

      // 1. 优先补充云台俯仰动作
      if (!hasGimbal && point.gimbalPitch !== undefined) {
        pointActions.push({
          action_func: 'gimbalRotate',
          gimbal_pitch_rotate_angle: point.gimbalPitch,
          gimbal_yaw_rotate_angle: point.gimbalYaw !== undefined ? point.gimbalYaw : 0,
          payload_position_index: 0,
          use_global_payload_lens_index: 1
        });
      }

      // 2. 偏航角动作
      if (!hasYaw && point.yaw !== undefined) {
        pointActions.push({
          action_func: 'rotateYaw',
          aircraft_heading: point.yaw,
          aircraft_path_mode: 'clockwise'
        });
      }

      // 3. 变焦倍数
      if (!hasZoom && point.zoomFactor && point.zoomFactor > 1) {
        pointActions.push({
          action_func: 'zoom',
          zoom: point.zoomFactor
        });
      }

      // 4. 其他用户动作 (拍照、悬停等)
      if (point.actions && point.actions.length > 0) {
        point.actions.forEach(act => {
          pointActions.push({ ...act });
        });
      }

      if (pointActions.length > 0) {
        action_group_list.push({
          action_group_id: globalGroupId++,
          action_group_start_index: index,
          action_group_end_index: index,
          action_trigger_type: 'reachPoint',
          actions: pointActions.map((act, aIdx) => ({
            ...act,
            action_index: aIdx
          }))
        });
      }

      return {
        route_point_index: index,
        longitude: point.longitude,
        latitude: point.latitude,
        height: point.height,
        speed: point.speed || config.globalSpeed,
        gimbal_pitch: point.gimbalPitch,
        custom_gimbal_pitch: point.gimbalPitch,
        aircraft_heading: point.yaw,
        yaw: point.yaw,
        waypoint_heading_param: {
          waypoint_heading_mode: config.yawMode || 'smoothTransition',
          waypoint_heading_angle: point.yaw || 0,
          waypoint_heading_path_mode: 'clockwise'
        },
        action_group_list: action_group_list.length > 0 ? action_group_list : undefined
      };
    });

    return {
      name: config.name,
      template_type: config.templateType || 'waypoint',
      take_off_ref_point: takeoffRefPointStr,
      drone_type: config.droneType ?? DRONE_TYPE.M30,
      sub_drone_type: config.subDroneType ?? 0,
      payload_type: config.payloadType ?? PAYLOAD_TYPE.M30D,
      payload_sub_type: 0,
      payload_position: 0,
      image_format: (config.photoType && config.photoType.length > 0 ? config.photoType : ['visable']).join(','),
      finish_action: config.finishAction || 'goHome',
      exit_on_rc_lost_action: config.exitOnRCLost || 'goBack',
      global_height: config.rthAltitude || 100,
      auto_flight_speed: config.globalSpeed || 10,
      waypoint_heading_req: {
        waypoint_heading_mode: config.yawMode || 'followWayline'
      },
      waypoint_turn_req: {
        waypoint_turn_mode: 'toPointAndStopWithDiscontinuityCurvature'
      },
      gimbal_pitch_mode: config.gimbalPitchMode || 'usePointSetting',
      route_point_list
    };
  };

  /**
   * 一键保存航线至航线库 (Direct Save)
   */
  const saveWaylineDirectly = async (config: RouteGlobalConfig): Promise<boolean> => {
    if (waypoints.value.length === 0) {
      message.warning('当前未记录任何航点，无法生成航线');
      return false;
    }

    const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId);
    if (!workspaceId) {
      message.error('未找到工作空间ID，请重新登录');
      return false;
    }

    const waylineJson = buildWaylineJson(config);
    try {
      const res = await saveWaylineEdit(workspaceId, config.name, waylineJson);
      if (res && res.code === 0) {
        message.success(`🎉 航线【${config.name}】已成功生成并保存至航线库！`);
        return true;
      } else {
        message.error(`保存航线失败: ${res?.message || '未知错误'}`);
        return false;
      }
    } catch (err: any) {
      console.error('[WaypointRecorder] 保存航线失败:', err);
      message.error(`保存航线请求失败: ${err.message || '网络异常'}`);
      return false;
    }
  };

  /**
   * 跳转至 3D 航线编辑器进行精细化编辑 (Open in 3D Editor)
   */
  const openIn3DWaylineEditor = (config: RouteGlobalConfig) => {
    if (waypoints.value.length === 0) {
      message.warning('当前未记录任何航点，无法进入编辑器');
      return;
    }

    const waylineJson = buildWaylineJson(config);

    // 将完整的航线配置与打点数据保存至 sessionStorage / localStorage
    const recordedWaylinePayload = {
      fromCockpitRecorder: true,
      config: {
        name: config.name,
        routeType: config.templateType || 'waypoint',
        aircraftSeries: config.aircraftSeries ?? DRONE_TYPE.M30,
        aircraftModel: config.aircraftModel ?? PAYLOAD_TYPE.M30D,
        takeoffAirport: config.dockSn || '',
        takeoffLatitude: config.takeoffPoint?.latitude,
        takeoffLongitude: config.takeoffPoint?.longitude,
        takeoffHeight: config.takeoffPoint?.height || 0
      },
      settings: {
        globalSpeed: config.globalSpeed,
        flightHeight: config.rthAltitude,
        finishAction: config.finishAction,
        exitOnRCLost: config.exitOnRCLost,
        yawMode: config.yawMode,
        gimbalPitchMode: config.gimbalPitchMode,
        photoType: config.photoType
      },
      waylineData: waylineJson,
      waypointsList: waypoints.value
    };

    // 将完整的航线配置与打点数据保存至 sessionStorage 与 localStorage (跨窗口共享)
    sessionStorage.setItem('cockpitRecordedWayline', JSON.stringify(recordedWaylinePayload));
    localStorage.setItem('cockpitRecordedWayline', JSON.stringify(recordedWaylinePayload));
    localStorage.setItem('editWaylineData', JSON.stringify({
      waylineData: waylineJson,
      source: 'cockpitRecorder'
    }));

    message.loading({ content: '正在新窗口载入 3D 航线编辑器...', key: 'nav_3d_editor', duration: 1.5 });

    // 使用 router.resolve 解析新窗口 URL，在新标签页/窗口打开，避免关闭或刷新虚拟座舱
    const routeData = router.resolve({
      path: '/newWayLine',
      query: { from: 'cockpit', ts: Date.now() }
    });

    const newWindow = window.open(routeData.href, '_blank');
    if (newWindow) {
      newWindow.focus();
      message.success({ content: '已在新窗口打开 3D 航线编辑器', key: 'nav_3d_editor' });
    } else {
      // 兜底：若浏览器拦截新标签弹出，则提示用户或在当前页面打开
      window.location.href = routeData.href;
    }
  };

  /**
   * 导出标准 DJI WPML / JSON 文件
   */
  const exportWPMLFile = (config: RouteGlobalConfig) => {
    if (waypoints.value.length === 0) {
      message.warning('当前未记录任何航点');
      return;
    }
    const waylineJson = buildWaylineJson(config);
    const jsonStr = JSON.stringify(waylineJson, null, 2);
    const blob = new Blob([jsonStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${config.name || 'wayline'}_${Date.now()}.wpml.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    message.success('已成功导出 WPML 航线数据文件');
  };

  return {
    waypoints,
    isRecordingEnabled,
    totalCount,
    totalDistance,
    estimatedDuration,
    cesiumWaylinePoints,
    recordWaypoint,
    removeWaypoint,
    clearWaypoints,
    moveWaypoint,
    updateWaypoint,
    buildWaylineJson,
    saveWaylineDirectly,
    openIn3DWaylineEditor,
    exportWPMLFile
  };
}
