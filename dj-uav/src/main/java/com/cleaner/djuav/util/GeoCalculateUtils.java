package com.cleaner.djuav.util;


import com.cleaner.djuav.enums.WayPointTypeEnum;
import com.cleaner.djuav.util.model.Gps;
import com.cleaner.djuav.util.model.Point2D;
import com.cleaner.djuav.util.model.WayPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GeoCalculateUtils {

    public static final double EARTH_RADIUS = 6378137.0; // 地球半径（米）

    /**
     * WGS84坐标转换为局部平面坐标（米）
     * 使用等距圆柱投影（Equirectangular Projection）
     */
    public static Point2D latLonToMeters(double lat, double lon, double refLat, double refLon) {
        double latRad = Math.toRadians(refLat);
        double x = Math.toRadians(lon - refLon) * EARTH_RADIUS * Math.cos(latRad);
        double y = Math.toRadians(lat - refLat) * EARTH_RADIUS;
        return new Point2D(x, y);
    }

    public static Point2D latLonToMeters(Gps gps, Gps refGps) {
        return latLonToMeters(gps.getWgLat(), gps.getWgLon(), refGps.getWgLat(), refGps.getWgLon());
    }

    public static List<Point2D> latLonListToMeters(List<Gps> gpsList, Gps refGps) {
        List<Point2D> point2DList = new ArrayList<>();
        for (Gps coord : gpsList) {
            Point2D p = latLonToMeters(coord, refGps);
            point2DList.add(p);
        }
        return point2DList;
    }

    /**
     * 局部平面坐标（米）转换为WGS84坐标
     */
    public static Gps metersToLatLon(double x, double y, double refLat, double refLon) {
        double latRad = Math.toRadians(refLat);
        double lon = refLon + Math.toDegrees(x / (EARTH_RADIUS * Math.cos(latRad)));
        double lat = refLat + Math.toDegrees(y / EARTH_RADIUS);

        Gps coord = new Gps();
        coord.setWgLon(lon);
        coord.setWgLat(lat);
        return coord;
    }

    public static WayPoint metersToWayPoint(Point2D point2D, Gps refGps) {
        Gps gps = metersToLatLon(point2D.x, point2D.y, refGps.getWgLat(), refGps.getWgLon());
        WayPoint wayPoint = new WayPoint(0, WayPointTypeEnum.NORMAL);
        wayPoint.setWgLat(gps.getWgLat());
        wayPoint.setWgLon(gps.getWgLon());
        return wayPoint;
    }

    /**
     * 根据飞行高度和相机视场角计算地面视野宽度
     *
     * @param height 飞行高度（米）
     * @param fovDegrees 相机视场角（度），默认73.7度（DJI Mavic 3E）
     * @return 地面视野宽度（米）
     */
    private static double calculateFieldOfViewWidth(double height, double fovDegrees) {
        if (height <= 0) {
            throw new IllegalArgumentException("飞行高度必须大于0");
        }
        if (fovDegrees <= 0 || fovDegrees >= 180) {
            throw new IllegalArgumentException("视场角必须在0到180度之间");
        }

        double fovRadians = Math.toRadians(fovDegrees);
        double fieldOfViewWidth = 2 * height * Math.tan(fovRadians / 2);

        return fieldOfViewWidth;
    }

    /**
     * 根据飞行高度、视场角和旁向重叠率计算实际航线间距
     *
     * @param height 飞行高度（米）
     * @param fovDegrees 相机视场角（度）
     * @param overlapPercent 旁向重叠率（0-100，例如70表示70%重叠）
     * @return 航线间距（米）
     */
    public static double calculateFieldOfViewWidth(double height, double fovDegrees, double overlapPercent) {
        if (overlapPercent < 0 || overlapPercent > 100) {
            throw new IllegalArgumentException("重叠率必须在0到100之间");
        }

        double fullWidth = calculateFieldOfViewWidth(height, fovDegrees);
        return fullWidth * (1 - overlapPercent / 100.0);
    }

    /**
     * Haversine公式计算两点间距离（米）
     */
    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    public static double getTotalDistance(List<WayPoint> waypoints) {
        double totalDistance = 0;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            totalDistance += haversineDistance(
                    waypoints.get(i).getWgLat(), waypoints.get(i).getWgLon(),
                    waypoints.get(i + 1).getWgLat(), waypoints.get(i + 1).getWgLon()
            );
        }
        return totalDistance;
    }
    
    /**
     * 判断点是否在多边形内部（射线法 Ray Casting Algorithm）
     */
    public static boolean isPointInPolygon(Point2D point, List<Point2D> polygon) {
        int intersections = 0;
        int n = polygon.size();

        for (int i = 0; i < n; i++) {
            Point2D p1 = polygon.get(i);
            Point2D p2 = polygon.get((i + 1) % n);

            if ((p1.y > point.y) != (p2.y > point.y)) {
                double xIntersection = (p2.x - p1.x) * (point.y - p1.y) / (p2.y - p1.y) + p1.x;
                if (point.x < xIntersection) {
                    intersections++;
                }
            }
        }

        return (intersections % 2) == 1;
    }
    
    /**
     * 判断是否为凹多边形
     */
    public static boolean isConcavePolygon(List<Point2D> polygon) {
        int n = polygon.size();
        if (n < 3) return false;
        
        boolean hasPositive = false;
        boolean hasNegative = false;
        
        for (int i = 0; i < n; i++) {
            Point2D p1 = polygon.get(i);
            Point2D p2 = polygon.get((i + 1) % n);
            Point2D p3 = polygon.get((i + 2) % n);
            
            double cross = (p2.x - p1.x) * (p3.y - p2.y) - (p2.y - p1.y) * (p3.x - p2.x);
            
            if (cross > 1e-10) hasPositive = true;
            if (cross < -1e-10) hasNegative = true;
            
            if (hasPositive && hasNegative) return true;
        }
        
        return false;
    }

    /**
     * 旋转多边形
     */
    public static List<Point2D> rotatePolygon(List<Point2D> polygon, double angleRadians) {
        List<Point2D> rotated = new ArrayList<>();
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);

        for (Point2D p : polygon) {
            double newX = p.x * cos - p.y * sin;
            double newY = p.x * sin + p.y * cos;
            rotated.add(new Point2D(newX, newY));
        }

        return rotated;
    }

    /**
     * 旋转航点列表
     */
    public static List<Point2D> rotatePoints(List<Point2D> points, double angleRadians) {
        return rotatePolygon(points, angleRadians);
    }

    public static double get2PointDistance(Point2D pointA, Point2D pointB) {
        return Math.sqrt(Math.pow(pointA.x - pointB.x, 2) + Math.pow(pointA.y - pointB.y, 2));
    }

    public static Gps PointToGps(Point2D point, double angleRadians, Gps refGps) {
        List<Point2D> points = rotatePoints(Arrays.asList(point), -angleRadians);
        return metersToLatLon(points.get(0).x, points.get(0).y, refGps.getWgLat(), refGps.getWgLon());
    }

    public static Point2D GpsToPoint(Gps gps, double angleRadians, Gps refGps) {
        Point2D meterPoint = latLonToMeters(gps, refGps);
        List<Point2D> points = rotatePoints(Arrays.asList(meterPoint), angleRadians);
        return points.get(0);
    }
}
