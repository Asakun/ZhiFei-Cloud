package com.dji.sample.manage.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPasswordDTO {

    @NonNull
    private String oldPassword;

    @NonNull
    private String newPassword;
}
