package com.dji.sample.wayline.model.enums;

import lombok.Getter;

@Getter
public enum WaylineJobGroupByEnum {
    USERNAME("username"),

    DOCK_SN("dock_sn");

    private String field;

    WaylineJobGroupByEnum(String field) {this.field = field;}
}
