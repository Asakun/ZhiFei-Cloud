package com.dji.sample.wayline.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sample.component.mqtt.model.EventsReceiver;
import com.dji.sample.component.redis.RedisConst;
import com.dji.sample.component.redis.RedisOpsUtils;
import com.dji.sample.control.model.param.TakeoffToPointParam;
import com.dji.sample.manage.model.dto.DeviceDTO;
import com.dji.sample.manage.service.IDeviceRedisService;
import com.dji.sample.manage.service.IDeviceService;
import com.dji.sample.media.model.MediaFileCountDTO;
import com.dji.sample.media.service.IFileService;
import com.dji.sample.psdk.service.IPsdkAeroSpotFileService;
import com.dji.sample.wayline.dao.IWaylineJobMapper;
import com.dji.sample.wayline.model.dto.DuringFlightInfo;
import com.dji.sample.wayline.model.dto.WaylineJobDTO;
import com.dji.sample.wayline.model.entity.WaylineJobEntity;
import com.dji.sample.wayline.model.enums.WaylineJobStatusEnum;
import com.dji.sample.wayline.model.param.CreateJobParam;
import com.dji.sample.wayline.model.param.JobQueryParam;
import com.dji.sample.wayline.repository.WaylineJobRepository;
import com.dji.sample.wayline.service.ICvrmpCameraService;
import com.dji.sample.wayline.service.IWaylineFileService;
import com.dji.sample.wayline.service.IWaylineJobService;
import com.dji.sample.wayline.service.IWaylineRedisService;
import com.dji.sdk.cloudapi.device.DockModeCodeEnum;
import com.dji.sdk.cloudapi.device.DroneModeCodeEnum;
import com.dji.sdk.cloudapi.device.OsdDock;
import com.dji.sdk.cloudapi.device.OsdDockDrone;
import com.dji.sdk.cloudapi.wayline.*;
import com.dji.sdk.common.Pagination;
import com.dji.sdk.common.PaginationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.dji.sample.common.util.CommonUtil.dealStartEndTime;

/**
 * @author sean
 * @version 1.1
 * @date 2022/6/1
 */
@Service
@Transactional
@Slf4j
public class WaylineJobServiceImpl implements IWaylineJobService {

    @Autowired
    private IWaylineJobMapper mapper;

    @Autowired
    private IWaylineFileService waylineFileService;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private IFileService fileService;

    @Autowired
    private IDeviceRedisService deviceRedisService;

    @Autowired
    private IWaylineRedisService waylineRedisService;

    @Autowired
    private WaylineJobRepository waylineJobRepository;

    @Autowired
    private ICvrmpCameraService cvrmpService;

    @Autowired
    private IPsdkAeroSpotFileService psdkAeroSpotFileService;

    private Optional<WaylineJobDTO> insertWaylineJob(WaylineJobEntity jobEntity) {
        int id = mapper.insert(jobEntity);
        if (id <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.entity2Dto(jobEntity));
    }

    @Override
    public Optional<WaylineJobDTO> createWaylineJob(CreateJobParam param, String workspaceId, String username, Long beginTime, Long endTime) {
        if (Objects.isNull(param)) {
            return Optional.empty();
        }
        // Immediate tasks, allocating time on the backend.
        WaylineJobEntity jobEntity = WaylineJobEntity.builder()
                .name(param.getName())
                .dockSn(param.getDockSn())
                .fileId(param.getFileId())
                .username(username)
                .workspaceId(workspaceId)
                .jobId(UUID.randomUUID().toString())
                .beginTime(beginTime)
                .endTime(endTime)
                .status(WaylineJobStatusEnum.PENDING.getVal())
                .taskType(param.getTaskType().getType())
                .waylineType(param.getWaylineType().getValue())
                .outOfControl(param.getOutOfControlAction().getAction())
                .rthAltitude(param.getRthAltitude())
                .mediaCount(0)
                .simulateMission(param.getSimulateMission())
                .build();

        return insertWaylineJob(jobEntity);
    }

    @Override
    public Optional<WaylineJobDTO> createWaylineJob(TakeoffToPointParam param, String workspaceId, String username, String dockSn) {
        if (Objects.isNull(param)) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        long time = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        String name = String.format("%s_%s",dockSn,now.format(formatter));
        WaylineJobEntity jobEntity = WaylineJobEntity.builder()
                .name(name)
                .dockSn(dockSn)
                .username(username)
                .workspaceId(workspaceId)
                .jobId(param.getFlightId())
                .beginTime(time)
                .endTime(time)
                .status(WaylineJobStatusEnum.PENDING.getVal())
                .taskType(TaskTypeEnum.IMMEDIATE.getType())
                .waylineType(WaylineTypeEnum.MANUAL.getValue())
                .outOfControl(param.RcLostAction2OutOfControlAction().getAction())
                .rthAltitude(param.getRthAltitude())
                .mediaCount(0)
                .build();

        return insertWaylineJob(jobEntity);
    }

