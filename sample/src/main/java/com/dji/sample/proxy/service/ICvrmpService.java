package com.dji.sample.proxy.service;

import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.manage.model.dto.DeviceDTO;
import com.dji.sample.proxy.model.LlmImgDetectRequest;
import com.dji.sample.wayline.model.dto.DuringFlightInfo;
import com.dji.sdk.common.HttpResultResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ICvrmpService {
    Map<String, String> initDeviceMapping() throws JsonProcessingException;

    public String getToken();

    public String getApiUrl();

    public String getCameraId(String deviceSn) throws JsonProcessingException;

    String getCameraIdBySn(String deviceSn) throws JsonProcessingException;

    public List<String> getCameraIdList(String workspaceId, String deviceSn) throws JsonProcessingException;

    public ResponseEntity<String> postRequest(String path, String requestBody, MultiValueMap<String, String> params);

    ResponseEntity<String> postFileRequest(String path, MultipartFile file, MultiValueMap<String, String> params) throws IOException;

    String saveCamera(DeviceDTO device) throws JsonProcessingException;

    Boolean executeRule(String cid, String key);

    String saveAndSendRule(CustomClaim customClaim, DuringFlightInfo flightInfo, DeviceDTO deviceDTO, String etIds) throws JsonProcessingException;

    ResponseEntity<String> postEventSumRequest(String workspaceId, Long startTime, Long endTime) throws JsonProcessingException;

    ArrayNode getTableListById(String table) throws JsonProcessingException;

    ArrayNode getTableListById(String table, String requestBody) throws JsonProcessingException;

    ArrayNode getEventTypeList(String requestBody) throws JsonProcessingException;

    HttpResultResponse sendLlmImgDetect(LlmImgDetectRequest request) throws JsonProcessingException;
}
