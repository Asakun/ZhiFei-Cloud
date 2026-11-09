package com.dji.sample.map.service.impl;

import com.dji.sample.common.util.CommonUtil;
import com.dji.sample.component.websocket.model.BizCodeEnum;
import com.dji.sample.component.websocket.service.IWebSocketMessageService;
import com.dji.sample.map.model.dto.GroupElementDTO;
import com.dji.sample.map.model.dto.ImportMapElementRequest;
import com.dji.sample.map.model.dto.MapElementDTO;
import com.dji.sample.map.model.entity.GroupEntity;
import com.dji.sample.map.service.IElementCoordinateService;
import com.dji.sample.map.service.IGroupElementService;
import com.dji.sample.map.service.IGroupService;
import com.dji.sample.map.service.IWorkspaceElementService;
import com.dji.sdk.cloudapi.map.*;
import com.dji.sdk.common.HttpResultResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author sean
 * @version 0.2
 * @date 2021/11/30
 */
@Transactional
@Service
public class WorkspaceElementServiceImpl implements IWorkspaceElementService {

    @Autowired
    private IGroupService groupService;

    @Autowired
    private IGroupElementService groupElementService;

    @Autowired
    private IElementCoordinateService elementCoordinateService;

    @Autowired
    private IWebSocketMessageService webSocketMessageService;

    @Override
    public List<GetMapElementsResponse> getAllGroupsByWorkspaceId(String workspaceId, String groupId, Boolean isDistributed) {
        List<GetMapElementsResponse> groupList = groupService.getAllGroupsByWorkspaceId(workspaceId, groupId, isDistributed);
        groupList.forEach(group -> group.setElements(groupElementService.getElementsByGroupId(group.getId())));
        return groupList;
    }

    @Override
    public HttpResultResponse saveElement(String workspaceId, String groupId, CreateMapElementRequest elementCreate, boolean notify) {
        boolean saveElement = groupElementService.saveElement(groupId, elementCreate);
        if (!saveElement) {
            return HttpResultResponse.error("Failed to save the element.");
        }
        if (notify) {
            // Notify all WebSocket connections in this workspace to be updated when an element is created.
            getElementByElementId(elementCreate.getId())
                    .ifPresent(groupElement -> webSocketMessageService.sendBatch(
                            workspaceId, BizCodeEnum.MAP_ELEMENT_CREATE.getCode(),
                            element2CreateWsElement(groupElement)));
        }
        return HttpResultResponse.success();
    }

    @Override
    public HttpResultResponse updateElement(String workspaceId, String elementId, UpdateMapElementRequest elementUpdate, String username, boolean notify) {
        boolean updElement = groupElementService.updateElement(elementId, elementUpdate, username);
        if (!updElement) {
            return HttpResultResponse.error("Failed to update the element.");
        }

        if (notify) {
            // Notify all WebSocket connections in this workspace to update when there is an element update.
            getElementByElementId(elementId)
                    .ifPresent(groupElement -> webSocketMessageService.sendBatch(
                            workspaceId, BizCodeEnum.MAP_ELEMENT_UPDATE.getCode(),
                            element2UpdateWsElement(groupElement)));
        }
        return HttpResultResponse.success();
    }

    @Override
    public HttpResultResponse deleteElement(String workspaceId, String elementId, boolean notify) {
        Optional<GroupElementDTO> elementOpt = getElementByElementId(elementId);
        boolean delElement = groupElementService.deleteElement(elementId);
        if (!delElement) {
            return HttpResultResponse.error("Failed to delete the element.");
        }

        // delete all coordinates according to element id.
        boolean delCoordinate = elementCoordinateService.deleteCoordinateByElementId(elementId);
        if (!delCoordinate) {
            return HttpResultResponse.error("Failed to delete the coordinate.");
        }

        if (notify) {
            // Notify all WebSocket connections in this workspace to update when an element is deleted.
            elementOpt.ifPresent(element ->
                    webSocketMessageService.sendBatch(workspaceId, BizCodeEnum.MAP_ELEMENT_DELETE.getCode(),
                            new MapElementDeleteWsResponse()
                                    .setGroupId(element.getGroupId())
                                    .setId(elementId)));
        }

        return HttpResultResponse.success();
    }

