package com.dji.sample.media.controller;

import com.dji.sample.media.model.MapFileDTO;
import com.dji.sample.media.model.MapFileQueryParam;
import com.dji.sample.media.service.IMapFileService;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.common.PaginationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Map file management.
 */
@RestController
@RequestMapping("${url.media.prefix}${url.media.version}/map-files")
public class MapFileController {

    @Autowired
    private IMapFileService mapFileService;

    /**
     * Get a paged list of map files by workspace.
     * @param workspaceId workspace id (path variable, snake_case)
     * @param page current page (default 1)
     * @param pageSize page size (default 10, name = "page_size")
     * @param queryParam query conditions
     * @return paged list
     */
    @PostMapping("/{workspace_id}/page-list")
    public HttpResultResponse<PaginationData<MapFileDTO>> getPageList(
            @PathVariable(name = "workspace_id") String workspaceId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(name = "page_size", defaultValue = "10") Long pageSize,
            @RequestBody(required = false) MapFileQueryParam queryParam) {
        PaginationData<MapFileDTO> data = mapFileService.getPaginationByWorkspaceId(workspaceId, queryParam, page, pageSize);
        return HttpResultResponse.success(data);
    }
}
