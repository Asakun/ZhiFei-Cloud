package com.dji.sdk.cloudapi.device;

import java.util.List;

public class PsdkWidgetValues {

    private List<PsdkWidgetValue> psdkWidgetValues;

    public PsdkWidgetValues() {
    }

    @Override
    public String toString() {
        return "PsdkWidgetValues{" +
                "psdkWidgetValues=" + psdkWidgetValues +
                '}';
    }

    public List<PsdkWidgetValue> getPsdkWidgetValues() {
        return psdkWidgetValues;
    }

    public PsdkWidgetValues setPsdkWidgetValues(List<PsdkWidgetValue> psdkWidgetValues) {
        this.psdkWidgetValues = psdkWidgetValues;
        return this;
    }
}