    @Override
    public Optional<GroupElementDTO> getElementByElementId(String elementId) {
        return groupElementService.getElementByElementId(elementId);
    }

    @Override
    public HttpResultResponse deleteAllElementByGroupId(String workspaceId, String groupId) {
        List<MapGroupElement> groupElementList = groupElementService.getElementsByGroupId(groupId);
        for (MapGroupElement groupElement : groupElementList) {
            HttpResultResponse response = this.deleteElement(workspaceId, groupElement.getId(), true);
            if (HttpResultResponse.CODE_SUCCESS != response.getCode()) {
                return response;
            }
        }

        return HttpResultResponse.success();
    }

    public MapElementCreateWsResponse element2CreateWsElement(GroupElementDTO element) {
        if (element == null) {
            return null;
        }
        return new MapElementCreateWsResponse()
                .setId(element.getElementId())
                .setGroupId(element.getGroupId())
                .setName(element.getName())
                .setResource(element.getResource())
                .setUpdateTime(element.getUpdateTime())
                .setCreateTime(element.getCreateTime());
    }

    public MapElementUpdateWsResponse element2UpdateWsElement(GroupElementDTO element) {
        if (element == null) {
            return null;
        }
        return new MapElementUpdateWsResponse()
                .setId(element.getElementId())
                .setGroupId(element.getGroupId())
                .setName(element.getName())
                .setResource(element.getResource())
                .setUpdateTime(element.getUpdateTime())
                .setCreateTime(element.getCreateTime());
    }

    @Override
    public HttpResultResponse saveGroup(String workspaceId, CreateMapElementGroupRequest element, boolean notify) {
        GroupEntity groupEntity = new GroupEntity();
        groupEntity.setWorkspaceId(workspaceId);
        groupEntity.setUsername(element.getUsername());
        groupEntity.setGroupName(element.getName());
        groupEntity.setGroupId(element.getId());
        groupEntity.setGroupType(element.getGroupType()==null?GroupTypeEnum.DEFAULT.getType():element.getGroupType().getType());

        Boolean result = groupService.saveGroup(groupEntity);
        if (!result) {
            return HttpResultResponse.error("Failed to save the group.");
        }

        if (notify && StringUtils.isNotEmpty(groupEntity.getGroupId())) {
            webSocketMessageService.sendBatch(
                    workspaceId, BizCodeEnum.MAP_ELEMENT_GROUP_CREATE.getCode(),
                    groupEntity.toString());
        }
        return HttpResultResponse.success(new CreateMapElementResponse().setId(groupEntity.getGroupId()));
    }

    @Override
    public HttpResultResponse updateGroup(String workspaceId, String groupId, UpdateMapElementGroupRequest element, String username, boolean notify) {
        GroupEntity groupEntity = new GroupEntity();
        if (element.getName() != null) {
            groupEntity.setGroupName(element.getName());
        }
        if (element.getGroupType() != null) {
            groupEntity.setGroupType(element.getGroupType().getType());
        }
        groupEntity.setUsername(username);
        Boolean result = groupService.updateGroup(workspaceId, groupId, groupEntity);
        if (!result) {
            return HttpResultResponse.error("Failed to update the group.");
        }
        if (notify) {
            webSocketMessageService.sendBatch(
                    workspaceId, BizCodeEnum.MAP_ELEMENT_GROUP_UPDATE.getCode(),
                    groupEntity.toString());
        }
        return HttpResultResponse.success();
    }

    @Override
    public HttpResultResponse deleteGroup(String workspaceId, String groupId, boolean notify) {
        HttpResultResponse resp = deleteAllElementByGroupId(workspaceId, groupId);
        if (resp.getCode() != HttpResultResponse.CODE_SUCCESS) {
            return resp;
        }

        Boolean result = groupService.deleteGroup(workspaceId, groupId);
        if (!result) {
            return HttpResultResponse.error("Failed to delete the group.");
        }
        if (notify) {
            webSocketMessageService.sendBatch(workspaceId, BizCodeEnum.MAP_ELEMENT_GROUP_DELETE.getCode(),
                    new MapElementDeleteWsResponse().setGroupId(groupId));
        }
        return HttpResultResponse.success();
    }

