package com.dji.sample.control.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DroneControlKeyEnum {
    FLIGHT("flight");

    private String key;

    DroneControlKeyEnum(String key) {
        this.key = key;
    }

    @JsonValue
    public String getKey() {
        return key;
    }
}
