package com.dji.sdk.cloudapi.device;

public class PsdkUiResource {
    private Integer psdkIndex;

    private ReadyOrNotEnum psdkReady;

    private String objectKey;

    public PsdkUiResource() {
    }

    @Override
    public String toString() {
        return "PsdkUiResource{" +
                "psdkIndex=" + psdkIndex +
                ", psdkReady=" + psdkReady +
                ", objectKey='" + objectKey + '\'' +
                '}';
    }

    public Integer getPsdkIndex() {
        return psdkIndex;
    }

    public void setPsdkIndex(Integer psdkIndex) {
        this.psdkIndex = psdkIndex;
    }

    public ReadyOrNotEnum getPsdkReady() {
        return psdkReady;
    }

    public void setPsdkReady(ReadyOrNotEnum psdkReady) {
        this.psdkReady = psdkReady;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }
}
