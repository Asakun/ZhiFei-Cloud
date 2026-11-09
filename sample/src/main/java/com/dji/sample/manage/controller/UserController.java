package com.dji.sample.manage.controller;

import com.dji.sample.common.error.CommonErrorEnum;
import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.component.RsaUtil;
import com.dji.sample.manage.model.dto.UserDTO;
import com.dji.sample.manage.model.dto.UserListDTO;
import com.dji.sample.manage.model.dto.UserLoginDTO;
import com.dji.sample.manage.model.dto.UserPasswordDTO;
import com.dji.sample.manage.model.enums.UserStatusEnum;
import com.dji.sample.manage.service.IUserService;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.common.PaginationData;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import java.util.Optional;

import static com.dji.sample.component.AuthInterceptor.PARAM_TOKEN;
import static com.dji.sample.component.AuthInterceptor.TOKEN_CLAIM;


@RestController
@RequestMapping("${url.manage.prefix}${url.manage.version}/users")
public class UserController {

    @Autowired
    private IUserService userService;

    @Autowired
    private RsaUtil rsaUtil;

    /**
     * Query the information of the current user.
     * @param request
     * @return
     */
    @GetMapping("/current")
    public HttpResultResponse getCurrentUserInfo(HttpServletRequest request) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        return userService.getUserByUsername(customClaim.getUsername(), customClaim.getWorkspaceId());
    }

    /**
     * Paging to query all users in a workspace.
     * @param page      current page
     * @param pageSize
     * @return
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @GetMapping("/{workspace_id}/users")
    public HttpResultResponse<PaginationData<UserListDTO>> getUsers(@RequestParam(defaultValue = "1") Long page,
                                                                    @RequestParam(value = "page_size", defaultValue = "50") Long pageSize,
                                                                    HttpServletRequest request) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        PaginationData<UserListDTO> paginationData = userService.getUsersByWorkspaceId(page, pageSize, customClaim);
        return HttpResultResponse.success(paginationData);
    }

    /**
     * Modify user information. Only mqtt account information is included, nothing else can be modified.
     * @param user
     * @param workspaceId
     * @param userId
     * @return
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PutMapping("/{workspace_id}/users/{user_id}")
    public HttpResultResponse updateUser(@RequestBody UserListDTO user,
                                         @PathVariable("workspace_id") String workspaceId,
                                         @PathVariable("user_id") String userId,
                                         HttpServletRequest request) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        if ((!customClaim.getId().equals(userId) || !customClaim.getWorkspaceId().equals(workspaceId)) && customClaim.getLevel() < 5) {
            return HttpResultResponse.error("only edit user info by user self");
        }
        userService.updateUser(workspaceId, userId, user);
        return HttpResultResponse.success();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @PostMapping("/reset-password")
    public HttpResultResponse resetPassword(HttpServletRequest request,
                                            @Valid @RequestBody UserPasswordDTO passwordDTO) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        passwordDTO.setOldPassword(rsaUtil.decryptByPrivateKey(passwordDTO.getOldPassword()));
        passwordDTO.setNewPassword(rsaUtil.decryptByPrivateKey(passwordDTO.getNewPassword()));
        return userService.resetPassword(customClaim.getUsername(), passwordDTO);
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/init-password/{user_id}")
    public HttpResultResponse initPassword(HttpServletRequest request, @PathVariable("user_id") String userId,
                                           @RequestBody @NotNull String password) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        if (customClaim.getId().equals(userId)) {
            return HttpResultResponse.error("invalid action");
        }
        password = rsaUtil.decryptByPrivateKey(password);
        return userService.initPassword(userId, password);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PostMapping("/register")
    public HttpResultResponse userRegister(HttpServletRequest request, @RequestBody UserLoginDTO user) throws JsonProcessingException {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        user.setPassword(rsaUtil.decryptByPrivateKey(user.getPassword()));
        return userService.userRegister(customClaim, user);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PutMapping("/{user_id}/status")
    public HttpResultResponse updateUserStatus(HttpServletRequest request,
                                               @PathVariable("user_id")String userId, Integer status) throws JsonProcessingException {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        if (customClaim.getId().equals(userId)) {
            return HttpResultResponse.error("can not update self status");
        }
        UserStatusEnum userStatus = UserStatusEnum.find(status);
        if (userStatus == null) {return HttpResultResponse.error("illegal status code");}
        if (userService.updateUserStatus(customClaim, userId, userStatus)) {
            return HttpResultResponse.success();
        }
        return HttpResultResponse.error();
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/change-self-workspace-id/{workspace_id}")
    public HttpResultResponse changeSelfWorkspaceId(HttpServletRequest request,
                                                    HttpServletResponse response,
                                                    @PathVariable(name = "workspace_id") String workspaceId) {
        CustomClaim customClaim = (CustomClaim)request.getAttribute(TOKEN_CLAIM);
        if (customClaim.getWorkspaceId().equals(workspaceId)) {
            return HttpResultResponse.error("workspace id not change");
        }
        Boolean result = userService.updateUser(workspaceId, customClaim.getId(), null);
        if (result) {
            Optional<UserDTO> user = userService.refreshToken(request.getHeader(PARAM_TOKEN), workspaceId);
            if (user.isEmpty()) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                return HttpResultResponse.error(CommonErrorEnum.NO_TOKEN.getMessage());
            }
            return HttpResultResponse.success(user.get());
        }
        return HttpResultResponse.error("failed to change self workspace id");
    }
}
