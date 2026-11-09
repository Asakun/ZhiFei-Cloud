package com.dji.sample.map.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dji.sample.map.dao.IGroupMapper;
import com.dji.sample.map.model.entity.GroupEntity;
import com.dji.sample.map.service.IGroupElementService;
import com.dji.sample.map.service.IGroupService;
import com.dji.sdk.cloudapi.map.GetMapElementsResponse;
import com.dji.sdk.cloudapi.map.GroupTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author sean
 * @version 0.2
 * @date 2021/11/29
 */
@Service
@Transactional
public class GroupServiceImpl implements IGroupService {

    @Autowired
    private IGroupMapper mapper;

    @Autowired
    private IGroupElementService groupElementService;

    @Override
    public List<GetMapElementsResponse> getAllGroupsByWorkspaceId(String workspaceId, String groupId, Boolean isDistributed) {

        return mapper.selectList(
                new LambdaQueryWrapper<GroupEntity>()
                        .eq(GroupEntity::getWorkspaceId, workspaceId)
                        .eq(StringUtils.hasText(groupId), GroupEntity::getGroupId, groupId)
                        .eq(isDistributed != null, GroupEntity::getIsDistributed, isDistributed))
                .stream()
                .map(this::entityConvertToDto)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<GetMapElementsResponse> getCustomGroupByWorkspaceId(String workspaceId) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.lambdaQuery(GroupEntity.class)
                        .eq(GroupEntity::getWorkspaceId, workspaceId)
                        .eq(GroupEntity::getGroupType, GroupTypeEnum.CUSTOM.getType())
                        .last(" limit 1")))
                .map(this::entityConvertToDto);
    }

    @Override
    public Boolean saveGroup(GroupEntity groupEntity) {
        return mapper.insert(groupEntity) > 0;
    }

    @Override
    public Boolean updateGroup(String workspaceId, String groupId, GroupEntity group) {
        GroupEntity groupEntity = getGroupByWorkspaceIdGroupId(workspaceId, groupId);
        if (groupEntity != null) {
            if (group.getGroupName() != null) {
                groupEntity.setGroupName(group.getGroupName());
            }
            if (group.getGroupType() != null) {
                groupEntity.setGroupType(group.getGroupType());
            }
            groupEntity.setUsername(group.getUsername());
            return this.mapper.updateById(groupEntity) > 0;
        }
        return false;
    }

    @Override
    public Boolean deleteGroup(String workspaceId, String groupId) {
        GroupEntity groupEntity = getGroupByWorkspaceIdGroupId(workspaceId, groupId);
        if (groupEntity != null) {
            return this.mapper.deleteById(groupEntity.getId()) > 0;
        }
        return false;
    }

    private GroupEntity getGroupByWorkspaceIdGroupId(String workspaceId, String groupId) {
        return mapper.selectOne(
                Wrappers.lambdaQuery(GroupEntity.class)
                        .eq(GroupEntity::getWorkspaceId, workspaceId)
                        .eq(GroupEntity::getGroupId, groupId)
                        .eq(GroupEntity::getGroupType, GroupTypeEnum.CUSTOM.getType())
                        .last(" limit 1"));
    }

    /**
     * Convert database entity objects into group data transfer object.
     * @param entity
     * @return
     */
    private GetMapElementsResponse entityConvertToDto(GroupEntity entity) {
        if (entity == null) {
            return null;
        }

        return new GetMapElementsResponse()
                .setId(entity.getGroupId())
                .setName(entity.getGroupName())
                .setType(GroupTypeEnum.find(entity.getGroupType()))
                .setLock(entity.getIsLock());
    }

    @Override
    public List<GroupEntity> getAllGroupsList(String workspaceId, Boolean isDistributed, Integer groupType) {
        return mapper.selectList(
                        new LambdaQueryWrapper<GroupEntity>()
                                .eq(GroupEntity::getWorkspaceId, workspaceId)
                                .eq(isDistributed != null, GroupEntity::getIsDistributed, isDistributed)
                                .eq(groupType != null, GroupEntity::getGroupType, groupType));
    }
}
