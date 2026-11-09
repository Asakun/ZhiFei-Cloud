package com.dji.sample.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sample.media.dao.IMapFileMapper;
import com.dji.sample.media.model.MapFileDTO;
import com.dji.sample.media.model.MapFileEntity;
import com.dji.sample.media.model.MapFileQueryParam;
import com.dji.sample.media.service.IMapFileService;
import com.dji.sdk.common.Pagination;
import com.dji.sdk.common.PaginationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link IMapFileService}.
 */
@Service
@Transactional
public class MapFileServiceImpl implements IMapFileService {

    @Autowired
    private IMapFileMapper mapper;

    @Override
    public PaginationData<MapFileDTO> getPaginationByWorkspaceId(String workspaceId, MapFileQueryParam param, long page, long pageSize) {
        String keyword = param != null ? param.getKeyword() : null;
        LambdaQueryWrapper<MapFileEntity> wrapper = new LambdaQueryWrapper<MapFileEntity>()
                .eq(MapFileEntity::getWorkspaceId, workspaceId)
                .eq(param != null && param.getFileType() != null, MapFileEntity::getFileType, param == null ? null : param.getFileType())
                .and(StringUtils.hasText(keyword), w -> w.like(MapFileEntity::getName, keyword)
                        .or().like(MapFileEntity::getRemark, keyword))
                .orderByDesc(MapFileEntity::getCreateTime);
        Page<MapFileEntity> pageData = mapper.selectPage(new Page<MapFileEntity>(page, pageSize), wrapper);

        List<MapFileDTO> records = pageData.getRecords().stream()
                .map(this::entityConvertToDto)
                .collect(Collectors.toList());

        return new PaginationData<>(records, new Pagination(pageData.getCurrent(), pageData.getSize(), pageData.getTotal()));
    }

    @Override
    public Integer save(String workspaceId, MapFileDTO dto) {
        MapFileEntity entity = dtoConvertToEntity(workspaceId, dto);
        if (!StringUtils.hasText(entity.getFileId())) {
            entity.setFileId(UUID.randomUUID().toString());
        }
        long now = System.currentTimeMillis();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return mapper.insert(entity);
    }

    @Override
    public Boolean update(String workspaceId, MapFileDTO dto) {
        return mapper.update(null,
                new LambdaUpdateWrapper<MapFileEntity>()
                        .eq(MapFileEntity::getWorkspaceId, workspaceId)
                        .eq(MapFileEntity::getFileId, dto.getFileId())
                        .set(dto.getName() != null, MapFileEntity::getName, dto.getName())
                        .set(dto.getRemark() != null, MapFileEntity::getRemark, dto.getRemark())
                        .set(dto.getMaxLatitude() != null, MapFileEntity::getMaxLatitude, dto.getMaxLatitude())
                        .set(dto.getMinLatitude() != null, MapFileEntity::getMinLatitude, dto.getMinLatitude())
                        .set(dto.getMaxLongitude() != null, MapFileEntity::getMaxLongitude, dto.getMaxLongitude())
                        .set(dto.getMinLongitude() != null, MapFileEntity::getMinLongitude, dto.getMinLongitude())
                        .set(MapFileEntity::getUpdateTime, System.currentTimeMillis())) > 0;
    }

    @Override
    public Boolean delete(String workspaceId, String fileId) {
        return mapper.delete(new LambdaQueryWrapper<MapFileEntity>()
                .eq(MapFileEntity::getWorkspaceId, workspaceId)
                .eq(MapFileEntity::getFileId, fileId)) > 0;
    }

    /**
     * DTO to Entity.
     */
    private MapFileEntity dtoConvertToEntity(String workspaceId, MapFileDTO dto) {
        return MapFileEntity.builder()
                .fileId(dto.getFileId())
                .name(dto.getName())
                .remark(dto.getRemark())
                .filePath(dto.getFilePath())
                .workspaceId(workspaceId)
                .objectKey(dto.getObjectKey())
                .fileType(dto.getFileType())
                .fileSize(dto.getFileSize())
                .maxLatitude(dto.getMaxLatitude())
                .minLatitude(dto.getMinLatitude())
                .maxLongitude(dto.getMaxLongitude())
                .minLongitude(dto.getMinLongitude())
                .createTime(dto.getCreateTime() == null ? null
                        : dto.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .updateTime(dto.getUpdateTime() == null ? null
                        : dto.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();
    }

    /**
     * Entity to DTO.
     */
    private MapFileDTO entityConvertToDto(MapFileEntity entity) {
        return MapFileDTO.builder()
                .id(entity.getId())
                .fileId(entity.getFileId())
                .name(entity.getName())
                .remark(entity.getRemark())
                .filePath(entity.getFilePath())
                .workspaceId(entity.getWorkspaceId())
                .objectKey(entity.getObjectKey())
                .fileType(entity.getFileType())
                .fileSize(entity.getFileSize())
                .maxLatitude(entity.getMaxLatitude())
                .minLatitude(entity.getMinLatitude())
                .maxLongitude(entity.getMaxLongitude())
                .minLongitude(entity.getMinLongitude())
                .createTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(entity.getCreateTime()), ZoneId.systemDefault()))
                .updateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(entity.getUpdateTime()), ZoneId.systemDefault()))
                .build();
    }
}
