package com.cleaner.djuav.util;

import com.cleaner.djuav.enums.WayPointTypeEnum;
import com.cleaner.djuav.util.model.Cell;
import com.cleaner.djuav.util.model.Gps;
import com.cleaner.djuav.util.model.Point2D;
import com.cleaner.djuav.util.model.WayPoint;

import java.util.*;

import static com.cleaner.djuav.util.GeoCalculateUtils.*;
import static com.cleaner.djuav.util.PolygonUtils.decomposeConcavePolygon;

/**
 * 覆盖航线生成工具 - 除草机算法（Boustrophedon/Lawnmower Pattern）
 * 用于无人机测绘任务的航点路径规划
 * 
 * @author Dyw
 * @date 2026-08-28
 */
public class Mapping2dPathGenerator {
    
    /**
     * 生成覆盖航线
     * 
     * @param polygonCoords 多边形边界坐标列表
     * @param fieldOfViewWidth 航线间距（米）
     * @param startPoint 起始点坐标
     * @param globeHeight 全局高度
     * @param directionDegrees 航线航向角
     * @param enableOptimizeElevation 是否优化高程
     * @return 覆盖整个区域的航点列表
     */
    public static List<WayPoint> generateCoveragePath(
            List<Gps> polygonCoords,
            double fieldOfViewWidth,
            WayPoint startPoint,
            double globeHeight,
            double directionDegrees,
            Integer enableOptimizeElevation) {
        return generateCoveragePath(polygonCoords, fieldOfViewWidth, startPoint, globeHeight, directionDegrees,
                enableOptimizeElevation != null && enableOptimizeElevation == 1, true);
    }
    
    /**
     * 生成覆盖航线（支持自定义航向角和算法）
     * 
     * @param polygonCoords 多边形边界坐标列表
     * @param fieldOfViewWidth 航线间距（米）
     * @param startPoint 起始点坐标
     * @param decomposePolygon 是否开启凹多边形分割
     * @return 覆盖整个区域的航点列表
     */
    public static List<WayPoint> generateCoveragePath(
            List<Gps> polygonCoords,
            double fieldOfViewWidth,
            WayPoint startPoint,
            double globeHeight,
            Double directionDegrees,
            Boolean enableOptimizeElevation,
            Boolean decomposePolygon) {
        
        if (polygonCoords == null || polygonCoords.size() < 3) {
            throw new IllegalArgumentException("多边形至少需要3个顶点");
        }
        if (fieldOfViewWidth <= 0) {
            throw new IllegalArgumentException("视野宽度必须大于0");
        }

        // 自动计算最优方向
        if (directionDegrees == null){
            directionDegrees = calculateOptimalDirection(polygonCoords, fieldOfViewWidth);
        }

        // 1. 选择参考点（多边形第一个点）
        Gps refPoint = polygonCoords.get(0);

        // 2. 将WGS84坐标转换为局部平面XY坐标（米）
        List<Point2D> localPolygon = latLonListToMeters(polygonCoords, refPoint);
        Point2D center = calculatePolygonCenter(localPolygon);

        // 2.5. 旋转多边形使航线方向对齐到X轴
        double rotationAngle = 0;
        // DJI direction: 0=正北，顺时针; 数学角度: 0=东，逆时针
        // 转换: mathAngle = 90 - djiDirection
        rotationAngle = Math.toRadians(90 - directionDegrees);
        localPolygon = rotatePolygon(localPolygon, rotationAngle);

        // 3. 生成航点（凹多边形分区覆盖 vs 凸多边形直接覆盖）
        List<List<Point2D>> waypoints;

        Point2D startPoint2d = GpsToPoint(startPoint, rotationAngle, refPoint);

        if (isConcavePolygon(localPolygon) && decomposePolygon) {
            List<Cell> cells = decomposeConcavePolygon(localPolygon);
            if (!cells.isEmpty()) {
                waypoints = coverCellsInOrder(cells, fieldOfViewWidth, startPoint2d);
            } else {
                // 分解失败，退回到直接扫描线方法
                waypoints = Collections.singletonList(generateScanLineWaypoints(localPolygon, fieldOfViewWidth));
            }
        } else {
            // 凸多边形：直接生成扫描线
            waypoints = Collections.singletonList(generateScanLineWaypoints(localPolygon, fieldOfViewWidth));
        }

        // 4 如果指定了起点，从最近的端点开始排序（在局部坐标系中优化）
        if (startPoint != null && !waypoints.isEmpty()) {
            waypoints = reorderSegmentsFromStartPoint(waypoints, startPoint2d);
        }

        // 5. 将航点反向旋转回原坐标系
        List<List<Point2D>> rotatedSegments = new ArrayList<>();
        for (List<Point2D> segment : waypoints) {
            rotatedSegments.add(rotatePoints(segment, -rotationAngle));
        }
        
        // 6. 扁平化分段并转换回WGS84坐标，同时设置航点类型
        List<WayPoint> result = new ArrayList<>();
        for (List<Point2D> segment : rotatedSegments) {
            for (int i = 0; i < segment.size(); i++) {
                Point2D wp = segment.get(i);
                WayPoint coord = metersToWayPoint(wp, refPoint);
                coord.setHeight(globeHeight);
                
                if (segment.size() > 1) {
                    if (i == 0) {
                        coord.setType(WayPointTypeEnum.START_PHOTO);
                    } else if (i == segment.size() - 1) {
                        coord.setType(WayPointTypeEnum.END_PHOTO);
                    } else {
                        coord.setType(WayPointTypeEnum.NORMAL);
                    }
                } else {
                    coord.setType(WayPointTypeEnum.NORMAL);
                }
                
                result.add(coord);
            }
        }

        // 7. 如果启用高程优化，添加两个航点
        if (enableOptimizeElevation && !result.isEmpty()) {
            // 第一个点：与航线结尾点相同位置，type为ELEVATION_OPTIMIZE
            WayPoint lastPoint = result.get(result.size() - 1);
            WayPoint elevationPoint = new WayPoint();
            elevationPoint.setWgLat(lastPoint.getWgLat());
            elevationPoint.setWgLon(lastPoint.getWgLon());
            elevationPoint.setHeight(globeHeight);
            elevationPoint.setType(WayPointTypeEnum.ELEVATION_OPTIMIZE);
            result.add(elevationPoint);
            
            // 第二个点：多边形中心点，type为END_PHOTO
            WayPoint centerPoint = metersToWayPoint(center, refPoint);
            centerPoint.setHeight(globeHeight);
            centerPoint.setType(WayPointTypeEnum.END_PHOTO);
            result.add(centerPoint);
        }
        
        return result;
    }
    
