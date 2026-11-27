package com.cleaner.djuav.enums;

public enum DeviceFovEnum {
//    M3D(80, "Matrice 3D 相机"),
    M3TD(81, 73.7f),
//    M4E(88, "M4E 相机"),
    M4T(89, 73.7f),
//    M4D(98, "M4D 相机"),
    M4TD(99, 73.7f);

    private final Integer payloadType;
    // HFOV degree
    private final Float degree;

    DeviceFovEnum(Integer payloadType, Float degree) {
        this.payloadType = payloadType;
        this.degree = degree;
    }

    public Integer getPayloadType() {
        return payloadType;
    }

    public Float getDegree() {
        return degree;
    }

    public static float findDegreeByType(int type) {
        for (DeviceFovEnum fov : values()) {
            if (fov.payloadType == type) {
                return fov.degree;
            }
        }
        throw new IllegalArgumentException("DeviceFovEnum not found: " + type);
    }
}
