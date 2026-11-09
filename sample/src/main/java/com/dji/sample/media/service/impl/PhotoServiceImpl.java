package com.dji.sample.media.service.impl;

import com.dji.sample.component.oss.model.OssConfiguration;
import com.dji.sample.component.oss.service.impl.OssServiceContext;
import com.dji.sample.media.model.GeoCoordinate;
import com.dji.sample.media.model.PhotoProcessRequest;
import com.dji.sample.media.model.PhotoProcessResponse;
import com.dji.sample.media.service.IPhotoService;
import com.dji.sample.media.util.ExifData;
import com.dji.sample.media.util.PixelToGeoConverter;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@Transactional
public class PhotoServiceImpl implements IPhotoService {

    @Autowired
    private OssServiceContext ossServiceContext;

    @Autowired
    private PixelToGeoConverter pixelToGeoConverter;

    @Override
    public PhotoProcessResponse processPhotoCoordinates(String workspaceId, PhotoProcessRequest request) {
        try {
            ExifData exifData = extractExifData(request.getObjectKey(), request.getUseLrfCorrection());
            
            List<GeoCoordinate> coordinates = new ArrayList<>();
            for (List<Double> normalized : request.getNormalizedCoordinates()) {
                if (normalized == null || normalized.size() != 2) {
                    log.warn("Invalid normalized coordinate format: {}", normalized);
                    continue;
                }
                
                double normX = normalized.get(0);
                double normY = normalized.get(1);
                
                GeoCoordinate coord = pixelToGeoConverter.convertNormalizedToGeo(
                    exifData, normX, normY, request.getUseLrfCorrection()
                );
                coordinates.add(coord);
            }
            
            Double area = null;
            if (coordinates.size() >= 3) {
                area = calculatePolygonArea(coordinates);
            }
            
            return PhotoProcessResponse.builder()
                    .coordinates(coordinates)
                    .areaSquareMeters(area)
                    .status("success")
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to process photo coordinates for objectKey: {}", request.getObjectKey(), e);
            return PhotoProcessResponse.builder()
                    .status("error: " + e.getMessage())
                    .build();
        }
    }

