package com.dji.sdk.cloudapi.device;

import com.dji.sdk.exception.CloudSDKException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum SpeakerPlayModeEnum {

    PLAY_MODE(0),

    LOOP_MODE(1),

    NONE(65535);
    ;

    private final int mode;

    SpeakerPlayModeEnum(int mode) {
        this.mode = mode;
    }

    @JsonValue
    public int getMode() {
        return mode;
    }

    @JsonCreator
    public static SpeakerPlayModeEnum find(int mode) {
        return Arrays.stream(values()).filter(modeEnum -> modeEnum.mode == mode).findAny()
                .orElseThrow(() -> new CloudSDKException(SpeakerPlayModeEnum.class, mode));
    }
}
