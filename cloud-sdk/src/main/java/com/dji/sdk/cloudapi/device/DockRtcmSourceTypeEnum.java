package com.dji.sdk.cloudapi.device;

import com.dji.sdk.exception.CloudSDKException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum DockRtcmSourceTypeEnum {
    UNCALIBRATED(0),

    SELF_CONVERGENCE_CALIBRATED(1),

    MANUAL_CALIBRATION(2),

    RTK_CALIBRATION(3),
    ;

    private final int type;

    DockRtcmSourceTypeEnum(int type) {
        this.type = type;
    }

    @JsonValue
    public int getType() {
        return type;
    }

    @JsonCreator
    public static DockRtcmSourceTypeEnum find(int type) {
        return Arrays.stream(values()).filter(typeEnum -> typeEnum.type == type).findAny()
                .orElseThrow(() -> new CloudSDKException(DongleTypeEnum.class, type));
    }
}
