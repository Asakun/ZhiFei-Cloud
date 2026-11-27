package com.cleaner.djuav.util;

import com.cleaner.djuav.domain.*;
import com.cleaner.djuav.enums.DeviceFovEnum;
import com.cleaner.djuav.enums.WayPointTypeEnum;
import com.cleaner.djuav.enums.kml.*;
import com.cleaner.djuav.util.model.Gps;
import com.cleaner.djuav.util.model.WayPoint;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Mapping2dUtils {

    private static final double MIN_SHOOT_INTERVAL = 2.0;
    private static final double HOVER_TIME = 0.5;
    private static final double NORMAL_GIMBAL_PITCH = -90.0;
    private static final double ELEVATION_GIMBAL_PITCH = -45.0;

    public static void preBuildKmz(UavRouteReq uavRouteReq){
        if (uavRouteReq.getHeightMode() == null) {uavRouteReq.setHeightMode("relativeToStartPoint");}
        if (TemplateTypeEnums.MAPPING2D.getValue().equals(uavRouteReq.getTemplateType())) {
            Double height = uavRouteReq.getGlobalHeight();
            if (HeightModeEnums.RELATIVE_TO_START_POINT.getValue().equals(uavRouteReq.getHeightMode())) {
                height = height + uavRouteReq.getTakeOffRefPointAGLHeight();
            }
            Integer overlapW = uavRouteReq.getMappingTypeReq().getOverlapW();
            float hfov = DeviceFovEnum.findDegreeByType(uavRouteReq.getPayloadType());
            double fieldOfViewWidth = GeoCalculateUtils.calculateFieldOfViewWidth(height, hfov, overlapW);
            List<Gps> gpsCoords = listCoordinatePointReq2listGps(uavRouteReq.getMappingTypeReq().getCoordinates());
            Double direction;
            if (StringUtils.isEmpty(uavRouteReq.getMappingTypeReq().getDirection())){
                direction = Mapping2dPathGenerator.calculateOptimalDirection(gpsCoords, fieldOfViewWidth);
                uavRouteReq.getMappingTypeReq().setDirection(String.valueOf(direction));
            } else {
                direction = Double.parseDouble(uavRouteReq.getMappingTypeReq().getDirection());
            }
            WayPoint startPoint = new WayPoint(uavRouteReq.getTakeOffRefPoint());
            List<WayPoint> waypoints = Mapping2dPathGenerator.generateCoveragePath(
                    gpsCoords, fieldOfViewWidth, startPoint, height, direction,
                    uavRouteReq.getMappingTypeReq().getElevationOptimizeEnable());
            uavRouteReq.getMappingTypeReq().setSimRoutePointList(waypoints);
        }
    }

    private static List<Gps> listCoordinatePointReq2listGps(List<CoordinatePointReq> polygonCoords) {
        List<Gps> gpsCoords = new ArrayList<>();
        for (CoordinatePointReq point : polygonCoords) {
            Gps gps = new Gps();
            gps.setWgLon(point.getLongitude());
            gps.setWgLat(point.getLatitude());
            gpsCoords.add(gps);
        }
        return gpsCoords;
    }

    public static void simRoutePointList2RoutePointList(UavRouteReq uavRouteReq) {
        MappingTypeReq mappingTypeReq = uavRouteReq.getMappingTypeReq();
        if (mappingTypeReq == null || mappingTypeReq.getSimRoutePointList() == null) {
            return;
        }

        List<WayPoint> simRoutePointList = mappingTypeReq.getSimRoutePointList();
        List<RoutePointReq> routePointList = new ArrayList<>();
        int actionGroupIdCounter = 0;
        
        String imageFormat = uavRouteReq.getImageFormat();
        if (imageFormat == null || imageFormat.isEmpty()) {
            imageFormat = "visable";
        }

        int endIndex = findEndPhotoIndex(simRoutePointList, 0);

        // Build start action group (per waylines reference)
        uavRouteReq.setStartActionList(buildStartActionList());

        for (int i = 0; i < simRoutePointList.size(); i++) {
            WayPoint wayPoint = simRoutePointList.get(i);
            RoutePointReq routePointReq = new RoutePointReq();
            
            routePointReq.setRoutePointIndex(i);
            routePointReq.setLongitude(wayPoint.getWgLon());
            routePointReq.setLatitude(wayPoint.getWgLat());
            routePointReq.setHeight(wayPoint.getHeight());
            routePointReq.setSpeed(uavRouteReq.getAutoFlightSpeed());

            // Calculate and set waypoint heading
            WaypointHeadingReq headingReq = new WaypointHeadingReq();
            if (uavRouteReq.getWaypointHeadingReq() != null) {
                headingReq.setWaypointHeadingMode(uavRouteReq.getWaypointHeadingReq().getWaypointHeadingMode());
                headingReq.setWaypointPoiPoint(uavRouteReq.getWaypointHeadingReq().getWaypointPoiPoint());
            } else {
                headingReq.setWaypointHeadingMode("followWayline");
                headingReq.setWaypointPoiPoint("0.000000,0.000000,0.000000");
            }
            
            if (i < simRoutePointList.size() - 1) {
                WayPoint nextPoint = simRoutePointList.get(i + 1);
                double heading = calculateHeading(wayPoint, nextPoint);
                headingReq.setWaypointHeadingAngle(heading);
            } else {
                headingReq.setWaypointHeadingAngle(0.0);
            }
            routePointReq.setWaypointHeadingReq(headingReq);

            // Set waypoint turn parameters (fixed values per waylines reference)
            WaypointTurnReq turnReq = getWaypointTurnReq(i, simRoutePointList, mappingTypeReq.getElevationOptimizeEnable());
            routePointReq.setWaypointTurnReq(turnReq);

            List<ActionGroupReq> actionGroupList = new ArrayList<>();
            WayPointTypeEnum type = wayPoint.getType();

            if (type == WayPointTypeEnum.START_PHOTO) {
                List<ActionGroupReq> groups = createStartPhotoActionGroups(
                    actionGroupIdCounter, i, endIndex, NORMAL_GIMBAL_PITCH, MIN_SHOOT_INTERVAL, imageFormat);
                actionGroupList.addAll(groups);
                actionGroupIdCounter += groups.size();
            } else if (type == WayPointTypeEnum.END_PHOTO) {
                ActionGroupReq group = createEndPhotoActionGroup(actionGroupIdCounter, i, imageFormat);
                actionGroupList.add(group);
                actionGroupIdCounter++;
                
                if (i + 1 < simRoutePointList.size()) {
                    endIndex = findEndPhotoIndex(simRoutePointList, i + 1);
                }
            } else if (type == WayPointTypeEnum.ELEVATION_OPTIMIZE) {
                List<ActionGroupReq> groups = createElevationOptimizeActionGroups(
                    actionGroupIdCounter, i, endIndex, ELEVATION_GIMBAL_PITCH, MIN_SHOOT_INTERVAL, imageFormat);
                actionGroupList.addAll(groups);
                actionGroupIdCounter += groups.size();
            }

            routePointReq.setActionGroupList(actionGroupList);
            routePointList.add(routePointReq);
        }

        uavRouteReq.setRoutePointList(routePointList);
    }

    private static @NotNull WaypointTurnReq getWaypointTurnReq(int i, List<WayPoint> simRoutePointList, Integer elevationOptimizeEnable) {
        WaypointTurnReq turnReq = new WaypointTurnReq();
        int endIndex = simRoutePointList.size() - 1;
        if (1 == elevationOptimizeEnable) {
            endIndex = simRoutePointList.size() - 3;
        }
        if (0 < i && i < endIndex) {
            // 中间点：协调转弯，阻尼10
            turnReq.setWaypointTurnMode("coordinateTurn");
            turnReq.setWaypointTurnDampingDist(10.0);
        } else {
            // 起始点/终点：到点停，阻尼0
            turnReq.setWaypointTurnMode("toPointAndStopWithDiscontinuityCurvature");
            turnReq.setWaypointTurnDampingDist(0.0);
        }
        turnReq.setUseStraightLine(1);
        return turnReq;
    }

    private static List<PointActionReq> buildStartActionList() {
        List<PointActionReq> actions = new ArrayList<>();

        // Action 0: gimbalRotate -90°
        actions.add(createGimbalRotateAction(0, NORMAL_GIMBAL_PITCH, 0.0));

        // Action 1: hover 0.5s
        actions.add(createHoverAction(1, 0.5));

        // Action 2: setFocusType manual
        PointActionReq setFocusType = new PointActionReq();
        setFocusType.setActionIndex(2);
        setFocusType.setActionFunc(ActionActuatorFuncEnums.SET_FOCUS_TYPE.getValue());
        setFocusType.setCameraFocusType("manual");
        actions.add(setFocusType);

        // Action 3: focus infinite
        PointActionReq focus = new PointActionReq();
        focus.setActionIndex(3);
        focus.setActionFunc(ActionActuatorFuncEnums.FOCUS.getValue());
        focus.setIsInfiniteFocus(1);
        actions.add(focus);

        // Action 4: hover 1s
        actions.add(createHoverAction(4, 1.0));

        return actions;
    }

    private static double calculateHeading(WayPoint from, WayPoint to) {
        double lat1 = Math.toRadians(from.getWgLat());
        double lat2 = Math.toRadians(to.getWgLat());
        double deltaLon = Math.toRadians(to.getWgLon() - from.getWgLon());
        
        double y = Math.sin(deltaLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - 
                   Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);

        return Math.toDegrees(Math.atan2(y, x));
    }

    private static int findEndPhotoIndex(List<WayPoint> waypoints, int startIndex) {
        for (int i = startIndex; i < waypoints.size(); i++) {
            if (waypoints.get(i).getType() == WayPointTypeEnum.END_PHOTO ||
                waypoints.get(i).getType() == WayPointTypeEnum.ELEVATION_OPTIMIZE) {
                return i;
            }
        }
        return waypoints.size() - 1;
    }

    private static List<ActionGroupReq> createStartPhotoActionGroups(
            int actionGroupIdCounter, int currentIndex, int endIndex, 
            double gimbalPitch, double interval, String imageFormat) {
        
        List<ActionGroupReq> groups = new ArrayList<>();

        // ActionGroup 1: betweenAdjacentPoints
        ActionGroupReq group1 = createBetweenAdjacentPointsActionGroup(
            actionGroupIdCounter, currentIndex, endIndex);

        List<PointActionReq> actions1 = new ArrayList<>();
        
        // Action 0: gimbalAngleLock
        PointActionReq lockAction = createGimbalLockAction(0);
        actions1.add(lockAction);

        // Action 1: gimbalRotate
        PointActionReq rotateAction = createGimbalRotateAction(1, gimbalPitch, null);
        actions1.add(rotateAction);

        // Action 2: startTimeLapse
        PointActionReq startPhotoAction = createStartTimeLapseAction(2, interval, imageFormat);
        actions1.add(startPhotoAction);

        group1.setActions(actions1);
        groups.add(group1);

        // ActionGroup 2: multipleTiming
        ActionGroupReq group2 = createMultipleTimingActionGroup(
            actionGroupIdCounter + 1, currentIndex, endIndex, interval);

        List<PointActionReq> actions2 = new ArrayList<>();
        
        // Action 0: gimbalRotate
        PointActionReq rotateAction2 = createGimbalRotateAction(0, gimbalPitch, null);
        actions2.add(rotateAction2);

        group2.setActions(actions2);
        groups.add(group2);

        return groups;
    }

    private static ActionGroupReq createEndPhotoActionGroup(
            int actionGroupId, int index, String imageFormat) {
        
        ActionGroupReq group = createReachPointActionGroup(actionGroupId, index, index);

        List<PointActionReq> actions = new ArrayList<>();

        // Action 0: stopTimeLapse
        PointActionReq stopPhotoAction = createStopTimeLapseAction(0, imageFormat);
        actions.add(stopPhotoAction);

        // Action 1: gimbalAngleUnlock
        PointActionReq unlockAction = createGimbalUnlockAction(1);
        actions.add(unlockAction);

        group.setActions(actions);
        return group;
    }

    private static List<ActionGroupReq> createElevationOptimizeActionGroups(
            int actionGroupIdCounter, int currentIndex, int endIndex,
            double gimbalPitch, double interval, String imageFormat) {
        
        List<ActionGroupReq> groups = new ArrayList<>();

        // ActionGroup 1: reachPoint (gimbalRotate + hover)
        ActionGroupReq group1 = createReachPointActionGroup(actionGroupIdCounter, currentIndex, currentIndex);

        List<PointActionReq> actions1 = new ArrayList<>();

        // Action 0: gimbalRotate with yaw
        PointActionReq rotateAction1 = createGimbalRotateAction(0, gimbalPitch, 0.0);
        actions1.add(rotateAction1);

        // Action 1: hover
        PointActionReq hoverAction = createHoverAction(1, HOVER_TIME);
        actions1.add(hoverAction);

        group1.setActions(actions1);
        groups.add(group1);

        // ActionGroup 2: betweenAdjacentPoints (lock + rotate + startTimeLapse)
        ActionGroupReq group2 = createBetweenAdjacentPointsActionGroup(
            actionGroupIdCounter + 1, currentIndex, endIndex);

        List<PointActionReq> actions2 = new ArrayList<>();
        
        // Action 0: gimbalAngleLock
        PointActionReq lockAction = createGimbalLockAction(0);
        actions2.add(lockAction);

        // Action 1: gimbalRotate
        PointActionReq rotateAction2 = createGimbalRotateAction(1, gimbalPitch, null);
        actions2.add(rotateAction2);

        // Action 2: startTimeLapse
        PointActionReq startPhotoAction = createStartTimeLapseAction(2, interval, imageFormat);
        actions2.add(startPhotoAction);

        group2.setActions(actions2);
        groups.add(group2);

        // ActionGroup 3: multipleTiming
        ActionGroupReq group3 = createMultipleTimingActionGroup(
            actionGroupIdCounter + 2, currentIndex, endIndex, interval);

        List<PointActionReq> actions3 = new ArrayList<>();
        
        // Action 0: gimbalRotate
        PointActionReq rotateAction3 = createGimbalRotateAction(0, gimbalPitch, null);
        actions3.add(rotateAction3);

        group3.setActions(actions3);
        groups.add(group3);

        return groups;
    }

    private static ActionGroupReq createReachPointActionGroup(int actionGroupId, int startIndex, int endIndex) {
        ActionGroupReq group = new ActionGroupReq();
        group.setActionGroupId(actionGroupId);
        group.setActionGroupStartIndex(startIndex);
        group.setActionGroupEndIndex(endIndex);
        group.setActionTriggerType(ActionTriggerTypeEnums.REACH_POINT.getValue());
        return group;
    }

    private static ActionGroupReq createBetweenAdjacentPointsActionGroup(
            int actionGroupId, int startIndex, int endIndex) {
        ActionGroupReq group = new ActionGroupReq();
        group.setActionGroupId(actionGroupId);
        group.setActionGroupStartIndex(startIndex);
        group.setActionGroupEndIndex(endIndex);
        group.setActionTriggerType(ActionTriggerTypeEnums.BETWEEN_ADJACENT_POINTS.getValue());
        return group;
    }

    private static ActionGroupReq createMultipleTimingActionGroup(
            int actionGroupId, int startIndex, int endIndex, double interval) {
        ActionGroupReq group = new ActionGroupReq();
        group.setActionGroupId(actionGroupId);
        group.setActionGroupStartIndex(startIndex);
        group.setActionGroupEndIndex(endIndex);
        group.setActionTriggerType(ActionTriggerTypeEnums.MULTIPLE_TIMING.getValue());
        group.setActionTriggerParam(interval);
        return group;
    }

    private static PointActionReq createGimbalRotateAction(int index, double pitch, Double yaw) {
        PointActionReq action = new PointActionReq();
        action.setActionIndex(index);
        action.setActionFunc(ActionActuatorFuncEnums.GIMBAL_ROTATE.getValue());
        action.setGimbalPitchRotateAngle(pitch);
        action.setGimbalRotateTime(10);
        action.setGimbalHeadingYawBase(GimbalHeadingYawBaseEnums.AIRCRAFT.getValue());
        if (yaw != null) {
            action.setGimbalYawRotateAngle(yaw);
        }
        return action;
    }

    private static PointActionReq createHoverAction(int index, double seconds) {
        PointActionReq action = new PointActionReq();
        action.setActionIndex(index);
        action.setActionFunc(ActionActuatorFuncEnums.HOVER.getValue());
        action.setHoverTime(seconds);
        return action;
    }

    private static PointActionReq createGimbalUnlockAction(int index) {
        PointActionReq action = new PointActionReq();
        action.setActionIndex(index);
        action.setActionFunc(ActionActuatorFuncEnums.GIMBAL_ANGEL_UNLOCK.getValue());
        return action;
    }

    private static PointActionReq createGimbalLockAction(int index) {
        PointActionReq action = new PointActionReq();
        action.setActionIndex(index);
        action.setActionFunc(ActionActuatorFuncEnums.GIMBAL_ANGEL_LOCK.getValue());
        return action;
    }

    private static PointActionReq createStartTimeLapseAction(int index, double interval, String imageFormat) {
        PointActionReq action = new PointActionReq();
        action.setActionIndex(index);
        action.setActionFunc(ActionActuatorFuncEnums.START_TIME_LAPSE.getValue());
        action.setMinShootInterval(interval);
        action.setImageFormat(imageFormat);
        return action;
    }

    private static PointActionReq createStopTimeLapseAction(int index, String imageFormat) {
        PointActionReq action = new PointActionReq();
        action.setActionIndex(index);
        action.setActionFunc(ActionActuatorFuncEnums.STOP_TIME_LAPSE.getValue());
        action.setImageFormat(imageFormat);
        return action;
    }
}
