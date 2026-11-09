package com.dji.sample.psdk.service;

import org.springframework.web.multipart.MultipartFile;

public interface IPsdkAeroSpotFileService {
    String uploadFileToOss(MultipartFile file, String aeroSpotSn);

    Boolean dealWithAeroSpotToPsdk(String dockSn, String droneSn, String flightId);
}
