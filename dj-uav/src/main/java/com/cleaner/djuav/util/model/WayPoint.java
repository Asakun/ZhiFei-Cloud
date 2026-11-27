package com.cleaner.djuav.util.model;

import com.cleaner.djuav.enums.WayPointTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
public class WayPoint extends Gps{
    private double height;

    private WayPointTypeEnum type;

    public WayPoint() {
    }

    public WayPoint(String takeOffRefPoint) {
        String[] split = takeOffRefPoint.split(",");
        this.setWgLat(Double.parseDouble(split[0]));
        this.setWgLon(Double.parseDouble(split[1]));
        this.height = Double.parseDouble(split[2]);
        this.type = WayPointTypeEnum.NORMAL;
    }
}
