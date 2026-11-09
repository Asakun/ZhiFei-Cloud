package com.dji.sample.map.model.dto;

import com.dji.sdk.cloudapi.map.MapGroupElement;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Data
public class MapElementDTO extends MapGroupElement {
    private String relatedSystem;

    private String relatedId;
}
