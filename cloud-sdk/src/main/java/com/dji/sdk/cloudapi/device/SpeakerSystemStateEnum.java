package com.dji.sdk.cloudapi.device;

import com.dji.sdk.exception.CloudSDKException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum SpeakerSystemStateEnum {

    IDLE(0),

    TRANSFER_TO_DRONE(1),

    PLAYING(2),

    ABNORMAL(3),

    TTS_TEXT_CONVERSION(4),

    DOWNLOADING_FROM_CLOUD(5),

    NONE(65535),
    ;

    private final int state;

    SpeakerSystemStateEnum(int state) {
        this.state = state;
    }

    @JsonValue
    public int getState() {
        return state;
    }

    @JsonCreator
    public static SpeakerSystemStateEnum find(int state) {
        return Arrays.stream(values()).filter(stateEnum -> stateEnum.state == state).findAny()
                .orElseThrow(() -> new CloudSDKException(SpeakerSystemStateEnum.class, state));
    }
}
