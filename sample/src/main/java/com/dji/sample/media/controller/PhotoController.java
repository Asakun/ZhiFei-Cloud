package com.dji.sample.media.controller;

import com.dji.sample.media.model.PhotoProcessRequest;
import com.dji.sample.media.model.PhotoProcessResponse;
import com.dji.sample.media.service.IPhotoService;
import com.dji.sdk.common.HttpResultResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * Photo processing controller for pixel to geographic coordinate conversion
 */
@RestController
@RequestMapping("${url.media.prefix}${url.media.version}/photos")
public class PhotoController {

    @Autowired
    private IPhotoService photoService;

    /**
     * Process photo pixel coordinates to geographic coordinates
     * @param workspaceId workspace ID
     * @param request photo process request containing objectKey and pixel coordinates
     * @return converted geographic coordinates
     */
    @PostMapping("/{workspace_id}/process")
    public HttpResultResponse<PhotoProcessResponse> processPhotoCoordinates(
            @PathVariable(name = "workspace_id") String workspaceId,
            @RequestBody @Valid PhotoProcessRequest request) {
        
        PhotoProcessResponse response = photoService.processPhotoCoordinates(workspaceId, request);
        
        return HttpResultResponse.success(response);
    }
}
