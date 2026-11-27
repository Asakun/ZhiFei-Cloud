package com.cleaner.djuav;

import com.cleaner.djuav.util.Mapping2dPathGenerator;
import com.cleaner.djuav.util.model.Cell;
import com.cleaner.djuav.util.model.Gps;
import com.cleaner.djuav.util.model.Point2D;
import com.cleaner.djuav.util.model.WayPoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static com.cleaner.djuav.util.Mapping2dPathGenerator.*;
import static com.cleaner.djuav.util.GeoCalculateUtils.*;
import static com.cleaner.djuav.util.PolygonUtils.decomposeConcavePolygon;
import static com.cleaner.djuav.util.kmlUtils.*;

@SpringBootTest
class DjUavApplicationTests {

    @Test
    void contextLoads() {
    }

    /**
     * 测试主函数
     */
    public static void main(String[] args) {
        System.out.println("=== 覆盖航线生成工具测试 ===\n");

        String root = "/Users/dyw/Downloads/wpmz-3/";

        // 示例1: 从KML文件读取多边形
        try {
            String kmlPath = root+"template.kml";
            List<Gps> polygon = readPolygonFromKml(kmlPath);

            System.out.println("从KML读取的多边形顶点数: " + polygon.size());
            System.out.println("多边形坐标:");
            for (int i = 0; i < polygon.size(); i++) {
                Gps p = polygon.get(i);
                System.out.printf("  点%d: (%.8f, %.8f)\n", i + 1, p.getWgLon(), p.getWgLat());
            }

            // 从KML读取起飞参考点作为起点
            WayPoint startPoint = readTakeOffPointFromKml(kmlPath);
            System.out.println("\n从KML读取起飞参考点:");
            System.out.printf("  起飞点: (%.8f, %.8f)\n", startPoint.getWgLon(), startPoint.getWgLat());

            // 从KML读取飞行高度、重叠率、航向角并计算航线间距
            Double height = readHeightFromKml(kmlPath);
            double[] overlap = readOverlapFromKml(kmlPath);
            Double direction = readDirectionFromKml(kmlPath);
            double fieldOfViewWidth;

            double overlapW = overlap[0];
            double overlapH = overlap[1];
            fieldOfViewWidth = calculateFieldOfViewWidth(height, 73.7, overlapW);

            System.out.println("\n从KML读取参数:");
            System.out.printf("  飞行高度: %.2f米\n", height);
            System.out.printf("  旁向重叠率: %.0f%%\n", overlapW);
            System.out.printf("  航向重叠率: %.0f%%\n", overlapH);
            System.out.printf("  航线方向角: %.0f° (0=正北，顺时针)\n", direction != null ? direction : 0.0);
            System.out.printf("  实际航线间距: %.2f米 (考虑%.0f%%重叠)\n", fieldOfViewWidth, overlapW);

            System.out.println("\n生成覆盖航线参数:");
            System.out.println("  视野宽度: " + fieldOfViewWidth + "米");
            System.out.printf("  起点: (%.8f, %.8f)\n", startPoint.getWgLon(), startPoint.getWgLat());

            direction = Mapping2dPathGenerator.calculateOptimalDirection(polygon, fieldOfViewWidth);
            long startTime = System.currentTimeMillis();
            List<WayPoint> waypoints = generateCoveragePath(polygon, fieldOfViewWidth, startPoint, height, direction, 1);
            long endTime = System.currentTimeMillis();

            System.out.println("\n生成结果:");
            System.out.println("  航点总数: " + waypoints.size());
            System.out.println("  计算耗时: " + (endTime - startTime) + "ms");

            // 打印前10个航点
            System.out.println("\n前10个航点:");
            for (int i = 0; i < Math.min(10, waypoints.size()); i++) {
                Gps wp = waypoints.get(i);
                System.out.printf("  航点%d: (%.8f, %.8f)\n", i + 1, wp.getWgLon(), wp.getWgLat());
            }

            // 计算总航线长度
            double totalDistance = getTotalDistance(waypoints);
            System.out.printf("\n估算航线总长度: %.2f米\n", totalDistance);

            // 测试凹多边形分解
            List<Cell> cells = testConcavePolygonDecomposition(root);

            // 保存为GeoJSON文件（包含多边形、分解单元和航线）
            String geoJsonPath = root+"waypoints.geojson";
            Gps ref = polygon.get(0);
            saveWaypointsToGeoJson(waypoints, polygon, cells, ref, geoJsonPath);

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 测试凹多边形分解功能
     */
    public static List<Cell> testConcavePolygonDecomposition(String root) {
        System.out.println("=== 凹多边形分解测试 ===\n");

        try {
            // 从KML文件读取多边形
            String kmlPath = root + "template.kml";
            List<Gps> polygon = readPolygonFromKml(kmlPath);

            System.out.println("输入多边形顶点数: " + polygon.size());
            System.out.println("多边形坐标:");
            for (int i = 0; i < polygon.size(); i++) {
                Gps p = polygon.get(i);
                System.out.printf("  点%d: (%.8f, %.8f)\n",
                        i + 1, p.getWgLon(), p.getWgLat());
            }

            // 转换为Point2D坐标
            Gps ref = polygon.get(0);
            List<Point2D> localPolygon = polygon.stream()
                    .map(p -> latLonToMeters(p.getWgLat(), p.getWgLon(), ref.getWgLat(), ref.getWgLon()))
                    .collect(java.util.stream.Collectors.toList());

            // 判断凹凸性
            boolean isConcave = isConcavePolygon(localPolygon);
            System.out.println("\n多边形类型: " + (isConcave ? "凹多边形" : "凸多边形"));

            // 如果是凹多边形，进行分解
            List<Cell> cells;
            if (isConcave) {
                cells = decomposeConcavePolygon(localPolygon);
                System.out.println("\n分解结果: " + cells.size() + " 个凸单元\n");

                for (int i = 0; i < cells.size(); i++) {
                    Cell cell = cells.get(i);
                    System.out.println("单元" + (i + 1) + ":");
                    System.out.printf("  顶点数: %d\n", cell.vertices.size());
                    System.out.printf("  中心点: (%.2f, %.2f)\n", cell.center.x, cell.center.y);
                }
            } else {
                cells = java.util.Collections.singletonList(new Cell(localPolygon));
                System.out.println("\n凸多边形，无需分解\n");
            }

            return cells;

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
            return java.util.Collections.emptyList();
        }
    }

}
