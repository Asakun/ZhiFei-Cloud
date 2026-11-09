package com.dji.sample.psdk.model.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class WidgetValueSetDto {
    @NotNull
    private String dockSn;

    @NotNull
    private Integer psdkIndex;

    @NotNull
    private Integer index;

    @NotNull
    private Integer value;

    private String name;
}