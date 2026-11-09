package com.dji.sdk.cloudapi.livestream;

import com.dji.sdk.exception.CloudSDKException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public enum GatewayTypeEnum {
    DOCK("dock"),

    RC("rc");

    private final String type;

    GatewayTypeEnum(String type) {
        this.type = type;
    }

    @JsonValue
    public String getType() {
        return type;
    }

    @JsonCreator
    public static GatewayTypeEnum find(String gateway) {
        if (StringUtils.isEmpty(gateway)) {return DOCK;}
        return Arrays.stream(values()).filter(typeEnum -> typeEnum.type.equals(gateway)).findAny()
                .orElseThrow(() -> new CloudSDKException(GatewayTypeEnum.class , gateway));
    }
}
