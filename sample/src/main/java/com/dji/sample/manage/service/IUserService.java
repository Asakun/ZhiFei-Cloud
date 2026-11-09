package com.dji.sample.manage.service;

import com.dji.sample.common.model.CustomClaim;
import com.dji.sample.manage.model.dto.UserDTO;
import com.dji.sample.manage.model.dto.UserListDTO;
import com.dji.sample.manage.model.dto.UserLoginDTO;
import com.dji.sample.manage.model.dto.UserPasswordDTO;
import com.dji.sample.manage.model.enums.UserStatusEnum;
import com.dji.sdk.common.HttpResultResponse;
import com.dji.sdk.common.PaginationData;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Optional;

public interface IUserService {

    /**
     * Query user's details based on username.
     * @param username
     * @param workspaceId
     * @return
     */
    HttpResultResponse getUserByUsername(String username, String workspaceId);

    /**
     * Verify the username and password to log in.
     * @param username
     * @param password
     * @param flag
     * @return
     */
    HttpResultResponse userLogin(String username, String password, Integer flag);

    /**
     * Create a user object containing a new token.
     * @param token
     * @return
     */
    Optional<UserDTO> refreshToken(String token);

    Optional<UserDTO> refreshToken(String token, String workspaceId);

    HttpResultResponse userRegister(CustomClaim customClaim, UserLoginDTO user) throws JsonProcessingException;

    HttpResultResponse resetPassword(String username, UserPasswordDTO passwordDTO);

    HttpResultResponse initPassword(String userId, String password);

    /**
     * Query information about all users in a workspace.
     * @param customClaim
     * @return
     */
    PaginationData<UserListDTO> getUsersByWorkspaceId(long page, long pageSize, CustomClaim customClaim);

    Boolean updateUser(String workspaceId, String userId, UserListDTO user);

    Boolean updateUserStatus(CustomClaim customClaim, String userId, UserStatusEnum status) throws JsonProcessingException;
}
