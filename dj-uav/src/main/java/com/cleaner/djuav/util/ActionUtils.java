package com.cleaner.djuav.util;

import com.cleaner.djuav.domain.PointActionReq;
import com.cleaner.djuav.domain.kml.KmlAction;
import com.cleaner.djuav.domain.kml.KmlActionActuatorFuncParam;
import com.cleaner.djuav.domain.kml.KmlParams;
import com.cleaner.djuav.enums.kml.ActionActuatorFuncEnums;
import com.cleaner.djuav.enums.kml.AircraftPathModeEnums;
import com.cleaner.djuav.enums.kml.ZoomFactorEnums;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ActionUtils {
    /**
     * 构建航点动作
     *
     * @param actionList 航点动作列表
     * @param kmlParams
     * @return
     */
    public static List<KmlAction> getKmlActionList(List<PointActionReq> actionList, KmlParams kmlParams) {
        List<KmlAction> kmlActionList = new ArrayList<>();
        for (PointActionReq pointActionReq : actionList) {
            if (!shouldBuildAction(pointActionReq)) {
                continue;
            }
            String actionId = String.valueOf(pointActionReq.getActionIndex());
            kmlActionList.add(buildKmlAction(actionId, pointActionReq, kmlParams));
        }
        return kmlActionList;
    }

    /**
     * 保留原有筛选规则：忽略未支持的动作，以及缺少必要参数的部分动作。
     * 定时拍摄等动作的进一步参数校验仍由参数构建方法负责。
     */
    private static boolean shouldBuildAction(PointActionReq action) {
        ActionActuatorFuncEnums actionFunc = Arrays.stream(ActionActuatorFuncEnums.values())
                .filter(type -> type.getValue().equals(action.getActionFunc()))
                .findFirst()
                .orElse(null);
        if (actionFunc == null) {
            return false;
        }
        switch (actionFunc) {
            case HOVER:
                return action.getHoverTime() != null;
            case ROTATE_YAW:
                return action.getAircraftHeading() != null;
            case GIMBAL_ROTATE:
                return action.getGimbalYawRotateAngle() != null || action.getGimbalPitchRotateAngle() != null;
            case ZOOM:
                return action.getZoom() != null;
            case TAKE_PHOTO:
            case PANO_SHOT:
            case START_RECORD:
            case STOP_RECORD:
            case CUSTOM_DIRNAME:
            case ORIENTED_SHOOT:
            case GIMBAL_ANGEL_LOCK:
            case GIMBAL_ANGEL_UNLOCK:
            case START_TIME_LAPSE:
            case STOP_TIME_LAPSE:
            case FOCUS:
            case SET_FOCUS_TYPE:
                return true;
            default:
                return false;
        }
    }

    private static KmlAction buildKmlAction(String actionId, PointActionReq pointActionReq, KmlParams kmlParams) {
        String actionActuatorFunc = pointActionReq.getActionFunc();
        KmlAction kmlAction = new KmlAction();
        kmlAction.setActionId(actionId);
        kmlAction.setActionActuatorFunc(actionActuatorFunc);
        kmlAction.setActionActuatorFuncParam(buildKmlActionActuatorFuncParam(actionActuatorFunc, pointActionReq, kmlParams));
        return kmlAction;
    }

    private static KmlActionActuatorFuncParam buildKmlActionActuatorFuncParam(String actionActuatorFunc, PointActionReq pointActionReq, KmlParams kmlParams) {
        KmlActionActuatorFuncParam kmlActionActuatorFuncParam = new KmlActionActuatorFuncParam();
        // WPML 解锁动作不包含 actionActuatorFuncParam 节点。
        if (ActionActuatorFuncEnums.GIMBAL_ANGEL_UNLOCK.getValue().equals(actionActuatorFunc)) {
            return kmlActionActuatorFuncParam;
        } else if (ActionActuatorFuncEnums.GIMBAL_ANGEL_LOCK.getValue().equals(actionActuatorFunc)) {
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
        } else if (ActionActuatorFuncEnums.START_TIME_LAPSE.getValue().equals(actionActuatorFunc)) {
            Double interval = pointActionReq.getMinShootInterval();
            if (interval == null || !Double.isFinite(interval) || interval <= 0) {
                throw new IllegalArgumentException("startTimeLapse minShootInterval must be a finite positive number of seconds");
            }
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
            kmlActionActuatorFuncParam.setMinShootInterval(String.valueOf(interval));
            boolean explicitLens = StringUtils.isNotBlank(pointActionReq.getImageFormat());
            kmlActionActuatorFuncParam.setUseGlobalPayloadLensIndex(explicitLens ? "0" : "1");
            if (explicitLens) {
                kmlActionActuatorFuncParam.setPayloadLensIndex(pointActionReq.getImageFormat());
            }
        } else if (ActionActuatorFuncEnums.STOP_TIME_LAPSE.getValue().equals(actionActuatorFunc)) {
            String lens = StringUtils.isNotBlank(pointActionReq.getImageFormat())
                    ? pointActionReq.getImageFormat() : kmlParams.getImageFormat();
            if (StringUtils.isBlank(lens)) {
                throw new IllegalArgumentException("stopTimeLapse requires an action or global imageFormat");
            }
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
            kmlActionActuatorFuncParam.setPayloadLensIndex(lens);
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.TAKE_PHOTO.getValue()) ||
                StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.START_RECORD.getValue())) {
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
            setPayloadLensIndex(kmlActionActuatorFuncParam, pointActionReq, actionActuatorFunc);
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.STOP_RECORD.getValue())) {
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.GIMBAL_ROTATE.getValue())) {
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
            kmlActionActuatorFuncParam.setGimbalHeadingYawBase(pointActionReq.getGimbalHeadingYawBase());
            kmlActionActuatorFuncParam.setGimbalRotateMode("absoluteAngle");
            if (!Objects.isNull(pointActionReq.getGimbalPitchRotateAngle())) {
                kmlActionActuatorFuncParam.setGimbalPitchRotateEnable("1");
                kmlActionActuatorFuncParam.setGimbalPitchRotateAngle(String.valueOf(pointActionReq.getGimbalPitchRotateAngle()));
            } else {
                kmlActionActuatorFuncParam.setGimbalPitchRotateEnable("0");
                kmlActionActuatorFuncParam.setGimbalPitchRotateAngle("0");
            }
            kmlActionActuatorFuncParam.setGimbalRollRotateEnable("0");
            kmlActionActuatorFuncParam.setGimbalRollRotateAngle("0");
            if (!Objects.isNull(pointActionReq.getGimbalYawRotateAngle())) {
                kmlActionActuatorFuncParam.setGimbalYawRotateEnable("1");
                kmlActionActuatorFuncParam.setGimbalYawRotateAngle(String.valueOf(pointActionReq.getGimbalYawRotateAngle()));
            } else {
                kmlActionActuatorFuncParam.setGimbalYawRotateEnable("0");
                kmlActionActuatorFuncParam.setGimbalYawRotateAngle("0");
            }
            kmlActionActuatorFuncParam.setGimbalRotateTimeEnable("0");
            kmlActionActuatorFuncParam.setGimbalRotateTime(String.valueOf(pointActionReq.getGimbalRotateTime()));
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.ROTATE_YAW.getValue())) {
            kmlActionActuatorFuncParam.setAircraftHeading(String.valueOf(pointActionReq.getAircraftHeading()));
            if (pointActionReq.getAircraftHeading() >= 0) {
                kmlActionActuatorFuncParam.setAircraftPathMode(AircraftPathModeEnums.CLOCKWISE.getValue());
            } else {
                kmlActionActuatorFuncParam.setAircraftPathMode(AircraftPathModeEnums.COUNTER_CLOCKWISE.getValue());
            }
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.HOVER.getValue())) {
            kmlActionActuatorFuncParam.setHoverTime(String.valueOf(pointActionReq.getHoverTime()));
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.SET_FOCUS_TYPE.getValue())) {
            kmlActionActuatorFuncParam.setCameraFocusType(pointActionReq.getCameraFocusType());
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.FOCUS.getValue())) {
            kmlActionActuatorFuncParam.setFocusX(String.valueOf(pointActionReq.getFocusX() == null ? 0 : pointActionReq.getFocusX()));
            kmlActionActuatorFuncParam.setFocusY(String.valueOf(pointActionReq.getFocusY() == null ? 0 : pointActionReq.getFocusY()));
            kmlActionActuatorFuncParam.setFocusRegionWidth(String.valueOf(pointActionReq.getFocusRegionWidth() == null ? 0 : pointActionReq.getFocusRegionWidth()));
            kmlActionActuatorFuncParam.setFocusRegionHeight(String.valueOf(pointActionReq.getFocusRegionHeight() == null ? 0 : pointActionReq.getFocusRegionHeight()));
            kmlActionActuatorFuncParam.setIsPointFocus(String.valueOf(pointActionReq.getIsPointFocus() == null ? 0 : pointActionReq.getIsPointFocus()));
            kmlActionActuatorFuncParam.setIsInfiniteFocus(String.valueOf(pointActionReq.getIsInfiniteFocus() == null ? 1 : pointActionReq.getIsInfiniteFocus()));
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.ZOOM.getValue())) {
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
            kmlActionActuatorFuncParam.setFocalLength(String.valueOf(pointActionReq.getZoom()* ZoomFactorEnums.DEFAULT.getVal()));
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.PANO_SHOT.getValue())) {
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
            kmlActionActuatorFuncParam.setPanoShotSubMode("panoShot_360");
            kmlActionActuatorFuncParam.setUseGlobalPayloadLensIndex("0");
            kmlActionActuatorFuncParam.setPayloadLensIndex("wide");
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.CUSTOM_DIRNAME.getValue())) {
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
            kmlActionActuatorFuncParam.setDirectoryName(pointActionReq.getDirectoryName());
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.ORIENTED_SHOOT.getValue())) {
            if (pointActionReq.getGimbalPitchRotateAngle() == null) {pointActionReq.setGimbalPitchRotateAngle(0.);}
            kmlActionActuatorFuncParam.setGimbalPitchRotateAngle(String.valueOf(pointActionReq.getGimbalPitchRotateAngle()));
            kmlActionActuatorFuncParam.setGimbalRollRotateAngle("0");
            if (pointActionReq.getGimbalYawRotateAngle() == null) {pointActionReq.setGimbalYawRotateAngle(0.);}
            kmlActionActuatorFuncParam.setGimbalYawRotateAngle(String.valueOf(pointActionReq.getGimbalYawRotateAngle()));
            kmlActionActuatorFuncParam.setFocusX("0");
            kmlActionActuatorFuncParam.setFocusY("0");
            kmlActionActuatorFuncParam.setFocusRegionWidth("0");
            kmlActionActuatorFuncParam.setFocusRegionHeight("0");
            kmlActionActuatorFuncParam.setFocalLength(String.valueOf(pointActionReq.getZoom()*ZoomFactorEnums.DEFAULT.getVal()));
            kmlActionActuatorFuncParam.setAircraftHeading(String.valueOf(pointActionReq.getGimbalYawRotateAngle()));
            kmlActionActuatorFuncParam.setAccurateFrameValid("0");
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
            setPayloadLensIndex(kmlActionActuatorFuncParam, pointActionReq, actionActuatorFunc);
            kmlActionActuatorFuncParam.setTargetAngle("0");
            kmlActionActuatorFuncParam.setImageWidth("0");
            kmlActionActuatorFuncParam.setImageHeight("0");
            kmlActionActuatorFuncParam.setAFPos("0");
            kmlActionActuatorFuncParam.setGimbalPort("0");
            kmlActionActuatorFuncParam.setOrientedCameraType(String.valueOf(kmlParams.getPayloadType()));
            kmlActionActuatorFuncParam.setOrientedPhotoMode("normalPhoto");
        }
        return kmlActionActuatorFuncParam;
    }

    private static void setPayloadLensIndex(KmlActionActuatorFuncParam kmlActionActuatorFuncParam, PointActionReq pointActionReq, String actionActuatorFunc) {
        if (StringUtils.isNotEmpty(pointActionReq.getImageFormat())) {
            pointActionReq.setUseGlobalImageFormat(0);
            kmlActionActuatorFuncParam.setPayloadLensIndex(pointActionReq.getImageFormat());
        } else {
            pointActionReq.setUseGlobalImageFormat(1);
        }
        kmlActionActuatorFuncParam.setUseGlobalPayloadLensIndex(String.valueOf(pointActionReq.getUseGlobalImageFormat()));
        if (StringUtils.isNotEmpty(pointActionReq.getFileSuffix())) {
            if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.ORIENTED_SHOOT.getValue())) {
                kmlActionActuatorFuncParam.setOrientedFileSuffix(pointActionReq.getFileSuffix());
            } else {
                kmlActionActuatorFuncParam.setFileSuffix(pointActionReq.getFileSuffix());
            }
        }
    }

}
