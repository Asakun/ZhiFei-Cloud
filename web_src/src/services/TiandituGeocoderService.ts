/* eslint-disable @typescript-eslint/no-explicit-any */

import * as Cesium from 'cesium';
import { getElementGroupsReq } from '@/api/layer';

export interface LocalPoint {
    name: string;
    longitude: number;
    latitude: number;
    height?: number;
    address?: string;
}

export interface TDTGeocoderOptions {
    key: string;        // 天地图 key (tk)
    specify?: string;   // 行政区划代码 (例如：'156500000' 为重庆市。若不传，则为全国范围搜索)
    getLocalPoints?: () => LocalPoint[];
}

export interface TDTSearchResult {
    name: string;
    address: string;
    lonlat: string;
}

export interface CesiumGeocodeResult {
    displayName: string;
    destination: any;
}

export default class TiandituGeocoderService {
    key: string;
    specify?: string;
    getLocalPoints?: () => LocalPoint[];

    private cachedLocalPoints: LocalPoint[] = [];
    private lastFetchTime: number = 0;

    constructor(options: TDTGeocoderOptions) {
        this.key = options.key;
        this.specify = options.specify;
        this.getLocalPoints = options.getLocalPoints;
    }

    /**
     * 🔄 自动从后台接口加载并缓存地图标注点
     */
    private async fetchLocalPoints(): Promise<LocalPoint[]> {
        const now = Date.now();
        // 缓存 10 秒以避免频繁触发接口请求
        if (this.cachedLocalPoints.length > 0 && now - this.lastFetchTime < 10000) {
            return this.cachedLocalPoints;
        }

        try {
            const res = await getElementGroupsReq({});
            const list: LocalPoint[] = [];
            if (res && res.code === 0 && Array.isArray(res.data)) {
                res.data.forEach((group: any) => {
                    if (group && Array.isArray(group.elements)) {
                        group.elements.forEach((el: any) => {
                            if (el && el.resource?.content?.geometry) {
                                const geo = el.resource.content.geometry;
                                if (geo.type === 'Point' && Array.isArray(geo.coordinates) && geo.coordinates.length >= 2) {
                                    list.push({
                                        name: el.name || '未命名标注点',
                                        longitude: Number(geo.coordinates[0]),
                                        latitude: Number(geo.coordinates[1]),
                                        height: geo.height !== undefined ? Number(geo.height) : undefined,
                                        address: group.name ? `图层: ${group.name}` : '项目标注'
                                    });
                                }
                            }
                        });
                    }
                });
            }
            this.cachedLocalPoints = list;
            this.lastFetchTime = now;
            return list;
        } catch (e) {
            console.error("天地图服务获取本地项目标注点失败:", e);
            return this.cachedLocalPoints; // 失败时返回旧缓存，保证高可用
        }
    }

    /**
     * 🔍 天地图关键字 POI 搜索 (包含本地项目标注点搜索)
     */
    async geocode(keyword: string): Promise<CesiumGeocodeResult[]> {
        // 1. 获取本地项目标注点 (合并静态回调与动态 API 接口获取的标注点)
        let localPoints: LocalPoint[] = [];
        
        let callbackPoints: LocalPoint[] = [];
        if (this.getLocalPoints) {
            try {
                callbackPoints = this.getLocalPoints();
            } catch (e) {
                console.error("通过回调获取本地标注点失败:", e);
            }
        }
        
        const apiPoints = await this.fetchLocalPoints();
        const mergedPoints = [...callbackPoints, ...apiPoints];

        // 🔍 对本地点位进行唯一性去重 (根据 经度 + 纬度 + 名称)
        const seen = new Set<string>();
        for (const p of mergedPoints) {
            if (!p) continue;
            // 通过经纬度坐标与名称组合生成唯一 Key，保留精确到小数点后 5 位（约 1 米精度）
            const key = `${p.name}_${Number(p.longitude).toFixed(5)}_${Number(p.latitude).toFixed(5)}`;
            if (!seen.has(key)) {
                seen.add(key);
                localPoints.push(p);
            }
        }

        // 2. 本地项目标注点模糊匹配
        let localResults: CesiumGeocodeResult[] = [];
        if (keyword) {
            const kw = keyword.trim().toLowerCase();
            if (kw) {
                const filtered = localPoints.filter(
                    p => p && p.name && p.name.toLowerCase().includes(kw)
                );
                localResults = filtered.map(p => {
                    const height = p.height !== undefined && !isNaN(Number(p.height)) ? Number(p.height) : 150;
                    return {
                        displayName: `📍 [项目标注] ${p.name}${p.address ? `（${p.address}）` : ''}`,
                        destination: Cesium.Cartesian3.fromDegrees(Number(p.longitude), Number(p.latitude), height)
                    };
                });
            }
        }

        // 3. 调用天地图网络检索
        const postParams: any = {
            keyWord: keyword,
            mapBound: '-180,-90,180,90',
            level: 16,
            queryType: 1, // 1 = 地名地址
            start: 0,
            count: 300,
        };

        if (this.specify) {
            postParams.specify = this.specify;
        }

        const url = `https://api.tianditu.gov.cn/v2/search?postStr=${encodeURIComponent(
            JSON.stringify(postParams)
        )}&type=query&tk=${this.key}`;

        try {
            const response = await fetch(url);
            const res = await response.json();
            const pois: TDTSearchResult[] = res?.pois || [];
            const remoteResults = pois
                .filter((poi) => poi && poi.lonlat)
                .map((poi) => {
                    const [lng, lat] = poi.lonlat.split(",").map(Number);
                    return {
                        displayName: `${poi.name}${poi.address ? `（${poi.address}）` : ''}`,
                        destination: Cesium.Cartesian3.fromDegrees(lng, lat, 150)
                    };
                });

            // 本地标注点匹配结果优先排在最前
            return [...localResults, ...remoteResults];
        } catch (err) {
            console.error("天地图检索接口请求失败:", err);
            return localResults; // 网络失败时降级返回本地匹配到的项目标注点
        }
    }

    /**
     * ♻ 逆地理编码
     */
    reverseGeocode(lng: number, lat: number) {
        const url = `https://api.tianditu.gov.cn/geocoder?location=${lat},${lng}&tk=${this.key}`;

        return fetch(url)
            .then((r) => r.json())
            .then((res) => {
                const addr = res?.result?.formatted_address || "未知位置";

                return {
                    displayName: addr,
                    longitude: lng,
                    latitude: lat
                };
            })
            .catch((err) => {
                console.error("天地图逆地理编码失败:", err);
                return {
                    displayName: "未知位置",
                    longitude: lng,
                    latitude: lat
                };
            });
    }
}
