package com.dji.sdk.cloudapi.device;

public class DroneRemainingPowerForReturnHome {
    private Integer RemainingPowerForReturnHome;

    public DroneRemainingPowerForReturnHome() {
    }

    @Override
    public String toString() {
        return "DroneRemainingPowerForReturnHome{" +
                "RemainingPowerForReturnHome=" + RemainingPowerForReturnHome +
                '}';
    }

    public Integer getRemainingPowerForReturnHome() {
        return RemainingPowerForReturnHome;
    }

    public void setRemainingPowerForReturnHome(Integer remainingPowerForReturnHome) {
        RemainingPowerForReturnHome = remainingPowerForReturnHome;
    }
}
