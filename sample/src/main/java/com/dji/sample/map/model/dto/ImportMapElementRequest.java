package com.dji.sample.map.model.dto;

import com.dji.sdk.cloudapi.map.CreateMapElementRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;


@EqualsAndHashCode(callSuper = true)
@Data
public class ImportMapElementRequest extends CreateMapElementRequest {
    private String relatedSystem;

    private String relatedId;
}
