package com.cleaner.djuav.enums.kml;

public enum SubDroneEnumValueEnums {
    TD(1, "三摄系统（广角+长焦+红外热成像）"),
    D(0, "双相机系统（4/3 CMOS广角+长焦）");

    private Integer value;
    private String description;

    SubDroneEnumValueEnums(Integer value, String description) {
        this.value = value;
        this.description = description;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

