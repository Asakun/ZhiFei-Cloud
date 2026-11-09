package com.dji.sdk.cloudapi.device;

public class Speaker {
    private SpeakerWorkModeEnum workMode;

    private SpeakerPlayModeEnum playMode;

    private Integer playVolume;

    private SpeakerSystemStateEnum systemState;

    private String playFileName;

    private String playFileMd5;

    public Speaker() {
    }

    @Override
    public String toString() {
        return "Speaker{" +
                "workMode=" + workMode +
                ", playMode=" + playMode +
                ", playVolume=" + playVolume +
                ", systemState=" + systemState +
                ", playFileName='" + playFileName + '\'' +
                ", playFileMd5='" + playFileMd5 + '\'' +
                '}';
    }

    public SpeakerWorkModeEnum getWorkMode() {
        return workMode;
    }

    public void setWorkMode(SpeakerWorkModeEnum workMode) {
        this.workMode = workMode;
    }

    public SpeakerPlayModeEnum getPlayMode() {
        return playMode;
    }

    public void setPlayMode(SpeakerPlayModeEnum playMode) {
        this.playMode = playMode;
    }

    public Integer getPlayVolume() {
        return playVolume;
    }

    public void setPlayVolume(Integer playVolume) {
        this.playVolume = playVolume;
    }

    public SpeakerSystemStateEnum getSystemState() {
        return systemState;
    }

    public void setSystemState(SpeakerSystemStateEnum systemState) {
        this.systemState = systemState;
    }

    public String getPlayFileName() {
        return playFileName;
    }

    public void setPlayFileName(String playFileName) {
        this.playFileName = playFileName;
    }

    public String getPlayFileMd5() {
        return playFileMd5;
    }

    public void setPlayFileMd5(String playFileMd5) {
        this.playFileMd5 = playFileMd5;
    }
}
