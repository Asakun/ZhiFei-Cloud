package com.dji.sdk.cloudapi.psdk;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PsdkMethodEnum {
    PSDK_WIDGET_VALUE_SET("psdk_widget_value_set"),
    ;

    private final String method;

    PsdkMethodEnum(String method) {
        this.method = method;
    }

    @JsonValue
    public String getMethod() {
        return method;
    }
}
