package com.dji.sample.control.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ControlAuthority {
    private AuthorityOwner flightAuthority;

    private AuthorityOwner payloadAuthority;
}