    private ExifData extractExifData(String objectKey, boolean needLrfData) throws Exception {
        try (InputStream inputStream = ossServiceContext.getObject(OssConfiguration.bucket, objectKey)) {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
            
            GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDir == null) {
                throw new IllegalArgumentException("No GPS data found in image");
            }
            
            double cameraLat = parseGpsCoordinate(gpsDir.getDescription(GpsDirectory.TAG_LATITUDE), 
                                                  gpsDir.getDescription(GpsDirectory.TAG_LATITUDE_REF));
            double cameraLon = parseGpsCoordinate(gpsDir.getDescription(GpsDirectory.TAG_LONGITUDE), 
                                                  gpsDir.getDescription(GpsDirectory.TAG_LONGITUDE_REF));
            
            XmpDirectory xmpDir = metadata.getFirstDirectoryOfType(XmpDirectory.class);
            if (xmpDir == null) {
                throw new IllegalArgumentException("No XMP data found in image");
            }
            
            String xmpXml = xmpDir.getXMPMeta().dumpObject();
            
            double relativeAltitude = extractNumericValue(xmpXml, "RelativeAltitude");
            double gimbalPitch = extractNumericValue(xmpXml, "GimbalPitchDegree");
            double gimbalYaw = extractNumericValue(xmpXml, "GimbalYawDegree");
            double gimbalRoll;
            try {
                gimbalRoll = extractNumericValue(xmpXml, "GimbalRollDegree");
            } catch (Exception e) {
                log.debug("GimbalRollDegree not found, assuming 0.0");
                gimbalRoll = 0.0;
            }
            double fov = calculateFovFromExif(metadata);

            int imageWidth = metadata.getFirstDirectoryOfType(com.drew.metadata.jpeg.JpegDirectory.class)
                    .getInt(com.drew.metadata.jpeg.JpegDirectory.TAG_IMAGE_WIDTH);
            int imageHeight = metadata.getFirstDirectoryOfType(com.drew.metadata.jpeg.JpegDirectory.class)
                    .getInt(com.drew.metadata.jpeg.JpegDirectory.TAG_IMAGE_HEIGHT);
            
            ExifData.ExifDataBuilder builder = ExifData.builder()
                    .cameraLat(cameraLat)
                    .cameraLon(cameraLon)
                    .relativeAltitude(relativeAltitude)
                    .fov(fov)
                    .gimbalPitch(gimbalPitch)
                    .gimbalYaw(gimbalYaw)
                    .gimbalRoll(gimbalRoll)
                    .imageWidth(imageWidth)
                    .imageHeight(imageHeight);
            
            if (needLrfData) {
                try {
                    Double lrfDistance = extractNumericValue(xmpXml, "LRFTargetDistance");
                    builder.lrfTargetDistance(lrfDistance);
                } catch (Exception e) {
                    log.warn("LRF distance not found in EXIF, will proceed without LRF correction");
                }
            }
            
            return builder.build();
        }
    }

    private double parseGpsCoordinate(String dmsString, String refString) {
        Pattern pattern = Pattern.compile("(\\d+)°\\s*(\\d+)'\\s*([\\d.]+)\"");
        Matcher matcher = pattern.matcher(dmsString);
        
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid GPS coordinate format: " + dmsString);
        }
        
        double degrees = Double.parseDouble(matcher.group(1));
        double minutes = Double.parseDouble(matcher.group(2));
        double seconds = Double.parseDouble(matcher.group(3));
        
        double decimal = degrees + minutes / 60.0 + seconds / 3600.0;
        
        if ("S".equals(refString) || "W".equals(refString)) {
            decimal = -decimal;
        }
        
        return decimal;
    }

    private double extractNumericValue(String xmpXml, String tagName) {
        Pattern pattern = Pattern.compile(tagName + "[\"']?[\\s>:=]+[\"']?([+-]?[\\d.]+)");
        Matcher matcher = pattern.matcher(xmpXml);
        
        if (!matcher.find()) {
            throw new IllegalArgumentException("Tag not found in XMP: " + tagName);
        }
        
        return Double.parseDouble(matcher.group(1));
    }

    private double calculateFovFromExif(Metadata metadata) {
        ExifSubIFDDirectory exifSubDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (exifSubDir == null || !exifSubDir.containsTag(ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH)) {
            throw new IllegalArgumentException("FocalLengthIn35mmFormat not found in EXIF");
        }
        
        Integer focalLength35mm = exifSubDir.getInteger(ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH);
        if (focalLength35mm == null || focalLength35mm <= 0) {
            throw new IllegalArgumentException("Invalid FocalLengthIn35mmFormat value: " + focalLength35mm);
        }
        
        double digitalZoomRatio = 1.0;
        if (exifSubDir.containsTag(ExifSubIFDDirectory.TAG_DIGITAL_ZOOM_RATIO)) {
            Double value = exifSubDir.getDoubleObject(ExifSubIFDDirectory.TAG_DIGITAL_ZOOM_RATIO);
            if (value == null || !Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("Invalid DigitalZoomRatio value: " + value);
            }
            // EXIF uses zero to indicate that digital zoom was not used.
            if (value > 0.0) {
                digitalZoomRatio = value;
            }
        }

        return 2.0 * Math.toDegrees(Math.atan(36.0 / (2.0 * focalLength35mm * digitalZoomRatio)));
    }

    private double calculatePolygonArea(List<GeoCoordinate> coordinates) {
        int n = coordinates.size();
        if (n < 3) {
            return 0.0;
        }

        double area = 0.0;
        double avgLat = coordinates.stream()
                .mapToDouble(GeoCoordinate::getLatitude)
                .average()
                .orElse(0.0);

        double metersPerDegreeLon = 111320.0 * Math.cos(Math.toRadians(avgLat));

        for (int i = 0; i < n; i++) {
            GeoCoordinate current = coordinates.get(i);
            GeoCoordinate next = coordinates.get((i + 1) % n);

            double x1 = current.getLongitude() * metersPerDegreeLon;
            double y1 = current.getLatitude() * 111320.0;
            double x2 = next.getLongitude() * metersPerDegreeLon;
            double y2 = next.getLatitude() * 111320.0;

            area += x1 * y2 - x2 * y1;
        }

        return Math.abs(area) / 2.0;
    }
}
