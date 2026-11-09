package com.dji.sdk.cloudapi.device;

import com.dji.sdk.exception.CloudSDKException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum DockLiveStatusEnum {
    NOT_LIVE(0),

    IN_LIVE(1);

    private final int code;

    DockLiveStatusEnum(int code) {
        this.code = code;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    @JsonCreator
    public static DockLiveStatusEnum find(int code) {
        return Arrays.stream(values()).filter(modeCode -> modeCode.code == code).findAny()
                .orElseThrow(() -> new CloudSDKException(DockLiveStatusEnum.class, code));
    }
}
