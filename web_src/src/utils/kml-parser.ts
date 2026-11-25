import JSZip from 'jszip';

/**
 * 航点数据接口
 */
export interface WayPoint {
  index: number;
  longitude: number;
  latitude: number;
  height: number;
  ellipsoidHeight: number;
  speed: number;
}

/**
 * 航线数据接口
 */
export interface WaylineData {
  waypoints: WayPoint[];
  globalHeight: number;
  autoFlightSpeed: number;
  takeOffRefPoint: {
    longitude: number;
    latitude: number;
    height: number;
  };
}

/**
 * 解析 KMZ 文件（实际是 ZIP 格式）
 * @param file KMZ 文件的 Blob 对象
 * @returns template.kml 的文本内容
 */
export async function parseKmzFile(file: Blob): Promise<string> {
  const zip = new JSZip();
  const zipContent = await zip.loadAsync(file);
  
  // 查找 wpmz/template.kml 文件
  const kmlFile = zipContent.file('wpmz/template.kml');
  
  if (!kmlFile) {
    throw new Error('未找到 wpmz/template.kml 文件');
  }
  
  const kmlContent = await kmlFile.async('string');
  return kmlContent;
}

/**
 * 解析 KML XML 内容，提取航点坐标
 */
export function parseKmlContent(kmlContent: string): WaylineData {
  const parser = new DOMParser();
  const xmlDoc = parser.parseFromString(kmlContent, 'text/xml');
  
  const parserError = xmlDoc.querySelector('parsererror');
  if (parserError) {
    throw new Error('KML 文件解析失败');
  }
  
  const waypoints: WayPoint[] = [];
  
  // 获取全局配置
  const globalHeight = parseFloat(xmlDoc.querySelector('wpml\\:globalHeight, globalHeight')?.textContent || '0');
  const autoFlightSpeed = parseFloat(xmlDoc.querySelector('wpml\\:autoFlightSpeed, autoFlightSpeed')?.textContent || '0');
  
  // 获取起飞参考点（格式: 纬度,经度,高度）
  const takeOffRefPointText = xmlDoc.querySelector('wpml\\:takeOffRefPoint, takeOffRefPoint')?.textContent || '0,0,0';
  const refCoords = takeOffRefPointText.split(',').map(v => parseFloat(v.trim()));
  const refLat = refCoords[0] || 0;
  const refLon = refCoords[1] || 0;
  const refHeight = refCoords[2] || 0;
  
  // 获取所有航点
  const placemarks = xmlDoc.querySelectorAll('Placemark');
  
  placemarks.forEach((placemark) => {
    const indexElement = placemark.querySelector('wpml\\:index, index');
    if (!indexElement) return;
    
    const index = parseInt(indexElement.textContent || '0');
    
    const coordinatesElement = placemark.querySelector('coordinates');
    if (!coordinatesElement) return;
    
    // 清除空格和换行，解析坐标
    const coordinatesText = coordinatesElement.textContent?.replace(/\s+/g, '') || '';
    const coords = coordinatesText.split(',').map(v => parseFloat(v.trim()));
    
    if (coords.length < 2) return;
    
    const heightElement = placemark.querySelector('wpml\\:height, height');
    const ellipsoidHeightElement = placemark.querySelector('wpml\\:ellipsoidHeight, ellipsoidHeight');
    const speedElement = placemark.querySelector('wpml\\:waypointSpeed, waypointSpeed');
    
    waypoints.push({
      index,
      longitude: coords[0],
      latitude: coords[1],
      height: parseFloat(heightElement?.textContent || '0'),
      ellipsoidHeight: parseFloat(ellipsoidHeightElement?.textContent || '0'),
      speed: parseFloat(speedElement?.textContent || '0')
    });
  });
  
  waypoints.sort((a, b) => a.index - b.index);
  
  return {
    waypoints,
    globalHeight,
    autoFlightSpeed,
    takeOffRefPoint: {
      longitude: refLon,
      latitude: refLat,
      height: refHeight
    }
  };
}

/**
 * 完整的 KMZ 文件解析流程
 * @param file KMZ 文件的 Blob 对象
 * @returns 航线数据
 */
export async function parseWaylineFile(file: Blob): Promise<WaylineData> {
  const kmlContent = await parseKmzFile(file);
  const waylineData = parseKmlContent(kmlContent);
  return waylineData;
}
