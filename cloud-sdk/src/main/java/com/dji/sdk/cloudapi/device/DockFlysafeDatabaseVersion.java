package com.dji.sdk.cloudapi.device;

public class DockFlysafeDatabaseVersion {
    private String FlysafeDatabaseVersion;

    public DockFlysafeDatabaseVersion() {
    }

    @Override
    public String toString() {
        return "DockFlysafeDatabaseVersion{" +
                "FlysafeDatabaseVersion='" + FlysafeDatabaseVersion + '\'' +
                '}';
    }

    public String getFlysafeDatabaseVersion() {
        return FlysafeDatabaseVersion;
    }

    public void setFlysafeDatabaseVersion(String flysafeDatabaseVersion) {
        FlysafeDatabaseVersion = flysafeDatabaseVersion;
    }
}
