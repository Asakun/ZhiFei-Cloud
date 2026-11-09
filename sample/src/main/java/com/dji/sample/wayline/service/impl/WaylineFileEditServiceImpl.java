package com.dji.sample.wayline.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cleaner.djuav.domain.*;
import com.dji.sample.common.util.KmzUtil;
import com.dji.sample.component.oss.model.OssConfiguration;
import com.dji.sample.component.oss.service.impl.OssServiceContext;
import com.dji.sample.wayline.dao.IWaylineFileEditMapper;
import com.dji.sample.wayline.model.entity.WaylineFileEditEntity;
import com.dji.sample.wayline.model.entity.WaylineFileEntity;
import com.dji.sample.wayline.service.IWaylineFileEditService;
import com.dji.sample.wayline.service.IWaylineFileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;


@Service
@Transactional
public class WaylineFileEditServiceImpl implements IWaylineFileEditService {

    @Autowired
    private IWaylineFileEditMapper mapper;

    @Autowired
    private IWaylineFileService waylineFileService;

    @Autowired
    private OssServiceContext ossService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public WaylineFileEditEntity getWaylineFileEdit(String waylineId) {
        return mapper.selectOne(
                new LambdaQueryWrapper<WaylineFileEditEntity>()
                        .eq(WaylineFileEditEntity::getWaylineId, waylineId));
    }

    @Override
    public JsonNode readKmzJson(String workspaceId, String waylineId) throws JsonProcessingException {
        WaylineFileEditEntity waylineFileEdit = getWaylineFileEdit(waylineId);
        if (waylineFileEdit == null) {
            WaylineFileEntity wayline = waylineFileService.getWaylineFileByWaylineId(workspaceId, waylineId);
            if (wayline != null) {
                InputStream inputStream = ossService.getObject(OssConfiguration.bucket, wayline.getObjectKey());
                if (inputStream == null) {
                    throw new RuntimeException("kmz file not found");
                }
                UavRouteReq uavRouteReq = KmzUtil.parseKmz(inputStream);
                waylineFileEdit = WaylineFileEditEntity.builder()
                        .waylineId(waylineId).uavRoute(objectMapper.writeValueAsString(uavRouteReq)).build();
                mapper.insert(waylineFileEdit);
            } else {
                throw new RuntimeException("wayline file not found");
            }
        }
        return objectMapper.readTree(waylineFileEdit.getUavRoute());
    }

    @Override
    public UavRouteReq prepareKmzJson(UavRouteReq uavRouteReq) {
        KmzUtil.preBuildKmz(uavRouteReq);
        return uavRouteReq;
    }

    @Override
    public String saveKmzJson(UavRouteReq uavRouteReq, String workspaceId, String creator, String filename)
            throws IOException {
        prepareKmzJson(uavRouteReq);
        String kmzJson = objectMapper.writeValueAsString(uavRouteReq);
        filename = KmzUtil.getKmzFileName(filename);
        String filePath = KmzUtil.buildKmz(uavRouteReq);
        MultipartFile multipartFile = KmzUtil.convert(filePath, filename);
        return waylineFileService.importKmzFile(multipartFile, workspaceId, creator, kmzJson);
    }

    @Override
    public String updateKmzJson(String waylineId, UavRouteReq uavRouteReq, String workspaceId, String creator, String dockSn) throws IOException {
        prepareKmzJson(uavRouteReq);
        String kmzJson = objectMapper.writeValueAsString(uavRouteReq);
        WaylineFileEditEntity waylineFileEdit = getWaylineFileEdit(waylineId);
        if (waylineFileEdit == null) {
            throw new RuntimeException("waylineId not found");
        } else {
            WaylineFileEntity waylineFile = waylineFileService.getWaylineFileByWaylineId(workspaceId, waylineId);
            if (waylineFile == null) {
                throw new RuntimeException("wayline file not found");
            }
            String filename = KmzUtil.getKmzFileName(waylineFile.getName());
            waylineFileEdit.setUavRoute(kmzJson);
            String filePath = KmzUtil.buildKmz(uavRouteReq);
            MultipartFile multipartFile = KmzUtil.convert(filePath, filename);
            if (StringUtils.hasText(dockSn)) {waylineFile.setDockSn(dockSn);}
            Integer count = waylineFileService.updateKmzFile(multipartFile, waylineFile, creator);
            if (count > 0) {
                mapper.updateById(waylineFileEdit);
            }
        }
        return waylineId;
    }

}
