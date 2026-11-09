package com.dji.sample.wayline.model.dto;

import cn.hutool.core.util.ObjectUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DuringFlightInfo {
    private String flightId;

    private String droneSn;

    private Integer mediaCount;

    private String toStopCameraId;

    private String toUploadAeroSpotKey;

    @Builder.Default
    private Boolean enablePsdkFloatingWindowTextRecord = true;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false; // 判空 + 判类型

        DuringFlightInfo that = (DuringFlightInfo) o;

        return ObjectUtil.equal(getFlightId(), that.getFlightId())
                && ObjectUtil.equal(getDroneSn(), that.getDroneSn())
                && ObjectUtil.equal(getMediaCount(), that.getMediaCount())
                && ObjectUtil.equal(getToStopCameraId(), that.getToStopCameraId())
                && ObjectUtil.equal(getToUploadAeroSpotKey(), that.getToUploadAeroSpotKey())
                && ObjectUtil.equal(getEnablePsdkFloatingWindowTextRecord(), that.getEnablePsdkFloatingWindowTextRecord());
    }
}
