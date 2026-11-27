package com.cleaner.djuav.enums.kml;

/**
 * wpml:gimbalHeadingYawBase	 云台偏航角基准
 */
public enum GimbalHeadingYawBaseEnums {

    NORTH("north", "正北。云台偏航角以地理正北为0°，gimbalYawRotateAngle表示相对正北的绝对角度"),
    AIRCRAFT("aircraft", "机头。云台偏航角以飞机当前机头方向为0°，gimbalYawRotateAngle表示相对机头的角度");

    private String value;
    private String description;

    GimbalHeadingYawBaseEnums(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
