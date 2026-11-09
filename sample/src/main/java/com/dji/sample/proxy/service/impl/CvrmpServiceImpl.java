package com.dji.sample.proxy.service.impl;

import cn.hutool.core.date.DateUtil;
import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.common.util.IpUtil;
import com.dji.sample.common.util.JwtUtil;
import com.dji.sample.component.mqtt.config.MqttPropertyConfiguration;
import com.dji.sample.component.oss.model.OssConfiguration;
import com.dji.sample.component.oss.service.impl.OssServiceContext;
import com.dji.sample.manage.model.dto.DeviceDTO;
import com.dji.sample.manage.model.dto.DevicePayloadDTO;
import com.dji.sample.manage.model.dto.LiveStreamProperty;
import com.dji.sample.manage.model.param.DeviceQueryParam;
import com.dji.sample.manage.service.IDeviceService;
import com.dji.sample.proxy.model.LlmImgDetectRequest;
import com.dji.sample.proxy.model.LlmImgEventRequest;
import com.dji.sample.media.service.ICvrmpEventService;
import com.dji.sample.proxy.service.ICvrmpService;
import com.dji.sample.proxy.service.IProxyService;
import com.dji.sample.wayline.service.ICvrmpCameraService;
import com.dji.sample.wayline.model.dto.DuringFlightInfo;
import com.dji.sdk.cloudapi.control.DrcModeMqttBroker;
import com.dji.sdk.cloudapi.device.PayloadPositionEnum;
import com.dji.sdk.cloudapi.livestream.UrlTypeEnum;
import com.dji.sdk.common.HttpResultResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.dji.sample.common.util.CommonUtil.dealStartEndTime;

@Service
@Slf4j
public class CvrmpServiceImpl implements ICvrmpService, ICvrmpCameraService, ICvrmpEventService {
    @Value("${proxy.cvrmp.url}")
    private String TARGET_API_URL;

    private final String permanentToken;

    private Map<String,String> deviceMapping;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private IProxyService proxyService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OssServiceContext ossService;

    @Value("${mqtt.BASIC.host}")
    private String mqttBroker;

    @Value("${mqtt.BASIC.port}")
    private String mqttPort;

    @Value("${spring.application.name}")
    private String appName;

    @Value("${server.port}")
    private String serverPort;

