package com.dji.sample.wayline.service;

import com.cleaner.djuav.domain.UavRouteReq;
import com.dji.sample.wayline.model.entity.WaylineFileEditEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public interface IWaylineFileEditService {
    WaylineFileEditEntity getWaylineFileEdit(String waylineId);

    JsonNode readKmzJson(String workspaceId, String waylineId) throws JsonProcessingException;

    UavRouteReq prepareKmzJson(UavRouteReq uavRouteReq);

    String saveKmzJson(UavRouteReq uavRouteReq, String workspaceId, String creator, String filename) throws IOException;

    String updateKmzJson(String waylineId, UavRouteReq uavRouteReq, String workspaceId, String creator, String dockSn) throws IOException;
}
