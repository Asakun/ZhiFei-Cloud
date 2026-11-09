package com.dji.sdk.cloudapi.device;

public class MobilityStatusNotify {
    private Integer MobilityStatusNotify;

    public MobilityStatusNotify() {
    }

    @Override
    public String toString() {
        return "MobilityStatusNotify{" +
                "MobilityStatusNotify=" + MobilityStatusNotify +
                '}';
    }

    public Integer getMobilityStatusNotify() {
        return MobilityStatusNotify;
    }

    public void setMobilityStatusNotify(Integer mobilityStatusNotify) {
        MobilityStatusNotify = mobilityStatusNotify;
    }
}
