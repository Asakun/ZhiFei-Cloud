/* eslint-disable @typescript-eslint/no-explicit-any */

export interface AMapPoiGeocoderOptions {
    apiKey: string;
    city?: string;
}

export interface AMapPoi {
    name: string;
    address: string;
    location: string;
}

export interface AMapPoiResponse {
    pois?: AMapPoi[];
}

export interface CesiumGeocodeResult {
    displayName: string;
    destination: any; // Cesium.Cartesian3
}

export interface CesiumReverseGeocodeResult {
    displayName: string;
    longitude: number;
    latitude: number;
}

import * as Cesium from 'cesium';

export default class AMapPoiGeocoderService {
    apiKey: string;
    city: string;

    constructor(options: AMapPoiGeocoderOptions) {
        this.apiKey = options.apiKey;
        this.city = options.city ?? "";
    }

    /**
     * 🔍 POI 关键词搜索
     */
    geocode(keyword: string): Promise<CesiumGeocodeResult[]> {
        const url = `https://restapi.amap.com/v5/place/text?key=${this.apiKey}&keywords=${encodeURIComponent(
            keyword
        )}&city=${this.city}&page_size=10`;

        return fetch(url)
            .then((r) => r.json())
            .then((res: AMapPoiResponse) => {
                if (!res.pois || res.pois.length === 0) return [];

                return res.pois
                    .filter((p) => p.location)
                    .map((poi) => {
                        const [lng, lat] = poi.location.split(",").map(Number);

                        return {
                            displayName: `${poi.name}（${poi.address}）`,
                            destination: Cesium.Cartesian3.fromDegrees(lng, lat, 50)
                        } as CesiumGeocodeResult;
                    });
            });
    }

    /**
     * ♻ 逆地理编码
     */
    reverseGeocode(
        lng: number,
        lat: number
    ): Promise<CesiumReverseGeocodeResult> {
        const url = `https://restapi.amap.com/v3/geocode/regeo?key=${this.apiKey}&location=${lng},${lat}&extensions=all`;

        return fetch(url)
            .then((r) => r.json())
            .then((res: any) => {
                const addr = res?.regeocode?.formatted_address ?? "未知位置";

                return {
                    displayName: addr,
                    longitude: lng,
                    latitude: lat
                };
            });
    }
}
