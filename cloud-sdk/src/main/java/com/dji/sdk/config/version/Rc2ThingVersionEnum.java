package com.dji.sdk.config.version;

import com.fasterxml.jackson.annotation.JsonValue;


public enum Rc2ThingVersionEnum implements IThingVersion {

    V1_2_0("1.2.0", CloudSDKVersionEnum.V1_0_3),

    V1_3_5("1.3.5", CloudSDKVersionEnum.V1_16_1)
    ;

    private final String thingVersion;

    private final CloudSDKVersionEnum cloudSDKVersion;

    Rc2ThingVersionEnum(String thingVersion, CloudSDKVersionEnum cloudSDKVersion) {
        this.thingVersion = thingVersion;
        this.cloudSDKVersion = cloudSDKVersion;
    }

    @JsonValue
    public String getThingVersion() {
        return thingVersion;
    }

    public CloudSDKVersionEnum getCloudSDKVersion() {
        return cloudSDKVersion;
    }

    public static Rc2ThingVersionEnum find(String thingVersion) {
        return V1_2_0;
    }
}
