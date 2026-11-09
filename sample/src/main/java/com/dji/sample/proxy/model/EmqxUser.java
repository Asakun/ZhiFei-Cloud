package com.dji.sample.proxy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmqxUser {
    @JsonProperty("user_id")
    private String userId;

    private String password;

    @JsonProperty("is_superuser")
    private Boolean isSuperuser;
}
