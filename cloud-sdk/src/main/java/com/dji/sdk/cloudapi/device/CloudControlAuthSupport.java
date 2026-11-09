package com.dji.sdk.cloudapi.device;

public class CloudControlAuthSupport {
    Boolean support;

    public CloudControlAuthSupport() {
    }

    @Override
    public String toString() {
        return "CloudControlAuthSupport{" +
                "support=" + support +
                '}';
    }

    public Boolean getSupport() {
        return support;
    }

    public void setSupport(Boolean support) {
        this.support = support;
    }
}
