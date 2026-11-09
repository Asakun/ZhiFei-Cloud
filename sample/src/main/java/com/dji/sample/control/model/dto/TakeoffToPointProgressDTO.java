package com.dji.sample.control.model.dto;

import com.dji.sdk.cloudapi.control.TakeoffToPointProgress;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TakeoffToPointProgressDTO extends TakeoffToPointProgress {
    private String dockSn;
}