    private CvrmpServiceImpl(@Value("${proxy.cvrmp.sub}") String sub, @Value("${proxy.cvrmp.secret}") String secret) {
        try {
            permanentToken = JwtUtil.createToken(secret, sub, 9999999999L);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        if (StringUtils.isNotEmpty(getToken())) {
            headers.add("authorization", String.format("Bearer %s", getToken()));
        }
        return headers;
    }

    @Override
    public Map<String, String> initDeviceMapping() throws JsonProcessingException {
        deviceMapping = new HashMap<>();
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("user", appName);
        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("table", "boxcamera");
        String url = String.format("%s/%s", TARGET_API_URL, "query_list");
        ResponseEntity<String> responseEntity = proxyService.postRequest(url, getHeaders(), requestBody, params);
        JsonNode responseNode = objectMapper.readTree(responseEntity.getBody());
        JsonNode dataNodes = responseNode.get("data");
        for (JsonNode dataNode : dataNodes) {
            String device_name = dataNode.get("device_name").asText();
            String id = dataNode.get("id").asText();
            deviceMapping.put(device_name,id);
        }
        log.info("initDeviceMapping by user={}", appName);
        return deviceMapping;
    }

    @Override
    public String getToken(){
        return permanentToken;
    }

    @Override
    public String getCameraId(String deviceSn) throws JsonProcessingException {
        if (deviceMapping == null){
            initDeviceMapping();
        }
        String result = deviceMapping.get(deviceSn);
        if (StringUtils.isEmpty(result)) {
            throw new RuntimeException("device mapping id not found");
        }
        return result;
    }

    @Override
    public String getCameraIdBySn(String deviceSn) throws JsonProcessingException {
        String url = String.format("%s/%s", TARGET_API_URL, "query_list");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("table", "boxcamera");
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("device_name", deviceSn);
        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        ResponseEntity<String> responseEntity = proxyService.postRequest(url, getHeaders(), requestBody, params);
        JsonNode responseNode = objectMapper.readTree(responseEntity.getBody());
        JsonNode dataNodes = responseNode.get("data");
        for (JsonNode dataNode : dataNodes) {
            return dataNode.get("id").asText();
        }
        return "";
    }

    @Override
    public List<String> getCameraIdList(String workspaceId, String deviceSn) throws JsonProcessingException {
        if (deviceMapping == null){
            initDeviceMapping();
        }
        if (StringUtils.isNotEmpty(deviceSn) && deviceMapping.containsKey(deviceSn)) {
            return List.of(getCameraId(deviceSn));
        }
        List<DeviceDTO> deviceDTOList = deviceService.getDevicesByParams(DeviceQueryParam.builder()
                .workspaceId(workspaceId)
                .subType(1)
                .build());
        List<String> list = new ArrayList<>();
        list.add("0");
        for (DeviceDTO deviceDTO : deviceDTOList) {
            if (deviceMapping.containsKey(deviceDTO.getDeviceSn())) {
                list.add(getCameraId(deviceDTO.getDeviceSn()));
            }
        }
        return list;
    }

    @Override
    public String getApiUrl() {
        return TARGET_API_URL;
    }

    @Override
    public ResponseEntity<String> postRequest(String path, String requestBody, MultiValueMap<String, String> params) {
        String url = String.format("%s/%s", TARGET_API_URL, path);

        return proxyService.postRequest(url, getHeaders(), requestBody, params);
    }

    @Override
    public ResponseEntity<String> postFileRequest(String path, MultipartFile multipartFile, MultiValueMap<String, String> params) throws IOException {
        String url = String.format("%s/%s", TARGET_API_URL, path);
        HttpHeaders headers = getHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        fileHeaders.setContentDispositionFormData("file", multipartFile.getOriginalFilename());
        body.add("file", new HttpEntity<>(multipartFile.getBytes(), fileHeaders));
        return proxyService.postFileRequest(url, headers, body, params);
    }

    private static String extractBaseUrl(String url) {
        // 找到协议部分后的第一个斜杠位置
        int startIndex = url.indexOf("://") + 3;
        // 找到下一个斜杠的位置
        int endIndex = url.indexOf("/", startIndex);
        // 截取基础URL
        return url.substring(0, endIndex);
    }

    private DevicePayloadDTO getDeviceCamera(DeviceDTO device) {
        for (DevicePayloadDTO payloadDTO: device.getPayloadsList()) {
            if (payloadDTO.getIndex() == 0) {
                return payloadDTO;
            }
        }
        throw new IllegalArgumentException("camera doesn't exist.");
    }

    @Override
    public String saveCamera(DeviceDTO device) throws JsonProcessingException {
        String url = String.format("%s/%s", TARGET_API_URL, "save_camera");
        String livestreamUrl = LiveStreamProperty.get(UrlTypeEnum.WHIP).toString();
        DevicePayloadDTO payloadDTO = getDeviceCamera(device);
        // TODO cvrmp转webrtc
        String stream = String.format("%s/live/%s-%s.flv",
                extractBaseUrl(livestreamUrl), device.getDeviceSn(), payloadDTO.getPayloadIndex().toString());
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("device_name", device.getDeviceSn());
        rootNode.put("user", appName);
        rootNode.put("name", device.getNickname());
        rootNode.put("wvp_device", "0");
        rootNode.put("wvp_channel", stream);
        rootNode.put("wvp_type", "flv");
        rootNode.put("rtspstream", stream);
        rootNode.put("capture_interval", 1);
        rootNode.put("capture_delay", 0);
        rootNode.put("boxid", -1);
        rootNode.put("devicetypeid", 5);
        rootNode.put("ocr_preset", 0);
        rootNode.put("mask", "{}");
        rootNode.put("fps", 30);
        rootNode.put("type", 2);
        for (DevicePayloadDTO payload: device.getPayloadsList()) {
            if (payload.getIndex() == PayloadPositionEnum.FRONT_LEFT.getPosition()) {
                rootNode.put("device_code", payload.getPayloadIndex().toString());
            }
        }
        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        ResponseEntity<String> responseEntity = proxyService.postRequest(url, getHeaders(), requestBody, new LinkedMultiValueMap<>());
        JsonNode responseNode = objectMapper.readTree(responseEntity.getBody());
        return responseNode.get("data").asText();
    }

    private ArrayNode getRuleList(String cId) throws JsonProcessingException {
        String url = String.format("%s/%s", TARGET_API_URL, "query_list");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("table", "eventrule");
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("cameraid", cId);
        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        ResponseEntity<String> responseEntity = proxyService.postRequest(url, getHeaders(), requestBody, params);
        JsonNode responseNode = objectMapper.readTree(responseEntity.getBody());
        if (responseNode.get("status").asBoolean()) {
            return (ArrayNode) responseNode.get("data");
        }
        return null;
    }

    private Boolean saveRules(ArrayNode tasksNode) throws JsonProcessingException {
        String url = String.format("%s/%s", TARGET_API_URL, "save_rules");
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.set("tasks", tasksNode);
        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        ResponseEntity<String> responseEntity = proxyService.postRequest(url, getHeaders(), requestBody, new LinkedMultiValueMap<>());
        JsonNode responseNode = objectMapper.readTree(responseEntity.getBody());
        return responseNode.get("status").asBoolean();
    }

    private JsonNode getRuleTemplate(Map<String, String> vars, String etId, ArrayNode etVal) throws JsonProcessingException {
        var ref = new Object() {
            Integer conf = 90;
            Set<String> multiCls = new HashSet<>();
        };
        ArrayNode targetsNode = objectMapper.createArrayNode();
        if (etVal != null) {
            StreamSupport.stream(etVal.spliterator(), false)
                    .forEach(node ->{
                        ref.conf = Math.min(ref.conf, node.get("conf").asInt());
                        ref.multiCls.add(node.get("cls").asText());
                        targetsNode.add(node);
                    });
        }
        String clientId = vars.get("droneSn") + "_AI.BACK";
        String username = "ai.back";
        DrcModeMqttBroker drcModeMqttBroker = MqttPropertyConfiguration.getMqttBrokerWithDrc(
                clientId, username, 3600L, Map.of("clientId", clientId, "username", username));
        vars.put("etId", etId);
        vars.put("conf", String.valueOf(ref.conf));
        vars.put("multi_cls", String.join(",", ref.multiCls));
        vars.put("targets", targetsNode.toString());
        vars.put("mqttBroker", mqttBroker);
        vars.put("mqttPort", mqttPort);
        vars.put("username", drcModeMqttBroker.getUsername());
        vars.put("password", drcModeMqttBroker.getPassword());
        String template = "{\n" +
                "            \"cameraid\": ${cId},\n" +
                "            \"presetid\": 1,\n" +
                "            \"eventtypeid\": ${etId},\n" +
                "            \"area\": {\n" +
                "                \"area1\": [],\n" +
                "                \"area2\": []\n" +
                "            },\n" +
                "            \"conf\": ${conf},\n" +
                "            \"intervals\": ${intervals},\n" +
                "            \"period\": \"[{\\\"type\\\":\\\"Day\\\",\\\"begin\\\":\\\"00:00:00\\\",\\\"end\\\":\\\"23:59:59\\\",\\\"times\\\":[\\\"00:00:00\\\",\\\"23:59:59\\\"]}]\",\n" +
                "            \"priority\": 1,\n" +
                "            \"rulemodel\": {\n" +
                "                \"top_mark\": 1,\n" +
                "                \"step_printing_1\": 2,\n" +
                "                \"do_pre_compress\": 1,\n" +
                "                \"multi_cls\": \"${multi_cls}\",\n" +
                "                \"step_printing_2\": 10,\n" +
                "                \"hit_floor\": 1,\n" +
                "                \"dji_flight_hub\": {\n" +
                "                    \"mqtt_broker\": \"${mqttBroker}\",\n" +
                "                    \"mqtt_port\": ${mqttPort},\n" +
                "                    \"mqtt_username\": \"${username}\",\n" +
                "                    \"mqtt_password\": \"${password}\",\n" +
                "                    \"workspace_id\": \"${workspaceId}\",\n" +
                "                    \"payload_index\": \"${payloadIndex}\",\n" +
                "                    \"token\": \"${token}\",\n" +
                "                    \"dock_sn\": \"${dockSn}\",\n" +
                "                    \"sn\": \"${droneSn}\"\n" +
                "                },\n" +
                "                \"external_id\": \"${jobId}\",\n" +
                "                \"cloud_url\": \"${cloudUrl}\",\n" +
                "                \"dji_detect_task\": {\n" +
                "                    \"detect_interval\": ${detect_interval},\n" +
                "                    \"targets\": ${targets}\n" +
                "                }\n" +
                "            },\n" +
                "            \"size\": {\n" +
                "                \"minsize\": {\n" +
                "                    \"w\": 10,\n" +
                "                    \"h\": 10\n" +
                "                },\n" +
                "                \"maxsize\": {\n" +
                "                    \"w\": 1000,\n" +
                "                    \"h\": 1000\n" +
                "                }\n" +
                "            },\n" +
                "            \"state\": 1,\n" +
                "            \"type\": 1\n" +
                "        }";
        String out = StringSubstitutor.replace(template, vars);
        return objectMapper.readTree(out);
    }

    @Override
    public Boolean executeRule(String cid, String key) {
        if (StringUtils.isEmpty(cid)){return true;}
        String url = String.format("%s/%s", TARGET_API_URL, key);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("cameraid", cid);
        ResponseEntity<String> responseEntity = proxyService.postRequest(url, getHeaders(), null, params);
        return responseEntity.getStatusCode() == HttpStatus.OK;
    }

    @Async
    @Override
    public void stopCameraAsync(String cameraId) {
        executeRule(cameraId, "stop_camera");
    }

    private String getAndRm(ObjectNode node, String key, String defaultValue) {
        if (node.has(key)) {
            JsonNode value = node.get(key);
            node.remove(key);
            return value.asText();
        }
        return defaultValue;
    }

    private String getTempToken(CustomClaim customClaim) {
        CustomClaim claim = new CustomClaim(customClaim.getId(),
                customClaim.getUsername(), customClaim.getUserType(),
                customClaim.getWorkspaceId(), "ROLE_USER", 3);

        return JwtUtil.createToken(claim.convertToMap(), 3600);
    }

    private String getCloudUrl() {
        return String.format("http://%s:%s", IpUtil.getServerRealIp(), serverPort);
    }

    @Override
    public String saveAndSendRule(CustomClaim customClaim, DuringFlightInfo flightInfo, DeviceDTO deviceDTO, String etIds) throws JsonProcessingException {
        String droneSn = deviceDTO.getChildDeviceSn();
        String cId = getCameraId(droneSn);
        deviceService.spliceDeviceTopo(deviceDTO);
        ArrayNode ruleList = getRuleList(cId);
        ArrayNode tasksNode = objectMapper.createArrayNode();
        Map<String, String> vars = new HashMap<>(Map.of(
                "cId", cId,
                "droneSn", droneSn,
                "jobId", flightInfo.getFlightId(),
                "dockSn", deviceDTO.getDeviceSn(),
                "workspaceId", customClaim.getWorkspaceId(),
                "token", getTempToken(customClaim),
                "cloudUrl", getCloudUrl()
        ));
        if (deviceDTO.getChildren() == null){
            throw new RuntimeException("deviceDTO.getChildren() == null");
        }
        List<DevicePayloadDTO> payloadDTOList = deviceDTO.getChildren().getPayloadsList();
        if (payloadDTOList != null && !payloadDTOList.isEmpty()) {
            vars.put("payloadIndex", payloadDTOList.get(0).getPayloadIndex().toString());
        }
        ObjectNode etNode = (ObjectNode) objectMapper.readTree(etIds);
        vars.put("detect_interval", getAndRm(etNode, "detect_interval", "10"));
        vars.put("intervals", getAndRm(etNode, "intervals", "10"));
        if (!etNode.isEmpty()) {
            StreamSupport.stream(ruleList.spliterator(), false)
                    .forEach(node ->{
                        if (!etNode.has(node.get("eventtypeid").asText())) {
                            ObjectNode obj = (ObjectNode) node;
                            String etId = "-"+obj.get("eventtypeid").asText();
                            obj.put("eventtypeid", etId);
                            tasksNode.add(obj);
                        }
                    });
            Iterator<Map.Entry<String, JsonNode>> entries = etNode.fields();
            while (entries.hasNext()) {
                Map.Entry<String, JsonNode> entry = entries.next();
                JsonNode ruleNode = getRuleTemplate(vars, entry.getKey(), (ArrayNode) entry.getValue());
                tasksNode.add(ruleNode);
            }
        }
        if (tasksNode.isEmpty()) {
            tasksNode.add(getRuleTemplate(vars, "999", null));
            saveRules(tasksNode);
            return "-"+cId;
        }
        if (saveRules(tasksNode)) {
            if (executeRule(cId, "sendRule")) {
                return cId;
            }
            throw new RuntimeException("send rule failed");
        }
        throw new RuntimeException("update rule failed");
    }

    @Override
    public ResponseEntity<String> postEventSumRequest(String workspaceId, Long startTime, Long endTime) throws JsonProcessingException {
        Long[] ref = dealStartEndTime(startTime, endTime);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("st", DateUtil.format(DateUtil.date(ref[0]), "yyyy-MM-dd"));
        params.add("et", DateUtil.format(DateUtil.date(ref[1]), "yyyy-MM-dd"));
        List<String> cameraIdList = getCameraIdList(workspaceId, null);
        params.add("cids", StringUtils.join(cameraIdList,","));
        return postRequest("/query_event_sum_statistics", null,params);
    }

    @Override
    public ArrayNode getTableListById(String table) throws JsonProcessingException {
        return getTableListById(table, "{}");
    }

    @Override
    public ArrayNode getTableListById(String table, String requestBody) throws JsonProcessingException {
        String url = String.format("%s/%s", TARGET_API_URL, "query_list");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("table", table);
        if (StringUtils.isEmpty(requestBody)) {requestBody = "{}";}
        ResponseEntity<String> responseEntity = proxyService.postRequest(url, getHeaders(), requestBody, params);
        JsonNode responseNode = objectMapper.readTree(responseEntity.getBody());
        if (responseNode.get("status").asBoolean()) {
            return (ArrayNode) responseNode.get("data");
        }
        return null;
    }

    @Override
    public ArrayNode getEventTypeList(String requestBody) throws JsonProcessingException {
        ArrayNode array1 = getTableListById("llm_skill");
        ArrayNode array2 = getTableListById("eventtype", requestBody);
        ArrayNode result = objectMapper.createArrayNode();
        if (array1 != null) {
            StreamSupport.stream(array1.spliterator(), false)
                    .forEach(node -> {
                        ObjectNode obj = (ObjectNode) node;
                        String id = obj.get("id").asText();
                        obj.put("id", "-" + id);
                        result.add(obj);
                    });
        }
        if (array2 != null) {
            result.addAll(array2);
        }
        return result;
    }

    private void sendUrlsEvent(List<String> urls, JsonNode params) {
        try {
            if (urls == null) {
                log.warn("sendUrlsEvent urls is null");
                return;
            }
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.set("urls", objectMapper.valueToTree(urls));
            rootNode.set("params", params != null ? params : objectMapper.createObjectNode());
            String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
            ResponseEntity<String> response = postRequest("receive_urls_event", requestBody, new LinkedMultiValueMap<>());
            log.info("sendUrlsEvent response: {}", response.getBody());
        } catch (Exception e) {
            log.error("sendUrlsEvent error", e);
        }
    }

    private void sendLlmImgEvent(List<String> urls, LlmImgEventRequest request) {
        if (Objects.isNull(request)) {
            log.warn("sendLlmImgEvent request is null");
            return;
        }
        ObjectNode params = objectMapper.createObjectNode();
        if (StringUtils.isNotEmpty(request.getLlmId())) {
            params.put("model_name", request.getLlmId());
        }
        if (StringUtils.isNotEmpty(request.getSkillId())) {
            params.put("skill_id", request.getSkillId());
        }
        if (Objects.nonNull(request.getPromptDict())) {
            params.set("prompt_dict", objectMapper.valueToTree(request.getPromptDict()));
        }
        if (StringUtils.isNotEmpty(request.getJobId())) {
            params.put("job_id", request.getJobId());
        }
        if (StringUtils.isNotEmpty(request.getNum())) {
            params.put("num", request.getNum());
        }
        if (StringUtils.isNotEmpty(request.getDjiMediaType())) {
            params.put("dji_media_type", request.getDjiMediaType());
        }
        log.info("sendLlmImgEvent params: {}", params);
        sendUrlsEvent(urls, params);
    }

    @Async
    @Override
    public void sendLlmImgEvent(List<String> urls, String jobId, String rule, String num) {
        List<String> objectUrls = new ArrayList<>();
        for (String objectKey : urls) {
            objectUrls.add(ossService.getObjectUrl(OssConfiguration.bucket, objectKey).toString());
        }
        // rule 可能为 null（视频），需先判空再 split
        Map<String, String> map = new HashMap<>();
        if (StringUtils.isNotEmpty(rule)) {
            map = Arrays.stream(rule.split("&"))
                    .map(s -> s.split("=", 2)) // split限制分割2段！value内如果包含=不会截断
                    .filter(arr -> arr.length == 2)
                    .collect(Collectors.toMap(
                            arr -> arr[0],
                            arr -> arr[1],
                            (oldVal, newVal) -> newVal // key冲突策略：新值覆盖旧值
                    ));
        }
        String promptDictHead = "pd$";
        Map<String, String> promptDict = map.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(promptDictHead))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().substring(promptDictHead.length()),
                        Map.Entry::getValue
                ));
        // 解析编号段：按第一个 '-' 拆成数字编号与 DJI 类型；无 '-' 时类型默认 V
        String numValue = null;
        String djiMediaType = null;
        if (StringUtils.isNotEmpty(num)) {
            int dashIdx = num.indexOf("-");
            if (dashIdx >= 0) {
                numValue = num.substring(0, dashIdx);
                djiMediaType = num.substring(dashIdx + 1);
            } else {
                numValue = num;
                djiMediaType = "V";
            }
        }
        sendLlmImgEvent(objectUrls, LlmImgEventRequest.builder()
                .num(numValue)
                .djiMediaType(djiMediaType)
                .llmId(map.get("li"))
                .skillId(map.get("si"))
                .promptDict(promptDict)
                .jobId(jobId)
                .build());
    }

    @Override
    public HttpResultResponse sendLlmImgDetect(LlmImgDetectRequest request) throws JsonProcessingException {
        if (StringUtils.isNotEmpty(request.getDeviceSn())) {
            request.setCid(getCameraId(request.getDeviceSn()));
        }
        String url = String.format("%s/%s", TARGET_API_URL, "llm_img_detection");
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("model_name", request.getModelName());
        rootNode.set("prompt", objectMapper.valueToTree(request.getPromptDict()));
        ArrayNode imgB64sNode = objectMapper.createArrayNode();
        // imgBs64 支持多张图片，使用 "|" 分隔
        if (StringUtils.isNotEmpty(request.getImgBs64())) {
            for (String imgBs64 : request.getImgBs64().split("\\|")) {
                if (StringUtils.isNotEmpty(imgBs64)) {
                    imgB64sNode.add(imgBs64);
                }
            }
        }
        rootNode.set("img_b64s", imgB64sNode);
        rootNode.put("skill_id", request.getSkillId());
        ObjectNode eventInfoNode = objectMapper.createObjectNode();
        eventInfoNode.put("dt", request.getDt());
        eventInfoNode.put("lat", request.getLat());
        eventInfoNode.put("lon", request.getLon());
        eventInfoNode.put("cid", request.getCid());
        eventInfoNode.put("job_id", request.getJobId());
        rootNode.set("event_info", eventInfoNode);
        String requestBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        ResponseEntity<String> responseEntity = proxyService.postRequest(url, getHeaders(), requestBody, new LinkedMultiValueMap<>());
        JsonNode responseNode = objectMapper.readTree(responseEntity.getBody());
        if (responseNode.get("status").asBoolean()) {
            return HttpResultResponse.success(responseNode.get("data"));
        }
        return HttpResultResponse.error(responseNode.asText());
    }
}
