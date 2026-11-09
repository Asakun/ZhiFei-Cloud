/*
 * @Author: dyw ding30821082@live.com
 * @Date: 2026-09-18 14:54:21
 * @LastEditors: dyw ding30821082@live.com
 * @LastEditTime: 2026-09-23 10:58:25
 * @FilePath: /DJI-Cloud-API-Demo/sample/src/main/java/com/dji/sample/proxy/model/LlmImgEventRequest.java
 * @Description: 这是默认设置,请设置`customMade`, 打开koroFileHeader查看配置 进行设置: https://github.com/OBKoro1/koro1FileHeader/wiki/%E9%85%8D%E7%BD%AE
 */
package com.dji.sample.proxy.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmImgEventRequest {
    private String num;

    private String djiMediaType;

    private String llmId;

    private String skillId;

    private String jobId;

    private Map<String, String> promptDict;
}
