package com.dji.sample.psdk.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FloatingWindowTextNotifyDto {
    private String text;

    private String sn;
}
