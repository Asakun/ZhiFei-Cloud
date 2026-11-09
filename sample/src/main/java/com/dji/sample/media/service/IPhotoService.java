package com.dji.sample.media.service;

import com.dji.sample.media.model.PhotoProcessRequest;
import com.dji.sample.media.model.PhotoProcessResponse;

public interface IPhotoService {

    /**
     * Process photo coordinates conversion from pixel to geographic coordinates
     * @param workspaceId workspace ID
     * @param request photo process request containing objectKey and pixel coordinates
     * @return response with converted geographic coordinates
     */
    PhotoProcessResponse processPhotoCoordinates(String workspaceId, PhotoProcessRequest request);
}
