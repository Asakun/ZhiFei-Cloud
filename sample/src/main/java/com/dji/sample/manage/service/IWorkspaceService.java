package com.dji.sample.manage.service;


import com.dji.sample.manage.model.dto.WorkspaceDTO;
import com.dji.sdk.common.PaginationData;

import java.util.Optional;

public interface IWorkspaceService {

    /**
     * Query the information of a workspace based on its workspace id.
     * @param workspaceId
     * @return
     */
    Optional<WorkspaceDTO> getWorkspaceByWorkspaceId(String workspaceId);

    Optional<WorkspaceDTO> getWorkspaceByIdLike(String shortId);

    /**
     * Query the workspace of a workspace based on bind code.
     * @param bindCode
     * @return
     */
    Optional<WorkspaceDTO> getWorkspaceNameByBindCode(String bindCode);

    Boolean saveWorkspace(WorkspaceDTO workspaceDTO);

    Boolean updateWorkspace(WorkspaceDTO workspaceDTO);

    PaginationData<WorkspaceDTO> getWorkspaceByPage(int page, int size);
}
