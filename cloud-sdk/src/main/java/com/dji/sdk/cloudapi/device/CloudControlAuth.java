package com.dji.sdk.cloudapi.device;

import java.util.List;

public class CloudControlAuth {
    List<String> cloudControlAuth;

    public CloudControlAuth() {
    }

    @Override
    public String toString() {
        return "CloudControlAuth{" +
                "cloudControlAuth=" + cloudControlAuth +
                '}';
    }

    public List<String> getCloudControlAuth() {
        return cloudControlAuth;
    }

    public void setCloudControlAuth(List<String> cloudControlAuth) {
        this.cloudControlAuth = cloudControlAuth;
    }
}
