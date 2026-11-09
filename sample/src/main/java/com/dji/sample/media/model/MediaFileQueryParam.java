package com.dji.sample.media.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MediaFileQueryParam {

    private String fileName;

    private String drone;

    private String jobId;

    private Long startTime;

    private Long endTime;

    private Integer isOriginal;
}
