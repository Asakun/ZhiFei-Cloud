package com.dji.sdk.cloudapi.device;

public class LeafNodes extends CenterNode {
    private Integer controlSourceIndex;

    private Boolean valid;

    @Override
    public String toString() {
        return "LeafNodes{" +
                "controlSourceIndex=" + controlSourceIndex +
                ", valid=" + valid +
                ", sdrId=" + sdrId +
                ", sn='" + sn + '\'' +
                '}';
    }

    public Integer getControlSourceIndex() {
        return controlSourceIndex;
    }

    public void setControlSourceIndex(Integer controlSourceIndex) {
        this.controlSourceIndex = controlSourceIndex;
    }

    public Boolean getValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }
}
