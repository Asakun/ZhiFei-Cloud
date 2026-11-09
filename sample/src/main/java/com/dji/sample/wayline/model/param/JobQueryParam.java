package com.dji.sample.wayline.model.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobQueryParam {

    private String jobId;

    private String jobName;

    private String fileId;

    private String dockSn;

    private String workspaceId;

    private Integer waylineType;

    private Integer taskType;

    private Long startTime;

    private Long endTime;

    private Integer status;

    private String parentId;
}