    /**
     * 计算水平扫描线与线段的交点
     * 
     * @param lineY 扫描线的Y坐标
     * @param p1 线段起点
     * @param p2 线段终点
     * @return 交点的X坐标，如果没有交点返回null
     */
    public static Double lineIntersectsSegment(double lineY, Point2D p1, Point2D p2) {
        // 检查扫描线是否在线段的Y范围内
        if ((p1.y <= lineY && p2.y >= lineY) || (p2.y <= lineY && p1.y >= lineY)) {
            // 处理水平线段
            if (Math.abs(p2.y - p1.y) < 1e-10) {
                return null; // 平行，不计算交点
            }
            
            // 线性插值计算交点的X坐标
            double t = (lineY - p1.y) / (p2.y - p1.y);
            double x = p1.x + t * (p2.x - p1.x);
            return x;
        }
        return null;
    }

    /**
     * 在局部坐标系中重新排序航段，从最近的端点开始
     */
    private static List<List<Point2D>> reorderSegmentsFromStartPoint(
            List<List<Point2D>> segments,
            Point2D startPoint) {
        
        if (segments.isEmpty()) {
            return segments;
        }
        
        List<List<Point2D>> result = new ArrayList<>();
        
        // 检查第一个航段，选择最近的端点
        List<Point2D> firstSegment = segments.get(0);
        if (firstSegment.isEmpty()) {
            result.addAll(segments);
            return result;
        }
        
        Point2D firstPoint = firstSegment.get(0);
        Point2D lastPoint = firstSegment.get(firstSegment.size() - 1);
        
        double distanceToFirst = get2PointDistance(startPoint, firstPoint);
        double distanceToLast = get2PointDistance(startPoint, lastPoint);
        
        // 如果终点更近，反转所有航段
        if (distanceToLast < distanceToFirst) {
            for (int i = segments.size() - 1; i >= 0; i--) {
                List<Point2D> segment = new ArrayList<>(segments.get(i));
                Collections.reverse(segment);
                result.add(segment);
            }
        } else {
            result.addAll(segments);
        }
        
        // 在开头插入起点作为独立航段
//        result.add(0, Collections.singletonList(startPoint));
        
        return result;
    }
    
    /**
     * 计算最优航线方向（使航线数量最少）
     * 
     * @param polygonCoords 多边形边界坐标列表
     * @param fieldOfViewWidth 航线间距（米）
     * @return 最优航线方向角（度，0-360）
     */
    public static double calculateOptimalDirection(
            List<Gps> polygonCoords,
            double fieldOfViewWidth) {
        
        Gps refPoint = polygonCoords.get(0);
        
        // 将多边形转换为局部坐标
        List<Point2D> localPolygon = latLonListToMeters(polygonCoords, refPoint);
        
        double optimalAngle = 0;
        int minLineCount = Integer.MAX_VALUE;
        
        // 尝试0-180度，每隔5度采样一次
        for (int angle = 0; angle < 180; angle += 5) {
            double angleRad = Math.toRadians(90 - angle);
            List<Point2D> rotated = rotatePolygon(localPolygon, angleRad);
            
            // 计算该方向下的航线数量
            double minY = rotated.stream().mapToDouble(p -> p.y).min().orElse(0);
            double maxY = rotated.stream().mapToDouble(p -> p.y).max().orElse(0);
            int lineCount = (int) Math.ceil((maxY - minY) / fieldOfViewWidth) + 1;
            
            if (lineCount < minLineCount) {
                minLineCount = lineCount;
                optimalAngle = angle;
            }
        }
        
        return optimalAngle;
    }
    
