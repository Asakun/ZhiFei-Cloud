package com.dji.sample.psdk.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PsdkWidgetValueSetDTO {

    private Integer id;

    private String name;

    private String sn;

    private Integer psdkIndex;

    private Integer index;

    private Integer value;

    private String action;

    private String remark;

    private Integer visible;
}
