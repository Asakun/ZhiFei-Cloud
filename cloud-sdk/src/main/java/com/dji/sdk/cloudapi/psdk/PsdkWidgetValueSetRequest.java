package com.dji.sdk.cloudapi.psdk;

public class PsdkWidgetValueSetRequest {
    private Integer psdkIndex;

    private Integer index;

    private Integer value;

    public PsdkWidgetValueSetRequest() {
    }

    @Override
    public String toString() {
        return "PsdkWidgetValueSet{" +
                "psdkIndex=" + psdkIndex +
                ", index=" + index +
                ", value=" + value +
                '}';
    }

    public Integer getPsdkIndex() {
        return psdkIndex;
    }

    public PsdkWidgetValueSetRequest setPsdkIndex(Integer psdkIndex) {
        this.psdkIndex = psdkIndex;
        return this;
    }

    public Integer getIndex() {
        return index;
    }

    public PsdkWidgetValueSetRequest setIndex(Integer index) {
        this.index = index;
        return this;
    }

    public Integer getValue() {
        return value;
    }

    public PsdkWidgetValueSetRequest setValue(Integer value) {
        this.value = value;
        return this;
    }
}
