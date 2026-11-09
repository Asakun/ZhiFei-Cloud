package com.dji.sample.psdk.model.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class AeroSpotUploadParam {
    @NotNull
    private String deviceSn;
}
