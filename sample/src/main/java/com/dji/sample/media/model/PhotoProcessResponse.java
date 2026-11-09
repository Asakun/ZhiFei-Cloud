package com.dji.sample.media.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoProcessResponse {

    private List<GeoCoordinate> coordinates;

    private Double areaSquareMeters;

    private String status;
}
