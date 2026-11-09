package com.dji.sdk.cloudapi.wayline;

import com.dji.sdk.exception.CloudSDKException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum PlanStatusEnum {
    FAILED_OR_BEING_PANNED(0),

    PATH_UNREACHABLE(1),

    POWER_SHORTAGE_UNREACHABLE(2),

    DESTINATION_REACHABLE(3),
    ;

    private final int type;

    PlanStatusEnum(int type) {
        this.type = type;
    }

    @JsonValue
    public int getType() {
        return type;
    }

    @JsonCreator
    public static PlanStatusEnum find(int type) {
        return Arrays.stream(values()).filter(typeEnum -> typeEnum.type == type).findAny()
                .orElseThrow(() -> new CloudSDKException(ActionTypeEnum.class, type));
    }
}
