package com.dji.sdk.cloudapi.map;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Arrays;

@Schema(description = "Create element group request data")
public class CreateMapElementGroupRequest {

    @NotNull
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    @Schema(description = "group id", format = "uuid")
    private String id;

    @Schema(description = "group name", example = "PILOT 1")
    @NotNull
    private String name;

    private String username;

    private GroupTypeEnum groupType;

    public CreateMapElementGroupRequest() {
    }

    @Override
    public String toString() {
        return "CreateMapElementGroupRequest{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    public String getId() {
        return id;
    }

    public CreateMapElementGroupRequest setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public CreateMapElementGroupRequest setName(String name) {
        this.name = name;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public CreateMapElementGroupRequest setUsername(String username) {
        this.username = username;
        return this;
    }

    public GroupTypeEnum getGroupType() {
        return groupType;
    }

    public CreateMapElementGroupRequest setGroupType(Integer groupType) {
        this.groupType = Arrays.stream(GroupTypeEnum.values()).filter(format -> format.getType()==groupType).findAny()
                .orElseThrow(() -> new IllegalArgumentException("GroupTypeEnum not found:"+groupType));
        return this;
    }
}
