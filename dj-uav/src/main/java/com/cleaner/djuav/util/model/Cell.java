package com.cleaner.djuav.util.model;

import java.util.ArrayList;
import java.util.List;

public class Cell {
    public List<Point2D> vertices;
    public Point2D center;
    public List<Point2D> waypoints;
    public Point2D firstPoint;
    public Point2D lastPoint;

    public Cell(List<Point2D> vertices) {
        this.vertices = vertices;
        this.center = calculateCenter(vertices);
    }

    public void setWaypoints(List<Point2D> waypoints) {
        this.waypoints = new ArrayList<>(waypoints);
        if (!waypoints.isEmpty()) {
            this.firstPoint = waypoints.get(0);
            this.lastPoint = waypoints.get(waypoints.size() - 1);
        }
    }

    private Point2D calculateCenter(List<Point2D> vertices) {
        double sumX = 0, sumY = 0;
        for (Point2D p : vertices) {
            sumX += p.x;
            sumY += p.y;
        }
        return new Point2D(sumX / vertices.size(), sumY / vertices.size());
    }
}
