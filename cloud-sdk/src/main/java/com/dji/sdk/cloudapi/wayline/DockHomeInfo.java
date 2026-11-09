package com.dji.sdk.cloudapi.wayline;

public class DockHomeInfo {
    private String sn;

    private PlanStatusEnum planStatus;
    /**
     * 预估电量消耗	{"max":"100","min":"0","step":"1","unit_name":null}
     */
    private Integer estimatedBatteryConsumption;

    private Float homeDistance;

    public DockHomeInfo() {
    }

    @Override
    public String toString() {
        return "DockHomeInfo{" +
                "sn='" + sn + '\'' +
                ", planStatus=" + planStatus +
                ", estimatedBatteryConsumption=" + estimatedBatteryConsumption +
                ", homeDistance=" + homeDistance +
                '}';
    }

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }

    public PlanStatusEnum getPlanStatus() {
        return planStatus;
    }

    public void setPlanStatus(PlanStatusEnum planStatus) {
        this.planStatus = planStatus;
    }

    public Integer getEstimatedBatteryConsumption() {
        return estimatedBatteryConsumption;
    }

    public void setEstimatedBatteryConsumption(Integer estimatedBatteryConsumption) {
        this.estimatedBatteryConsumption = estimatedBatteryConsumption;
    }

    public Float getHomeDistance() {
        return homeDistance;
    }

    public void setHomeDistance(Float homeDistance) {
        this.homeDistance = homeDistance;
    }
}