    /**
     * 计算多边形中心点
     */
    private static Point2D calculatePolygonCenter(List<Point2D> polygon) {
        double sumX = 0, sumY = 0;
        for (Point2D p : polygon) {
            sumX += p.x;
            sumY += p.y;
        }
        return new Point2D(sumX / polygon.size(), sumY / polygon.size());
    }
    
    /**
     * 按合理顺序覆盖所有单元（基于端点匹配，支持航段反转）
     */
    private static List<List<Point2D>> coverCellsInOrder(List<Cell> cells, double spacing, Point2D startPoint) {
        List<List<Point2D>> allSegments = new ArrayList<>();
        
        // 步骤1：预先生成所有cell的航线
        for (Cell cell : cells) {
            List<Point2D> waypoints = generateScanLineWaypoints(cell.vertices, spacing);
            if (!waypoints.isEmpty()) {
                cell.setWaypoints(waypoints);
            }
        }
        
        List<Cell> cellsWithWaypoints = new ArrayList<>();
        for (Cell cell : cells) {
            if (cell.waypoints != null && !cell.waypoints.isEmpty()) {
                cellsWithWaypoints.add(cell);
            }
        }
        
        if (cellsWithWaypoints.isEmpty()) return allSegments;
        
        // 步骤2：从起飞点开始
        List<Cell> remaining = new ArrayList<>(cellsWithWaypoints);
        Point2D currentPoint = startPoint;

        // 步骤3：贪心选择最近的航段（支持反转）
        while (!remaining.isEmpty()) {
            Point2D lastPoint = currentPoint;

            Cell bestCell = null;
            double minDistance = Double.MAX_VALUE;
            boolean shouldReverse = false;
            
            for (Cell cell : remaining) {
                double distFirst = get2PointDistance(lastPoint, cell.firstPoint);
                double distLast = get2PointDistance(lastPoint, cell.lastPoint);
                
                if (distFirst < minDistance) {
                    minDistance = distFirst;
                    bestCell = cell;
                    shouldReverse = false;
                }
                
                if (distLast < minDistance) {
                    minDistance = distLast;
                    bestCell = cell;
                    shouldReverse = true;
                }
            }
            
            if (bestCell != null) {
                remaining.remove(bestCell);
                
                List<Point2D> segmentToAdd = new ArrayList<>(bestCell.waypoints);
                if (shouldReverse) {
                    Collections.reverse(segmentToAdd);
                }
                
                if (!segmentToAdd.isEmpty()) {
                    allSegments.add(segmentToAdd);
                    currentPoint = segmentToAdd.get(segmentToAdd.size() - 1);
                }
            }
        }
        
        return allSegments;
    }

    /**
     * 生成单个多边形区域的扫描线航点
     * 
     * @param polygon 多边形顶点列表
     * @param spacing 扫描线间距（米）
     * @return 扫描线航点列表
     */
    private static List<Point2D> generateScanLineWaypoints(List<Point2D> polygon, double spacing) {
        List<Point2D> waypoints = new ArrayList<>();

        // 计算边界框
        double minY = polygon.stream().mapToDouble(p -> p.y).min().orElse(0);
        double maxY = polygon.stream().mapToDouble(p -> p.y).max().orElse(0);

        //TODO 自主控制方向
        boolean leftToRight = true;

        for (double y = minY; y <= maxY; y += spacing) {
            List<Double> intersections = new ArrayList<>();

            for (int i = 0; i < polygon.size(); i++) {
                Point2D p1 = polygon.get(i);
                Point2D p2 = polygon.get((i + 1) % polygon.size());

                Double x = lineIntersectsSegment(y, p1, p2);
                if (x != null) {
                    intersections.add(x);
                }
            }

            Collections.sort(intersections);

            List<Double> uniqueIntersections = new ArrayList<>();
            for (int i = 0; i < intersections.size(); i++) {
                if (i == 0 || Math.abs(intersections.get(i) - intersections.get(i - 1)) > 0.01) {
                    uniqueIntersections.add(intersections.get(i));
                }
            }

            if (uniqueIntersections.size() >= 2) {
                List<Point2D> validSegmentPoints = new ArrayList<>();

                for (int i = 0; i < uniqueIntersections.size() - 1; i++) {
                    double x1 = uniqueIntersections.get(i);
                    double x2 = uniqueIntersections.get(i + 1);

                    Point2D midPoint = new Point2D((x1 + x2) / 2, y);
                    if (isPointInPolygon(midPoint, polygon)) {
                        validSegmentPoints.add(new Point2D(x1, y));
                        validSegmentPoints.add(new Point2D(x2, y));
                        i++;
                    }
                }

                if (!validSegmentPoints.isEmpty()) {
                    if (!leftToRight) {
                        Collections.reverse(validSegmentPoints);
                    }
                    waypoints.addAll(validSegmentPoints);
                    leftToRight = !leftToRight;
                }
            }
        }
        
        return waypoints;
    }
}
