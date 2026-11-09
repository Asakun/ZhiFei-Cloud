package com.dji.sample.wayline.model.enums;

import lombok.Getter;

@Getter
public enum WaylineJobSortEnum {
    TOTAL_TIME("total_time"),

    TOTAL_DISTANCE("total_distance"),

    TOTAL_COUNT("total_count");

    private String field;

    WaylineJobSortEnum(String field) {this.field = field;}
}
