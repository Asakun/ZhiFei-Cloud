package com.dji.sample.psdk.model.enums;

public enum PsdkWidgetNameEnum {
    AERO_SPOT("PSJ_aeroSpot"),

    SNIFFER4D_NANO2("Sniffer4D·Nano2"),
    ;
    String name;

    PsdkWidgetNameEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static PsdkWidgetNameEnum find(String name) {
        for (PsdkWidgetNameEnum item : PsdkWidgetNameEnum.values()) {
            if (item.getName().equals(name)) {
                return item;
            }
        }
        return null;
    }
}
