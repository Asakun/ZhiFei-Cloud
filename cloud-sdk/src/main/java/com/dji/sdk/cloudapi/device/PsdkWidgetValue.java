package com.dji.sdk.cloudapi.device;

import java.util.List;

public class PsdkWidgetValue {
    private Integer psdkIndex;

    private String psdkName;

    private String psdkSn;

    private String psdkVersion;

    private String psdkLibVersion;

    private Integer psdkType;

    private Speaker speaker;

    private List<SpeakerControlValue> values;

    public PsdkWidgetValue() {
    }

    @Override
    public String toString() {
        return "PsdkWidgetValue{" +
                "psdkIndex=" + psdkIndex +
                ", psdkName='" + psdkName + '\'' +
                ", psdkSn='" + psdkSn + '\'' +
                ", psdkVersion='" + psdkVersion + '\'' +
                ", psdkLibVersion='" + psdkLibVersion + '\'' +
                ", speaker=" + speaker +
                ", values=" + values +
                '}';
    }

    public Integer getPsdkIndex() {
        return psdkIndex;
    }

    public void setPsdkIndex(Integer psdkIndex) {
        this.psdkIndex = psdkIndex;
    }

    public String getPsdkName() {
        return psdkName;
    }

    public void setPsdkName(String psdkName) {
        this.psdkName = psdkName;
    }

    public String getPsdkSn() {
        return psdkSn;
    }

    public void setPsdkSn(String psdkSn) {
        this.psdkSn = psdkSn;
    }

    public String getPsdkVersion() {
        return psdkVersion;
    }

    public void setPsdkVersion(String psdkVersion) {
        this.psdkVersion = psdkVersion;
    }

    public String getPsdkLibVersion() {
        return psdkLibVersion;
    }

    public void setPsdkLibVersion(String psdkLibVersion) {
        this.psdkLibVersion = psdkLibVersion;
    }

    public Integer getPsdkType() {
        return psdkType;
    }

    public void setPsdkType(Integer psdkType) {
        this.psdkType = psdkType;
    }

    public Speaker getSpeaker() {
        return speaker;
    }

    public void setSpeaker(Speaker speaker) {
        this.speaker = speaker;
    }

    public List<SpeakerControlValue> getValues() {
        return values;
    }

    public void setValues(List<SpeakerControlValue> values) {
        this.values = values;
    }
}
