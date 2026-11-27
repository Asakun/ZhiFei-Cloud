package com.cleaner.djuav.enums.kml;

import lombok.Getter;

@Getter
public enum PayloadSubEnumValueEnums {
    TD(2, "TD 机型"),
    D(0, "D 机型");

    private Integer value;
    private String description;

    PayloadSubEnumValueEnums(Integer value, String description) {
        this.value = value;
        this.description = description;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
