package com.dji.sample.psdk.model.dto;

import lombok.Data;

@Data
public class AeroSpotTransmissionRequest {
    private Integer type;

    private String data;

    public AeroSpotTransmissionRequest(String data) {
        this.type = 3;
        this.data = data;
    }
}
