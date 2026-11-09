package com.dji.sdk.cloudapi.device;

public class HideUomRealNameState {
    private Integer uomRealNameState;

    public HideUomRealNameState() {
    }

    @Override
    public String toString() {
        return "HideUomRealNameState{" +
                "uomRealNameState=" + uomRealNameState +
                '}';
    }

    public Integer getUomRealNameState() {
        return uomRealNameState;
    }

    public void setUomRealNameState(Integer uomRealNameState) {
        this.uomRealNameState = uomRealNameState;
    }
}
