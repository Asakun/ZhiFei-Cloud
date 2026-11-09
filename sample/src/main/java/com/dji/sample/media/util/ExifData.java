package com.dji.sample.media.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExifData {

    private double cameraLat;

    private double cameraLon;

    private double relativeAltitude;

    private double fov;

    private double gimbalPitch;

    private double gimbalYaw;

    private double gimbalRoll;

    private int imageWidth;

    private int imageHeight;

    private Double lrfTargetLat;

    private Double lrfTargetLon;

    private Double lrfTargetDistance;
}
