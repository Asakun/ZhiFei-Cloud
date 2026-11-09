package com.dji.sample.common.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cleaner.djuav.domain.*;
import com.cleaner.djuav.domain.kml.*;
import com.cleaner.djuav.util.Mapping2dUtils;
import com.cleaner.djuav.util.RouteFileUtils;
import com.dji.sample.common.model.CustomMultipartFile;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class KmzUtil {

    private static void reflectToUavRouteReq(UavRouteReq uavRouteReq, KmzInfoVO kmzInfoVO) {
        KmlInfo kmlInfo = kmzInfoVO.getKmlInfo();
        KmlInfo wpmlInfo = kmzInfoVO.getWpmlInfo();
        KmlMissionConfig config = kmlInfo.getDocument().getKmlMissionConfig();

        uavRouteReq.setDroneType(Integer.valueOf(config.getDroneInfo().getDroneEnumValue()));
        uavRouteReq.setSubDroneType(Integer.valueOf(config.getDroneInfo().getDroneSubEnumValue()));
        uavRouteReq.setPayloadType(Integer.valueOf(config.getPayloadInfo().getPayloadEnumValue()));
        uavRouteReq.setPayloadPosition(Integer.valueOf(config.getPayloadInfo().getPayloadPositionIndex()));
        uavRouteReq.setFinishAction(config.getFinishAction());
        uavRouteReq.setExitOnRcLostAction(config.getExecuteRCLostAction());
        uavRouteReq.setTakeOffRefPoint(config.getTakeOffRefPoint());

        KmlFolder folder = kmlInfo.getDocument().getFolder();
        uavRouteReq.setTemplateType(folder.getTemplateType());
        uavRouteReq.setGlobalHeight(Double.valueOf(kmlInfo.getDocument().getKmlMissionConfig().getGlobalRTHHeight()));
        uavRouteReq.setAutoFlightSpeed(Double.valueOf(folder.getAutoFlightSpeed()));

        KmlFolder wpmlFolder = wpmlInfo.getDocument().getFolder();
        if (ObjectUtil.isNotNull(wpmlFolder.getStartActionGroup()) && ObjectUtil.isNotEmpty(wpmlFolder.getStartActionGroup().getAction())) {
            List<PointActionReq> actions = new ArrayList<>();
            for (KmlAction action : wpmlFolder.getStartActionGroup().getAction()) {
                actions.add(reflectToPointActionReq(action));
            }
            uavRouteReq.setStartActionList(actions);
        }

        KmlGlobalWaypointHeadingParam globalWaypointHeadingParam = folder.getGlobalWaypointHeadingParam();
        if (ObjectUtil.isNotEmpty(globalWaypointHeadingParam)) {
            WaypointHeadingReq waypointHeadingReq = new WaypointHeadingReq();
            waypointHeadingReq.setWaypointHeadingMode(globalWaypointHeadingParam.getWaypointHeadingMode());
            if (globalWaypointHeadingParam.getWaypointHeadingAngle() != null){
                waypointHeadingReq.setWaypointHeadingAngle(Double.valueOf(globalWaypointHeadingParam.getWaypointHeadingAngle()));
            }
            waypointHeadingReq.setWaypointPoiPoint(globalWaypointHeadingParam.getWaypointPoiPoint());
            uavRouteReq.setWaypointHeadingReq(waypointHeadingReq);
        }

        WaypointTurnReq waypointTurnReq = new WaypointTurnReq();
        waypointTurnReq.setWaypointTurnMode(folder.getGlobalWaypointTurnMode());
        waypointTurnReq.setUseStraightLine(StringUtils.isNotBlank(folder.getGlobalUseStraightLine()) ? Integer.valueOf(folder.getGlobalUseStraightLine()) : null);

        uavRouteReq.setWaypointTurnReq(waypointTurnReq);
        uavRouteReq.setGimbalPitchMode(folder.getGimbalPitchMode());
        uavRouteReq.setPayloadPosition(Integer.valueOf(kmlInfo.getDocument().getKmlMissionConfig().getPayloadInfo().getPayloadPositionIndex()));
        uavRouteReq.setImageFormat(folder.getPayloadParam().getImageFormat());

        List<RoutePointReq> routePointList = new ArrayList<>();
        for (KmlPlacemark placemark: folder.getPlacemarkList()) {
            RoutePointReq routePoint = new RoutePointReq();
            routePoint.setRoutePointIndex(Integer.valueOf(placemark.getIndex()));
            String[] coordinates = placemark.getKmlPoint().getCoordinates().split(",");
            routePoint.setLongitude(Double.valueOf(coordinates[0]));
            routePoint.setLatitude(Double.valueOf(coordinates[1]));
            if (StringUtils.isNotEmpty(placemark.getHeight())) {routePoint.setHeight(Double.valueOf(placemark.getHeight()));}
            if (StringUtils.isNotEmpty(placemark.getWaypointSpeed())) {routePoint.setSpeed(Double.valueOf(placemark.getWaypointSpeed()));}
            if (ObjectUtil.isNotEmpty(placemark.getWaypointHeadingParam())) {
                WaypointHeadingReq waypointHeadingReq = new WaypointHeadingReq();
                waypointHeadingReq.setWaypointHeadingAngle(Double.valueOf(placemark.getWaypointHeadingParam().getWaypointHeadingAngle()));
                waypointHeadingReq.setWaypointPoiPoint(placemark.getWaypointHeadingParam().getWaypointPoiPoint());
                waypointHeadingReq.setWaypointHeadingMode(placemark.getWaypointHeadingParam().getWaypointHeadingMode());
                routePoint.setWaypointHeadingReq(waypointHeadingReq);
            }
            if (ObjectUtil.isNotEmpty(placemark.getWaypointTurnParam())) {
                WaypointTurnReq waypointTurn = new WaypointTurnReq();
                waypointTurn.setWaypointTurnMode(placemark.getWaypointTurnParam().getWaypointTurnMode());
                waypointTurn.setWaypointTurnDampingDist(Double.valueOf(placemark.getWaypointTurnParam().getWaypointTurnDampingDist()));
                waypointTurn.setUseStraightLine(Integer.valueOf(placemark.getUseStraightLine()));
                routePoint.setWaypointTurnReq(waypointTurn);
            }
            if (StringUtils.isNotEmpty(placemark.getGimbalPitchAngle())) {routePoint.setGimbalPitchAngle(Double.valueOf(placemark.getGimbalPitchAngle()));}
            List<ActionGroupReq> actionGroupList = new ArrayList<>();
            if (ObjectUtil.isNotEmpty(placemark.getActionGroup())) {
                for (KmlActionGroup kmlActionGroup: placemark.getActionGroup()){
                    ActionGroupReq actionGroupReq = new ActionGroupReq();
                    actionGroupReq.setActionGroupId(Integer.valueOf(kmlActionGroup.getActionGroupId()));
                    actionGroupReq.setActionGroupStartIndex(Integer.valueOf(kmlActionGroup.getActionGroupStartIndex()));
                    actionGroupReq.setActionGroupEndIndex(Integer.valueOf(kmlActionGroup.getActionGroupEndIndex()));
                    actionGroupReq.setActionTriggerType(kmlActionGroup.getActionTrigger().getActionTriggerType());
                    if (ObjectUtil.isNotEmpty(kmlActionGroup.getActionTrigger().getActionTriggerParam())) {
                        actionGroupReq.setActionTriggerParam(Double.valueOf(kmlActionGroup.getActionTrigger().getActionTriggerParam()));
                    }
                    List<PointActionReq> actions = new ArrayList<>();
                    for (KmlAction kmlAction: kmlActionGroup.getAction()){
                        actions.add(reflectToPointActionReq(kmlAction));
                    }
                    actionGroupReq.setActions(actions);
                    actionGroupList.add(actionGroupReq);
                }
            }
            routePoint.setActionGroupList(actionGroupList);
            routePointList.add(routePoint);
        }
        uavRouteReq.setRoutePointList(routePointList);
    }

    private static PointActionReq reflectToPointActionReq(KmlAction kmlAction){
        PointActionReq action = new PointActionReq();
        action.setActionIndex(Integer.valueOf(kmlAction.getActionId()));
        action.setActionFunc(kmlAction.getActionActuatorFunc());
        if (ObjectUtil.isNotEmpty(kmlAction.getActionActuatorFuncParam().getHoverTime())) {
            action.setHoverTime(Double.valueOf(kmlAction.getActionActuatorFuncParam().getHoverTime()));
        }
        if (ObjectUtil.isNotEmpty(kmlAction.getActionActuatorFuncParam().getAircraftHeading())){
            action.setAircraftHeading(Double.valueOf(kmlAction.getActionActuatorFuncParam().getAircraftHeading()));
        }
        if (ObjectUtil.isNotEmpty(kmlAction.getActionActuatorFuncParam().getUseGlobalPayloadLensIndex())){
            action.setUseGlobalImageFormat(Integer.valueOf(kmlAction.getActionActuatorFuncParam().getUseGlobalPayloadLensIndex()));
        }
        action.setImageFormat(kmlAction.getActionActuatorFuncParam().getPayloadLensIndex());
        if (ObjectUtil.isNotEmpty(kmlAction.getActionActuatorFuncParam().getGimbalYawRotateAngle())){
            action.setGimbalYawRotateAngle(Double.valueOf(kmlAction.getActionActuatorFuncParam().getGimbalYawRotateAngle()));
        }
        if (ObjectUtil.isNotEmpty(kmlAction.getActionActuatorFuncParam().getGimbalPitchRotateAngle())){
            action.setGimbalPitchRotateAngle(Double.valueOf(kmlAction.getActionActuatorFuncParam().getGimbalPitchRotateAngle()));
        }
        if (ObjectUtil.isNotEmpty(kmlAction.getActionActuatorFuncParam().getFocalLength())){
            action.setZoom(Double.valueOf(kmlAction.getActionActuatorFuncParam().getFocalLength()));
        }
        if (ObjectUtil.isNotEmpty(kmlAction.getActionActuatorFuncParam().getDirectoryName())){
            action.setDirectoryName(kmlAction.getActionActuatorFuncParam().getDirectoryName());
        }
        if (ObjectUtil.isNotEmpty(kmlAction.getActionActuatorFuncParam().getFileSuffix())){
            action.setFileSuffix(kmlAction.getActionActuatorFuncParam().getFileSuffix());
        }
        return action;
    }

    public static void preBuildKmz(UavRouteReq uavRouteReq){
        Mapping2dUtils.preBuildKmz(uavRouteReq);
    }

    public static String buildKmz(UavRouteReq uavRouteReq) {
        Mapping2dUtils.simRoutePointList2RoutePointList(uavRouteReq);
        KmlParams kmlParams = new KmlParams();
        BeanUtils.copyProperties(uavRouteReq, kmlParams);
        kmlParams.setRoutePointList(BeanUtil.copyToList(uavRouteReq.getRoutePointList(), RoutePointInfo.class));
        return RouteFileUtils.buildKmz(UUID.randomUUID().toString(), kmlParams);
    }

    // TODO 导入mapping2d
    public static UavRouteReq parseKmz(InputStream inputStream) {
        KmzInfoVO kmzInfoVO = new KmzInfoVO();
        try (ArchiveInputStream archiveInputStream = new ZipArchiveInputStream(inputStream)) {
            ArchiveEntry entry;
            while (!Objects.isNull(entry = archiveInputStream.getNextEntry())) {
                String name = entry.getName();
                if (name.toLowerCase().endsWith(".kml")) {
                    kmzInfoVO.setKmlInfo(RouteFileUtils.parseKml(archiveInputStream));
                } else if (name.toLowerCase().endsWith(".wpml")) {
                    kmzInfoVO.setWpmlInfo(RouteFileUtils.parseKml(archiveInputStream));
                }
            }
            UavRouteReq uavRouteReq = new UavRouteReq();
            reflectToUavRouteReq(uavRouteReq, kmzInfoVO);
            return uavRouteReq;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("parse kmz error", e);
        }
    }

    private static String getContentType(String fileName) {
        if (fileName.endsWith(".txt")) return "text/plain";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream"; // 默认二进制流
    }

    public static MultipartFile convert(String filePath, String filename) throws IOException {
        // 参数说明：字段名、文件名、文件类型、文件输入流
        MultipartFile file = new CustomMultipartFile(
                "file",
                filename,
                getContentType(filename),
                filePath
        );
        Path path = Paths.get(filePath);
        Files.deleteIfExists(path);
        return file;
    }

    public static String getKmzFileName(String fileName) {
        return String.format("%s.kmz", fileName);
    }
}
