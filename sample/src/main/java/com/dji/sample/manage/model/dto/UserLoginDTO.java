package com.dji.sample.manage.model.dto;

import com.dji.sample.manage.model.enums.UserTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Arrays;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginDTO {

    @NonNull
    private String username;

    @NonNull
    private String password;

    private String workspaceId;

    @NonNull
    private UserTypeEnum flag;

    @NonNull
    private String captchaKey;

    @NonNull
    private String captchaCode;

    public void setFlag(Integer flag) {
        this.flag = Arrays.stream(UserTypeEnum.values()).filter(format -> format.getVal().equals(flag)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("UserTypeEnum not found:"+flag));
    }
}
