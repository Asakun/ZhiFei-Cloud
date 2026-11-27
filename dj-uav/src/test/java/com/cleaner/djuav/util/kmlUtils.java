package com.cleaner.djuav.util;

import cn.hutool.core.io.FileUtil;
import com.cleaner.djuav.domain.kml.KmlInfo;
import com.cleaner.djuav.domain.kml.KmlPolygon;
import com.cleaner.djuav.util.model.Cell;
import com.cleaner.djuav.util.model.Gps;
import com.cleaner.djuav.util.model.Point2D;
import com.cleaner.djuav.util.model.WayPoint;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.cleaner.djuav.util.GeoCalculateUtils.metersToLatLon;

public class kmlUtils {

    /**
     * 解析KML坐标字符串
     * 格式: "lon1,lat1,height1 lon2,lat2,height2 ..."
     */
    public static List<Gps> parseCoordinates(String coordinatesStr) {
        if (coordinatesStr == null || coordinatesStr.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String[] points = coordinatesStr.trim().split("\\s+");
        List<Gps> result = new ArrayList<>();

        for (String point : points) {
            String[] parts = point.split(",");
            if (parts.length >= 2) {
                Gps coord = new Gps();
                coord.setWgLon(Double.parseDouble(parts[0]));
                coord.setWgLat(Double.parseDouble(parts[1]));
                result.add(coord);
            }
        }

        return result;
    }

    /**
     * 从template.kml文件中读取多边形坐标
     *
     * @param kmlFilePath KML文件路径
     * @return 多边形坐标列表
     */
    public static List<Gps> readPolygonFromKml(String kmlFilePath) {
        try {
            File file = new File(kmlFilePath);
            InputStream inputStream = FileUtil.getInputStream(file);

            XStream xStream = new XStream(new DomDriver());
            xStream.processAnnotations(KmlInfo.class);
            xStream.ignoreUnknownElements();
            xStream.allowTypesByWildcard(new String[] {"com.cleaner.djuav.**"});

            KmlInfo kmlInfo = (KmlInfo) xStream.fromXML(inputStream);

            // 获取第一个Placemark中的Polygon
            if (kmlInfo.getDocument() != null &&
                    kmlInfo.getDocument().getFolder() != null &&
                    kmlInfo.getDocument().getFolder().getPlacemarkList() != null &&
                    !kmlInfo.getDocument().getFolder().getPlacemarkList().isEmpty()) {

                KmlPolygon polygon = kmlInfo.getDocument().getFolder().getPlacemarkList().get(0).getPolygon();

                if (polygon != null &&
                        polygon.getOuterBoundaryIs() != null &&
                        polygon.getOuterBoundaryIs().getLinearRing() != null) {

                    String coordinates = polygon.getOuterBoundaryIs().getLinearRing().getCoordinates();
                    return parseCoordinates(coordinates);
                }
            }

            throw new IllegalArgumentException("KML文件中未找到多边形坐标");

        } catch (Exception e) {
            throw new RuntimeException("读取KML文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从template.kml文件中读取起飞参考点
     *
     * @param kmlFilePath KML文件路径
     * @return 起飞参考点坐标，格式错误或不存在时返回null
     */
    public static WayPoint readTakeOffPointFromKml(String kmlFilePath) {
        try {
            File file = new File(kmlFilePath);
            InputStream inputStream = FileUtil.getInputStream(file);

            XStream xStream = new XStream(new DomDriver());
            xStream.processAnnotations(KmlInfo.class);
            xStream.ignoreUnknownElements();
            xStream.allowTypesByWildcard(new String[] {"com.cleaner.djuav.**"});

            KmlInfo kmlInfo = (KmlInfo) xStream.fromXML(inputStream);

            if (kmlInfo.getDocument() != null &&
                    kmlInfo.getDocument().getKmlMissionConfig() != null) {

                String takeOffRefPoint = kmlInfo.getDocument().getKmlMissionConfig().getTakeOffRefPoint();

                if (takeOffRefPoint != null && !takeOffRefPoint.trim().isEmpty()) {
                    // 格式: "lat,lon,height"
                    String[] parts = takeOffRefPoint.split(",");
                    if (parts.length >= 2) {
                        WayPoint coord = new WayPoint();
                        coord.setWgLat(Double.parseDouble(parts[0].trim()));
                        coord.setWgLon(Double.parseDouble(parts[1].trim()));
                        coord.setHeight(Double.parseDouble(parts[2].trim()));
                        return coord;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            System.err.println("读取起飞参考点失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从template.kml文件中读取航线方向角
     *
     * @param kmlFilePath KML文件路径
     * @return 航线方向角（度，0-360，0=正北，顺时针），读取失败返回null
     */
    public static Double readDirectionFromKml(String kmlFilePath) {
        try {
            File file = new File(kmlFilePath);
            InputStream inputStream = FileUtil.getInputStream(file);

            XStream xStream = new XStream(new DomDriver());
            xStream.processAnnotations(KmlInfo.class);
            xStream.ignoreUnknownElements();
            xStream.allowTypesByWildcard(new String[] {"com.cleaner.djuav.**"});

            KmlInfo kmlInfo = (KmlInfo) xStream.fromXML(inputStream);

            if (kmlInfo.getDocument() != null &&
                    kmlInfo.getDocument().getFolder() != null &&
                    kmlInfo.getDocument().getFolder().getPlacemarkList() != null &&
                    !kmlInfo.getDocument().getFolder().getPlacemarkList().isEmpty()) {

                String directionStr = kmlInfo.getDocument().getFolder().getPlacemarkList().get(0).getDirection();

                if (directionStr != null && !directionStr.trim().isEmpty()) {
                    return Double.parseDouble(directionStr.trim());
                }
            }

            return null;

        } catch (Exception e) {
            System.err.println("读取航线方向角失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从template.kml文件中读取重叠率参数
     *
     * @param kmlFilePath KML文件路径
     * @return 数组[旁向重叠率, 航向重叠率]，读取失败返回null
     */
    public static double[] readOverlapFromKml(String kmlFilePath) {
        try {
            File file = new File(kmlFilePath);
            InputStream inputStream = FileUtil.getInputStream(file);

            XStream xStream = new XStream(new DomDriver());
            xStream.processAnnotations(KmlInfo.class);
            xStream.ignoreUnknownElements();
            xStream.allowTypesByWildcard(new String[] {"com.cleaner.djuav.**"});

            KmlInfo kmlInfo = (KmlInfo) xStream.fromXML(inputStream);

            if (kmlInfo.getDocument() != null &&
                    kmlInfo.getDocument().getFolder() != null &&
                    kmlInfo.getDocument().getFolder().getPlacemarkList() != null &&
                    !kmlInfo.getDocument().getFolder().getPlacemarkList().isEmpty()) {

                var overlap = kmlInfo.getDocument().getFolder().getPlacemarkList().get(0).getOverlap();

                if (overlap != null) {
                    String overlapW = overlap.getOrthoCameraOverlapW();
                    String overlapH = overlap.getOrthoCameraOverlapH();

                    if (overlapW != null && overlapH != null &&
                            !overlapW.trim().isEmpty() && !overlapH.trim().isEmpty()) {
                        return new double[] {
                                Double.parseDouble(overlapW.trim()),
                                Double.parseDouble(overlapH.trim())
                        };
                    }
                }
            }

            return null;

        } catch (Exception e) {
            System.err.println("读取重叠率失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从template.kml文件中读取飞行高度
     *
     * @param kmlFilePath KML文件路径
     * @return 飞行高度（米），读取失败返回null
     */
    public static Double readHeightFromKml(String kmlFilePath) {
        try {
            File file = new File(kmlFilePath);
            InputStream inputStream = FileUtil.getInputStream(file);

            XStream xStream = new XStream(new DomDriver());
            xStream.processAnnotations(KmlInfo.class);
            xStream.ignoreUnknownElements();
            xStream.allowTypesByWildcard(new String[] {"com.cleaner.djuav.**"});

            KmlInfo kmlInfo = (KmlInfo) xStream.fromXML(inputStream);

            if (kmlInfo.getDocument() != null &&
                    kmlInfo.getDocument().getFolder() != null &&
                    kmlInfo.getDocument().getFolder().getPlacemarkList() != null &&
                    !kmlInfo.getDocument().getFolder().getPlacemarkList().isEmpty()) {

                String heightStr = kmlInfo.getDocument().getFolder().getPlacemarkList().get(0).getHeight();

                if (heightStr != null && !heightStr.trim().isEmpty()) {
                    return Double.parseDouble(heightStr.trim());
                }
            }

            return null;

        } catch (Exception e) {
            System.err.println("读取飞行高度失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 保存航点列表为GeoJSON文件（用于地图可视化）
     *
     * @param waypoints 航点列表
     * @param filePath 输出文件路径
     * @return 保存的文件路径
     */
    public static String saveWaypointsToGeoJson(List<Gps> waypoints, String filePath) {
        return saveWaypointsToGeoJson(waypoints, null, filePath);
    }

    /**
     * 保存航点列表和多边形边界为GeoJSON文件（用于地图可视化）
     *
     * @param waypoints 航点列表
     * @param polygon 输入的多边形边界（可选）
     * @param filePath 输出文件路径
     * @return 保存的文件路径
     */
    public static String saveWaypointsToGeoJson(List<Gps> waypoints,
                                                List<Gps> polygon,
                                                String filePath) {
        try {
            StringBuilder geoJson = new StringBuilder();
            geoJson.append("{\n");
            geoJson.append("  \"type\": \"FeatureCollection\",\n");
            geoJson.append("  \"features\": [\n");

            // 添加多边形边界（如果提供）
            if (polygon != null && !polygon.isEmpty()) {
                geoJson.append("    {\n");
                geoJson.append("      \"type\": \"Feature\",\n");
                geoJson.append("      \"properties\": {\n");
                geoJson.append("        \"name\": \"Survey Area\",\n");
                geoJson.append("        \"stroke\": \"#FF0000\",\n");
                geoJson.append("        \"stroke-width\": 2,\n");
                geoJson.append("        \"fill\": \"#FF0000\",\n");
                geoJson.append("        \"fill-opacity\": 0.1\n");
                geoJson.append("      },\n");
                geoJson.append("      \"geometry\": {\n");
                geoJson.append("        \"type\": \"Polygon\",\n");
                geoJson.append("        \"coordinates\": [[\n");

                for (int i = 0; i < polygon.size(); i++) {
                    Gps p = polygon.get(i);
                    geoJson.append("          [")
                            .append(p.getWgLon()).append(", ")
                            .append(p.getWgLat())
                            .append("],\n");
                }

                // 闭合多边形
                Gps first = polygon.get(0);
                geoJson.append("          [")
                        .append(first.getWgLon()).append(", ")
                        .append(first.getWgLat())
                        .append("]\n");

                geoJson.append("        ]]\n");
                geoJson.append("      }\n");
                geoJson.append("    },\n");
            }

            // 添加覆盖航线
            geoJson.append("    {\n");
            geoJson.append("      \"type\": \"Feature\",\n");
            geoJson.append("      \"properties\": {\n");
            geoJson.append("        \"name\": \"Coverage Path\",\n");
            geoJson.append("        \"waypointCount\": " + waypoints.size() + ",\n");
            geoJson.append("        \"stroke\": \"#0000FF\",\n");
            geoJson.append("        \"stroke-width\": 2\n");
            geoJson.append("      },\n");
            geoJson.append("      \"geometry\": {\n");
            geoJson.append("        \"type\": \"LineString\",\n");
            geoJson.append("        \"coordinates\": [\n");

            for (int i = 0; i < waypoints.size(); i++) {
                Gps wp = waypoints.get(i);
                geoJson.append("          [")
                        .append(wp.getWgLon()).append(", ")
                        .append(wp.getWgLat())
                        .append("]");
                if (i < waypoints.size() - 1) {
                    geoJson.append(",");
                }
                geoJson.append("\n");
            }

            geoJson.append("        ]\n");
            geoJson.append("      }\n");
            geoJson.append("    }\n");
            geoJson.append("  ]\n");
            geoJson.append("}\n");

            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(geoJson.toString());
            }

            System.out.println("GeoJSON已保存到: " + filePath);
            System.out.println("可在 https://geojson.io 中查看航线可视化");
            return filePath;

        } catch (Exception e) {
            throw new RuntimeException("保存GeoJSON文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存分解的单元为GeoJSON文件（用于地图可视化）
     *
     * @param cells 单元列表
     * @param ref 参考点坐标（用于坐标转换）
     * @param filePath 输出文件路径
     * @return 保存的文件路径
     */
    public static String saveWaypointsToGeoJson(List<Cell> cells, Gps ref, String filePath) {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("{\n");
            writer.write("  \"type\": \"FeatureCollection\",\n");
            writer.write("  \"features\": [\n");

            for (int i = 0; i < cells.size(); i++) {
                Cell cell = cells.get(i);
                writer.write("    {\n");
                writer.write("      \"type\": \"Feature\",\n");
                writer.write("      \"properties\": {\n");
                writer.write("        \"cellId\": " + (i + 1) + ",\n");
                writer.write("        \"vertexCount\": " + cell.vertices.size() + ",\n");
                writer.write(String.format("        \"centerX\": %.2f,\n", cell.center.x));
                writer.write(String.format("        \"centerY\": %.2f,\n", cell.center.y));
                writer.write("        \"stroke\": \"#FF0000\",\n");
                writer.write("        \"stroke-width\": 2,\n");
                writer.write("        \"fill\": \"#00FF00\",\n");
                writer.write("        \"fill-opacity\": 0.2\n");
                writer.write("      },\n");
                writer.write("      \"geometry\": {\n");
                writer.write("        \"type\": \"Polygon\",\n");
                writer.write("        \"coordinates\": [[\n");

                for (int j = 0; j < cell.vertices.size(); j++) {
                    Point2D p = cell.vertices.get(j);
                    Gps latLon = metersToLatLon(p.x, p.y, ref.getWgLat(), ref.getWgLon());
                    writer.write(String.format("          [%.8f, %.8f]",
                            latLon.getWgLon(), latLon.getWgLat()));
                    if (j < cell.vertices.size() - 1) {
                        writer.write(",");
                    }
                    writer.write("\n");
                }

                Point2D firstPoint = cell.vertices.get(0);
                Gps firstLatLon = metersToLatLon(firstPoint.x, firstPoint.y,
                        ref.getWgLat(), ref.getWgLon());
                writer.write(String.format("          [%.8f, %.8f]\n",
                        firstLatLon.getWgLon(), firstLatLon.getWgLat()));

                writer.write("        ]]\n");
                writer.write("      }\n");
                writer.write("    }");
                if (i < cells.size() - 1) {
                    writer.write(",");
                }
                writer.write("\n");
            }

            writer.write("  ]\n");
            writer.write("}\n");

            System.out.println("GeoJSON已保存到: " + filePath);
            System.out.println("可在 https://geojson.io 中查看单元可视化");
            return filePath;

        } catch (IOException e) {
            throw new RuntimeException("保存GeoJSON文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存航点、多边形和分解单元为GeoJSON文件（用于地图可视化）
     *
     * @param waypoints 航点列表
     * @param polygon 输入的多边形边界
     * @param cells 分解的单元列表
     * @param ref 参考点坐标（用于坐标转换）
     * @param filePath 输出文件路径
     * @return 保存的文件路径
     */
    public static String saveWaypointsToGeoJson(List<WayPoint> waypoints,
                                                List<Gps> polygon,
                                                List<Cell> cells,
                                                Gps ref,
                                                String filePath) {
        try {
            StringBuilder geoJson = new StringBuilder();
            geoJson.append("{\n");
            geoJson.append("  \"type\": \"FeatureCollection\",\n");
            geoJson.append("  \"features\": [\n");

            boolean needsComma = false;

            // 添加多边形边界
            if (polygon != null && !polygon.isEmpty()) {
                geoJson.append("    {\n");
                geoJson.append("      \"type\": \"Feature\",\n");
                geoJson.append("      \"properties\": {\n");
                geoJson.append("        \"name\": \"Survey Area\",\n");
                geoJson.append("        \"stroke\": \"#FF0000\",\n");
                geoJson.append("        \"stroke-width\": 2,\n");
                geoJson.append("        \"fill\": \"#FF0000\",\n");
                geoJson.append("        \"fill-opacity\": 0.1\n");
                geoJson.append("      },\n");
                geoJson.append("      \"geometry\": {\n");
                geoJson.append("        \"type\": \"Polygon\",\n");
                geoJson.append("        \"coordinates\": [[\n");

                for (int i = 0; i < polygon.size(); i++) {
                    Gps p = polygon.get(i);
                    geoJson.append("          [")
                            .append(p.getWgLon()).append(", ")
                            .append(p.getWgLat())
                            .append("],\n");
                }

                Gps first = polygon.get(0);
                geoJson.append("          [")
                        .append(first.getWgLon()).append(", ")
                        .append(first.getWgLat())
                        .append("]\n");

                geoJson.append("        ]]\n");
                geoJson.append("      }\n");
                geoJson.append("    }");
                needsComma = true;
            }

            // 添加分解单元
            if (cells != null && !cells.isEmpty() && ref != null) {
                for (int i = 0; i < cells.size(); i++) {
                    if (needsComma) {
                        geoJson.append(",\n");
                    }
                    Cell cell = cells.get(i);
                    geoJson.append("    {\n");
                    geoJson.append("      \"type\": \"Feature\",\n");
                    geoJson.append("      \"properties\": {\n");
                    geoJson.append("        \"name\": \"Cell " + (i + 1) + "\",\n");
                    geoJson.append("        \"cellId\": " + (i + 1) + ",\n");
                    geoJson.append("        \"vertexCount\": " + cell.vertices.size() + ",\n");
                    geoJson.append(String.format("        \"centerX\": %.2f,\n", cell.center.x));
                    geoJson.append(String.format("        \"centerY\": %.2f,\n", cell.center.y));
                    geoJson.append("        \"stroke\": \"#FFA500\",\n");
                    geoJson.append("        \"stroke-width\": 2,\n");
                    geoJson.append("        \"fill\": \"#00FF00\",\n");
                    geoJson.append("        \"fill-opacity\": 0.2\n");
                    geoJson.append("      },\n");
                    geoJson.append("      \"geometry\": {\n");
                    geoJson.append("        \"type\": \"Polygon\",\n");
                    geoJson.append("        \"coordinates\": [[\n");

                    for (int j = 0; j < cell.vertices.size(); j++) {
                        Point2D p = cell.vertices.get(j);
                        Gps latLon = metersToLatLon(p.x, p.y, ref.getWgLat(), ref.getWgLon());
                        geoJson.append(String.format("          [%.8f, %.8f],\n",
                                latLon.getWgLon(), latLon.getWgLat()));
                    }

                    Point2D firstPoint = cell.vertices.get(0);
                    Gps firstLatLon = metersToLatLon(firstPoint.x, firstPoint.y,
                            ref.getWgLat(), ref.getWgLon());
                    geoJson.append(String.format("          [%.8f, %.8f]\n",
                            firstLatLon.getWgLon(), firstLatLon.getWgLat()));

                    geoJson.append("        ]]\n");
                    geoJson.append("      }\n");
                    geoJson.append("    }");
                    needsComma = true;
                }
            }

            // 添加覆盖航线
            if (waypoints != null && !waypoints.isEmpty()) {
                if (needsComma) {
                    geoJson.append(",\n");
                }
                geoJson.append("    {\n");
                geoJson.append("      \"type\": \"Feature\",\n");
                geoJson.append("      \"properties\": {\n");
                geoJson.append("        \"name\": \"Coverage Path\",\n");
                geoJson.append("        \"waypointCount\": " + waypoints.size() + ",\n");
                geoJson.append("        \"stroke\": \"#0000FF\",\n");
                geoJson.append("        \"stroke-width\": 2\n");
                geoJson.append("      },\n");
                geoJson.append("      \"geometry\": {\n");
                geoJson.append("        \"type\": \"LineString\",\n");
                geoJson.append("        \"coordinates\": [\n");

                for (int i = 0; i < waypoints.size(); i++) {
                    Gps wp = waypoints.get(i);
                    geoJson.append("          [")
                            .append(wp.getWgLon()).append(", ")
                            .append(wp.getWgLat())
                            .append("]");
                    if (i < waypoints.size() - 1) {
                        geoJson.append(",");
                    }
                    geoJson.append("\n");
                }

                geoJson.append("        ]\n");
                geoJson.append("      }\n");
                geoJson.append("    }");
            }

            geoJson.append("\n  ]\n");
            geoJson.append("}\n");

            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(geoJson.toString());
            }

            System.out.println("GeoJSON已保存到: " + filePath);
            System.out.println("可在 https://geojson.io 中查看完整可视化");
            return filePath;

        } catch (Exception e) {
            throw new RuntimeException("保存GeoJSON文件失败: " + e.getMessage(), e);
        }
    }
}