    @Override
    public HttpResultResponse importElement(String workspaceId, String groupId, ImportMapElementRequest element) {
        boolean saveElement = groupElementService.importElement(groupId, element);
        if (!saveElement) {
            return HttpResultResponse.error("Failed to import the element.");
        }
        return HttpResultResponse.success();
    }

    @Override
    public List<GroupEntity> getAllGroupList(String workspaceId, Boolean isDistributed, Integer groupType) {
        return groupService.getAllGroupsList(workspaceId, isDistributed, groupType);
    }

    private ElementGeometryType getElementGeometryType(Map<String, Object> mapList, ElementResourceTypeEnum type) {
        ElementGeometryType geometry;
        List<Double> lats = CommonUtil.strToDoubleListWithCommons(mapList.get("latitudes").toString());
        List<Double> lons = CommonUtil.strToDoubleListWithCommons(mapList.get("longitudes").toString());
        List<Double> alts = null;
        if (mapList.get("altitudes") != null){
            alts = CommonUtil.strToDoubleListWithCommons(mapList.get("altitudes").toString());
        }
        List<ElementCoordinate> coordinateList = new ArrayList<>();
        for (int i = 0; i < lats.size(); i++) {
            ElementCoordinate coordinate = new ElementCoordinate().setLatitude(lats.get(i)).setLongitude(lons.get(i));
            if (alts != null) {coordinate.setAltitude(alts.get(i));}
            coordinateList.add(coordinate);
        }
        if (type == ElementResourceTypeEnum.POINT) {
            geometry = new ElementPointGeometry();
        } else if (type == ElementResourceTypeEnum.LINE_STRING) {
            geometry = new ElementLineStringGeometry();
        } else {
            geometry = new ElementPolygonGeometry();
        }
        geometry.adapterCoordinateType(coordinateList);
        return geometry;
    }

    private MapElementDTO mapLisToMapElementDTO(Map<String, Object> mapList) {
        MapElementDTO mapElementDTO = new MapElementDTO();
        ElementResource resource = new ElementResource();
        ElementContent content = new ElementContent();
        ElementProperty property = new ElementProperty();
        property.setColor(mapList.get("color").toString());
        property.setClampToGround((Boolean) mapList.get("clampToGround"));
        content.setProperties(property);
        resource.setType(ElementResourceTypeEnum.find(Integer.parseInt(mapList.get("elementType").toString())));
        resource.setUsername(mapList.get("username").toString());
        content.setGeometry(getElementGeometryType(mapList, resource.getType()));
        resource.setContent(content);
        mapElementDTO.setResource(resource);
        mapElementDTO.setId(mapList.get("elementId").toString());
        mapElementDTO.setName(mapList.get("elementName").toString());
        mapElementDTO.setCreateTime(Long.parseLong(mapList.get("createTime").toString()));
        mapElementDTO.setUpdateTime(Long.parseLong(mapList.get("updateTime").toString()));
        if (mapList.get("relatedSystem") != null && mapList.get("relatedId") != null){
            mapElementDTO.setRelatedSystem(mapList.get("relatedSystem").toString());
            mapElementDTO.setRelatedId(mapList.get("relatedId").toString());
        }
        return mapElementDTO;
    }

    @Override
    public List<MapElementDTO> getMapElementByGroupId(String workspaceId, String groupId, Long page, Long pageSize) {
        List<Map<String, Object>> mapElementMapList = groupElementService.getMapElementsByGroupId(groupId, page, pageSize);
        List<MapElementDTO> mapElementDTOList = new ArrayList<>();
        for (Map<String, Object> mapElementMap : mapElementMapList) {
            mapElementDTOList.add(mapLisToMapElementDTO(mapElementMap));
        }
        return mapElementDTOList;
    }
}
