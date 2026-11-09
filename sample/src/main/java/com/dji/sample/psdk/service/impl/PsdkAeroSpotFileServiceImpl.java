package com.dji.sample.psdk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dji.sample.common.util.CommonUtil;
import com.dji.sample.component.oss.model.OssConfiguration;
import com.dji.sample.component.oss.service.impl.OssServiceContext;
import com.dji.sample.component.redis.RedisOpsUtils;
import com.dji.sample.media.service.IMediaService;
import com.dji.sample.psdk.model.dto.AeroSpotTransmissionRequest;
import com.dji.sample.psdk.service.IInterConnectionService;
import com.dji.sample.psdk.service.IPsdkAeroSpotFileService;
import com.dji.sample.psdk.service.IPsdkAeroSpotService;
import com.dji.sample.wayline.dao.IWaylineJobMapper;
import com.dji.sample.wayline.model.entity.WaylineJobEntity;
import com.dji.sdk.cloudapi.device.DeviceEnum;
import com.dji.sdk.cloudapi.media.MediaFileExtension;
import com.dji.sdk.cloudapi.media.MediaUploadCallbackRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Set;

@Service
@Transactional
@Slf4j
public class PsdkAeroSpotFileServiceImpl implements IPsdkAeroSpotFileService {

    @Autowired
    private IMediaService mediaService;

    @Autowired
    private OssServiceContext ossService;

    @Autowired
    private IWaylineJobMapper jobMapper;

    @Autowired
    private IPsdkAeroSpotService aeroSpotService;

    @Autowired
    private IInterConnectionService interConnectionService;

    @Autowired
    private ObjectMapper objectMapper;

    private WaylineJobEntity getJobByJobId(String jobId) {
        return jobMapper.selectOne(new LambdaQueryWrapper<WaylineJobEntity>()
                .eq(WaylineJobEntity::getJobId, jobId));
    }

    @Override
    public String uploadFileToOss(MultipartFile file, String aeroSpotSn) {
        String filename = file.getOriginalFilename();
        log.info("AeroSpot_{} upload filename:{}", aeroSpotSn, filename);
        String name = CommonUtil.getFileNamePrefix(filename);
        Object obj = RedisOpsUtils.hashGet(aeroSpotService.getRedisASTUKey(aeroSpotSn), name);
        if (obj == null) {
            log.warn("AeroSpot filename:{} not found in redis", filename);
            return null;
        }
        MediaFileExtension ext = (MediaFileExtension) obj;
        WaylineJobEntity job = getJobByJobId(ext.getFileGroupId());
        String dockSn = job.getDockSn();
        String flightId = job.getJobId();
        String filepath = OssConfiguration.objectDirPrefix + File.separator + flightId + File.separator + "aero-spot";
        String objectKey = filepath + File.separator + filename;
        try {
            ossService.putObject(OssConfiguration.bucket,objectKey,file.getInputStream());
            MediaUploadCallbackRequest request = new MediaUploadCallbackRequest();
            request.setName(filename);
            request.setPath(filepath);
            request.setObjectKey(objectKey);
            request.setExt(ext);
            mediaService.saveMediaFile(job.getWorkspaceId(), request);
            RedisOpsUtils.hashDel(aeroSpotService.getRedisASTUKey(aeroSpotSn), new String[]{name});
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                // 处理重名逻辑
                log.error("File exists: {}", objectKey);
            } else {
                // 处理其他异常
                throw e;
            }
        } catch (Exception e) {
            log.error("AeroSpot filename:{} upload failed", filename);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        randomAeroSpotToPsdk(dockSn, aeroSpotSn);
        return objectKey;
    }

    private void saveASTUData(String aeroSpotSn, String filename, String flightId, String droneSn) {
        MediaFileExtension ext = new MediaFileExtension();
        ext.setSn(droneSn);
        ext.setFileGroupId(flightId);
        ext.setOriginal(false);
        ext.setPayloadModelKey(DeviceEnum.AERO_SPOT);
        String key = aeroSpotService.getRedisASTUKey(aeroSpotSn);
        RedisOpsUtils.hashSet(key, filename, ext);
        RedisOpsUtils.expireKey(key, 28800);
    }

    private Boolean randomAeroSpotToPsdk(String dockSn, String aeroSpotSn) {
        Object value = RedisOpsUtils.hashRandomKey(aeroSpotService.getRedisASTUKey(aeroSpotSn));
        if (value == null) {
            return false;
        }
        String filename = (String) value;
        return aeroSpotToPsdk(dockSn, filename);
    }

    private Boolean aeroSpotToPsdk(String dockSn, String filename) {
        AeroSpotTransmissionRequest request = new AeroSpotTransmissionRequest(filename);
        try {
            String jsonStr = objectMapper.writeValueAsString(request);
            interConnectionService.customDataTransmissionToPsdk(dockSn, jsonStr);
        } catch (JsonProcessingException e) {
            log.error("AeroSpotToPsdk JsonProcessingException");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public Boolean dealWithAeroSpotToPsdk(String dockSn, String droneSn, String flightId) {
        Set<Object> set = RedisOpsUtils.getSetAll(aeroSpotService.getRedisASFKey(flightId));
        String aeroSpotSn = (String) RedisOpsUtils.get(aeroSpotService.getRedisASSLKey(droneSn));
        String filename = "";
        for (Object o: set) {
            filename = (String) o;
            saveASTUData(aeroSpotSn, filename, flightId, droneSn);
        }
        RedisOpsUtils.deleteSet(aeroSpotService.getRedisASFKey(flightId));
        if (StringUtils.hasText(filename)) {
            return aeroSpotToPsdk(dockSn, filename);
        }
        return false;
    }
}
