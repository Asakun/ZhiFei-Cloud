package com.dji.sample.media.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Map file data transfer object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapFileDTO {

    private Integer id;

    private String fileId;

    private String name;

    private String remark;

    private String filePath;

    private String workspaceId;

    private String objectKey;

    /**
     * Map data file type. 0: image layer; 1: terrain layer; 2: 3d tileset
     */
    private Integer fileType;

    /**
     * file size byte
     */
    private Long fileSize;

    /**
     * The maximum latitude of the map data bounding box
     */
    private Double maxLatitude;

    /**
     * The minimum latitude of the map data bounding box
     */
    private Double minLatitude;

    /**
     * The maximum longitude of the map data bounding box
     */
    private Double maxLongitude;

    /**
     * The minimum longitude of the map data bounding box
     */
    private Double minLongitude;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
