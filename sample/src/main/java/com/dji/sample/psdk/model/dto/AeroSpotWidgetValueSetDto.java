package com.dji.sample.psdk.model.dto;

import com.dji.sample.psdk.model.enums.AeroSpotValueEnum;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Arrays;

@Data
public class AeroSpotWidgetValueSetDto {
    @NotNull
    private String dockSn;

    @NotNull
    private AeroSpotValueEnum value;

    public void setValue(@NotNull Integer value) {
        this.value = Arrays.stream(AeroSpotValueEnum.values()).filter(format -> format.getValue().equals(value)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("AeroSpotValueEnum not found:"+value));
    }
}
