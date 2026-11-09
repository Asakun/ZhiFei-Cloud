package com.dji.sample.wayline.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WaylineFileEditDTO {

    private String waylineId;

    private String uavRoute;
}
