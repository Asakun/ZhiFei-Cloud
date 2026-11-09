package com.dji.sample.media.service;

import com.dji.sample.media.model.MapFileDTO;
import com.dji.sample.media.model.MapFileQueryParam;
import com.dji.sdk.common.PaginationData;

/**
 * Service for map file operations.
 */
public interface IMapFileService {

    /**
     * Get a paged list of map files by workspace.
     * @param workspaceId workspace id
     * @param param query parameters
     * @param page current page (starting from 1)
     * @param pageSize page size
     * @return paged data
     */
    PaginationData<MapFileDTO> getPaginationByWorkspaceId(String workspaceId, MapFileQueryParam param, long page, long pageSize);

    /**
     * Save a map file.
     * @param workspaceId workspace id
     * @param dto map file data
     * @return rows affected
     */
    Integer save(String workspaceId, MapFileDTO dto);

    /**
     * Update a map file.
     * @param workspaceId workspace id
     * @param dto map file data
     * @return true if updated
     */
    Boolean update(String workspaceId, MapFileDTO dto);

    /**
     * Delete a map file by file id.
     * @param workspaceId workspace id
     * @param fileId file id
     * @return true if deleted
     */
    Boolean delete(String workspaceId, String fileId);
}
