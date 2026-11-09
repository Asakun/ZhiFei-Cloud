package com.dji.sample.manage.service;

import com.dji.sample.manage.model.dto.OsdDockDroneDTO;
import com.dji.sample.wayline.model.dto.WaylineJobDTO;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;

public interface IOsdService {
    List<OsdDockDroneDTO> getOsdDockDronesByJobId(String jobId) throws JsonProcessingException;

    void getFlightDistanceTime(String jobId, WaylineJobDTO job);
}
