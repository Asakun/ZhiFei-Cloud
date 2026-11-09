package com.dji.sample.media.util;

import com.dji.sample.media.model.GeoCoordinate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PixelToGeoConverter {

    private static final double EARTH_RADIUS_METERS = 6378137.0;
    private static final double EPSILON = 1.0e-12;

    /**
     * Convert normalized coordinates to geographic coordinates using DJI camera EXIF data
     */
    public GeoCoordinate convertNormalizedToGeo(ExifData exifData, double normX, double normY, boolean useLrfCorrection) {
        if (exifData == null) {
            throw new IllegalArgumentException("EXIF data cannot be null");
        }

        double cameraLat = exifData.getCameraLat();
        double cameraLon = exifData.getCameraLon();
        double h = exifData.getRelativeAltitude();
        double hfov = exifData.getFov();
        double pitchDeg = -exifData.getGimbalPitch();
        double yawDeg = exifData.getGimbalYaw();
        double rollDeg = exifData.getGimbalRoll();
        int imageWidth = exifData.getImageWidth();
        int imageHeight = exifData.getImageHeight();

        if (useLrfCorrection && exifData.getLrfTargetDistance() != null) {
            double measuredDistance = exifData.getLrfTargetDistance();
            if (!Double.isFinite(measuredDistance) || measuredDistance <= 0.0) {
                throw new IllegalArgumentException("LRF distance must be positive");
            }
            h = measuredDistance * Math.sin(Math.toRadians(pitchDeg));
        }

        if (!Double.isFinite(h) || h <= 0.0) {
            throw new IllegalArgumentException("Camera height must be positive");
        }
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        if (!Double.isFinite(hfov) || hfov <= 0.0 || hfov >= 180.0) {
            throw new IllegalArgumentException("Horizontal FOV must be between 0 and 180 degrees");
        }
        if (!Double.isFinite(normX) || !Double.isFinite(normY)
                || normX < 0.0 || normX > 1.0 || normY < 0.0 || normY > 1.0) {
            throw new IllegalArgumentException("Normalized coordinates must be within [0, 1]");
        }

        double aspectRatio = (double) imageWidth / imageHeight;
        double vfov = 2.0 * Math.toDegrees(Math.atan(
                Math.tan(Math.toRadians(hfov / 2.0)) / aspectRatio));

        double u = 2.0 * normX - 1.0;
        double v = 2.0 * normY - 1.0;
        double halfHorizontalFov = Math.toRadians(hfov / 2.0);
        double halfVerticalFov = Math.toRadians(vfov / 2.0);

        log.debug("screen_point_to_geo parameters: lat0={}, lon0={}, height={}, yawDeg={}, "
                + "pitchDeg={}, rollDeg={}, hfovDeg={}, vfovDeg={}, u={}, v={}, "
                + "normX={}, normY={}, useLrfCorrection={}",
            cameraLat, cameraLon, h, yawDeg, pitchDeg, rollDeg,
            hfov, vfov, u, v, normX, normY, useLrfCorrection);

        double[] cameraRay = {Math.tan(u * halfHorizontalFov),
                Math.tan(v * halfVerticalFov), 1.0};

        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double roll = Math.toRadians(rollDeg);

        double[] forward = normalize(new double[]{
                Math.cos(pitch) * Math.sin(yaw),
                Math.cos(pitch) * Math.cos(yaw),
                -Math.sin(pitch)
        });
        double[] right = cross(forward, new double[]{0.0, 0.0, 1.0});
        if (norm(right) < EPSILON) {
            right = new double[]{1.0, 0.0, 0.0};
        } else {
            right = normalize(right);
        }
        double[] down = normalize(cross(forward, right));

        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);
        double[] rolledRight = new double[3];
        double[] rolledDown = new double[3];
        for (int i = 0; i < 3; i++) {
            rolledRight[i] = cosRoll * right[i] + sinRoll * down[i];
            rolledDown[i] = -sinRoll * right[i] + cosRoll * down[i];
        }

        double[] ray = new double[3];
        for (int i = 0; i < 3; i++) {
            ray[i] = cameraRay[0] * rolledRight[i]
                    + cameraRay[1] * rolledDown[i]
                    + cameraRay[2] * forward[i];
        }
        ray = normalize(ray);

        if (ray[2] >= -EPSILON) {
            throw new IllegalArgumentException("Ray does not intersect the ground plane");
        }

        double distanceToGround = -h / ray[2];
        double east = ray[0] * distanceToGround;
        double north = ray[1] * distanceToGround;

        double latitudeRadians = Math.toRadians(cameraLat);
        double targetLat = cameraLat + Math.toDegrees(north / EARTH_RADIUS_METERS);
        double targetLon = cameraLon + Math.toDegrees(
                east / (EARTH_RADIUS_METERS * Math.cos(latitudeRadians)));

        return GeoCoordinate.builder()
                .latitude(targetLat)
                .longitude(targetLon)
                .build();
    }

    private double[] cross(double[] left, double[] right) {
        return new double[]{
                left[1] * right[2] - left[2] * right[1],
                left[2] * right[0] - left[0] * right[2],
                left[0] * right[1] - left[1] * right[0]
        };
    }

    private double norm(double[] vector) {
        return Math.sqrt(vector[0] * vector[0]
                + vector[1] * vector[1]
                + vector[2] * vector[2]);
    }

    private double[] normalize(double[] vector) {
        double length = norm(vector);
        if (!Double.isFinite(length) || length < EPSILON) {
            throw new IllegalArgumentException("Cannot normalize zero-length vector");
        }
        return new double[]{vector[0] / length, vector[1] / length, vector[2] / length};
    }
}
