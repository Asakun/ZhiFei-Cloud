package com.cleaner.djuav.enums;

/**
 * 航点类型枚举
 */
public enum WayPointTypeEnum {

    NORMAL(0, "普通点"),
    START_PHOTO(1, "开始拍照"),
    END_PHOTO(2, "结束拍照"),
    ELEVATION_OPTIMIZE(3, "高程优化");

    private final int value;
    private final String description;

    WayPointTypeEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static WayPointTypeEnum find(int value) {
        for (WayPointTypeEnum typeEnum : values()) {
            if (typeEnum.value == value) {
                return typeEnum;
            }
        }
        return NORMAL;
    }
}
