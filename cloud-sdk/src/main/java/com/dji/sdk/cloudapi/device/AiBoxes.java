package com.dji.sdk.cloudapi.device;

import java.util.List;

public class AiBoxes {
    // TODO
    List<String> aiBoxes;

    public AiBoxes() {
    }

    @Override
    public String toString() {
        return "AiBoxes{" +
                "aiBoxes=" + aiBoxes +
                '}';
    }

    public List<String> getAiBoxes() {
        return aiBoxes;
    }

    public AiBoxes setAiBoxes(List<String> aiBoxes) {
        this.aiBoxes = aiBoxes;
        return this;
    }
}
