package com.dji.sdk.cloudapi.control;

public class CloudControlAuthNotify {
    private String status;

    public CloudControlAuthNotify() {
    }

    @Override
    public String toString() {
        return "CloudControlAuthNotify{" +
                "status='" + status + '\'' +
                '}';
    }

    public String getStatus() {
        return status;
    }

    public CloudControlAuthNotify setStatus(String status) {
        this.status = status;
        return this;
    }
}
