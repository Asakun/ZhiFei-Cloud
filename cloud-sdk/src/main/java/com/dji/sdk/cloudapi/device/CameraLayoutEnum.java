package com.dji.sdk.cloudapi.device;

import com.dji.sdk.exception.CloudSDKException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum CameraLayoutEnum {
    TOP_LEFT(0),

    BOTTOM_LEFT(1),

    TOP_RIGHT(2),

    BOTTOM_RIGHT(3),
    ;

    private final int layout;

    CameraLayoutEnum(int layout) {
        this.layout = layout;
    }

    @JsonValue
    public int getLayout() {
        return layout;
    }

    @JsonCreator
    public static CameraLayoutEnum find(int layout) {
        return Arrays.stream(values()).filter(layoutEnum -> layoutEnum.layout == layout).findAny()
                .orElseThrow(() -> new CloudSDKException(CameraLayoutEnum.class, layout));
    }
}
