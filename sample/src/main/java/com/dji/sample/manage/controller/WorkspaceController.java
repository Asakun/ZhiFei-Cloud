package com.dji.sample.manage.controller;

import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.manage.model.dto.WorkspaceDTO;
import com.dji.sample.manage.service.IWorkspaceService;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.common.PaginationData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import java.util.Optional;

import static com.dji.sample.component.AuthInterceptor.TOKEN_CLAIM;

/**
 * @author sean.zhou
 * @version 0.1
 * @date 2021/11/23
 */
@RestController
@RequestMapping("${url.manage.prefix}${url.manage.version}/workspaces")
public class WorkspaceController {

    @Autowired
    private IWorkspaceService workspaceService;

    /**
     * Gets information about the workspace that the current user is in.
     * @param request
     * @return
     */
    @GetMapping("/current")
    public HttpResultResponse getCurrentWorkspace(HttpServletRequest request) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        Optional<WorkspaceDTO> workspaceOpt = workspaceService.getWorkspaceByWorkspaceId(customClaim.getWorkspaceId());

        return workspaceOpt.isEmpty() ? HttpResultResponse.error() : HttpResultResponse.success(workspaceOpt.get());
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/add")
    public HttpResultResponse addWorkspace(@RequestBody @NotNull WorkspaceDTO workspaceDTO) {
        if (StringUtils.isEmpty(workspaceDTO.getWorkspaceName())) {
            return HttpResultResponse.error("workspace name 不为空");
        }
        if (StringUtils.isEmpty(workspaceDTO.getBindCode())) {
            return HttpResultResponse.error("bind code 不为空");
        }
        Boolean result = workspaceService.saveWorkspace(workspaceDTO);
        return result ? HttpResultResponse.success(): HttpResultResponse.error("添加失败");
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/update")
    public HttpResultResponse updateWorkspace(@RequestBody @NotNull WorkspaceDTO workspaceDTO) {
        if (StringUtils.isEmpty(workspaceDTO.getWorkspaceId())) {
            return HttpResultResponse.error("workspace id 不为空");
        }
        Boolean result = workspaceService.updateWorkspace(workspaceDTO);
        return result ? HttpResultResponse.success(): HttpResultResponse.error("更新失败");
    }


    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/page-list")
    public HttpResultResponse getWorkspacePageList(@RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(value = "page_size", defaultValue = "50") Integer pageSize) {
        PaginationData<WorkspaceDTO> paginationData = workspaceService.getWorkspaceByPage(page, pageSize);
        return HttpResultResponse.success(paginationData);
    }
}