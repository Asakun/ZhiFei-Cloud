package com.dji.sdk.cloudapi.device;

public class HideArInfoSwitch {
    private Integer arInfoSwitch;

    public HideArInfoSwitch() {
    }

    @Override
    public String toString() {
        return "HideArInfoSwitch{" +
                "arInfoSwitch=" + arInfoSwitch +
                '}';
    }

    public Integer getArInfoSwitch() {
        return arInfoSwitch;
    }

    public void setArInfoSwitch(Integer arInfoSwitch) {
        this.arInfoSwitch = arInfoSwitch;
    }
}
