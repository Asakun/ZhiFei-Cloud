package com.dji.sdk.cloudapi.device;

public class SpeakerControlValue {
    private Integer index;

    private Integer value;

    public SpeakerControlValue() {
    }

    @Override
    public String toString() {
        return "SpeakerControlValue{" +
                "index=" + index +
                ", value=" + value +
                '}';
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
