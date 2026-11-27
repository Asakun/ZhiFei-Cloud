package com.cleaner.djuav.domain;

import com.cleaner.djuav.enums.kml.WaypointHeadingModeEnums;
import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Author:Cleaner
 * Date: 2024/12/22 13:05
 **/
@Data
public class WaypointHeadingReq implements Serializable {

    /**
     * 偏航角模式
     */
    private WaypointHeadingModeEnums waypointHeadingMode;

    /**
     * 偏航角度
     */
    private Double waypointHeadingAngle;

    /**
     * 兴趣点
     */
    private String waypointPoiPoint;

    public void setWaypointHeadingMode(String key) {
        this.waypointHeadingMode = Arrays.stream(WaypointHeadingModeEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("WaypointHeadingModeEnums not found:"+key));
    }

    public String getWaypointHeadingMode() {
        return waypointHeadingMode.getValue();
    }
}
