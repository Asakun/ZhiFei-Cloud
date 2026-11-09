package com.dji.sdk.cloudapi.device;

public class CapabilitySet {
    CloudControlAuthSupport cloudControlAuth;

    public CapabilitySet() {
    }

    @Override
    public String toString() {
        return "CapabilitySet{" +
                "cloudControlAuth=" + cloudControlAuth +
                '}';
    }

    public CloudControlAuthSupport getCloudControlAuth() {
        return cloudControlAuth;
    }

    public void setCloudControlAuth(CloudControlAuthSupport cloudControlAuth) {
        this.cloudControlAuth = cloudControlAuth;
    }
}