    @Override
    public Optional<WaylineJobDTO> createWaylineJobByParent(String workspaceId, String parentId) {
        Optional<WaylineJobDTO> parentJobOpt = this.getJobByJobId(workspaceId, parentId);
        if (parentJobOpt.isEmpty()) {
            return Optional.empty();
        }
        WaylineJobEntity jobEntity = this.dto2Entity(parentJobOpt.get());
        jobEntity.setJobId(UUID.randomUUID().toString());
        jobEntity.setErrorCode(null);
        jobEntity.setCompletedTime(null);
        jobEntity.setExecuteTime(null);
        jobEntity.setStatus(WaylineJobStatusEnum.PENDING.getVal());
        jobEntity.setParentId(parentId);

        return this.insertWaylineJob(jobEntity);
    }

    public List<WaylineJobDTO> getJobsByConditions(String workspaceId, Collection<String> jobIds, WaylineJobStatusEnum status) {
        return mapper.selectList(
                new LambdaQueryWrapper<WaylineJobEntity>()
                        .eq(WaylineJobEntity::getWorkspaceId, workspaceId)
                        .eq(Objects.nonNull(status), WaylineJobEntity::getStatus, status.getVal())
                        .and(!CollectionUtils.isEmpty(jobIds),
                                wrapper -> jobIds.forEach(id -> wrapper.eq(WaylineJobEntity::getJobId, id).or())))
                .stream()
                .map(this::entity2Dto)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<WaylineJobDTO> getJobByJobId(String workspaceId, String jobId) {
        WaylineJobEntity jobEntity = mapper.selectOne(
                new LambdaQueryWrapper<WaylineJobEntity>()
                        .eq(WaylineJobEntity::getWorkspaceId, workspaceId)
                        .eq(WaylineJobEntity::getJobId, jobId));
        return Optional.ofNullable(entity2Dto(jobEntity));
    }

    @Override
    public Boolean updateJob(WaylineJobDTO dto) {
        return mapper.update(this.dto2Entity(dto),
                new LambdaUpdateWrapper<WaylineJobEntity>()
                        .eq(WaylineJobEntity::getJobId, dto.getJobId())) > 0;
    }

    @Override
    public PaginationData<WaylineJobDTO> getJobsByWorkspaceId(String workspaceId, JobQueryParam queryParam, long page, long pageSize) {
        Page<WaylineJobEntity> pageData = mapper.selectPage(
                new Page<WaylineJobEntity>(page, pageSize),
                new LambdaQueryWrapper<WaylineJobEntity>()
                        .eq(WaylineJobEntity::getWorkspaceId, workspaceId)
                        .eq(StringUtils.hasText(queryParam.getJobId()), WaylineJobEntity::getJobId, queryParam.getJobId())
                        .eq(StringUtils.hasText(queryParam.getFileId()), WaylineJobEntity::getFileId, queryParam.getFileId())
                        .eq(queryParam.getStatus() != null, WaylineJobEntity::getStatus, queryParam.getStatus())
                        .eq(queryParam.getTaskType() != null, WaylineJobEntity::getTaskType, queryParam.getTaskType())
                        .eq(queryParam.getWaylineType() != null, WaylineJobEntity::getWaylineType, queryParam.getWaylineType())
                        .eq(StringUtils.hasText(queryParam.getDockSn()), WaylineJobEntity::getDockSn, queryParam.getDockSn())
                        .like(StringUtils.hasText(queryParam.getJobName()), WaylineJobEntity::getName, queryParam.getJobName())
                        .ge(queryParam.getStartTime() != null, WaylineJobEntity::getExecuteTime, queryParam.getStartTime())
                        .le(queryParam.getEndTime() != null, WaylineJobEntity::getExecuteTime, queryParam.getEndTime())
                        .orderByDesc(WaylineJobEntity::getId));
        List<WaylineJobDTO> records = pageData.getRecords()
                .stream()
                .map(this::entity2Dto)
                .collect(Collectors.toList());

        return new PaginationData<WaylineJobDTO>(records, new Pagination(pageData.getCurrent(), pageData.getSize(), pageData.getTotal()));
    }

    private WaylineJobEntity dto2Entity(WaylineJobDTO dto) {
        WaylineJobEntity.WaylineJobEntityBuilder builder = WaylineJobEntity.builder();
        if (dto == null) {
            return builder.build();
        }
        if (Objects.nonNull(dto.getBeginTime())) {
            builder.beginTime(dto.getBeginTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        if (Objects.nonNull(dto.getEndTime())) {
            builder.endTime(dto.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        if (Objects.nonNull(dto.getExecuteTime())) {
            builder.executeTime(dto.getExecuteTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        if (Objects.nonNull(dto.getCompletedTime())) {
            builder.completedTime(dto.getCompletedTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        return builder.status(dto.getStatus())
                .mediaCount(dto.getMediaCount())
                .name(dto.getJobName())
                .errorCode(dto.getCode())
                .jobId(dto.getJobId())
                .fileId(dto.getFileId())
                .dockSn(dto.getDockSn())
                .workspaceId(dto.getWorkspaceId())
                .taskType(Optional.ofNullable(dto.getTaskType()).map(TaskTypeEnum::getType).orElse(null))
                .waylineType(Optional.ofNullable(dto.getWaylineType()).map(WaylineTypeEnum::getValue).orElse(null))
                .username(dto.getUsername())
                .rthAltitude(dto.getRthAltitude())
                .outOfControl(Optional.ofNullable(dto.getOutOfControlAction())
                        .map(OutOfControlActionEnum::getAction).orElse(null))
                .simulateMission(dto.getSimulateMission())
                .parentId(dto.getParentId())
                .flightDistance(dto.getFlightDistance())
                .flightTime(dto.getFlightTime())
                .build();
    }

    public WaylineJobStatusEnum getWaylineState(String dockSn) {
        Optional<DeviceDTO> dockOpt = deviceRedisService.getDeviceOnline(dockSn);
        if (dockOpt.isEmpty() || !StringUtils.hasText(dockOpt.get().getChildDeviceSn())) {
            return WaylineJobStatusEnum.UNKNOWN;
        }
        Optional<OsdDock> dockOsdOpt = deviceRedisService.getDeviceOsd(dockSn, OsdDock.class);
        Optional<OsdDockDrone> deviceOsdOpt = deviceRedisService.getDeviceOsd(dockOpt.get().getChildDeviceSn(), OsdDockDrone.class);
        if (dockOsdOpt.isEmpty() || deviceOsdOpt.isEmpty() || DockModeCodeEnum.WORKING != dockOsdOpt.get().getModeCode()) {
            return WaylineJobStatusEnum.UNKNOWN;
        }

        OsdDockDrone osdDevice = deviceOsdOpt.get();
        if (DroneModeCodeEnum.WAYLINE == osdDevice.getModeCode()
                || DroneModeCodeEnum.MANUAL == osdDevice.getModeCode()
                || DroneModeCodeEnum.TAKEOFF_AUTO == osdDevice.getModeCode()) {
            if (StringUtils.hasText(waylineRedisService.getPausedWaylineJobId(dockSn))) {
                return WaylineJobStatusEnum.PAUSED;
            }
            if (waylineRedisService.getRunningWaylineJob(dockSn).isPresent()) {
                return WaylineJobStatusEnum.IN_PROGRESS;
            }
        }
        return WaylineJobStatusEnum.UNKNOWN;
    }

    private WaylineJobDTO entity2Dto(WaylineJobEntity entity) {
        if (entity == null) {
            return null;
        }

        WaylineJobDTO.WaylineJobDTOBuilder builder = WaylineJobDTO.builder()
                .jobId(entity.getJobId())
                .jobName(entity.getName())
                .fileId(entity.getFileId())
                .fileName(waylineFileService.getWaylineByWaylineId(entity.getWorkspaceId(), entity.getFileId())
                        .orElse(new GetWaylineListResponse()).getName())
                .dockSn(entity.getDockSn())
                .dockName(deviceService.getDeviceBySn(entity.getDockSn())
                        .orElse(DeviceDTO.builder().build()).getNickname())
                .username(entity.getUsername())
                .workspaceId(entity.getWorkspaceId())
                .status(WaylineJobStatusEnum.IN_PROGRESS.getVal() == entity.getStatus() &&
                        entity.getJobId().equals(waylineRedisService.getPausedWaylineJobId(entity.getDockSn())) ?
                                WaylineJobStatusEnum.PAUSED.getVal() : entity.getStatus())
                .code(entity.getErrorCode())
                .beginTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(entity.getBeginTime()), ZoneId.systemDefault()))
                .endTime(Objects.nonNull(entity.getEndTime()) ?
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(entity.getEndTime()), ZoneId.systemDefault()) : null)
                .executeTime(Objects.nonNull(entity.getExecuteTime()) ?
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(entity.getExecuteTime()), ZoneId.systemDefault()) : null)
                .completedTime(WaylineJobStatusEnum.find(entity.getStatus()).getEnd() ?
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(entity.getUpdateTime()), ZoneId.systemDefault()) : null)
                .taskType(TaskTypeEnum.find(entity.getTaskType()))
                .waylineType(WaylineTypeEnum.find(entity.getWaylineType()))
                .rthAltitude(entity.getRthAltitude())
                .outOfControlAction(OutOfControlActionEnum.find(entity.getOutOfControl()))
                .simulateMission(entity.getSimulateMission())
                .flightDistance(entity.getFlightDistance())
                .flightTime(entity.getFlightTime())
                .mediaCount(entity.getMediaCount());

        if (Objects.nonNull(entity.getEndTime())) {
            builder.endTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(entity.getEndTime()), ZoneId.systemDefault()));
        }
        if (WaylineJobStatusEnum.IN_PROGRESS.getVal() == entity.getStatus()) {
            builder.progress(waylineRedisService.getRunningWaylineJob(entity.getDockSn())
                    .map(EventsReceiver::getOutput)
                    .map(FlighttaskProgress::getProgress)
                    .map(FlighttaskProgressData::getPercent)
                    .orElse(null));
        }

        if (entity.getMediaCount() == 0) {
            return builder.build();
        }

        // sync the number of media files
        String key = RedisConst.MEDIA_HIGHEST_PRIORITY_PREFIX + entity.getDockSn();
        String countKey = RedisConst.MEDIA_FILE_PREFIX + entity.getDockSn();
        Object mediaFileCount = RedisOpsUtils.hashGet(countKey, entity.getJobId());
        if (Objects.nonNull(mediaFileCount)) {
            builder.uploadedCount(((MediaFileCountDTO) mediaFileCount).getUploadedCount())
                    .uploading(RedisOpsUtils.checkExist(key) && entity.getJobId().equals(((MediaFileCountDTO)RedisOpsUtils.get(key)).getJobId()));
            return builder.build();
        }

        int uploadedSize = fileService.getFilesByWorkspaceAndJobId(entity.getWorkspaceId(), entity.getJobId()).size();
        // All media for this job have been uploaded.
        if (uploadedSize >= entity.getMediaCount()) {
            return builder.uploadedCount(uploadedSize).build();
        }
        RedisOpsUtils.hashSet(countKey, entity.getJobId(),
                MediaFileCountDTO.builder()
                        .jobId(entity.getJobId())
                        .mediaCount(entity.getMediaCount())
                        .uploadedCount(uploadedSize).build());
        return builder.build();
    }

    @Override
    public DuringFlightInfo getOngoingJobIdByDockSn(String workspaceId, String dockSn) {
        Optional<DuringFlightInfo> duringFlightInfoOpt = waylineRedisService.getDuringFlightInfo(dockSn);
        if (duringFlightInfoOpt.isEmpty()) {
            throw new RuntimeException("drone is not in flight");
        }
        return duringFlightInfoOpt.get();
    }

    @Override
    public DuringFlightInfo setOngoingJobIdByDockSn(String workspaceId, String dockSn, DuringFlightInfo info) throws IllegalAccessException {
        DuringFlightInfo flightInfo = waylineRedisService.updateDuringFlightInfo(dockSn, info);
        if (flightInfo == null) {
            throw new RuntimeException("drone is not in flight");
        }
        return flightInfo;
    }

    @Override
    public void flightFinshToDo(String dockSn, DuringFlightInfo flightInfo) {
        if (StringUtils.hasText(flightInfo.getToUploadAeroSpotKey())) {
            psdkAeroSpotFileService.dealWithAeroSpotToPsdk(dockSn, flightInfo.getDroneSn(), flightInfo.getFlightId());
        }
        if (StringUtils.hasText(flightInfo.getToStopCameraId())) {
            cvrmpService.stopCameraAsync(flightInfo.getToStopCameraId());
        }
    }


    @Override
    public Map<String, Object> getWaylineJobSum(String workspaceId, Long startTime, Long endTime) {
        Long[] ref = dealStartEndTime(startTime, endTime);
        return waylineJobRepository.getWaylineJobSum(ref[0], ref[1], workspaceId);
    }

    @Override
    public List<Map<String, Object>> getWaylineJobSumByDate(String workspaceId, Long startTime, Long endTime) {
        Long[] ref = dealStartEndTime(startTime, endTime);
        return waylineJobRepository.getWaylineJobSumByDate(ref[0], ref[1], workspaceId);
    }

    @Override
    public List<Map<String, Object>> getWaylineJobRank(String workspaceId, Long startTime, Long endTime, String groupBy, String sort) {
        Long[] ref = dealStartEndTime(startTime, endTime);
        return mapper.getWaylineJobRank(ref[0], ref[1], groupBy, sort, workspaceId);
    }
}
