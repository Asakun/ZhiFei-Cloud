package com.dji.sample.media.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoCoordinate {

    private Double longitude;

    private Double latitude;

    private Double altitude;
}
