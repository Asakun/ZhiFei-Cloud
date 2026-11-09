package com.dji.sample.manage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dji.sample.manage.dao.IOsdDockDroneMapper;
import com.dji.sample.manage.model.dto.OsdDockDroneDTO;
import com.dji.sample.manage.model.entity.OsdDockDroneEntity;
import com.dji.sample.manage.service.IOsdService;
import com.dji.sample.wayline.model.dto.WaylineJobDTO;
import com.dji.sdk.cloudapi.device.OsdDrone;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class OsdServiceImpl implements IOsdService {
    @Autowired
    private IOsdDockDroneMapper osdDockDroneMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<OsdDockDroneDTO> getOsdDockDronesByJobId(String jobId){
        return osdDockDroneMapper.selectList(
                        new LambdaQueryWrapper<OsdDockDroneEntity>()
                                .eq(OsdDockDroneEntity::getJobId, jobId)).stream()
                .map(this::entity2Dto).collect(Collectors.toList());
    }

    public OsdDrone getOneOsdDockDroneByJobId(String jobId, boolean isAsc){
        OsdDockDroneEntity entity = osdDockDroneMapper.selectOne(
                new LambdaQueryWrapper<OsdDockDroneEntity>()
                        .eq(OsdDockDroneEntity::getJobId, jobId)
                        .orderBy(true,isAsc,OsdDockDroneEntity::getCreateTime)
                        .last("limit 1"));
        if(entity == null){return null;}
        try {
            return objectMapper.readValue(entity.getData(), OsdDrone.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void getFlightDistanceTime(String jobId, WaylineJobDTO job) {
        OsdDrone osd1 = getOneOsdDockDroneByJobId(jobId, true);
        OsdDrone osd2 = getOneOsdDockDroneByJobId(jobId, false);
        if (osd1 == null || osd2 == null) {
            job.setFlightDistance(0.);
            job.setFlightTime(0f);
        } else {
            job.setFlightDistance(osd2.getTotalFlightDistance()-osd1.getTotalFlightDistance());
            job.setFlightTime(osd2.getTotalFlightTime()-osd1.getTotalFlightTime());
        }
    }

    private OsdDockDroneDTO entity2Dto(OsdDockDroneEntity entity) {
        if (Objects.isNull(entity)) {
            return null;
        }
        try {
            return OsdDockDroneDTO.builder()
                    .osd(objectMapper.readTree(entity.getData()))
                    .createTime(entity.getCreateTime())
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
