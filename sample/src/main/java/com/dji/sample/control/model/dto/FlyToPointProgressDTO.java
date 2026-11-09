package com.dji.sample.control.model.dto;

import com.dji.sdk.cloudapi.control.FlyToPointProgress;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FlyToPointProgressDTO extends FlyToPointProgress {
    private String dockSn;

}
