package com.dji.sample.psdk.model.enums;

public enum AeroSpotValueEnum {
    ONE_PICK(8),

    CONTINUOUS_PICK_START(10),

    CONTINUOUS_PICK_STOP(11),

    QUERY_INFO(12),

    SD_FORMAT(14),

    NEW_FOLDER(15),
    ;
    Integer value;

    AeroSpotValueEnum(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
