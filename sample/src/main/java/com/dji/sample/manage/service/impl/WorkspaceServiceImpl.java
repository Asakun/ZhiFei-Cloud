package com.dji.sample.manage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sample.manage.dao.IWorkspaceMapper;
import com.dji.sample.manage.model.dto.WorkspaceDTO;
import com.dji.sample.manage.model.entity.WorkspaceEntity;
import com.dji.sample.manage.service.IWorkspaceService;
import com.dji.sdk.common.Pagination;
import com.dji.sdk.common.PaginationData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class WorkspaceServiceImpl implements IWorkspaceService {

    @Autowired
    private IWorkspaceMapper mapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Optional<WorkspaceDTO> getWorkspaceByWorkspaceId(String workspaceId) {
        return Optional.ofNullable(entityConvertToDto(
                mapper.selectOne(
                        new LambdaQueryWrapper<WorkspaceEntity>()
                                .eq(WorkspaceEntity::getWorkspaceId, workspaceId))));
    }

    @Override
    public Optional<WorkspaceDTO> getWorkspaceByIdLike(String shortId) {
        return Optional.ofNullable(entityConvertToDto(
                mapper.selectOne(
                        new LambdaQueryWrapper<WorkspaceEntity>()
                                .likeRight(WorkspaceEntity::getWorkspaceId, shortId))));
    }

    @Override
    public Optional<WorkspaceDTO> getWorkspaceNameByBindCode(String bindCode) {
        return Optional.ofNullable(entityConvertToDto(
                mapper.selectOne(new LambdaQueryWrapper<WorkspaceEntity>().eq(WorkspaceEntity::getBindCode, bindCode))));
    }

    /**
     * Convert database entity objects into workspace data transfer object.
     * @param entity
     * @return
     */
    private WorkspaceDTO entityConvertToDto(WorkspaceEntity entity) {
        if (entity == null) {
            return null;
        }
        return WorkspaceDTO.builder()
                .id(entity.getId())
                .workspaceId(entity.getWorkspaceId())
                .platformName(entity.getPlatformName())
                .workspaceDesc(entity.getWorkspaceDesc())
                .workspaceName(entity.getWorkspaceName())
                .bindCode(entity.getBindCode())
                .build();
    }

    private WorkspaceEntity dtoToEntity(WorkspaceDTO dto) {
        if (dto == null) {
            return null;
        }
        return WorkspaceEntity.builder()
                .workspaceId(dto.getWorkspaceId())
                .platformName(dto.getPlatformName())
                .workspaceDesc(dto.getWorkspaceDesc())
                .workspaceName(dto.getWorkspaceName())
                .bindCode(dto.getBindCode())
                .build();
    }

    @Override
    public Boolean saveWorkspace(WorkspaceDTO dto) {
        dto.setWorkspaceId(UUID.randomUUID().toString());
        WorkspaceEntity entity = dtoToEntity(dto);
        return mapper.insert(entity) > 0;
    }

    @Override
    public Boolean updateWorkspace(WorkspaceDTO dto) {
        WorkspaceEntity entity = dtoToEntity(dto);
        return mapper.update(entity, new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getWorkspaceId, dto.getWorkspaceId())) > 0;
    }

    @Override
    public PaginationData<WorkspaceDTO> getWorkspaceByPage(int page, int size) {
        Page<WorkspaceDTO> workspacePage = mapper.getWorkspacePageList(new Page<>(page, size),
                new QueryWrapper<WorkspaceDTO>().orderByDesc("create_time"));
        return new PaginationData<>(workspacePage.getRecords(),
                new Pagination(workspacePage.getCurrent(),workspacePage.getSize(), workspacePage.getTotal()));
    }
}
