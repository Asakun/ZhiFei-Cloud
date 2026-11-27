package com.cleaner.djuav.util;

import com.cleaner.djuav.util.model.Cell;
import com.cleaner.djuav.util.model.Point2D;

import java.util.ArrayList;
import java.util.List;

import static com.cleaner.djuav.util.GeoCalculateUtils.*;
import static com.cleaner.djuav.util.GeoCalculateUtils.isConcavePolygon;

public class PolygonUtils {

    /**
     * 将凹多边形分解为最少数量的凸多边形
     * 使用贪心策略：每次找最优的对角线分割凹顶点
     *
     * @param polygon 多边形顶点列表（已旋转，使飞行方向沿X轴）
     * @return 凸多边形单元列表
     */
    public static List<Cell> decomposeConcavePolygon(List<Point2D> polygon) {
        List<Cell> cells = new ArrayList<>();
        decomposeToConvexRecursive(new ArrayList<>(polygon), cells, 0);

        // 后处理：尝试合并相邻的凸多边形以减少总数
        cells = mergeAdjacentConvexCells(cells);

        return cells;
    }

    /**
     * 递归分解凹多边形为凸多边形
     */
    private static void decomposeToConvexRecursive(List<Point2D> polygon, List<Cell> cells, int depth) {
        if (depth > 20 || polygon.size() < 3) {
            return;
        }

        if (!isConcavePolygon(polygon)) {
            cells.add(new Cell(polygon));
            return;
        }

        int n = polygon.size();

        // 找到所有凹顶点（内角 > 180°）
        for (int i = 0; i < n; i++) {
            Point2D prev = polygon.get((i - 1 + n) % n);
            Point2D curr = polygon.get(i);
            Point2D next = polygon.get((i + 1) % n);

            double cross = crossProduct(prev, curr, next);

            if (cross < -1e-10) {
                // 找到最佳分割点：优先选择能创建最大凸多边形的对角线
                int bestTarget = findBestDiagonal(polygon, i);

                if (bestTarget != -1) {
                    List<Point2D> poly1 = new ArrayList<>();
                    List<Point2D> poly2 = new ArrayList<>();

                    // poly1: i -> ... -> bestTarget
                    for (int j = i; j != bestTarget; j = (j + 1) % n) {
                        poly1.add(polygon.get(j));
                    }
                    poly1.add(polygon.get(bestTarget));

                    // poly2: bestTarget -> ... -> i
                    for (int j = bestTarget; j != i; j = (j + 1) % n) {
                        poly2.add(polygon.get(j));
                    }
                    poly2.add(polygon.get(i));

                    if (poly1.size() >= 3 && poly2.size() >= 3) {
                        decomposeToConvexRecursive(poly1, cells, depth + 1);
                        decomposeToConvexRecursive(poly2, cells, depth + 1);
                        return;
                    }
                }
            }
        }

        // 找不到有效分割，作为单个cell处理
        cells.add(new Cell(polygon));
    }

    /**
     * 为凹顶点找到最佳分割对角线
     * 优先选择：1) 对角线在多边形内部 2) 创建的子多边形尽可能大且接近凸
     */
    private static int findBestDiagonal(List<Point2D> polygon, int concaveIdx) {
        int n = polygon.size();
        Point2D concaveVertex = polygon.get(concaveIdx);

        int bestTarget = -1;
        double bestScore = -1;

        for (int target = 0; target < n; target++) {
            if (target == concaveIdx ||
                    target == (concaveIdx - 1 + n) % n ||
                    target == (concaveIdx + 1) % n) {
                continue;
            }

            Point2D targetVertex = polygon.get(target);

            // 检查对角线是否完全在多边形内部
            if (!isDiagonalInside(polygon, concaveIdx, target)) {
                continue;
            }

            // 评分：优先选择距离适中、能创建较大子多边形的对角线
            double distance = get2PointDistance(targetVertex, concaveVertex);

            int segmentCount = Math.min(
                    (target - concaveIdx + n) % n,
                    (concaveIdx - target + n) % n
            );

            double score = distance * segmentCount;

            if (score > bestScore) {
                bestScore = score;
                bestTarget = target;
            }
        }

        return bestTarget;
    }

