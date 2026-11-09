package com.dji.sample.proxy.controller;

import com.dji.sample.proxy.service.IEmqxService;
import com.dji.sdk.common.HttpResultResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@Slf4j
@RequestMapping("${url.proxy.prefix}/emqx")
public class EmqxController {

    @Autowired
    private IEmqxService emqxService;

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @GetMapping("/clients")
    public HttpResultResponse getClientList(String clientId) throws JsonProcessingException {
        ArrayNode data = emqxService.getClientList(clientId);
        return HttpResultResponse.success(data);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/kick")
    public HttpResultResponse kickClients(@RequestBody ArrayNode clientIds) throws JsonProcessingException {
        boolean result = emqxService.kickClients(clientIds);
        if (result) {
            return HttpResultResponse.success();
        }
        return HttpResultResponse.error();
    }
}
