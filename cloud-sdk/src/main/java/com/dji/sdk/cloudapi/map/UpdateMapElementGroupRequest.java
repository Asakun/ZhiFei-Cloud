package com.dji.sdk.cloudapi.map;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.NotNull;
import java.util.Arrays;

@Schema(description = "Update element group request data")
public class UpdateMapElementGroupRequest {

    @Schema(description = "group name", example = "PILOT 1")
    @NotNull
    private String name;

    private GroupTypeEnum groupType;

    @Override
    public String toString() {
        return "UpdateMapElementGroupRequest{" +
                "name='" + name + '\'' +
                '}';
    }

    public @NotNull String getName() {
        return name;
    }

    public void setName(@NotNull String name) {
        this.name = name;
    }

    public GroupTypeEnum getGroupType() {
        return groupType;
    }

    public UpdateMapElementGroupRequest setGroupType(Integer groupType) {
        this.groupType = Arrays.stream(GroupTypeEnum.values()).filter(format -> format.getType()==groupType).findAny()
                .orElseThrow(() -> new IllegalArgumentException("GroupTypeEnum not found:"+groupType));
        return this;
    }
}
