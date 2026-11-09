package com.dji.sample.media.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoProcessRequest {

    @NotBlank(message = "objectKey cannot be blank")
    private String objectKey;

    @NotNull(message = "normalizedCoordinates cannot be null")
    private List<List<Double>> normalizedCoordinates;

    @Builder.Default
    private Boolean useLrfCorrection = false;
}
