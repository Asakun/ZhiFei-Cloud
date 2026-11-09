package com.dji.sample.psdk.controller;

import com.dji.sample.psdk.model.dto.AeroSpotUploadParam;
import com.dji.sample.psdk.service.IPsdkAeroSpotFileService;
import com.dji.sdk.common.HttpResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@RestController
@Slf4j
@RequestMapping("${url.psdk.prefix}${url.psdk.version}/aero_spot")
public class PsdkAeroSpotController {

    @Autowired
    private IPsdkAeroSpotFileService aeroSpotFileService;

    @PostMapping("/file/upload")
    public HttpResultResponse psdkAeroSpotFileUpload(@NotNull(message = "No file received.") MultipartFile file,
                                                     @Valid AeroSpotUploadParam param) {
        String ossKey = aeroSpotFileService.uploadFileToOss(file, param.getDeviceSn());
        if (StringUtils.hasText(ossKey)) {
            HttpResultResponse response = HttpResultResponse.success();
            return response.setMessage(ossKey);
        } else {
            return HttpResultResponse.error("upload file failed");
        }
    }
}
