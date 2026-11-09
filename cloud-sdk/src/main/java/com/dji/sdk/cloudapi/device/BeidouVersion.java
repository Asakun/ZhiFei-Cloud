package com.dji.sdk.cloudapi.device;

public class BeidouVersion {
    private Boolean isBeidouVersion;

    public BeidouVersion() {}

    @Override
    public String toString() {
        return "BeidouVersion{" +
                "isBeidouVersion=" + isBeidouVersion +
                '}';
    }

    public Boolean getBeidouVersion() {
        return isBeidouVersion;
    }

    public void setBeidouVersion(Boolean beidouVersion) {
        isBeidouVersion = beidouVersion;
    }
}
