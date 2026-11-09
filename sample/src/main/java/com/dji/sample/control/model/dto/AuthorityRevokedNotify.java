package com.dji.sample.control.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthorityRevokedNotify {
    private String authorityRevoked;

    private String dockSn;

    private String usurper;
}
