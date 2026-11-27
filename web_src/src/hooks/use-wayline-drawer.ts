import { ref, Ref } from "vue";
import { WaylineData, WayPoint } from "@/utils/kml-parser";
import * as Cesium from "cesium";

/**
 * 航线绘制管理
 */
export function useWaylineDrawer() {
  const currentWaylineEntities: Ref<any[]> = ref([]);

  /**
   * 在 Cesium 地图上绘制航线
   */
  function drawWayline(viewer: any, waylineData: WaylineData) {
    if (!viewer) return;

    const cesiumInstance = (window as any).Cesium || Cesium;
    if (!cesiumInstance) return;

    clearWayline(viewer);

    const { waypoints, takeOffRefPoint } = waylineData;

    // 起飞点的海拔高度
    const takeoffAltitude = takeOffRefPoint?.height || 0;

    // 绘制航点
    waypoints.forEach((wp: WayPoint) => {
      const relativeHeight = wp.height || 0; // 相对高度
      const absoluteHeight = takeoffAltitude + relativeHeight; // 海拔高度
      const entity = viewer.entities.add({
        position: Cesium.Cartesian3.fromDegrees(wp.longitude, wp.latitude, absoluteHeight),
        point: {
          pixelSize: 10,
          color: Cesium.Color.YELLOW,
          outlineColor: Cesium.Color.BLACK,
          outlineWidth: 2,
        },
        label: {
          text: `航点${wp.index}\n相对: ${relativeHeight.toFixed(1)}m\n海拔: ${absoluteHeight.toFixed(1)}m`,
          font: "14px sans-serif",
          fillColor: Cesium.Color.WHITE,
          outlineColor: Cesium.Color.BLACK,
          outlineWidth: 2,
          style: Cesium.LabelStyle.FILL_AND_OUTLINE,
          verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
          pixelOffset: new Cesium.Cartesian2(0, -15),
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
        },
      });
      currentWaylineEntities.value.push(entity);

      // 绘制垂直辅助线（从地面到航点）
      const groundPosition = Cesium.Cartesian3.fromDegrees(wp.longitude, wp.latitude, 0);
      const skyPosition = Cesium.Cartesian3.fromDegrees(wp.longitude, wp.latitude, absoluteHeight);

      const verticalLinePositions = [groundPosition, skyPosition];
      const verticalEntity = viewer.entities.add({
        polyline: {
          positions: verticalLinePositions,
          width: 1,
          material: new Cesium.ColorMaterialProperty(Cesium.Color.GRAY.withAlpha(0.6)),
        },
      });
      currentWaylineEntities.value.push(verticalEntity);
    });

    // 绘制航线
    if (waypoints.length > 1) {
      const positions = waypoints.map((wp: WayPoint) => {
        const relativeHeight = wp.height || 0;
        const absoluteHeight = takeoffAltitude + relativeHeight;
        return Cesium.Cartesian3.fromDegrees(wp.longitude, wp.latitude, absoluteHeight);
      });

      const lineEntity = viewer.entities.add({
        polyline: {
          positions: positions,
          width: 3,
          material: new Cesium.ColorMaterialProperty(Cesium.Color.CYAN),
        },
      });
      currentWaylineEntities.value.push(lineEntity);
    }

    // 绘制起飞点
    if (takeOffRefPoint) {
      const takeoffHeight = takeOffRefPoint.height || 0;
      const entity = viewer.entities.add({
        position: Cesium.Cartesian3.fromDegrees(takeOffRefPoint.longitude, takeOffRefPoint.latitude, takeoffHeight),
        point: {
          pixelSize: 12,
          color: Cesium.Color.GREEN,
          outlineColor: Cesium.Color.BLACK,
          outlineWidth: 2,
        },
        label: {
          text: `起飞点\n海拔: ${takeoffHeight.toFixed(1)}m`,
          font: "14px sans-serif",
          fillColor: Cesium.Color.WHITE,
          outlineColor: Cesium.Color.BLACK,
          outlineWidth: 2,
          style: Cesium.LabelStyle.FILL_AND_OUTLINE,
          verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
          pixelOffset: new Cesium.Cartesian2(0, -15),
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
        },
      });
      currentWaylineEntities.value.push(entity);

      // 起飞点到首个航点的连线
      if (waypoints.length > 0) {
        const firstRelativeHeight = waypoints[0].height || 0;
        const firstAbsoluteHeight = takeoffAltitude + firstRelativeHeight;
        const takeoffLinePositions = [
          Cesium.Cartesian3.fromDegrees(takeOffRefPoint.longitude, takeOffRefPoint.latitude, takeoffHeight),
          Cesium.Cartesian3.fromDegrees(waypoints[0].longitude, waypoints[0].latitude, firstAbsoluteHeight),
        ];

        const takeoffEntity = viewer.entities.add({
          polyline: {
            positions: takeoffLinePositions,
            width: 3,
            material: new Cesium.ColorMaterialProperty(Cesium.Color.LIME),
          },
        });
        currentWaylineEntities.value.push(takeoffEntity);
      }
    }

    // 相机飞到起飞点（如果有），否则飞到第一个航点
    if (takeOffRefPoint) {
      viewer.camera.flyTo({
        destination: Cesium.Cartesian3.fromDegrees(takeOffRefPoint.longitude, takeOffRefPoint.latitude, 8000),
        duration: 2,
      });
    } else if (waypoints.length > 0) {
      viewer.camera.flyTo({
        destination: Cesium.Cartesian3.fromDegrees(waypoints[0].longitude, waypoints[0].latitude, 8000),
        duration: 2,
      });
    }
  }

  /**
   * 清除地图上的航线
   */
  function clearWayline(viewer: any) {
    if (!viewer) return;
    currentWaylineEntities.value.forEach((item) => {
      if (item.isPrimitive) {
        viewer.scene.primitives.remove(item.primitive);
      } else {
        viewer.entities.remove(item);
      }
    });
    currentWaylineEntities.value = [];
  }

  return {
    drawWayline,
    clearWayline,
  };
}
