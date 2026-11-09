package com.dji.sample.wayline.model.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author sean
 * @version 0.3
 * @date 2021/12/22
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WaylineQueryParam {

    private String waylineId;

    private String waylineName;

    private Boolean favorited;

    private Integer templateType;

    private String username;

    private String dockSn;
}
