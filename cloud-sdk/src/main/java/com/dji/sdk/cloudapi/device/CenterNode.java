package com.dji.sdk.cloudapi.device;

public class CenterNode {
    public Integer sdrId;

    public String sn;

    @Override
    public String toString() {
        return "CenterNode{" +
                "sdrId=" + sdrId +
                ", sn='" + sn + '\'' +
                '}';
    }

    public Integer getSdrId() {
        return sdrId;
    }

    public void setSdrId(Integer sdrId) {
        this.sdrId = sdrId;
    }

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }
}

