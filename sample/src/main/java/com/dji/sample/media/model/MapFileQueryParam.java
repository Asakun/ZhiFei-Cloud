package com.dji.sample.media.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query parameters for paging the map file list.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MapFileQueryParam {

    /**
     * Keyword to fuzzy match file name or remark.
     */
    private String keyword;

    /**
     * Map data file type. 0: image layer; 1: terrain layer; 2: 3d tileset
     */
    private Integer fileType;

}
