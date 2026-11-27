package com.cleaner.djuav.domain;

import com.cleaner.djuav.enums.kml.GlobalWaypointTurnModeEnums;
import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Author:Cleaner
 * Date: 2024/12/22 13:05
 **/
@Data
public class WaypointTurnReq implements Serializable {

    /**
     * 航点转弯模式
     */
    private GlobalWaypointTurnModeEnums waypointTurnMode;

    /**
     * 航点转弯截距
     */
    private Double waypointTurnDampingDist = 0.;

    /**
     * 该航段是否贴合直线
     */
    private Integer useStraightLine;

    public void setWaypointTurnMode(String key) {
        this.waypointTurnMode = Arrays.stream(GlobalWaypointTurnModeEnums.values()).filter(format -> format.getValue().equals(key)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("GlobalWaypointTurnModeEnums not found:"+key));
    }

    public String getWaypointTurnMode() {
        return waypointTurnMode.getValue();
    }
}
