// 航线类型 (template_types 为 0 是航点航线，为 1 是面状航线)
export enum WaylineType {
  NormalWaypointWayline = 0, // 航点航线
  Mapping2d = 1, // 面状航线 (建图航线)
  AccurateReshootingWayline = 2, // 精准复拍航线
  noWayLine = -1, // 无航线
}

export interface WaylineFile {
  id: string,
  name: string,
  drone_model_key: any,
  payload_model_keys: string[],
  template_types: WaylineType[],
  update_time: number,
  user_name: string,
  dock_sn: string,
}
