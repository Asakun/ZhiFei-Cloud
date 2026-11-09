package com.dji.sample.psdk.model.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PsdkFloatingWindowTextDto {

    private Float latitude;

    private Float longitude;

    private Float height;

    private ObjectNode value;

    private String psdkName;

    private Long createTime;
}
