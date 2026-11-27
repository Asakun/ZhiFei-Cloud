package com.cleaner.djuav.util.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Gps {

    /**
     * 经度
     */
    private double wgLon;

    /**
     * 纬度
     */
    private double wgLat;

    public Gps() {

    }
}