    /**
     * 检查对角线是否在多边形内部
     */
    private static boolean isDiagonalInside(List<Point2D> polygon, int idx1, int idx2) {
        Point2D p1 = polygon.get(idx1);
        Point2D p2 = polygon.get(idx2);

        // 检查对角线中点是否在多边形内
        Point2D midPoint = new Point2D((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
        if (!isPointInPolygon(midPoint, polygon)) {
            return false;
        }

        // 检查对角线是否与其他边相交
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            if (i == idx1 || i == idx2 ||
                    (i + 1) % n == idx1 || (i + 1) % n == idx2) {
                continue;
            }

            Point2D edgeStart = polygon.get(i);
            Point2D edgeEnd = polygon.get((i + 1) % n);

            if (segmentsIntersect(p1, p2, edgeStart, edgeEnd)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 计算向量叉积，判断转向
     */
    private static double crossProduct(Point2D p1, Point2D p2, Point2D p3) {
        return (p2.x - p1.x) * (p3.y - p2.y) - (p2.y - p1.y) * (p3.x - p2.x);
    }

    /**
     * 检查两条线段是否相交
     */
    private static boolean segmentsIntersect(Point2D p1, Point2D p2, Point2D p3, Point2D p4) {
        double d1 = crossProduct(p3, p4, p1);
        double d2 = crossProduct(p3, p4, p2);
        double d3 = crossProduct(p1, p2, p3);
        double d4 = crossProduct(p1, p2, p4);

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }

        return false;
    }

    /**
     * 合并相邻的凸多边形单元
     * 贪心策略：重复尝试合并任意两个共享边的单元，直到无法继续合并
     */
    private static List<Cell> mergeAdjacentConvexCells(List<Cell> cells) {
        if (cells.size() <= 1) return cells;

        boolean merged;
        List<Cell> result = new ArrayList<>(cells);

        do {
            merged = false;

            for (int i = 0; i < result.size() && !merged; i++) {
                for (int j = i + 1; j < result.size() && !merged; j++) {
                    Cell mergedCell = tryMergeCells(result.get(i), result.get(j));

                    if (mergedCell != null && !isConcavePolygon(mergedCell.vertices)) {
                        // 合并成功且结果仍是凸多边形
                        result.remove(j);
                        result.remove(i);
                        result.add(mergedCell);
                        merged = true;
                    }
                }
            }
        } while (merged);

        return result;
    }

    /**
     * 尝试合并两个单元，如果它们共享一条边
     */
    private static Cell tryMergeCells(Cell cell1, Cell cell2) {
        List<Point2D> v1 = cell1.vertices;
        List<Point2D> v2 = cell2.vertices;

        // 查找共享边
        for (int i = 0; i < v1.size(); i++) {
            Point2D p1 = v1.get(i);
            Point2D p2 = v1.get((i + 1) % v1.size());

            for (int j = 0; j < v2.size(); j++) {
                Point2D q1 = v2.get(j);
                Point2D q2 = v2.get((j + 1) % v2.size());

                // 检查是否是共享边（方向相反）
                if (pointsEqual(p1, q2) && pointsEqual(p2, q1)) {
                    // 找到共享边，构造合并后的多边形
                    List<Point2D> merged = new ArrayList<>();

                    // 从 cell1 的下一个顶点开始，跳过共享边
                    for (int k = 0; k < v1.size() - 1; k++) {
                        merged.add(v1.get((i + 2 + k) % v1.size()));
                    }

                    // 添加 cell2 的顶点，跳过共享边
                    for (int k = 0; k < v2.size() - 1; k++) {
                        merged.add(v2.get((j + 2 + k) % v2.size()));
                    }

                    if (merged.size() >= 3) {
                        return new Cell(merged);
                    }
                }
            }
        }

        return null;
    }

    /**
     * 判断两个点是否相等（容忍微小误差）
     */
    private static boolean pointsEqual(Point2D p1, Point2D p2) {
        double epsilon = 1e-6;
        return Math.abs(p1.x - p2.x) < epsilon && Math.abs(p1.y - p2.y) < epsilon;
    }
}
